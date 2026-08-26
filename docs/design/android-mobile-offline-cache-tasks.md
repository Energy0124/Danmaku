# Android Mobile Offline Cache

## Implemented Slice

- `[x]` Cache one selected folder file without selecting siblings.
- `[x]` Cache an episode, a complete series, or a one-time recursive folder snapshot.
- `[x]` Persist authorized manifests and app-private cache metadata without access codes.
- `[x]` Download video with byte-range resume and cache resolved danmaku, subtitles, and posters.
- `[x]` Provide persistent background execution, pause, retry, cancel, delete, and clear-all.
- `[x]` Serialize queued transfers through WorkManager and throttle durable progress updates.
- `[x]` Browse and play completed entries without a live desktop connection.
- `[x]` Keep playback checkpoints outside disposable media entries and sync newer progress on reconnect.
- `[x]` Localize the mobile cache flow in English and Traditional Chinese.
- `[x]` Cover byte-range resume/restart, progress tombstones, corrupt indexes, and missing assets in unit tests.

## Boundaries

- Android phone/tablet only; Android TV and native desktop download UIs are unchanged.
- Folder selection is an explicit snapshot, not an automatic subscription.
- Storage is app-managed and never evicted automatically.
- Only files published from the user's trusted-LAN desktop roots are eligible.
