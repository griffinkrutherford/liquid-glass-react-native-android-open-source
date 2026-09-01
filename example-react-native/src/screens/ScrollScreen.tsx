import React from 'react';
import {Image, ScrollView, StyleSheet, Text, View} from 'react-native';
import {
  LiquidGlassScene,
  LiquidGlassView,
} from '@griffinkrutherford/liquid-glass-android';
import {glassAssets, shared} from '../theme';

const ROWS = Array.from({length: 24}, (_, i) => i);

/**
 * Deliverable: glass pinned over scrolling content. The ScrollView is a plain
 * non-glass sibling inside the scene, so the backdrop must update as it scrolls.
 */
export function ScrollScreen() {
  return (
    <LiquidGlassScene style={shared.scene}>
      <ScrollView
        style={StyleSheet.absoluteFill}
        contentContainerStyle={styles.scrollContent}>
        {ROWS.map(i => (
          <View
            key={i}
            style={[
              styles.row,
              {backgroundColor: i % 2 ? '#16224a' : '#2a1840'},
            ]}>
            <Image source={glassAssets.icon} style={shared.icon} />
            <Text style={shared.body}>Scrolling row {i}</Text>
          </View>
        ))}
      </ScrollView>

      <LiquidGlassView
        style={[shared.card, styles.header]}
        effect="satin"
        cornerRadius={0}
        blurRadius={4}
        refractionStrength={18}>
        <Text style={shared.title}>Pinned header</Text>
        <Text style={shared.caption}>backdrop should track the scroll</Text>
      </LiquidGlassView>

      <LiquidGlassView
        style={[shared.card, styles.pill]}
        effect="clear"
        cornerRadius={30}
        refractionStrength={34}
        dispersion={5}
        interactive
        draggable>
        <Text style={shared.buttonLabel}>Floating control</Text>
      </LiquidGlassView>
    </LiquidGlassScene>
  );
}

const styles = StyleSheet.create({
  scrollContent: {paddingTop: 120, paddingBottom: 140},
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingHorizontal: 20,
    paddingVertical: 18,
  },
  header: {position: 'absolute', top: 0, left: 0, right: 0, height: 104},
  pill: {
    position: 'absolute',
    left: 24,
    right: 24,
    bottom: 32,
    height: 72,
    alignItems: 'center',
  },
});
