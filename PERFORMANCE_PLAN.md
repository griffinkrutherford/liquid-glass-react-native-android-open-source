# Liquid Glass — Frame Rate Improvement Plan

## Objective

Raise sustained frame rate across the device range — mid-range 60 Hz phones through
120 Hz flagships — **without reducing output quality** at the default settings.

"Without reducing quality" is treated here as a hard constraint, not a preference.
Every item in Tiers 1 through 3 is either mathematically lossless or provably
below the visible threshold, and each carries the argument for why. Tier 4 is the
only section that trades quality for speed, it is opt-in, and it stays off on
hardware that does not need it.

This plan supersedes nothing in `LAUNCH_PLAN.md` Phase 2; it is the detailed
execution of that phase's "optimizations to evaluate" list.

## Current state

Recent uncommitted work already landed the two largest *scheduling* wins:

- Redraw is gated on membrane rest plus material-animator state, so a visually
  static screen no longer repaints at display refresh rate.
- The scene dirty-tracks its backdrop capture instead of re-rendering the whole
  child hierarchy every frame.

**Neither has been compiled or measured.** Everything below assumes those land
and hold. The work here targets the cost of the frames that *do* get drawn.

## Preconditions — do not start optimizing before this exists

Nothing in this document should be implemented before there is a baseline,
because several items below trade one resource for another and the correct
choice depends on which resource is actually scarce on the target device.

1. **Macrobenchmark harness** (`androidx.benchmark.macro`) driving the
   `example-react-native` screens: single card, three cards, glass over a
   scrolling `FlatList`, drag interaction.
2. **`JankStats`** wired into the example app to report dropped frames per screen.
3. **Perfetto / `systrace` captures** separating UI thread, RenderThread, and GPU
   queue time. The distinction matters: the shader work is GPU-bound, the
   backdrop capture is CPU-bound on the UI thread, and they need different fixes.
4. **A GPU profiler pass** (Android GPU Inspector, or Mali/Adreno vendor tools)
   to get per-pixel shader cost rather than inferring it.
5. **A fixed device set.** At minimum: one mid-range 60 Hz device, one 120 Hz
   flagship, one high-density tablet, and one API 32 device to confirm the
   fallback path is unaffected.

Record baseline numbers per screen per device before changing any code. Without
this, "faster" is unfalsifiable.

## Where the time actually goes

Two independent costs, requiring separate treatment.

### GPU: the AGSL shader, per pixel of glass

Counted from the current `GLASS_SHADER` source:

| Work | Count per pixel | Notes |
|---|---|---|
| `backdrop.eval` | **9** | base, centre, 4 blur taps, red, blue, internal reflection |
| `heightMap.eval` | **4** | via `heightAt`, for the physics slope |
| `roundedBoxSdf` | **10** | 4 in `edgeNormal`, 4 via `bevelGradient`→`bevelHeight`, 1 direct `bevelHeight`, 1 for `insideDistance` |
| `refractedRayOffset` | **3** | each with a `normalize`, `sqrt`, `length` and divide |
| `pow` | 2 | Fresnel |

**13 texture fetches and 10 SDF evaluations per pixel.** For a 1080×600 px card
on a 3× density screen that is roughly 8.4 M texture fetches per frame for a
single element. This is the dominant cost on any GPU-bound device, and it scales
linearly with glass area — so tablets and large cards suffer most.

### CPU: the backdrop capture, per dirty frame

`LiquidGlassScene.captureBackdrop` draws the non-glass child hierarchy into a
full-screen `ARGB_8888` software `Canvas`. Two consequences:

- It allocates and touches a full-screen bitmap (≈8.3 MB at 1440×2400).
- Drawing into a software canvas **forces CPU rasterization of the entire
  backdrop subtree**, discarding hardware acceleration for that content — text,
  images, shadows and all.

On a scrolling screen this runs on every frame that scrolls, on the UI thread.
This is the likely cause of jank during scroll, and it is invisible to a
GPU-only profiler.

---

## Tier 1 — Lossless shader arithmetic reductions

Pure algebraic and caching wins. Output is bit-identical or differs only below
one ULP of an 8-bit channel. Highest value per unit of risk; do these first.

### 1.1 Bake the static geometry field into a texture

`roundedBoxSdf`, `bevelHeight`, `bevelGradient` and `edgeNormal` depend **only**
on `size`, `cornerRadius` and `bevelDepth` — never on the backdrop, the physics,
or time. They are recomputed every pixel of every frame to produce a value that
changes only when a prop or the layout changes.

Precompute into a single `RGBA_F16` texture at view resolution:

| Channel | Value |
|---|---|
| R, G | `bevelGradient(p, zRadius)` |
| B | `bevelHeight(p, zRadius)` (the optical height) |
| A | `insideDistance` (drives `rim` / `rimCoordinate`) |

Regenerate on size, `cornerRadius` or `bevelDepth` change only. This removes
**all 10 `roundedBoxSdf` evaluations and the 8 `bevelHeight` calls** per pixel,
replacing them with one texture fetch.

- **Quality:** lossless at 1:1 resolution with F16 precision. These are smooth,
  band-limited fields; F16 has ample headroom for values in the 0–`radius` range.
- **Cost:** one extra full-resolution F16 texture per glass view (≈8 bytes/px).
  On a 1080×600 card that is ~5 MB. This trades memory for GPU time — if the
  baseline shows memory pressure rather than GPU pressure, prefer 1.2 and 1.3
  and consider a half-resolution field, which is still visually lossless because
  the underlying fields are smooth.
- Generate it with a second, trivial AGSL pass into a `RenderNode`, not on the CPU.

### 1.2 Delete `edgeNormal` entirely

`boundaryNormal` is computed with 4 `roundedBoxSdf` evaluations and used exactly
once:

```
float2 reflectionDirection = normalize(surfaceSlope + boundaryNormal * 0.001);
```

At a weight of `0.001` this is not shaping the reflection — it is a
degeneracy guard preventing `normalize` on a zero vector. Replace with a
constant epsilon:

```
float2 reflectionDirection = normalize(surfaceSlope + float2(1e-6, 1e-6));
```

- **Quality:** the perturbation it currently contributes is far below one 8-bit
  channel step. Verify numerically by differencing rendered output before and
  after; expect zero differing pixels.
- **Saving:** 4 SDF evaluations per pixel, independent of 1.1 (and free if 1.1
  lands anyway).

### 1.3 Branch out the dispersion fetches when separation is sub-pixel

`sourceRed` and `sourceBlue` cost 2 texture fetches and 2 full
`refractedRayOffset` evaluations. When `iorDelta` is small the three sample
points collapse to within a fraction of a pixel and the fetches return the same
texel.

Compute the red/blue offsets, and if `length(offsetRed - offsetGreen) < 0.5`,
skip both fetches and use `blurred.rgb`.

- **Quality:** lossless by construction — the branch only fires when the samples
  are provably within half a pixel of the green sample.
- **Saving:** 2 fetches on any configuration with low dispersion. Note this now
  fires more often than before: `dispersion` recently became dimensionless, so
  the default produces a smaller separation than it did when it was
  density-scaled.

### 1.4 Branch out the blur taps when the blur radius is sub-pixel

`b1`–`b4` are 4 fetches forming a cross blur at radius `materialBlur`. For
`crystal` (blur 0.8 dp) and often `regular` (2.2 dp scaled by `edgeSharpness`),
this radius falls below a pixel in the glass interior, where the four taps
return values indistinguishable from `b0`.

Guard the four taps on `materialBlur > 0.5`, falling back to `b0.rgb`.

- **Quality:** lossless where it fires. `satin` (blur 6.5 dp, `frostiness` 1.0)
  keeps all four taps.
- **Saving:** 4 of 9 fetches on the sharpest materials.

### 1.5 Branch out the internal-reflection fetch at low Fresnel

The internal-reflection fetch is mixed in at
`fresnel * mix(0.58, 0.42, regularity)`. In the flat interior of the glass,
`surfaceNormal.z ≈ 1`, so `fresnel ≈ f0` — about 0.037 at IOR 1.47. Below a
threshold where the contribution cannot change the output byte, skip the fetch.

- **Quality:** lossless below a correctly chosen threshold. Derive the threshold
  from the mix weight rather than guessing: skip when
  `fresnel * mixWeight * 255 < 0.5`.
- **Saving:** 1 fetch across most of the glass interior; the rim, where Fresnel
  actually matters, keeps it.

### 1.6 Physics slope from 4 fetches to 1

`heightAt` is called 4 times to central-difference the height map. Since the
normal map is authored on the CPU, write the **gradient** into the bitmap
directly (R = dx, G = dy, computed once per grid cell over a 24×24 grid) instead
of the scalar height.

- **Quality:** improves it, if anything. The gradient is currently computed from
  bilinearly interpolated samples of a 24×24 map; computing it on the grid and
  interpolating the gradient is at least as accurate.
- **Saving:** 3 of 4 height-map fetches per pixel.
- **Also:** the normal bitmap can drop from `ARGB_8888` to a two-channel format,
  cutting the per-frame `setPixels` upload.

**Tier 1 combined:** 13 texture fetches → **4–6** depending on material, and 10
SDF evaluations → **0**. This is the bulk of the available GPU win and none of it
costs image quality.

---

## Tier 2 — Lossless reduction of capture work

Attacks the CPU cost, which Tier 1 does not touch.

### 2.1 Capture only the region the glass can actually sample

The scene currently captures the full screen. The shader only ever reads
`sceneOrigin + p + offset`, where `|offset|` is bounded by the `refraction`
limit inside `refractedRayOffset`, plus the blur radius and the reflection
displacement.

Compute the union of glass-view bounds inflated by that provable maximum sample
distance, and capture only that region.

- **Quality:** lossless. Pixels outside the union are mathematically unreachable
  by any sample. Derive the inflation from the same expression the shader uses so
  the two cannot drift; add a JVM test asserting the bound.
- **Saving:** proportional to screen coverage. A floating control or a single
  card typically covers 15–30 % of the screen, so this is a 3–6× reduction in
  capture cost and in bitmap memory.
- This is likely the **single largest CPU win** and should be prioritized over
  dirty-region tracking, which is more complex and subsumed by it.

### 2.2 Intersect with the dirty region

Once 2.1 is in, additionally clip the capture to the invalidated rectangle
reported by the invalidation hooks. Only worth doing if profiling shows capture
still dominant after 2.1 — measure before building it.

### 2.3 Share one capture across glass views in a scene

Already the case (the scene owns one bitmap). Confirm this holds after 2.1: the
union rect must be shared, not per-view, or three cards will allocate three
captures. Each view needs its own `sceneOrigin` offset into the shared bitmap.

### 2.4 Decouple physics rate from display rate

The membrane integrates at whatever cadence `onDraw` runs. At 120 Hz that is
double the physics work for a simulation whose visible output is a smooth 24×24
displacement field. Fix the simulation at 60 Hz and interpolate, or simply let
the existing `FixedTimestepRunner` accumulator run at a 60 Hz timestep while
frames render at 120 Hz.

- **Quality:** unchanged, and *more* deterministic — the fixed-timestep runner
  exists precisely so behaviour does not depend on frame rate.
- **Saving:** halves CPU physics and normal-map upload on 120 Hz devices, which
  is exactly where the frame budget is tightest (8.3 ms rather than 16.6 ms).

### 2.5 Re-evaluate `setLayerType(LAYER_TYPE_HARDWARE)`

The glass view forces a hardware layer in its constructor. With the shader
drawn directly into the parent's display list, this may be forcing an
unnecessary offscreen render target and an extra full-surface composite per
frame. Test with it removed and measure; it may be a holdover.

---

## Tier 3 — Architectural: eliminate the software capture

The highest-ceiling change, and the one that removes the CPU cost class
entirely rather than shrinking it.

**The problem:** drawing the backdrop into a software `Canvas` forces CPU
rasterization of content the GPU already knows how to draw, then uploads the
result as a texture every dirty frame. It is a readback in all but name.

**The approach:** on API 31+, `RenderEffect.createRuntimeShaderEffect(shader, "backdrop")`
runs an AGSL shader with the view's own rasterized content bound as the named
input shader, entirely on the GPU inside the render pipeline. Applied to the
scene container, with the glass geometry passed as uniforms, this yields the
refracted result with **zero software rasterization, zero readback, and no
capture bitmap at all**.

Consequences to work through before committing to it:

- The effect applies to a view's own content, so the composition model inverts:
  the scene becomes the effect host and glass elements become regions described
  by uniforms, rather than views that sample a shared bitmap. Multiple glass
  elements need either an array of rects in one pass or one pass per element.
- Interaction with React Native's view hierarchy and Yoga layout needs care —
  glass views currently participate in layout normally, and that must survive.
- API 33+ already required for `RuntimeShader`; `RenderEffect` runtime-shader
  support is API 31+, so this does not narrow device support.
- The existing bitmap path stays as the implementation for anything the
  `RenderEffect` path cannot express.

**Quality:** improves. The backdrop is sampled at full GPU resolution from the
live render, with no intermediate 8-bit bitmap round-trip.

Prototype this against the benchmark harness **before** committing to the
rewrite. If Tiers 1 and 2 already clear the frame budget on the target device
set, this becomes optional — sequence it last for that reason.

---

## Tier 4 — Adaptive quality (the only quality trade)

Everything above is quality-neutral. This section is not, and therefore:

- **Off by default on any device meeting the frame budget.**
- Engaged only from measured frame timing, never from a static device allowlist.
- Exposed as an explicit opt-in prop so an application can refuse it outright.

Ordered from least to most visible:

1. **Reduce the physics grid** from 24×24 to 16×16 under load. Affects only the
   subtle membrane ripple; imperceptible while static.
2. **Capture the backdrop at 0.75× or 0.5×** and let the linear-filtered
   `BitmapShader` upsample. Nearly free on `satin` (already heavily blurred);
   visible on `crystal`, which is sharp by design. Gate per material — never
   downscale `crystal`.
3. **Drop to the cheapest branch set** in Tier 1 (force the sub-pixel paths).
   Visible as slightly reduced chromatic fringing.
4. **Reduce sampling for small or distant elements.** A 44 pt button does not
   need the same optical fidelity as a full-width card; scale capture resolution
   by element size. Low risk because the artefacts are sub-pixel at that scale.

Define the hysteresis explicitly — quality levels that oscillate frame to frame
look far worse than a consistently lower level.

---

## Protecting quality while optimizing

The constraint is only meaningful if regressions are detectable:

1. **Golden-image differencing.** For every Tier 1 change, render a fixed scene
   before and after and diff. Tier 1.1–1.6 should produce **zero differing
   pixels** on the branch-guarded paths and no more than ±1 in the last channel
   bit elsewhere. If a change produces a visible diff, it is misclassified and
   belongs in Tier 4.
2. **Tie into the Phase 5 visual-regression suite** rather than building a second
   harness — the reference scenes that plan already specifies (three materials,
   thickness range, bevel and IOR variants, edges and corners, light and dark
   backgrounds, shader and fallback renderers) are exactly the right coverage.
3. **Test at the extremes**, not just defaults. Maximum thickness, maximum
   dispersion and minimum corner radius stress the branch thresholds hardest;
   a guard tuned at default values can be wrong at the range limits.
4. **Verify on real hardware per GPU vendor.** AGSL branch behaviour and F16
   precision differ between Adreno, Mali and Xclipse. A branch that is a win on
   one can be a loss on another if it breaks wavefront coherence.

## Sequencing

1. Build the measurement harness and record baselines. **Nothing before this.**
2. Tier 1.2, 1.3, 1.4, 1.5 — small, independent, individually verifiable branch
   and deletion changes. Ship as separate commits with diff evidence each.
3. Tier 1.6 and 1.1 — the texture-baking changes, larger but still lossless.
4. Re-measure. Establish what is now the bottleneck; it will likely have moved
   from GPU to CPU capture.
5. Tier 2.1, then 2.4 and 2.5. Re-measure.
6. Tier 2.2 only if capture still dominates.
7. Prototype Tier 3 and decide on evidence whether the rewrite is justified.
8. Tier 4 last, and only for devices still missing budget after 1–3.

## Risks

- **Branch divergence.** Tier 1.3–1.5 introduce per-pixel branches. On tiled GPUs
  a divergent branch can cost more than the work it skips. These branches are
  spatially coherent (interior versus rim), which is the good case, but this must
  be measured per vendor rather than assumed.
- **Memory for GPU time.** Tier 1.1 trades a per-view F16 texture for shader
  cost. On a memory-constrained device with several glass elements this can be
  the wrong trade. Make it conditional on element count and available memory.
- **The dirty-tracking foundation is unverified.** Everything here assumes the
  recent invalidation-gating and capture dirty-tracking changes are correct.
  They have not been compiled or run. Validate them on hardware before building
  further optimization on top.
- **Stale backdrop for non-invalidating content.** `SurfaceView`, `TextureView`
  and video do not mark the backdrop dirty. Tier 2.1's tighter capture region
  does not worsen this, but any future capture-scope work must not make it
  harder to fix.

## Explicitly out of scope

- Lowering default optical quality to gain frame rate. If the defaults are too
  expensive for the target device set, that is a Tier 4 adaptive decision or a
  documented budget, not a silent downgrade.
- Reducing `blurRadius`, `dispersion` or `refractionStrength` defaults for speed.
- Anything that changes rendered output at default settings without an explicit
  decision to do so.

---

# Tier 0 — Exactly lossless wins found on a second read

Everything in this section is **pure algebra, dead-work elimination, or call
elimination**. Unlike Tiers 1.3–1.5, none of it relies on a threshold argument:
the output is bit-identical by construction, so it needs no perceptual
justification and carries no branch-divergence risk.

This section should be executed **before Tier 1**. It is cheaper, safer, and
several items are larger wins than the threshold-based branches.

## 0.1 Hoist every uniform-only expression out of the per-pixel shader

The shader recomputes, **for every pixel of every frame**, a set of values that
depend only on uniforms and are therefore constant across the entire draw:

| Expression | Cost | Depends only on |
|---|---|---|
| `zRadius = min(bevelDepth, min(size.x, size.y) * 0.24)` | 2 `min` | `bevelDepth`, `size` |
| `iorDelta`, `iorRed`, `iorBlue` | 3 ops | `dispersion`, `indexOfRefraction` |
| `opticalGain` | divide + `mix` | `refraction`, `zRadius`, `regularity` |
| `f0 = pow((ior-1)/(ior+1), 2.0)` | **a `pow`** | `indexOfRefraction` |
| `limit` inside `refractedRayOffset` | `mix`, `clamp`, divide — **evaluated 3× per pixel** | `refraction`, `baseThickness`, `size` |
| `texel = 1.0 / max(gridSize - 1.0, 1.0)` | divide | `gridSize` |
| `materialTint`, `schemeLift` | `mix` each | `tint.a`, `regularity`, `frostiness`, `appearance` |
| `mix(1.1, 0.82, regularity)`, `mix(0.92,0.72,…)`, `mix(0.58,0.42,…)`, `mix(0.32,0.46,…)`, `mix(0.18,0.08,…)`, `mix(0.42,1.0,…)`, `mix(0.90,1.0,…)` | 7 `mix` | `regularity` |

Compute all of these in `drawRuntimeGlass` on the CPU — once per frame instead of
once per pixel — and pass them as uniforms. On a 1080×600 card that converts
roughly **6 million redundant `pow`/divide/`mix` evaluations per frame into about
twenty CPU floating-point operations**.

A driver's optimizer *may* hoist some of these as uniform-invariant, but SkSL is
translated per-vendor and this cannot be relied on. Doing it explicitly is free
and guaranteed.

- **Quality:** bit-identical. The arithmetic is unchanged; only where it runs moves.
- **Risk:** none, beyond ordinary transcription care. Worth a golden-image diff
  asserting zero differing pixels.

## 0.2 Hoist the IOR-independent half of `refractedRayOffset`

`refractedRayOffset` is called three times per pixel with identical
`slope`, `opticalHeight` and `gain`, differing **only** in `ior`. Inspecting the
body, these are all IOR-independent and therefore computed three times for the
same result:

```
float3 normal          = normalize(float3(-slope.x, -slope.y, 1.0));   // a normalize
float  incidentCosine  = dot(normal, incident);
float  pathLength      = baseThickness + opticalHeight * 2.0;
float  limit           = refraction * mix(0.86, 1.32, …);              // also 0.1
```

Restructure so `normal`, `incidentCosine`, `pathLength` and `limit` are computed
once and passed in, leaving only the `eta`-dependent tail (`discriminant`,
`transmitted`, the divide and the clamp) to run three times.

- **Saving:** 2 of 3 `normalize` calls and 2 of 3 `dot` calls per pixel, plus the
  `limit` work already covered by 0.1.
- **Bonus:** `surfaceNormal` later in `main` recomputes
  `normalize(float3(-surfaceSlope.x, -surfaceSlope.y, 1.0))` — **the exact same
  value** as `normal` inside `refractedRayOffset`. Compute once, use for both.
  That is a third `normalize` eliminated.
- **Quality:** bit-identical.

## 0.3 Skip the physics path entirely when the membrane has never been disturbed

`interactive` defaults to **`false`**, and the membrane only ever receives energy
through `applyImpulse`, which is called exclusively from `onTouchEvent`. For a
non-interactive glass view — a card, a navigation bar, a floating control, which
is the common case — `physicsSlope` is **identically zero for the entire life of
the view**, yet every frame still pays for:

- 4 `heightMap.eval` fetches per pixel (`heightAt` ×4) — **4 of the 13 texture fetches**
- the initial 24×24 normal-map generation and `setPixels` upload
- a `Bitmap` allocation for the height map

The existing rest gate already prevents continuous integration and repeated uploads
while the membrane is undisturbed. This optimization removes the remaining GPU
sampling cost and one-time resource work; it does not claim those scheduling wins
again.

Track whether the membrane has ever been disturbed. Until it has, compile or
branch to a variant with `physicsSlope = float2(0.0)` and no height-map input,
and allocate no normal bitmap at all.

- **Quality:** bit-identical — adding a provably-zero slope changes nothing.
- **Saving:** roughly **30 % of all texture fetches** on the default
  configuration, plus the entire CPU physics and upload cost. Likely the single
  largest win in this document for typical usage, and it is free.
- Prefer two compiled shader variants over a runtime branch, so the fetches are
  removed from the program rather than merely skipped.

## 0.4 Fold the border pass into the main shader

`drawBorder` issues a **second full-perimeter `drawRoundRect`** with an
antialiased stroke and a freshly-bound `LinearGradient` — a separate draw call,
separate rasterization, and blending over the perimeter of every glass view,
every frame.

The main shader already computes `roundedBoxSdf(p)` and `rim`. The border is
analytically expressible from the SDF (`abs(sdf) < strokeWidth/2`) inside the
existing pass.

- **Quality:** improves. An SDF-derived border is analytically antialiased, and
  typically cleaner than a stroked path at the corners.
- **Saving:** one full draw call and one perimeter rasterization per view per
  frame; also removes the cached `borderGradient` and its `Paint`.
- This is the one Tier 0 item that is *not* bit-identical — it changes edge
  antialiasing — so validate it against golden images and treat a small,
  reviewed edge diff as acceptable only if it is visibly equal or better.

## 0.5 Stop re-uploading unchanged uniforms every frame

`drawRuntimeGlass` issues **19 `setFloatUniform` calls plus 2 `setInputShader`
calls on every frame, for every glass view**. Each crosses JNI.

`RuntimeShader` **retains uniform state** between draws. Of those 21 calls,
only `sceneOrigin` genuinely changes per frame during a drag or scroll; the rest
change only when a prop, size or material animation changes.

Track a dirty flag per uniform group (geometry, optics, material, tint) and set
only what changed. With three glass views at 120 Hz this removes on the order of
**7,000 JNI calls per second**.

- **Quality:** bit-identical.
- Combine with packing related scalars into `float4` uniforms to cut the call
  count further (`refraction`/`dispersion`/`ior`/`bevelDepth` into one vector).

## 0.6 Share one `RuntimeShader` across all glass views

`ensureRuntimeShader()` constructs `RuntimeShader(GLASS_SHADER)` **per view**, so
N glass elements compile N copies of the same AGSL program — N program objects,
N compilations, N sets of driver state.

Because uniforms and input shaders are set immediately before each
`drawRoundRect`, and all draws are serialized on the UI thread, a single shared
instance per process is safe.

- **Quality:** bit-identical.
- **Saving:** N−1 shader compilations at startup (a real contributor to
  first-frame jank) and the associated memory.
- **Interaction with 0.5:** these conflict — a shared shader cannot cache
  per-view uniform state. Resolve by sharing per *scene* and keeping the dirty
  tracking keyed on "last view drawn", or by accepting full uniform upload on a
  shared instance. Measure both; do not assume.

## 0.7 Drop the redundant `base` fetch by returning real alpha

The final line composites against an extra full texture fetch:

```
half4 base = backdrop.eval(sceneOrigin + p);   // fetch #1 of 9
…
glass = mix(base.rgb, glass, half(opticalAmount));
return half4(glass, 1.0);
```

`base` is the *un-refracted* backdrop directly beneath the pixel — which is
exactly what is already on screen underneath the glass. Returning
`half4(glass, opticalAmount)` and letting standard source-over blending do the
mix is algebraically identical and removes the fetch.

- **Saving:** 1 of 9 backdrop fetches, unconditionally.
- **Caveats — check both before adopting:**
  - With **overlapping glass**, the on-screen content beneath view A now includes
    view B's rendered output, whereas `base` came from the capture, which
    excludes glass. Overlap composition changes.
  - The view sets `LAYER_TYPE_HARDWARE`, so verify the layer composites
    source-over as expected rather than replacing the destination.
- **Quality:** identical in the non-overlapping case; changes overlap behaviour.
  Given the caveats this is lower priority than 0.1–0.5 despite being simple.

## 0.8 Tighten the two existing branches to sub-pixel thresholds

The shader already guards the blur taps and dispersion fetches, but on
`materialBlur > 0.0` and `dispersion > 0.0` — conditions that are true for
essentially every real configuration, so the branches almost never fire.

Replace with the sub-pixel thresholds from Tier 1.3 and 1.4: skip when the
sample separation is provably below half a pixel. Same code shape, but the
branches begin actually firing on `crystal` and `regular`.

## 0.9 CPU-side leftovers

- **`updateNormalMap` divides by `maxDisplacement` per cell** across 625 cells.
  Precompute the reciprocal and multiply.
- **The normal map is `ARGB_8888` writing `Color.rgb(v, v, v)`** — three
  identical channels — while the shader reads only `.r`. `ALPHA_8` is a 4×
  reduction in upload bandwidth (read `.a` in the shader).
- **`captureBackdrop` calls `drawColor(0, CLEAR)` over the full bitmap** before
  drawing. When the scene has an opaque background covering its bounds, the clear
  is redundant — a full-screen memory write per dirty frame.
- **`heightAt` calls `clamp(uv, 0, 1)`** before sampling, but the `BitmapShader`
  is already `TileMode.CLAMP`. Redundant per fetch.
- **Every prop setter calls `invalidate()`**, and React Native sets all 16 props
  on mount and on any prop change — up to 16 invalidations per update. Coalesce
  behind a single posted invalidation.

## 0.10 Use `half` precision for colour-range arithmetic

Values already confined to 0–1 colour range (`rim`, `frostiness`, `materialTint`,
`schemeLift`, `interiorTransmission`, the tint and darkness mixes) are computed
in `float` and immediately narrowed to `half` at use. On Mali and Adreno, fp16
ALU throughput is commonly **twice** fp32.

Keep `float` for anything positional or geometric — `p`, the SDF, offsets,
`normalize` — where precision genuinely matters. Convert only the colour math.

- **Quality:** output is 8-bit per channel; fp16 has ~11 bits of mantissa, so
  colour-range math has more than enough headroom. Verify with a golden diff and
  revert any expression that moves a pixel.

## Revised sequencing

Tier 0 displaces the front of the original plan:

1. Measurement harness and baselines. **Still first; nothing before it.**
2. **Tier 0.3** — the non-interactive physics skip. Largest default-case win, and
   bit-identical.
3. **Tier 0.1 and 0.2** — uniform hoisting and `refractedRayOffset` restructuring.
   Large, exactly lossless, no branch risk.
4. **Tier 0.5, 0.9** — CPU and JNI overhead.
5. **Tier 0.4, 0.6** — border folding and shader sharing. Re-measure.
6. **Tier 0.10, 0.8** — precision and threshold tightening, each golden-diffed.
7. Then Tier 1 (geometry-field baking), Tier 2, and only then Tier 3.
8. Tier 0.7 whenever the overlap semantics have been decided.

Tier 0 requires no new textures, no new memory, and no architectural change —
so unlike Tier 1.1 it carries no memory-versus-GPU trade to get wrong.

## Unrelated defect noticed while reading the shader

Inside the exclusion block, a local shadows the uniform:

```
uniform float4 exclusion;
…
float exclusion = 1.0 - smoothstep(exclusionInner, exclusionOuter, exclusionDistance);
localRefraction = 1.0 - exclusion;
```

The existing shader has launched successfully through Android's AGSL compiler, so
this is not a known compile failure. The shadowing is still needlessly ambiguous
for readers and tooling. Rename the local (`exclusionMask`) regardless.
