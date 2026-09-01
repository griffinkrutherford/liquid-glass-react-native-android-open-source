import type {ColorValue, HostComponent, ViewProps} from 'react-native';
import type {Float, WithDefault} from 'react-native/Libraries/Types/CodegenTypes';
import codegenNativeComponent from 'react-native/Libraries/Utilities/codegenNativeComponent';

/**
 * Fabric spec for the glass surface. This is the internal, fully-resolved prop set: the public
 * `LiquidGlassView` component resolves `material` presets in JavaScript and always passes a
 * concrete value for every prop below.
 *
 * There is deliberately no `material` prop here. Under Fabric every prop arrives natively carrying
 * its default value, so native code cannot distinguish "not supplied" from "supplied as the
 * default" and therefore cannot implement preset merge semantics.
 *
 * Units: `cornerRadius`, `refractionStrength`, `bevelDepth`, `thickness` and `blurRadius` are
 * React Native density-independent pixels (dp) and are converted with `PixelUtil.toPixelFromDIP`
 * in `LiquidGlassViewManager`. `animationDuration` is milliseconds. `indexOfRefraction`,
 * `dispersion`, `effectAmount` and `tintAmount` are dimensionless and are passed through unscaled.
 *
 * The defaults below mirror `LIQUID_GLASS_DEFAULTS` and the native property defaults.
 */
export interface NativeProps extends ViewProps {
  /** Shader variant. @default 'regular' */
  effect?: WithDefault<'clear' | 'regular' | 'satin' | 'nocturne' | 'none', 'regular'>;
  /** Consume touches; pointer movement deforms the membrane, stationary taps do not. @default false */
  interactive?: WithDefault<boolean, false>;
  /** Allow dragging within the parent. @default false */
  draggable?: WithDefault<boolean, false>;
  /** Animate material transitions. @default true */
  animated?: WithDefault<boolean, true>;
  /** Material transition duration, milliseconds, `>= 0`. @default 320 */
  animationDuration?: WithDefault<Float, 320>;
  /** Corner radius, dp, `>= 0`. @default 32 */
  cornerRadius?: WithDefault<Float, 32>;
  /** Maximum refracted sample displacement, dp, `0`–`80`. @default 24 */
  refractionStrength?: WithDefault<Float, 24>;
  /** Chromatic dispersion, dimensionless, `0`–`12`. @default 2.4 */
  dispersion?: WithDefault<Float, 2.4>;
  /** Index of refraction, dimensionless, `1.01`–`3`. @default 1.47 */
  indexOfRefraction?: WithDefault<Float, 1.47>;
  /** Edge bevel depth, dp, `2`–`48`. @default 22 */
  bevelDepth?: WithDefault<Float, 22>;
  /** Slab thickness, dp, `0`–`64`. @default 6 */
  thickness?: WithDefault<Float, 6>;
  /** Blur radius of the refracted sample, dp, `0`–`12`. @default 2.2 */
  blurRadius?: WithDefault<Float, 2.2>;
  /** Blend between backdrop and glass, dimensionless, `0`–`1`. @default 0.96 */
  effectAmount?: WithDefault<Float, 0.96>;
  /** Tint colour. @default '#BEE5FF' */
  tintColor?: ColorValue;
  /** Tint strength, dimensionless, `0`–`1`. @default 0.11 */
  tintAmount?: WithDefault<Float, 0.11>;
  /** Appearance used for the internal light response. @default 'system' */
  colorScheme?: WithDefault<'light' | 'dark' | 'system', 'system'>;
}

export default codegenNativeComponent<NativeProps>(
  'RNLiquidGlassView',
) as HostComponent<NativeProps>;
