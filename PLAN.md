# Liquid Glass for Android — Open Source Library Plan

## 1. Vision

Build an open source Android library that renders interactive, translucent “liquid glass” surfaces. The effect should react naturally to touch, motion, nearby content, and shape changes while remaining performant enough for production interfaces.

The project will consist of:

- A native Kotlin Android library containing the physics, rendering, and public APIs.
- A thin React Native Android package exposing the native view and imperative controls.
- A sample Android app and React Native example app.
- Benchmarks, visual regression tests, documentation, and reusable presets.

The physics engine will be implemented specifically for deformable UI surfaces. It will not depend on a general-purpose game physics engine.

## 2. Goals and non-goals

### Goals

- Render convincing glass refraction, highlights, tint, blur, and edge distortion.
- Simulate responsive waves, wobble, elasticity, damping, and shape recovery.
- Maintain 60 FPS on representative mid-range devices and support 90/120 Hz displays where practical.
- Produce deterministic simulations for repeatable tests.
- Expose simple presets as well as advanced physics and rendering controls.
- Work with Android Views first, with a Jetpack Compose adapter and React Native wrapper.
- Degrade gracefully when advanced GPU capabilities are unavailable.
- Publish reproducible artifacts to Maven Central and npm.

### Non-goals for version 1.0

- General rigid-body or fluid-volume simulation.
- Pixel-perfect replication of another platform's proprietary implementation.
- Arbitrary 3D glass geometry.
- Supporting Android versions that cannot provide an acceptable fallback path.

## 3. Proposed architecture

```text
Touch, motion, layout
        |
        v
Input mapper --> Custom physics engine --> Surface state
                                            |
                                            v
Captured content --> Render pipeline --> Android/Compose view
                                            |
                                            v
                                  React Native wrapper
```

Suggested modules:

- `liquid-glass-core`: platform-light math, simulation state, solvers, and tests.
- `liquid-glass-renderer`: Android GPU rendering, shaders, content capture, and fallbacks.
- `liquid-glass-view`: Android View API and lifecycle integration.
- `liquid-glass-compose`: Compose modifier/composable adapter.
- `react-native-liquid-glass`: React Native component, Fabric-compatible native view, and TypeScript API.
- `benchmark`: macrobenchmarks, GPU profiling scenarios, and reference scenes.
- `sample-android` and `example-react-native`: demos and integration examples.

Keep the simulation independent of Android UI classes so it can be unit tested without a device and reused by every frontend.

## 4. Custom physics engine design

### Surface model

Represent the glass as a bounded 2D deformable membrane rather than a full fluid volume. Begin with a regular grid of nodes; each node stores displacement and velocity. Connect neighboring nodes with virtual springs and apply damping, restoring forces, and optional pressure constraints.

Core state per node:

- Position in normalized local coordinates.
- Displacement or height from equilibrium.
- Velocity.
- Boundary weight and interaction impulse.

Core forces:

- Neighbor coupling for wave propagation.
- Restoring force for shape recovery.
- Velocity damping for viscosity.
- Boundary force for rounded-rectangle, capsule, and circle constraints.
- Touch impulses for press, drag, release, and multi-touch interaction.
- Optional device-motion force for tilt and inertia.

Use a fixed simulation timestep with an accumulator so behavior is stable across display refresh rates. Start with semi-implicit Euler integration because it is inexpensive and predictable; evaluate Verlet or position-based dynamics only if stability or constraints require it.

### Engine API

The engine should expose immutable configuration and allocation-free frame updates:

```kotlin
data class LiquidPhysicsConfig(
    val resolution: Int,
    val stiffness: Float,
    val damping: Float,
    val viscosity: Float,
    val touchForce: Float,
    val maxDisplacement: Float
)

interface LiquidSimulation {
    fun resize(width: Float, height: Float)
    fun applyImpulse(x: Float, y: Float, radius: Float, strength: Float)
    fun step(fixedDeltaSeconds: Float)
    fun snapshot(): SurfaceSnapshot
    fun reset()
}
```

Implementation constraints:

- Avoid per-frame heap allocation.
- Clamp extreme inputs and cap simulation catch-up work.
- Make seeded inputs deterministic.
- Allow simulation resolution to scale by device capability and view size.
- Pause updates when detached, invisible, or settled.
- Record numerical stability limits in tests and documentation.

### Physics validation

- Unit-test integration, damping, boundary behavior, impulses, and reset behavior.
- Run long simulations to detect energy growth, NaNs, and divergence.
- Test equivalent results across 60, 90, and 120 Hz render schedules.
- Benchmark multiple grid sizes and document the quality/performance tradeoff.
- Add a debug overlay for mesh, displacement, velocity, frame time, and active impulses.

## 5. Rendering strategy

Build the render pipeline separately from the physics engine. The renderer converts the surface snapshot into a displacement/normal field and uses it to shade captured background content.

Target effects:

- Background refraction driven by the simulated surface normals.
- Configurable blur and chromatic dispersion.
- Fresnel-style edge highlights and inner shadows.
- Tint, saturation, opacity, and noise controls.
- Rounded masks and animated shape transitions.

Investigate Android `RuntimeShader`/AGSL for supported versions and provide a documented fallback using simpler blur, tint, highlight, and scale distortion. Confirm the minimum SDK only after prototyping content capture and GPU paths; do not promise support based solely on API availability.

Rendering rules:

- Never read pixels back to the CPU per frame.
- Minimize offscreen passes and texture reallocations.
- Reuse buffers when dimensions are unchanged.
- Treat nested glass views and frequently changing backgrounds as explicit benchmark cases.
- Respect reduced-motion and animator-duration accessibility settings.
- Provide a static translucent fallback for battery saver, unsupported hardware, or disabled animation.

## 6. Public API design

Offer a small stable API with progressive customization:

- Presets: `subtle`, `regular`, `elastic`, and `gel`.
- Appearance: tint, opacity, blur, refraction, dispersion, highlight, corner radius.
- Physics: stiffness, damping, viscosity, response radius, touch force, motion influence.
- Behavior: interactive, motion enabled, quality tier, fallback mode.
- Events: interaction start/end and optional settled callback.
- Commands: pulse, apply impulse, reset, and animate to preset.

Kotlin, Compose, and TypeScript names should align wherever possible. Validate and normalize values on the native side so JavaScript cannot place the engine in an unstable state.

Example React Native API:

```tsx
<LiquidGlassView
  preset="regular"
  tint="#DDEBFF"
  cornerRadius={24}
  interactive
  style={styles.card}
>
  <CardContent />
</LiquidGlassView>
```

## 7. Delivery phases

### Phase 0 — Research and acceptance criteria

- Define supported device tiers and candidate minimum SDK.
- Create reference videos and measurable visual acceptance criteria.
- Prototype background capture and shader support on representative devices.
- Set initial budgets for CPU simulation, GPU rendering, memory, and startup cost.
- Document legal boundaries: original branding, original implementation, and no copied proprietary assets or shader code.

Exit criterion: one rendering path is proven feasible on real hardware and the compatibility strategy is documented.

### Phase 1 — Physics proof of concept

- Implement vector math, node storage, fixed-timestep loop, spring forces, damping, and impulses.
- Add circle and rounded-rectangle boundaries.
- Build a desktop or Android debug visualizer.
- Add stability, determinism, and performance tests.

Exit criterion: stable touch-driven deformation meets the CPU budget without per-frame allocations.

### Phase 2 — Native renderer prototype

- Convert simulated displacement into a GPU-consumable field.
- Implement refraction, blur/tint, edge lighting, and masking.
- Add buffer reuse, quality tiers, and static fallback.
- Profile with Android GPU Inspector, Perfetto, and Macrobenchmark.

Exit criterion: a single animated glass view holds the target frame rate on the baseline device.

### Phase 3 — Android library API

- Create `LiquidGlassView` with lifecycle, touch, accessibility, and state handling.
- Add Compose integration.
- Support XML attributes, Kotlin configuration, previews where feasible, and saved state.
- Write unit, instrumentation, screenshot, and benchmark tests.

Exit criterion: native consumers can integrate the effect from Maven artifacts using documented examples.

### Phase 4 — React Native integration

- Implement a Fabric-compatible native component and typed codegen specification.
- Map props without rebuilding the simulation for ordinary updates.
- Forward touch input appropriately and expose imperative commands.
- Test mounting, recycling, clipping, transforms, lists, modals, and hot reload.

Exit criterion: the example app demonstrates stable behavior in common React Native layouts on supported architectures.

### Phase 5 — Hardening and 1.0 release

- Test low-memory behavior, app backgrounding, rotation, font scaling, RTL, reduced motion, and screen readers.
- Establish an Android device/API compatibility matrix.
- Audit API surface, binary compatibility, dependencies, licensing, and artifact contents.
- Publish release candidates, collect community feedback, and resolve critical issues.
- Publish signed Maven Central and npm packages with changelogs and migration policy.

Exit criterion: performance targets, documentation, compatibility testing, and release automation all pass in CI.

## 8. Testing and performance targets

Initial targets, to be refined after Phase 0:

- No sustained frame drops for one medium-sized glass surface on the baseline device.
- Physics work below 1 ms per active view at the default quality tier.
- No allocations in steady-state physics or rendering loops.
- Simulation determinism within a documented floating-point tolerance.
- No crashes or invalid values after a 30-minute randomized stress test.
- Screenshot coverage for presets, shapes, fallback mode, RTL, and accessibility settings.

CI should run Kotlin checks, Android lint, unit tests, instrumentation/screenshot tests, TypeScript checks, React Native integration tests, API compatibility checks, and publishing dry runs. Performance regressions should be tracked on dedicated hardware rather than treated as reliable on shared CI runners.

## 9. Open source project setup

- License: Apache License 2.0 unless dependency or project constraints require another permissive license.
- Governance: maintainer roles, decision process, and a lightweight request-for-comments process for major API changes.
- Community files: `README.md`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`, issue templates, and pull request template.
- Versioning: semantic versioning, generated changelog, and explicit experimental API annotations.
- Supply chain: dependency locking, automated update checks, signed artifacts, provenance, and secret-free publishing workflows.
- Documentation: quick start, API reference, architecture guide, physics guide, performance guide, troubleshooting, compatibility matrix, and example gallery.

## 10. Key risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Background capture is expensive or inconsistent | Prototype first, limit redraw regions, cache safely, and provide a simpler fallback. |
| Simulation becomes unstable | Fixed timesteps, bounded inputs, displacement clamps, stability tests, and conservative defaults. |
| Multiple glass views exceed GPU budget | Quality tiers, shared resources, inactive-state freezing, documented limits, and benchmarks. |
| Visual output varies by GPU/vendor | Device matrix, shader conformance tests, tolerant visual comparisons, and feature flags. |
| React Native bridge causes excess updates | Use typed native props, batch changes, keep animation native, and avoid frame-by-frame JS traffic. |
| API becomes too complex | Lead with presets, keep advanced controls grouped, and require evidence before expanding the stable surface. |
| “Liquid glass” branding causes confusion | Use original project branding and state clearly that the project is independent and unaffiliated. |

## 11. First milestone backlog

1. Choose project name, package coordinates, license, minimum Java/Kotlin versions, and initial SDK range.
2. Scaffold the Gradle multi-module project and continuous integration.
3. Write benchmark scenes and define the baseline test devices.
4. Implement deterministic fixed-timestep simulation state.
5. Implement spring coupling, damping, touch impulses, and boundary masks.
6. Build the mesh/debug overlay and automated stability tests.
7. Prototype the primary GPU shader and content-capture path.
8. Compare physics grid resolutions and renderer quality tiers.
9. Record the architecture decision for renderer APIs and fallbacks.
10. Publish a proof-of-concept demo and request focused feedback before stabilizing the API.

## 12. Definition of done for version 1.0

Version 1.0 is complete when the native Android and React Native APIs are documented and stable, the custom physics engine is deterministic and stress-tested, the renderer meets agreed performance budgets on the published device matrix, accessible fallbacks work, CI can reproduce and publish signed artifacts, and a new contributor can build and run both examples from the repository instructions.
