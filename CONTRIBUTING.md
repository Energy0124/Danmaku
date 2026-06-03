# Contributing

Danmaku is in early foundation work. Contributions are welcome, especially when
they keep the first release targets focused: Windows desktop, Android, and
Android TV.

## Development Priorities

- Keep Windows, Android, and Android TV as first-class targets.
- Prefer Kotlin and Compose for application code.
- Use Media3 ExoPlayer for Android and Android TV playback.
- Use libmpv for Windows playback.
- Use Rust only for focused native systems work such as parsing, indexing,
  desktop helper APIs, download/cache management, or native playback bridges.
- Keep native APIs coarse-grained. Do not cross the Kotlin/Rust boundary per
  video frame or per danmaku comment.

## Source And Security Rules

- Support authorized media sources only.
- Do not add DRM circumvention.
- Do not add torrent/search/download behavior without an approved authorized
  source policy.
- Do not log pairing tokens, credentials, cookies, signed URLs, or raw provider
  secrets.
- Keep provider-specific response models at plugin boundaries. Persist
  normalized domain models instead.
- Treat the current LAN server as trusted-local-network only.

## Before Opening A PR

Run the relevant checks for your change:

```powershell
cargo fmt --all --check
cargo test --workspace
.\gradlew.bat --no-daemon :shared:domain:jvmTest
.\gradlew.bat --no-daemon :shared:library-client:jvmTest
.\gradlew.bat --no-daemon :shared:library-server-core:jvmTest
.\gradlew.bat --no-daemon :apps:desktop-windows:desktopTest
.\gradlew.bat --no-daemon :shared:library-client-android:testDebugUnitTest
.\gradlew.bat --no-daemon :shared:player-android-media3:assembleDebugAndroidTest
.\gradlew.bat --no-daemon :apps:android-mobile:assembleDebug :apps:android-tv:assembleDebug
```

With an Android emulator or device online, also run:

```powershell
.\gradlew.bat --no-daemon :shared:player-android-media3:connectedDebugAndroidTest
.\gradlew.bat --no-daemon :apps:android-tv:connectedDebugAndroidTest
```

## Documentation

Update documentation when a change affects architecture, platform behavior,
security boundaries, or the roadmap. The most useful files are:

- `README.md`
- `docs/current-state.md`
- `docs/tasks.md`
- `docs/architecture.md`
- `docs/roadmap.md`

## Style

- Keep changes small and reviewable.
- Prefer existing project patterns over new abstractions.
- Add tests for shared domain behavior, Rust core behavior, native boundaries,
  and user-visible workflows.
- Avoid committing local SDK paths, downloaded media, credentials, build output,
  or generated caches.
