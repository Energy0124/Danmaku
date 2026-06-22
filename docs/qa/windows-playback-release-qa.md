# Windows Playback Release QA

Use this checklist before treating Windows desktop playback as release-ready. The automated smoke script proves the libmpv bridge can play one file; this checklist covers the interactive behaviors that depend on hardware, displays, codecs, and real media.

## Prerequisites

- Build the Windows distributable and install the pinned libmpv dependency.
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

The runner calls `verify-windows-mpv-runtime.ps1` and
`smoke-windows-playback.ps1`, then writes
`build/qa/windows-playback/windows-playback-release-qa.md`. Use the lower-level
commands directly only when debugging one failed step.

```powershell
.\tools\windows\verify-windows-mpv-runtime.ps1 -WindowsDistributionPath <windows-portable-path>
.\tools\windows\smoke-windows-playback.ps1 -WindowsDistributionPath <windows-portable-path> -MediaPath <known-good-media>
```

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