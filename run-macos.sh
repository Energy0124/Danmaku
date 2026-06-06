#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$script_dir"

cargo build --release -p player-windows-mpv --lib

export DANMAKU_MPV_BRIDGE_PATH="$script_dir/target/release/libplayer_windows_mpv.dylib"

if [[ -z "${DANMAKU_LIBMPV_PATH:-}" ]]; then
  for candidate in \
    "$script_dir/runtime/macos/libmpv/libmpv.2.dylib" \
    "$script_dir/runtime/macos/libmpv/libmpv.dylib" \
    "/opt/homebrew/lib/libmpv.2.dylib" \
    "/opt/homebrew/lib/libmpv.dylib" \
    "/usr/local/lib/libmpv.2.dylib" \
    "/usr/local/lib/libmpv.dylib"; do
    if [[ -f "$candidate" ]]; then
      export DANMAKU_LIBMPV_PATH="$candidate"
      break
    fi
  done
fi

if [[ -z "${DANMAKU_LIBMPV_PATH:-}" ]]; then
  echo "DANMAKU_LIBMPV_PATH is not set and libmpv was not found in common macOS locations."
  echo "The app will still start, but playback will use command-log-only mode until libmpv is installed."
fi

bash ./gradlew --no-daemon :apps:desktop-windows:run
