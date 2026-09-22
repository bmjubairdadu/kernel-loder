#!/bin/bash
# ============================================================================
# Kernel Loder - UNIVERSAL .ko FACTORY (WSL)
# Builds kloader_driver.ko for EVERY kernel.org release in versions.txt.
#  - native Daisy device builds (4.9.337 / 4.9.307) stay shipped as-is AND the
#    pipeline also builds mainline uni_4.9.337 / uni_4.9.307 for other phones
#    with the same X.Y.Z (the loader only prefers the Daisy build on a real
#    Daisy kernel)
#  - skips versions whose source tarball does not exist on kernel.org (404)
#  - per-version: defconfig (MODVERSIONS/SIG/BTF off -> loadable on ANY device
#    with the same X.Y.Z), modules_prepare, module build, vermagic check,
#    copy to app assets as uni_<ver>.ko, then delete the tree to save disk.
#  - 3 parallel workers via flock queue; every step logged.
# ============================================================================
set -u
PROJ=/mnt/c/Users/Administrator/Downloads/DaisyDiverLoder
OUT=$PROJ/driver/out_all
ASSETS=$PROJ/app/src/main/assets/drivers
STATUS=$PROJ/driver/build_all_status.txt
LOGDIR=$PROJ/driver/logs
WORK=/root/kernel-all
TOOL=$WORK/toolchain
VFILE=$PROJ/driver/versions.txt
QUEUE=$WORK/queue.txt
LOCK=$WORK/queue.lock
mkdir -p "$OUT" "$LOGDIR" "$WORK"

log(){ echo "$(date +%m-%d\ %H:%M:%S) $*" >> "$STATUS"; }

# ---------- deps ----------
# Only touch apt when something is actually missing. The archive mirrors are often
# unreachable from WSL, and a bare `apt-get update` then retries for minutes while
# every package we need is already installed - which stalled a whole relaunch.
export DEBIAN_FRONTEND=noninteractive
have_all=1
for t in bc bison flex curl xz git; do
  command -v "$t" >/dev/null 2>&1 || have_all=0
done
if [ "$have_all" -eq 1 ]; then
  log "SETUP: build deps already present - apt skipped"
else
  log "SETUP: installing build deps..."
  (apt-get update -y && apt-get install -y curl xz-utils bc bison flex libssl-dev libelf-dev git) >> $LOGDIR/deps.log 2>&1 \
    || log "SETUP: apt FAILED (continuing, deps may already exist)"
fi

# ---------- toolchains ----------
if [ ! -x "$TOOL/gcc49/bin/aarch64-linux-android-gcc" ]; then
  log "TOOLCHAIN: cloning LineageOS gcc 4.9 (aarch64)..."
  git clone --depth=1 https://github.com/LineageOS/android_prebuilts_gcc_linux-x86_aarch64_aarch64-linux-android-4.9 "$TOOL/gcc49" >> $LOGDIR/toolchain.log 2>&1 \
    || log "TOOLCHAIN: gcc49 clone FAILED"
fi
if [ ! -x "$TOOL/proton/bin/clang" ] && ! command -v clang >/dev/null 2>&1; then
  log "TOOLCHAIN: no proton and no system clang - installing apt clang..."
  apt-get install -y clang lld >> $LOGDIR/deps.log 2>&1 || log "TOOLCHAIN: apt clang FAILED"
fi

# ---------- queue ----------
tr -d '\r' < "$VFILE" > "$QUEUE"
touch "$LOCK"

build_one(){
  local VER=$1
  local MAJOR=${VER%%.*}
  local TARBALL=$WORK/linux-$VER.tar.xz
  local TREE=$WORK/linux-$VER

  [ -f "$OUT/uni_$VER.ko" ] && { log "SKIP-DONE  $VER"; return 0; }
  [ -f "$ASSETS/uni_$VER.ko" ] && { log "SKIP-DONE  $VER"; return 0; }

  # mainline arm64 starts at 3.7, so 3.0..3.6 can never produce an arm64 module.
  # This check used to sit AFTER the download, so every one of those versions wasted
  # a full (throttled, ~20 min) tarball fetch before being skipped. Do it first.
  if [ "$MAJOR" -eq 3 ]; then
    local MINOR3=${VER#3.}; MINOR3=${MINOR3%%.*}
    [ "$MINOR3" -lt 7 ] && { log "SKIP-NOARM64 $VER (mainline arm64 starts at 3.7)"; return 2; }
  fi

  local URL="https://cdn.kernel.org/pub/linux/kernel/v${MAJOR}.x/linux-$VER.tar.xz"
  # --- tarball fetch ----------------------------------------------------------
  # The CDN link is throttled to ~50 KB/s, so one 64 MB tarball takes ~20 min.
  # The old code had no resume and deleted the partial on failure, so every abort
  # restarted from zero and was then mislabelled "no source tarball on kernel.org"
  # (a Windows-side HEAD check proves those URLs answer HTTP 200). Now: validate a
  # cached tarball, keep + resume partials, verify with xz -t, and report
  # SKIP-NOSRC only when kernel.org really answers 404/403/410.
  if [ -f "$TARBALL" ] && ! xz -t "$TARBALL" >/dev/null 2>&1; then
    log "DISCARD    $VER (cached tarball corrupt/truncated - re-downloading)"
    rm -f "$TARBALL"
  fi
  if [ ! -f "$TARBALL" ]; then
    local try=0 CODE=000 CRES=""
    while [ "$try" -lt 4 ]; do
      try=$((try+1))
      CRES=""
      [ -s "$TARBALL.part" ] && CRES="--continue-at -"
      log "DOWNLOAD   $VER (attempt $try) ..."
      CODE=$(curl -L --fail --retry 3 --retry-delay 5 --connect-timeout 30 \
                  $CRES --max-time 5400 --speed-limit 1000 --speed-time 120 \
                  -o "$TARBALL.part" -w '%{http_code}' "$URL" 2>"$LOGDIR/dl-$VER.err")
      [ -z "$CODE" ] && CODE=000
      if [ -s "$TARBALL.part" ] && xz -t "$TARBALL.part" >/dev/null 2>&1; then
        mv -f "$TARBALL.part" "$TARBALL"
        log "GOT        $VER ($(stat -c%s "$TARBALL") bytes)"
        break
      fi
      case "$CODE" in
        404|403|410)
          log "SKIP-NOSRC $VER (kernel.org answers HTTP $CODE - release not on cdn)"
          rm -f "$TARBALL.part"
          return 2 ;;
      esac
      log "RETRY-DL   $VER (http=$CODE, kept $(stat -c%s "$TARBALL.part" 2>/dev/null || echo 0) bytes, resuming)"
      sleep 10
    done
    if [ ! -f "$TARBALL" ]; then
      log "FAIL-DL    $VER (incomplete after 4 attempts - partial kept so the next run resumes)"
      return 1
    fi
  fi

  rm -rf "$TREE"; mkdir -p "$TREE"
  tar -xf "$TARBALL" -C "$TREE" --strip-components=1 \
    || { log "FAIL       $VER (extract)"; rm -rf "$TREE"; return 1; }

  local MD=$WORK/mod-$VER
  rm -rf "$MD"; mkdir -p "$MD"
  sed "s/4\.9\.337/$VER/g; s/2\.0-337/2.0-${VER}/g" "$PROJ/driver/kloader_driver.c" > "$MD/kloader_driver.c"
  printf 'obj-m += kloader_driver.o\n' > "$MD/Makefile"

  local GCC49=$TOOL/gcc49/bin
  local PROTON=$TOOL/proton/bin
  # fall back to apt clang if proton clone is missing
  [ -x "$PROTON/clang" ] || PROTON=/usr/bin

  try_toolchain(){
    local NAME=$1; shift
    local TLOG=$LOGDIR/$VER.$NAME.log
    local PATHPRE=""
    local -a VARS=()
    for a in "$@"; do
      case "$a" in
        PATHPRE=*) PATHPRE=${a#PATHPRE=} ;;
        *) VARS+=("$a") ;;
      esac
    done
    : > "$TLOG"
    ( export PATH="$PATHPRE"
      for v in "${VARS[@]}"; do export "$v"; done
      cd "$TREE"
      echo "--- defconfig ---"
      make ARCH=arm64 defconfig                      || exit 1
      # universal-friendly config: no CRC table, no signing, no BTF/pahole
      ./scripts/config -d MODVERSIONS -d MODULE_SIG -d DEBUG_INFO_BTF -e MODULES 2>/dev/null || true
      make ARCH=arm64 olddefconfig                   || exit 1
      echo "--- modules_prepare ---"
      # HOSTCFLAGS -fcommon: host GCC 10+ defaults to -fno-common which breaks
      # old-kernel host tools (dtc "multiple definition of yylloc" etc.)
      make ARCH=arm64 -j"$(nproc)" modules_prepare HOSTCFLAGS="-O2 -fcommon" || exit 1
      echo "--- module build ---"
      if ! make ARCH=arm64 M="$MD" modules HOSTCFLAGS="-O2 -fcommon"; then
        # Some arm64 trees (3.14, and 4.2/4.3 with CONFIG_ARM64_ERRATUM_843419) build
        # modules with -mcmodel=large together with -fPIC, which both GCC and clang
        # reject ("sorry, unimplemented: code model 'large' with -fpic"). The
        # workaround exists to emit PLT stubs for the erratum - our driver never runs
        # that sequence, so drop it and force -fno-pic, then build once more.
        if grep -q "code model 'large' with -fpic" "$TLOG" 2>/dev/null; then
          echo "--- PIC RETRY: dropping ARM64 erratum + forcing -fno-pic ---"
          ./scripts/config -d ARM64_ERRATUM_843419 -d ARM64_ERRATUM_845719 2>/dev/null || true
          make ARCH=arm64 olddefconfig >/dev/null 2>&1 || true
          make ARCH=arm64 M="$MD" modules HOSTCFLAGS="-O2 -fcommon" KCFLAGS="-fno-pic -fno-pie" || exit 1
        else
          exit 1
        fi
      fi
      local MAGIC
      MAGIC=$(strings "$MD/kloader_driver.ko" | grep -m1 '^vermagic=')
      echo "VERMAGIC: $MAGIC"
      case "$MAGIC" in
        "vermagic=$VER "*)
          cp "$MD/kloader_driver.ko" "$OUT/uni_$VER.ko"
          cp "$MD/kloader_driver.ko" "$ASSETS/uni_$VER.ko"
          echo "BUILD-OK" ;;
        *) echo "VERMAGIC-MISMATCH (got '$MAGIC', want '$VER ...')"; exit 1 ;;
      esac
    ) >> "$TLOG" 2>&1
    grep -q '^BUILD-OK$' "$TLOG"
  }

  # (arm64 availability for 3.0..3.6 is already handled before the download)
  local built=0
  if [ "$MAJOR" -le 4 ]; then
    [ -x "$GCC49/aarch64-linux-android-gcc" ] && \
      try_toolchain gcc49 "PATHPRE=$GCC49:$PATH" CROSS_COMPILE=aarch64-linux-android- && built=1
    if [ $built -eq 0 ] && [ -x "$PROTON/clang" ]; then
      # clang + gcc49 binutils (old kernels need $(CROSS_COMPILE)ld)
      try_toolchain clang "PATHPRE=$PROTON:$GCC49:$PATH" CROSS_COMPILE=aarch64-linux-android- CLANG_TRIPLE=aarch64-linux-android- CC=clang && built=1
    fi
  else
    [ -x "$PROTON/clang" ] && \
      try_toolchain clang "PATHPRE=$PROTON:$GCC49:$PATH" CROSS_COMPILE=aarch64-linux-android- CC=clang && built=1
    if [ $built -eq 0 ] && [ -x "$GCC49/aarch64-linux-android-gcc" ]; then
      try_toolchain gcc49 "PATHPRE=$GCC49:$PATH" CROSS_COMPILE=aarch64-linux-android- && built=1
    fi
  fi

  rm -rf "$TREE"
  if [ $built -eq 1 ]; then
    log "OK-BUILD   $VER"
    rm -f "$TARBALL"
    return 0
  else
    log "FAIL-BUILD $VER (see logs)"
    return 1
  fi
}

worker(){
  while :; do
    local VER=""
    { flock -x 9
      VER=$(head -n 1 "$QUEUE" 2>/dev/null)
      [ -n "$VER" ] && sed -i 1d "$QUEUE"
    } 9> "$LOCK"
    [ -z "$VER" ] && break
    build_one "$VER"
  done
}

log "PIPELINE: starting 3 workers for $(wc -l < "$VFILE") versions"
for i in 1 2 3; do worker & done
wait
log "PIPELINE: DONE -> ko=$(ls "$OUT" 2>/dev/null | wc -l) built"