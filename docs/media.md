# Media placeholders

Nothing in this repository's documentation ships a screenshot or a video yet.
Everything below **must be captured on real hardware** and none of it can be
produced without a device or emulator. This page is the capture checklist; it is
deleted or replaced by the assets themselves once they exist.

Do not substitute a rendering, a mock-up, or an image from another project.

## Capture environment

Record the environment with every asset, in the file name or an adjacent
caption:

- device or emulator model, Android version, and API level;
- GPU, if known;
- library version and React Native version;
- New or Old Architecture;
- debug or release build.

Two devices are needed at minimum: one on API 33+ for the shader path, and one
on API 23–32 for the fallback. An emulator is acceptable for the fallback tier;
the shader tier should be a physical device, because emulator GPU behaviour is
not representative.

## Important: screen capture may not show the effect

The runtime-shader path requires a hardware-accelerated canvas. Some capture
paths render in software and will show the fallback gradient instead of the
glass. Verify every capture against what the device screen actually shows before
publishing it. `adb shell screenrecord` and `adb exec-out screencap -p` are the
first things to try; an off-device camera recording of the screen is an
acceptable fallback for the video if the on-device recorders fail.

## Still images

| # | Asset | Screen | Shows |
| --- | --- | --- | --- |
| 1 | `before-after-card.png` | `HomeScreen` | Side by side: the same card with `effect="none"` and with `material="crystal"`, over the same backdrop. This is the primary README image. |
| 2 | `materials.png` | `MultiGlassScreen` | `crystal`, `satin`, and `nocturne` on identical tiles over identical backdrop. Same size, same corner radius, defaults for everything a preset does not set. |
| 3 | `tier-fallback.png` | `HomeScreen` | The same screen on an API 23–32 device, showing the static gradient fallback. Pair it with #1 at the same crop so the difference is unambiguous. |
| 4 | `navigation-bar.png` | `ScrollScreen` | The pinned satin header over scrolled content, mid-scroll, so the backdrop is visibly tracking. |
| 5 | `floating-control.png` | `ScrollScreen` or `MultiGlassScreen` | The draggable pill overlapping high-contrast content, showing chromatic dispersion at the edges. |
| 6 | `nested-content.png` | `HomeScreen` | Text, image, icon, and button inside one glass surface, to show children are ordinary React Native views. |

Crop consistently, use the example app's own backdrop asset so the images are
reproducible, and keep the light/dark state consistent within a pair.

## Demo video

One short clip, no longer than about 20 seconds, no narration, no music, no
titles. It must show, in order:

1. the glass at rest over static content;
2. a scroll, so the backdrop visibly tracks moving content;
3. a drag across an `interactive` surface, showing movement-driven membrane
   deformation while a stationary tap leaves the optics unchanged;
4. a drag of a `draggable` control across high-contrast content;
5. a material change with `animated` on, showing the transition.

Capture at the device's native resolution and frame rate. Do not speed it up,
slow it down, or cut between takes — the point of the video is that this is what
the library actually does in real time.

`adb shell screenrecord --bit-rate 12000000 /sdcard/demo.mp4` is the starting
point; check the caveat above about software rendering before trusting the
result.

## Where they go

- #1 and the video: `README.md`, immediately after the intro.
- #2: `docs/materials.md`, above the preset table.
- #3: `docs/compatibility.md`, in the rendering-tiers section.
- #4, #5, #6: `docs/recipes.md`, one per recipe.

Store the files under `docs/assets/`. Keep each still under about 400 KB and the
video under about 8 MB, or host the video outside the repository and link it.
