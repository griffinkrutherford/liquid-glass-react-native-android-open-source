# Liquid Glass example app

A React Native 0.81 Android app that consumes
`@griffinkrutherford/liquid-glass-android` **the way an external consumer does**:
as a dependency on the packed npm tarball, resolved out of `node_modules` by
Metro and Android autolinking.

There is no workspace link, no `watchFolders`, and no `extraNodeModules`
aliasing. If the library's `files` allowlist, `react-native.config.js`,
`codegenConfig`, or Kotlin sources are broken, this app breaks.

This is the Phase 1 verification harness from [`../LAUNCH_PLAN.md`](../LAUNCH_PLAN.md),
not a showcase.

---

## Prerequisites

| Step | Needs Node | Needs JDK 17 | Needs Android SDK | Needs a device/emulator |
| --- | :-: | :-: | :-: | :-: |
| Install dependencies | yes | no | no | no |
| Typecheck | yes | no | no | no |
| Metro bundle check | yes | no | no | no |
| `npm run android` | yes | **yes** | **yes** | **yes** |
| `npm run android:release` | yes | **yes** | **yes** | **yes** |

- Node >= 20 (developed against Node 26 / npm 11).
- JDK 17 on `PATH` and `JAVA_HOME` set (Android Gradle Plugin 8.x requirement).
- Android SDK with `compileSdk 36`, `buildTools 36.0.0`, and NDK `27.1.12297006`
  installed, and `ANDROID_HOME` set.

The Node-only steps are the ones that run in a JDK-free environment; everything
in the Gradle column must be validated in CI or on a machine with the SDK.

---

## From zero, in order

Run every command from the **repository root** unless stated otherwise.

```shell
# 1. Clone and install the library's own toolchain.
git clone https://github.com/griffinkrutherford/liquid-glass-react-native-android-open-source.git
cd liquid-glass-react-native-android-open-source
npm ci

# 2. Sanity-check the library itself. (Node only.)
npm run typecheck
npm run build

# 3. Prove the published artifact is consumable at all. (Node only.)
#    Builds, packs, installs into a clean throwaway app in a temp dir, and
#    asserts every Android source and autolinking file survived packing.
npm run test:pack

# 4. Build, pack, and install the tarball into this example app. (Node only.)
#    This is the ONLY supported way to refresh the example's copy of the
#    library. It rewrites the example's dependency to the tarball it just made.
npm run example:install

# 5. Typecheck and bundle the example against the installed tarball. (Node only.)
npm run example:typecheck
npm run example:bundle

# 6. Build and install the debug APK.  ***Requires JDK 17 + Android SDK + a
#    running emulator or connected device.***
npm run example:android

# 7. Optional: release variant. Same requirements as step 6.
npm run example:android:release
```

Metro is started automatically by `npm run example:android`. To run it manually
in a second terminal:

```shell
npm run example:start
```

### Re-running after a library change

Steps 4 onward. A change to the library's JavaScript, Kotlin, or codegen spec is
**not** picked up by Fast Refresh, because the example consumes an installed
tarball rather than the source tree:

```shell
npm run example:install    # rebuild + repack + reinstall
npm run example:android    # rebuild the native app  (JDK + SDK)
```

Changes to files inside `example-react-native/` itself *are* picked up by Fast
Refresh with Metro running.

### Working directly inside the example

```shell
cd example-react-native
npm run android
npm run start
npm run typecheck
```

---

## Testing the Old Architecture

The app defaults to the New Architecture (Fabric). The toggle is one line in
[`android/gradle.properties`](android/gradle.properties):

```properties
newArchEnabled=true     # New Architecture (Fabric) — the default
newArchEnabled=false    # Old Architecture (legacy ViewManager path)
```

Changing it alters generated code, so a clean rebuild is required:

```shell
cd example-react-native/android
./gradlew clean
cd ..
npm run android
```

Or, without editing the file:

```shell
cd example-react-native/android
./gradlew clean assembleDebug -PnewArchEnabled=false
```

Both require JDK 17 and the Android SDK.

---

## What each screen verifies

Navigate with the buttons on the first screen. Every screen is a separate
React Navigation native-stack route, so simply moving between them exercises
mount, unmount, and return.

| Screen | Phase 1 deliverable |
| --- | --- |
| **Nested content** (`src/screens/HomeScreen.tsx`) | Text, an `Image`, an icon-sized `Image`, a `Pressable` button, and nested `View` rows rendered *inside* one `LiquidGlassView`. The in-glass tap counter proves nested controls stay touchable. Also displays `isLiquidGlassSupported`. |
| **Multiple glass** (`src/screens/MultiGlassScreen.tsx`) | Four `LiquidGlassView`s sharing one `LiquidGlassScene`, one per effect (`clear`, `satin`, `nocturne`, `regular`), plus an absolutely positioned draggable bar overlapping them. |
| **ScrollView** (`src/screens/ScrollScreen.tsx`) | A pinned glass header and a floating glass control over a scrolling `ScrollView`. The backdrop must track the scroll instead of freezing. |
| **FlatList** (`src/screens/ListScreen.tsx`) | Glass over a virtualized `FlatList`, with glass cells *inside* the list so recycling repeatedly attaches and detaches the native view. |
| **Lifecycle** (`src/screens/LifecycleScreen.tsx`) | In-place remount, unmount/mount, and add/remove of glass views, on top of the navigation churn. Repeat with **Go back** and re-entry to check for crashes, stale backdrops, or growing memory. |

`src/Backdrop.tsx` is the shared non-glass scene content. It is deliberately
high-contrast: refraction and chromatic dispersion are invisible against a flat
background.

### Manual checks that need a device

These cannot be automated here and are the reason the app exists:

1. **Fast Refresh** — edit any `src/screens/*.tsx` file with Metro running and
   confirm the glass survives the refresh.
2. **JS reload** — press `r` in the Metro terminal (or shake > Reload).
3. **App restart** — kill and relaunch the app from the launcher.
4. **Navigate away and back** — no crash, no stale or frozen backdrop.
5. **Rotation and background/foreground** — backdrop buffers resize and are
   released.
6. **Old Architecture** — repeat 1-5 with `newArchEnabled=false`.

---

## Notes and known constraints

- **The example's `package-lock.json` is not committed.** It would pin the
  integrity hash of a locally packed tarball, which differs on every machine and
  every re-pack. Run `npm run example:install` instead of `npm ci` here.
- **Packed tarballs (`*.tgz`) are gitignored.** `npm run example:install`
  deletes stale ones before packing so the example can never silently depend on
  an old build.
- **Navigation dependencies are pinned with `~` to the React Native 0.81 era.**
  Newer `react-native-screens` releases target a newer `@react-native/codegen`
  (`React.ComponentRef` rather than `React.ElementRef`) and fail to bundle on
  0.81. Bump them together with React Native, not independently.
- The debug keystore signs the release variant too, so `assembleRelease` can be
  exercised without provisioning secrets. Never ship that configuration.
- iOS is intentionally absent. The library is Android-only and renders a plain
  `View` elsewhere.
