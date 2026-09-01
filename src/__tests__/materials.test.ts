import {
  LIQUID_GLASS_DEFAULTS,
  LIQUID_GLASS_MATERIALS,
  resolveLiquidGlassProps,
} from '../materials';
import type {ColorValue} from 'react-native';
import type {LiquidGlassMaterial, LiquidGlassStyleProps} from '../materials';

/**
 * Preset resolution is the whole reason merge semantics live in JavaScript: under Fabric every
 * prop arrives natively already carrying its default, so "not supplied" and "supplied as the
 * default" are indistinguishable on the native side. These tests pin the contract documented on
 * `resolveLiquidGlassProps`:
 *
 *   explicit prop  >  `material` preset  >  LIQUID_GLASS_DEFAULTS
 */

const MATERIALS = Object.keys(LIQUID_GLASS_MATERIALS) as LiquidGlassMaterial[];

/** The optical props a preset is allowed to supply. */
const OPTICAL_KEYS = [
  'effect',
  'refractionStrength',
  'dispersion',
  'indexOfRefraction',
  'bevelDepth',
  'thickness',
  'blurRadius',
  'effectAmount',
  'tintColor',
  'tintAmount',
] as const;

/** Props a preset must never touch: geometry, behaviour, motion and appearance. */
const NON_OPTICAL_KEYS = [
  'cornerRadius',
  'interactive',
  'draggable',
  'animated',
  'animationDuration',
  'colorScheme',
] as const;

describe('LIQUID_GLASS_DEFAULTS', () => {
  it('is frozen so a consumer cannot mutate the shared default table', () => {
    expect(Object.isFrozen(LIQUID_GLASS_DEFAULTS)).toBe(true);
  });

  it('covers exactly the optical and non-optical prop sets, with no extras', () => {
    expect(Object.keys(LIQUID_GLASS_DEFAULTS).sort()).toEqual(
      [...OPTICAL_KEYS, ...NON_OPTICAL_KEYS].sort(),
    );
  });
});

describe('LIQUID_GLASS_MATERIALS', () => {
  it.each(MATERIALS)('%s is frozen', (material) => {
    expect(Object.isFrozen(LIQUID_GLASS_MATERIALS[material])).toBe(true);
  });

  it.each(MATERIALS)('%s supplies every optical prop and nothing else', (material) => {
    expect(Object.keys(LIQUID_GLASS_MATERIALS[material]).sort()).toEqual([...OPTICAL_KEYS].sort());
  });
});

describe('resolveLiquidGlassProps — defaults', () => {
  it('returns exactly LIQUID_GLASS_DEFAULTS for an empty prop set', () => {
    expect(resolveLiquidGlassProps({})).toEqual(LIQUID_GLASS_DEFAULTS);
  });

  it('returns a fresh object rather than the frozen default table', () => {
    const resolved = resolveLiquidGlassProps({});
    expect(resolved).not.toBe(LIQUID_GLASS_DEFAULTS);
    expect(Object.isFrozen(resolved)).toBe(false);
  });

  it('always produces a concrete value for every prop', () => {
    const resolved = resolveLiquidGlassProps({material: 'satin', thickness: 3});
    for (const key of Object.keys(LIQUID_GLASS_DEFAULTS)) {
      expect((resolved as unknown as Record<string, unknown>)[key]).toBeDefined();
    }
  });
});

describe('resolveLiquidGlassProps — preset beats default', () => {
  it.each(MATERIALS)('%s applies its whole optical bundle', (material) => {
    const resolved = resolveLiquidGlassProps({material});
    for (const key of OPTICAL_KEYS) {
      expect(resolved[key]).toBe(LIQUID_GLASS_MATERIALS[material][key]);
    }
  });

  it.each(MATERIALS)('%s leaves geometry, behaviour and motion at their defaults', (material) => {
    const resolved = resolveLiquidGlassProps({material});
    for (const key of NON_OPTICAL_KEYS) {
      expect(resolved[key]).toBe(LIQUID_GLASS_DEFAULTS[key]);
    }
  });

  it('presets differ from the defaults, so the test above is not vacuous', () => {
    // At least one optical value per preset must actually change, otherwise "preset beats
    // default" would pass even if preset resolution were a no-op.
    for (const material of MATERIALS) {
      const resolved = resolveLiquidGlassProps({material});
      const changed = OPTICAL_KEYS.filter((key) => resolved[key] !== LIQUID_GLASS_DEFAULTS[key]);
      expect(changed.length).toBeGreaterThan(0);
    }
  });
});

describe('resolveLiquidGlassProps — explicit prop beats preset', () => {
  it.each(MATERIALS)('an explicit value overrides %s for that prop only', (material) => {
    const resolved = resolveLiquidGlassProps({material, thickness: 41});
    expect(resolved.thickness).toBe(41);
    // Every other optical prop still comes from the preset.
    for (const key of OPTICAL_KEYS) {
      if (key === 'thickness') continue;
      expect(resolved[key]).toBe(LIQUID_GLASS_MATERIALS[material][key]);
    }
  });

  it('a full explicit prop set makes the preset irrelevant', () => {
    const explicit: LiquidGlassStyleProps = {
      material: 'nocturne',
      effect: 'clear',
      refractionStrength: 1,
      dispersion: 2,
      indexOfRefraction: 1.1,
      bevelDepth: 3,
      thickness: 4,
      blurRadius: 5,
      effectAmount: 0.5,
      tintColor: '#010203',
      tintAmount: 0.6,
    };
    const resolved = resolveLiquidGlassProps(explicit);
    for (const key of OPTICAL_KEYS) {
      expect(resolved[key]).toBe(explicit[key]);
    }
  });

  it('the deprecated `effect` prop overrides the preset variant', () => {
    expect(LIQUID_GLASS_MATERIALS.satin.effect).toBe('satin');
    expect(resolveLiquidGlassProps({material: 'satin'}).effect).toBe('satin');
    expect(resolveLiquidGlassProps({material: 'satin', effect: 'none'}).effect).toBe('none');
  });
});

describe('resolveLiquidGlassProps — undefined means "not supplied"', () => {
  it('an explicit `undefined` falls through to the preset', () => {
    const resolved = resolveLiquidGlassProps({material: 'crystal', thickness: undefined});
    expect(resolved.thickness).toBe(LIQUID_GLASS_MATERIALS.crystal.thickness);
  });

  it('an explicit `undefined` with no preset falls through to the default', () => {
    const resolved = resolveLiquidGlassProps({thickness: undefined, tintColor: undefined});
    expect(resolved.thickness).toBe(LIQUID_GLASS_DEFAULTS.thickness);
    expect(resolved.tintColor).toBe(LIQUID_GLASS_DEFAULTS.tintColor);
  });

  it('an undefined `material` behaves as no preset at all', () => {
    expect(resolveLiquidGlassProps({material: undefined})).toEqual(LIQUID_GLASS_DEFAULTS);
  });

  it('a spread of an all-undefined prop object still resolves to the defaults', () => {
    // This is the realistic shape: `<LiquidGlassView {...props} />` where the caller's own
    // optional props are undefined. `??` must not be replaced with `||` here.
    const fromCaller = {thickness: undefined, effectAmount: undefined, animated: undefined};
    expect(resolveLiquidGlassProps(fromCaller)).toEqual(LIQUID_GLASS_DEFAULTS);
  });
});

describe('resolveLiquidGlassProps — explicitly falsy values are honoured', () => {
  it('keeps `0` for every numeric prop instead of falling back', () => {
    const zeroed = resolveLiquidGlassProps({
      material: 'nocturne',
      refractionStrength: 0,
      dispersion: 0,
      bevelDepth: 0,
      thickness: 0,
      blurRadius: 0,
      effectAmount: 0,
      tintAmount: 0,
      cornerRadius: 0,
      animationDuration: 0,
    });
    expect(zeroed.refractionStrength).toBe(0);
    expect(zeroed.dispersion).toBe(0);
    expect(zeroed.bevelDepth).toBe(0);
    expect(zeroed.thickness).toBe(0);
    expect(zeroed.blurRadius).toBe(0);
    expect(zeroed.effectAmount).toBe(0);
    expect(zeroed.tintAmount).toBe(0);
    expect(zeroed.cornerRadius).toBe(0);
    expect(zeroed.animationDuration).toBe(0);
  });

  it('keeps `false` for `animated`, whose default is `true`', () => {
    expect(LIQUID_GLASS_DEFAULTS.animated).toBe(true);
    expect(resolveLiquidGlassProps({animated: false}).animated).toBe(false);
  });

  it('keeps `false` for `interactive` and `draggable`', () => {
    const resolved = resolveLiquidGlassProps({interactive: false, draggable: false});
    expect(resolved.interactive).toBe(false);
    expect(resolved.draggable).toBe(false);
  });

  it('keeps `true` for `interactive` and `draggable`, whose defaults are `false`', () => {
    const resolved = resolveLiquidGlassProps({interactive: true, draggable: true});
    expect(resolved.interactive).toBe(true);
    expect(resolved.draggable).toBe(true);
  });

  it('keeps a falsy tintColor rather than substituting the preset tint', () => {
    // React Native accepts processed colours as integers at runtime, and `0` is
    // fully-transparent black. Typing forbids it, so the cast is the point of the test: a `||`
    // fallback here would silently repaint a deliberately transparent tint with the preset's.
    const transparentBlack = 0 as unknown as ColorValue;
    expect(resolveLiquidGlassProps({material: 'satin', tintColor: transparentBlack}).tintColor).toBe(
      transparentBlack,
    );
    expect(resolveLiquidGlassProps({tintColor: 'transparent'}).tintColor).toBe('transparent');
  });

  it("keeps the `'none'` variant, which reads as 'unset' but is a real value", () => {
    expect(resolveLiquidGlassProps({effect: 'none'}).effect).toBe('none');
    expect(resolveLiquidGlassProps({material: 'crystal', effect: 'none'}).effect).toBe('none');
  });
});

describe('resolveLiquidGlassProps — robustness', () => {
  it('does not mutate the caller props object', () => {
    const props: LiquidGlassStyleProps = {material: 'crystal', thickness: 2};
    const snapshot = {...props};
    resolveLiquidGlassProps(props);
    expect(props).toEqual(snapshot);
  });

  it('falls back to the defaults for an unknown material rather than throwing', () => {
    const resolved = resolveLiquidGlassProps({
      material: 'obsidian' as LiquidGlassMaterial,
    });
    expect(resolved).toEqual(LIQUID_GLASS_DEFAULTS);
  });

  it('still honours explicit props alongside an unknown material', () => {
    const resolved = resolveLiquidGlassProps({
      material: 'obsidian' as LiquidGlassMaterial,
      thickness: 7,
    });
    expect(resolved.thickness).toBe(7);
    expect(resolved.blurRadius).toBe(LIQUID_GLASS_DEFAULTS.blurRadius);
  });

  it('is pure: the same input always resolves to the same output', () => {
    const props: LiquidGlassStyleProps = {material: 'satin', dispersion: 0};
    expect(resolveLiquidGlassProps(props)).toEqual(resolveLiquidGlassProps(props));
  });
});
