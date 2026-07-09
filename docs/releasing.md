# Releasing

Danmaku does not have a stable public release process yet. Current artifacts are
development outputs built by CI and local scripts.

## Artifact Boundaries

### Windows Desktop

- Primary desktop release target.
- Portable artifact is runtime-free and requires a user-installed Java 17+
  runtime.
- The portable artifact includes the app, launcher scripts, the Rust mpv bridge,
  and the approved pinned LGPL `libmpv-2.dll` dependency.
- Third-party license text, notices, source provenance, and dependency versions
  must ship with the artifact.

### Rust Library Server

- Standalone Windows headless server artifact for the Rust Phase 1 migration.
- The artifact serves the existing trusted-LAN HTTP API and UDP discovery
  protocol for Android mobile, Android TV, and web clients.
- The package includes `library-server.exe`, bundled web UI assets under
  `web/`, `LICENSE`, `THIRD_PARTY_NOTICES.md`, `RUST_CRATE_LICENSES.md`, and a
  package README with CLI flags, importer usage, LAN trust warnings, and
  port/discovery defaults.
- The release script builds the Rust binary, builds the Vite web UI, stages the
  package, runs a generated-fixture smoke check, writes a content manifest, and
  produces a versioned zip under `build/release/rust-library-server/`.

### Android Mobile And Android TV

- Debug APKs are produced by CI today.
- Release signing is supported through CI secret environment variables when
  configured.
- Android playback depends on Media3 and streams from a trusted desktop LAN
  server.

### macOS Desktop

- Experimental development artifact only.
- Uses the shared Compose desktop app and mpv command bridge.
- Release-ready app packaging and embedded video composition are not complete.

## Local Commands

Windows development shell:

```powershell
.\run-windows.ps1
```

Windows portable build/run:

```powershell
.\run-windows.ps1 -Portable
```

Prepare Windows portable release without launching through the helper:

```powershell
cargo build --release -p player-windows-mpv --lib
.\gradlew.bat --no-daemon :apps:desktop-windows:createDistributable
.\tools\windows\prepare-windows-release.ps1
.\tools\windows\verify-windows-mpv-runtime.ps1
.\tools\windows\run-windows-playback-release-qa.ps1 -WindowsDistributionPath .\apps\desktop-windows\build\release\windows-portable -MediaPath <known-good-media>
```

Prepare the standalone Rust headless server release:

```powershell
.\tools\windows\prepare-rust-server-release.ps1
```

The packaged server runs as:

```powershell
.\library-server.exe --data-dir <dir> --root <folder> --web-assets-dir .\web
```

Use `--import-desktop-catalog <db-copy>` with `--data-dir <dir>` to import a
read-only copy of the existing desktop catalog into the Rust server data
directory, then exit. Do not point the importer at the live desktop database.

Android debug artifacts:

```powershell
.\gradlew.bat --no-daemon :apps:android-mobile:assembleDebug :apps:android-tv:assembleDebug
```

macOS development build:

```bash
./run-macos.sh
```

## Release Checklist

- `[ ]` Run CI-equivalent Gradle, Rust, and Worker proxy checks.
- `[ ]` Verify the pinned libmpv bundle hash and license/source provenance.
- `[ ]` Build the standalone Rust library server zip and verify the packaged
  smoke check passes.
- `[ ]` Run Windows playback release QA automation with representative real media.
- `[ ]` Validate Windows fullscreen, resize, aspect, and hardware decoding manually.
- `[ ]` Validate Android mobile and TV streaming against a Windows host.
- `[ ]` Confirm no local SDK paths, provider credentials, pairing tokens, or
  generated build output are included.
