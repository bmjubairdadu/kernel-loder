# Coverage progress: how many of the 84 kernels are done / skipped / still pending
$proj = "c:\Users\Administrator\Downloads\DaisyDiverLoder"
$out  = "$proj\driver\coverage_status.txt"
$w = @()

$vers = Get-Content "$proj\driver\versions.txt" -ErrorAction SilentlyContinue |
        ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' }

$status = Get-Content "$proj\driver\build_all_status.txt" -ErrorAction SilentlyContinue

# last known event per version
$ev = @{}
foreach ($l in $status) {
  if     ($l -match 'SKIP-NOARM64\s+(\S+)') { $ev[$Matches[1]] = 'NOARM64' }
  elseif ($l -match 'SKIP-NOSRC\s+(\S+)')   { $ev[$Matches[1]] = 'NOSRC' }
  elseif ($l -match 'SKIP-DONE\s+(\S+)')    { $ev[$Matches[1]] = 'BUILT' }
  elseif ($l -match 'OK-BUILD\s+(\S+)')     { $ev[$Matches[1]] = 'BUILT' }
  elseif ($l -match 'FAIL-BUILD\s+(\S+)')   { $ev[$Matches[1]] = 'FAILBUILD' }
  elseif ($l -match 'FAIL-DL\s+(\S+)')      { $ev[$Matches[1]] = 'FAILDL' }
  elseif ($l -match 'RETRY-DL\s+(\S+)')     { $ev[$Matches[1]] = 'DOWNLOADING' }
  elseif ($l -match 'DOWNLOAD\s+(\S+)')     { if (-not $ev.ContainsKey($Matches[1])) { $ev[$Matches[1]] = 'DOWNLOADING' } }
  elseif ($l -match 'DISCARD\s+(\S+)')      { $ev[$Matches[1]] = 'DOWNLOADING' }
}

$assets = "$proj\app\src\main\assets\drivers"
$built = @(); $noarm = @(); $nosrc = @(); $dl = @(); $notstarted = @(); $fail = @()
foreach ($v in $vers) {
  if (Test-Path "$assets\uni_$v.ko") { $built += $v; continue }
  if (-not $ev.ContainsKey($v))     { $notstarted += $v; continue }
  switch ($ev[$v]) {
    'BUILT'       { $built += $v }
    'NOARM64'     { $noarm += $v }
    'NOSRC'       { $nosrc += $v }
    'DOWNLOADING' { $dl += $v }
    'FAILBUILD'   { $fail += $v }
    'FAILDL'      { $fail += $v }
    default       { $notstarted += $v }
  }
}

$w += "==================== COVERAGE PROGRESS ===================="
$w += ("total kernels in plan      : {0}" -f $vers.Count)
$w += ("BUILT (uni_*.ko shipped)   : {0}" -f $built.Count)
$w += ("still downloading          : {0}" -f $dl.Count)
$w += ("not started yet            : {0}" -f $notstarted.Count)
$w += ("skipped - no arm64 (3.0-3.6): {0}" -f $noarm.Count)
$w += ("skipped - no source on cdn : {0}" -f $nosrc.Count)
$w += ("failed (will retry)        : {0}" -f $fail.Count)
$w += ""
$w += ("REMAINING TO ATTEMPT       : {0}" -f ($dl.Count + $notstarted.Count + $fail.Count))
$w += ""

$w += "--- BUILT ---"
if ($built.Count -eq 0) { $w += "  (none yet)" } else { $w += ("  " + (($built | Sort-Object) -join ' ')) }
$w += "--- DOWNLOADING NOW ---"
if ($dl.Count -eq 0) { $w += "  (none)" } else { $w += ("  " + (($dl | Sort-Object) -join ' ')) }
$w += "--- NOT STARTED ---"
if ($notstarted.Count -eq 0) { $w += "  (none)" } else { $w += ("  " + (($notstarted | Sort-Object) -join ' ')) }
$w += "--- SKIPPED: NO ARM64 (expected, mainline arm64 starts at 3.7) ---"
if ($noarm.Count -eq 0) { $w += "  (none)" } else { $w += ("  " + (($noarm | Sort-Object) -join ' ')) }
$w += "--- SKIPPED: NO SOURCE ---"
if ($nosrc.Count -eq 0) { $w += "  (none)" } else { $w += ("  " + (($nosrc | Sort-Object) -join ' ')) }
$w += "--- FAILED ---"
if ($fail.Count -eq 0) { $w += "  (none)" } else { $w += ("  " + (($fail | Sort-Object) -join ' ')) }

$w += ""
$w += "--- all uni_*.ko actually on disk ---"
$f = Get-ChildItem $assets -Filter 'uni_*.ko' -File -ErrorAction SilentlyContinue | Sort-Object Name
$w += ("count: {0}" -f $f.Count)
foreach ($x in $f) { $w += ("  {0,7:N0} KB  {1}" -f ($x.Length/1KB), $x.Name) }

$w += ""
$w += ("status log lines: {0}" -f $status.Count)
$status | Select-Object -Last 8 | ForEach-Object { $w += ("  {0}" -f $_) }
$w += ""
$w += ("C drive FREE: {0:N1} GB" -f ((Get-PSDrive C).Free/1GB))
$w | Set-Content -Path $out -Encoding UTF8
