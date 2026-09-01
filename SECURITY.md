# Security Policy

## Reporting a vulnerability

Report privately. Do not open a public issue, discussion, or pull request for a
suspected vulnerability.

Preferred channel — GitHub private vulnerability reporting:

<https://github.com/griffinkrutherford/liquid-glass-react-native-android-open-source/security/advisories/new>

If you cannot use GitHub Security Advisories, email
barrykarlrutherford@gmail.com with `SECURITY` in the subject line.

Please include:

- affected version of `@griffinkrutherford/liquid-glass-android`
- Android version and API level, device or emulator, and GPU if known
- React Native version and whether the New or Old Architecture is in use
- a reproduction: minimal props, scene composition, or input values
- observed impact (crash, hang, out-of-memory, unexpected data exposure)
- any stack trace or `logcat` output

## Response times

This project is maintained by one person in their own time. The commitments
below are what a solo maintainer can realistically keep:

| Stage | Target |
| --- | --- |
| Acknowledge the report | 5 business days |
| Initial assessment and severity | 15 business days |
| Fix or documented mitigation for a confirmed high-severity issue | 60 days from acknowledgement |

If a report is not acknowledged within 10 business days, send a follow-up email;
the first message may have been missed. Coordinated disclosure after a fix ships
is welcome, and reporters are credited in the advisory unless they prefer not to
be.

## Supported versions

| Version | Status |
| --- | --- |
| `0.2.x` | Planned supported beta line. Will receive security fixes once released. |
| `0.1.x` | Prototype. No security support. Upgrade to `0.2.x` when it is published. |

The project is pre-1.0. Only the newest release of the supported line receives
fixes; there are no backports to earlier patch versions. Fixes ship as a new
`0.x` release, not as a patch to an older tag.

## Scope

This is an Android rendering library. It has no network client, no persistent
storage, no authentication, and no IPC surface of its own. Realistic reports
fall into these categories:

In scope:

- Malformed, extreme, or hostile prop values (dimensions, corner radius, index
  of refraction, colors) that cause a crash, an unrecoverable native error, an
  out-of-memory condition, or an unbounded allocation.
- Backdrop capture or bitmap handling that leaks pixel content across scenes,
  activities, or applications, or that retains buffers it should release.
- AGSL shader input handling: values that reach the runtime shader and cause a
  GPU driver crash, a device hang, or a shader compilation failure that is not
  caught and degraded to the fallback path.
- Supply-chain integrity of the published artifact: an npm tarball whose
  contents do not match the tagged source, a compromised release workflow, or a
  problem with build provenance.
- Vulnerable transitive dependency that is actually reachable from library code.

Out of scope:

- Visual artifacts, incorrect optics, or performance problems with no security
  impact. Report those as normal issues.
- Crashes only reproducible on a rooted device, with a modified system GPU
  driver, or under a debugger-attached process.
- Vulnerabilities in React Native, the Android platform, or GPU drivers
  themselves. Report those upstream; tell us if a workaround belongs here.
- Anything requiring an attacker to already control the application's own source
  code, since that code can call any Android API directly.
- Denial of service produced only by absurd resource use that the host
  application chose, such as hundreds of simultaneous glass views.

## Hardening notes for consumers

- Pin an exact version and commit your lockfile. The library is pre-1.0 and
  native code changes between releases.
- Validate any prop values that come from remote configuration or user input
  before passing them to `LiquidGlassView`, particularly dimensions and index of
  refraction.
- Release builds are published with npm provenance. Verify it if your
  supply-chain policy requires attestation.
