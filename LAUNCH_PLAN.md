# React Native Liquid Glass — Launch Hardening Plan

## Objective

Prepare `@griffinkrutherford/liquid-glass-android` for a dependable public beta,
then stabilize its API and performance for a production-ready `1.0.0` release.
The optical quality is already compelling; this plan focuses on integration,
performance, lifecycle correctness, testing, documentation, and releases.

## Release strategy

Use incremental prereleases instead of treating the current prototype as a
stable API:

1. `0.2.0-beta.1`: real React Native example, installation proof, and baseline profiling.
2. `0.2.0`: corrected beta findings and documented support boundaries.
3. `0.x`: API refinement, performance work, and broader device coverage.
4. `1.0.0`: stable API, reliable upgrades, and a documented performance envelope.

Do not publish `1.0.0` until the public API and scene-composition rules can be
maintained without frequent breaking changes.

## Phase 1 — Prove real React Native integration

### Deliverables

- Create an `example-react-native` application that installs the packed library
  exactly as an external consumer would.
- Test both React Native architectures where supported.
- Verify Android autolinking and Fabric codegen from a clean checkout.
- Exercise debug and release variants.
- Add an Expo development-build example or documented verification procedure.
- Verify Fast Refresh, JavaScript reload, and application restart behavior.
- Test React Navigation screen mounting, unmounting, and transitions.
- Render text, images, icons, buttons, and nested React Native children inside glass.
- Test multiple glass elements sharing one scene.

### Exit criteria

- A clean machine can run the example using only documented commands.
- Installation succeeds from the generated npm tarball, not a workspace path.
- Old and New Architecture builds pass in CI.
- Mounting, navigating away, and returning produce no crash or stale backdrop.

## Phase 2 — Performance and memory hardening

### Measure

- UI and render-thread frame times.
- Dropped frames at 60, 90, and 120 Hz where hardware permits.
- CPU/GPU utilization while static, dragging, scrolling, and animating content.
- Bitmap allocations, retained memory, and garbage-collection pressure.
- Cost of one, three, and many glass elements.
- Behavior on high-density phones and tablets.
- Background video, animated images, and rapidly changing React Native content.

### Optimizations to evaluate

- Stop invalidating when the backdrop and membrane are both at rest.
- Resume rendering only after content, layout, material, or physics changes.
- Reuse all backdrop and normal-map allocations.
- Capture only dirty regions when that produces a measurable improvement.
- Share scene resources across multiple glass elements.
- Add adaptive physics-grid and capture resolution quality levels.
- Reduce sampling work for small or distant glass elements.
- Release GPU and bitmap resources promptly during detach and memory pressure.

### Initial performance gates

- No continuous CPU-heavy redraw when a screen is visually static.
- No per-frame bitmap allocation in the steady rendering path.
- A representative single-card screen sustains 60 FPS on a selected mid-range device.
- Three simultaneous cards stay within the documented frame and memory budgets.
- Repeated navigation does not cause unbounded memory growth.

## Phase 3 — Scene and lifecycle correctness

Test and document the following composition cases:

- `ScrollView`, `FlatList`, and nested scrolling.
- React Native transforms and animations.
- Clipping, opacity, elevation, and rounded containers.
- Orientation and window-size changes.
- Keyboard resizing and system window insets.
- App background/foreground transitions.
- React Native modals, portals, and navigation overlays.
- Transparent backgrounds and nested scenes.
- Overlapping glass elements.
- RTL layouts and font scaling.

Add development warnings for invalid or unsupported arrangements, including a
`LiquidGlassView` mounted outside `LiquidGlassScene`.

### Exit criteria

- Supported cases have automated or reproducible manual tests.
- Unsupported cases fail gracefully and are documented.
- Backdrop buffers resize and release correctly through lifecycle transitions.
- Nested controls remain touchable and accessible.

## Phase 4 — Stabilize the public API

Review naming, units, defaults, and clamping for every public prop:

- `effect`
- `thickness`
- `bevelDepth`
- `indexOfRefraction`
- `refractionStrength`
- `dispersion`
- `blurRadius`
- `effectAmount`
- `tintColor` and `tintAmount`
- `interactive` and `draggable`
- `animated` and `animationDuration`
- `colorScheme`

### Preset API

Provide simple presets for normal usage while retaining advanced overrides:

```tsx
<LiquidGlassView material="crystal" />
<LiquidGlassView material="satin" />
<LiquidGlassView material="nocturne" />
```

Define whether user-supplied optical props merge with or replace preset values.
Document all dimension props as React Native density-independent units and IOR
as a dimensionless physical value.

### Compatibility policy

- Publish the supported React Native version range.
- Publish the supported Android API range.
- Specify Android 13+ behavior and the older-device fallback.
- Define the iOS fallback contract.
- Use deprecation periods before removing public props after the beta phase.

## Phase 5 — Automated quality gates

### Unit and integration tests

- Physics determinism and fixed-timestep behavior.
- Optical-property clamping and React Native unit conversion.
- Material preset resolution and prop overrides.
- Backdrop allocation, reuse, resize, and disposal.
- Scene attachment and invalid composition warnings.
- Fabric codegen and Kotlin manager compilation.
- Old Architecture registration and prop dispatch.
- Packed npm installation in a clean fixture application.

### Visual regression tests

Capture stable reference scenes for:

- Crystal, Satin, and Nocturne materials.
- Minimum, default, and maximum thickness.
- Multiple bevel depths and IOR values.
- Straight edges, corners, and content crossing the glass boundary.
- Light and dark backgrounds.
- Android runtime shader and fallback renderer.

Use tolerances appropriate for GPU rendering while still detecting meaningful
changes in refraction, blur, tint, and edge highlights.

### CI matrix

- Minimum and current supported React Native versions.
- New Architecture enabled and disabled.
- Android API 23 fallback.
- Android API 32 fallback boundary.
- Android API 33 runtime-shader boundary.
- Latest stable Android API.
- Debug and release builds.

## Phase 6 — Documentation and developer experience

- Keep the one-time installation guide current.
- Add a complete props reference with defaults and valid ranges.
- Add recipes for cards, navigation bars, buttons, and floating controls.
- Document correct scene ordering and absolute positioning.
- Add performance recommendations for lists, video, and multiple elements.
- Add an Expo development-build guide.
- Add troubleshooting for autolinking, codegen, Gradle, and shader fallback.
- Document accessibility and reduced-motion behavior.
- Include before/after screenshots and a short high-quality demo video.
- Provide a minimal reproducible issue template.

## Phase 7 — Open-source and release operations

- Add `CONTRIBUTING.md` with local setup and validation commands.
- Add `CODE_OF_CONDUCT.md`.
- Add `SECURITY.md` with private vulnerability-reporting instructions.
- Add issue and pull-request templates.
- Protect `main` and require CI for pull requests.
- Adopt Changesets or an equivalent version/changelog workflow.
- Generate release notes and changelogs from reviewed release PRs.
- Configure trusted npm publishing or the `NPM_TOKEN` release secret.
- Preserve reproducible npm tarballs as release artifacts.
- Test the exact tarball before publishing it.
- Publish prereleases under an npm beta tag before promoting to latest.

## Phase 8 — External beta

Recruit at least two or three applications outside the repository to test:

- Installation and upgrades.
- Navigation and lifecycle behavior.
- Complex real-world screen composition.
- Mid-range device performance.
- Expo development builds where applicable.
- API clarity without direct maintainer assistance.

Track beta feedback by severity. Block the stable release on crashes, memory
growth, broken upgrades, incorrect touch handling, or repeatable severe frame
drops in supported configurations.

## Definition of beta-ready

- A real external-style React Native example builds in CI.
- Fabric and legacy manager compilation are continuously verified.
- Packed-package installation is tested.
- Static scenes do not perform unnecessary expensive rendering.
- Basic lifecycle, scrolling, navigation, and multiple-glass tests pass.
- Public props have documented defaults, units, and ranges.
- npm prerelease automation is configured.
- Known limitations are explicit.

## Definition of 1.0-ready

- The public API is considered stable and follows a documented compatibility policy.
- Supported React Native and Android versions pass the release matrix.
- Performance and memory budgets are measured and published.
- Visual regression coverage protects the core materials and optical controls.
- No open launch-blocking crash, leak, lifecycle, accessibility, or upgrade issue remains.
- External beta applications have successfully integrated and upgraded the package.
- Installation, troubleshooting, contribution, security, and release documentation are complete.

## Recommended execution order

1. Build the real React Native example and packed-package install test.
2. Profile static and active rendering; eliminate unnecessary continuous work.
3. Harden lifecycle, navigation, scrolling, and multi-glass composition.
4. Finalize presets, prop semantics, and compatibility policy.
5. Add visual regression coverage and the full CI matrix.
6. Complete contributor, security, API, and troubleshooting documentation.
7. Publish `0.2.0-beta.1` and integrate it into external applications.
8. Resolve beta findings, publish `0.2.0`, and iterate toward `1.0.0`.
