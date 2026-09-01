# Accessibility and motion

This page describes what the code does today. Two gaps are called out as gaps
rather than papered over.

## Touch handling

`LiquidGlassView` is a `ViewGroup`. It does **not** override
`onInterceptTouchEvent`, so touch dispatch is entirely standard: children are
offered every touch first, and the glass view only sees what they did not
handle.

What the view itself does with a touch depends on `interactive`:

| `interactive` | `ACTION_DOWN` | Effect |
| --- | --- | --- |
| `false` (default) | returns `false` | The view never claims a touch. Nothing is consumed, nothing is deformed. |
| `true` | returns `true` | The view claims the gesture and calls `requestDisallowInterceptTouchEvent(true)` on its parent. Pointer movement deforms the membrane; a stationary tap does not change the optics. On release without a drag, it calls `performClick()`. |

Consequences worth knowing:

- **Nested controls stay touchable.** A `Pressable`, `TextInput`, or button
  inside a `LiquidGlassView` receives touches normally, with or without
  `interactive`. `HomeScreen.tsx` in the example app has an in-glass tap counter
  for exactly this reason.
- **`interactive` claims the gesture from ancestors.** Because it calls
  `requestDisallowInterceptTouchEvent(true)`, an enclosing `ScrollView` cannot
  take the gesture over once the glass has it. Do not put an `interactive` glass
  surface where a scroll gesture needs to start.
- **`interactive` conflicts with a `Pressable` wrapper.** Wrapping an
  `interactive` glass view in a `Pressable` puts two things in competition for
  the same gesture. Either leave `interactive` off and wrap, or set
  `interactive` and put the `Pressable` *inside* the glass. See
  [recipes.md](recipes.md).

## Focus and screen readers

`LiquidGlassView` sets no `contentDescription`, does not set itself focusable or
clickable, and adds no accessibility node of its own. It is a transparent
container as far as accessibility is concerned, which is the correct default for
a decorative surface: children are exposed to TalkBack normally, in tree order.

Standard React Native accessibility props are forwarded to the native view and
handled by React Native's `BaseViewManager`, so the usual controls work:

```tsx
<LiquidGlassView
  material="satin"
  accessible
  accessibilityRole="summary"
  accessibilityLabel="Weather card"
  style={styles.card}>
  {/* … */}
</LiquidGlassView>
```

`LiquidGlassScene` is likewise a plain container for accessibility purposes.

### Known gap: `performClick()` without a click

When `interactive` is set, a tap that is not a drag calls `performClick()` on
release. The view has no click listener and is not marked clickable, so this
does not run any callback — but `performClick()` does emit a
`TYPE_VIEW_CLICKED` accessibility event. A screen reader may therefore announce
a click on a view it does not consider clickable.

If you set `interactive` on a surface that is not meant to be a control, set
`accessibilityRole` deliberately, or set `importantForAccessibility="no"` on the
glass view and expose its children instead.

### Contrast

The glass material modifies whatever is behind it, and the amount varies with
the backdrop. Text placed on glass has no guaranteed contrast ratio, and the
library does not measure or enforce one. If a label must meet a contrast target,
give it its own opaque or semi-opaque background inside the glass rather than
relying on the material — the example app's `shared.button` style does this.

`nocturne` with a high `tintAmount`, or a lower `effectAmount`, both raise the
floor on contrast, but neither guarantees it.

## Reduced motion

### Known gap: reduced motion is not honoured

Nothing in the library reads the platform's reduce-motion setting. There is no
call to `AccessibilityManager`, no check of `Settings.Global.ANIMATOR_DURATION_SCALE`
or `TRANSITION_ANIMATION_SCALE`, and no React Native `AccessibilityInfo` usage.
A user who has turned animations off system-wide still gets the material
transition and the membrane deformation.

This is a gap, not a decision. Until it is closed, honour the setting from
JavaScript:

```tsx
import {useEffect, useState} from 'react';
import {AccessibilityInfo} from 'react-native';
import {LiquidGlassView} from '@griffinkrutherford/liquid-glass-android';

function useReduceMotion() {
  const [reduce, setReduce] = useState(false);
  useEffect(() => {
    AccessibilityInfo.isReduceMotionEnabled().then(setReduce);
    const sub = AccessibilityInfo.addEventListener(
      'reduceMotionChanged',
      setReduce,
    );
    return () => sub.remove();
  }, []);
  return reduce;
}

export function Card() {
  const reduceMotion = useReduceMotion();
  return (
    <LiquidGlassView
      material="satin"
      animated={!reduceMotion}
      interactive={!reduceMotion}
    />
  );
}
```

`animated={false}` removes the material transition; `interactive={false}`
removes the membrane deformation, which is the only other source of motion in
the library. Together they make a glass surface completely static.

## What is not covered

- **RTL** — `LiquidGlassView` uses React Native's layout for its children, so
  RTL layout is React Native's normal behaviour. The optical treatment itself is
  direction-agnostic. Not tested.
- **Font scaling** — glass has no text of its own; children scale normally.
  Untested at large font scales, where a fixed-height glass card will clip.
- **High-contrast and colour-inversion modes** — not tested. The shader operates
  on captured pixels, so a system-level inversion applied after composition
  should apply to the result, but this has not been confirmed.
