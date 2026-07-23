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

1. Create an empty GitHub repo: `dodopayments/dodopayments-checkout-ios`.
2. Generate a GitHub PAT (fine-grained, scoped to that repo, Contents:
   read/write) or a deploy key with write access.
3. Add it as the `SWIFT_MIRROR_TOKEN` secret on **this** repo
   (`dodopayments-mobile-sdk`).
4. Copy the `LICENSE` file into the satellite repo's root too — this monorepo's
   root `LICENSE` doesn't automatically reach it.

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
| React Native | `version` in `react-native/package.json` | `react-native-v1.0.1` | `.github/workflows/release-react-native.yml` — vendors the Swift core, tests, verifies the vendored core is actually in the pack, publishes to npm |
| Flutter | `version` in `flutter/pubspec.yaml`, `flutter/android/build.gradle`, and `flutter/ios/dodopayments_checkout.podspec` (all three, kept in sync) | `flutter-v1.0.1` | `.github/workflows/release-flutter.yml` — same vendor-then-verify pattern, publishes to pub.dev |
| Swift | *(no version field — SPM versions come purely from the tag)* | `v1.0.1` | `.github/workflows/release-swift.yml` — builds/tests, mirrors `swift/` into the satellite repo, tags it there |

A version bump PR should also update that package's `CHANGELOG.md`.

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
cd react-native && ./scripts/sync-ios-core.sh && npm run typecheck && npm test && npm pack --dry-run

# Flutter
cd flutter && ./scripts/sync-ios-core.sh && flutter test && dart pub publish --dry-run

# Swift
cd swift && swift build && swift test
```
