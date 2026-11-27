#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

if [ ! -f local.properties ] && [ -d "$HOME/Android/Sdk" ]; then
  echo "sdk.dir=$HOME/Android/Sdk" > local.properties
fi

AAPT2_FLAG=""
if [ -x "${PREFIX:-/data/data/com.termux/files/usr}/bin/aapt2" ]; then
  AAPT2_FLAG="-Pandroid.aapt2FromMavenOverride=${PREFIX:-/data/data/com.termux/files/usr}/bin/aapt2"
fi

gradle :app:assembleDebug --no-daemon $AAPT2_FLAG
echo "APK ready at: app/build/outputs/apk/debug/app-debug.apk"
