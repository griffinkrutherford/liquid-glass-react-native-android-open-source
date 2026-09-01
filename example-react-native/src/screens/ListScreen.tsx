import React, {useCallback} from 'react';
import {FlatList, Image, StyleSheet, Text, View} from 'react-native';
import {
  LiquidGlassScene,
  LiquidGlassView,
} from '@griffinkrutherford/liquid-glass-android';
import {glassAssets, shared} from '../theme';

type Item = {id: string; index: number};

const DATA: Item[] = Array.from({length: 60}, (_, i) => ({
  id: String(i),
  index: i,
}));

/**
 * Deliverable: glass over a virtualized list. Every eighth row is itself a
 * glass cell, so recycled cells exercise attach/detach of the native view.
 */
export function ListScreen() {
  const renderItem = useCallback(({item}: {item: Item}) => {
    if (item.index % 8 === 0) {
      return (
        <LiquidGlassView
          style={[shared.card, styles.glassCell]}
          effect="clear"
          cornerRadius={20}
          refractionStrength={26}>
          <Text style={shared.title}>Glass cell {item.index}</Text>
          <Text style={shared.caption}>recycled by FlatList</Text>
        </LiquidGlassView>
      );
    }
    return (
      <View
        style={[
          styles.cell,
          {backgroundColor: item.index % 2 ? '#131c3d' : '#26173a'},
        ]}>
        <Image source={glassAssets.icon} style={shared.icon} />
        <Text style={shared.body}>Plain cell {item.index}</Text>
      </View>
    );
  }, []);

  return (
    <LiquidGlassScene style={shared.scene}>
      <FlatList
        style={StyleSheet.absoluteFill}
        data={DATA}
        keyExtractor={item => item.id}
        renderItem={renderItem}
        contentContainerStyle={styles.listContent}
        initialNumToRender={8}
        windowSize={5}
      />

      <LiquidGlassView
        style={[shared.card, styles.overlay]}
        effect="nocturne"
        cornerRadius={24}
        blurRadius={3.5}>
        <Text style={shared.title}>Overlay above the list</Text>
      </LiquidGlassView>
    </LiquidGlassScene>
  );
}

const styles = StyleSheet.create({
  listContent: {paddingTop: 16, paddingBottom: 140},
  cell: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingHorizontal: 20,
    paddingVertical: 16,
  },
  glassCell: {marginHorizontal: 16, marginVertical: 8, height: 108},
  overlay: {
    position: 'absolute',
    left: 20,
    right: 20,
    bottom: 28,
    height: 84,
  },
});
