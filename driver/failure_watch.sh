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

command -v gh >/dev/null 2>&1 || { echo "install gh first: sudo apt install gh && gh auth login"; exit 1; }

MODE="${1:-once}"   # once | watch
SLEEP_SECS="${2:-300}"

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
else
  triage
  echo ""
  echo "tip: $0 watch 300   (auto-check every 5 minutes)"
fi
