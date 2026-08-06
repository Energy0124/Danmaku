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

Get explicit user approval before running:

- QA against real libraries or live external accounts.
- Scripts that boot or control Android emulators.
- Desktop GUI or screenshot QA, including localization/playback capture and `tools/windows/run-rust-player-ui-qa.ps1`.

## Architecture and Security

- Follow current boundaries in [docs/architecture.md](docs/architecture.md); keep Android TV dedicated, with TV-specific layouts, focus, and remote navigation.
- Keep native APIs coarse-grained; do not cross native boundaries per frame or rendered comment.
- Keep provider response objects at plugin/client boundaries and persist normalized domain models.
- Support authorized media sources only; do not add DRM circumvention or unauthorized source behavior.
- Never log, commit, or expose pairing tokens, credentials, cookies, signed URLs, or raw provider secrets.

## Localization

- English and Traditional Chinese (`zh-TW`) are release requirements for user-visible text.
- Add desktop UI strings to the Compose XML resources and `DesktopStrings` adapter, not the Kotlin fallback initializer except for non-Compose error/default strings.
