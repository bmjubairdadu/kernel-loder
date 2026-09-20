# Progress of the native (DaisyForGaming) rebuild
$proj = "c:\Users\Administrator\Downloads\DaisyDiverLoder"
$out  = "$proj\driver\native_status.txt"
$w = @()
$run = "/mnt/c/Users/Administrator/Downloads/DaisyDiverLoder/driver"
$lf = "`n"

$sh = "$proj\driver\_nstatus.sh"
$lines = @(
 'echo "=== BUILD PROCESS RUNNING? ==="',
 'ps -eo pid,etime,args | grep -E "build_native|make .*-C|clang" | grep -v grep | head -8',
 'echo "=== /tmp/native.log (tail 45) ==="',
 'tail -45 /tmp/native.log 2>/dev/null || echo "  (no log yet)"',
 'echo "=== OUTPUTS ==="',
 'ls -la $HOME/ko-build/kloader_driver.ko $HOME/ko-build-307/kloader_driver.ko 2>/dev/null || true',
 'ls -la /mnt/c/Users/Administrator/Downloads/DaisyDiverLoder/driver/out/native_*.ko 2>/dev/null || echo "  driver/out: none"',
 'ls -la /mnt/c/Users/Administrator/Downloads/DaisyDiverLoder/app/src/main/assets/drivers/native_*.ko 2>/dev/null || echo "  assets: none (deleted, waiting for rebuild)"'
)
[IO.File]::WriteAllText($sh, ($lines -join $lf) + $lf, (New-Object Text.UTF8Encoding($false)))
$res = & wsl -d Ubuntu-22.04 -- bash "$run/_nstatus.sh" 2>&1
Remove-Item $sh -Force -ErrorAction SilentlyContinue

$w += $res
$w += ""
$w += "=== launcher stdout/stderr on the Windows side ==="
foreach ($f in @("$proj\driver\native_out.log","$proj\driver\native_err.log")) {
  if (Test-Path $f) { $w += ("--- {0} ---" -f (Split-Path $f -Leaf)); $w += (Get-Content $f -ErrorAction SilentlyContinue) }
}
$w += ("C drive FREE: {0:N1} GB" -f ((Get-PSDrive C).Free/1GB))
$w | Set-Content -Path $out -Encoding UTF8
