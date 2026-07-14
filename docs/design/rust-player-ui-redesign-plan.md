# Rust Player UI Redesign

## Goal

Move the native Windows player from a functional migration client to a calm,
consumer-facing media application. Server and playback machinery remain
available, but the default interface emphasizes content and progressive
disclosure instead of connection details and control density.

The approved visual references are:

- [Library and home](rust-player-ui-mockups/library-home.png)
- [Playback](rust-player-ui-mockups/playback.png)
- [First-run onboarding](rust-player-ui-mockups/onboarding.png)

The mockups set hierarchy and tone. The implementation remains native egui and
does not depend on generated imagery or copy a commercial streaming product.

## Design Rules

1. Content occupies most of the window; navigation and status stay quiet.
2. Local hosting is automatic. Raw URLs and pairing fields are advanced paths.
3. One primary action appears per empty or setup state.
4. Playback chrome fades over video and keeps infrequent options in menus.
5. Blue is a focus/action accent, not a decorative glow across every surface.
6. English and Traditional Chinese ship together for every new visible string.
7. Layout must remain usable at the existing desktop minimum window size.

## Applied Screen Structure

### First Run

- Compact brand bar with language selection.
- Centered library illustration, headline, explanation, and one folder action.
- Three-step setup indicator and a small local-server state card.
- Remote discovery and manual URL/token fields remain collapsed until requested.

### Library

- Persistent 88 px icon-first navigation rail.
- Product header with library summary, search, and a quiet online indicator.
- Continue Watching promotes one item into a cinematic hero.
- Remaining progress, Next Up, and recently added series use larger poster cards.
- Refresh, settings, and disconnect move out of the content header.

### Playback

- Video remains edge-to-edge.
- A minimal title/back band and one floating bottom control surface fade together.
- Progress is visually primary; playback actions use compact symbols and tooltips.
- Audio, subtitles, speed, danmaku, settings, and fullscreen stay available
  without exposing dense sliders permanently.
- Volume and mute remain direct controls.

## Follow-up

- [x] Run supervised screenshot QA at 1280x720 and maximized desktop size,
  including live-video playback with the controls visible.
- [x] Refine the Settings screen into grouped consumer cards.
- [x] Add keyboard-focus and hover-state screenshot coverage. The repeatable
  `tools/windows/run-rust-player-ui-qa.ps1` wrapper captures deterministic
  onboarding states with a visible keyboard-focus ring.
- [x] Run Windows minimum-size QA at 960x600. The onboarding and local-server
  content fit through vertical scrolling without horizontal clipping, so a
  separate compact responsive fallback is not required.
- [x] Replace temporary glyph symbols with a small code-native vector icon set,
  avoiding font-dependent missing-glyph boxes and a new UI dependency.

### Second alignment pass (2026-07)

- [x] Library home restructured to the mockup order: wordmark + greeting,
  search, full-bleed Continue Watching hero (episode line, progress,
  remaining minutes), rails, and a quiet Local library / Online pill.
- [x] Poster cards switched to full-bleed art with caption scrims.
- [x] Playback control bar rebuilt: full-width thumbed seek bar; time and
  volume on the left, centered transport, track/danmaku/speed/settings/
  fullscreen on the right; labeled Danmaku toggle; gradient title band with
  series and episode lines; "Next:" episode preview card.
- [x] Onboarding: vertically centered column, badge-style local server card,
  link-style secondary action, centered globe language selector.
- [x] Screenshot QA re-run against the three mockups with a neutral generated
  media fixture (onboarding, library home, playback with controls).
- [x] Settings rebuilt into grouped consumer cards: a back chip + title
  header, a centered content column, rounded raised cards per group, a
  segmented language selector, themed accent sliders with value readouts,
  animated pill toggles, and styled action buttons. Removes the raw egui
  slider/checkbox/button look.

## Verification Gates

- cargo fmt --all --check
- cargo test --workspace
- player-only Clippy with warnings denied
- Rust release package build and verifier
- supervised visual QA before treating the redesign as release-final
