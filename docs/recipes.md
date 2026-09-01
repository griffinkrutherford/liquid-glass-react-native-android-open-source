# Recipes

Every example below is reduced from a real screen in
[`example-react-native/src/screens/`](../example-react-native/src/screens),
which is the app used to verify the library against a packed tarball.

## Scene rules

Read these first. Most "the glass is empty" reports are one of them.

1. **Every `LiquidGlassView` must be a descendant of a `LiquidGlassScene`.**
   Any depth works — the view walks up its parents to find the scene. Without
   one there is no captured backdrop, so the glass has nothing to refract and
   draws the plain fallback. `__DEV__` warns once when this happens.

2. **Only non-glass children of the scene become the backdrop.** Glass views are
   suppressed during the capture pass, so glass never samples itself or another
   glass view. Two overlapping glass surfaces both refract the same non-glass
   content; neither sees the other.

3. **Content outside the scene is not captured.** A navigation header, a status
   bar, or a parent screen background rendered above the scene is not part of
   the backdrop. Put the scene where it can contain both the glass and
   everything the glass should refract.

4. **The scene's own `backgroundColor` is captured.** `<LiquidGlassScene
   style={{flex: 1, backgroundColor: '#0b1020'}}>` gives the glass a base colour
   even before any child draws.

5. **Capture order is tree order, and it is independent of z-order.** Every
   visible non-glass child is captured, including ones declared *after* the
   glass. A non-glass sibling declared later is drawn into the backdrop *and*
   painted on top of the glass. If something should sit on top of glass without
   also being refracted by it, that arrangement is not currently expressible.

6. **A scene needs a size.** It measures to the size its parent gives it, so
   `flex: 1` or explicit dimensions are required; a scene with zero width or
   height captures nothing.

7. **Absolute positioning is measured against the scene.** The glass samples the
   backdrop at its own position relative to the scene, accumulating ancestor
   offsets and scroll positions on the way up. Absolutely positioned glass,
   glass inside a `ScrollView`, and dragged glass all sample the right region.

## Card

A card in normal flow layout, with nested React Native children. From
`HomeScreen.tsx`.

```tsx
import {Image, Pressable, StyleSheet, Text, View} from 'react-native';
import {
  LiquidGlassScene,
  LiquidGlassView,
} from '@griffinkrutherford/liquid-glass-android';

export function CardScreen() {
  return (
    <LiquidGlassScene style={styles.scene}>
      {/* Non-glass backdrop. High contrast on purpose: refraction and
          dispersion are invisible against a flat colour. */}
      <Image
        source={require('./backdrop.png')}
        style={StyleSheet.absoluteFill}
        resizeMode="cover"
      />

      <View style={styles.content}>
        <LiquidGlassView material="crystal" cornerRadius={28} style={styles.card}>
          <View style={styles.row}>
            <Image source={require('./icon.png')} style={styles.icon} />
            <Text style={styles.title}>Nested content</Text>
          </View>
          <Pressable style={styles.button} onPress={onPress}>
            <Text style={styles.buttonLabel}>Children stay touchable</Text>
          </Pressable>
        </LiquidGlassView>
      </View>
    </LiquidGlassScene>
  );
}

const styles = StyleSheet.create({
  scene: {flex: 1, backgroundColor: '#0b1020'},
  content: {flex: 1, justifyContent: 'center', padding: 20},
  card: {padding: 20, minHeight: 220, justifyContent: 'center'},
  row: {flexDirection: 'row', alignItems: 'center', gap: 12},
  icon: {width: 36, height: 36, borderRadius: 18},
  title: {color: '#fff', fontSize: 22, fontWeight: '700'},
  button: {
    marginTop: 14,
    alignSelf: 'flex-start',
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 999,
    backgroundColor: 'rgba(255,255,255,0.18)',
  },
  buttonLabel: {color: '#fff', fontSize: 14, fontWeight: '600'},
});
```

Children are laid out by React Native's layout engine, not by the native view.
Padding, flex, and gap on the `LiquidGlassView` behave like they do on a `View`.

## Navigation bar

A pinned header over scrolling content. From `ScrollScreen.tsx`.

The scroll view is a plain non-glass sibling **inside** the scene, so its
scrolled content is what the header refracts.

```tsx
<LiquidGlassScene style={styles.scene}>
  <ScrollView
    style={StyleSheet.absoluteFill}
    contentContainerStyle={styles.scrollContent}>
    {rows}
  </ScrollView>

  <LiquidGlassView
    material="satin"
    cornerRadius={0}
    refractionStrength={18}
    style={styles.header}>
    <Text style={styles.title}>Pinned header</Text>
  </LiquidGlassView>
</LiquidGlassScene>;

const styles = StyleSheet.create({
  scene: {flex: 1, backgroundColor: '#0b1020'},
  // Leave room under the bar so content is not permanently hidden behind it.
  scrollContent: {paddingTop: 120, paddingBottom: 140},
  header: {position: 'absolute', top: 0, left: 0, right: 0, height: 104, padding: 20},
});
```

`cornerRadius={0}` gives a square edge-to-edge bar. `satin` suits a bar: the
blur keeps text underneath from competing with the header's own label.

For a bottom tab bar, swap `top: 0` for `bottom: 0` and add the safe-area inset
to the bar's height.

Only glass **inside** the scene works. A React Navigation header rendered by
the navigator sits outside your screen component and therefore outside the
scene; to make the header itself glass, render a custom header inside the
screen with `headerShown: false`.

## Button

Glass is a surface, not a pressable. Compose it with `Pressable`:

```tsx
<Pressable accessibilityRole="button" onPress={onPress}>
  <LiquidGlassView material="crystal" cornerRadius={24} style={styles.button}>
    <Text style={styles.buttonLabel}>Continue</Text>
  </LiquidGlassView>
</Pressable>
```

Leave `interactive` off here. `interactive` makes the native view consume
touches so pointer movement can deform the membrane, which competes with the
`Pressable` wrapping it. A stationary tap never changes the optics. Set
`interactive` only when movement-driven membrane deformation is feedback you
want, and then handle the press with a nested `Pressable` inside the glass
rather than a wrapper around it — as `HomeScreen.tsx` does.

Small buttons need small numbers. `bevelDepth` is capped at 24% of the shorter
side, so on a 48 dp-tall button anything above ~11 dp does nothing; and a large
`refractionStrength` on a small surface samples backdrop from far outside the
button, which reads as noise rather than glass.

## Floating control

An absolutely positioned, draggable pill. From `ScrollScreen.tsx` and
`MultiGlassScreen.tsx`.

```tsx
<LiquidGlassView
  material="crystal"
  cornerRadius={30}
  refractionStrength={34}
  interactive
  draggable
  style={styles.pill}>
  <Text style={styles.buttonLabel}>Floating control</Text>
</LiquidGlassView>;

const styles = StyleSheet.create({
  pill: {
    position: 'absolute',
    left: 24,
    right: 24,
    bottom: 32,
    height: 72,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
```

`draggable` requires `interactive`. The drag is clamped to the parent's bounds
and moves the native view directly, so the position is not visible to React
Native layout and resets on re-layout. It is meant for demos and transient
floating controls, not for persistent positioning — drive that from JavaScript
state instead.

## Multiple glass elements in one scene

Any number of glass views can share one scene. They share the same backdrop
bitmap; the scene captures once per change, not once per glass view. Prefer one
scene per screen over one scene per glass element.

```tsx
<LiquidGlassScene style={styles.scene}>
  <Backdrop />
  <View style={styles.grid}>
    <LiquidGlassView material="crystal" style={styles.tile} />
    <LiquidGlassView material="satin" style={styles.tile} />
    <LiquidGlassView material="nocturne" style={styles.tile} />
  </View>
</LiquidGlassScene>
```

## Glass over a list

From `ListScreen.tsx`. The `FlatList` is a non-glass sibling in the scene; the
overlay is absolutely positioned above it.

```tsx
<LiquidGlassScene style={styles.scene}>
  <FlatList style={StyleSheet.absoluteFill} data={data} renderItem={renderItem} />

  <LiquidGlassView material="nocturne" cornerRadius={24} style={styles.overlay}>
    <Text style={styles.title}>Overlay above the list</Text>
  </LiquidGlassView>
</LiquidGlassScene>
```

Glass cells *inside* the list also work — the view finds the scene through the
list — but see [performance.md](performance.md) before putting glass in a
recycled row.

## Known composition limitation

A direct child of the scene is drawn into the backdrop without its own
`transform` or `opacity` applied, because the capture pass draws each direct
child by itself rather than through its parent's transform. Wrapping the
transformed content in a plain `View` makes it a grandchild and restores normal
behaviour:

```tsx
<LiquidGlassScene style={styles.scene}>
  {/* transform may not appear in the backdrop */}
  <Animated.View style={[StyleSheet.absoluteFill, {transform: [{scale}]}]} />

  {/* wrapped: the transform is applied by the wrapper during capture */}
  <View style={StyleSheet.absoluteFill}>
    <Animated.View style={[StyleSheet.absoluteFill, {transform: [{scale}]}]} />
  </View>
</LiquidGlassScene>
```

This follows from how the capture pass draws direct children and has not yet
been confirmed on hardware. Confirm it in your own scene before working around
it.
