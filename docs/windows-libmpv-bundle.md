# Windows libmpv Bundle

Windows playback uses libmpv through the Rust `player-windows-mpv` bridge. The
project redistributes a pinned LGPL `libmpv-2.dll` dependency for Windows
portable artifacts.

## Policy

- Keep the libmpv DLL as a separately licensed third-party dependency.
- Verify the approved producer artifact and hash before packaging.
- Include LGPL/GPL license text and source/provenance information in release
  artifacts.
- Do not silently replace the DLL or update hashes without re-auditing the
  source and license metadata.
- Local development may install the dependency into ignored runtime folders.

## Important Files

- `third_party/windows/libmpv/SOURCE.md`
- `THIRD_PARTY_NOTICES.md`
- `tools/windows/install-libmpv-dependency.ps1`
- `tools/windows/verify-libmpv-bundle.ps1`
- `tools/windows/test-install-libmpv-dependency.ps1`
- `tools/windows/test-verify-libmpv-bundle.ps1`
- `tools/windows/prepare-windows-release.ps1`
- `tools/windows/verify-windows-mpv-runtime.ps1`

## Local Verification

```powershell
.\tools\windows\test-verify-libmpv-bundle.ps1
.\tools\windows\test-install-libmpv-dependency.ps1
.\tools\windows\install-libmpv-dependency.ps1 -AcceptLicense
cargo build --release -p player-windows-mpv --lib
.\tools\windows\verify-windows-mpv-runtime.ps1
```

For a real playback smoke test:

```powershell
.\tools\windows\smoke-windows-playback.ps1 -MediaPath C:\media\sample.mkv -Seconds 6
```
