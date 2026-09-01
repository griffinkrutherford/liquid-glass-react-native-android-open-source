# Summary

What changes, and what observable behavior differs afterward.

Fixes #

## Validation

Commands run locally. Check only what you actually ran, and say what failed if
anything did. CI runs all of these
(`.github/workflows/ci.yml`).

- [ ] `npm run typecheck`
- [ ] `npm run build`
- [ ] `npm run android:bridge-check`
- [ ] `./gradlew test lintDebug :sample-android:assembleDebug`
- [ ] Ran on a device or emulator, not only compiled

Device tested:

- Android version and API level:
- Device or emulator:
- Rendering path: runtime AGSL shader (API 33+) / static fallback (API 32 and
  below) / both

## Native rebuild

- [ ] This change is JavaScript or TypeScript only and needs no native rebuild
- [ ] This change requires consuming apps to rebuild Android (Kotlin, AGSL
      shader, Fabric spec props, or Gradle configuration)

If a Fabric component spec changed, confirm codegen was regenerated and the
Kotlin view managers still satisfy the generated interface:

- [ ] `npm run android:bridge-check` passes with the new spec

## Visual change?

This is a rendering library, so a diff cannot show a regression in refraction,
blur, dispersion, tint, or edge optics.

- [ ] No visual change
- [ ] Visual change, before and after screenshots or a screen recording attached
      below

If visual, capture the same scene on both sides, and note whether it was taken
on the shader path or the fallback path.

| Before | After |
| --- | --- |
|  |  |

## API impact

- [ ] No public API change
- [ ] Adds a prop or option
- [ ] Changes or removes existing behavior (breaking; describe the migration)

The project is pre-1.0, so breaking changes are allowed, but they must be
described here so they reach the release notes.

## Checklist

- [ ] One logical change; unrelated refactors are in a separate pull request
- [ ] No generated output committed (`lib/`, `**/build/`, `.gradle/`)
- [ ] Documentation updated if props, defaults, ranges, or setup changed
- [ ] Added a changeset for a consumer-visible change, or explained why none is needed
- [ ] I agree to license this contribution under the Apache License 2.0
