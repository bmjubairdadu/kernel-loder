# Rebuild native_4.9.337.ko + native_4.9.307.ko from the real DaisyForGaming kernel tree.
# Pre-flight first; only launch if the kernel tree + toolchain are actually present.
$proj = "c:\Users\Administrator\Downloads\DaisyDiverLoder"
$out  = "$proj\driver\rebuild_native.txt"
$w = @()

$run = "/mnt/c/Users/Administrator/Downloads/DaisyDiverLoder/driver"
$lf = "`n"

# ---------- pre-flight as the default (non-root) WSL user ----------
$pf = "$proj\driver\_preflight.sh"
$lines = @(
 'echo "USER=$(whoami)"',
 'echo "HOME=$HOME"',
 'echo "=== daisy-build contents ==="',
 'ls -la $HOME/daisy-build/ 2>/dev/null || echo "  MISSING: $HOME/daisy-build"',
 'echo "=== kernel_source ==="',
 'test -d $HOME/daisy-build/kernel_source && echo "  kernel_source dir: OK" || echo "  kernel_source dir: MISSING"',
 'printf "  kernel.release = "; cat $HOME/daisy-build/kernel_source/out/include/config/kernel.release 2>/dev/null || echo "MISSING"',
 'echo "=== proton-clang toolchain ==="',
 'test -x $HOME/daisy-build/toolchain/proton-clang/bin/clang && echo "  proton-clang clang: OK" || echo "  proton-clang clang: MISSING"',
 'echo "=== kernel_307 tree ==="',
 'if [ -f $HOME/daisy-build/kernel_307/out/include/config/kernel.release ]; then printf "  prepared, release = "; cat $HOME/daisy-build/kernel_307/out/include/config/kernel.release; else echo "  not prepared yet (script creates it)"; fi',
 'echo "=== daisy_defconfig available? ==="',
 'ls $HOME/daisy-build/kernel_source/arch/arm64/configs/daisy_defconfig 2>/dev/null || echo "  no daisy_defconfig"'
)
[IO.File]::WriteAllText($pf, ($lines -join $lf) + $lf, (New-Object Text.UTF8Encoding($false)))
$resUser = & wsl -d Ubuntu-22.04 -- bash "$run/_preflight.sh" 2>&1
Remove-Item $pf -Force -ErrorAction SilentlyContinue

$w += "===== PRE-FLIGHT (default WSL user) ====="
$w += $resUser

# ---------- same check from root, in case the tree lives in /root ----------
$pf2 = "$proj\driver\_preflight_root.sh"
$lines2 = @(
 'echo "=== /root/daisy-build ==="',
 'ls -la /root/daisy-build/ 2>/dev/null || echo "  MISSING: /root/daisy-build"',
 'printf "  kernel.release = "; cat /root/daisy-build/kernel_source/out/include/config/kernel.release 2>/dev/null || echo "MISSING"',
 'test -x /root/daisy-build/toolchain/proton-clang/bin/clang && echo "  proton-clang clang: OK" || echo "  proton-clang clang: MISSING"'
)
[IO.File]::WriteAllText($pf2, ($lines2 -join $lf) + $lf, (New-Object Text.UTF8Encoding($false)))
$resRoot = & wsl -d Ubuntu-22.04 -u root -- bash "$run/_preflight_root.sh" 2>&1
Remove-Item $pf2 -Force -ErrorAction SilentlyContinue

$w += ""
$w += "===== PRE-FLIGHT (root) ====="
$w += $resRoot

# ---------- decide ----------
$u = ($resUser -join "`n")
$ok = ($u -match 'kernel_source dir: OK') -and ($u -match 'kernel\.release = 4\.9\.337') -and ($u -match 'proton-clang clang: OK')
$w += ""
if ($ok) {
  $w += "PREFLIGHT: OK -> launching native rebuild"
} else {
  $w += "PREFLIGHT: NOT OK -> not launching. See the two reports above."
}

$w | Set-Content -Path $out -Encoding UTF8

if ($ok) {
  Clear-Content "$proj\driver\native_out.log" -ErrorAction SilentlyContinue
  Clear-Content "$proj\driver\native_err.log" -ErrorAction SilentlyContinue
  Start-Process -FilePath "wsl.exe" `
    -ArgumentList @("-d","Ubuntu-22.04","--","bash","$run/wsl_launch_native.sh") `
    -WindowStyle Hidden `
    -RedirectStandardOutput "$proj\driver\native_out.log" `
    -RedirectStandardError  "$proj\driver\native_err.log"
}
