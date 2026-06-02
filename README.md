# Danmaku

Danmaku is a planned cross-platform media library, authorized download manager,
streaming player, and synchronized scrolling-comment overlay application.

The initial product targets are Windows, Android, and Android TV. The project
uses Kotlin and Compose for application code, Media3 on Android, libmpv on
Windows, and a deliberately small Rust core for performance-sensitive systems
work.

## Current Status

The canonical checkout is `S:\Projects\Danmaku`.

The repository has a working first local-network vertical slice. The Windows
Compose shell recursively indexes an anime folder and exposes a JSON catalog
plus paired byte-range media streaming on port `8686`. Android mobile and
Android TV clients compile into APKs, browse that PC catalog with the displayed
pairing code, discover the server over the LAN, and hand selected streams to
Media3 ExoPlayer.

Windows native playback rendering is not connected yet. The desktop shell also
includes a synthetic animated overlay demo backed by the shared
collision-aware danmaku lane scheduler.

## Documentation

- [Current state](docs/current-state.md)
- [Architecture](docs/architecture.md)
- [Roadmap](docs/roadmap.md)
- [Task backlog](docs/tasks.md)
- [Architecture decision: Kotlin, Compose, and focused Rust](docs/adr/0001-kotlin-compose-and-focused-rust.md)
- [Architecture decision: Windows libmpv distribution](docs/adr/0002-windows-libmpv-distribution.md)

## Verify The Current Slice

```powershell
cargo test --workspace
.\gradlew.bat --no-daemon :shared:domain:jvmTest
.\gradlew.bat --no-daemon :apps:desktop-windows:desktopTest
.\gradlew.bat --no-daemon :apps:android-mobile:assembleDebug :apps:android-tv:assembleDebug
```

## Run The Windows Shell

```powershell
.\gradlew.bat :apps:desktop-windows:run
```

Choose an anime folder in the Windows shell, then use `Discover PC` in the
Android or Android TV client. Enter the pairing code displayed by Windows and
refresh the PC library. Manual LAN URL entry remains available. The server is
intended for trusted local networks only.

## Probe A Windows libmpv Bundle

Set `DANMAKU_LIBMPV_PATH` to an audited `libmpv-2.dll` or its directory, then
run:

```powershell
cargo run -p player-windows-mpv --bin mpv-probe
```

The probe loads the DLL, prints its client API version, initializes an mpv
context, and shuts it down cleanly.
