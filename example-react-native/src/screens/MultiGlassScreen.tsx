import React from 'react';
import {Image, StyleSheet, Text, View} from 'react-native';
import {
  LiquidGlassScene,
  LiquidGlassView,
} from '@griffinkrutherford/liquid-glass-android';
import {Backdrop} from '../Backdrop';
import {glassAssets, shared} from '../theme';

/**
 * Deliverable: multiple glass elements sharing one scene, including an
 * absolutely positioned element that overlaps a flow-laid-out one.
 */
export function MultiGlassScreen() {
  return (
    <LiquidGlassScene style={shared.scene}>
      <Backdrop label="one scene, four glass elements" />

      <View style={styles.grid}>
        <LiquidGlassView
          style={[shared.card, styles.tile]}
          effect="clear"
          cornerRadius={24}
          refractionStrength={30}
          dispersion={4}>
          <Text style={shared.title}>clear</Text>
          <Text style={shared.caption}>high refraction</Text>
        </LiquidGlassView>

        <LiquidGlassView
          style={[shared.card, styles.tile]}
          effect="satin"
          cornerRadius={24}
          blurRadius={5}>
          <Image source={glassAssets.icon} style={shared.icon} />
          <Text style={shared.title}>satin</Text>
        </LiquidGlassView>

        <LiquidGlassView
          style={[shared.card, styles.tile]}
          effect="nocturne"
          cornerRadius={24}
          colorScheme="dark">
          <Text style={shared.title}>nocturne</Text>
          <Text style={shared.caption}>dark scheme</Text>
        </LiquidGlassView>

        <LiquidGlassView
          style={[shared.card, styles.tile]}
          effect="regular"
          cornerRadius={24}
          tintColor="rgba(255, 200, 120, 0.18)"
          tintAmount={0.24}>
          <Text style={shared.title}>regular</Text>
          <Text style={shared.caption}>warm tint</Text>
        </LiquidGlassView>
      </View>

      {/* Absolutely positioned bar that overlaps the tiles above. */}
      <LiquidGlassView
        style={[shared.card, styles.bar]}
        effect="clear"
        cornerRadius={26}
        thickness={9}
        bevelDepth={26}
        draggable>
        <Text style={shared.body}>Draggable overlay — drag me across the tiles</Text>
      </LiquidGlassView>
    </LiquidGlassScene>
  );
}

const styles = StyleSheet.create({
  grid: {
    flex: 1,
    flexDirection: 'row',
    flexWrap: 'wrap',
    padding: 16,
    gap: 16,
    alignContent: 'flex-start',
  },
  tile: {width: '47%', height: 150},
  bar: {
    position: 'absolute',
    left: 20,
    right: 20,
    bottom: 40,
    height: 92,
  },
});
