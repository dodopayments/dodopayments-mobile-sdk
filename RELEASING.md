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

### Swift → satellite repo (git submodule)

SPM resolves packages by git tag on the repo containing `Package.swift`, and
requires that file at the repo's root — which this monorepo can't satisfy
directly. `swift/` is a **git submodule** pointing at
`dodopayments/dodopayments-mobile-sdk-ios`, which is the canonical source for
the Swift package; this monorepo just pins a commit of it. That repo has its
own CI (`.github/workflows/ci.yml` there) and needs no secrets from this repo.

Cloning this repo does **not** pull in Swift source by default:

```sh
git clone --recurse-submodules https://github.com/dodopayments/dodopayments-mobile-sdk.git
# or, after a normal clone:
git submodule update --init --recursive
```

To make a change to Swift code:

```sh
cd swift
git checkout main            # submodules start in detached HEAD
# ...edit, commit, push to dodopayments-mobile-sdk-ios directly...
cd ..
git add swift                 # stages the new pinned commit
git commit -m "..."           # a normal commit in this repo
```

**Current state:** `dodopayments-mobile-sdk-ios` already has `v1.0.0`. The
Swift package has no version field of its own — SPM versions come purely from
tags on the satellite repo. Releasing means tagging
`dodopayments-mobile-sdk-ios` directly (build/test there first), then bumping
this repo's submodule pointer to match if you want the monorepo to reflect it.

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
| Swift | *(no version field — SPM versions come purely from the tag)* | `v1.0.1` | Tag `dodopayments-mobile-sdk-ios` directly (its own CI builds/tests); optionally bump this repo's `swift` submodule pointer to match |

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

Swift is similar but not automatic anymore now that `swift/` is a submodule:
React Native and Flutter each re-vendor the Swift core fresh from `swift/` in
their own release workflows (`scripts/sync-ios-core.sh`), so they pick up
whatever commit the submodule is *currently pinned to* — not necessarily the
satellite repo's latest. If you release a new Swift version, remember to bump
this repo's submodule pointer (see above) before releasing React Native or
Flutter, or they'll vendor a stale Swift commit.

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
