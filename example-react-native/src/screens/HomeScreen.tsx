import React, {useState} from 'react';
import {Image, Pressable, StyleSheet, Text, View} from 'react-native';
import {
  LiquidGlassScene,
  LiquidGlassView,
  isLiquidGlassSupported,
} from '@griffinkrutherford/liquid-glass-android';
import type {NativeStackScreenProps} from '@react-navigation/native-stack';
import {Backdrop} from '../Backdrop';
import {glassAssets, shared} from '../theme';
import type {RootStackParamList} from '../navigation';

type Props = NativeStackScreenProps<RootStackParamList, 'Home'>;

const ROUTES = [
  {route: 'Multi', label: 'Multiple glass elements'},
  {route: 'Scroll', label: 'Glass over a ScrollView'},
  {route: 'List', label: 'Glass over a FlatList'},
  {route: 'Lifecycle', label: 'Mount / unmount churn'},
] as const;

/**
 * Deliverable: text, images, icons, buttons and nested React Native children
 * rendered *inside* a single glass surface.
 */
export function HomeScreen({navigation}: Props) {
  const [taps, setTaps] = useState(0);

  return (
    <LiquidGlassScene style={shared.scene}>
      <Backdrop label="backdrop: plain RN Image + stripes" />

      <View style={styles.content}>
        <LiquidGlassView
          style={[shared.card, styles.hero]}
          effect="clear"
          cornerRadius={28}
          thickness={12}
          bevelDepth={28}
          indexOfRefraction={1.5}
          refractionStrength={28}
          dispersion={3}
          tintColor="rgba(220, 242, 255, 0.08)"
          interactive>
          {/* nested children: rows, images, icons, text, a pressable */}
          <View style={shared.row}>
            <Image source={glassAssets.icon} style={shared.icon} />
            <View style={styles.grow}>
              <Text style={shared.title}>Nested content</Text>
              <Text style={shared.caption}>
                Image + icon + text + button inside one glass view
              </Text>
            </View>
          </View>

          <View style={[shared.row, styles.gap]}>
            <Image source={glassAssets.backdrop} style={shared.thumb} />
            <Text style={[shared.body, styles.grow]}>
              Child views are laid out by React Native, not by the native view.
            </Text>
          </View>

          <Pressable
            accessibilityRole="button"
            style={shared.button}
            onPress={() => setTaps(t => t + 1)}>
            <Text style={shared.buttonLabel}>Tapped inside glass: {taps}</Text>
          </Pressable>
        </LiquidGlassView>

        <View style={styles.nav}>
          <Text style={styles.support}>
            isLiquidGlassSupported: {String(isLiquidGlassSupported)}
          </Text>
          {ROUTES.map(({route, label}) => (
            <Pressable
              key={route}
              accessibilityRole="button"
              style={styles.navButton}
              onPress={() => navigation.navigate(route)}>
              <Text style={shared.buttonLabel}>{label}</Text>
            </Pressable>
          ))}
        </View>
      </View>
    </LiquidGlassScene>
  );
}

const styles = StyleSheet.create({
  content: {flex: 1, justifyContent: 'space-between', padding: 20},
  hero: {minHeight: 260},
  grow: {flex: 1},
  gap: {marginTop: 16},
  nav: {gap: 8},
  support: {color: 'rgba(255,255,255,0.8)', fontSize: 12, marginBottom: 4},
  navButton: {
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderRadius: 14,
    backgroundColor: 'rgba(10,14,28,0.66)',
  },
});
