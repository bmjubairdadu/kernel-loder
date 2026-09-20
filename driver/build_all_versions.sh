#!/bin/bash
# ============================================================================
# Kernel Loder - UNIVERSAL .ko FACTORY (WSL)
# Builds kloader_driver.ko for EVERY kernel.org release in versions.txt.
#  - skips versions we already ship (4.9.337 / 4.9.307)
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
log "SETUP: installing build deps..."
export DEBIAN_FRONTEND=noninteractive
(apt-get update -y && apt-get install -y curl xz-utils bc bison flex libssl-dev libelf-dev git) >> $LOGDIR/deps.log 2>&1 \
  || log "SETUP: apt FAILED (continuing, deps may already exist)"

# ---------- toolchains ----------
if [ ! -x "$TOOL/gcc49/bin/aarch64-linux-android-gcc" ]; then
  log "TOOLCHAIN: cloning LineageOS gcc 4.9 (aarch64)..."
  git clone --depth=1 https://github.com/LineageOS/android_prebuilts_gcc_linux-x86_aarch64_aarch64-linux-android-4.9 "$TOOL/gcc49" >> $LOGDIR/toolchain.log 2>&1 \
    || log "TOOLCHAIN: gcc49 clone FAILED"
fi
if [ ! -x "$TOOL/proton/bin/clang" ]; then
  log "TOOLCHAIN: proton unavailable - installing apt clang..."
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

  local URL="https://cdn.kernel.org/pub/linux/kernel/v${MAJOR}.x/linux-$VER.tar.xz"
  if [ ! -f "$TARBALL" ]; then
    log "DOWNLOAD   $VER ..."
    curl -sfL --retry 2 --connect-timeout 20 -o "$TARBALL.part" "$URL" \
      || { log "SKIP-NOSRC $VER (no source tarball on kernel.org)"; rm -f "$TARBALL.part"; return 2; }
    mv "$TARBALL.part" "$TARBALL"
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
      make ARCH=arm64 -j"$(nproc)" modules_prepare   || exit 1
      echo "--- module build ---"
      make ARCH=arm64 M="$MD" modules                || exit 1
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

  # arm64 exists only from kernel 3.7+ - 3.0..3.6 simply cannot build arm64 modules
  tar -tf "$TARBALL" >/dev/null 2>&1   # sanity: tarball readable
  if [ "$MAJOR" -eq 3 ]; then
    local MINOR3=${VER#3.}; MINOR3=${MINOR3%%.*}
    if [ "$MINOR3" -lt 7 ]; then
      log "SKIP-NOARM64 $VER (mainline arm64 starts at 3.7)"
      rm -rf "$TREE"
      return 2
    fi
  fi

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