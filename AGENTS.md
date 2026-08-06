# Danmaku Project Agent Guide

Keep this guide limited to durable repository rules; use the canonical documents below for changing details.

## Sources of Truth

- Setup and run instructions: [README.md](README.md)
- Implemented, partial, and missing behavior: [docs/current-state.md](docs/current-state.md)
- Platform roles and module boundaries: [docs/architecture.md](docs/architecture.md)
- Product direction and backlog: [docs/roadmap.md](docs/roadmap.md) and [docs/tasks.md](docs/tasks.md)
- Contribution policy and verification commands: [CONTRIBUTING.md](CONTRIBUTING.md)

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
