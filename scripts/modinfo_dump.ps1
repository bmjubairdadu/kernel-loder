# Dump the metadata actually embedded inside a built .ko (like modinfo)
$proj = "c:\Users\Administrator\Downloads\DaisyDiverLoder"
$out  = "$proj\driver\modinfo.txt"
$w = @()

$files = Get-ChildItem "$proj\app\src\main\assets\drivers" -Filter 'uni_*.ko' -File |
         Sort-Object Name

function Get-KoStrings([string]$path) {
  $b = [IO.File]::ReadAllBytes($path)
  $sb = New-Object Text.StringBuilder
  foreach ($x in $b) {
    if ($x -ge 32 -and $x -lt 127) { [void]$sb.Append([char]$x) } else { [void]$sb.Append("`n") }
  }
  return $sb.ToString()
}

# first: full metadata for one representative module
$one = "$proj\app\src\main\assets\drivers\uni_3.18.140.ko"
if (Test-Path $one) {
  $w += "=========== CONTENT OF uni_3.18.140.ko ==========="
  $s = Get-KoStrings $one
  foreach ($k in @('vermagic=','name=','license=','description=','version=','author=','depends=','srcversion=','alias=','parm=')) {
    $m = [regex]::Matches($s, [regex]::Escape($k) + '[ -~]*')
    if ($m.Count -gt 0) {
      $seen = @{}
      foreach ($x in $m) {
        $v = $x.Value.Trim()
        if (-not $seen.ContainsKey($v)) { $seen[$v] = $true; $w += ("  {0}" -f $v) }
      }
    }
  }
  $w += "  --- 'Kernel Loder' strings embedded ---"
  $seen = @{}
  foreach ($x in [regex]::Matches($s, 'Kernel Loder[ -~]*')) {
    $v = $x.Value.Trim()
    if (-not $seen.ContainsKey($v)) { $seen[$v] = $true; $w += ("  {0}" -f $v) }
  }
  $w += "  --- device node name ---"
  foreach ($x in [regex]::Matches($s, 'kloaderctl')) { $w += "  found: kloaderctl"; break }
} else { $w += "uni_3.18.140.ko not found" }

# then: vermagic of every built module (proves each is stamped for its own kernel)
$w += ""
$w += "=========== VERMAGIC OF EVERY BUILT MODULE ==========="
foreach ($f in $files) {
  $s = Get-KoStrings $f.FullName
  $m = [regex]::Match($s, 'vermagic=[ -~]*')
  $desc = [regex]::Match($s, 'Kernel Loder universal driver - [0-9.]+')
  $w += ("  {0,-22} {1}" -f $f.Name, $m.Value.Trim())
  if ($desc.Success) { $w += ("  {0,-22}   desc: {1}" -f "", $desc.Value) }
}
$w += ""
$w += ("total built modules: {0}" -f $files.Count)
$w | Set-Content -Path $out -Encoding UTF8
