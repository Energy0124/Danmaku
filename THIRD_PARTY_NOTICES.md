# Third-Party Notices

Danmaku is licensed under the MIT License. Third-party software used by,
downloaded for, or linked with Danmaku remains under its own license.

## Windows Playback Dependency

Danmaku's Windows playback foundation is designed to load `libmpv-2.dll`
dynamically. Windows CI and release builds resolve the latest published
`mpv-dev-lgpl-x86_64-*.7z` asset from
[zhongfly/mpv-winbuild](https://github.com/zhongfly/mpv-winbuild), verifies the
GitHub-provided archive SHA-256, extracts only `libmpv-2.dll`, and records its
SHA-256 before packaging.

The exact producer release, asset, hashes, resolution time, and selection
policy for each Danmaku release are recorded in
`dependencies/libmpv/libmpv-provenance.json` and published beside the GitHub
Release. The release also contains the LGPL license texts and the source notice
under `dependencies/libmpv`.

The producer describes the LGPL artifact as LGPLv2.1+ libmpv with statically
linked FFmpeg under LGPLv3. The producer also states that it cannot guarantee
every LGPL-incompatible package has been disabled. Danmaku accepts that
residual risk as a project distribution decision. Redistributing the dependency
does not change Danmaku's MIT license or relicense libmpv, FFmpeg, or their
bundled dependencies.

## Application Dependencies

Danmaku also uses third-party libraries including Rust crates, Kotlin, AndroidX
Compose, AndroidX Media3, kotlinx serialization, and kotlinx coroutines. Their
source archives and license texts are distributed by their respective upstream
projects and package repositories. Release artifacts include applicable
license texts and generated dependency inventories with exact versions.
Nothing in Danmaku's MIT License replaces those terms.
