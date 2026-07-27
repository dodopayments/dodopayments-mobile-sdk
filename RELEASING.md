# Releasing

Each of the four SDKs is versioned and released independently — a change to
Flutter doesn't force a version bump on Kotlin, and vice versa. Releases are
triggered by pushing a package-scoped git tag; a GitHub Actions workflow
handles the rest.

## One-time setup (accounts, repos, secrets)

### Kotlin → Maven Central

1. Create an account at [central.sonatype.com](https://central.sonatype.com)
   and verify the `com.dodopayments` namespace.
2. Generate a user token (Account → Generate User Token) — this gives a
   username/password pair, not your login credentials.
3. Generate a GPG keypair for signing artifacts (`gpg --full-generate-key`),
   and publish the public key to a keyserver (e.g.
   `gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>`).
4. Add as GitHub Actions secrets (Settings → Secrets and variables → Actions):
   - `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD` — the user token
   - `GPG_PRIVATE_KEY` — export with `gpg --export-secret-keys --armor <KEY_ID>`
   - `GPG_PASSPHRASE` — the key's passphrase
   - `GPG_KEY_ID` — the key's short ID

### React Native → npm

1. Generate an npm automation token with publish rights on the `@dodopayments`
   org (npmjs.com → Access Tokens → Generate New Token → Automation).
2. Add it as the `NPM_TOKEN` secret.

### Flutter → pub.dev

Publishes via GitHub Actions OIDC — no long-lived secret. One-time setup:

1. Publish the package manually once (`dart pub publish` from a local
   machine) so it exists on pub.dev under your account.
2. In the package's pub.dev admin page (Admin tab → Automated publishing),
   enable GitHub Actions publishing and set the repository to
   `dodopayments/dodopayments-mobile-sdk` with tag pattern `flutter-v{{version}}`.

### Swift → satellite repo

SPM resolves packages by git tag on the repo containing `Package.swift`, and
requires that file at the repo's root — which this monorepo's `swift/`
subdirectory can't satisfy directly. Releasing therefore mirrors `swift/` out
to a dedicated repo on every release.

1. Create the GitHub repo `dodopayments/dodopayments-checkout-ios` **with an
   initial commit** (a README is enough). `actions/checkout` cannot check out a
   truly empty repo (it fails with `couldn't find remote ref`), so the mirror
   job would never get off the ground.
2. Generate a GitHub PAT (fine-grained, scoped to that repo, Contents:
   read/write) or a deploy key with write access.
3. Add it as the `SWIFT_MIRROR_TOKEN` secret on **this** repo
   (`dodopayments-mobile-sdk`).

The satellite is a pure mirror of `swift/`: the sync runs `rsync --delete`, so
anything that exists only there (a hand-edited README, a `.github/` dir) is
deleted on the next release. `swift/LICENSE` is already in the mirror set, so
the license travels automatically, with nothing to copy by hand.

**Current state:** `dodopayments-checkout-ios` already has `v1.0.0`, pushed by
hand before this workflow existed, and its contents match `swift/` exactly.
This monorepo has no matching tag. The mirror job now refuses to overwrite a
tag the satellite already published (moving a tag breaks the revision SPM
consumers pinned in `Package.resolved`), so the next Swift release from here
must be `v1.0.1` or later.

### Bump-dependents PR bot

`bump-dependents.yml` opens its PR using a PAT, not the default `GITHUB_TOKEN`
— PRs opened with `GITHUB_TOKEN` don't trigger other workflows (GitHub's
anti-recursion behavior), so `ci.yml` would never run on these bot PRs
otherwise.

1. Generate a GitHub PAT (fine-grained, scoped to this repo, Contents: write,
   Pull requests: write).
2. Add it as the `BUMP_DEPENDENTS_TOKEN` secret.

## Releasing a package

Bump the version, merge to `main`, then push the matching tag. Each package's
tag format and what happens:

| Package | Bump | Tag | Workflow |
|---|---|---|---|
| Kotlin | `version` in `kotlin/build.gradle.kts` | `kotlin-v1.0.1` | `.github/workflows/release-kotlin.yml` — tests, then publishes to Maven Central |
| React Native | `version` in `react-native/package.json` | `react-native-v1.0.1` | `.github/workflows/release-react-native.yml` - vendors the Swift core, builds `lib/`, tests, verifies both the build output and the vendored core are in the pack, publishes to npm |
| Flutter | `version` in `flutter/pubspec.yaml`, `flutter/android/build.gradle`, and `flutter/ios/dodopayments_checkout.podspec` (all three, kept in sync) | `flutter-v1.0.1` | `.github/workflows/release-flutter.yml` — same vendor-then-verify pattern, publishes to pub.dev |
| Swift | *(no version field — SPM versions come purely from the tag)* | `v1.0.1` | `.github/workflows/release-swift.yml` — builds/tests, mirrors `swift/` into the satellite repo, tags it there |

A version bump PR should also update that package's `CHANGELOG.md`.

Every release workflow first asserts that the tag matches the version recorded
in the package (all three files, for Flutter). The publishing tools take the
version from the package, not from the tag, so without that check a mistyped
tag would silently publish the wrong version or fail late as a duplicate.

## Cross-package dependency bumps

React Native and Flutter both pin a specific Kotlin version as a Maven
coordinate (`com.dodopayments:checkout-android:X.Y.Z`) in their respective
`android/build.gradle`. Neither picks up a new Kotlin release automatically —
after a Kotlin release succeeds, `.github/workflows/bump-dependents.yml`
opens a PR bumping that pin in both React Native and Flutter. It does **not**
auto-merge — review it, let CI go green, then merge and release those
packages if you want the update to reach their consumers.

Swift doesn't need this: React Native and Flutter each re-vendor the Swift
core fresh from `swift/` in their own release workflows (`scripts/sync-ios-core.sh`),
not from a pinned or committed copy — the next time either releases, it
automatically picks up whatever `swift/` currently contains.

## Verifying before you tag

Each release workflow already runs the relevant checks, but it's worth
confirming locally first:

```sh
# Kotlin
cd kotlin && ./gradlew test && ./gradlew publishToMavenLocal

# React Native
cd react-native && ./scripts/sync-ios-core.sh && npm run build && npm run typecheck && npm test && npm pack --dry-run

# Flutter
cd flutter && ./scripts/sync-ios-core.sh && flutter test && dart pub publish --dry-run

# Swift
cd swift && swift build && swift test
```
