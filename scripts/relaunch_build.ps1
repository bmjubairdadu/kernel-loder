# Stop the old (broken) .ko pipeline, syntax-check the fixed script, relaunch it.
$proj = "c:\Users\Administrator\Downloads\DaisyDiverLoder"
$out  = "$proj\driver\relaunch_result.txt"
$r = @()

# 1. archive the old status log (keeps the evidence of the false SKIP-NOSRC storm)
$st = "$proj\driver\build_all_status.txt"
if (Test-Path $st) {
  Copy-Item $st "$proj\driver\logs\status_run1.txt" -Force
  $r += ("archived old status -> logs\status_run1.txt  ({0} lines)" -f (Get-Content $st).Count)
}

# 2. stop the old pipeline (VM disk survives, so cached tarballs are kept)
& wsl --terminate Ubuntu-22.04 2>&1 | Out-Null
Start-Sleep -Seconds 6
$r += "old pipeline terminated"

# 3. LF-normalise script + versions list, then syntax-check in WSL
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

# 4. fresh status file so the new run is unambiguous
if (Test-Path $st) { Set-Content -Path $st -Value "" -Encoding UTF8 }

# 5. relaunch as a detached persistent wsl client
$so = "$proj\driver\wsl_build_out.log"
$se = "$proj\driver\wsl_build_err.log"
foreach ($f in @($so,$se)) { if (Test-Path $f) { Clear-Content $f -ErrorAction SilentlyContinue } }
Start-Process -FilePath "wsl.exe" -ArgumentList @("-d","Ubuntu-22.04","-u","root","--","bash","/root/build_all.sh") -WindowStyle Hidden -RedirectStandardOutput $so -RedirectStandardError $se
$r += "RELAUNCHED-PERSISTENT"
$r | Set-Content -Path $out -Encoding UTF8
