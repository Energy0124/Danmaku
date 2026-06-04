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

## Preferred Candidate: zhongfly LGPL Build

Reviewed on 2026-06-04:

- Project:
  [zhongfly/mpv-winbuild](https://github.com/zhongfly/mpv-winbuild)
- Release:
  [2026-06-04-1d82932cce](https://github.com/zhongfly/mpv-winbuild/releases/tag/2026-06-04-1d82932cce)
- Archive: `mpv-dev-lgpl-x86_64-20260604-git-1d82932cce.7z`
- Release SHA-256:
  `eacba7b1afdb5620fd556da1141fc5267dca9d81a5d7a649a36384af77405855`
- Local `mpv-probe` result: loaded successfully, reported client API version
  `131077`, initialized an mpv context, and shut down cleanly.

This is the preferred current candidate for an MIT-licensed Danmaku
application with separately licensed LGPL playback dependencies:

- The project publishes specifically named `mpv-dev-lgpl-*` artifacts.
- Its
  [LGPL build patch](https://github.com/zhongfly/mpv-winbuild/blob/main/compile-lgpl-libmpv.patch)
  builds mpv with `-Dgpl=false`, removes FFmpeg's `--enable-gpl`, and removes
  known GPL-incompatible dependencies such as x264 and x265.
- The project describes the LGPL libmpv artifact as LGPLv2.1+ with statically
  linked FFmpeg under LGPLv3. Treat the combined DLL distribution as requiring
  LGPLv3 compliance.

The candidate is **not yet approved for Danmaku redistribution**:

- The maintainer states that they cannot guarantee every LGPL-incompatible
  package has been disabled.
- The archive contains the DLL, import library, and headers, but no license or
  notice files.
- Danmaku still needs a complete bundled-component version and license
  inventory, corresponding source links or archives, build configuration, and
  required notices before publishing the DLL.

The repository's MIT license applies to its build scripts, not to libmpv,
FFmpeg, or the resulting DLL. Danmaku's own source may remain MIT licensed
while the bundled playback dependency is identified and distributed under its
LGPL terms.
