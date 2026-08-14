#!/bin/bash
set -euo pipefail

app_path="${1:-}"
probe_binary="${2:-}"
libmpv_path="${3:-${DANMAKU_LIBMPV_PATH:-}}"

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "macOS player verification must run on macOS." >&2
  exit 1
fi
if [[ -z "$app_path" || ! -d "$app_path" ]]; then
  echo "Usage: $0 /path/to/Danmaku.app /path/to/mpv-probe /path/to/libmpv.2.dylib" >&2
  exit 1
fi

player="$app_path/Contents/MacOS/danmaku-player"
server="$app_path/Contents/MacOS/library-server"
required=(
  "$player"
  "$server"
  "$app_path/Contents/Info.plist"
  "$app_path/Contents/Resources/AppIcon.icns"
  "$app_path/Contents/Resources/web/index.html"
  "$app_path/Contents/Resources/LICENSE"
  "$app_path/Contents/Resources/THIRD_PARTY_NOTICES.md"
  "$app_path/Contents/Resources/licenses/LGPL-3.0.txt"
  "$app_path/Contents/Resources/README.md"
)
for path in "${required[@]}"; do
  if [[ ! -f "$path" ]]; then
    echo "Required macOS bundle file is missing: $path" >&2
    exit 1
  fi
done

if [[ "$(plutil -extract CFBundleExecutable raw "$app_path/Contents/Info.plist")" != "danmaku-player" ]]; then
  echo "CFBundleExecutable is invalid." >&2
  exit 1
fi
if [[ "$(plutil -extract CFBundleIdentifier raw "$app_path/Contents/Info.plist")" != "app.danmaku.player" ]]; then
  echo "CFBundleIdentifier is invalid." >&2
  exit 1
fi

if ! "$player" --help | grep -q "Usage: danmaku-player"; then
  echo "Packaged player --help check failed." >&2
  exit 1
fi
if ! "$server" --help | grep -q "Usage: library-server"; then
  echo "Packaged server --help check failed." >&2
  exit 1
fi
if [[ ! -x "$probe_binary" || ! -f "$libmpv_path" ]]; then
  echo "mpv-probe or libmpv path is invalid." >&2
  exit 1
fi
DANMAKU_LIBMPV_PATH="$libmpv_path" "$probe_binary"

codesign --verify --deep --strict "$app_path"
if find "$app_path" -type f \( -name '*.exe' -o -name '*.dll' -o -name '*.jar' \) | grep -q .; then
  echo "macOS bundle contains a Windows or JVM runtime artifact." >&2
  exit 1
fi

echo "Verified macOS player bundle at $app_path"
echo "  player: $player"
echo "  server: $server"
echo "  libmpv: $libmpv_path"
