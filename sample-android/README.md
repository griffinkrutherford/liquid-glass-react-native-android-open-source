# Android physics demo

This application demonstrates `liquid-glass-view` over real Android scene content. On Android 13+
the surface uses AGSL to refract a captured backdrop, add chromatic dispersion, a small blur,
tint, Fresnel edge light, and physics-driven specular highlights. Older versions use a static
translucent fallback.

Run it from Android Studio or with:

```shell
./gradlew :sample-android:installDebug
```

Then touch or drag inside the glass panel. The custom membrane changes the refraction normals in
real time, bending the colored cards and grid behind the surface.
