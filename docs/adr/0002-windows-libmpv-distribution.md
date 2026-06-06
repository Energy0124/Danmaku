# ADR 0002: Windows libmpv Distribution

## Status

Accepted on 2026-06-01.

## Context

The Windows client needs broad media-format support and reliable desktop
playback. libmpv provides an embeddable client API and can render into a host
window or a host rendering context.

mpv is GPLv2-or-later by default. The official mpv build also supports an
LGPLv2.1-or-later configuration with `-Dgpl=false`. FFmpeg and other bundled
dependencies have their own configuration-dependent licensing requirements.

## Decision

Load `libmpv-2.dll` dynamically from the Windows adapter.

During development, find the DLL in this order:

1. `DANMAKU_LIBMPV_PATH`, which may name a DLL or a directory.
2. `libmpv-2.dll` beside the application executable.

Do not commit native DLLs to the repository. Contributor builds and unit tests
must work without libmpv installed.

Danmaku's own source code is MIT licensed. The Windows release directly
redistributes the pinned zhongfly `mpv-dev-lgpl-*` artifact as a separately
licensed LGPLv3-or-later dependency. Release preparation downloads the producer
archive, verifies the archive and DLL hashes, and copies only `libmpv-2.dll`
into the application directory.

The default Windows Compose distributable also builds Danmaku's Rust mpv bridge
and copies the locally installed approved `libmpv-2.dll` beside the application
so packaged smoke tests use the release-like native layout.

The release also includes GPL/LGPL license texts, the exact manifest, and a
source and provenance notice. Approval relies on the producer's explicit LGPL
artifact designation, mpv's `gpl: false` configure result, FFmpeg's
`License: LGPL version 3 or later` configure result, and the pinned hashes. The
producer's caveat about potentially missed LGPL-incompatible packages is
accepted as a project distribution risk rather than treated as a prohibition.

Use the repository's manifest verifier to reject missing metadata, unlisted
license files, and checksum mismatches before running `mpv-probe` or copying
manifest-listed files into a distributable. The verifier does not replace the
human license and supply-chain review.

## Consequences

- The Kotlin and Rust foundation remains buildable without a native media
  bundle.
- Local development can use an explicit DLL path.
- Release packaging has an explicit legal and supply-chain verification gate.
- Danmaku remains MIT licensed while the bundled DLL remains LGPL licensed.
- The native render integration is implemented through a stable Windows child
  window and mpv-rendered subtitle/ASS overlays; broad hardware and fullscreen
  validation remains a release gate.
