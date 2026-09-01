# React Native screen macrobenchmarks

The instrumentation targets the example application's `benchmark` build type and drives the
actual React Native screens with UI Automator. It records startup and frame timing for the home
card, multiple simultaneous glass elements, scrolling glass-backed FlatList, and drag interaction.

With an API 24+ physical device or emulator connected:

```shell
cd example-react-native/android
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest
```

Use a physical, non-debuggable device for trustworthy baseline numbers. Benchmark JSON and trace
artifacts are written to the standard AndroidX Benchmark output directory. Per-screen JankStats
summaries from the target app can be collected separately with:

```shell
adb logcat -s LiquidGlassJank:I
```

Each iteration also emits a Perfetto trace beside the benchmark JSON. Open those traces in
Perfetto UI to separate UI-thread capture time, RenderThread work, and GPU queue pressure.

## Emulator harness baseline

The harness was validated on an API 35 arm64 emulator at 1080 x 2400 on 2026-09-01. These numbers
are useful only as a repeatability check; they are not a substitute for the physical-device matrix.

| Journey | CPU frame P50 | CPU frame P95 | Overrun P50 | Overrun P95 |
|---|---:|---:|---:|---:|
| FlatList scroll | 5.47 ms | 7.82 ms | -10.04 ms | -5.36 ms |
| Drag interaction | 2.58 ms | 6.59 ms | -12.61 ms | -7.46 ms |
| Multiple glass elements | 18.51 ms | 19.68 ms | 2.48 ms | 3.68 ms |
| Cold startup / single card | 31.90 ms | 50.67 ms | 20.32 ms | 36.10 ms |

Cold startup time-to-initial-display was 161.50 ms median across five runs. The multiple-element
journey is the only steady screen over the 60 Hz frame budget in this emulator baseline. Record a
new table for every physical device before using results to accept an optimization.

A same-emulator run after the first physics/shader changes showed large run-to-run variance
(especially in the drag journey), so it was deliberately not used to claim a speedup. Physical
devices with stable thermal conditions remain the acceptance gate for further tiers.
