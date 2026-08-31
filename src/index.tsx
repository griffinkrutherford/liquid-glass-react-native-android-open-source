import React from 'react';
import {Platform, View} from 'react-native';
import type {ViewProps} from 'react-native';
import NativeLiquidGlassView from './LiquidGlassNativeComponent';
import type {NativeProps as LiquidGlassViewProps} from './LiquidGlassNativeComponent';
import NativeLiquidGlassScene from './LiquidGlassSceneNativeComponent';

export type {LiquidGlassViewProps};

/** A scene that captures its non-glass children as the live optical backdrop. */
export function LiquidGlassScene(props: ViewProps) {
  if (Platform.OS !== 'android') return <View {...props} />;
  return <NativeLiquidGlassScene {...props} />;
}

/** An Android liquid-glass surface. Unsupported platforms render a normal View. */
export function LiquidGlassView({children, ...props}: LiquidGlassViewProps) {
  if (Platform.OS !== 'android') return <View {...props}>{children}</View>;
  return <NativeLiquidGlassView {...props}>{children}</NativeLiquidGlassView>;
}

export const isLiquidGlassSupported =
  Platform.OS === 'android' && Number(Platform.Version) >= 33;
