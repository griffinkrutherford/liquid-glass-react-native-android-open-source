const {getDefaultConfig, mergeConfig} = require('@react-native/metro-config');

/**
 * Metro configuration
 * https://reactnative.dev/docs/metro
 *
 * The library is consumed from node_modules as an installed tarball, so no
 * watchFolders / extraNodeModules aliasing is needed. That is deliberate: this
 * example must resolve the package exactly the way an external consumer does.
 *
 * @type {import('@react-native/metro-config').MetroConfig}
 */
const config = {};

module.exports = mergeConfig(getDefaultConfig(__dirname), config);
