#!/usr/bin/env bash
# The S26 update check (owner call A, R140). It builds Ledga dev at a newer version, serves it as a GitHub-shaped
# release on 127.0.0.1, and lets the phone reach it through `adb reverse`. Ledga dev only: the release build never reads it.
#
#   scripts/update-test-server.sh 2.0.0-beta.2          # PORT=8765 by default; CORRUPT=1 serves a tampered APK
#
# Then point Ledga dev at it and check at once:
#   adb shell am broadcast -a com.ledga.app.DEBUG_UPDATES -n com.ledga.app.dev/com.ledga.app.debug.DebugUpdateReceiver \
#     --es base http://127.0.0.1:8765 --ez check true
# Afterwards: --es base clear, and reinstall the normal build with `adb install -r -d` (it is a lower versionCode).
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION="${1:?usage: update-test-server.sh <a version above the installed one, e.g. 2.0.0-beta.2>}"
PORT="${PORT:-8765}"
OUT="$(mktemp -d)"
BASE="http://127.0.0.1:$PORT"

./gradlew -q :app:assembleDebug -Pledga.versionName="$VERSION"
CODE="$(./gradlew -q :app:ledgaVersion -Pledga.versionName="$VERSION" | sed -n 's/^VERSION_CODE=//p')"
MIN_SDK="$(./gradlew -q :app:ledgaVersion -Pledga.versionName="$VERSION" | sed -n 's/^MIN_SDK=//p')"
python3 scripts/release_files.py --apk app/build/outputs/apk/debug/app-debug.apk --version "$VERSION" \
  --version-code "$CODE" --min-sdk "$MIN_SDK" --out "$OUT"
if [ "${CORRUPT:-0}" = "1" ]; then
  printf 'x' >> "$OUT/ledga-$VERSION.apk"   # no longer matches the manifest's SHA-256
fi

python3 - "$OUT" "$VERSION" "$BASE" <<'PY'
import json, os, sys, time
out, version, base = sys.argv[1:4]
names = [f"ledga-{version}.apk", "ledga-release.json"]
release = {
    "tag_name": f"v{version}",
    "name": f"Ledga {version}",
    "draft": False,
    "prerelease": "-beta." in version,
    "published_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
    "html_url": f"{base}/",
    "body": "## What's new\n- A test build served from this Mac\n",
    "assets": [{"name": n, "browser_download_url": f"{base}/{n}", "size": os.path.getsize(os.path.join(out, n))} for n in names],
}
with open(os.path.join(out, "releases.json"), "w", encoding="utf-8") as f:
    json.dump([release], f, indent=2)
PY

adb reverse "tcp:$PORT" "tcp:$PORT"
echo "Serving $OUT at $BASE (Ctrl-C to stop)"
exec python3 -m http.server "$PORT" --bind 127.0.0.1 --directory "$OUT"
