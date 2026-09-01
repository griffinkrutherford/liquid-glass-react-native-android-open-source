# Expo development builds

> **Unverified.** No Expo project has been built against this library. The steps
> below follow from how the package is put together — a standard Android
> autolinked native module with a Fabric spec — and from Expo's documented
> development-build workflow. They have not been run. If you try this, a report
> either way is useful: [bug report
> template](../.github/ISSUE_TEMPLATE/bug_report.yml).

## Expo Go will not work

Expo Go ships a fixed set of native modules. It cannot load a third-party native
library, and this package is one. There is no configuration, no plugin, and no
flag that changes that. You need a **development build**: your own binary,
containing your own native dependencies, that the Expo dev server connects to.

Symptom if you try anyway: the component renders as an unimplemented native
view, or the app errors on the first render of `LiquidGlassView`.

## Setup

```shell
npx expo install expo-dev-client
npm install @griffinkrutherford/liquid-glass-android
```

The package requires no Expo config plugin. It has no `AndroidManifest`
entries, no permissions, no Gradle properties of its own, and autolinks through
its shipped `react-native.config.js`.

Build locally, which requires JDK 17 and the Android SDK:

```shell
npx expo run:android
```

Or with EAS, which does not:

```shell
eas build --profile development --platform android
```

Then start the dev server and open the build:

```shell
npx expo start --dev-client
```

## Rebuild after any native dependency change

A development build embeds native code. Installing, upgrading, or removing this
package — or any other native dependency — requires a **new development
build**. Fast Refresh and `expo start` will not pick it up, and an old dev build
against a new JavaScript bundle fails at the native boundary rather than
cleanly.

In particular, upgrading `@griffinkrutherford/liquid-glass-android` may change
Kotlin sources, the AGSL shader, or the Fabric codegen spec. Always rebuild.

## Things to check first if it does not work

- **`newArchEnabled`.** Recent Expo SDKs default to the New Architecture. Both
  architectures are supported; if a build fails, try the other one to isolate
  the problem. See [troubleshooting.md](troubleshooting.md).
- **`compileSdk` and Kotlin version.** Expo pins these through
  `expo-build-properties`. The library reads `compileSdkVersion`,
  `minSdkVersion`, `targetSdkVersion`, and `kotlinVersion` from the root
  project, so Expo's values win. Its own floors are `minSdk` 23 and Kotlin
  2.0.21.
- **Prebuild.** If you use continuous native generation, `npx expo prebuild
  --clean` regenerates `android/` and re-runs autolinking.
- **React Native version.** The peer range is `>= 0.76`; only 0.81 is exercised.
  Check which React Native version your Expo SDK ships.
