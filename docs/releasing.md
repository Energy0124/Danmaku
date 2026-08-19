# Releasing

Danmaku publishes signed Windows desktop releases from SemVer tags and
development artifacts through CI. Windows and the experimental macOS desktop
build are Rust-native; there is no Java/Compose desktop artifact.

## Artifacts

### Windows Native Player

Pushing a tag such as `v0.2.0` runs `.github/workflows/release.yml`. The tag
must match the `danmaku-player` and `library-server` Cargo versions and a
non-empty `CHANGELOG.md` section. The workflow requires these repository
secrets and fails closed when either is missing:

- `WINDOWS_SIGNING_PFX_BASE64`: base64-encoded Authenticode PFX;
- `WINDOWS_SIGNING_PFX_PASSWORD`: PFX password.

The workflow uses Velopack 1.2.0 to publish the signed per-user Setup, full and
delta update packages, `releases.win-x64-stable.json`, the portable zip, and
`SHA256SUMS.txt`. It creates the GitHub release as a draft and publishes it
only after verification. The installed app checks stable GitHub releases at
startup and applies an update only after the user selects **Update and
restart**.

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
.\tools\windows\prepare-windows-installer.ps1 `
  -VpkPath <path-to-vpk-1.2.0>
```

For a production-equivalent local build, also pass `-ReleaseNotesPath`,
`-SigningPfxPath`, and `-RequireSigning`; provide the password through
`WINDOWS_SIGNING_PFX_PASSWORD`. Do not place certificates or passwords in the
repository. The installer is one-click, per-user, and does not install the
optional background host automatically.

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

### macOS Native Player (Experimental)

Install Homebrew libmpv, build the web UI/player/server, assemble
`Danmaku.app`, run structural and `mpv-probe` checks, ad-hoc sign it, and create
the archive:

```bash
brew install mpv
./build-macos.sh
```

Outputs are written below `build/release/macos/`. The app contains
`danmaku-player`, `library-server`, `/web/` assets, its icon, licenses, and
notices. It does not bundle libmpv; both Apple Silicon and Intel builds discover
Homebrew's stable `opt/mpv/lib` paths, or `DANMAKU_LIBMPV_PATH` may select a
specific dylib.

This is a development artifact, not a release-ready Mac distribution. It is
ad-hoc signed and not notarized. Online provider HTTPS and protected provider
credential persistence remain Windows-only. Do not publish a bundled macOS
libmpv until its producer, configuration, licenses, source provenance, and hash
have been reviewed.

### Android

CI publishes Android mobile and TV debug APKs. Release signing uses the
configured CI secrets when present. Android artifacts consume the trusted-LAN
server and use Media3 for playback.

## Release Checklist

- `[ ]` Run CI-equivalent Rust, Gradle, web, and Worker checks.
- `[ ]` Verify the pinned libmpv hashes, license texts, and source provenance.
- `[ ]` Build and verify the standalone server and unified Windows zips.
- `[ ]` Confirm the tag, both Rust package versions, and changelog section
  match; confirm signing secrets are available to the protected tag workflow.
- `[ ]` Verify Setup/full/delta/feed/checksum assets and valid Authenticode
  signatures without changing the approved libmpv hash.
- `[ ]` Exercise install, startup check, release-note approval, update/restart,
  corrupted-package rejection, background-host refresh, and uninstall data
  preservation with two release versions.
- `[ ]` Build and verify the macOS `.app` on Apple Silicon and Intel.
- `[ ]` Before macOS promotion, complete libmpv provenance, release signing,
  notarization, provider HTTPS/token storage, and supervised playback QA.
- `[ ]` Run supervised Windows playback against representative real media.
- `[ ]` Validate fullscreen, resizing, hardware decoding, resume, and the
  optional background host manually.
- `[ ]` Validate Android mobile and TV streaming against the Rust host.
- `[ ]` Confirm no credentials, pairing tokens, local SDK paths, or generated
  build output are included.
