# Windows Installer And Updater

Implemented: 2026-08-16.

## Decision

Danmaku uses Velopack 1.2.0 for the Windows x64 per-user installer and stable
automatic updates. Git tags named `vX.Y.Z` are the sole production trigger.
The portable zip remains a supported diagnostic/development artifact but does
not update itself. macOS and Android packaging are unchanged.

## Application Flow

`VelopackApp` runs before ordinary CLI parsing. Normal installed launches
start a background GitHub release check. Smoke, screenshot/onboarding QA,
development, portable, and non-Windows launches do not check the network.

The Settings screen exposes current/checking/available/downloading/ready/error
states in English and Traditional Chinese. When an update is available, the
modal includes release notes. **Not now** dismisses it for the process;
**Update and restart** authorizes download and application. The external
updater waits for the current process to close so Rust drops can stop mpv and
the player-owned server cleanly.

## Packaging And Trust

The verified portable stage is the input to `vpk pack`. The package ID is
`app.danmaku.player`, runtime/channel is `win-x64-stable`, and the main
executable is `danmaku-player.exe`. Production uses SHA-256 Authenticode with
an RFC 3161 timestamp. CI fails if `WINDOWS_SIGNING_PFX_BASE64` or
`WINDOWS_SIGNING_PFX_PASSWORD` is absent. `libmpv-2.dll` is excluded from
signing and its release-resolved hash is checked before and after packaging.

The release workflow validates the tag against both Rust package versions and
the matching `CHANGELOG.md` section, downloads the previous stable package for
delta generation when one exists, and publishes Setup, full/delta packages,
the channel feed, portable zip, exact libmpv provenance, release notes, and
SHA-256 checksums only after verification succeeds. Before building, it resolves
the latest published LGPL x64 libmpv asset, verifies GitHub's asset digest, and
caches the archive by that digest rather than relying on an expiring dated URL.

## Background Host

Velopack's restarted hook runs background-host `Refresh`. It no-ops when the
task is absent; otherwise it stops the task, prepares a complete sibling
directory, swaps directories, rolls back on failure, and restarts the task.
Configuration and all server data remain outside the copied program directory.
The uninstall hook removes the task and copied program files but deliberately
preserves configuration, preferences, credentials, roots, and databases.

## Remaining Supervised QA

- Install/uninstall shortcut and Apps & Features behavior.
- A real two-version stable-feed upgrade, relaunch, and data persistence pass.
- Rejection of a corrupted package and recovery on retry.
- Playback shutdown and absence of orphaned player-owned server processes.
- Refresh of an actively installed background host and rollback fault testing.
