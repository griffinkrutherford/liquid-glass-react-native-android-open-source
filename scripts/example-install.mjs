#!/usr/bin/env node
/**
 * Builds and packs the library, then installs the resulting tarball into
 * `example-react-native/`.
 *
 * The example app must depend on the packed tarball, never on a workspace or
 * relative source path, so that it exercises exactly what an external consumer
 * gets from npm: the `files` allowlist, autolinking config, Kotlin sources and
 * codegen spec. Running this script is the only supported way to refresh the
 * example's copy of the library.
 *
 * No JDK or Android SDK is required. This installs JavaScript dependencies
 * only; building the APK is a separate step (`npm run example:android`).
 */
import {execFileSync} from 'node:child_process';
import {existsSync, readdirSync, readFileSync, rmSync} from 'node:fs';
import {dirname, join, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const EXAMPLE = join(ROOT, 'example-react-native');
const skipBuild = process.argv.includes('--skip-build');

const run = (cmd, args, cwd) => {
  console.log(`\n$ ${cmd} ${args.join(' ')}  (in ${cwd === ROOT ? '.' : 'example-react-native'})`);
  execFileSync(cmd, args, {cwd, stdio: 'inherit'});
};

const pkg = JSON.parse(readFileSync(join(ROOT, 'package.json'), 'utf8'));
const {name} = pkg;

if (!existsSync(EXAMPLE)) {
  console.error(`example app not found at ${EXAMPLE}`);
  process.exit(1);
}

// 1. Build (bob: commonjs + module + typescript + Fabric codegen).
if (skipBuild) {
  console.log('Skipping build (--skip-build); packing the existing lib/ output.');
} else {
  run('npm', ['run', 'build'], ROOT);
}

// 2. Remove tarballs from previous runs so the example cannot silently keep
//    depending on a stale one.
for (const entry of readdirSync(ROOT)) {
  if (entry.endsWith('.tgz') && entry.includes('liquid-glass-android')) {
    rmSync(join(ROOT, entry));
    console.log(`removed stale tarball ${entry}`);
  }
}

// 3. Pack. `--ignore-scripts` avoids re-running `prepare`, which would rebuild
//    what step 1 just built.
const tarball = execFileSync('npm', ['pack', '--ignore-scripts', '--silent'], {
  cwd: ROOT,
  encoding: 'utf8',
})
  .trim()
  .split('\n')
  .pop()
  .trim();

const tarballPath = join(ROOT, tarball);
if (!existsSync(tarballPath)) {
  console.error(`npm pack did not produce ${tarballPath}`);
  process.exit(1);
}
console.log(`\npacked ${tarball}`);

// 4. Drop any previously installed copy. npm keys `file:` installs by tarball
//    integrity, and re-packing the same version produces a new hash; deleting
//    the installed directory makes the refresh unconditional.
const installed = join(EXAMPLE, 'node_modules', ...name.split('/'));
if (existsSync(installed)) {
  rmSync(installed, {recursive: true, force: true});
  console.log(`removed previously installed ${name}`);
}

// 5. Install. `--save` rewrites the example's dependency spec to the tarball
//    that was just produced, which keeps it correct across version bumps.
run('npm', ['install', `../${tarball}`, '--save', '--no-audit', '--no-fund'], EXAMPLE);

// 6. Report the recorded spec so a version drift is visible in the log.
const examplePkg = JSON.parse(readFileSync(join(EXAMPLE, 'package.json'), 'utf8'));
const spec = examplePkg.dependencies?.[name];
console.log(`\nexample-react-native now depends on: ${name}@${spec}`);
if (!spec || !spec.startsWith('file:')) {
  console.error('expected a file: tarball dependency; refusing to leave the example in this state');
  process.exit(1);
}

// Guard the whole point of this example: no source-path aliasing. Comments are
// stripped first so the file is free to explain why the aliases are absent.
const metro = readFileSync(join(EXAMPLE, 'metro.config.js'), 'utf8')
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .replace(/(^|[^:])\/\/.*$/gm, '$1');
if (/watchFolders|extraNodeModules/.test(metro)) {
  console.error(
    'metro.config.js aliases the library source. The example must resolve the installed tarball.',
  );
  process.exit(1);
}

console.log('\nDone. Next: npm run example:android  (requires a JDK 17 + Android SDK).');
