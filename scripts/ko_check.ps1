# Verify .ko output: how many real modules exist now, and what the archived run produced
$proj = "c:\Users\Administrator\Downloads\DaisyDiverLoder"
$out  = "$proj\driver\ko_check.txt"
$w = @()

foreach ($d in @("$proj\driver\out_all", "$proj\app\src\main\assets\drivers")) {
  $f = Get-ChildItem $d -Filter 'uni_*.ko' -File -ErrorAction SilentlyContinue | Sort-Object Name
  $w += ("=== {0} ===" -f $d.Replace($proj,'.'))
  $w += ("uni_*.ko count: {0}" -f $f.Count)
  foreach ($x in $f) { $w += ("  {0,9:N0} KB  {1}" -f ($x.Length/1KB), $x.Name) }
  $all = Get-ChildItem $d -Filter '*.ko' -File -ErrorAction SilentlyContinue
  $w += ("all *.ko count: {0}" -f $all.Count)
}

$w += "=== OK-BUILD in archived run (logs\status_run1.txt) ==="
$old = Get-Content "$proj\driver\logs\status_run1.txt" -ErrorAction SilentlyContinue
$oks = $old | Select-String 'OK-BUILD'
$w += ("OK-BUILD lines: {0}" -f $oks.Count)
foreach ($o in $oks) { $w += ("  {0}" -f $o.Line.Trim()) }

$w += "=== current run status ==="
$now = Get-Content "$proj\driver\build_all_status.txt" -ErrorAction SilentlyContinue
$w += ("lines: {0}" -f $now.Count)
$now | Select-Object -Last 12 | ForEach-Object { $w += ("  {0}" -f $_) }
$w += ("OK-BUILD now : {0}" -f (($now | Select-String 'OK-BUILD').Count))
$w += ("GOT (dl done): {0}" -f (($now | Select-String 'GOT ').Count))
$w += ("SKIP-NOARM64 : {0}" -f (($now | Select-String 'SKIP-NOARM64').Count))
$w += ("SKIP-NOSRC   : {0}" -f (($now | Select-String 'SKIP-NOSRC').Count))
$w += ("RETRY-DL     : {0}" -f (($now | Select-String 'RETRY-DL').Count))

$w += ("C drive FREE: {0:N1} GB" -f ((Get-PSDrive C).Free/1GB))
$w | Set-Content -Path $out -Encoding UTF8
