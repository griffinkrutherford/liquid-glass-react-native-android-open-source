import type {ColorValue, HostComponent, ViewProps} from 'react-native';
import type {Float, WithDefault} from 'react-native/Libraries/Types/CodegenTypes';
import codegenNativeComponent from 'react-native/Libraries/Utilities/codegenNativeComponent';

export interface NativeProps extends ViewProps {
  effect?: WithDefault<'clear' | 'regular' | 'satin' | 'nocturne' | 'none', 'regular'>;
  interactive?: WithDefault<boolean, false>;
  draggable?: WithDefault<boolean, false>;
  animated?: WithDefault<boolean, true>;
  animationDuration?: WithDefault<Float, 320>;
  cornerRadius?: WithDefault<Float, 32>;
  refractionStrength?: WithDefault<Float, 24>;
  dispersion?: WithDefault<Float, 2.4>;
  indexOfRefraction?: WithDefault<Float, 1.47>;
  bevelDepth?: WithDefault<Float, 22>;
  thickness?: WithDefault<Float, 6>;
  blurRadius?: WithDefault<Float, 2.2>;
  effectAmount?: WithDefault<Float, 0.96>;
  tintColor?: ColorValue;
  tintAmount?: WithDefault<Float, 0.11>;
  colorScheme?: WithDefault<'light' | 'dark' | 'system', 'system'>;
}

export default codegenNativeComponent<NativeProps>(
  'RNLiquidGlassView',
) as HostComponent<NativeProps>;
