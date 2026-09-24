# Liquid Glass — Performance Ideas (Brainstorm)

Brainstormed 2026-09-23 against `perf/half-resolution-capture` (`bee3a9b`).
This began as unmeasured candidates, roughly ordered by expected frame-rate
impact. Remaining candidates are not established; profile first (see
[Suggested order](#suggested-order)).
Complements [PERFORMANCE_PLAN.md](PERFORMANCE_PLAN.md).

Status updated 2026-09-23: the inside-glass invalidation fix and flat-interior
bevel branch are implemented. A scroll capture trace and an isolated RenderNode
prototype are recorded in [PERFORMANCE_PLAN.md](PERFORMANCE_PLAN.md); neither
establishes a physical-device speedup.

## 1. Move backdrop capture to the GPU (likely the biggest win)

`LiquidGlassScene.dispatchDraw` rasterizes the non-glass hierarchy into a software
`Canvas(bitmap)` on the UI thread (`LiquidGlassScene.kt`, `dispatchDraw` /
`captureBackdrop`). The changed bitmap is then re-uploaded as a texture. During a
scroll this happens every frame. Half resolution shrinks that work but doesn't
remove it.

### Option A: RenderNode + RenderEffect (API 33, which the effect already requires)

- The scene records its non-glass children into a `RenderNode`. On a recording
  canvas, child views mostly reuse their existing display lists, so the CPU cost
  is just recording the draw calls. Nothing is redrawn in software and nothing is
  uploaded.
- Each glass view draws that node, shifted by its scene origin and clipped to its
  bounds plus the reach from `ShaderSampleBounds`, into its own `RenderNode` with
  `RenderEffect.createRuntimeShaderEffect(shader, "backdrop")`.
- Scrolling costs almost nothing extra: the GPU just redraws the content under
  each glass rect.
- Side benefits:
  - The backdrop can't go stale when content changes without invalidating the
    scene, because display lists are always current.
  - `TextureView` content would probably show up. `SurfaceView` still won't.
  - Much of the dirty-tracking code could go away.
- Prior art: Dimezis BlurView's RenderNode path, Haze, and Kyant's backdrop
  libraries.
- Risks, which PERFORMANCE_PLAN.md already flags:
  - Coordinates when the shader samples outside the node's bounds.
  - Nested and overlapping glass.
  - Getting output that matches the current path. Keep the bitmap path as a
    reference to compare against.

### Option B: `HardwareRenderer` → `ImageReader` → `Bitmap.wrapHardwareBuffer`

- A smaller change: the GPU draws the same content into an offscreen buffer, and
  the existing `BitmapShader` and shader stay as they are.
- This removes the software drawing and the texture upload.
- It adds one frame of latency and some synchronization complexity.

## 2. Cheap fixes if the bitmap path stays

- **Ignore invalidations from content inside glass — implemented.** The former
  `onDescendantInvalidated` check missed two cases:
  - An animating icon, spinner, or pressed state inside a glass button, where
    `target` is the inner view.
  - A glass view wrapped in another view, which is very common in React Native.

  The implementation walks up from `target` and ignores invalidations inside a
  glass view owned by that scene. It stops at an inner scene so an outer scene
  still recaptures glass drawn by the inner one. Connected tests cover both
  wrapper and nested-scene cases. The scroll trace measures capture time but
  does not isolate savings from this fix.
- **Capture only the region the glass needs.** Wire `ShaderSampleBounds` into the
  capture so it covers only the combined glass bounds plus sample reach. A tab
  bar over a full-screen feed would capture about 10–15% of the screen instead of
  all of it. This is the best step short of section 1.
- **Skip recaptures that can't affect the glass.** Once capture is bounded,
  ignore invalidations whose rect doesn't intersect it. This helps with spinners,
  Lottie animations, and blinking cursors elsewhere on screen. It doesn't help
  scrolling.
- **Skip capture when no glass is visible.** If every glass view is off screen,
  `GONE`, or fully transparent, `shouldCapture()` could return false.

## 3. Shader

- **Skip the bevel math in the interior — implemented.** The rounded-box
  distance function changes by at most the distance moved. When
  `insideDistance >= zRadius + 2.0`, the four samples 1.5 px away are beyond the
  bevel radius, with a 0.5 px floating-point margin. The gradient is zero and
  `opticalHeight` is `zRadius`. This skips five rounded-box distance evaluations
  for interior pixels; physical-device pixel comparison remains outstanding.
- **Pre-blur once per capture instead of per pixel.** Frosted materials multiply
  the blur radius by up to 4.8×, and the 5-tap cross blur runs for every glass
  pixel on every frame. A pre-blurred, lower-resolution backdrop produced once
  per capture and shared by all glass views would replace those taps with 1–2
  samples. With option A, `RenderEffect.createBlurEffect` could be chained for
  this. The output changes, so it's a quality trade, but it probably looks
  better than the cross blur for Satin.
- **Reconsider `LAYER_TYPE_HARDWARE` on the glass view.** While the glass redraws
  every frame (scrolling, ripples), the layer adds an extra offscreen pass and
  composite. It only pays off when the glass is static and its parent redraws.
  Measure the scroll case with and without it.

## 4. Scheduling

- **Don't redraw on frames where physics didn't step.** Physics runs at a fixed
  60 Hz, but `postInvalidateOnAnimation` redraws the glass every vsync. On a
  120 Hz display, half of those redraws produce identical pixels. Advancing the
  simulation from a `Choreographer` callback and only invalidating when a step
  ran (or the material animator is running) halves the shader cost during
  ripples, with no visible change.

## 5. Scaling quality to the device

PERFORMANCE_PLAN.md defers these as quality trades, but they're how the library
reaches "as many devices as possible":

- A `quality="auto|high|balanced|low"` prop. Lower tiers would:
  - capture at ¼ resolution (hard to notice under frost or blur);
  - skip the dispersion samples;
  - skip the internal-reflection sample;
  - use fewer blur taps.
- For `auto`, combine a static tier (`MEDIA_PERFORMANCE_CLASS`,
  `isLowRamDevice`) with live feedback: step down when `FrameMetrics` shows
  missed frame budgets or `PowerManager.getThermalHeadroom` gets low.

## Suggested order

1. Profile a scroll in the macrobenchmark with Perfetto. Emulator UI-thread
   capture time is recorded; GPU shader time and physical-device data remain.
2. Ship the inside-glass invalidation fix and the interior bevel branch. Done;
   physical-device pixel comparison remains.
3. Prototype option A on a branch against changing and overlapping glass.
   `perf/rendernode-prototype` has standalone GPU probes, but its integrated
   scene pixel checks are unresolved. Keep the bitmap path as the reference.
