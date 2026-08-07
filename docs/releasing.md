# Releasing

Danmaku currently publishes development artifacts through CI. Windows desktop
is Rust-native; there is no Java/Compose or macOS desktop artifact.

## Artifacts

### Windows Native Player

The `danmaku-windows-native-player` artifact contains a versioned zip with:

- `danmaku-player.exe` and `library-server.exe`;
- bundled `/web/` assets and launcher/background-host scripts;
- the approved pinned `libmpv-2.dll` and `mpv-probe` verification;
- project licenses, libmpv provenance, and generated Rust dependency
  inventories.

Build and verify it locally:

```powershell
.\build-rust-player.bat
.\run-rust-player.bat
```

The build wrapper prepares the web UI, builds both Rust binaries and
`mpv-probe`, verifies the package, and writes the zip under
`build/release/rust-player/`.

Run individual packaging checks only after `apps/web-ui/dist` exists:

```powershell
.\tools\windows\prepare-rust-player-release.ps1
.\tools\windows\verify-rust-player-release.ps1 `
  -WindowsDistributionPath .\build\release\rust-player\danmaku-player-0.1.0-windows-x64 `
  -ProbeExecutable .\target\release\mpv-probe.exe
```

Supervised playback QA requires known-good local media and launches the GUI:

```powershell
.\tools\windows\run-windows-playback-release-qa.ps1 `
  -DistributionPath .\build\release\rust-player\danmaku-player-0.1.0-windows-x64 `
  -MediaPath <known-good-media>
```

### Standalone Rust Server

The `danmaku-rust-library-server` artifact contains `library-server.exe`, web
assets, licenses, a dependency inventory, and a generated package manifest.

```powershell
.\tools\windows\prepare-rust-server-release.ps1
.\library-server.exe --data-dir <dir> --root <folder> --web-assets-dir .\web
```

There is no legacy desktop database import. Configure roots and rescan into a
new Rust data directory.

### Android

CI publishes Android mobile and TV debug APKs. Release signing uses the
configured CI secrets when present. Android artifacts consume the trusted-LAN
server and use Media3 for playback.

## Release Checklist

- `[ ]` Run CI-equivalent Rust, Gradle, web, and Worker checks.
- `[ ]` Verify the pinned libmpv hashes, license texts, and source provenance.
- `[ ]` Build and verify the standalone server and unified Windows zips.
- `[ ]` Run supervised Windows playback against representative real media.
- `[ ]` Validate fullscreen, resizing, hardware decoding, resume, and the
  optional background host manually.
- `[ ]` Validate Android mobile and TV streaming against the Rust host.
- `[ ]` Confirm no credentials, pairing tokens, local SDK paths, or generated
  build output are included.
