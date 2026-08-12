#!/bin/bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")" && pwd)"

cd "$repo_root/apps/web-ui"
npm ci
npm run build

cd "$repo_root"
exec "$repo_root/tools/macos/prepare-rust-player-release.sh"
