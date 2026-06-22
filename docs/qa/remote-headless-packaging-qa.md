# Remote And Headless Packaging QA

Use this checklist to validate the server/client split without breaking the default embedded desktop workflow.

## Automated Gates

```powershell
.\tools\windows\run-headless-web-ui-qa.ps1
.\tools\windows\run-embedded-web-ui-qa.ps1
```

The headless gate proves the standalone JVM host can serve `/web/`, catalog, media, subtitles, progress, provider readiness, provider search, external list controls, cached catalog startup, and persisted progress. The embedded gate proves the Windows desktop host serves the same web/client surface while using isolated desktop app data.

## Headless Server Checks

- Launch with explicit `--data-dir`, `--root`, `--port`, `--pairing-token`, and `--web-assets-dir`.
- Verify `server-settings.json`, stable pairing token, catalog snapshot, progress, and lock file live under the data directory.
- Attempt a second host with the same data directory and verify locking prevents concurrent writes.
- Restart without explicit roots and verify cached catalog/progress readback still works.
- Confirm logs and reports do not contain provider secrets or pairing tokens beyond the intended QA token.

## Embedded Desktop Checks

- Launch desktop with `--server-port`, `--server-pairing-token`, `--web-assets-dir`, and `--qa-library-root` against isolated `LOCALAPPDATA`.
- Verify `/api/server/status` reports `embedded-desktop` and `webUiAvailable=true`.
- Verify `/web/` loads, catalog item count is non-zero, media HEAD works, and progress read/write works.
- Run browser interaction QA against the embedded URL and pairing token.
- Relaunch without QA flags only after confirming the normal user profile is not touched by the isolated run.

## Remote Client Checks

- Launch headless server first, then launch desktop with `--remote-server-url` and `--remote-pairing-token`.
- Verify the desktop opens the Library tab and auto-loads the remote catalog when a token is supplied.
- Start remote playback from the desktop remote browser and verify libmpv receives the LAN media URL.
- Repeat the same connection from Android mobile and Android TV where devices/emulators are available.

## Packaging Checks

- Prepare the Windows portable release.
- Verify the release contains the launcher, app directory, mpv bridge, approved libmpv DLL, dependency installer, license files, and third-party notices.
- Verify the release does not contain local SDK paths, generated QA reports, provider credentials, or downloaded media.
- Run the packaged app once as embedded desktop host and once as remote client.