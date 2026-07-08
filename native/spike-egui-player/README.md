# egui/libmpv Compositing Spike

Throwaway Phase 0 spike for proving that libmpv can render through the
render API into an app-owned OpenGL FBO inside an egui/glow app, with egui
UI and native danmaku painted over the video. This crate is intentionally
excluded from the root Cargo workspace.

## Run

From this directory:

```powershell
cargo run --release
cargo run --release -- --media "C:\path\to\sample.mkv"
cargo run --release -- --smoke 20
```

The default media is:

```text
av://lavfi:testsrc2=duration=3600:size=1920x1080:rate=60
```

`--smoke <seconds>` opens the window, auto-plays, records render/UI/danmaku
stats, prints a PASS/FAIL report, and exits with code 0 or 1.

libmpv lookup order is:

1. `DANMAKU_LIBMPV_PATH`
2. `player_windows_mpv::locator::find_library_for_current_process()`
3. `apps/desktop-windows/build/release/windows-portable/app/libmpv-2.dll`

## Design Notes

- mpv integration uses an additive extension in `native/player-windows-mpv`:
  raw handle access plus render API symbol loading/wrappers. The spike does
  not duplicate the libmpv loader.
- Video rendering uses an `egui::PaintCallback` with `egui_glow::CallbackFn`.
  Each callback renders mpv into an app-owned GL texture/FBO, restores egui's
  target framebuffer, then draws a textured quad into the callback viewport.
- mpv is created with `vo=libmpv` and `hwdec=auto`. The render context is
  freed before the mpv handle is dropped.
- `mpv_render_context_set_update_callback` requests egui repaint, so playback
  drives redraws.
- No child-HWND, `wid`, or z-order embedding path is used.
- CJK text uses bundled `assets/NotoSansCJKtc-Regular.otf` from Noto Sans CJK
  Traditional Chinese, under the SIL Open Font License 1.1.

## Gate Status

- P0-S1: Implemented. Smoke verified FBO render callbacks with default lavfi
  media. Full visual confirmation, 4K HEVC comparison, and exact fullscreen
  restore behavior still need lead-supervised eyes-on runs.
- P0-S2: Implemented. Danmaku is driven by `danmaku_core::Timeline`; the
  synthetic 60-minute track generates 150 comments/sec and smoke reached
  1,754 active comments. Seek/pause/rate behavior is derived from mpv
  `time-pos`, but manual visual confirmation is still needed.
- P0-S3: Implemented search text field and bundled CJK font. zh-TW IME input
  still needs manual verification by the lead.
- P0-S4: Implemented virtualized `show_rows` poster grid with 1,200
  generated gradient/title posters. There is no network and no async loader
  because the placeholders are procedural.
- P0-S5: Implemented dark cinematic styling, rounded poster cards, hover
  highlight, fade-over-video controls, seek slider, and lightweight animated
  control fade. Final visual accept/reject remains a lead call.
- P0-S6: Stats overlay surfaces `hwdec-current`, `video-params`,
  `estimated-vf-fps`, and `frame-drop-count`. Default lavfi smoke reported
  `hwdec_current: no`; hardware-decode sanity on real HEVC media and multiple
  GPU classes remains unverified.

## Verification

Commands run in this crate:

```powershell
cargo fmt --check
cargo clippy
cargo test
cargo build --release
cargo run --release -- --smoke 20
```

Root workspace checks run from the repository root:

```powershell
cargo fmt --all --check
cargo test --workspace
git diff --check
```
