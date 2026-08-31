# Liquid Glass for Android

An experimental open source Android liquid-glass effect powered by a custom,
deterministic membrane physics engine.

The first milestone contains a platform-light physics module and an Android
sample that visualizes touch-driven surface deformation. See [PLAN.md](PLAN.md)
for the full roadmap.

## Build

Requirements: JDK 17 and Android SDK 35.

```shell
./gradlew test assembleDebug
```

Install the demo on a connected emulator or device:

```shell
./gradlew :sample-android:installDebug
adb shell am start -n \
  io.github.griffinkrutherford.liquidglass.sample/.MainActivity
```

## Project status

This project is an early proof of concept. APIs and visuals will change before
the first stable release.

## License

Apache License 2.0. See [LICENSE](LICENSE).

