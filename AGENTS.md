# Danmaku Project Agent Guide

Operational guide for coding agents working in this repository. Project
status, roadmap, and backlog live in `docs/` and are not restated here.

## Environment

- Workspace: `S:\Projects\Danmaku` on Windows 11. Run repository commands
  from this path.
- Shell: PowerShell. Use `.\gradlew.bat --no-daemon` for all Gradle tasks.
- Toolchains: JDK 17+, stable Rust, Android SDK via ignored
  `local.properties` (`sdk.dir=...`), optional Node 22 for `apps/web-ui` and
  `tools/dandanplay-worker-proxy`.
- Gradle builds in this repository are slow. Prefer the narrowest task that
  verifies your change; run the full suite only before handing off larger
  work.

## Project Status And Direction

- What is implemented, partial, and missing: `docs/current-state.md`
- Module boundaries and platform roles: `docs/architecture.md`
- Ordered product direction: `docs/roadmap.md`
- Active backlog and QA gates: `docs/tasks.md`

Product summary: a cross-platform media library, authorized download manager,
streaming player, and danmaku overlay application. First-class targets are
Windows desktop, Android mobile/tablet, and Android TV. macOS, Linux, iOS,
iPadOS, and web come later. Do not compromise the first-class targets to
force identical implementations everywhere.

## Verify Your Change

Match verification to the code you touched:

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

Gradle tasks above run as, for example:

```powershell
.\gradlew.bat --no-daemon :shared:domain:jvmTest
```

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
