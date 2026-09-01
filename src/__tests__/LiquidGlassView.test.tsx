import React from 'react';
import {Platform, StyleSheet, Text} from 'react-native';
import TestRenderer from 'react-test-renderer';
import type {ReactTestRenderer, ReactTestRendererJSON} from 'react-test-renderer';

import {LiquidGlassScene, LiquidGlassView} from '../index';
import {resetWarnings} from '../devWarnings';
import {LIQUID_GLASS_DEFAULTS, LIQUID_GLASS_MATERIALS} from '../materials';

/**
 * Rendering behaviour of the public components.
 *
 * Two things matter here and neither needs a device:
 *
 * 1. On Android the fully-resolved prop set reaches the native host component.
 * 2. On every other platform the component degrades to a plain `View` — and the glass props must
 *    not ride along, because React Native forwards unknown props to the host view and a stray
 *    `thickness`/`tintColor` on a plain `View` is at best noise and at worst a native error.
 */

const ORIGINAL_OS = Platform.OS;

/** Every prop that belongs to the glass and must never appear on a fallback `View`. */
const GLASS_ONLY_PROPS = [...Object.keys(LIQUID_GLASS_DEFAULTS), 'material'];

function setPlatform(os: 'android' | 'ios' | 'web'): void {
  (Platform as unknown as {OS: string}).OS = os;
}

function render(element: React.ReactElement): ReactTestRenderer {
  let renderer!: ReactTestRenderer;
  TestRenderer.act(() => {
    renderer = TestRenderer.create(element);
  });
  return renderer;
}

/** The single host element rendered for `element`, as `toJSON` sees it. */
function renderHost(element: React.ReactElement): ReactTestRendererJSON {
  const json = render(element).toJSON();
  if (json == null || Array.isArray(json)) {
    throw new Error('expected exactly one host element');
  }
  return json;
}

let warn: jest.SpyInstance;

beforeEach(() => {
  resetWarnings();
  warn = jest.spyOn(console, 'warn').mockImplementation(() => {});
});

afterEach(() => {
  warn.mockRestore();
  setPlatform(ORIGINAL_OS as 'android' | 'ios');
});

describe('LiquidGlassView on Android', () => {
  beforeEach(() => setPlatform('android'));

  it('renders the native host component', () => {
    expect(renderHost(<LiquidGlassView />).type).toBe('RNLiquidGlassView');
  });

  it('passes a concrete value for every prop in the resolved set', () => {
    const host = renderHost(<LiquidGlassView />);
    for (const [key, value] of Object.entries(LIQUID_GLASS_DEFAULTS)) {
      expect(host.props).toHaveProperty(key, value);
    }
  });

  it('passes the resolved preset values, not the preset name', () => {
    const host = renderHost(<LiquidGlassView material="satin" />);
    for (const [key, value] of Object.entries(LIQUID_GLASS_MATERIALS.satin)) {
      expect(host.props).toHaveProperty(key, value);
    }
    // `material` is deliberately absent from the Fabric spec: preset merging happens in JS.
    expect(host.props).not.toHaveProperty('material');
  });

  it('forwards standard View props and style unchanged', () => {
    const style = {width: 100, height: 40};
    const host = renderHost(
      <LiquidGlassView testID="glass" accessibilityLabel="Glass card" style={style} />,
    );
    expect(host.props.testID).toBe('glass');
    expect(host.props.accessibilityLabel).toBe('Glass card');
    expect(StyleSheet.flatten(host.props.style)).toMatchObject(style);
  });

  it('does not turn cornerRadius into a borderRadius style — native draws the SDF corners', () => {
    const host = renderHost(<LiquidGlassView cornerRadius={12} />);
    expect(host.props.cornerRadius).toBe(12);
    expect(StyleSheet.flatten(host.props.style) ?? {}).not.toHaveProperty('borderRadius');
  });

  it('renders children inside the native host', () => {
    const host = renderHost(
      <LiquidGlassView>
        <Text>inside</Text>
      </LiquidGlassView>,
    );
    expect(JSON.stringify(host.children)).toContain('inside');
  });
});

describe('LiquidGlassScene', () => {
  it('renders the native scene on Android', () => {
    setPlatform('android');
    expect(renderHost(<LiquidGlassScene />).type).toBe('RNLiquidGlassScene');
  });

  it('renders a plain View everywhere else', () => {
    setPlatform('ios');
    expect(renderHost(<LiquidGlassScene />).type).toBe('View');
  });

  it('forwards layout props and children on the fallback path', () => {
    setPlatform('ios');
    const host = renderHost(
      <LiquidGlassScene testID="scene" style={{flex: 1}}>
        <Text>behind</Text>
      </LiquidGlassScene>,
    );
    expect(host.props.testID).toBe('scene');
    expect(StyleSheet.flatten(host.props.style)).toMatchObject({flex: 1});
    expect(JSON.stringify(host.children)).toContain('behind');
  });
});

describe.each(['ios', 'web'] as const)('LiquidGlassView fallback on %s', (os) => {
  beforeEach(() => setPlatform(os));

  it('renders a plain View, not the native host component', () => {
    expect(renderHost(<LiquidGlassView />).type).toBe('View');
  });

  it('leaks no glass-only prop onto the View, even when every one is supplied', () => {
    const host = renderHost(
      <LiquidGlassView
        material="nocturne"
        effect="clear"
        cornerRadius={12}
        refractionStrength={10}
        dispersion={1}
        indexOfRefraction={1.2}
        bevelDepth={5}
        thickness={4}
        blurRadius={3}
        effectAmount={0.5}
        tintColor="#123456"
        tintAmount={0.2}
        interactive
        draggable
        animated={false}
        animationDuration={100}
        colorScheme="dark"
      />,
    );
    for (const prop of GLASS_ONLY_PROPS) {
      expect(host.props).not.toHaveProperty(prop);
    }
  });

  it('leaks nothing when no glass prop is supplied either', () => {
    const host = renderHost(<LiquidGlassView />);
    for (const prop of GLASS_ONLY_PROPS) {
      expect(host.props).not.toHaveProperty(prop);
    }
  });

  it('forwards cornerRadius as borderRadius so clipping stays comparable', () => {
    const host = renderHost(<LiquidGlassView cornerRadius={18} />);
    expect(StyleSheet.flatten(host.props.style)).toMatchObject({borderRadius: 18});
  });

  it('uses the default cornerRadius when none is supplied', () => {
    const host = renderHost(<LiquidGlassView />);
    expect(StyleSheet.flatten(host.props.style)).toMatchObject({
      borderRadius: LIQUID_GLASS_DEFAULTS.cornerRadius,
    });
  });

  it('honours an explicit cornerRadius of 0 rather than falling back to the default', () => {
    const host = renderHost(<LiquidGlassView cornerRadius={0} />);
    expect(StyleSheet.flatten(host.props.style)).toMatchObject({borderRadius: 0});
  });

  it('ignores the preset for cornerRadius, which presets never set', () => {
    const host = renderHost(<LiquidGlassView material="nocturne" />);
    expect(StyleSheet.flatten(host.props.style)).toMatchObject({
      borderRadius: LIQUID_GLASS_DEFAULTS.cornerRadius,
    });
  });

  it('lets a caller style override the injected borderRadius', () => {
    const host = renderHost(<LiquidGlassView cornerRadius={18} style={{borderRadius: 4}} />);
    expect(StyleSheet.flatten(host.props.style)).toMatchObject({borderRadius: 4});
  });

  it('preserves the rest of the caller style', () => {
    const host = renderHost(<LiquidGlassView style={{width: 80, opacity: 0.5}} />);
    expect(StyleSheet.flatten(host.props.style)).toMatchObject({width: 80, opacity: 0.5});
  });

  it('forwards standard View props', () => {
    const onLayout = jest.fn();
    const host = renderHost(
      <LiquidGlassView
        testID="glass"
        accessible
        accessibilityLabel="Glass card"
        accessibilityRole="button"
        pointerEvents="box-none"
        onLayout={onLayout}
      />,
    );
    expect(host.props.testID).toBe('glass');
    expect(host.props.accessible).toBe(true);
    expect(host.props.accessibilityLabel).toBe('Glass card');
    expect(host.props.onLayout).toBe(onLayout);
  });

  it('renders children', () => {
    const host = renderHost(
      <LiquidGlassView>
        <Text>inside</Text>
      </LiquidGlassView>,
    );
    expect(JSON.stringify(host.children)).toContain('inside');
  });
});

describe('missing-scene development warning', () => {
  const missingSceneWarnings = () =>
    warn.mock.calls.map((call) => String(call[0])).filter((m) => m.includes('<LiquidGlassScene>'));

  it.each(['android', 'ios'] as const)('fires on %s outside a scene', (os) => {
    setPlatform(os);
    render(<LiquidGlassView />);
    expect(missingSceneWarnings()).toHaveLength(1);
  });

  it('stays silent for a direct child of a scene', () => {
    setPlatform('android');
    render(
      <LiquidGlassScene>
        <LiquidGlassView />
      </LiquidGlassScene>,
    );
    expect(missingSceneWarnings()).toHaveLength(0);
  });

  it('stays silent for a deeply nested descendant of a scene', () => {
    setPlatform('ios');
    render(
      <LiquidGlassScene>
        <LiquidGlassScene>
          <LiquidGlassView>
            <LiquidGlassView />
          </LiquidGlassView>
        </LiquidGlassScene>
      </LiquidGlassScene>,
    );
    expect(missingSceneWarnings()).toHaveLength(0);
  });

  it('fires once, not once per unscoped view', () => {
    render(
      <>
        <LiquidGlassView />
        <LiquidGlassView />
        <LiquidGlassView />
      </>,
    );
    expect(missingSceneWarnings()).toHaveLength(1);
  });

  it('does not leak scene context to a sibling subtree', () => {
    render(
      <>
        <LiquidGlassScene>
          <LiquidGlassView />
        </LiquidGlassScene>
        <LiquidGlassView />
      </>,
    );
    expect(missingSceneWarnings()).toHaveLength(1);
  });

  it('is stripped entirely from release bundles, where __DEV__ is false', () => {
    const originalDev = (globalThis as {__DEV__?: boolean}).__DEV__;
    (globalThis as {__DEV__?: boolean}).__DEV__ = false;
    try {
      jest.isolateModules(() => {
        // The whole module graph is re-required inside the isolated registry, React and the
        // renderer included: mixing the outer React with an isolated component would leave the
        // hook dispatcher unset.
        const IsolatedReact = require('react');
        const IsolatedRenderer = require('react-test-renderer');
        const release = require('../index');
        IsolatedRenderer.act(() => {
          IsolatedRenderer.create(
            IsolatedReact.createElement(release.LiquidGlassView, {
              thickness: 9999,
              material: 'obsidian',
            }),
          );
        });
      });
      expect(warn).not.toHaveBeenCalled();
    } finally {
      (globalThis as {__DEV__?: boolean}).__DEV__ = originalDev;
    }
  });
});

describe('prop validation warnings reach the developer through the component', () => {
  it('warns about an out-of-range prop at render time', () => {
    render(<LiquidGlassScene><LiquidGlassView thickness={9999} /></LiquidGlassScene>);
    const messages = warn.mock.calls.map((call) => String(call[0])).join('\n');
    expect(messages).toContain('thickness');
    expect(messages).toContain('clamped natively');
  });

  it('still renders, because a warning must never break the app', () => {
    setPlatform('ios');
    const host = renderHost(<LiquidGlassView thickness={9999} indexOfRefraction={-5} />);
    expect(host.type).toBe('View');
  });
});
