# ADR 0001: Kotlin, Compose, And Focused Rust

## Status

Accepted on 2026-06-01.

## Context

The product needs to support Windows, Android, Android TV, and later additional
desktop, Apple, and web platforms. Windows, Android, and Android TV are the
first-class targets. Android TV requires native-quality remote navigation and
media integration. Windows needs a capable desktop playback engine.

## Decision

Use Kotlin and Compose as the primary application stack:

- Compose Multiplatform Desktop for Windows.
- Jetpack Compose for Android.
- Compose for TV for Android TV.
- Kotlin Multiplatform modules for shared domain behavior.

Use platform media engines:

- libmpv on Windows.
- Media3 ExoPlayer on Android and Android TV.

Use Rust selectively for systems work such as high-throughput danmaku parsing
and indexing, desktop downloads, cache management, and native desktop helpers.
Do not implement UI in Rust. Do not replace Media3 with a Rust playback engine
on Android.

## Consequences

- Android TV receives a native-quality experience.
- Windows can use libmpv without forcing that player onto every platform.
- Shared Kotlin code remains straightforward to inspect and evolve.
- Rust can improve throughput and reliability where profiling supports it.
- Native boundaries require careful API design and packaging work.
- The web client will likely use React and TypeScript rather than Compose web
  while Compose web remains a less mature target.

