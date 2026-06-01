# Danmaku

Danmaku is a planned cross-platform media library, authorized download manager,
streaming player, and synchronized scrolling-comment overlay application.

The initial product targets are Windows, Android, and Android TV. The project
uses Kotlin and Compose for application code, Media3 on Android, libmpv on
Windows, and a deliberately small Rust core for performance-sensitive systems
work.

## Current Status

The repository is in foundation stage. The first executable component is the
dependency-free Rust danmaku timeline index under `native/rust-core`.

## Documentation

- [Architecture](docs/architecture.md)
- [Roadmap](docs/roadmap.md)
- [Task backlog](docs/tasks.md)
- [Architecture decision: Kotlin, Compose, and focused Rust](docs/adr/0001-kotlin-compose-and-focused-rust.md)

## Verify The Current Slice

```powershell
cargo test --workspace
.\gradlew.bat :shared:domain:jvmTest
```
