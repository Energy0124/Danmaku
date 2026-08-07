# Native Player And Headless Server Packaging QA

Use this checklist to validate the Rust server/player split. GUI, real-media,
real-library, emulator, and live-provider steps require explicit supervision.

## Automated Gates

```powershell
.\tools\windows\run-headless-web-ui-qa.ps1
.\build-rust-player.bat
.\tools\windows\prepare-rust-server-release.ps1
```

The headless gate builds the web UI, starts an isolated fixture-backed Rust
server, verifies status/catalog/media/subtitle/progress/provider routes,
restarts it to prove persisted catalog/progress behavior, and optionally runs
the Chrome/Edge interaction probe.

## Server Checks

- Launch with explicit `--data-dir`, `--root`, `--port`, `--pairing-token`, and
  `--web-assets-dir`.
- Verify settings, catalog, progress, tracking state, and the lock remain under
  the data directory.
- Verify a second process cannot write the same data directory.
- Restart without CLI roots and confirm saved roots/catalog/progress load.
- Confirm logs and reports redact credentials, pairing tokens, and signed URLs.

## Player Checks

- Launch without arguments, select a root, and verify the packaged server is
  started and connected without blocking the UI.
- Verify the player stops only a server process it owns.
- Install the optional background host and verify the player attaches without
  exposing child-process controls.
- Connect to another LAN server manually and through discovery.
- Play local and remote catalog items and verify resume/progress round trips.

## Package Checks

- Verify the native zip contains the player, server, web assets, pinned libmpv,
  launch/background scripts, licenses, provenance, and dependency inventories.
- Verify no Java runtime, application JARs, JVM bridge DLL, credentials, SDK
  paths, generated QA reports, or downloaded media are present.
- Run the native `--help`, server `--help`, background-host PlanOnly, libmpv
  probe, and supervised real-media smoke paths.
