# Relaunch the .ko factory with the expanded queue (4.9.1..4.9.337 included).
$proj = "c:\Users\Administrator\Downloads\DaisyDiverLoder"
$out  = "$proj\driver\relaunch_result.txt"
$r = @()
$st = "$proj\driver\build_all_status.txt"
if (Test-Path $st) {
  $stamp = Get-Date -Format 'MMdd-HHmm'
  $arc = "$proj\driver\logs\status_run_$stamp.txt"
  Copy-Item $st $arc -Force
  $r += ("archived old status -> logs\status_run_{0}.txt" -f $stamp)
}
& wsl --terminate Ubuntu-22.04 2>&1 | Out-Null
Start-Sleep -Seconds 6
$r += "old pipeline terminated"
$src = "$proj\driver\build_all_versions.sh"
$lf  = "$proj\driver\build_all_lf.sh"
$content = [IO.File]::ReadAllText($src).Replace("`r`n","`n")
[IO.File]::WriteAllText($lf, $content, [Text.UTF8Encoding]::new($false))
$vsrc = "$proj\driver\versions.txt"
$vcontent = [IO.File]::ReadAllText($vsrc).Replace("`r`n","`n").TrimEnd("`n") + "`n"
[IO.File]::WriteAllText("$proj\driver\versions_lf.txt", $vcontent, [Text.UTF8Encoding]::new($false))
Copy-Item "$proj\driver\versions_lf.txt" $vsrc -Force
$chk = & wsl -d Ubuntu-22.04 -u root -- bash -c "cp /mnt/c/Users/Administrator/Downloads/DaisyDiverLoder/driver/build_all_lf.sh /root/build_all.sh; chmod +x /root/build_all.sh; bash -n /root/build_all.sh && echo SYNTAX-OK || echo SYNTAX-FAIL" 2>&1
$r += ("syntax check: {0}" -f ("$chk").Trim())
if ("$chk" -notmatch 'SYNTAX-OK') {
  $r += "ABORTED - script has a syntax error, not launching"
  $r | Set-Content -Path $out -Encoding UTF8
  exit 1
}
if (Test-Path $st) { Set-Content -Path $st -Value "" -Encoding UTF8 }
$so = "$proj\driver\wsl_build_out.log"
$se = "$proj\driver\wsl_build_err.log"
foreach ($f in @($so,$se)) { if (Test-Path $f) { Clear-Content $f -ErrorAction SilentlyContinue } }
Start-Process -FilePath "wsl.exe" -ArgumentList @("-d","Ubuntu-22.04","-u","root","--","bash","/root/build_all.sh") -WindowStyle Hidden -RedirectStandardOutput $so -RedirectStandardError $se
$r += "RELAUNCHED-PERSISTENT (421 versions incl. full 4.9.x)"
$r | Set-Content -Path $out -Encoding UTF8
