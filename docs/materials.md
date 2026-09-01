# Materials

`material` is the recommended entry point. It selects a matched bundle of
optical parameters instead of requiring ten numbers to be tuned together.

```tsx
<LiquidGlassView material="crystal" />
<LiquidGlassView material="satin" />
<LiquidGlassView material="nocturne" />
```

## What each preset resolves to

| | `crystal` | `satin` | `nocturne` |
| --- | --- | --- | --- |
| `effect` | `clear` | `satin` | `nocturne` |
| `refractionStrength` (dp) | `34` | `14` | `20` |
| `dispersion` | `4.2` | `1.2` | `2` |
| `indexOfRefraction` | `1.52` | `1.42` | `1.49` |
| `bevelDepth` (dp) | `18` | `26` | `24` |
| `thickness` (dp) | `8` | `10` | `12` |
| `blurRadius` (dp) | `0.8` | `6.5` | `3.4` |
| `effectAmount` | `1` | `0.92` | `0.98` |
| `tintColor` | `#FFFFFF` | `#E8F2FF` | `#5A6B85` |
| `tintAmount` | `0.03` | `0.22` | `0.3` |

- `crystal` — near-colourless, highly transmissive: strong refraction, visible
  chromatic dispersion, crisp edges, almost no tint.
- `satin` — frosted and diffusing: soft blur, reduced refraction, cool milky
  tint.
- `nocturne` — smoked dark glass: moderate refraction, thick slab, strong slate
  tint.

The table is exported at runtime, so a variation can be derived from a preset
rather than transcribed:

```tsx
import {LIQUID_GLASS_MATERIALS} from '@griffinkrutherford/liquid-glass-android';

const {blurRadius} = LIQUID_GLASS_MATERIALS.satin;

<LiquidGlassView material="satin" blurRadius={blurRadius * 1.5} />;
```

The same bundles exist natively as the `LiquidGlassMaterial` enum with
`applyMaterial()`, so an Android-only integration and a React Native
integration produce identical glass.

## Merge semantics

Precedence, highest first:

1. an explicitly supplied prop,
2. the `material` preset's value for that prop,
3. `LIQUID_GLASS_DEFAULTS`.

`undefined` means "not supplied" and falls through to the next level. Passing
`blurRadius={undefined}` is identical to omitting it.

```tsx
// satin everywhere, except a crisper blur.
<LiquidGlassView material="satin" blurRadius={2} />

// no preset: every optical prop comes from LIQUID_GLASS_DEFAULTS.
<LiquidGlassView blurRadius={2} />
```

### Presets cover optics only

A preset supplies exactly the ten optical props in the table above. It never
supplies:

- geometry — `cornerRadius`
- behaviour — `interactive`, `draggable`
- motion — `animated`, `animationDuration`
- `colorScheme`

Those always come from an explicit prop or from `LIQUID_GLASS_DEFAULTS`.
Switching `material` therefore never moves, re-shapes, or changes the
interaction behaviour of a view.

### An unknown material name

An unrecognised `material` resolves to no preset at all: every prop the caller
did not supply falls back to `LIQUID_GLASS_DEFAULTS`. In `__DEV__` this logs a
one-time warning. It does not throw.

## Resolution happens in JavaScript

`resolveLiquidGlassProps` runs in the React component, and the native view
receives a fully resolved, concrete value for every prop. There is deliberately
no `material` prop in the Fabric spec.

This is forced by Fabric. Under the New Architecture every prop arrives natively
carrying its declared default value, whether or not the caller supplied it.
Native code therefore cannot distinguish "not supplied" from "supplied as the
default", which is exactly the distinction the merge rules depend on. The
resolver is exported so the behaviour is testable and inspectable:

```tsx
import {resolveLiquidGlassProps} from '@griffinkrutherford/liquid-glass-android';

resolveLiquidGlassProps({material: 'satin', blurRadius: 2}).blurRadius; // 2
resolveLiquidGlassProps({material: 'satin'}).blurRadius;               // 6.5
resolveLiquidGlassProps({}).blurRadius;                                // 2.2
```

## `effect` is soft-deprecated

`effect` is the low-level shader variant. It remains supported for the whole
`0.x` series and is only removed after a full deprecation period; see
[compatibility.md](compatibility.md).

Prefer `material`. Reach for `effect` only when you want a variant without
adopting a preset's optical bundle — for example the `regular` variant with
otherwise default numbers, which no preset selects.

Supplying both is allowed. `effect` wins for the shader variant, like any other
explicitly supplied prop, and `__DEV__` logs a one-time warning:

```tsx
// crystal's optics, but the satin shader variant.
<LiquidGlassView material="crystal" effect="satin" />
```

Migration:

| Was | Now |
| --- | --- |
| `effect="clear"` with tuned optics | `material="crystal"` |
| `effect="satin"` with tuned optics | `material="satin"` |
| `effect="nocturne"` with tuned optics | `material="nocturne"` |
| `effect="regular"` | keep `effect="regular"`; no preset selects it |
| `effect="none"` | keep `effect="none"`; it is an off switch, not a material |
