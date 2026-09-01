import {LIQUID_GLASS_MATERIALS} from './materials';
import type {LiquidGlassStyleProps} from './materials';

/**
 * Development-only diagnostics. Every export in this file is called from inside an `if (__DEV__)`
 * guard so bundlers strip the calls, and this module along with them, from release builds.
 */

const alreadyWarned = new Set<string>();

/** Logs `message` at most once per key for the lifetime of the JavaScript context. */
export function warnOnce(key: string, message: string): void {
  if (alreadyWarned.has(key)) return;
  alreadyWarned.add(key);
  console.warn(`[liquid-glass] ${message}`);
}

/** Test helper: forget which warnings have already been emitted. */
export function resetWarnings(): void {
  alreadyWarned.clear();
}

type RangeSpec = {
  min: number;
  max: number;
  unit: string;
};

/**
 * Documented ranges, mirroring the clamps applied natively by `LiquidGlassView`. Values outside
 * these ranges are not an error: native code clamps them. The warning exists so the clamp is
 * visible to the developer instead of silently changing the result.
 */
const RANGES: Readonly<Record<string, RangeSpec>> = {
  cornerRadius: {min: 0, max: Number.POSITIVE_INFINITY, unit: 'dp'},
  refractionStrength: {min: 0, max: 80, unit: 'dp'},
  dispersion: {min: 0, max: 12, unit: 'dimensionless'},
  indexOfRefraction: {min: 1.01, max: 3, unit: 'dimensionless'},
  bevelDepth: {min: 2, max: 48, unit: 'dp'},
  thickness: {min: 0, max: 64, unit: 'dp'},
  blurRadius: {min: 0, max: 12, unit: 'dp'},
  effectAmount: {min: 0, max: 1, unit: 'dimensionless'},
  tintAmount: {min: 0, max: 1, unit: 'dimensionless'},
  animationDuration: {min: 0, max: Number.POSITIVE_INFINITY, unit: 'ms'},
};

function describeRange(spec: RangeSpec): string {
  const max = spec.max === Number.POSITIVE_INFINITY ? '∞' : String(spec.max);
  return `${spec.min}–${max} ${spec.unit}`;
}

/**
 * Warns (never throws) about prop values that native code will clamp, unknown `material` names,
 * and the deprecated `material` + `effect` combination.
 */
export function warnOnInvalidProps(props: LiquidGlassStyleProps): void {
  for (const name of Object.keys(RANGES)) {
    const value = (props as Record<string, unknown>)[name];
    if (value === undefined || value === null) continue;
    const spec = RANGES[name]!;
    if (typeof value !== 'number' || Number.isNaN(value)) {
      warnOnce(
        `type:${name}`,
        `\`${name}\` must be a number (${describeRange(spec)}), received ${JSON.stringify(value)}.`,
      );
      continue;
    }
    if (value < spec.min || value > spec.max) {
      warnOnce(
        `range:${name}`,
        `\`${name}\` is ${value} but the supported range is ${describeRange(spec)}. ` +
          'The value will be clamped natively.',
      );
    }
  }

  const exclusion = props.refractionExclusion;
  if (exclusion !== undefined) {
    if (exclusion.shape !== 'circle') {
      warnOnce('exclusion:shape', '`refractionExclusion.shape` must be "circle".');
    }
    for (const [name, value, min, max, unit] of [
      ['centerX', exclusion.centerX, 0, 1, 'normalized'],
      ['centerY', exclusion.centerY, 0, 1, 'normalized'],
      ['radius', exclusion.radius, 0, Number.POSITIVE_INFINITY, 'dp'],
      ['feather', exclusion.feather ?? 0, 0, Number.POSITIVE_INFINITY, 'dp'],
    ] as const) {
      if (typeof value !== 'number' || !Number.isFinite(value) || value < min || value > max) {
        const upper = max === Number.POSITIVE_INFINITY ? '∞' : max;
        warnOnce(
          `exclusion:${name}`,
          `\`refractionExclusion.${name}\` must be ${min}–${upper} ${unit}; received ${String(value)}.`,
        );
      }
    }
  }

  if (props.material !== undefined && !(props.material in LIQUID_GLASS_MATERIALS)) {
    warnOnce(
      `material:${String(props.material)}`,
      `Unknown material "${String(props.material)}". Expected one of ` +
        `${Object.keys(LIQUID_GLASS_MATERIALS).join(', ')}. ` +
        'Falling back to LIQUID_GLASS_DEFAULTS for every prop the caller did not supply.',
    );
  }

  if (props.material !== undefined && props.effect !== undefined) {
    warnOnce(
      'material-and-effect',
      '`effect` is deprecated and was supplied together with `material`. `effect` wins for the ' +
        'shader variant, like any other explicitly supplied prop, but prefer `material` alone: ' +
        '`effect` is removed after the 0.x deprecation period.',
    );
  }
}

/** Warns once when a `LiquidGlassView` is rendered outside a `LiquidGlassScene`. */
export function warnMissingScene(): void {
  warnOnce(
    'missing-scene',
    'A <LiquidGlassView> was rendered outside a <LiquidGlassScene>. Without a scene there is no ' +
      'captured backdrop, so the glass has nothing to refract and renders as an empty pane. ' +
      'Wrap the screen (or the section containing the glass and the content behind it) in ' +
      '<LiquidGlassScene>.',
  );
}
