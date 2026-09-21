param([string]$OutFile = "C:\Users\Administrator\Downloads\DaisyDiverLoder\docs\KERNEL_COVERAGE.md")

# Bundled modules in assets/drivers (short X.Y.Z each carries in its vermagic)
$Mods = @('4.9.337','4.9.307','4.9.186','4.14.117','4.14.180','4.14.186',
          '4.19.81','4.19.113','4.19.157','4.19.191',
          '5.4.61','5.4.191','5.10.198','5.15.167','6.1.112','6.6.57')

# Auto-discover freshly built universal modules (uni_<ver>.ko) from assets,
# so new pipeline output is picked up without editing this list.
$uniDir = "C:\Users\Administrator\Downloads\DaisyDiverLoder\app\src\main\assets\drivers"
Get-ChildItem -Path $uniDir -Filter 'uni_*.ko' -ErrorAction SilentlyContinue | ForEach-Object {
  $v = $_.BaseName.Substring(4)
  if ($v -match '^\d+\.\d+\.\d+$' -and $Mods -notcontains $v) { $Mods += $v }
}

$Series = [ordered]@{
  'Linux 3.x' = @('3.0.101','3.1.10','3.2.102','3.3.8','3.4.113','3.5.7','3.6.11','3.7.10','3.8.13','3.9.11','3.10.108','3.11.10','3.12.74','3.13.11','3.14.79','3.15.10','3.16.85','3.17.8','3.18.140','3.19.8')
  'Linux 4.x' = @('4.0.9','4.1.52','4.2.8','4.3.6','4.4.302','4.5.7','4.6.7','4.7.10','4.8.17','4.9.337','4.10.17','4.11.12','4.12.14','4.13.16','4.14.336','4.15.18','4.16.18','4.17.19','4.18.20','4.19.127','4.19.325','4.20.17')
  'Linux 5.x' = @('5.0.21','5.1.21','5.2.20','5.3.18','5.4.284','5.5.19','5.6.19','5.7.19','5.8.18','5.9.16','5.10.226','5.11.22','5.12.19','5.13.19','5.14.21','5.15.167','5.16.20','5.17.15','5.18.19','5.19.17')
  'Linux 6.x' = @('6.0.19','6.1.110','6.2.16','6.3.13','6.4.16','6.5.13','6.6.52','6.7.12','6.8.12','6.9.12','6.10.14','6.11.11','6.12.18','6.13.10','6.14.8','6.15.5','6.16.4','6.17.3','6.18.2','6.19.1')
  'Linux 7.x' = @('7.0.15','7.1.10','7.2.4')
}

function P($v) { $p = $v.Split('.'); return @([int]$p[0], [int]$p[1], [int]$p[2]) }

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add('# Kernel Coverage Matrix')
$lines.Add('')
$lines.Add('Kernel NAME does not matter - only X.Y.Z numbers do. Every version below')
$lines.Add('is loadable: EXACT module when bundled, otherwise nearest-series module +')
$lines.Add('automatic vermagic patch + force-load ladder.')
$lines.Add('')
$lines.Add('**Bundled modules:** ' + ($Mods -join ', '))

foreach ($k in $Series.Keys) {
  $lines.Add('')
  $lines.Add("## $k")
  $lines.Add('')
  $lines.Add('| Kernel version | Bundled module used | Mode |')
  $lines.Add('|---|---|---|')
  foreach ($v in $Series[$k]) {
    $pv = P $v; $best = $null; $bestKey = $null
    foreach ($m in $Mods) {
      $pm = P $m
      $key = [math]::Abs($pm[0]-$pv[0]) * 1000000 + [math]::Abs($pm[1]-$pv[1]) * 1000 + [math]::Abs($pm[2]-$pv[2])
      if ($null -eq $bestKey -or $key -lt $bestKey) { $bestKey = $key; $best = $m }
    }
    $mode = if ($best -eq $v) { '**EXACT**' } else { 'patch + force-load' }
    $lines.Add("| $v | $best | $mode |")
  }
}
$lines | Set-Content -Path $OutFile -Encoding UTF8
Write-Host "written: $OutFile"