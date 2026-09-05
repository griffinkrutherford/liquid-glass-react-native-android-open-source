# Half-resolution capture validation and release handoff

Status: development candidate, **not approved for a tester release**.

## Repository scope

The supplied September 5 responsiveness plan describes a separate application
patch with cropped, union-clipped, double-buffered capture. This repository's
baseline is `d4ede4a`: full-scene, single-buffer capture. The dependency patch,
linked library developer plan, September 4 measurements, mobile application,
API service, and S24 Ultra are not present in this workspace.

This candidate implements the contained half-resolution change against the
actual library baseline. It does not add cropping, double buffering, an atlas,
hardware capture, general scroll reuse, or a runtime quality flag. The installed
sample application is not replaced by local instrumented tests. Existing sample
UI edits remain outside the implementation commits.

## Implementation contract

- Allocate `ceil(W / 2) × ceil(H / 2)` for nonempty physical scene dimensions.
  Keep a constant scale of 0.5, including odd and one-pixel dimensions. Do not
  derive scale from the rounded bitmap-to-scene dimension ratio.
- Keep physical width, height, origin, and scale in immutable metadata bundled
  with the bitmap. Replace the bundle on resize even when rounded allocation
  dimensions happen to match. Consumers adopt the bundle together.
- Disable bitmap density conversion, then scale the capture canvas before
  translating the physical origin. Save/restore the matrix and clip each pass.
  This follows Android's [Canvas density and transform contract](https://developer.android.com/reference/android/graphics/Canvas#setDensity(int)).
- Keep the full-scene capture bounds and invalidation policy. Invalidations
  arriving during capture must survive until the following pass; clear the
  dirty flag before drawing and restore it if capture fails.
- Apply `(scenePoint - captureOrigin) * scale` only at the final backdrop lookup.
  All nine backdrop samples use that helper. Optical distances, exclusion masks,
  glass geometry, and the physics height texture remain in their original units.
- Retain bilinear backdrop filtering. Full-resolution metadata remains supported
  internally; views outside a scene retain their existing non-refracting gradient
  fallback. There is no new public quality API.
- Reuse capture storage while physical dimensions match. Clear consumer references
  before recycling storage on replacement, last-view removal, or existing lifecycle
  release callbacks. This retains the baseline's buffer ownership model; it does
  not claim that a two-buffer swap alone would prove deferred-rendering safety.

## Local checks

Run from the repository root with JDK 17 and the Android SDK configured:

```sh
npm test -- --runInBand
npm run typecheck
npm run build
npm run android:bridge-check
npm run test:pack
./gradlew :liquid-glass-core:test \
  :liquid-glass-view:testDebugUnitTest \
  :liquid-glass-view:testReleaseUnitTest \
  :liquid-glass-view:lint \
  :sample-android:assembleRelease
./gradlew :liquid-glass-view:connectedDebugAndroidTest
```

Instrumentation uses a separate library test APK and AndroidJUnitRunner. It
checks capture pixels, disabled density conversion, save/restore after failure,
reuse, odd-size replacement, hidden/reappearing consumers, registration/removal,
and invalidation during capture. Both physics variants are recorded against half-
and full-resolution inputs. An isolated GPU readback checks actual sampled pixels
when scale or origin changes while retaining the same bitmap identity.

The GPU helper is test code only. It submits and consumes one image at a time,
then releases the renderer, bitmap, buffer, and reader; Android documents the
[requirement to consume custom HardwareRenderer output promptly](https://developer.android.com/reference/android/graphics/HardwareRenderer).
It does not establish the viability of a production hardware capture pipeline.

Observed on 2026-09-05: 196 JavaScript tests, TypeScript checks, build/codegen,
13 core JVM tests, 12 view unit tests in each of debug and release, seven API 35
instrumented tests, Android view lint, React Native bridge compilation, release
sample APK assembly, and the
packed-package consumer installation check passed. The GPU coordinate test
passed for scale and origin replacement. Release APK assembly used the existing
local sample UI edits and was a compilation check, not an A/B measurement.

These are this workspace's results, not the separate application's stated
3,271 mobile and 2,386 API test counts. Those application suites and persisted
patch reproducibility still need to be run in their owning repository.

## Physical-device A/B procedure

Build baseline `d4ede4a` and the candidate from separate clean checkouts, using
the same consuming application revision and release-like build configuration.
Only the library revision should differ. Do not mix this repository's unrelated
local sample edits into one side of the comparison.

On the same S24 Ultra, repeat the same account, scene, traversal duration,
interaction path, display settings, and initial thermal conditions. Alternate
baseline/candidate order over at least three pairs. Record the actual rendering
size and active refresh cadence from the running app/trace, not only the panel
specifications or selected settings.

Collect per-run evidence without continuously logging from the measured frame:

```sh
adb shell wm size
adb shell wm density
adb shell dumpsys display > display.txt
adb shell dumpsys thermalservice > thermal-before.txt
adb shell dumpsys gfxinfo YOUR_PACKAGE reset
# Perform the fixed-duration traversal, using the same input sequence each run.
adb shell dumpsys gfxinfo YOUR_PACKAGE framestats > frames.txt
adb shell dumpsys meminfo YOUR_PACKAGE > memory.txt
adb shell dumpsys thermalservice > thermal-after.txt
```

Use Perfetto to distinguish UI capture work, RenderThread, and GPU work. Frame
stats can be incomplete or misleading for some pipelines; cross-check their
coverage against the trace. Record frame median/p95 and jank against the active
refresh budget, capture count, allocated bitmap dimensions/bytes, allocations
across resize/navigation, and memory after repeated navigation. Capture counts
need identical low-overhead instrumentation on both sides; neither dumpsys output
nor texture size alone establishes those counts.

| Evidence | Baseline | Candidate |
| --- | --- | --- |
| App/library commits and build variant | Pending | Pending |
| Device, OS, actual render size and cadence | Pending | Pending |
| Frame count, median, p95, jank definition/rate | Pending | Pending |
| UI, RenderThread, GPU trace | Pending | Pending |
| Capture count and bitmap allocation | Pending | Pending |
| Memory/thermal state before and after | Pending | Pending |
| Visual acceptance and reviewer | Pending | Pending |

## Visual and release gates

Compare sharp text, fine stars/constellation lines, and high-contrast chromatic
edges behind glass. Check scroll, parallax, drag, rotation, resize, hidden and
reappearing views, keyboard changes, navigation, nested glass, and overlapping
glass. Confirm alignment and sampling coverage as well as blur. Downsampling may
soften or alias these details; it is not a lossless optimization.

A tester binary needs explicit physical-device visual acceptance first. A speed
claim also needs measured before/after results. Smaller capture bitmaps do not
establish a proportional frame-time reduction, universal 60/120 FPS, or iOS parity.
No package publication or tester deployment is part of this candidate.

After measurement, prioritize capture-area reduction only if traces support it,
then a separately scoped hardware prototype. General scroll invalidation remains
necessary. Reuse is only plausible for an isolated immutable layer with proven
uniform motion and coverage. A later runtime quality tier needs its own API,
default, ownership, and documentation decision.
