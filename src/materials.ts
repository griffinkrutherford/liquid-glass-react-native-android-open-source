import type {ColorValue} from 'react-native';

/**
 * Low-level material variant. Controls the shader's regularity, frostiness, darkness and
 * materialization terms only; it does not change any numeric optical parameter.
 *
 * @deprecated Prefer the {@link LiquidGlassMaterial} presets exposed through the `material` prop.
 * `effect` stays supported through the `0.x` series and is only removed after a full
 * deprecation period; it remains the escape hatch for selecting a variant without adopting a
 * preset's optical bundle.
 */
export type LiquidGlassEffect = 'clear' | 'regular' | 'satin' | 'nocturne' | 'none';

/**
 * A named optical preset. Presets bundle a matched set of optical parameters that are known to
 * look correct together; individual parameters can still be overridden per prop.
 *
 * - `crystal` — near-colourless, highly transmissive glass: strong refraction, visible chromatic
 *   dispersion, crisp edges and almost no tint.
 * - `satin` — frosted, diffusing glass: soft blur, reduced refraction, cool milky tint.
 * - `nocturne` — smoked/dark glass: moderate refraction, thick slab, strong slate tint.
 */
export type LiquidGlassMaterial = 'crystal' | 'satin' | 'nocturne';

/** Appearance used for the internal light-response term. `system` follows the Android UI mode. */
export type LiquidGlassColorScheme = 'light' | 'dark' | 'system';

/**
 * The optical parameters a {@link LiquidGlassMaterial} preset supplies.
 *
 * Presets deliberately cover optics only. Geometry (`cornerRadius`), behaviour (`interactive`,
 * `draggable`), motion (`animated`, `animationDuration`) and `colorScheme` are never part of a
 * preset, so switching material never moves or re-shapes a view.
 */
export interface LiquidGlassOpticalBundle {
  /** Shader variant selected by this preset. */
  effect: LiquidGlassEffect;
  /** Maximum refracted sampling displacement, in dp. */
  refractionStrength: number;
  /** Chromatic dispersion amount (dimensionless). */
  dispersion: number;
  /** Index of refraction (dimensionless). */
  indexOfRefraction: number;
  /** Depth of the rounded edge bevel, in dp. */
  bevelDepth: number;
  /** Slab thickness of the glass, in dp. */
  thickness: number;
  /** Blur radius applied to the refracted sample, in dp. */
  blurRadius: number;
  /** Blend between the untouched backdrop (`0`) and the full glass result (`1`). */
  effectAmount: number;
  /** Tint colour, as a hex string so the table is introspectable and serialisable. */
  tintColor: string;
  /** Tint strength, `0`–`1`. */
  tintAmount: number;
}

/** Every public prop after preset resolution, with a concrete value for each. */
export interface LiquidGlassResolvedProps extends Omit<LiquidGlassOpticalBundle, 'tintColor'> {
  tintColor: ColorValue;
  /** Corner radius in dp. */
  cornerRadius: number;
  interactive: boolean;
  draggable: boolean;
  animated: boolean;
  /** Milliseconds. */
  animationDuration: number;
  colorScheme: LiquidGlassColorScheme;
}

/**
 * The base values used for any prop that is neither supplied explicitly nor covered by a preset.
 * These match the native defaults in `LiquidGlassView` and the Fabric spec defaults.
 *
 * Units: dimension values are React Native density-independent pixels (dp), `animationDuration`
 * is milliseconds, and `indexOfRefraction`, `dispersion`, `effectAmount` and `tintAmount` are
 * dimensionless.
 */
export const LIQUID_GLASS_DEFAULTS: Readonly<LiquidGlassResolvedProps> = Object.freeze({
  effect: 'regular',
  cornerRadius: 32,
  refractionStrength: 24,
  dispersion: 2.4,
  indexOfRefraction: 1.47,
  bevelDepth: 22,
  thickness: 6,
  blurRadius: 2.2,
  effectAmount: 0.96,
  tintColor: '#BEE5FF',
  tintAmount: 0.11,
  interactive: false,
  draggable: false,
  animated: true,
  animationDuration: 320,
  colorScheme: 'system',
} as const);

/**
 * The optical bundle each `material` preset resolves to.
 *
 * Exported so applications, documentation and tests can introspect exactly what a preset means
 * and derive their own variations from it:
 *
 * ```tsx
 * const {blurRadius} = LIQUID_GLASS_MATERIALS.satin;
 * <LiquidGlassView material="satin" blurRadius={blurRadius * 1.5} />
 * ```
 */
export const LIQUID_GLASS_MATERIALS: Readonly<
  Record<LiquidGlassMaterial, Readonly<LiquidGlassOpticalBundle>>
> = Object.freeze({
  crystal: Object.freeze({
    effect: 'clear',
    refractionStrength: 34,
    dispersion: 4.2,
    indexOfRefraction: 1.52,
    bevelDepth: 18,
    thickness: 8,
    blurRadius: 0.8,
    effectAmount: 1,
    tintColor: '#FFFFFF',
    tintAmount: 0.03,
  } as const),
  satin: Object.freeze({
    effect: 'satin',
    refractionStrength: 14,
    dispersion: 1.2,
    indexOfRefraction: 1.42,
    bevelDepth: 26,
    thickness: 10,
    blurRadius: 6.5,
    effectAmount: 0.92,
    tintColor: '#E8F2FF',
    tintAmount: 0.22,
  } as const),
  nocturne: Object.freeze({
    effect: 'nocturne',
    refractionStrength: 20,
    dispersion: 2,
    indexOfRefraction: 1.49,
    bevelDepth: 24,
    thickness: 12,
    blurRadius: 3.4,
    effectAmount: 0.98,
    tintColor: '#5A6B85',
    tintAmount: 0.3,
  } as const),
} as const);

/** The optical and behavioural props accepted by `LiquidGlassView`, all optional. */
export interface LiquidGlassStyleProps {
  /**
   * Optical preset. The recommended entry point for normal usage.
   *
   * Merge semantics: an explicitly supplied optical prop always wins over the preset's value for
   * that prop; every prop the caller does not supply takes the preset's value; anything the preset
   * does not cover falls back to {@link LIQUID_GLASS_DEFAULTS}. Presets never set `cornerRadius`,
   * `interactive`, `draggable`, `animated`, `animationDuration` or `colorScheme`.
   *
   * @default undefined — no preset, {@link LIQUID_GLASS_DEFAULTS} apply
   */
  material?: LiquidGlassMaterial;
  /**
   * Low-level shader variant.
   *
   * @deprecated Use `material` instead. Supplying `effect` together with `material` overrides the
   * preset's variant and logs a one-time `__DEV__` warning. `effect` remains supported for the
   * whole `0.x` series.
   * @default 'regular'
   */
  effect?: LiquidGlassEffect;
  /**
   * Corner radius of the glass slab, in dp. Also used as the SDF radius for the edge bevel.
   *
   * @default 32
   * Range: `>= 0` dp. Negative values are clamped to `0` natively.
   */
  cornerRadius?: number;
  /**
   * Maximum displacement applied to a refracted backdrop sample, in dp. Higher values bend the
   * backdrop further at the edges.
   *
   * @default 24
   * Range: `0`–`80` dp.
   */
  refractionStrength?: number;
  /**
   * Chromatic dispersion: how far the red and blue index of refraction are split from
   * `indexOfRefraction`, producing coloured fringing. Dimensionless — the resulting fringe width
   * already scales with the dp-based refraction geometry, so this value is not density-converted.
   *
   * @default 2.4
   * Range: `0`–`12` (dimensionless).
   */
  dispersion?: number;
  /**
   * Index of refraction of the glass, dimensionless. `1.0` is vacuum, ~`1.33` water,
   * ~`1.5` common glass, ~`2.4` diamond.
   *
   * @default 1.47
   * Range: `1.01`–`3`.
   */
  indexOfRefraction?: number;
  /**
   * Depth of the rounded bevel at the edge of the slab, in dp. Larger values widen the band where
   * the surface curves and therefore the refracted rim.
   *
   * @default 22
   * Range: `2`–`48` dp. Additionally capped natively at 24% of the view's shorter side.
   */
  bevelDepth?: number;
  /**
   * Thickness of the glass slab, in dp. Controls the optical path length, so it scales how far
   * refracted rays travel before they leave the material.
   *
   * @default 6
   * Range: `0`–`64` dp.
   */
  thickness?: number;
  /**
   * Blur radius applied to the refracted backdrop sample, in dp. The `satin` variant multiplies
   * this internally to produce frosted glass.
   *
   * @default 2.2
   * Range: `0`–`12` dp.
   */
  blurRadius?: number;
  /**
   * Overall strength of the optical treatment: `0` shows the untouched backdrop, `1` shows the
   * fully refracted, tinted result. Dimensionless.
   *
   * @default 0.96
   * Range: `0`–`1`.
   */
  effectAmount?: number;
  /**
   * Tint colour mixed into the transmitted light. Any React Native colour value.
   *
   * @default '#BEE5FF'
   */
  tintColor?: ColorValue;
  /**
   * Strength of `tintColor`, dimensionless. `0` leaves the transmitted colour untouched.
   *
   * @default 0.11
   * Range: `0`–`1`.
   */
  tintAmount?: number;
  /**
   * Whether the view consumes touches so pointer movement can deform the liquid membrane.
   * A stationary tap never changes the optical surface.
   * When `false` the view never claims a touch, so children and views behind it receive it.
   *
   * @default false
   */
  interactive?: boolean;
  /**
   * Whether the glass can be dragged around its parent with a finger. Requires `interactive`.
   *
   * @default false
   */
  draggable?: boolean;
  /**
   * Whether changing `material`/`effect` animates between variants instead of switching instantly.
   *
   * @default true
   */
  animated?: boolean;
  /**
   * Duration of the material transition, in milliseconds. `0` disables the transition.
   *
   * @default 320
   * Range: `>= 0` ms.
   */
  animationDuration?: number;
  /**
   * Appearance used for the internal light-response term. `system` follows the Android UI mode.
   *
   * @default 'system'
   */
  colorScheme?: LiquidGlassColorScheme;
}

/**
 * Resolves `material` plus any explicitly supplied props into a fully specified prop set.
 *
 * Precedence, highest first:
 * 1. an explicitly supplied prop (`undefined` counts as "not supplied"),
 * 2. the `material` preset's value for that prop,
 * 3. {@link LIQUID_GLASS_DEFAULTS}.
 *
 * Resolution happens in JavaScript on purpose: under Fabric every prop arrives natively carrying
 * its default, so native code cannot tell "not supplied" from "supplied as the default value" and
 * merge semantics are only expressible here.
 */
export function resolveLiquidGlassProps(
  props: LiquidGlassStyleProps,
): LiquidGlassResolvedProps {
  const preset =
    props.material !== undefined ? LIQUID_GLASS_MATERIALS[props.material] : undefined;
  const base = LIQUID_GLASS_DEFAULTS;
  return {
    effect: props.effect ?? preset?.effect ?? base.effect,
    cornerRadius: props.cornerRadius ?? base.cornerRadius,
    refractionStrength:
      props.refractionStrength ?? preset?.refractionStrength ?? base.refractionStrength,
    dispersion: props.dispersion ?? preset?.dispersion ?? base.dispersion,
    indexOfRefraction:
      props.indexOfRefraction ?? preset?.indexOfRefraction ?? base.indexOfRefraction,
    bevelDepth: props.bevelDepth ?? preset?.bevelDepth ?? base.bevelDepth,
    thickness: props.thickness ?? preset?.thickness ?? base.thickness,
    blurRadius: props.blurRadius ?? preset?.blurRadius ?? base.blurRadius,
    effectAmount: props.effectAmount ?? preset?.effectAmount ?? base.effectAmount,
    tintColor: props.tintColor ?? preset?.tintColor ?? base.tintColor,
    tintAmount: props.tintAmount ?? preset?.tintAmount ?? base.tintAmount,
    interactive: props.interactive ?? base.interactive,
    draggable: props.draggable ?? base.draggable,
    animated: props.animated ?? base.animated,
    animationDuration: props.animationDuration ?? base.animationDuration,
    colorScheme: props.colorScheme ?? base.colorScheme,
  };
}
