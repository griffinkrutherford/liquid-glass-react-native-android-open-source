import React, {useState} from 'react';
import {Pressable, StyleSheet, Text, View} from 'react-native';
import {
  LiquidGlassScene,
  LiquidGlassView,
} from '@griffinkrutherford/liquid-glass-android';
import {useNavigation} from '@react-navigation/native';
import {Backdrop} from '../Backdrop';
import {shared} from '../theme';

/**
 * Deliverable: mounting, navigating away and returning must not crash or leave
 * a stale backdrop. This screen adds in-place remount churn on top of the
 * navigation churn so backdrop buffers are allocated and released repeatedly.
 */
export function LifecycleScreen() {
  const navigation = useNavigation();
  const [generation, setGeneration] = useState(0);
  const [mounted, setMounted] = useState(true);
  const [count, setCount] = useState(1);

  return (
    <LiquidGlassScene style={shared.scene}>
      <Backdrop label={`generation ${generation}`} />

      <View style={styles.content}>
        <View style={styles.controls}>
          <Button label="Remount" onPress={() => setGeneration(g => g + 1)} />
          <Button label={mounted ? 'Unmount' : 'Mount'} onPress={() => setMounted(m => !m)} />
          <Button label="Add" onPress={() => setCount(c => Math.min(c + 1, 6))} />
          <Button label="Remove" onPress={() => setCount(c => Math.max(c - 1, 0))} />
          <Button label="Go back" onPress={() => navigation.goBack()} />
        </View>

        {mounted ? (
          <View key={generation} style={styles.stack}>
            {Array.from({length: count}, (_, i) => (
              <LiquidGlassView
                key={i}
                style={[shared.card, styles.item]}
                effect={i % 2 ? 'satin' : 'clear'}
                cornerRadius={20}
                refractionStrength={24}>
                <Text style={shared.body}>
                  gen {generation} · view {i}
                </Text>
              </LiquidGlassView>
            ))}
          </View>
        ) : (
          <Text style={shared.body}>All glass views unmounted.</Text>
        )}
      </View>
    </LiquidGlassScene>
  );
}

function Button({label, onPress}: {label: string; onPress: () => void}) {
  return (
    <Pressable accessibilityRole="button" style={styles.button} onPress={onPress}>
      <Text style={shared.buttonLabel}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  content: {flex: 1, padding: 20, gap: 16},
  controls: {flexDirection: 'row', flexWrap: 'wrap', gap: 8},
  button: {
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderRadius: 12,
    backgroundColor: 'rgba(10,14,28,0.7)',
  },
  stack: {gap: 12},
  item: {height: 74},
});
