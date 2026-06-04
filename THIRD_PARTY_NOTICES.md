# Third-Party Notices

Danmaku is licensed under the MIT License. Third-party software used by,
downloaded for, or linked with Danmaku remains under its own license.

## Optional Windows Playback Dependency

Danmaku's Windows playback foundation is designed to load `libmpv-2.dll`
dynamically. Danmaku release artifacts do not include libmpv or FFmpeg.

The included optional dependency installer can download this pinned third-party
artifact after the user explicitly accepts its license:

- Artifact:
  `mpv-dev-lgpl-x86_64-20260604-git-1d82932cce.7z`
- Producer:
  [zhongfly/mpv-winbuild](https://github.com/zhongfly/mpv-winbuild)
- Release:
  [2026-06-04-1d82932cce](https://github.com/zhongfly/mpv-winbuild/releases/tag/2026-06-04-1d82932cce)
- Producer build:
  [GitHub Actions run 26950382201](https://github.com/zhongfly/mpv-winbuild/actions/runs/26950382201)
- mpv source:
  [1d82932ccebd562a3edb85581c93d89d5c904b26](https://github.com/mpv-player/mpv/commit/1d82932ccebd562a3edb85581c93d89d5c904b26)
- FFmpeg source:
  [c27a3b12e3bfdedbd3af6cab9ed95c0a39ae3416](https://github.com/FFmpeg/FFmpeg/commit/c27a3b12e3bfdedbd3af6cab9ed95c0a39ae3416)
- mpv license:
  [LGPL-2.1-or-later when built with `-Dgpl=false`](https://github.com/mpv-player/mpv#license)
- FFmpeg license:
  [LGPL with configuration-dependent terms](https://ffmpeg.org/legal.html)
- LGPL version 3 text:
  [GNU Lesser General Public License version 3](https://www.gnu.org/licenses/lgpl-3.0.html)

The producer describes the `mpv-dev-lgpl-*` artifact as LGPLv2.1+ libmpv with
statically linked FFmpeg under LGPLv3. Danmaku therefore presents the optional
dependency as an LGPLv3 dependency. The producer also states that it cannot
guarantee every LGPL-incompatible package has been disabled.

Danmaku verifies the pinned archive and extracted DLL SHA-256 hashes before
installation. Installing the dependency does not change Danmaku's MIT license,
and it does not relicense libmpv, FFmpeg, or their bundled dependencies.

## Application Dependencies

Danmaku also uses third-party libraries including Kotlin, Compose
Multiplatform, AndroidX Media3, SQLDelight, SQLite JDBC, JNA, kotlinx
serialization, and kotlinx coroutines. Their source archives and license texts
are distributed by their respective upstream projects and package
repositories. The current distributable dependency graphs are validated as
Apache License 2.0 dependencies. Release artifacts include the full Apache
License 2.0 text and a generated dependency inventory with exact versions.
Nothing in Danmaku's MIT License replaces those terms.

## Windows Java Runtime

The runtime-free Windows desktop release does not include Java. Users must
install Java 17 or newer separately and accept that runtime's license. Danmaku
does not relicense or redistribute the user's Java runtime.
