import {readFileSync} from 'node:fs';
import {join} from 'node:path';

import {LIQUID_GLASS_DEFAULTS} from '../materials';

/**
 * Drift guard.
 *
 * The default value of every public prop is written down in four places that must agree:
 *
 *   1. `LIQUID_GLASS_DEFAULTS`                    — the runtime fallback used by JS resolution.
 *   2. `WithDefault<T, D>` in the Fabric spec     — what codegen bakes into the native delegate.
 *   3. the `@default` JSDoc tag in the Fabric spec — what a reader of the spec believes.
 *   4. the `@default` JSDoc tag on the public prop — what a reader of the API believes.
 *
 * Nothing in the type system connects them, and (2) is the one that silently changes native
 * behaviour. So the source files are parsed as text and the four are compared directly. Parsing
 * text is deliberate: reading the compiled types back would only prove TypeScript agrees with
 * itself, not that the documented and codegen'd numbers match.
 */

const SRC = join(__dirname, '..');
const SPEC_SOURCE = readFileSync(join(SRC, 'LiquidGlassNativeComponent.ts'), 'utf8');
const MATERIALS_SOURCE = readFileSync(join(SRC, 'materials.ts'), 'utf8');

/** Parses a TypeScript literal (`32`, `2.4`, `true`, `'system'`) into its runtime value. */
function parseLiteral(literal: string): unknown {
  const text = literal.trim();
  if (text === 'true') return true;
  if (text === 'false') return false;
  if (/^'[^']*'$/.test(text) || /^"[^"]*"$/.test(text)) return text.slice(1, -1);
  const numeric = Number(text);
  if (text !== '' && !Number.isNaN(numeric)) return numeric;
  return text;
}

/** `propName -> value` for every `name?: WithDefault<Type, Default>` declaration. */
function parseCodegenDefaults(source: string): Map<string, unknown> {
  const result = new Map<string, unknown>();
  const declaration = /(\w+)\?:\s*WithDefault<([^;]*)>;/g;
  let match: RegExpExecArray | null;
  while ((match = declaration.exec(source)) !== null) {
    const [, name, args] = match;
    // The default is the last comma-separated argument; the type before it may itself be a
    // union containing commas is not possible here, but string unions do contain `|`.
    const separator = args!.lastIndexOf(',');
    if (separator === -1) continue;
    result.set(name!, parseLiteral(args!.slice(separator + 1)));
  }
  return result;
}

/** `propName -> value` for every optional property preceded by a JSDoc block with `@default`. */
function parseDocumentedDefaults(source: string): Map<string, unknown> {
  const result = new Map<string, unknown>();
  const documented = /\/\*\*([\s\S]*?)\*\/\s*(\w+)\?:/g;
  let match: RegExpExecArray | null;
  while ((match = documented.exec(source)) !== null) {
    const [, doc, name] = match;
    const tag = /@default[ \t]+(.+)/.exec(doc!);
    if (!tag) continue;
    // Strip a trailing `*/` fragment (single-line JSDoc) and any prose after an em dash.
    const value = tag[1]!.replace(/\*\/\s*$/, '').split('—')[0]!.trim();
    result.set(name!, parseLiteral(value));
  }
  return result;
}

/** The body of `export interface Name { ... }`, matched by brace depth. */
function interfaceBody(source: string, name: string): string {
  const start = source.indexOf(`export interface ${name}`);
  if (start === -1) throw new Error(`interface ${name} not found`);
  const open = source.indexOf('{', start);
  let depth = 0;
  for (let i = open; i < source.length; i++) {
    if (source[i] === '{') depth++;
    else if (source[i] === '}' && --depth === 0) return source.slice(open + 1, i);
  }
  throw new Error(`unterminated interface ${name}`);
}

const SPEC_BODY = interfaceBody(SPEC_SOURCE, 'NativeProps');
const PUBLIC_BODY = interfaceBody(MATERIALS_SOURCE, 'LiquidGlassStyleProps');

const codegenDefaults = parseCodegenDefaults(SPEC_BODY);
const specDocDefaults = parseDocumentedDefaults(SPEC_BODY);
const publicDocDefaults = parseDocumentedDefaults(PUBLIC_BODY);

/** Every prop declared in the Fabric spec (`WithDefault` or not). */
const specProps = [...SPEC_BODY.matchAll(/^\s*(\w+)\?:/gm)].map((m) => m[1]!);
const defaultKeys = Object.keys(LIQUID_GLASS_DEFAULTS);
const NATIVE_ONLY_DEFAULTS: Readonly<Record<string, unknown>> = {
  exclusionEnabled: false,
  exclusionCenterX: 0.5,
  exclusionCenterY: 0.5,
  exclusionRadius: 0,
  exclusionFeather: 0,
};

describe('the parser itself found something to check', () => {
  it('found the spec props', () => {
    expect(specProps.length).toBeGreaterThan(10);
  });

  it('found WithDefault declarations and JSDoc defaults', () => {
    expect(codegenDefaults.size).toBeGreaterThan(10);
    expect(specDocDefaults.size).toBeGreaterThan(10);
    expect(publicDocDefaults.size).toBeGreaterThan(10);
  });
});

describe('Fabric spec coverage', () => {
  it('declares exactly the props in LIQUID_GLASS_DEFAULTS', () => {
    expect([...specProps].sort()).toEqual(
      [...defaultKeys, ...Object.keys(NATIVE_ONLY_DEFAULTS)].sort(),
    );
  });

  it('never declares `material`, which is resolved in JavaScript', () => {
    expect(specProps).not.toContain('material');
  });

  it('documents a default for every prop it declares', () => {
    expect([...specDocDefaults.keys()].sort()).toEqual([...specProps].sort());
  });
});

describe.each(Object.entries(NATIVE_ONLY_DEFAULTS))('%s native exclusion primitive', (prop, expected) => {
  it('keeps its codegen and documented defaults aligned', () => {
    expect(codegenDefaults.get(prop)).toBe(expected);
    expect(specDocDefaults.get(prop)).toBe(expected);
  });
});

describe.each(defaultKeys)('%s', (prop) => {
  const expected = LIQUID_GLASS_DEFAULTS[prop as keyof typeof LIQUID_GLASS_DEFAULTS];

  it('has a documented default in the Fabric spec that matches LIQUID_GLASS_DEFAULTS', () => {
    expect(specDocDefaults.get(prop)).toBe(expected);
  });

  it('has a documented default on the public prop that matches LIQUID_GLASS_DEFAULTS', () => {
    expect(publicDocDefaults.get(prop)).toBe(expected);
  });

  it('is codegen-defaulted to the same value, or is a colour with no WithDefault wrapper', () => {
    // `ColorValue` cannot be wrapped in `WithDefault`, so `tintColor` has no codegen default and
    // relies on JavaScript always sending a concrete value.
    if (!codegenDefaults.has(prop)) {
      expect(prop).toBe('tintColor');
      expect(SPEC_BODY).toMatch(/tintColor\?:\s*ColorValue;/);
      return;
    }
    expect(codegenDefaults.get(prop)).toBe(expected);
  });
});

describe('public prop documentation', () => {
  it('documents `material` as having no default', () => {
    expect(publicDocDefaults.get('material')).toBe('undefined');
  });

  it('documents a default for every public prop except `material`', () => {
    const publicProps = [...PUBLIC_BODY.matchAll(/^\s*(\w+)\?:/gm)].map((m) => m[1]!);
    expect([...publicDocDefaults.keys()].sort()).toEqual([...publicProps].sort());
    expect(publicProps.sort()).toEqual([...defaultKeys, 'material', 'refractionExclusion'].sort());
  });

  it('documents refractionExclusion as opt-in', () => {
    expect(publicDocDefaults.get('refractionExclusion')).toBe('undefined');
  });
});

describe('units and ranges are stated where they are not obvious', () => {
  it.each(['cornerRadius', 'refractionStrength', 'bevelDepth', 'thickness', 'blurRadius'])(
    'documents %s in dp in the Fabric spec',
    (prop) => {
      const doc = new RegExp(`/\\*\\*([^*]|\\*(?!/))*\\*/\\s*${prop}\\?:`).exec(SPEC_BODY);
      expect(doc?.[0]).toContain('dp');
    },
  );

  it('documents animationDuration in milliseconds', () => {
    expect(/@default 320/.test(SPEC_BODY)).toBe(true);
    expect(/milliseconds[\s\S]*animationDuration\?:/.test(SPEC_BODY)).toBe(true);
  });
});
