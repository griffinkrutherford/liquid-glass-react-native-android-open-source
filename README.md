# Liquid Glass for Android

An experimental open source Android liquid-glass effect for React Native,
powered by a custom, deterministic membrane physics engine.

On Android 13 and newer, an AGSL runtime shader applies physics-driven
refraction, blur, chromatic dispersion, tint, and Fresnel-style edge optics to
real content captured from behind the glass. Older Android versions receive a
static translucent fallback; other platforms render a plain `View`. See
[docs/compatibility.md](docs/compatibility.md) for the exact contract.

> **Media placeholder.** This README should open with a before/after image and a
> short demo clip. Neither exists yet: both must be captured on real hardware,
> and nothing in this repository can produce them. The capture checklist —
> which screens, which devices, which caveats — is in
> [docs/media.md](docs/media.md).

## Status

Pre-1.0 and unstable. Prop names, units, defaults, and visual output can change
in any `0.x` release. Pin an exact version. See
[docs/compatibility.md](docs/compatibility.md) for the deprecation policy and
[LAUNCH_PLAN.md](LAUNCH_PLAN.md) for the road to a stable release.

## Documentation

| Document | Contents |
| --- | --- |
| [docs/props.md](docs/props.md) | Every prop: unit, default, valid range, clamping. |
| [docs/materials.md](docs/materials.md) | The `material` presets and how they merge with explicit props. |
| [docs/compatibility.md](docs/compatibility.md) | Supported React Native and Android ranges, the three rendering tiers. |
| [docs/recipes.md](docs/recipes.md) | Scene rules and worked examples: cards, navigation bars, buttons, floating controls. |
| [docs/performance.md](docs/performance.md) | What the renderer schedules, and the known limitations. |
| [docs/troubleshooting.md](docs/troubleshooting.md) | Autolinking, codegen, Gradle, shader fallback, packaging. |
| [docs/expo.md](docs/expo.md) | Expo development builds. Unverified. |
| [docs/accessibility.md](docs/accessibility.md) | Touch, focus, screen readers, and the reduced-motion gap. |

## React Native installation

Requires React Native 0.76 or newer. Both the New and Old Architecture are
supported. Android only for the glass; every other platform gets a `View`
fallback, so the same tree is safe to render everywhere.

### From npm

**Not yet published.** Once a release is on the registry this is the supported
path, and it installs exactly the artifact the packaging test verifies:

```shell
npm install @griffinkrutherford/liquid-glass-android
npx react-native run-android
```

### From a packed tarball

Until then, build the tarball and install it. This is the path the example app
and `npm run test:pack` exercise, so it is the one known to work:

```shell
git clone https://github.com/griffinkrutherford/liquid-glass-react-native-android-open-source.git
cd liquid-glass-react-native-android-open-source
npm ci
npm run build
npm pack            # writes griffinkrutherford-liquid-glass-android-<version>.tgz
```

Then, from your app:

```shell
npm install /path/to/griffinkrutherford-liquid-glass-android-<version>.tgz
npx react-native run-android
```

Building the tarball needs Node only. Building your app needs JDK 17 and the
Android SDK.

### From a Git dependency

**Unverified.** `npm install github:...` runs the package's `prepare` script,
which runs `bob build` inside your `node_modules`. That makes your install
depend on the library's build toolchain resolving correctly in your tree, and it
is not what the example app or the packaging test exercises. Prefer a tarball.
If you do it anyway, pin a tag and never a branch:

```shell
npm install github:griffinkrutherford/liquid-glass-react-native-android-open-source#v0.1.0
```

### One-time app setup

Installing is a one-time step. Commit the manifest and the lockfile so teammates
and CI resolve the same release, then create one native Android build:

```shell
git add package.json package-lock.json
git commit -m "Add Android liquid glass"
npx react-native run-android
```

After that, ordinary TypeScript and component changes use Fast Refresh, and
restarting the app does not reinstall anything. A fresh checkout only needs
`npm ci`. Rebuild Android when the liquid-glass version changes, because an
update may contain Kotlin, shader, or Fabric codegen changes. For Expo, make a
new development build after any native dependency update — see
[docs/expo.md](docs/expo.md).

## Usage

Put the background content and the glass inside one `LiquidGlassScene`. Glass
elements sample the non-glass children that appear behind them.

```tsx
import {Image, StyleSheet, Text} from 'react-native';
import {
  LiquidGlassScene,
  LiquidGlassView,
  isLiquidGlassSupported,
} from '@griffinkrutherford/liquid-glass-android';

export function GlassScreen() {
  return (
    <LiquidGlassScene style={styles.scene}>
      <Image source={require('./background.jpg')} style={StyleSheet.absoluteFill} />
      <LiquidGlassView material="crystal" cornerRadius={28} style={styles.card}>
        <Text style={styles.title}>Physical glass</Text>
      </LiquidGlassView>
    </LiquidGlassScene>
  );
}

const styles = StyleSheet.create({
  scene: {flex: 1},
  card: {position: 'absolute', left: 24, right: 24, top: 220, height: 212, padding: 24},
  title: {color: 'white', fontSize: 24},
});
```

`material` is the recommended entry point: `crystal`, `satin`, or `nocturne`.
Every optical value a preset supplies can still be overridden per prop — an
explicit prop always wins. Presets cover optics only, never geometry, behaviour,
motion, or colour scheme. See [docs/materials.md](docs/materials.md).

To keep background lines from bending beneath an overlapping button while preserving one
continuous glass surface, add a feathered shader-level exclusion:

```tsx
<LiquidGlassView
  refractionExclusion={{
    shape: 'circle',
    centerX: 0.5,
    centerY: 0.5,
    radius: 44,
    feather: 8,
  }}
/>
```

This disables only displaced optics in the circle. Blur, tint, frost, borders and highlights
remain continuous; no extra view or backdrop capture is created.

The full prop list, with units, defaults, and ranges, is in
[docs/props.md](docs/props.md). Dimension props are React Native
density-independent units (dp); `indexOfRefraction`, `dispersion`,
`effectAmount`, and `tintAmount` are dimensionless; `animationDuration` is
milliseconds.

`isLiquidGlassSupported` is `true` on Android API 33+. It is a
rendering-quality check, not an availability check: `LiquidGlassView` is always
safe to render and always lays out correctly, on every platform.

More examples, including a real app that installs the packed tarball the way an
external consumer does, are in
[`example-react-native/`](example-react-native/README.md).

## Native Android usage

The React Native bridge is a thin wrapper over an Android library that can be
used directly. Place ordinary views and the glass overlay inside a
`LiquidGlassScene`:

```kotlin
val scene = LiquidGlassScene(context)
scene.addView(contentBehindTheGlass)
scene.addView(LiquidGlassView(context).apply {
    applyMaterial(LiquidGlassMaterial.SATIN)
    interactive = true
    draggable = true // Optional; useful for demos and floating controls
    colorScheme = LiquidGlassColorScheme.SYSTEM
    blurRadius = 8f * resources.displayMetrics.density // per-property override
})
```

`LiquidGlassMaterial` holds the same three preset bundles the React Native
`material` prop resolves to, so an Android-only integration and a React Native
integration produce identical glass. Native properties are in pixels, not dp.

## How it works

The scene renders non-glass children into a shared offscreen bitmap without a
CPU pixel readback, and re-captures only when that content actually changes.
Each `LiquidGlassView` samples that bitmap plus a small height texture generated
by the membrane simulation.

The optical path uses a rounded-rectangle signed-distance field to construct a
half-circle bevel height profile. Its numerical gradient becomes a 3D surface
normal; glass thickness and index of refraction then determine separate red,
green, and blue ray offsets. A Schlick Fresnel term blends a backdrop-derived
internal reflection at grazing angles. The physics height gradient is combined
with the bevel gradient, so interaction modifies the same optical surface rather
than adding a separate animation.

`LiquidGlassView` is a `ViewGroup`, so labels, icons, and other controls can be
placed inside it.

The biconvex bevel and wavelength-dependent refraction model was informed by the
open source WebGL work in
[`ybouane/liquidglass`](https://github.com/ybouane/liquidglass), then adapted
independently for Android AGSL and this project's membrane engine. The material
API follows the useful cross-platform concepts from
[`@callstack/liquid-glass`](https://github.com/callstack/liquid-glass): tint,
light/dark/system appearance, animated material changes, plus optional
interaction that affects only the refraction physics. Callstack's iOS package
wraps Apple's native `UIGlassEffect`; this project implements the observable
behaviour independently with Android rendering and custom physics.

See [PLAN.md](PLAN.md) for the rendering roadmap.

## Build from source

Requirements: JDK 17 and Android SDK 35.

```shell
npm ci
npm run typecheck
npm run build
./gradlew test assembleDebug
```

Install the native demo on a connected emulator or device:

```shell
./gradlew :sample-android:installDebug
adb shell am start -n \
  io.github.griffinkrutherford.liquidglass.sample/.MainActivity
```

[CONTRIBUTING.md](CONTRIBUTING.md) has the full local setup, the repository
layout, and the validation commands CI runs.

## Automatic dependency updates

Pin production apps to an exact version rather than a branch. Commit the
lockfile and let Dependabot open tested update pull requests:

```yaml
# .github/dependabot.yml in the consuming app
version: 2
updates:
  - package-ecosystem: npm
    directory: /
    schedule:
      interval: weekly
```

For Git-tag installations, Renovate can update the pinned tag. Avoid a floating
Git branch: an unreviewed native change could break your app's build.

## Contributing, security, and conduct

- [CONTRIBUTING.md](CONTRIBUTING.md) — local setup, repository layout, and the
  checks to run before opening a pull request.
- [SECURITY.md](SECURITY.md) — how to report a vulnerability privately. Do not
  open a public issue for one.
- [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) — expected conduct and how to report
  a violation.

Bug reports are most useful with the information listed under "Reporting a
problem" in [docs/troubleshooting.md](docs/troubleshooting.md).

## License

Apache License 2.0. See [LICENSE](LICENSE).
