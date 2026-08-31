# Android physics demo

This application is a small, dependency-light visualizer for `liquid-glass-core`. It draws a
translucent rounded surface and overlays the membrane height field so that impulses and damping
remain easy to inspect, including on Android versions without runtime shader support.

Run it from Android Studio or with:

```shell
./gradlew :sample-android:installDebug
```

Then touch or drag anywhere inside the glass panel. The demo intentionally uses Canvas rather
than approximating production refraction; the renderer module can replace this visual layer while
continuing to drive it from the same `LiquidMembrane` snapshots.
