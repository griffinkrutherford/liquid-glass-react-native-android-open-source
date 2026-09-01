import {
  resetWarnings,
  warnMissingScene,
  warnOnInvalidProps,
  warnOnce,
} from '../devWarnings';
import {
  LIQUID_GLASS_DEFAULTS,
  LIQUID_GLASS_MATERIALS,
  resolveLiquidGlassProps,
} from '../materials';
import type {LiquidGlassMaterial, LiquidGlassStyleProps} from '../materials';

/**
 * Development diagnostics must be loud enough to be useful and quiet enough to be tolerable:
 * every warning is emitted at most once per JavaScript context, and none of them ever throws.
 */

let warn: jest.SpyInstance;

beforeEach(() => {
  resetWarnings();
  warn = jest.spyOn(console, 'warn').mockImplementation(() => {});
});

afterEach(() => {
  warn.mockRestore();
});

/** Every warning this module emits, joined into one searchable string. */
const messages = () => warn.mock.calls.map((call) => String(call[0])).join('\n');

describe('warnOnce', () => {
  it('logs the first time a key is used', () => {
    warnOnce('a', 'first');
    expect(warn).toHaveBeenCalledTimes(1);
  });

  it('prefixes every message so it is greppable in a noisy Metro log', () => {
    warnOnce('a', 'first');
    expect(warn.mock.calls[0][0]).toBe('[liquid-glass] first');
  });

  it('dedupes repeated keys, even with a different message', () => {
    warnOnce('a', 'first');
    warnOnce('a', 'second');
    warnOnce('a', 'third');
    expect(warn).toHaveBeenCalledTimes(1);
    expect(messages()).toContain('first');
    expect(messages()).not.toContain('second');
  });

  it('treats different keys independently', () => {
    warnOnce('a', 'first');
    warnOnce('b', 'second');
    expect(warn).toHaveBeenCalledTimes(2);
  });

  it('is reset by resetWarnings, so tests do not leak into one another', () => {
    warnOnce('a', 'first');
    resetWarnings();
    warnOnce('a', 'first');
    expect(warn).toHaveBeenCalledTimes(2);
  });
});

describe('warnOnInvalidProps — range warnings', () => {
  it('stays silent for an empty prop set', () => {
    warnOnInvalidProps({});
    expect(warn).not.toHaveBeenCalled();
  });

  it('stays silent for in-range values', () => {
    warnOnInvalidProps({
      cornerRadius: 16,
      refractionStrength: 24,
      dispersion: 2.4,
      indexOfRefraction: 1.47,
      bevelDepth: 22,
      thickness: 6,
      blurRadius: 2.2,
      effectAmount: 0.96,
      tintAmount: 0.11,
      animationDuration: 320,
    });
    expect(warn).not.toHaveBeenCalled();
  });

  it.each<[keyof LiquidGlassStyleProps, number, number]>([
    ['refractionStrength', 0, 80],
    ['dispersion', 0, 12],
    ['indexOfRefraction', 1.01, 3],
    ['bevelDepth', 2, 48],
    ['thickness', 0, 64],
    ['blurRadius', 0, 12],
    ['effectAmount', 0, 1],
    ['tintAmount', 0, 1],
  ])('treats the documented %s bounds as inclusive', (name, min, max) => {
    warnOnInvalidProps({[name]: min} as LiquidGlassStyleProps);
    warnOnInvalidProps({[name]: max} as LiquidGlassStyleProps);
    expect(warn).not.toHaveBeenCalled();
  });

  it.each<[keyof LiquidGlassStyleProps, number]>([
    ['cornerRadius', -1],
    ['refractionStrength', 81],
    ['dispersion', -0.1],
    ['indexOfRefraction', 1],
    ['bevelDepth', 1.9],
    ['thickness', 64.1],
    ['blurRadius', 13],
    ['effectAmount', 1.5],
    ['tintAmount', -0.001],
    ['animationDuration', -1],
  ])('warns that %s = %p will be clamped natively', (name, value) => {
    warnOnInvalidProps({[name]: value} as LiquidGlassStyleProps);
    expect(warn).toHaveBeenCalledTimes(1);
    const message = String(warn.mock.calls[0][0]);
    expect(message).toContain(name);
    expect(message).toContain('clamped natively');
  });

  it('names the unit in the range description', () => {
    warnOnInvalidProps({thickness: 999});
    expect(messages()).toContain('0–64 dp');
    resetWarnings();
    warnOnInvalidProps({dispersion: 99});
    expect(messages()).toContain('0–12 dimensionless');
  });

  it('renders an unbounded maximum as ∞ rather than "Infinity"', () => {
    warnOnInvalidProps({cornerRadius: -4});
    expect(messages()).toContain('∞');
    expect(messages()).not.toContain('Infinity');
  });

  it('warns once per prop, not once per render', () => {
    warnOnInvalidProps({thickness: 500});
    warnOnInvalidProps({thickness: 600});
    warnOnInvalidProps({thickness: 700});
    expect(warn).toHaveBeenCalledTimes(1);
  });

  it('warns separately for each out-of-range prop', () => {
    warnOnInvalidProps({thickness: 500, blurRadius: 500});
    expect(warn).toHaveBeenCalledTimes(2);
  });

  it('warns about a non-numeric value instead of silently comparing it', () => {
    warnOnInvalidProps({thickness: '6' as unknown as number});
    expect(warn).toHaveBeenCalledTimes(1);
    expect(messages()).toContain('must be a number');
    expect(messages()).toContain('"6"');
  });

  it('warns about NaN, which no comparison would catch', () => {
    warnOnInvalidProps({indexOfRefraction: Number.NaN});
    expect(messages()).toContain('must be a number');
  });

  it('warns about a non-finite value, which is out of every bounded range', () => {
    warnOnInvalidProps({blurRadius: Number.POSITIVE_INFINITY});
    expect(warn).toHaveBeenCalledTimes(1);
  });

  it('ignores undefined and null, which mean "not supplied"', () => {
    warnOnInvalidProps({
      thickness: undefined,
      blurRadius: null as unknown as number,
      tintAmount: undefined,
    });
    expect(warn).not.toHaveBeenCalled();
  });

  it('never throws, whatever it is handed', () => {
    expect(() =>
      warnOnInvalidProps({
        thickness: {} as unknown as number,
        material: null as unknown as LiquidGlassMaterial,
        effect: 42 as unknown as LiquidGlassStyleProps['effect'],
      }),
    ).not.toThrow();
  });
});

describe('warnOnInvalidProps — unknown material', () => {
  it.each(Object.keys(LIQUID_GLASS_MATERIALS) as LiquidGlassMaterial[])(
    'stays silent for the known material %s',
    (material) => {
      warnOnInvalidProps({material});
      expect(warn).not.toHaveBeenCalled();
    },
  );

  it('warns for an unknown material and lists the valid names', () => {
    warnOnInvalidProps({material: 'obsidian' as LiquidGlassMaterial});
    expect(warn).toHaveBeenCalledTimes(1);
    const message = messages();
    expect(message).toContain('obsidian');
    for (const known of Object.keys(LIQUID_GLASS_MATERIALS)) {
      expect(message).toContain(known);
    }
  });

  it('warns once per unknown name, but separately for different names', () => {
    warnOnInvalidProps({material: 'obsidian' as LiquidGlassMaterial});
    warnOnInvalidProps({material: 'obsidian' as LiquidGlassMaterial});
    expect(warn).toHaveBeenCalledTimes(1);
    warnOnInvalidProps({material: 'quartz' as LiquidGlassMaterial});
    expect(warn).toHaveBeenCalledTimes(2);
  });

  // KNOWN DEFECT (not fixed here — reported instead).
  //
  // `devWarnings.ts` tests membership with `props.material in LIQUID_GLASS_MATERIALS`. The `in`
  // operator walks the prototype chain, and `Object.freeze({...})` does not remove
  // `Object.prototype`, so `material="toString"` / `"constructor"` / `"valueOf"` are treated as
  // known materials and the unknown-material warning is suppressed. Resolution itself is safe by
  // accident: `LIQUID_GLASS_MATERIALS['toString']` is a function whose optical keys are all
  // `undefined`, so every value still falls through to `LIQUID_GLASS_DEFAULTS`.
  //
  // Fix: `Object.prototype.hasOwnProperty.call(LIQUID_GLASS_MATERIALS, props.material)`.
  // `it.failing` asserts the *correct* behaviour: this test passes while the defect exists and
  // starts failing the moment it is fixed, at which point drop the `.failing`.
  it.failing('does not mistake an inherited Object.prototype key for a material', () => {
    warnOnInvalidProps({material: 'toString' as LiquidGlassMaterial});
    expect(messages()).toContain('Unknown material');
  });

  it('still resolves an inherited-key material to the plain defaults', () => {
    // Guards the "safe by accident" half of the defect above: whatever the warning does, the
    // resolved prop set must never contain a function or an `undefined`.
    const resolved = resolveLiquidGlassProps({material: 'toString' as LiquidGlassMaterial});
    expect(resolved).toEqual(LIQUID_GLASS_DEFAULTS);
  });
});

describe('warnOnInvalidProps — `effect` deprecation', () => {
  it('stays silent for `effect` alone, which is still supported through 0.x', () => {
    warnOnInvalidProps({effect: 'satin'});
    expect(warn).not.toHaveBeenCalled();
  });

  it('stays silent for `material` alone', () => {
    warnOnInvalidProps({material: 'satin'});
    expect(warn).not.toHaveBeenCalled();
  });

  it('warns when `material` and `effect` are combined', () => {
    warnOnInvalidProps({material: 'satin', effect: 'clear'});
    expect(warn).toHaveBeenCalledTimes(1);
    const message = messages();
    expect(message).toContain('deprecated');
    expect(message).toContain('`effect` wins');
  });

  it('warns only once across renders', () => {
    warnOnInvalidProps({material: 'satin', effect: 'clear'});
    warnOnInvalidProps({material: 'crystal', effect: 'none'});
    expect(warn).toHaveBeenCalledTimes(1);
  });
});

describe('warnOnInvalidProps — refraction exclusion', () => {
  it('accepts a valid circular exclusion', () => {
    warnOnInvalidProps({
      refractionExclusion: {shape: 'circle', centerX: 0.5, centerY: 0.5, radius: 44, feather: 8},
    });
    expect(warn).not.toHaveBeenCalled();
  });

  it('warns for normalized centres outside the view', () => {
    warnOnInvalidProps({
      refractionExclusion: {shape: 'circle', centerX: -0.1, centerY: 1.1, radius: 44},
    });
    expect(messages()).toContain('centerX');
    expect(messages()).toContain('centerY');
  });

  it('warns for negative radius and feather values', () => {
    warnOnInvalidProps({
      refractionExclusion: {shape: 'circle', centerX: 0.5, centerY: 0.5, radius: -1, feather: -2},
    });
    expect(messages()).toContain('radius');
    expect(messages()).toContain('feather');
  });
});

describe('warnMissingScene', () => {
  it('explains the consequence and the fix', () => {
    warnMissingScene();
    expect(warn).toHaveBeenCalledTimes(1);
    const message = messages();
    expect(message).toContain('<LiquidGlassView>');
    expect(message).toContain('<LiquidGlassScene>');
    expect(message).toContain('nothing to refract');
  });

  it('warns once no matter how many views are mounted outside a scene', () => {
    warnMissingScene();
    warnMissingScene();
    warnMissingScene();
    expect(warn).toHaveBeenCalledTimes(1);
  });
});
