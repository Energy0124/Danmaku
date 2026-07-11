# Windows Playback Release QA

Use this checklist before treating Windows desktop playback as release-ready. The automated smoke script proves the libmpv bridge can play one file; this checklist covers the interactive behaviors that depend on hardware, displays, codecs, and real media.

## Prerequisites

- Build either the Rust-native player package or the Compose compatibility
  distributable. Both must contain the pinned libmpv dependency.
- Have at least one 1080p H.264 file, one 4K file, one HEVC or AV1 file if available, and one file with sidecar subtitles.
- Use a machine with hardware decoding support and, when available, a second display.

## Baseline Commands

Run the automated baseline against the release/portable distribution and every
representative media file available for the pass:

```powershell
.\tools\windows\run-windows-playback-release-qa.ps1 `
  -WindowsDistributionPath <windows-portable-path> `
  -MediaPath <1080p-media>,<4k-media>,<hevc-or-av1-media>,<sidecar-subtitle-media>
```

The runner auto-detects a root-level Rust package (`danmaku-player.exe` plus
`libmpv-2.dll`) or the legacy Compose layout. It calls
`verify-windows-mpv-runtime.ps1` and `smoke-windows-playback.ps1`, then writes
`build/qa/windows-playback/windows-playback-release-qa.md`. Use the lower-level
commands directly only when debugging one failed step.

```powershell
.\tools\windows\verify-windows-mpv-runtime.ps1 -WindowsDistributionPath <windows-portable-path>
.\tools\windows\smoke-windows-playback.ps1 -WindowsDistributionPath <windows-portable-path> -MediaPath <known-good-media>
```

## Rust-Native M5 Pass

On 2026-07-11, the packaged Rust player passed the automated release matrix
against `build/release/rust-player/danmaku-player-0.1.0-windows-x64`:

- 1080p H.264/AAC MP4: 176 rendered frames, 0 failures, NVDEC, 2 dropped.
- 1080p HEVC-10bit/ASS MKV: 178 rendered frames, 0 failures, NVDEC, 6 dropped.
- 4K HEVC/AAC MKV: 174 rendered frames, 0 failures, NVDEC, 7 dropped.
- Large BD MKV: 181 rendered frames, 0 failures, NVDEC, 5 dropped.

Each smoke ran for eight seconds. The packaged runtime probe, approved libmpv
hash check, and generated report also passed. The report is written to
`build/qa/windows-playback/windows-playback-release-qa.md`; manual interaction
and multi-display scenarios below remain release sign-off rather than blockers
for the automated M5 packaging gate.

## Latest Automated Pass

On 2026-06-22, the automated release baseline passed against the runtime-free
`apps/desktop-windows/build/release/windows-portable` package. The pass covered
runtime probing plus smoke playback for these real `W:/anime` samples:

- 1080p H.264/AAC MP4: `[ANi] OVERLORD 第四季 - 01 [1080P][Baha][WEB-DL][AAC AVC][CHT].mp4`
- 1080p HEVC/ASS MKV: `[Airota&LoliHouse] Majo no Tabitabi - 07 [WebRip 1080p HEVC-10bit AAC ASSx2].mkv`
- 4K HEVC/AAC MKV: `[NC-Raws] 电锯人 - 02 (B-Global 3840x2160 HEVC AAC MKV) [2D4CDC58].mkv`
- Large BD MKV with sidecar ASS available: `[Kamigami] Kara no Kyoukai 1 - Fukan Fuukei [BD 1920×1080 DTS-HD(5.1ch,2.0ch)].mkv`

Generated report: `build/qa/windows-playback/windows-playback-release-qa.md`.
The actual `run-danmaku.ps1` portable launcher was also smoke-tested with
the same MP4 sample and exited cleanly after reaching `PLAYING`.
Manual sign-off below is still required before calling Windows playback
release-ready.

## Latest Window-Use Manual Pass

On 2026-06-22, window-level QA against the Compose desktop app verified:

- Start playback from Home, with embedded libmpv video visible.
- Danmaku overlay attachment over the native video surface.
- Pause/resume through visible controls.
- Forward/back seek controls.
- Fullscreen enter and exit through visible controls.

Blocking bug found and fixed: the first pass showed fullscreen exit could
restore the window off-screen on a high-DPI `2560x1440` desktop, making bottom
playback controls physically unreachable. The fix saves the pre-fullscreen AWT
and Compose window state, restores AWT bounds in the scaled screen coordinate
space, then reapplies the Compose window size/position so the floating window
settles back to its original bounds.

Follow-up Computer Use QA on 2026-06-22 against the rebuilt distributable
verified the fullscreen round trip: start/playback bounds `1588x954` at
`(81,72)`, fullscreen bounds `2560x1440` at `(0,0)`, restored bounds
`1588x954` at `(81,72)`, delta `0x0` and `(0,0)`.

## Manual Scenarios

- Start playback from the local Library and verify video appears in the embedded host.
- Toggle app fullscreen on and off using the UI and keyboard shortcut.
- Resize the window while playing and verify aspect mode, controls, and overlay remain coherent.
- Seek forward/backward repeatedly and verify playback position and progress persistence.
- Pause/resume and change playback rate.
- Switch audio and subtitle tracks when the file exposes multiple tracks.
- Attach sidecar subtitle and manual danmaku overlay where available.
- Play a 4K file for at least two minutes and watch for stalls, A/V drift, or UI lockups.
- Verify hardware decoding status from mpv logs when supported by the file/GPU.
- Move the app between displays and repeat fullscreen plus resize checks.
- Close and relaunch, then verify resume position and recently watched state.

## Report Template

```text
Build/distribution path:
Machine/GPU/display setup:
Media files used:
Runtime verification: PASS/FAIL
Smoke playback: PASS/FAIL
Fullscreen: PASS/FAIL
Resize/aspect: PASS/FAIL
Seek/pause/rate: PASS/FAIL
Audio/subtitle tracks: PASS/FAIL
Danmaku overlay: PASS/FAIL
4K/hardware decode: PASS/FAIL
Multi-display: PASS/FAIL/NA
Resume after relaunch: PASS/FAIL
Blocking bugs:
Notes:
```
