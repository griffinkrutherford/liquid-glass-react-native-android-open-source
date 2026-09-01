import React from 'react';

/**
 * True for any subtree rendered inside a `LiquidGlassScene`.
 *
 * Used only to detect invalid composition in development. The provider renders no host view and
 * carries a constant value, so it cannot affect layout, rendering or re-render behaviour.
 */
export const LiquidGlassSceneContext: React.Context<boolean> =
  React.createContext<boolean>(false);

LiquidGlassSceneContext.displayName = 'LiquidGlassSceneContext';
