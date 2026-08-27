# libmpv Corresponding Source And Provenance

Danmaku directly redistributes `libmpv-2.dll` as a separately licensed
Windows playback dependency. Windows CI and release builds resolve the newest
published `mpv-dev-lgpl-x86_64-*.7z` asset from
[zhongfly/mpv-winbuild](https://github.com/zhongfly/mpv-winbuild).

The release process rejects drafts, prereleases, GPL-named artifacts,
`x86_64-v3` builds, ambiguous matches, non-GitHub download URLs, and assets
without a GitHub-provided SHA-256 digest. It verifies that digest before
extracting only `libmpv-2.dll`, records the DLL hash, and packages the complete
result as `dependencies/libmpv/libmpv-provenance.json`.

That generated provenance document is the source of truth for a particular
Danmaku release. It records:

- the producer repository and release URL;
- the exact upstream release tag and publication time;
- the selected asset ID, filename, URL, and archive SHA-256;
- the extracted `libmpv-2.dll` SHA-256;
- the automated selection policy and accepted residual licensing risk.

Danmaku also publishes the provenance document beside each GitHub Release so
the dependency can be audited without extracting the application package.

## Source And License Evidence

The producer describes `mpv-dev-lgpl-*` as LGPLv2.1+ libmpv built without
LGPL-incompatible packages and statically linked with FFmpeg under LGPLv3.
The corresponding source and producer build scripts are available from the
exact producer release and repository recorded in the generated provenance.

Danmaku distributes the DLL under LGPLv3-or-later terms and includes the LGPL
license texts in every Windows package. The producer states that it cannot
guarantee every LGPL-incompatible package has been disabled. Danmaku accepts
that residual risk as a project distribution decision; this notice does not
relicense libmpv, FFmpeg, or their bundled dependencies.
