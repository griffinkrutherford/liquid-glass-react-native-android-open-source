import type {HostComponent, ViewProps} from 'react-native';
import codegenNativeComponent from 'react-native/Libraries/Utilities/codegenNativeComponent';

/**
 * Fabric spec for the backdrop-capturing scene. The scene takes no glass props of its own: it
 * only defines which subtree is captured as the optical backdrop, so it accepts the standard
 * `View` props (layout `style`, accessibility, `testID`, …) and nothing else.
 */
export interface NativeProps extends ViewProps {}

export default codegenNativeComponent<NativeProps>(
  'RNLiquidGlassScene',
) as HostComponent<NativeProps>;
