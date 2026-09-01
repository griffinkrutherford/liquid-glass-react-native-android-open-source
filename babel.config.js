/**
 * Babel configuration for the repository's Jest run only.
 *
 * The published JavaScript is built by `react-native-builder-bob`, which compiles with
 * `configFile: false` and its own preset, so this file cannot affect the contents of `lib/`
 * or of the npm tarball. It exists purely so `babel-jest` can transform the TypeScript/JSX
 * sources in `src/` and `src/__tests__/` the same way Metro would in a real application.
 */
module.exports = {
  presets: ['module:@react-native/babel-preset'],
};
