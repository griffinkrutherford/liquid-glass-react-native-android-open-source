/**
 * Jest configuration for the library's JavaScript/TypeScript unit tests.
 *
 * These tests cover the parts of the public surface that live in JavaScript and therefore need
 * no JDK, Android SDK, emulator or device: material preset resolution, development warnings,
 * the non-Android fallback rendering path, and a drift guard tying the Fabric spec's documented
 * defaults to `LIQUID_GLASS_DEFAULTS`.
 *
 * The `react-native` preset is used so the sources are resolved, transformed and mocked exactly
 * as Metro/Jest would in a consuming application (`__DEV__` defined, `Platform` resolvable,
 * `codegenNativeComponent` returning a mock host component).
 */
module.exports = {
  preset: 'react-native',
  // `lib/` is build output of `src/`; without this, Jest's haste map sees duplicate module names.
  modulePathIgnorePatterns: [
    '<rootDir>/lib/',
    '<rootDir>/example-react-native/',
    '<rootDir>/android/build/',
  ],
  testMatch: ['<rootDir>/src/__tests__/**/*.test.{ts,tsx}'],
  moduleFileExtensions: ['ts', 'tsx', 'js', 'jsx', 'json', 'node'],
  clearMocks: true,
  collectCoverageFrom: ['src/**/*.{ts,tsx}', '!src/__tests__/**'],
};
