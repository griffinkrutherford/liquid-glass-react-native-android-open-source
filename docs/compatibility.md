# Compatibility policy

## React Native

| | Value |
| --- | --- |
| Declared peer range | `react-native >= 0.76`, `react >= 18` |
| Built and exercised against | React Native 0.81 (dev dependency and the example app) |
| Architectures | New Architecture (Fabric) and Old Architecture |

`>= 0.76` is the range the package declares, because 0.76 is the first release
where the Fabric codegen and autolinking shapes this package relies on are
stable. Only 0.81 is exercised end to end today: it is the version the example
app and the packaging test use. Versions between 0.76 and 0.81 are expected to
work and are not verified.

Both architectures are supported by the same Kotlin sources.
`LiquidGlassViewManager` is a `ViewGroupManager` that implements the generated
Fabric interface *and* carries `@ReactProp` annotations, so the New
Architecture uses the generated delegate and the Old Architecture uses the
annotations. See [troubleshooting.md](troubleshooting.md) for the toggle.

## Android

| | Value |
| --- | --- |
| `minSdk` | 23 (Android 6.0) |
| `compileSdk` | 35 |
| `targetSdk` | 35 |
| Kotlin | 2.0.21 |
| JVM target / JDK | 17 |

These are defaults. `android/build.gradle` reads them through `safeExtGet`, so a
consuming app that defines `minSdkVersion`, `compileSdkVersion`,
`targetSdkVersion`, or `kotlinVersion` in its root `build.gradle` `ext` block
overrides them. The example app builds the library at `compileSdk 36` and
`minSdk 24` this way.

## Rendering tiers

There are exactly three, and every one of them mounts and lays out correctly.

### Tier 1 — Android API 33+ (Android 13 and newer)

The full material. `RuntimeShader` exists, `LiquidGlassScene` allocates and
captures a backdrop bitmap, and the AGSL shader applies physics-driven
refraction, chromatic dispersion, blur, tint, and the Fresnel edge response.

This tier additionally requires a hardware-accelerated canvas and a captured
backdrop. A `LiquidGlassView` on API 33+ that has no `LiquidGlassScene`
ancestor, or that draws before its scene has captured anything, falls back to
the tier-2 rendering for those frames.

### Tier 2 — Android API 23–32 (Android 6.0 through 12L)

The same native view mounts, measures, lays out, dispatches touches, and hosts
children exactly as on tier 1. It draws a static translucent gradient plus the
edge border instead of the shader.

Consequences:

- Every optical prop — `refractionStrength`, `dispersion`,
  `indexOfRefraction`, `bevelDepth`, `thickness`, `blurRadius`,
  `effectAmount`, `tintColor`, `tintAmount` — has no visible effect. It is
  accepted, clamped, and stored, but nothing samples it.
- `cornerRadius` still applies: the fallback and border are drawn as a rounded
  rectangle.
- `effect` still applies partially. `none` still hides the material, and
  `clear` still draws at a lower alpha than the other variants.
- `LiquidGlassScene` allocates nothing and captures nothing. There is no
  backdrop bitmap and no capture pass.

### Tier 3 — every other platform

`LiquidGlassView` renders a plain `View`, and `LiquidGlassScene` renders a plain
`View`. Glass props are dropped, except `cornerRadius`, which is forwarded as
`borderRadius` so layout and clipping stay comparable. All standard `View`
props — `style`, accessibility props, pointer and layout handlers, `testID` —
are forwarded unchanged.

This is the iOS fallback contract, and it is the same on web and any other
React Native platform. Nothing about the component is Android-conditional in
your tree: the same JSX is valid everywhere.

## `isLiquidGlassSupported`

```tsx
import {
  isLiquidGlassSupported,
  LIQUID_GLASS_MIN_ANDROID_API, // 33
} from '@griffinkrutherford/liquid-glass-android';
```

`isLiquidGlassSupported` is `true` only on Android API 33 or newer.

It is a **rendering-quality check, not an availability check**. `false` never
means you must avoid the component: `LiquidGlassView` is always safe to render
and always lays out correctly. Use it to decide whether a design that *depends*
on refraction still reads, and to substitute a different treatment if not:

```tsx
<LiquidGlassView
  material="satin"
  style={[styles.card, isLiquidGlassSupported ? null : styles.solidCard]}>
  <Text>…</Text>
</LiquidGlassView>
```

It also does not promise the shader path will actually run on a given frame:
tier 1 additionally needs a hardware-accelerated canvas and a captured
backdrop, neither of which is knowable from JavaScript.

## API stability and deprecation

The package is pre-1.0. **Treat every `0.x` version as unstable.** Prop names,
units, defaults, clamps, and visual output can change in any release. Pin an
exact version, and read the release notes before upgrading.

From the first beta onward:

- A public prop that is going away is first marked deprecated in TypeScript
  with a `@deprecated` tag naming its replacement, and keeps working.
- A deprecated prop that is supplied logs a one-time `__DEV__` warning naming
  its replacement.
- The prop is only removed after a full deprecation period. `effect` is the
  current example: deprecated in favour of `material`, supported for the whole
  `0.x` series, removed no earlier than `1.0.0`.
- Defaults and clamp ranges are documented in [props.md](props.md) and are
  treated as part of the public API for deprecation purposes.

After `1.0.0` the public API follows semantic versioning: removals and
behavioural breaks only in a major release.

`LIQUID_GLASS_DEFAULTS`, `LIQUID_GLASS_MATERIALS`, `resolveLiquidGlassProps`,
`isLiquidGlassSupported`, and `LIQUID_GLASS_MIN_ANDROID_API` are public and
covered by the same policy. Anything not exported from the package root is
internal and may change without notice.
