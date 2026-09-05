# Performance

No frame rates, memory figures, or device budgets are published here. None have
been measured. This document describes what work the renderer schedules and
when, which is what you need to reason about cost; the numbers come after
[Phase 2](../LAUNCH_PLAN.md) profiling.

## What actually costs something

Two independent pieces of work run per glass screen.

**Backdrop capture**, once per scene. `LiquidGlassScene` owns one
`ARGB_8888` bitmap at half the scene's physical width and height, rounding each
nonempty dimension up. It redraws visible non-glass children into that bitmap
when dirty. Bitmap storage is approximately one quarter of full resolution;
view traversal and frame time do not necessarily fall by the same factor.

Half-resolution capture is a visual quality trade: text and thin lines behind
glass may soften or alias. Refraction, blur, exclusion, and reflection distances
remain in physical scene pixels. A common sampling helper converts the final
positions to texture coordinates. There is no runtime quality flag. Physical-device
visual and performance validation is pending; see the
[capture validation handoff](capture-validation.md).

**Shader evaluation**, once per glass view per frame it draws. The AGSL shader
runs per pixel of the glass surface and makes up to nine backdrop samples per
pixel: one base, five for the blur cross, one each for the red and blue
dispersion offsets, and one internal-reflection sample. This scales with the glass surface's pixel area.

After interaction, each glass view runs a fixed-timestep membrane simulation on
a 25×25 grid and updates its texture after simulation steps. Untouched views use
a shader with no physics texture allocation or sampling. The grid does not scale
with the view.

## When work is scheduled

The renderer does not redraw continuously. As of the current version:

- **A glass view redraws only while something is moving.** After drawing a
  frame it re-posts itself only if the membrane simulation has not settled or a
  material transition is still running. When both are at rest, it stops. A
  static glass surface over static content does no per-frame work at all.
- **The membrane simulation is only excited by touch.** With
  `interactive={false}` there is nothing to settle, so a non-interactive glass
  view is at rest from the first frame.
- **The scene re-captures only when the backdrop is actually dirty.** The
  capture is marked dirty by a non-glass descendant invalidating, by layout or
  size changes, by children being added or removed, and by the scene's own
  background changing. Invalidations that originate from a glass view are
  explicitly ignored, so glass redrawing does not trigger a re-capture.
- **Allocations are reused.** The scene's backdrop bitmap is reallocated only
  when the scene's size changes; the glass view's normal-map bitmap and the
  runtime shader are created once. Both are released on detach and on
  `onTrimMemory` at `TRIM_MEMORY_UI_HIDDEN` or above.

## Known limitations

### Content that updates without invalidating its parent shows a stale backdrop

Backdrop capture is driven by invalidation. Content that changes its pixels
*without* invalidating up through the scene never marks the backdrop dirty, so
the glass keeps refracting the last captured frame.

This affects, at least:

- `SurfaceView` and `TextureView` — including most video players, camera
  previews, map views, and `react-native-video` / `expo-av` surfaces;
- anything drawing on its own render thread or through a hardware overlay;
- some `GLSurfaceView`-based components.

A `SurfaceView` in particular is composited by SurfaceFlinger in a separate
layer, so its content is not part of the view hierarchy's drawing at all and
would not appear in the capture even if the capture ran every frame.

This is a real behavioural consequence of the dirty tracking, not a bug that
will be fixed by tuning. If you need glass over live video today, drive the
invalidation yourself by animating a cheap non-glass sibling inside the scene,
and accept the continuous capture cost that comes with it.

### Below API 33 the scene does nothing

`LiquidGlassScene` allocates no bitmap, runs no capture pass, and delivers no
backdrop on Android 12L and below. The scene is a plain container there and the
glass views draw a static gradient. This tier has no capture cost, and no
optical props have any effect. See [compatibility.md](compatibility.md).

## Guidance

### Lists and scrolling

Scrolling a non-glass `ScrollView` or `FlatList` inside a scene invalidates on
every frame of the scroll, so the backdrop is re-captured on every frame of the
scroll. That is what makes the backdrop track the content instead of freezing,
and it is the single most expensive thing this library does.

- Keep the scene no larger than it needs to be. Capture cost is the scene's
  pixel area, so a full-screen scene captures a full screen every scrolled
  frame.
- Prefer a small number of large glass surfaces over many small ones. Shader
  cost is total glass pixel area, but each additional view adds its own draw and
  its own uniform setup.
- Glass **inside** a recycled list row works — `ListScreen.tsx` exercises it —
  but each recycle attaches and detaches a native view, which registers and
  unregisters it with the scene and marks the backdrop dirty. Glass rows in a
  long, fast-scrolling list are the worst case in the library. Measure before
  shipping one.
- Do not animate `cornerRadius`, `bevelDepth`, or any other optical prop
  through the JavaScript bridge during a scroll. Every prop write invalidates
  the glass view.

### Multiple glass elements

Multiple glass views in one scene share one backdrop bitmap and one capture
pass. Splitting them across several scenes gives each its own bitmap and its own
capture, which is strictly more work — prefer one scene per screen.

Overlapping glass surfaces do not compound capture cost, but they do compound
shader cost: each one shades its own pixels, and neither can see the other.

### Large scenes

The backdrop bitmap is `ARGB_8888` at `ceil(width / 2) × ceil(height / 2)`,
where width and height are physical scene pixels. Its cost still grows with
screen density as well as with layout size. A scene that
only needs to cover the top of a screen should be sized to the top of the
screen, not to the whole screen.

### Lifecycle

Backdrop and shader resources are released when the scene or view detaches, and
on memory-trim callbacks. Repeated navigation to and from a glass screen should
therefore reach a steady state rather than growing; `LifecycleScreen.tsx` in the
example app exists to make that observable. It has not been measured.

## Measuring it yourself

Until the profiling pass lands, use standard Android tooling against the example
app. `adb shell dumpsys gfxinfo <package>` for frame times, Android Studio's
profiler for allocations, and Perfetto for render-thread work. Report results
against a named device and API level — a number without one is not useful for
this library, because the whole tier-1 path depends on the GPU.
