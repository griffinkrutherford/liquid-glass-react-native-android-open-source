# RenderNode backdrop prototype (2026-09-23)

This branch contains an opt-in `LiquidGlassScene.useRenderNodeBackdrop` path. It
records the non-glass hierarchy into a `RenderNode`, then uses
`RenderEffect.createRuntimeShaderEffect` to feed those GPU pixels to each glass
shader. The default remains the bitmap path. This is an experiment, not a
replacement ready for release.

On an API 35 arm64 emulator, the standalone tests establish that:

- A runtime shader can sample another node's pixels, including an offset sample.
- Source and effect nodes need explicit rerecording to show updated pixels in
  the isolated `HardwareRenderer` test.
- A glass view nested in a wrapper can survive suppression through its cached
  hardware display list. Invalidating the glass before recording hides it in
  the isolated test; the visible pass then needs a second invalidation.
- Nested `RenderNode.beginRecording` calls themselves preserve pixels.

The two integrated pixel tests model the changing backdrop of `ScrollScreen`
and the nested overlapping views of `MultiGlassScreen`. They are skipped because
the captured scene node rendered transparent in this detached-view harness,
including when capture was prepared before the outer node recording. The
standalone effect test still passes. The cause is unresolved, so the branch
does not support an FPS or visual-quality claim for those screens.

Next validation requires an attached activity window, pixel readback for both
screen layouts, and comparison with the bitmap path. Pay particular attention
to outside-scene sampling: `BitmapShader` clamps at the scene edge, while this
prototype's RenderEffect input is transparent there. The per-glass effect node
also adds padding for `ShaderSampleBounds` and a round-rect clip; those pixels
need comparison at high-contrast edges and in overlapping cards. Only after
those checks should a React Native prop enable the path in the example app for
Perfetto and GPU profiling.

Run the current green probes with:

```sh
./gradlew :liquid-glass-view:testDebugUnitTest :liquid-glass-view:connectedDebugAndroidTest
```
