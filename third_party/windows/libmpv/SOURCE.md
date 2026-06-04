# libmpv Corresponding Source And Provenance

Danmaku directly redistributes the following separately licensed Windows
playback dependency:

- File: `libmpv-2.dll`
- License: LGPL version 3 or later
- Producer artifact:
  `mpv-dev-lgpl-x86_64-20260604-git-1d82932cce.7z`
- Producer release:
  <https://github.com/zhongfly/mpv-winbuild/releases/tag/2026-06-04-1d82932cce>
- Producer build:
  <https://github.com/zhongfly/mpv-winbuild/actions/runs/26950382201>
- Archive SHA-256:
  `eacba7b1afdb5620fd556da1141fc5267dca9d81a5d7a649a36384af77405855`
- DLL SHA-256:
  `7e68bedee4c2241056ad9fb9fb3fca5a967d442f900e3891dfd5be95b8fecf49`

## Source

- mpv source commit:
  <https://github.com/mpv-player/mpv/commit/1d82932ccebd562a3edb85581c93d89d5c904b26>
- FFmpeg source commit:
  <https://github.com/FFmpeg/FFmpeg/commit/c27a3b12e3bfdedbd3af6cab9ed95c0a39ae3416>
- Producer build scripts:
  <https://github.com/zhongfly/mpv-winbuild/commit/970da30d1d88d8d1e863e0f0cf4b57f2a5abdcc5>
- Producer LGPL patch:
  <https://github.com/zhongfly/mpv-winbuild/blob/970da30d1d88d8d1e863e0f0cf4b57f2a5abdcc5/compile-lgpl-libmpv.patch>

The producer build scripts identify and fetch the transitive source packages
used by the build. Danmaku preserves the exact producer commit, upstream
commits, build-run URL, archive hash, and DLL hash so recipients can obtain and
verify the corresponding source and rebuild the dependency.

## Build And License Evidence

The reviewed producer build reports:

- mpv `gpl: false`
- FFmpeg `License: LGPL version 3 or later`
- an LGPL patch that removes FFmpeg `--enable-gpl` and known GPL dependencies
  such as x264 and x265

The producer explicitly publishes this artifact as `mpv-dev-lgpl-*`, describing
it as LGPLv2.1+ libmpv statically linked with FFmpeg under LGPLv3. Danmaku
therefore distributes the DLL under LGPLv3-or-later terms.

The producer also states that it cannot guarantee every LGPL-incompatible
package has been disabled. Danmaku accepts that residual risk as a project
distribution decision. This notice does not relicense libmpv, FFmpeg, or any
bundled dependency.
