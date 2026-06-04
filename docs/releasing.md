# Releasing Danmaku

Danmaku's own source code is licensed under MIT. Third-party components retain
their own licenses, so a binary release must preserve the boundaries described
below.

## Release Artifacts

- Android mobile and TV APKs include Danmaku's `LICENSE`,
  `THIRD_PARTY_NOTICES.md`, the Apache License 2.0 text, and a generated
  Licensee dependency inventory.
- The Windows desktop archive includes the same project and Gradle dependency
  materials plus a PowerShell launcher. It is runtime-free and requires
  user-installed Java 17 or newer.
- The Windows archive directly includes the approved pinned `libmpv-2.dll` as a
  separately licensed LGPLv3-or-later dependency. It also includes GPL/LGPL
  texts, the exact manifest, and a source and provenance notice.

The user's Java runtime remains under its own license and is not redistributed
or relicensed by Danmaku.

## Required Checks

Run these before publishing artifacts:

```powershell
.\tools\windows\test-verify-libmpv-bundle.ps1
.\tools\windows\test-install-libmpv-dependency.ps1
cargo fmt --all --check
cargo test --workspace
.\gradlew.bat --no-daemon :apps:desktop-windows:licensee :apps:android-mobile:licenseeDebug :apps:android-tv:licenseeDebug
.\gradlew.bat --no-daemon :shared:domain:jvmTest :shared:library-client:jvmTest :shared:library-server-core:jvmTest :apps:desktop-windows:desktopTest :shared:library-client-android:testDebugUnitTest :shared:player-android-media3:assembleDebugAndroidTest :apps:android-mobile:assembleDebug :apps:android-tv:assembleDebug :apps:desktop-windows:createDistributable
.\tools\windows\prepare-windows-release.ps1
.\tools\windows\verify-release-licensing.ps1
```

CI runs the same release licensing gates before uploading artifacts.

## Dependency Changes

- Do not add a dependency license to the Licensee allowlist without reviewing
  its compatibility and redistribution requirements.
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
