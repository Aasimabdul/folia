# Contributing to Folia

Thanks for helping! Folia is a small, offline, privacy-first reader, so changes should keep it **light, offline and safe**.

## Ground rules

- No network code, trackers or analytics. Do not add the `INTERNET` permission.
- Prefer small, well-known open-source dependencies; avoid large libraries (we care about APK size and RAM).
- Anything that parses untrusted files must use the limits in `engine/SafeIo.kt` (`SafeZip`, `SafeXml`, `LimitedInputStream`).
- Be honest in the UI: never claim a feature works for a format where it does not.

## Setup

Requirements: JDK 17 and the Android SDK (Android Studio Ladybug or newer works well).

```bash
git clone https://github.com/Aasimabdul/folia.git
cd folia
./gradlew testDebugUnitTest       # unit tests
./gradlew assembleDebug           # debug APK in app/build/outputs/apk/debug
./gradlew assembleRelease         # signed release APKs (uses signing/ if no secrets are set)
```

## Pull requests

1. Open an issue first for larger changes.
2. Keep one topic per pull request, with tests for logic (see `app/src/test`).
3. CI must pass: unit tests, debug + release build, signature verification.
4. Describe what you changed and how you tested it (device, Android version, file types).

## Commit style

Short imperative summary, details in the body, for example `Add folder sorting to Library`.

## Releases (maintainers)

Bump `versionCode` and `versionName` in `app/build.gradle.kts` following [Semantic Versioning](https://semver.org/),
add a section to `CHANGELOG.md` and a `docs/release-notes/vX.Y.Z.md` file, then push to `main`.
CI builds, verifies the signature, creates the `vX.Y.Z` tag and publishes the release with `SHA256SUMS.txt`.
