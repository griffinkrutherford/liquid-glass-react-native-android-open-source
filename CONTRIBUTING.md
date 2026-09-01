# Contributing

Thanks for your interest in this project. It is an early prototype, so the most
useful contributions are reproducible bug reports, device coverage, and small
focused fixes.

## Project status

The package is pre-1.0. The public API, prop names, units, defaults, and visual
output can change in any release. Treat every `0.x` version as unstable. If you
depend on this library, pin an exact version. See [LAUNCH_PLAN.md](LAUNCH_PLAN.md)
for the road to a stable release and [PLAN.md](PLAN.md) for the rendering
roadmap.

## Prerequisites

- JDK 17. Newer JDKs are not supported by the Gradle and Android Gradle Plugin
  versions used here.
- Android SDK with platform 35 installed, and `ANDROID_HOME` (or
  `ANDROID_SDK_ROOT`) set. `local.properties` with `sdk.dir=...` also works.
- Node.js 22 or newer, with npm. CI uses Node 22.
- A device or emulator running Android 13 (API 33) or newer to see the AGSL
  shader path. API 23 to API 32 devices exercise the static translucent
  fallback, which is also worth testing.

## Repository layout

| Path | Contents |
| --- | --- |
| `liquid-glass-core/` | Pure-Kotlin membrane physics. JVM module, no Android dependencies, unit tested. |
| `liquid-glass-view/` | Android library: `LiquidGlassScene`, `LiquidGlassView`, styles, and the AGSL shader. |
| `android/` | React Native Fabric bridge (view managers and package). Compiles `liquid-glass-core` and `liquid-glass-view` sources into one Android library for consumers. |
| `src/` | TypeScript surface and Fabric codegen specs (`LiquidGlassNativeComponent.ts`, `LiquidGlassSceneNativeComponent.ts`). |
| `sample-android/` | Native Android demo app. No React Native involved. |
| `example-react-native/` | React Native example that installs the packed tarball like an external consumer. |
| `gradle/react-native-validation.gradle` | Init script that lets `:liquid-glass-view` compile the bridge sources against React Native without a full app. |

`android/build.gradle` is a separate Gradle build used by consuming React Native
apps through autolinking. The root `settings.gradle.kts` build (`:liquid-glass-core`,
`:liquid-glass-view`, `:sample-android`) is what you use for local development.

## Setup

```shell
npm ci
```

Gradle uses the wrapper, so no separate Gradle install is needed.

## Validation

Run the same commands CI runs (`.github/workflows/ci.yml`) before opening a
pull request:

```shell
npm run typecheck
npm run build
npm run android:bridge-check
npm test -- --runInBand
npm run test:pack
npm run example:install
npm run example:typecheck
npm run example:bundle
./example-react-native/android/gradlew -p example-react-native/android \
  assembleDebug assembleRelease -PnewArchEnabled=true -PreactNativeArchitectures=x86_64
./gradlew test lintDebug :sample-android:assembleDebug
```

What each one covers:

- `npm run typecheck` — `tsc --noEmit` over `src/`.
- `npm run build` — `react-native-builder-bob` build of the CommonJS, ESM,
  TypeScript declaration, and codegen targets into `lib/`.
- `npm run android:bridge-check` — regenerates Fabric codegen and compiles the
  Kotlin view managers in `android/src` against React Native. This is the check
  that catches a TypeScript spec and Kotlin manager drifting apart.
- `npm test -- --runInBand` — JavaScript preset, warning, fallback, and public
  component tests.
- `npm run test:pack` — installs the npm tarball into a clean temporary fixture
  and audits the files, entry points, autolinking metadata, and codegen config.
- `npm run example:install`, `example:typecheck`, `example:bundle`, and the
  example Gradle build — prove that an external-style app can consume the
  packed tarball, generate its own Fabric bindings, and assemble debug and
  release APKs. CI also assembles a legacy-architecture debug APK.
- `./gradlew test lintDebug :sample-android:assembleDebug` — physics unit tests,
  Android lint, and a full debug build of the native sample.

If you only changed Kotlin physics, `./gradlew :liquid-glass-core:test` is a
fast inner loop. Run the full set before pushing.

## Codegen

`src/LiquidGlassNativeComponent.ts` and `src/LiquidGlassSceneNativeComponent.ts`
are Fabric component specs. React Native's codegen turns them into the
`RNLiquidGlassSpec` Java delegate and interface consumed by
`android/src/main/java/io/github/griffinkrutherford/liquidglass/react/`.

Re-run codegen whenever you:

- add, remove, or rename a prop on either native component,
- change a prop's type, or
- change `codegenConfig` in `package.json`.

```shell
npm run android:bridge-check
```

That script runs `bob build --target codegen` and then compiles against the
generated sources, so it both regenerates and verifies. Generated output lands
under `android/app/build/generated/` and is not committed. If a Kotlin view
manager no longer satisfies the generated interface, this is where it fails.

Consuming apps regenerate codegen during their own Android build, so a prop
change is a native change: app developers must rebuild, not just reload JS.

## Running the native sample

The fastest way to see a rendering change. It does not involve React Native.

```shell
./gradlew :sample-android:installDebug
adb shell am start -n \
  io.github.griffinkrutherford.liquidglass.sample/.MainActivity
```

Drag the card to excite the membrane physics. See
[sample-android/README.md](sample-android/README.md).

## Running the React Native example

`example-react-native/` installs the library the way an external consumer does,
from a packed tarball rather than a workspace link. Its dependency is
`file:../griffinkrutherford-liquid-glass-android-0.1.0.tgz`, so build and pack
the library at the repository root first:

```shell
npm run build
npm pack --ignore-scripts
cd example-react-native
npm install
npm run android
```

Repack and reinstall after any library change; the example does not pick up
edits automatically.

The example defaults to the New Architecture. Its
`example-react-native/android/gradle.properties` documents how to flip
`newArchEnabled` for an Old Architecture run, which needs a clean rebuild
because the flag changes generated code. Verify both before changing anything
in the bridge.

Expo projects need a development build; Expo Go cannot load native modules.

## Changes that need a native rebuild

Reloading JavaScript is enough only for changes confined to `src/` that do not
touch a component spec. Everything below requires a fresh Android build in any
app or example that consumes the library:

- Kotlin in `liquid-glass-core/`, `liquid-glass-view/`, or `android/`
- the AGSL shader source
- prop additions or type changes in the Fabric specs
- Gradle configuration, `minSdk`/`compileSdk`, or dependency changes

## Commits and pull requests

- Write commit subjects in the imperative mood, under about 72 characters, with
  no trailing period and no emoji. Match the existing history
  (`git log --oneline`).
- Keep a pull request to one logical change. Split unrelated refactors out.
- Explain the observable behavior change, not just the diff.
- Include before and after screenshots or a short screen recording for anything
  that alters rendering. This is a visual library and a diff alone cannot show a
  regression in refraction, blur, tint, or edge optics.
- State which Android version and device you tested on, and whether the shader
  path or the pre-API-33 fallback was in use.
- Do not commit generated output: `lib/`, `**/build/`, `.gradle/`, or the
  packed `.tgz` produced by `npm pack`.
- CI must be green before review.

## Changesets and releases

Add a changeset for every user-visible fix, feature, or breaking change:

```shell
npm run changeset
```

Documentation, tests, and internal refactors that do not affect consumers may
use an empty changeset or explain in the pull request why none is needed. The
repository is pre-1.0, but use `patch` for compatible fixes and `minor` for new
features or intentional breaking changes during the beta period.

Maintainers prepare a release with:

```shell
npm run version-packages
npm ci
npm run typecheck
npm test -- --runInBand
npm run test:pack
```

Commit the generated version and changelog changes, tag that exact commit, and
push the tag. The release workflow independently installs the tarball into the
React Native example and assembles a release APK before npm publication. npm
publishing runs only when the repository's `NPM_TOKEN` secret is configured.

## Reporting bugs

Use the issue templates. A report without a version, an Android API level, and
either a reproduction repository or a self-contained snippet usually cannot be
acted on, because most failures here depend on the device GPU and the exact
scene composition.

Do not report a suspected vulnerability in a public issue. Follow
[SECURITY.md](SECURITY.md).

## Code of conduct

Participation is governed by [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

## License

By contributing you agree that your contributions are licensed under the
Apache License 2.0, matching [LICENSE](LICENSE).
