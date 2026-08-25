# Android Mobile Offline Cache

## Implemented Slice

- `[x]` Cache one selected folder file without selecting siblings.
- `[x]` Cache an episode, a complete series, or a one-time recursive folder snapshot.
- `[x]` Persist authorized manifests and app-private cache metadata without access codes.
- `[x]` Download video with byte-range resume and cache resolved danmaku, subtitles, and posters.
- `[x]` Provide persistent background execution, pause, retry, cancel, delete, and clear-all.
- `[x]` Browse and play completed entries without a live desktop connection.
- `[x]` Checkpoint offline playback in the Media3 service and sync newer progress on reconnect.
- `[x]` Localize the mobile cache flow in English and Traditional Chinese.

## Boundaries

- Android phone/tablet only; Android TV and native desktop download UIs are unchanged.
- Folder selection is an explicit snapshot, not an automatic subscription.
- Storage is app-managed and never evicted automatically.
- Only files published from the user's trusted-LAN desktop roots are eligible.
