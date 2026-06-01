# Danmaku Project Agent Guide

## Product Direction

Build a cross-platform media library, authorized download manager, streaming
player, and danmaku overlay application.

The first-class release targets are:

1. Windows desktop
2. Android mobile and tablet
3. Android TV

Later targets are macOS, Linux, iOS, iPadOS, and web. Do not compromise the
first-class targets to force identical implementations everywhere.

## Architecture Rules

- Use Kotlin and Compose as the application layer.
- Share domain models, repositories, playback state, source contracts, and
  danmaku scheduling logic where practical.
- Keep Android TV as a dedicated app module with TV-specific layouts, focus
  behavior, and remote navigation.
- Use Media3 ExoPlayer for Android and Android TV playback.
- Use libmpv for Windows playback.
- Use Rust only for focused systems work where it clearly earns its boundary:
  high-throughput parsing, indexing, desktop downloads, cache management, and
  native desktop helpers.
- Keep Rust APIs coarse-grained. Do not cross the language boundary per frame
  or per rendered comment.
- Put platform media and download implementations behind contracts. UI and
  domain code must not depend directly on player-specific types.
- Treat provider integrations as plugins. Store normalized domain models in
  the library database, not provider response objects.
- Support authorized media sources only. Do not implement DRM circumvention or
  provider access that violates service terms.

## Repository Layout

```text
apps/                 Platform application modules
shared/               Kotlin Multiplatform domain and shared application code
native/               Rust and native platform helpers
docs/                 Architecture, roadmap, and decisions
```

## Development Order

1. Prove Windows libmpv playback and overlay composition.
2. Prove Android and Android TV Media3 playback.
3. Implement danmaku parsing, scheduling, filtering, and seeking behavior.
4. Add the local library and download queue.
5. Add provider plugins and cloud features after local workflows are solid.

## Quality Gates

- Add tests for shared domain behavior and Rust core behavior.
- Profile before moving Kotlin logic into Rust.
- Test seeking, pause/resume, playback-rate changes, and large danmaku tracks.
- Test Android TV screens with D-pad navigation and visible focus states.
- Test Windows playback with hardware decoding, fullscreen, resize, and 4K
  media before expanding desktop support.
- Keep documentation updated when architecture or milestones change.

## Working Conventions

- Prefer small, reviewable changes.
- Keep dependencies minimal until a vertical slice needs them.
- Use stable toolchain versions for committed build files.
- Commit the Gradle wrapper when the Kotlin build is bootstrapped.
- Do not commit local SDK paths, downloaded media, or generated build output.

