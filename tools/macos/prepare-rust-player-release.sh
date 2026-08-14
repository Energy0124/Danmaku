#!/bin/bash
set -euo pipefail

script_dir="$(cd "$(dirname "$0")" && pwd)"
repo_root="$(cd "$script_dir/../.." && pwd)"
release_root="$repo_root/build/release/macos"
web_dist="${DANMAKU_WEB_UI_DIST:-$repo_root/apps/web-ui/dist}"

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "macOS player packaging must run on macOS." >&2
  exit 1
fi

mkdir -p "$release_root"

if [[ ! -f "$web_dist/index.html" ]]; then
  echo "Built web UI is missing at $web_dist/index.html; run npm ci and npm run build in apps/web-ui." >&2
  exit 1
fi

resolve_libmpv() {
  local configured="${DANMAKU_LIBMPV_PATH:-}"
  local candidate
  if [[ -n "$configured" ]]; then
    if [[ -d "$configured" ]]; then
      for candidate in "$configured/libmpv.2.dylib" "$configured/libmpv.dylib"; do
        [[ -f "$candidate" ]] && { printf '%s\n' "$candidate"; return 0; }
      done
    elif [[ -f "$configured" ]]; then
      printf '%s\n' "$configured"
      return 0
    fi
  fi
  if command -v brew >/dev/null 2>&1; then
    local brew_prefix
    brew_prefix="$(brew --prefix mpv 2>/dev/null || true)"
    for candidate in "$brew_prefix/lib/libmpv.2.dylib" "$brew_prefix/lib/libmpv.dylib"; do
      [[ -f "$candidate" ]] && { printf '%s\n' "$candidate"; return 0; }
    done
  fi
  for candidate in \
    /opt/homebrew/opt/mpv/lib/libmpv.2.dylib \
    /opt/homebrew/opt/mpv/lib/libmpv.dylib \
    /usr/local/opt/mpv/lib/libmpv.2.dylib \
    /usr/local/opt/mpv/lib/libmpv.dylib; do
    [[ -f "$candidate" ]] && { printf '%s\n' "$candidate"; return 0; }
  done
  return 1
}

libmpv_path="$(resolve_libmpv || true)"
if [[ -z "$libmpv_path" ]]; then
  echo "Homebrew libmpv was not found. Install it with: brew install mpv" >&2
  echo "Alternatively set DANMAKU_LIBMPV_PATH to libmpv.2.dylib or its directory." >&2
  exit 1
fi

version="$(awk -F '"' '/^version = "/ { print $2; exit }' "$repo_root/native/player-app/Cargo.toml")"
if [[ -z "$version" ]]; then
  echo "Could not read the danmaku-player version." >&2
  exit 1
fi

architecture="$(uname -m)"
case "$architecture" in
  arm64) package_arch="arm64" ;;
  x86_64) package_arch="x64" ;;
  *)
    echo "Unsupported macOS architecture: $architecture" >&2
    exit 1
    ;;
esac

target_root="${CARGO_TARGET_DIR:-$repo_root/target}"
if [[ "$target_root" != /* ]]; then
  target_root="$repo_root/$target_root"
fi

cd "$repo_root"
cargo build --release --locked -p danmaku-player -p library-server
cargo build --release --locked -p player-windows-mpv --bin mpv-probe

player_binary="$target_root/release/danmaku-player"
server_binary="$target_root/release/library-server"
probe_binary="$target_root/release/mpv-probe"
for required in "$player_binary" "$server_binary" "$probe_binary"; do
  if [[ ! -x "$required" ]]; then
    echo "Required release executable is missing: $required" >&2
    exit 1
  fi
done

package_name="danmaku-player-$version-macos-$package_arch"
stage_root="$release_root/$package_name"
app_path="$stage_root/Danmaku.app"
zip_path="$release_root/$package_name.zip"

rm -rf "$stage_root"
rm -f "$zip_path"
mkdir -p "$app_path/Contents/MacOS" "$app_path/Contents/Resources/licenses"

cp "$player_binary" "$app_path/Contents/MacOS/danmaku-player"
cp "$server_binary" "$app_path/Contents/MacOS/library-server"
cp -R "$web_dist" "$app_path/Contents/Resources/web"
cp "$repo_root/LICENSE" "$app_path/Contents/Resources/LICENSE"
cp "$repo_root/THIRD_PARTY_NOTICES.md" "$app_path/Contents/Resources/THIRD_PARTY_NOTICES.md"
for license in APACHE-2.0.txt GPL-3.0.txt LGPL-2.1.txt LGPL-3.0.txt; do
  cp "$repo_root/third_party/licenses/$license" "$app_path/Contents/Resources/licenses/$license"
done

cat > "$app_path/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "https://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleDevelopmentRegion</key><string>en</string>
  <key>CFBundleDisplayName</key><string>Danmaku</string>
  <key>CFBundleExecutable</key><string>danmaku-player</string>
  <key>CFBundleIconFile</key><string>AppIcon</string>
  <key>CFBundleIdentifier</key><string>app.danmaku.player</string>
  <key>CFBundleInfoDictionaryVersion</key><string>6.0</string>
  <key>CFBundleName</key><string>Danmaku</string>
  <key>CFBundlePackageType</key><string>APPL</string>
  <key>CFBundleShortVersionString</key><string>$version</string>
  <key>CFBundleVersion</key><string>$version</string>
  <key>LSApplicationCategoryType</key><string>public.app-category.video</string>
  <key>LSMinimumSystemVersion</key><string>12.0</string>
  <key>NSHighResolutionCapable</key><true/>
</dict>
</plist>
PLIST

icon_source="$repo_root/native/player-app/assets/app-icon.png"
sips -s format icns "$icon_source" \
  --out "$app_path/Contents/Resources/AppIcon.icns" >/dev/null

cat > "$app_path/Contents/Resources/README.md" <<README
# Danmaku for macOS $version

This is the native Rust macOS development build. It contains the player,
local library server, and web administration UI. Install the runtime playback
dependency with \`brew install mpv\` before launching the app.

The app stores durable data under \`~/Library/Application Support/Danmaku\`
and disposable session data under \`~/Library/Caches/Danmaku\`.

The app is ad-hoc signed for local development and is not notarized. Online
provider HTTPS and secure provider-token persistence remain Windows-only in
this initial macOS slice; local/LAN playback and local danmaku files are
supported.
README

plutil -lint "$app_path/Contents/Info.plist" >/dev/null
codesign --force --deep --sign - "$app_path"

"$script_dir/verify-rust-player-release.sh" "$app_path" "$probe_binary" "$libmpv_path"

ditto -c -k --sequesterRsrc --keepParent "$app_path" "$zip_path"
echo "Prepared macOS player bundle: $app_path"
echo "Prepared macOS player archive: $zip_path"
