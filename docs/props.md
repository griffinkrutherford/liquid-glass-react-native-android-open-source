# Props reference

`LiquidGlassView` accepts every React Native `View` prop plus the glass props
below. `LiquidGlassScene` accepts `View` props only; it has no glass props of
its own.

## Units

| Kind | Props | Unit |
| --- | --- | --- |
| Dimension | `cornerRadius`, `refractionStrength`, `bevelDepth`, `thickness`, `blurRadius` | React Native density-independent units (dp). Converted with `PixelUtil.toPixelFromDIP` in `LiquidGlassViewManager`. |
| Time | `animationDuration` | Milliseconds. |
| Dimensionless | `indexOfRefraction`, `dispersion`, `effectAmount`, `tintAmount` | No unit, no density conversion. |
| Colour | `tintColor` | Any React Native colour value. |
| Enum / boolean | `material`, `effect`, `colorScheme`, `interactive`, `draggable`, `animated` | — |

`dispersion` is dimensionless on purpose: the chromatic split it produces
already scales with the dp-based refraction geometry, so converting it a second
time would make the same JavaScript value look different on every screen
density.

## Optical props

| Prop | Type | Unit | Default | Range |
| --- | --- | --- | --- | --- |
| `material` | `'crystal' \| 'satin' \| 'nocturne'` | — | `undefined` (no preset) | see [materials.md](materials.md) |
| `effect` | `'clear' \| 'regular' \| 'satin' \| 'nocturne' \| 'none'` | — | `'regular'` | — (deprecated, see below) |
| `refractionStrength` | `number` | dp | `24` | `0`–`80` |
| `dispersion` | `number` | dimensionless | `2.4` | `0`–`12` |
| `indexOfRefraction` | `number` | dimensionless | `1.47` | `1.01`–`3` |
| `bevelDepth` | `number` | dp | `22` | `2`–`48` |
| `thickness` | `number` | dp | `6` | `0`–`64` |
| `blurRadius` | `number` | dp | `2.2` | `0`–`12` |
| `effectAmount` | `number` | dimensionless | `0.96` | `0`–`1` |
| `tintColor` | `ColorValue` | — | `'#BEE5FF'` | any colour |
| `tintAmount` | `number` | dimensionless | `0.11` | `0`–`1` |

## Geometry, behaviour, and motion props

| Prop | Type | Unit | Default | Range |
| --- | --- | --- | --- | --- |
| `cornerRadius` | `number` | dp | `32` | `>= 0` |
| `interactive` | `boolean` | — | `false` | — |
| `draggable` | `boolean` | — | `false` | — |
| `animated` | `boolean` | — | `true` | — |
| `animationDuration` | `number` | ms | `320` | `>= 0` |
| `colorScheme` | `'light' \| 'dark' \| 'system'` | — | `'system'` | — |

These are never supplied by a `material` preset. Setting `material` cannot move
or re-shape a view.

The defaults are exported at runtime as `LIQUID_GLASS_DEFAULTS`, so a value in
the tables above can be read rather than copied:

```tsx
import {LIQUID_GLASS_DEFAULTS} from '@griffinkrutherford/liquid-glass-android';

console.log(LIQUID_GLASS_DEFAULTS.blurRadius); // 2.2
```

## Clamping

Out-of-range values are not an error. Native `LiquidGlassView` clamps every
numeric property in its setter, so an out-of-range value is silently corrected
rather than dropped or thrown. In `__DEV__` builds JavaScript logs a one-time
`console.warn` per prop so the clamp is visible; release bundles strip the
check.

Two clamps deserve attention:

- **`bevelDepth` has a non-zero minimum.** `0` is clamped to `2` dp, so "no
  bevel at all" is not expressible. Use a small `refractionStrength` or
  `effectAmount` instead if you want a flat surface.
- **`bevelDepth` is capped again inside the shader** at 24% of the view's
  shorter side. On a 60 dp-tall pill, a `bevelDepth` above ~14 dp has no
  additional effect.

`cornerRadius` is likewise capped inside the shader at half the shorter side,
so any value at or above that produces a fully rounded pill.

## Per-prop notes

### `effect`

Selects the low-level shader variant: it changes the regularity, frostiness,
darkness and materialization terms, and nothing else. No numeric optical
parameter changes with `effect`.

- `clear` — least regular, sharpest refraction.
- `regular` — the default balanced variant.
- `satin` — multiplies the effective blur internally for a frosted look.
- `nocturne` — darkens and cools the transmitted colour.
- `none` — animates the material out entirely. Neither the glass fill nor the
  border is drawn; children still render and still lay out.

`effect` is soft-deprecated in favour of `material`. See
[materials.md](materials.md).

Changing `effect` (including the implicit change on first mount when a preset
selects a variant other than `regular`) runs the material transition described
under `animated`.

### `refractionStrength`

The maximum displacement applied to a refracted backdrop sample. It is an upper
bound, not a multiplier: the shader computes a physically derived offset from
`thickness`, `indexOfRefraction` and the surface slope, then limits its
magnitude by `refractionStrength`.

### `indexOfRefraction`

The physical index. `1.0` is vacuum, ~`1.33` water, ~`1.5` common glass, ~`2.4`
diamond. The lower bound of `1.01` exists because `1.0` produces no bending at
all and divides out of the transmitted-ray computation.

### `thickness`

Optical path length. It scales how far a refracted ray travels inside the
material before it exits, so it interacts with `indexOfRefraction`: raising
either one bends the backdrop further, up to the `refractionStrength` limit.

### `effectAmount`

Blends between the untouched backdrop (`0`) and the fully refracted, tinted
result (`1`). It is the cheapest way to soften the whole material without
retuning individual parameters.

### `interactive`

When `false` the view returns `false` from `ACTION_DOWN`, so it never claims a
touch. When `true` it consumes touches that its children did not handle and
calls `performClick()` on release if the touch was not a drag. A stationary tap
does not deform the membrane. Pointer movement applies impulses along the
gesture path; with `draggable`, the same movement also translates the view.

Children are dispatched touches first in either case: `LiquidGlassView` does not
override `onInterceptTouchEvent`, so nested buttons and inputs stay touchable.
See [accessibility.md](accessibility.md).

### `draggable`

Moves the view with `translationX`/`translationY` inside its parent, clamped to
the parent's bounds. Requires `interactive`, because without it the view never
receives the `ACTION_DOWN` that starts the drag. The translation is accounted
for when the view computes where to sample the backdrop, so a dragged surface
refracts the content it is currently over.

`draggable` moves the native view directly. React Native's layout is unchanged,
so the position is lost on re-layout and is not readable from JavaScript. It is
intended for demos and floating controls, not for persistent positioning.

### `animated` and `animationDuration`

Control only the transition between shader variants when `effect` (or the
variant implied by `material`) changes. They do not affect the membrane physics,
which always run at their own fixed timestep.

`animated={false}`, or `animationDuration={0}`, applies the new variant on the
next frame instead.

Because JavaScript always sends a concrete `effect`, mounting a view with
`material="crystal"` sends `effect="clear"`, which differs from the native
default of `regular` and therefore animates in over `animationDuration`. Pass
`animated={false}` if you need the material to be correct on the first frame.

### `colorScheme`

Selects the sign of the internal light-response term. `system` reads the Android
UI night mode. It is not a full theme: it lifts or lowers the transmitted
luminance and nothing else.

### `tintColor` and `tintAmount`

`tintColor` is mixed into the transmitted light in proportion to `tintAmount`.
The alpha channel of `tintColor` is ignored — `tintAmount` is the strength
control. `rgba(220, 242, 255, 0.08)` and `#DCF2FF` behave identically.

## Development warnings

In `__DEV__` builds `LiquidGlassView` warns once per issue:

- a numeric prop outside its documented range, with the range in the message;
- a numeric prop that is not a number;
- an unknown `material` name;
- `material` and `effect` supplied together;
- a `LiquidGlassView` rendered outside a `LiquidGlassScene`.

Every warning is a `console.warn`. Nothing throws, and nothing warns in release
builds.
