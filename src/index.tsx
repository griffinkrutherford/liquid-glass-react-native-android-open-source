import React from 'react';
import {Platform, View} from 'react-native';
import type {ViewProps} from 'react-native';
import NativeLiquidGlassView from './LiquidGlassNativeComponent';
import type {NativeProps as LiquidGlassViewNativeProps} from './LiquidGlassNativeComponent';
import NativeLiquidGlassScene from './LiquidGlassSceneNativeComponent';
import {LiquidGlassSceneContext} from './LiquidGlassSceneContext';
import {resolveLiquidGlassProps} from './materials';
import type {LiquidGlassStyleProps} from './materials';
import {warnMissingScene, warnOnInvalidProps} from './devWarnings';

export {
  LIQUID_GLASS_DEFAULTS,
  LIQUID_GLASS_MATERIALS,
  resolveLiquidGlassProps,
} from './materials';
export type {
  LiquidGlassColorScheme,
  LiquidGlassEffect,
  LiquidGlassMaterial,
  LiquidGlassOpticalBundle,
  LiquidGlassRefractionExclusion,
  LiquidGlassResolvedProps,
  LiquidGlassStyleProps,
} from './materials';
export type {LiquidGlassViewNativeProps};

/** Props accepted by {@link LiquidGlassView}: every `View` prop plus the glass props. */
export interface LiquidGlassViewProps extends ViewProps, LiquidGlassStyleProps {}

/** Props accepted by {@link LiquidGlassScene}: the standard `View` props. */
export interface LiquidGlassSceneProps extends ViewProps {}

/**
 * A scene that captures its non-glass children as the live optical backdrop.
 *
 * Every {@link LiquidGlassView} must be a descendant of a scene; the scene renders its non-glass
 * children into a shared offscreen bitmap each frame and hands it to the glass views.
 *
 * On non-Android platforms this renders a plain `View` with the same layout, so the same tree can
 * be shared across platforms.
 */
export function LiquidGlassScene({children, ...props}: LiquidGlassSceneProps) {
  const content =
    Platform.OS === 'android' ? (
      <NativeLiquidGlassScene {...props}>{children}</NativeLiquidGlassScene>
    ) : (
      <View {...props}>{children}</View>
    );
  return (
    <LiquidGlassSceneContext.Provider value={true}>{content}</LiquidGlassSceneContext.Provider>
  );
}

/**
 * A liquid-glass surface that refracts the backdrop captured by its {@link LiquidGlassScene}.
 *
 * Platform behaviour (see {@link isLiquidGlassSupported}):
 * - Android API 33+ — the full runtime-shader material.
 * - Android API 23–32 — the same native view, drawing a static gradient fallback. Layout, touch
 *   handling and children behave identically; the optical props have no visible effect.
 * - Every other platform — a plain `View`. Glass props are ignored; `cornerRadius` is forwarded as
 *   `borderRadius` so layout and clipping stay comparable, and standard `View` props (`style`,
 *   accessibility, pointer and layout handlers, `testID`, …) are forwarded unchanged.
 *
 * Preset merge semantics: explicitly supplied props override the `material` preset, unsupplied
 * props take the preset's value, and anything the preset does not cover falls back to
 * `LIQUID_GLASS_DEFAULTS`.
 */
export function LiquidGlassView(props: LiquidGlassViewProps) {
  const isInsideScene = React.useContext(LiquidGlassSceneContext);
  const {
    // Glass-only props: destructured out so they are never spread onto a plain `View`.
    material: _material,
    effect: _effect,
    cornerRadius: _cornerRadius,
    refractionStrength: _refractionStrength,
    dispersion: _dispersion,
    indexOfRefraction: _indexOfRefraction,
    bevelDepth: _bevelDepth,
    thickness: _thickness,
    blurRadius: _blurRadius,
    effectAmount: _effectAmount,
    tintColor: _tintColor,
    tintAmount: _tintAmount,
    refractionExclusion: _refractionExclusion,
    interactive: _interactive,
    draggable: _draggable,
    animated: _animated,
    animationDuration: _animationDuration,
    colorScheme: _colorScheme,
    children,
    style,
    ...viewProps
  } = props;

  if (__DEV__) {
    warnOnInvalidProps(props);
    if (!isInsideScene) warnMissingScene();
  }

  const resolved = resolveLiquidGlassProps(props);
  const exclusion = props.refractionExclusion;
  const exclusionEnabled = exclusion?.shape === 'circle' && exclusion.radius > 0;

  if (Platform.OS !== 'android') {
    return (
      <View {...viewProps} style={[{borderRadius: resolved.cornerRadius}, style]}>
        {children}
      </View>
    );
  }

  return (
    <NativeLiquidGlassView
      {...viewProps}
      {...resolved}
      exclusionEnabled={exclusionEnabled}
      exclusionCenterX={exclusion?.centerX ?? 0.5}
      exclusionCenterY={exclusion?.centerY ?? 0.5}
      exclusionRadius={exclusion?.radius ?? 0}
      exclusionFeather={exclusion?.feather ?? 0}
      style={style}>
      {children}
    </NativeLiquidGlassView>
  );
}

/** Lowest Android API level that can render the runtime-shader glass material. */
export const LIQUID_GLASS_MIN_ANDROID_API = 33;

/**
 * Whether the current device renders the real glass material.
 *
 * `true` only on Android API {@link LIQUID_GLASS_MIN_ANDROID_API}+, where `RuntimeShader` exists.
 * `false` means the component still mounts and lays out correctly but draws the gradient fallback
 * (older Android) or a plain `View` (every other platform), so it is a rendering-quality check,
 * not an availability check — `LiquidGlassView` is always safe to render.
 */
export const isLiquidGlassSupported: boolean =
  Platform.OS === 'android' && Number(Platform.Version) >= LIQUID_GLASS_MIN_ANDROID_API;
