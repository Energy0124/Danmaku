# Android GitHub Release Updater

Last updated: 2026-08-31.

## Product Contract

- Android mobile, Android TV, and Windows share one stable `vX.Y.Z` GitHub
  Release.
- Mobile and TV check `android-update.json` automatically at startup at most
  once per 24 hours. Manual checks bypass the interval.
- An available update is downloaded only after the user selects **Update now**.
- Android's package installer always owns final installation confirmation.
  There is no silent-install path.
- **Later** dismisses the version until the next daily check; automatic check
  failures remain silent and manual failures remain visible.

## Release Assets

- `danmaku-android-mobile.apk`
- `danmaku-android-tv.apk`
- `android-update.json`
- `SHA256SUMS.txt`

The manifest schema is version 1. It records the release tag, display version,
monotonic Android version code, release page, and one target per application.
Each target records its application ID, stable asset name, tag-specific GitHub
download URL, exact byte size, and SHA-256 digest. The apps accept only HTTPS
assets from the same GitHub repository and tag as the manifest.

## Trust Boundary

The updater downloads into app-private cache through a size-bounded temporary
file. Before exposing the APK to Android it verifies the manifest size and
SHA-256, application ID, exact version code, and equality with the currently
installed APK signing certificate. `FileProvider` grants read access only to
the app-update cache directory. On Android 8 and newer the user may need to
allow this app as an installation source before the system installer opens.

The release workflow fails closed without the durable Android keystore. The
same certificate signs both apps, APK metadata/signatures are inspected before
publication, and the GitHub Release stays draft until Windows and Android
verification completes.

## QA Still Requiring Supervision

- Upgrade between two production-signed versions on a physical phone.
- Upgrade on representative Android/Google TV hardware using only a remote.
- First-time unknown-source authorization, installer cancellation, and retry.
- Corrupted/truncated APK rejection and recovery.
- Confirmation that locally debug-signed installs clearly require uninstall
  before the first production-signed install.
