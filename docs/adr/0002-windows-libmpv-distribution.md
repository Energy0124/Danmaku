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

Danmaku's own source code is MIT licensed. Until a native bundle has a complete
component/license inventory, notices, and corresponding source material,
Danmaku release artifacts remain DLL-free. They include an explicit
user-invoked installer that downloads a pinned LGPL-oriented libmpv artifact
from its producer, verifies the archive and DLL hashes, and requires license
acceptance before installation.

For any future direct redistribution, package an audited and pinned bundle
beside the executable. Prefer an LGPL-compatible build configured with
`-Dgpl=false` unless the project deliberately chooses GPL distribution. Record
the exact mpv, FFmpeg, and transitive dependency versions, configuration flags,
provenance, checksums, license notices, and any source-availability obligations
before redistribution.

Use the repository's manifest verifier to reject missing metadata, unlisted
license files, and checksum mismatches before running `mpv-probe` or copying
manifest-listed files into a distributable. The verifier does not replace the
human license and supply-chain review.

## Consequences

- The Kotlin and Rust foundation remains buildable without a native media
  bundle.
- Local development can use an explicit DLL path.
- Release packaging has an explicit legal and supply-chain review gate.
- The render integration remains a separate spike after library loading is
  proven.
