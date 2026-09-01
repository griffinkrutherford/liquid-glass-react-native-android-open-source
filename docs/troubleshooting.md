# Troubleshooting

Nothing on this page has an automated fix. Each entry names the symptom, the
cause, and the check that distinguishes them.

## Build: unresolved reference to the generated delegate

```
e: .../LiquidGlassViewManager.kt: Unresolved reference: RNLiquidGlassViewManagerDelegate
e: .../LiquidGlassViewManager.kt: Unresolved reference: RNLiquidGlassViewManagerInterface
e: .../LiquidGlassSceneManager.kt: Unresolved reference: RNLiquidGlassSceneManagerDelegate
```

`RNLiquidGlass*ManagerDelegate` and `RNLiquidGlass*ManagerInterface` are
produced by React Native's Fabric codegen from the specs in `src/`. They do not
exist in the repository. For the consumer's build to produce them, the library's
`android/build.gradle` must do two things:

```groovy
// 1. run codegen for this module
apply plugin: 'com.facebook.react'

android {
  sourceSets {
    main.java.srcDirs += [
      // 2. compile what codegen wrote
      layout.buildDirectory.dir('generated/source/codegen/java').get().asFile
    ]
  }
}
```

Both are present in the shipped `android/build.gradle`. If you see this error,
check in order:

1. `node_modules/@griffinkrutherford/liquid-glass-android/android/build.gradle`
   actually contains both lines. An old cached version of the package will not.
2. `package.json` in the installed package still has `codegenConfig` with
   `"jsSrcsDir": "src"`, and the `src/` directory is present. `npm run
   test:pack` asserts both.
3. The consumer app's `android/settings.gradle` includes the
   `@react-native/gradle-plugin` composite build. Every app created from the
   React Native template does; a heavily customised one may not, and the
   `com.facebook.react` plugin is supplied by it.

Then rebuild clean:

```shell
cd android && ./gradlew clean && cd ..
npx react-native run-android
```

This was a real bug in `0.1.0`: without those two lines a consumer's build
failed with exactly the errors above even though the library's own CI passed,
because the library's local Gradle build compiles the Kotlin sources through a
different path. If you are on `0.1.0`, upgrade.

## Build: the package is not autolinked at all

Symptom: the app builds, but rendering `LiquidGlassView` throws an
"unimplemented component" error, or `RNLiquidGlassView` is not a registered view
manager.

Check that autolinking sees the package:

```shell
npx react-native config | node -e "
  const c = JSON.parse(require('fs').readFileSync(0, 'utf8'));
  console.log(c.dependencies['@griffinkrutherford/liquid-glass-android']);
"
```

It must report an Android entry with `sourceDir` pointing at the package's
`android` directory. That comes from the shipped `react-native.config.js`:

```js
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
```

Causes, in order of likelihood:

- The app has a root `react-native.config.js` with a `dependencies` block that
  overrides or excludes this package.
- The package was installed but Gradle was not re-run. Autolinking is resolved
  at configure time; a JavaScript-only reload will not pick up a new native
  dependency.
- Metro is serving a stale bundle. `npx react-native start --reset-cache`.

## Build: Gradle and toolchain

| Symptom | Cause |
| --- | --- |
| `Unsupported class file major version` / Kotlin daemon failures | Not JDK 17. Set `JAVA_HOME` to a JDK 17 and re-run. Newer JDKs are not supported by the Gradle and AGP versions used here. |
| Kotlin version conflicts with another native dependency | Set `kotlinVersion` in the app's root `build.gradle` `ext` block. The library reads it through `safeExtGet` and falls back to `2.0.21`. |
| `compileSdk` / `minSdk` mismatch with the app | Set `compileSdkVersion`, `minSdkVersion`, `targetSdkVersion` in the app's root `ext`. The library reads all three, defaulting to 35 / 23 / 35. |
| `minSdk` conflict from a *different* dependency | The library's floor is 23. If something else requires a higher floor, raise the app's `minSdkVersion`; the library follows it. |

## Runtime: the glass renders but does not refract

You are seeing the static gradient fallback. There are four reasons, and they
are distinguishable:

1. **The device is below Android 13 (API 33).** Check
   `isLiquidGlassSupported` — it is `false`. This is expected; see
   [compatibility.md](compatibility.md). Nothing you change in props will help.
2. **The view has no `LiquidGlassScene` ancestor.** A `__DEV__` console warning
   names this, and Logcat carries `LiquidGlassView: LiquidGlassView has no
   LiquidGlassScene ancestor` on debuggable builds. Wrap the screen in a scene.
3. **There is nothing behind the glass to capture.** The scene captures only its
   non-glass children. A scene whose only child is the glass itself captures a
   transparent bitmap, and transparent refracted is still transparent. Give the
   scene a `backgroundColor` or a non-glass child.
4. **The canvas is not hardware accelerated.** The shader path requires it. This
   normally only happens when an ancestor forces a software layer, or under
   screenshot and screen-recording paths that render in software — which is why
   a recorded video may not show the effect that is visible on the device.

## Runtime: the backdrop is stale or frozen

The glass shows content from an earlier frame.

- If the content behind it is video, a camera preview, a map, or anything else
  backed by a `SurfaceView`/`TextureView`, this is a documented limitation of
  the current dirty tracking. See [performance.md](performance.md).
- If it is ordinary React Native content, this is a bug worth reporting. Include
  the scene structure, because whether the changing content is a *direct* child
  of the scene matters.

## Architecture: New vs Old

Both are supported by the same Kotlin sources. The manager implements the
generated Fabric interface *and* carries `@ReactProp` annotations, so Fabric
uses the generated delegate and the legacy path uses the annotations.

In the consuming app, `android/gradle.properties`:

```properties
newArchEnabled=true     # New Architecture (Fabric)
newArchEnabled=false    # Old Architecture
```

Changing it changes generated code, so a clean rebuild is required:

```shell
cd android && ./gradlew clean && cd ..
npx react-native run-android
```

Or without editing the file:

```shell
cd android && ./gradlew clean assembleDebug -PnewArchEnabled=false
```

Note that codegen still runs under the Old Architecture — the generated
interfaces are compiled either way. A build that fails only with
`newArchEnabled=false` is not a codegen problem.

## The package is Metro-only

`lib/commonjs/index.js` and `lib/module/index.js` `require` a sibling
`LiquidGlassNativeComponent` that is shipped as **uncompiled TypeScript**. This
is deliberate: React Native's Fabric codegen reads the `.ts` spec, and
`react-native-builder-bob`'s codegen target copies it rather than transpiling
it.

The consequence is that the package resolves only under Metro, which handles
`.ts` and the `react-native` field. It will **not** load under:

- plain Node (`node -e "require('@griffinkrutherford/liquid-glass-android')"`),
- Jest without React Native's preset,
- server-side rendering, or any bundler configured for a browser target.

For Jest, use React Native's preset and let it transform the package:

```js
// jest.config.js
module.exports = {
  preset: 'react-native',
  transformIgnorePatterns: [
    'node_modules/(?!(?:@react-native|react-native|@griffinkrutherford)/)',
  ],
};
```

If you only need the numbers in a non-Metro context — a design-token script, a
Storybook web build — import from the source module directly rather than the
package root; `materials.ts` has no React Native runtime dependency beyond the
`ColorValue` type.

## Reporting a problem

Use the [bug report template](../.github/ISSUE_TEMPLATE/bug_report.yml). A
report is only actionable with:

- library version, React Native version, New or Old Architecture;
- Android API level and device or emulator, and GPU if known;
- `isLiquidGlassSupported` as logged on the device;
- the smallest scene that reproduces it — a `LiquidGlassScene`, one non-glass
  child, one `LiquidGlassView`, and the exact props;
- for build failures, the full Gradle output with `--stacktrace`, not the
  summary line.

Security issues go to [SECURITY.md](../SECURITY.md) instead, privately.
