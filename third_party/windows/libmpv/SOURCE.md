# libmpv Corresponding Source And Provenance

Danmaku directly redistributes the following separately licensed Windows
playback dependency:

- File: `libmpv-2.dll`
- License: LGPL version 3 or later
- Producer artifact:
  `mpv-dev-lgpl-x86_64-20260708-git-68387ea859.7z`
- Producer release:
  <https://github.com/zhongfly/mpv-winbuild/releases/tag/2026-07-08-cc763d17dc>
- Producer build:
  <https://github.com/zhongfly/mpv-winbuild/actions/runs/28968190020>
- Archive SHA-256:
  `cc41049996a6b0010c7c15beb36c4fc5dcaceddae8f352833318118343d669ac`
- DLL SHA-256:
  `deae064ff0f48ed37927dbb83953937c67de67be65ba98ed4b01929d21db735a`

## Source

- mpv source commit:
  <https://github.com/mpv-player/mpv/commit/68387ea859d20bc3313cdbbe49ca2f25aa54b935>
- FFmpeg source commit:
  <https://github.com/FFmpeg/FFmpeg/commit/c57660fb18f058e8ead224e840b242d9c68fd3c4>
- Producer build scripts:
  <https://github.com/zhongfly/mpv-winbuild/commit/7d7d3f60010bdd2629cb6efdfa671a42fa6c32c9>
- Producer LGPL patch:
  <https://github.com/zhongfly/mpv-winbuild/blob/7d7d3f60010bdd2629cb6efdfa671a42fa6c32c9/compile-lgpl-libmpv.patch>

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
