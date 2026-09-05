# Liquid Glass — Performance Improvement Plan

## Objective and evidence standard

Reduce the cost of rendered frames. Preserve rendering quality for arithmetic
and scheduling changes; identify explicitly authorized quality trades separately.
Separate eliminated source work from measured CPU/GPU improvement: a driver may
already eliminate repeated expressions, and an emulator does not establish
physical-device frame rate or power consumption.

This document was re-audited on 2026-09-04 and updated on 2026-09-05 for the
half-resolution capture candidate. It replaces
the earlier speculative estimates and unconditional claims of losslessness.

## Implemented

- **Rest scheduling:** views request animation frames while the membrane or
  material animator is active. `advanceIfActive` now also stops within a catch-up
  batch as soon as the simulation reports rest, discarding leftover time before
  a subsequent impulse. Unconditional `advance` retains its fixed-step semantics.
  The rest thresholds intentionally permit tiny residual displacements; this is
  the existing simulation tolerance, not a claim of exact zero displacement.
- **Backdrop reuse:** each scene shares one capture across its glass views and
  recaptures when dirty. The bitmap is reused at unchanged dimensions.
- **Half-resolution capture candidate (2026-09-05):** captures use
  `ceil(width / 2) × ceil(height / 2)` texture pixels, with density conversion
  disabled and immutable origin/scale metadata published with each bitmap.
  All nine backdrop reads map physical sample coordinates through one helper.
  This is an explicitly requested quality trade, awaiting physical-device visual
  acceptance and measurement; see [the validation handoff](docs/capture-validation.md).
- **Physics cadence:** the default fixed timestep is 60 Hz, independent of display
  refresh rate. Normal-map pixels are updated after simulation steps.
- **Dormant physics variant:** until the first impulse, views use a shader without
  height-map samples and allocate no height-map bitmap. Once disturbed, a view
  retains the physics variant even after rest because residual heights may remain.
- **Retained shader state:** unchanged input bitmap identities retain their
  `BitmapShader` bindings, and optical/material uniforms upload when dirty.
  Scene origin still uploads on draws. Identical optical prop values do not
  invalidate the view.
- **Shared refraction geometry:** the three wavelength calculations and Fresnel
  reflection use one surface normal. Incident cosine, optical path length, and
  displacement limit are computed once and passed to the refraction helper.
  This removes three repeated normalizations and two copies of the other
  expressions in shader source while retaining their arithmetic and precision.
- **Exact-zero sample shortcuts:** zero blur uses the center sample; zero
  dispersion reuses its red and blue channels.
- **Exclusion local naming:** `exclusionMask` no longer shadows the uniform.

## Current work per pixel and capture

The full shader path has up to nine backdrop samples: base, center, four blur
samples, red, blue, and internal reflection. The disturbed physics variant adds
four height-map samples; the dormant variant adds none. Zero blur or dispersion
can reduce those counts. These are source-level counts, not GPU measurements.

There are ten rounded-box SDF evaluations: four for the boundary normal, four
through the bevel gradient, one for optical height, and one for inside distance.
The refraction helper still runs for three wavelengths. There are two `pow`
expressions, including the uniform-only Fresnel base reflectance.

Backdrop capture still rasterizes the non-glass hierarchy into a full-scene
software canvas, now scaled by one half. ARGB_8888 storage is
`ceil(width / 2) × ceil(height / 2) × 4` bytes: a 1440 × 2400 scene needs
3.46 MB (3.30 MiB), excluding overhead, versus 13.8 MB (13.2 MiB) at full resolution. Allocation occurs on initial
capture or resize, not on every dirty frame. Rasterization and clearing recur on
dirty frames. Both CPU capture and GPU shading need independent measurement.

## Measurement work before larger changes

1. Add repeatable benchmark scenarios for a single card, multiple cards,
   scrolling content, drag interaction, and overlapping glass. Record dimensions,
   density, material, build variant, thermal state, and refresh rate.
2. Capture UI-thread, RenderThread, and GPU timings separately with Perfetto and
   a supported device GPU profiler. Report distributions and missed frame budgets,
   not just average FPS. Keep reporting outside the measured workload.
3. Establish physical-device baselines on a mid-range 60 Hz phone, a 120 Hz phone,
   and a high-density device. Check the pre-API-33 fallback separately.
4. Establish deterministic rendered-image comparisons across materials, light/dark
   appearance, corner sizes, exclusion masks, overlapping views, and fixed physics
   states. Test high-contrast edges as well as photographs.

## Next candidates

### Capture correctness and bounded capture

Audit transformed children, scrolling ancestors, clipping, alpha, and nested glass
before changing capture bounds. The current capture manually translates and draws
children; optimizations must account for all relevant view transforms and capture
coordinate mappings.

`ShaderSampleBounds` provides tested bounds for shader displacements, but region
capture is not wired into `LiquidGlassScene`. A future implementation must include
blur/reflection reach, sampling footprints, transforms, and bitmap-origin offsets.
Compare it against full-scene capture before claiming equivalent output. Dirty
region intersection can follow once bounded capture is correct.

### Uniform-only arithmetic

Consider moving the bevel radius, wavelength IORs, Fresnel base reflectance, and
material coefficients to CPU uniform updates. This trades shader arithmetic for
uniform state and JNI work. CPU and GPU rounding, fused operations, and `pow`
implementations may differ; moving arithmetic across them is not automatically
bit-identical. Measure generated behavior and image differences first.

### Exact-zero dispersion arithmetic

The shader skips red/blue texture reads at zero dispersion but still calculates
their ray offsets. Consider placing wavelength-only arithmetic in the existing
branch. Preserve exclusion scaling and validate both zero and nonzero cases.

### Capture clear and layers

Skipping the clear requires proof that the background overwrites every captured
pixel with opaque content, including its bounds and drawable state. Merely having
a background is insufficient. Measure the explicit hardware layer independently;
it may help static composition while costing memory or work during animation.

### Architectural experiments

A hardware-backed capture could avoid software rasterization, but requires a
prototype with explicit synchronization, texture ownership, lifecycle behavior,
API compatibility, and nested/overlapping-glass semantics. A `RenderNode` is not
a drop-in bitmap shader input. Keep the current path as a comparison baseline.

## Proposals that are not established lossless optimizations

- **Sub-pixel blur/dispersion cutoffs:** bilinear sampling can change at arbitrarily
  small displacements, particularly near high-contrast edges. Half-pixel separation
  does not imply identical samples. Keep exact-zero guards unless an explicit
  quality trade is approved and validated.
- **Small Fresnel cutoffs:** a contribution below half a byte can still cross a
  rounding boundary. The ordinary flat-surface contribution is not generally zero.
- **Replacing the boundary normal with a constant epsilon:** normalization can
  amplify a small input, so this can change reflection direction substantially.
- **Precomputed geometry or gradient textures:** finite precision, filtering, and
  the order of differentiation/interpolation can change output. F16 storage is not
  proof of losslessness, and full-resolution geometry costs about eight bytes per
  pixel. The height texture stores scalar displacement, not a precomputed normal.
- **ALPHA_8 height maps:** verify filtering and input-shader channel semantics before
  assuming equivalent sampling. Reciprocal multiplication can also round differently
  from division during height quantization.
- **Shader sharing across views:** retained per-view uniforms conflict with a shared
  mutable instance. UI-thread serialization alone does not prove safety for deferred
  rendering; validate lifetime and snapshot behavior before implementing.
- **Border folding:** SDF antialiasing and a stroked rounded rectangle need not match.
- **Removing the base backdrop sample:** source-over composition changes overlapping
  glass semantics and requires premultiplied color. Returning straight RGB with a
  fractional alpha is not an equivalent replacement.
- **Half precision:** a final 8-bit output does not guarantee that intermediate fp16
  rounding preserves pixels, especially after multiple operations.

Adaptive resolution, skipped captures, smaller grids, and simplified optical
models remain deferred quality/performance trades. The fixed half-resolution
capture candidate above is the explicitly authorized exception.

## Validation for the September 4 round

- 196 JavaScript tests, TypeScript checks, package build, and Fabric codegen passed.
- Core JVM tests and debug/release Android view unit tests passed. New runner tests
  cover mid-batch settlement, fractional-time discard, waking after rest, and
  unchanged unconditional catch-up behavior.
- Android view lint, React Native bridge compilation, sample APK assembly, and
  packed-package consumer installation passed.
- API 35 emulator launch and touch/drag exercise checked dormant and disturbed
  shader paths. No fatal runtime or LiquidGlassView errors were observed.

These checks establish functional/build coverage. They do not establish a measured
frame-rate improvement or cross-device pixel identity. The sample APK includes the
workspace's existing uncommitted sample UI changes, which are outside these commits.
