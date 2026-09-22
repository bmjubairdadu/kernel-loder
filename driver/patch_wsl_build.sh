#!/usr/bin/env bash
# One-time patch: stop copying built .ko into the APK assets.
# Modules are published to the GitHub driver DB instead (publish_drivers.sh).
set -euo pipefail
F=/root/build_all.sh
[ -f "$F" ] || { echo "ERROR: $F missing"; exit 1; }

# 1. remove the assets copy
sed -i 's|cp "$MD/kloader_driver.ko" "$ASSETS/uni_$VER.ko"|# lightweight APK: publish to GitHub instead (publish_drivers.sh)|' "$F"

# 2. skip-check should look at OUT (not assets)
sed -i 's|\[ -f "$ASSETS/uni_$VER.ko" \]|[ -f "$OUT/uni_$VER.ko" ]|' "$F"

echo "--- patched lines ---"
grep -n 'ASSETS\|uni_$VER.ko' "$F" | head -10
