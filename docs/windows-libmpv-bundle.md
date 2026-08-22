# Windows libmpv Bundle

Windows playback uses libmpv through the Rust `player-windows-mpv` bridge. The
project resolves and redistributes the latest published LGPL x64
`libmpv-2.dll` dependency for Windows CI and release builds.

## Policy

- Keep the libmpv DLL as a separately licensed third-party dependency.
- Resolve only the newest published, non-prerelease
  `mpv-dev-lgpl-x86_64-*.7z` producer asset.
- Require and verify the GitHub release asset SHA-256 before extraction.
- Cache archives by the resolved digest, never by a floating filename or URL.
- Include LGPL/GPL license text and source/provenance information in release
  artifacts.
- Record the exact release, asset, archive hash, and extracted DLL hash in the
  portable package and as a standalone release asset.
- Re-audit the producer and selection policy before changing either one.
- Local development may install the dependency into ignored runtime folders.

## Important Files

- `third_party/windows/libmpv/SOURCE.md`
- `THIRD_PARTY_NOTICES.md`
- `tools/windows/resolve-latest-libmpv-dependency.ps1`
- `tools/windows/install-libmpv-dependency.ps1`
- `tools/windows/verify-libmpv-bundle.ps1`
- `tools/windows/test-install-libmpv-dependency.ps1`
- `tools/windows/test-resolve-latest-libmpv-dependency.ps1`
- `tools/windows/test-verify-libmpv-bundle.ps1`
- `tools/windows/prepare-rust-player-release.ps1`
- `tools/windows/verify-rust-player-release.ps1`

## Local Verification

```powershell
.\tools\windows\test-verify-libmpv-bundle.ps1
.\tools\windows\test-resolve-latest-libmpv-dependency.ps1
.\tools\windows\test-install-libmpv-dependency.ps1
.\tools\windows\install-libmpv-dependency.ps1 -AcceptLicense
cargo build --release -p player-windows-mpv --lib
.\build-rust-player.bat
```

The installer queries GitHub only when no explicit resolution document is
provided. It writes `runtime/windows/libmpv/libmpv-provenance.json` beside the
verified DLL. CI resolves first, restores a digest-keyed cache, and passes the
exact resolution and archive to the installer so retries use identical bytes.

For a real playback smoke test:

```powershell
.\tools\windows\smoke-windows-playback.ps1 -MediaPath C:\media\sample.mkv -Seconds 6
```
