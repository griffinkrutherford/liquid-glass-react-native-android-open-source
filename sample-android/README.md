# Android physics demo

This application demonstrates `liquid-glass-view` over real Android scene content. On Android 13+
the surface uses AGSL to refract a captured backdrop, add chromatic dispersion, a small blur,
tint, Fresnel edge light, and physics-driven specular highlights. Older versions use a static
translucent fallback.

Run it from Android Studio or with:

```shell
./gradlew :sample-android:installDebug
```

The demo uses a real CC0 beach photograph containing sky gradients, detailed palm fronds, a hard
horizon, and water highlights. Drag the weather card anywhere on the screen to compare how those
features fold through its inner rim. Dragging also excites the custom membrane physics.

See [ASSET_ATTRIBUTION.md](ASSET_ATTRIBUTION.md) for the photograph source and license.
