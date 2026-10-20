# Contributing to CloudBox

## Development setup

Use JDK 17 and Android SDK 36.1. The repository includes a checksum-verified Gradle 9.3.1 wrapper; use `./gradlew` rather than installing or generating another Gradle version.

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

Before opening a pull request, run the checks relevant to your changes. CI runs the unit/Robolectric tests, Android lint, and a debug build on pull requests and pushes to `main`/`master`.

## Pull requests

- Keep a pull request focused and describe the behavior changed and how it was verified.
- Add or update automated tests for behavior changes and bug fixes.
- Do not commit credentials, signing keys, `.env` files, local SDK paths, or generated build outputs.
- The CI workflow only needs read access to repository contents. Release publishing runs separately after successful CI on `main` or a `v*` tag.

## Releases

A successful push to `main` publishes/updates the rolling `latest` prerelease. A versioned release is made by pushing a semantic version tag after merging the release-ready commit:

```bash
git tag v1.2.3
git push origin v1.2.3
```

The CI workflow tests version tags before the CD workflow publishes the APKs. Version tags require a persistent signing key configured in repository Actions secrets:

- `KEYSTORE_BASE64`: base64-encoded release keystore (`base64 -w0 release.jks` on Linux)
- `STORE_PASSWORD`: keystore password
- `KEY_ALIAS`: alias of the signing key
- `KEY_PASSWORD`: key password

All four secrets must be set together. Keep a secure backup of the keystore: Android will reject app updates signed with a different key. Do not commit the keystore or passwords.

If no persistent signing secrets are configured, the rolling `latest` preview workflow generates a temporary signing key so CI delivery remains available. Such builds are not suitable for in-place upgrades and should not be distributed as production releases; configure a persistent key before wider distribution. Versioned releases intentionally fail rather than publish with an ephemeral key.

## Workflow ownership

- `.github/workflows/ci.yml`: wrapper validation, tests, Android lint, and debug APK artifact.
- `.github/workflows/release.yml`: release builds from the exact commit that passed CI, APK checksums, and GitHub Releases.
- `.github/dependabot.yml`: scheduled updates for Gradle dependencies and GitHub Actions.
