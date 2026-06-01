# Danmaku

Danmaku is a planned cross-platform media library, authorized download manager,
streaming player, and synchronized scrolling-comment overlay application.

The initial product targets are Windows, Android, and Android TV. The project
uses Kotlin and Compose for application code, Media3 on Android, libmpv on
Windows, and a deliberately small Rust core for performance-sensitive systems
work.

## Current Status

The repository is in foundation stage. It includes a shared Kotlin domain
module, a dependency-free Rust danmaku timeline index, a Windows libmpv loader
probe, and an initial Compose Desktop shell.

## Documentation

- [Architecture](docs/architecture.md)
- [Roadmap](docs/roadmap.md)
- [Task backlog](docs/tasks.md)
- [Architecture decision: Kotlin, Compose, and focused Rust](docs/adr/0001-kotlin-compose-and-focused-rust.md)
- [Architecture decision: Windows libmpv distribution](docs/adr/0002-windows-libmpv-distribution.md)

## Verify The Current Slice

```powershell
cargo test --workspace
.\gradlew.bat --no-daemon :shared:domain:jvmTest
.\gradlew.bat --no-daemon :apps:desktop-windows:compileKotlinDesktop
```

## Run The Windows Shell

```powershell
.\gradlew.bat :apps:desktop-windows:run
```

## Probe A Windows libmpv Bundle

Set `DANMAKU_LIBMPV_PATH` to an audited `libmpv-2.dll` or its directory, then
run:

```powershell
cargo run -p player-windows-mpv --bin mpv-probe
```

The probe loads the DLL, prints its client API version, initializes an mpv
context, and shuts it down cleanly.
