# Releasing Danmaku

Danmaku's own source code is licensed under MIT. Third-party components retain
their own licenses, so a binary release must preserve the boundaries described
below.

## Release Artifacts

- Android mobile and TV APKs include Danmaku's `LICENSE`,
  `THIRD_PARTY_NOTICES.md`, and the Apache License 2.0 text.
- The Windows desktop archive includes the same project license materials plus
  a PowerShell launcher. It is runtime-free and requires user-installed Java 17
  or newer.
- The Windows archive directly includes the approved pinned `libmpv-2.dll` as a
  separately licensed LGPLv3-or-later dependency. It also includes GPL/LGPL
  texts, the exact manifest, and a source and provenance notice.
- The Windows archive includes Danmaku's MIT-licensed
  `player_windows_mpv.dll` bridge, which the desktop shell uses to call the
  separately licensed libmpv dependency.

The user's Java runtime remains under its own license and is not redistributed
or relicensed by Danmaku.

## Required Checks

Run these before publishing artifacts:

```powershell
.\tools\windows\test-verify-libmpv-bundle.ps1
.\tools\windows\test-install-libmpv-dependency.ps1
cargo fmt --all --check
cargo test --workspace
cargo build --release -p player-windows-mpv --lib
.\gradlew.bat --no-daemon :shared:domain:jvmTest :shared:library-client:jvmTest :shared:library-server-core:jvmTest :apps:desktop-windows:desktopTest :shared:library-client-android:testDebugUnitTest :shared:player-android-media3:assembleDebugAndroidTest :apps:android-mobile:assembleDebug :apps:android-tv:assembleDebug :apps:desktop-windows:createDistributable
.\tools\windows\prepare-windows-release.ps1
.\tools\windows\verify-windows-mpv-runtime.ps1
```

To smoke-test packaged local-file playback with a real media sample, pass the
sample path to the runtime probe:

```powershell
.\tools\windows\verify-windows-mpv-runtime.ps1 -MediaPath C:\media\sample.mkv
```

CI builds, tests, prepares the Windows release, and verifies the packaged mpv
runtime before uploading artifacts.

## Dependency Changes

- Add the full license text and any required notices to release artifacts when
  a new allowed license appears.
- Keep the approved libmpv manifest URL and hashes pinned to the reviewed
  producer artifact.
- Repeat the libmpv license, build-output, source, and provenance review before
  changing the producer artifact or hashes.
- Keep clear source directions beside every distributed libmpv DLL for as long
  as the corresponding binary remains available.

This documentation is an engineering release gate, not legal advice. Obtain
legal review before a production or commercial distribution.
