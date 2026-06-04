# Windows libmpv Bundle Audit

Danmaku does not commit or automatically download native media DLLs. A release
bundle must be reviewed, pinned, and verified before it is copied beside the
packaged Windows executable.

The mpv project does not publish official Windows binaries. Its installation
page currently links third-party Windows builds, including the shinchiro build
repository. That link is useful provenance, but it is not approval for Danmaku
redistribution. The selected archive still needs a license and supply-chain
review.

Primary references:

- [mpv installation page](https://mpv.io/installation/)
- [mpv source and license summary](https://github.com/mpv-player/mpv)
- [mpv releases](https://github.com/mpv-player/mpv/releases)

## Local Bundle Layout

Place a reviewed bundle in the ignored local directory:

```text
runtime/windows/libmpv/
  bundle-manifest.json
  libmpv-2.dll
  LICENSE.LGPL
  ...other redistributed DLLs and notices...
```

Every redistributed file must be listed in `bundle-manifest.json`. The
manifest records the source archive, its hash, component versions, build
configuration, selected license mode, and license or notice files.

```json
{
  "schemaVersion": 1,
  "bundleName": "Replace with the reviewed bundle name",
  "sourceUrl": "https://replace.example/libmpv-archive.7z",
  "sourceArchiveSha256": "REPLACE_WITH_64_HEX_CHARACTERS",
  "configurationFlags": [
    "-Dgpl=false"
  ],
  "licenseMode": "LGPL-2.1-or-later",
  "components": [
    {
      "name": "mpv",
      "version": "replace",
      "license": "replace"
    },
    {
      "name": "FFmpeg",
      "version": "replace",
      "license": "replace"
    }
  ],
  "licenseFiles": [
    "LICENSE.LGPL"
  ],
  "files": [
    {
      "path": "libmpv-2.dll",
      "sha256": "REPLACE_WITH_64_HEX_CHARACTERS"
    },
    {
      "path": "LICENSE.LGPL",
      "sha256": "REPLACE_WITH_64_HEX_CHARACTERS"
    }
  ]
}
```

The component inventory must include mpv, FFmpeg, and every other bundled
dependency. The manifest should use the actual configuration reported by the
selected build. Do not claim LGPL compatibility merely because `-Dgpl=false`
is preferred by the project; verify the complete dependency configuration and
notices.

## Verify And Probe

Build the Rust probe, then verify the manifest and initialize libmpv:

```powershell
cargo build -p player-windows-mpv --bin mpv-probe
.\tools\windows\verify-libmpv-bundle.ps1 `
  -SourceArchivePath C:\path\to\reviewed-libmpv-archive.7z `
  -ProbeExecutable .\target\debug\mpv-probe.exe
```

The verifier rejects missing metadata, unlisted license files, path traversal,
missing files, duplicate paths, and SHA-256 mismatches. Pass the original
downloaded archive with `-SourceArchivePath` during the audit so its hash is
checked against the manifest before approving the extracted bundle.

## Package A Verified Bundle

Create the Compose distributable, then copy only manifest-listed files beside
its executable:

```powershell
.\gradlew.bat --no-daemon :apps:desktop-windows:createDistributable
.\tools\windows\verify-libmpv-bundle.ps1 `
  -SourceArchivePath C:\path\to\reviewed-libmpv-archive.7z `
  -ProbeExecutable .\target\debug\mpv-probe.exe `
  -DistributionPath .\apps\desktop-windows\build\compose\binaries\main\app\desktop-windows
```

This workflow is intentionally opt-in. CI continues to build a DLL-free
distributable until a specific bundle has passed review and its redistribution
obligations are documented.

## Candidate Review: shinchiro 20260604

Reviewed on 2026-06-04:

- Release:
  [shinchiro/mpv-winbuild-cmake 20260604](https://github.com/shinchiro/mpv-winbuild-cmake/releases/tag/20260604)
- Archive: `mpv-dev-x86_64-20260604-git-6d5c859.7z`
- Release SHA-256:
  `3ddcaba4143d35a63a3fee9ae9cd4189ad25887d3399e32cc49ceb3fb2da6569`
- Tagged build-repository commit:
  `5efd298cb51513c2410e4e9029b5e56b83c2aaac`
- Local `mpv-probe` result: loaded successfully, reported client API version
  `131077`, initialized an mpv context, and shut down cleanly.

This candidate is **not approved for Danmaku redistribution**:

- mpv's installation page describes Windows binary packages as unofficial
  third-party builds.
- The tagged
  [FFmpeg configuration](https://github.com/shinchiro/mpv-winbuild-cmake/blob/20260604/packages/ffmpeg.cmake)
  explicitly enables GPL and version 3 features.
- The tagged
  [mpv configuration](https://github.com/shinchiro/mpv-winbuild-cmake/blob/20260604/packages/mpv.cmake)
  does not set `-Dgpl=false`.
- The development archive contains `libmpv-2.dll`, an import library, and
  headers, but no license or notice files.

The archive is suitable only as an ignored local loader smoke-test candidate.
Do not copy it into a published Danmaku artifact. A release candidate still
needs an LGPL-compatible build or an explicit project decision to distribute
under GPL with complete notices and source-availability obligations.
