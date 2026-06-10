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
```

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
- `[ ]` Run Windows mpv runtime verification.
- `[ ]` Run a GUI playback smoke test with a real local media file.
- `[ ]` Validate Windows fullscreen, resize, aspect, and hardware decoding.
- `[ ]` Validate Android mobile and TV streaming against a Windows host.
- `[ ]` Confirm no local SDK paths, provider credentials, pairing tokens, or
  generated build output are included.
