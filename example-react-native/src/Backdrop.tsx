import React from 'react';
import {Image, StyleSheet, Text, View} from 'react-native';
import {glassAssets} from './theme';

/**
 * Non-glass scene content. Everything rendered here is what a sibling
 * LiquidGlassView samples as its live optical backdrop, so it deliberately
 * contains hard edges and saturated colour: refraction and chromatic dispersion
 * are only visible against high-contrast detail.
 */
export function Backdrop({label}: {label?: string}) {
  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="none">
      <Image
        source={glassAssets.backdrop}
        style={StyleSheet.absoluteFill}
        resizeMode="cover"
      />
      <View style={styles.stripes}>
        {Array.from({length: 14}, (_, i) => (
          <View
            key={i}
            style={[
              styles.stripe,
              {backgroundColor: i % 2 ? 'rgba(255,255,255,0.16)' : 'transparent'},
            ]}
          />
        ))}
      </View>
      {label ? <Text style={styles.label}>{label}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  stripes: {...StyleSheet.absoluteFillObject, justifyContent: 'space-between'},
  stripe: {height: 18},
  label: {
    position: 'absolute',
    left: 20,
    bottom: 20,
    color: 'rgba(255,255,255,0.75)',
    fontSize: 12,
  },
});
