module.exports = {
  dependency: {
    platforms: {
      android: {
        sourceDir: './android',
        packageImportPath:
          'import io.github.griffinkrutherford.liquidglass.react.LiquidGlassPackage;',
        packageInstance: 'new LiquidGlassPackage()',
      },
    },
  },
};
