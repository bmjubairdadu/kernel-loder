# Read pipeline state (writes to file)
$proj = "c:\Users\Administrator\Downloads\DaisyDiverLoder"
$out = "$proj\driver\check_result.txt"
$w = @()
$w += "=== WSL PROCS ==="
$w += wsl -d Ubuntu-22.04 -u root -- sh -c "ps aux | grep -E 'build_all|make|curl' | grep -v grep | head -15" 2>&1
$w += "=== STATUS FILE INFO ==="
$w += Get-Item "$proj\driver\build_all_status.txt" -ErrorAction SilentlyContinue | Select-Object Length, LastWriteTime | Format-List | Out-String
$w += "=== LAUNCH RESULT ==="
$w += Get-Content "$proj\driver\launch_result.txt" -ErrorAction SilentlyContinue
$w += "=== WSL ERR LOG ==="
$w += Get-Content "$proj\driver\wsl_build_err.log" -ErrorAction SilentlyContinue
$w += "=== STATUS TAIL ==="
$w += wsl -d Ubuntu-22.04 -u root -- tail -6 /mnt/c/Users/Administrator/Downloads/DaisyDiverLoder/driver/build_all_status.txt 2>&1
$w | Set-Content -Path $out -Encoding UTF8