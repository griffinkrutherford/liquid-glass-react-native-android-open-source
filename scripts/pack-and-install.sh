#!/usr/bin/env bash
#
# Packed-package install test for @griffinkrutherford/liquid-glass-android.
#
# Builds the library, packs it with `npm pack`, installs the resulting tarball
# into a throwaway fixture application in a temporary directory, and asserts
# that everything an Android consumer needs actually survived packing.
#
# This script deliberately does NOT run Gradle. It verifies packaging and
# module resolution only, so it runs on machines without a JDK or Android SDK.
# Compiling the Kotlin/Fabric sources is a separate, SDK-dependent check.
#
# Usage:
#   scripts/pack-and-install.sh [--skip-build] [--keep]
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG_NAME="$(node -p "require('$ROOT/package.json').name")"
PKG_VERSION="$(node -p "require('$ROOT/package.json').version")"

SKIP_BUILD=0
KEEP=0
for arg in "$@"; do
  case "$arg" in
    --skip-build) SKIP_BUILD=1 ;;
    --keep) KEEP=1 ;;
    *) echo "unknown argument: $arg" >&2; exit 2 ;;
  esac
done

FAILURES=0
step()  { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
pass()  { printf '  \033[32mok\033[0m   %s\n' "$*"; }
fail()  { printf '  \033[31mFAIL\033[0m %s\n' "$*"; FAILURES=$((FAILURES + 1)); }

WORKDIR="$(mktemp -d "${TMPDIR:-/tmp}/liquid-glass-pack-test.XXXXXX")"
cleanup() {
  if [ "$KEEP" -eq 1 ]; then
    printf '\nfixture kept at %s\n' "$WORKDIR"
  else
    rm -rf "$WORKDIR"
  fi
}
trap cleanup EXIT

# ---------------------------------------------------------------------------
# 1. Build
# ---------------------------------------------------------------------------
if [ "$SKIP_BUILD" -eq 0 ]; then
  step "Building $PKG_NAME@$PKG_VERSION (bob build, includes Fabric codegen)"
  (cd "$ROOT" && npm run build)
else
  step "Skipping build (--skip-build); using existing lib/ output"
fi

# ---------------------------------------------------------------------------
# 2. Pack
# ---------------------------------------------------------------------------
step "Packing tarball"
# --ignore-scripts: the build above already ran `prepare`; re-running it here
# would make the packed contents depend on script ordering.
TARBALL_NAME="$(cd "$ROOT" && npm pack --ignore-scripts --silent --pack-destination "$WORKDIR")"
TARBALL="$WORKDIR/$TARBALL_NAME"
[ -f "$TARBALL" ] || { echo "npm pack did not produce $TARBALL" >&2; exit 1; }
printf '  tarball: %s (%s)\n' "$TARBALL_NAME" "$(du -h "$TARBALL" | cut -f1)"

# Contents of the tarball, with the leading "package/" prefix stripped.
tar -tzf "$TARBALL" | sed 's|^package/||' | sed '/\/$/d' | sort > "$WORKDIR/tarball-files.txt"
printf '  %s files in tarball\n' "$(wc -l < "$WORKDIR/tarball-files.txt" | tr -d ' ')"

# ---------------------------------------------------------------------------
# 3. Clean fixture app + install
# ---------------------------------------------------------------------------
step "Installing tarball into a clean fixture app"
FIXTURE="$WORKDIR/fixture-app"
mkdir -p "$FIXTURE"
cat > "$FIXTURE/package.json" <<'JSON'
{
  "name": "liquid-glass-pack-fixture",
  "version": "0.0.0",
  "private": true,
  "description": "Throwaway consumer used to verify the packed tarball installs cleanly."
}
JSON

# --legacy-peer-deps keeps the fixture from downloading react-native itself:
# this test checks packaging and resolution, not a full app dependency tree.
# --ignore-scripts keeps a third-party install script from running here.
(cd "$FIXTURE" && npm install "$TARBALL" \
  --legacy-peer-deps --ignore-scripts --no-audit --no-fund --loglevel=error)

INSTALLED="$FIXTURE/node_modules/$PKG_NAME"
[ -d "$INSTALLED" ] || { echo "package did not install to $INSTALLED" >&2; exit 1; }

# ---------------------------------------------------------------------------
# 4. Assertions
# ---------------------------------------------------------------------------
step "Asserting the installed tree"

require_file() {
  if [ -f "$INSTALLED/$1" ]; then pass "$1"; else fail "missing file: $1"; fi
}
require_absent() {
  if [ -e "$INSTALLED/$1" ]; then fail "unexpected entry shipped: $1"; else pass "absent: $1"; fi
}

echo "-- package metadata"
require_file package.json
require_file README.md
require_file LICENSE

echo "-- documentation"
require_file docs/README.md
require_file docs/props.md
require_file docs/materials.md
require_file docs/compatibility.md
require_file docs/recipes.md
require_file docs/performance.md
require_file docs/troubleshooting.md
require_file docs/expo.md
require_file docs/accessibility.md

echo "-- autolinking"
require_file react-native.config.js

echo "-- android library module"
require_file android/build.gradle
require_file android/settings.gradle
require_file android/gradle.properties
require_file android/consumer-rules.pro
require_file android/src/main/AndroidManifest.xml

echo "-- react native bridge sources (Kotlin)"
RN_PKG_DIR="android/src/main/java/io/github/griffinkrutherford/liquidglass/react"
require_file "$RN_PKG_DIR/LiquidGlassPackage.kt"
require_file "$RN_PKG_DIR/LiquidGlassViewManager.kt"
require_file "$RN_PKG_DIR/LiquidGlassSceneManager.kt"

echo "-- android view + physics sources (Kotlin)"
VIEW_DIR="liquid-glass-view/src/main/java/io/github/griffinkrutherford/liquidglass"
require_file liquid-glass-view/src/main/AndroidManifest.xml
require_file "$VIEW_DIR/LiquidGlassView.kt"
require_file "$VIEW_DIR/LiquidGlassScene.kt"
require_file "$VIEW_DIR/LiquidGlassStyle.kt"
CORE_DIR="liquid-glass-core/src/main/kotlin/com/griffinkrutherford/liquidglass/core"
require_file "$CORE_DIR/LiquidMembrane.kt"
require_file "$CORE_DIR/LiquidSimulation.kt"
require_file "$CORE_DIR/LiquidPhysicsConfig.kt"
require_file "$CORE_DIR/FixedTimestepRunner.kt"
require_file "$CORE_DIR/SurfaceSnapshot.kt"

echo "-- javascript / typescript entry points"
require_file src/index.tsx
require_file src/LiquidGlassNativeComponent.ts
require_file src/LiquidGlassSceneNativeComponent.ts
require_file lib/commonjs/index.js
require_file lib/module/index.js
require_file lib/typescript/index.d.ts

echo "-- nothing that should never ship"
require_absent node_modules
require_absent android/app
require_absent sample-android
if grep -qE '(^|/)build/' "$WORKDIR/tarball-files.txt"; then
  fail "tarball contains build output directories:"
  grep -E '(^|/)build/' "$WORKDIR/tarball-files.txt" | sed 's/^/       /'
else
  pass "no build/ output in tarball"
fi

echo "-- declared entry points resolve"
node - "$FIXTURE" "$PKG_NAME" <<'NODE' || FAILURES=$((FAILURES + 1))
const path = require('path');
const fs = require('fs');
const [fixture, name] = process.argv.slice(2);
const dir = path.join(fixture, 'node_modules', name);
const pkg = JSON.parse(fs.readFileSync(path.join(dir, 'package.json'), 'utf8'));
let bad = 0;
const check = (field) => {
  const value = pkg[field];
  if (!value) { console.log(`  FAIL missing "${field}" field`); bad++; return; }
  const target = path.join(dir, value);
  if (fs.existsSync(target)) console.log(`  ok   ${field} -> ${value}`);
  else { console.log(`  FAIL ${field} -> ${value} does not exist`); bad++; }
};
['main', 'module', 'types', 'react-native', 'source'].forEach(check);

// require.resolve is what a consumer's bundler and `npm ls` rely on.
try {
  const resolved = require.resolve(name, {paths: [fixture]});
  console.log(`  ok   require.resolve -> ${path.relative(fs.realpathSync(dir), resolved)}`);
} catch (error) {
  console.log(`  FAIL require.resolve(${name}) threw: ${error.message}`);
  bad++;
}

// Fabric codegen configuration must survive packing, or the consumer's
// React Native Gradle plugin will not generate the ViewManager delegates.
const codegen = pkg.codegenConfig;
if (!codegen || codegen.name !== 'RNLiquidGlassSpec') {
  console.log('  FAIL codegenConfig.name is not RNLiquidGlassSpec');
  bad++;
} else if (!fs.existsSync(path.join(dir, codegen.jsSrcsDir))) {
  console.log(`  FAIL codegenConfig.jsSrcsDir "${codegen.jsSrcsDir}" is not in the tarball`);
  bad++;
} else {
  console.log(`  ok   codegenConfig ${codegen.name} (jsSrcsDir=${codegen.jsSrcsDir})`);
}

// react-native.config.js drives Android autolinking in the consumer app.
const cfg = require(path.join(dir, 'react-native.config.js'));
const android = cfg && cfg.dependency && cfg.dependency.platforms && cfg.dependency.platforms.android;
if (!android) {
  console.log('  FAIL react-native.config.js has no dependency.platforms.android');
  bad++;
} else {
  const sourceDir = path.join(dir, android.sourceDir || 'android');
  if (fs.existsSync(path.join(sourceDir, 'build.gradle'))) {
    console.log(`  ok   autolinking sourceDir -> ${android.sourceDir}`);
  } else {
    console.log(`  FAIL autolinking sourceDir "${android.sourceDir}" has no build.gradle`);
    bad++;
  }
  for (const field of ['packageImportPath', 'packageInstance']) {
    if (typeof android[field] === 'string' && android[field].length > 0) {
      console.log(`  ok   ${field}`);
    } else {
      console.log(`  FAIL react-native.config.js is missing ${field}`);
      bad++;
    }
  }
}

// The library's android/build.gradle reaches out of its own directory for the
// core and view sources. Those relative paths must resolve inside node_modules.
const gradle = fs.readFileSync(path.join(dir, 'android', 'build.gradle'), 'utf8');
const srcDirs = [...gradle.matchAll(/'(\.\.\/[^']+)'/g)].map((m) => m[1]);
if (srcDirs.length === 0) {
  console.log('  FAIL android/build.gradle declares no relative sourceSets');
  bad++;
}
for (const rel of srcDirs) {
  const target = path.resolve(dir, 'android', rel);
  if (fs.existsSync(target)) console.log(`  ok   sourceSet android/${rel}`);
  else { console.log(`  FAIL sourceSet android/${rel} is not in the tarball`); bad++; }
}

process.exit(bad === 0 ? 0 : 1);
NODE

# ---------------------------------------------------------------------------
# 5. Result
# ---------------------------------------------------------------------------
if [ "$FAILURES" -ne 0 ]; then
  printf '\n\033[31m%s check(s) failed.\033[0m The packed tarball is not consumable.\n' "$FAILURES" >&2
  exit 1
fi

printf '\n\033[32mPacked-package install test passed.\033[0m\n'
printf 'Note: Gradle was not run. Kotlin compilation, Fabric codegen in the\n'
printf 'consumer build, and on-device behaviour still require a JDK + Android SDK.\n'
