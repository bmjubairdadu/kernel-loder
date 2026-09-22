# Expand 4.9.x to the full 4.9.1..4.9.337 series (user wants every patch release).
$proj = "c:\Users\Administrator\Downloads\DaisyDiverLoder"
$r = @()
# 1. versions.txt (no BOM - the WSL queue reader does tr -d '\r' only)
$v = "$proj\driver\versions.txt"
$lines = Get-Content $v
$out = New-Object System.Collections.Generic.List[string]
foreach ($l in $lines) {
  if ($l.Trim() -eq "4.9.337") { foreach ($i in 1..337) { $out.Add("4.9.$i") } }
  else { $out.Add($l) }
}
[IO.File]::WriteAllLines($v, $out, (New-Object Text.UTF8Encoding($false)))
$r += ("versions.txt lines: {0}" -f $out.Count)
# 2. KernelCoverage.kt (keep BOM state)
$k = "$proj\app\src\main\java\com\kernelloader\driver\KernelCoverage.kt"
$kb = [IO.File]::ReadAllBytes($k)
$hasBom = ($kb.Length -gt 3 -and $kb[0] -eq 0xEF -and $kb[1] -eq 0xBB -and $kb[2] -eq 0xBF)
$t = [Text.Encoding]::UTF8.GetString($kb)
$anchor = '"4.15.18", "4.16.18", "4.17.19", "4.18.20", "4.19.127", "4.19.325",'
if ($t.Contains($anchor)) {
  $add = $anchor + "`r`n        ),`r`n        `"Linux 4.9.x (full series)`" to (1..337).map { `"4.9.`$it`" },"
  $t = $t.Replace($anchor, $add)
  [IO.File]::WriteAllText($k, $t, (New-Object Text.UTF8Encoding($hasBom)))
  $r += "KernelCoverage.kt: 4.9.x series added"
} else { $r += "KernelCoverage.kt: ANCHOR NOT FOUND" }
# 3. gen_coverage_doc.ps1
$g = "$proj\scripts\gen_coverage_doc.ps1"
$g49 = ((1..337 | ForEach-Object { "'4.9.$_'" }) -join ",")
$gt = [IO.File]::ReadAllText($g)
$ganchor = "  'Linux 5.x' = @("
if ($gt.Contains($ganchor)) {
  $gt = $gt.Replace($ganchor, "  'Linux 4.9.x' = @($g49)`r`n$ganchor")
  [IO.File]::WriteAllText($g, $gt)
  $r += "gen_coverage_doc.ps1: 4.9.x series added"
} else { $r += "gen_coverage_doc.ps1: ANCHOR NOT FOUND" }
$r | Set-Content -Path "$proj\driver\gen49x_result.txt" -Encoding UTF8
