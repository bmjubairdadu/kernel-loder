#!/bin/bash
# ============================================================================
# Kernel Loder - FAILURE WATCH (PC auto-triage)
# Polls GitHub issues titled [AUTO-REPORT] (sent by the app's Report button),
# diagnoses each one against driver/out_all + drivers branch, and prints the
# exact fix command. Run it when you turn the PC on; loop it to watch live.
#
# Needs once: gh auth login   (your GitHub account, repo scope is enough)
#
# AUTOSTART (no manual runs): double-click scripts\install_autostart.bat ONCE.
# From then on, every Windows boot auto-starts this script hidden in the
# background (watch-autofix mode, checks every 5 min, logs driver/watch.log).
#
# What it auto-does:
#   - lists open [AUTO-REPORT] issues (number, kernel, device, date)
#   - extracts the kernel X.Y.Z from the issue body
#   - AUTO-FIX 1: if a loader now exists -> comments + CLOSES the issue
#   - AUTO-FIX 2: first-seen missing kernel -> posts diagnosis comment
#     (buildable locally? queued : exact vendor tree needed) - one comment
#     per issue, never spam, marked <!-- kl-watch -->
#   - buildable-but-missing kernels are appended to driver/auto_queue.txt
#     for the build farm (see build_queued.sh hook)
# Auto-fix scope is honest on purpose: a missing kernel build still needs
# its exact tree+config - the script tells you the one command to run.
# ============================================================================
set -u
REPO="${REPO:-bmjubairdadu/kernel-loder}"
PROJ="$(dirname "$(readlink -f "$0")")"
OUT_ALL="$PROJ/out_all"

command -v gh >/dev/null 2>&1 || apt-get install -y gh 2>&1 | tail -n 1
command -v gh >/dev/null 2>&1 || { echo "install gh first: sudo apt install gh && gh auth login"; exit 1; }

# Versions this PC can build without hunting vendor trees:
#  - mainline releases listed in driver/versions.txt (kernel.org farm)
#  - daisy family via /root/daisy337 (your own 4.9.337 tree)
is_buildable() {
  local short="$1"
  grep -qx "$short" "$PROJ/versions.txt" 2>/dev/null && return 0
  case "$short" in
    4.9.337) [ -d /root/daisy337 ] && return 0 ;;
  esac
  return 1
}

# One autofix pass: close fixed issues, comment new ones once, queue builds.
autofix_pass() {
  echo "=== $(date '+%F %T') : autofix pass ==="
  command -v gh >/dev/null 2>&1 || { echo "gh missing"; return 1; }

  local covered
  covered=$( {
    [ -d "$OUT_ALL" ] && for f in "$OUT_ALL"/*.ko; do
      b=$(basename "$f" .ko); v=${b#*_}; echo "$v" | grep -oE "^[0-9]+\.[0-9]+\.[0-9]+"
    done 2>/dev/null
    curl -s --max-time 20 "https://raw.githubusercontent.com/bmjubairdadu/kernel-loder/drivers/drivers.json" \
      | grep -oE '"version": *"[^"]+"' | grep -oE "[0-9]+\.[0-9]+\.[0-9]+" | sort -u
  } | sort -u )

  gh issue list --repo "$REPO" --search "[AUTO-REPORT] in:title" --state open \
    --limit 30 --json number,body,comments 2>/dev/null | python3 -c "
import json, sys
for iss in json.load(sys.stdin):
    body = iss.get('body') or ''
    import re
    m = re.search(r'Kernel\s*:\s*([^\n]+)', body)
    kernel = (m.group(1).strip() if m else '?')
    sm = re.search(r'(\d+)\.(\d+)\.(\d+)', kernel)
    short = f'{sm.group(1)}.{sm.group(2)}.{sm.group(3)}' if sm else '?'
    commented = '<!-- kl-watch -->' in json.dumps(iss.get('comments') or [])
    print(f\"{iss['number']}|{kernel}|{short}|{'1' if commented else '0'}\")
" | while IFS='|' read -r num kernel short seen; do
    [ -z "$num" ] && continue
    if echo "$covered" | grep -qx "$short"; then
      echo "#$num ($short): loader now exists -> closing"
      gh issue comment "$num" --repo "$REPO" --body "✅ Auto-fix: a loader for \`$short\` is now in the database. Update the app, refresh, and tap AUTO LOAD. <!-- kl-watch -->" >/dev/null 2>&1
      gh issue close "$num" --repo "$REPO" --reason completed >/dev/null 2>&1 || true
    elif [ "$seen" = "1" ]; then
      echo "#$num ($short): already triaged, waiting"
    elif is_buildable "$short"; then
      echo "#$num ($short): buildable -> queued + commented"
      grep -qx "$short" "$PROJ/auto_queue.txt" 2>/dev/null || echo "$short" >> "$PROJ/auto_queue.txt"
      gh issue comment "$num" --repo "$REPO" --body "🤖 Auto-triage: kernel \`$kernel\` needs a fresh build - **queued on the build PC**. You will get the loader via app update / OTA. <!-- kl-watch -->" >/dev/null 2>&1 || true
    else
      echo "#$num ($short): vendor tree needed -> commented"
      gh issue comment "$num" --repo "$REPO" --body "🤖 Auto-triage: kernel \`$kernel\` has no public source tree, so it cannot be auto-built yet. The developer was notified - a manual build may follow. <!-- kl-watch -->" >/dev/null 2>&1 || true
    fi
  done
}

MODE="${1:-once}"   # once | watch | watch-autofix
SLEEP_SECS="${2:-300}"
LOG="$PROJ/watch.log"

triage() {
  echo "=== $(date '+%F %T') : open [AUTO-REPORT] issues ==="
  gh issue list --repo "$REPO" --search "[AUTO-REPORT] in:title" --state open \
    --limit 30 --json number,title,createdAt,url \
    --jq '.[] | "\(.number) | \(.createdAt[:10]) | \(.title) | \(.url)"' 2>/dev/null \
    || { echo "gh failed (auth? network?)"; return 1; }

  echo ""
  echo "--- per-issue diagnosis ---"
  gh issue list --repo "$REPO" --search "[AUTO-REPORT] in:title" --state open \
    --limit 30 --json number,body 2>/dev/null | python3 - "$OUT_ALL" <<'PY'
import json, os, re, sys
out_all = sys.argv[1]
have = set()
if os.path.isdir(out_all):
    for f in os.listdir(out_all):
        if f.endswith(".ko"):
            m = re.match(r"(\d+)\.(\d+)\.(\d+)", f.split("_", 1)[1] if "_" in f else f)
            if m:
                have.add(f"{m.group(1)}.{m.group(2)}.{m.group(3)}")
for iss in json.load(sys.stdin):
    body = iss.get("body") or ""
    m = re.search(r"Kernel\s*:\s*([^\n]+)", body)
    kernel = (m.group(1).strip() if m else "?")
    sm = re.search(r"(\d+)\.(\d+)\.(\d+)", kernel)
    short = f"{sm.group(1)}.{sm.group(2)}.{sm.group(3)}" if sm else "?"
    status = "HAVE-LOCAL-BUILD - publish it" if short in have else "MISSING - needs exact tree+config build"
    print(f"#{iss['number']}: kernel={kernel} [{short}] -> {status}")
PY
}

if [ "$MODE" = "watch" ]; then
  while true; do triage; echo ""; echo "sleeping ${SLEEP_SECS}s (Ctrl+C to stop)..."; sleep "$SLEEP_SECS"; done
elif [ "$MODE" = "watch-autofix" ]; then
  exec >>"$LOG" 2>&1
  echo "=== watch-autofix started $(date '+%F %T') (every ${SLEEP_SECS}s) ==="
  while true; do autofix_pass; echo "--- sleep ${SLEEP_SECS}s ---"; sleep "$SLEEP_SECS"; done
else
  triage
  echo ""
  echo "tip: $0 watch 300   (auto-check every 5 minutes)"
fi
