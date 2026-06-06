# Windows libmpv Bundle Audit

Danmaku does not commit native media DLLs. Local packaging may use only an
approved, pinned, and verified bundle before copying it into the Windows
application directory.

Danmaku's own source code is MIT licensed. libmpv, FFmpeg, and their bundled
dependencies are not MIT licensed and cannot be relicensed by Danmaku.

The mpv project does not publish official Windows binaries. Its installation
page currently links third-party Windows builds, including the shinchiro build
repository. That link is useful provenance, but it is not approval for Danmaku
redistribution. The selected archive still needs a license and supply-chain
review.

Primary references:

- [mpv installation page](https://mpv.io/installation/)
- [mpv source and license summary](https://github.com/mpv-player/mpv)
- [mpv releases](https://github.com/mpv-player/mpv/releases)

## Current Release Model

Danmaku's Windows release directly redistributes the approved pinned zhongfly
LGPL libmpv DLL as a separately licensed dependency. It includes:

```text
desktop-windows/
  LICENSE
  THIRD_PARTY_NOTICES.md
  run-danmaku.ps1
  app/
    libmpv-2.dll
  licenses/
    APACHE-2.0.txt
    GPL-3.0.txt
    LGPL-2.1.txt
    LGPL-3.0.txt
  dependencies/libmpv/
    SOURCE.md
    install-libmpv-dependency.ps1
    zhongfly-lgpl-x86_64-20260604.json
```

Release preparation downloads the pinned zhongfly LGPL artifact from the
producer, verifies the archive SHA-256, extracts only `libmpv-2.dll`, verifies
the DLL SHA-256, and places it in the portable package's `app` directory. The
launcher exposes that path through `DANMAKU_LIBMPV_PATH`. Danmaku's code remains
MIT licensed while the bundled DLL remains LGPLv3-or-later licensed.

The normal Compose `createDistributable` task also builds
`player_windows_mpv.dll` and copies the installed approved `libmpv-2.dll` into
the app directory by default, so local smoke tests exercise the same native file
layout as the portable release. Install the pinned dependency first if the local
`runtime/windows/libmpv/libmpv-2.dll` file is missing.

The Windows archive is runtime-free and requires user-installed Java 17 or
newer. This avoids redistributing an OpenJDK runtime inside the release
artifact.

From a source checkout:

```powershell
.\tools\windows\install-libmpv-dependency.ps1 -AcceptLicense
```

The pinned dependency manifest is committed at
`third_party/windows/libmpv/zhongfly-lgpl-x86_64-20260604.json`. Do not change
its URL or hashes without repeating the producer-build review and local probe.
The corresponding source and provenance notice is committed beside it as
`SOURCE.md`.

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

For the approved zhongfly dependency, install it locally and create the Compose
distributable:

```powershell
.\tools\windows\install-libmpv-dependency.ps1 -AcceptLicense
.\gradlew.bat --no-daemon :apps:desktop-windows:createDistributable
.\tools\windows\verify-windows-mpv-runtime.ps1
```

For a future candidate bundle, use the generic verifier to copy only
manifest-listed files beside the executable:

```powershell
.\tools\windows\verify-libmpv-bundle.ps1 `
  -SourceArchivePath C:\path\to\reviewed-libmpv-archive.7z `
  -ProbeExecutable .\target\debug\mpv-probe.exe `
  -DistributionPath .\apps\desktop-windows\build\compose\binaries\main\app\desktop-windows
```

This generic direct-bundle workflow remains available for reviewing future
candidate bundles. CI directly packages only the specifically approved zhongfly
manifest.

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

This is the approved direct-redistribution bundle for an MIT-licensed Danmaku
application with separately licensed LGPL playback dependencies:

- The project publishes specifically named `mpv-dev-lgpl-*` artifacts.
- Its
  [LGPL build patch](https://github.com/zhongfly/mpv-winbuild/blob/main/compile-lgpl-libmpv.patch)
  builds mpv with `-Dgpl=false`, removes FFmpeg's `--enable-gpl`, and removes
  known GPL-incompatible dependencies such as x264 and x265.
- The project describes the LGPL libmpv artifact as LGPLv2.1+ with statically
  linked FFmpeg under LGPLv3. Treat the combined DLL distribution as requiring
  LGPLv3 compliance.

Approval is based on:

- The producer's explicit `mpv-dev-lgpl-*` artifact designation.
- The reviewed mpv configure result `gpl: false`.
- The reviewed FFmpeg configure result `License: LGPL version 3 or later`.
- The pinned archive and DLL SHA-256 hashes.
- Danmaku packaging the GPL/LGPL license texts, exact manifest, and source and
  provenance notice.

The maintainer states that they cannot guarantee every LGPL-incompatible
package has been disabled. Danmaku accepts that residual risk as a project
distribution decision rather than treating it as a prohibition.

The repository's MIT license applies to its build scripts, not to libmpv,
FFmpeg, or the resulting DLL. Directly redistributing the LGPL DLL does not
relicense Danmaku's own source.
