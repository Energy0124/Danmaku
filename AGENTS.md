# Danmaku Project Agent Guide

Keep this guide limited to durable repository rules; use the canonical documents below for changing details.

## Sources of Truth

- Setup and run instructions: [README.md](README.md)
- Implemented, partial, and missing behavior: [docs/current-state.md](docs/current-state.md)
- Platform roles and module boundaries: [docs/architecture.md](docs/architecture.md)
- Product direction and backlog: [docs/roadmap.md](docs/roadmap.md) and [docs/tasks.md](docs/tasks.md)
- Contribution policy and verification commands: [CONTRIBUTING.md](CONTRIBUTING.md)

## Engineering Principles

- Study how established products solve the problem before designing a solution. Adopt their proven patterns and conventions rather than inventing an approach from scratch.
- Do not preserve backward compatibility unless it's necessary or requested. Remove obsolete paths instead of adding compatibility layers, fallbacks, or migrations.
- Choose the simplest implementation that fully meets the current requirements. Avoid speculative abstractions, configuration, and indirection.
- Grow the system in layers. Start from the smallest version that works end to end, and add each new capability on top of a product that already works. Never trade a working product for unfinished complexity.
- Keep components modular and concerns clearly separated.
- Prefer established, well-maintained libraries when they reduce overall complexity or improve reliability. Do not reimplement common functionality without a clear reason.
- Lean on the dependencies already in the project before writing your own implementation or adding packages. Do not assume a library lacks a capability without checking its documentation and types.
- Make architectural decisions for the long term. Do not accept a stopgap that only works for now and is meant to be replaced later.

## Verification

- Use PowerShell and run Gradle tasks through `.\gradlew.bat --no-daemon`.
- Run the narrowest relevant check from [CONTRIBUTING.md](CONTRIBUTING.md); documentation-only changes do not require builds.
- Do not assume an Android device or emulator is available for instrumentation tests.
- Finish with `git diff --check`, then review the task-scoped diff and working-tree status.

## Safety Gates

| Area touched                  | Verification command                                      |
| ----------------------------- | --------------------------------------------------------- |
| `shared/domain`               | `:shared:domain:jvmTest`                                   |
| `shared/library-client`       | `:shared:library-client:jvmTest`                           |
| `shared/library-client-android` | `:shared:library-client-android:testDebugUnitTest`       |
| `shared/player-android-media3` | `:shared:player-android-media3:assembleDebugAndroidTest`  |
| `apps/android-mobile`         | `:apps:android-mobile:assembleDebug`                       |
| `apps/android-tv`             | `:apps:android-tv:assembleDebug`                           |
| `native/` (Rust)              | `cargo fmt --all --check` then `cargo test --workspace`    |
| `tools/dandanplay-worker-proxy` | `npm run typecheck` and `npm test` in that directory     |
| `apps/web-ui`                 | `npm install` and `npm run build` in that directory        |

Get explicit user approval before running:

- QA against real libraries or live external accounts.
- Scripts that boot or control Android emulators.
- Desktop GUI or screenshot QA, including localization/playback capture and `tools/windows/run-rust-player-ui-qa.ps1`.

## Architecture and Security

Always finish with `git diff --check`. The full pre-PR suite and the
connected Android instrumentation commands are listed in `CONTRIBUTING.md`.

Android instrumentation tests (`connectedDebugAndroidTest`) need a running
emulator or device; do not assume one is available.

## Do Not Run Unattended

Only run these when the user explicitly asks; they use live accounts, real
libraries, emulators, or take over the desktop session:

- `tools\windows\run-live-external-sync-readback-qa.ps1` (live MAL/Bangumi
  accounts)
- `tools\windows\run-android-mobile-emulator-qa.ps1` and
  `tools\windows\run-android-tv-emulator-qa.ps1` (boot emulators)
- `tools\windows\run-rust-player-ui-qa.ps1` and
  `tools\windows\run-windows-playback-release-qa.ps1` (launch the GUI and may
  capture the screen)

## Architecture Rules

- Use Rust/egui for Windows application and server code; use Kotlin/Compose for
  Android application code.
- Share domain models, repositories, playback state, source contracts, and
  danmaku scheduling logic where practical.
- Keep Android TV as a dedicated app module with TV-specific layouts, focus
  behavior, and remote navigation.
- Use Media3 ExoPlayer for Android and Android TV playback.
- Use libmpv for Windows playback.
- Keep native APIs coarse-grained. Do not cross a platform boundary per frame
  or per rendered comment.
- Put platform media and download implementations behind contracts. UI and
  domain code must not depend directly on player-specific types.
- Treat provider integrations as plugins. Store normalized domain models in
  the library database, not provider response objects.
- Support authorized media sources only. Do not add DRM circumvention or
  unauthorized source behavior.
- Do not log pairing tokens, credentials, cookies, signed URLs, or raw
  provider secrets.

## Localization

English and Traditional Chinese (`zh-TW`) are release requirements for UI
text. Windows strings live in `native/player-app/src/localization.rs`. Android
strings remain in each application's Android resources.

## Repository Layout

```text
apps/
  android-mobile/         Android phone/tablet app
  android-tv/             Dedicated Android TV app
  web-ui/                 Trusted-LAN TypeScript browser client (Vite)

shared/
  domain/                 Core models, catalog logic, playback contracts, danmaku logic
  library-client/         Shared LAN client/session/progress policy
  library-client-android/ Android HTTP/discovery/storage adapters
  player-android-media3/  Shared Media3 playback adapter/service

native/
  rust-core/              Rust timeline/indexing core
  library-server/         Authoritative desktop library/provider/progress host
  player-app/             Rust-native Windows player
  player-windows-mpv/     Rust libmpv loader, renderer, and probe

tools/
  windows/                Windows release, libmpv, QA, and smoke scripts
  dandanplay-worker-proxy/ Cloudflare Worker proxy for signed dandanplay requests

docs/                     Current state, architecture, roadmap, tasks, design work
```

## Documentation Upkeep

When a change affects architecture, platform behavior, security boundaries,
project state, or the roadmap, update the matching docs in the same change:

- `docs/current-state.md` for implemented/partial/missing status
- `docs/architecture.md` for module boundaries or platform roles
- `docs/roadmap.md` and `docs/tasks.md` for direction and backlog
- `README.md` for build/run/setup instructions
- the relevant task log under `docs/design/` for active design tracks

## Working Conventions

- Prefer small, reviewable changes.
- Prefer existing project patterns over new abstractions.
- Add tests for shared domain behavior, Rust core behavior, native
  boundaries, and user-visible workflows.
- Keep dependencies minimal until a vertical slice needs them.
- Use stable toolchain versions for committed build files.
- Do not commit local SDK paths, downloaded media, credentials, generated
  build output, or caches.
