# Live WSL monitor: opens a NEW visible PowerShell window that tails the
# .ko factory pipeline every 10 seconds (status + WSL workers + downloads).
$proj = "c:\Users\Administrator\Downloads\DaisyDiverLoder"
$mon = "$proj\scripts\live_loop.ps1"
$loop = @(
  'while (1) {',
  '  Clear-Host',
  "  Write-Host '=== KERNEL LODER .ko FACTORY - LIVE (10s refresh, Ctrl+C to stop) ===' -ForegroundColor Cyan",
  '  Write-Host (Get-Date -Format ''HH:mm:ss'')',
  "  Write-Host ''",
  "  Write-Host '--- build_all_status.txt (last 15) ---' -ForegroundColor Yellow",
  "  Get-Content 'c:\Users\Administrator\Downloads\DaisyDiverLoder\driver\build_all_status.txt' -Tail 15",
  "  Write-Host ''",
  "  Write-Host '--- WSL workers ---' -ForegroundColor Yellow",
  "  wsl -d Ubuntu-22.04 -u root -- bash -c 'ps -eo pid,etime,args | head -25'",
  "  Write-Host ''",
  "  Write-Host '--- uni_*.ko built ---' -ForegroundColor Yellow",
  "  (Get-ChildItem 'c:\Users\Administrator\Downloads\DaisyDiverLoder\app\src\main\assets\drivers' -Filter 'uni_*.ko' | Measure-Object).Count",
  '  Start-Sleep -Seconds 10',
  '}'
)
Set-Content -Path $mon -Value $loop -Encoding UTF8
Start-Process powershell -ArgumentList '-NoExit','-ExecutionPolicy','Bypass','-File', $mon
"OPENED live monitor window"
