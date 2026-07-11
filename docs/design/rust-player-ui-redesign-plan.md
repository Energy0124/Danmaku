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

- [ ] Run supervised screenshot QA at 1280x720, 1600x900, and fullscreen.
- [ ] Refine the Settings screen into grouped consumer cards.
- [ ] Add keyboard-focus and hover-state screenshot coverage.
- [ ] Add a compact responsive fallback if Windows minimum-size QA finds
  clipping in the library hero or playback control row.
- [ ] Replace temporary glyph symbols only if a small bundled icon set clearly
  improves consistency without adding a heavy UI dependency.

## Verification Gates

- cargo fmt --all --check
- cargo test --workspace
- player-only Clippy with warnings denied
- Rust release package build and verifier
- supervised visual QA before treating the redesign as release-final