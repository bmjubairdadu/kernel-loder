while (1) {
  Clear-Host
  Write-Host '=== KERNEL LODER .ko FACTORY - LIVE (10s refresh, Ctrl+C to stop) ===' -ForegroundColor Cyan
  Write-Host (Get-Date -Format 'HH:mm:ss')
  Write-Host ''
  Write-Host '--- build_all_status.txt (last 15) ---' -ForegroundColor Yellow
  Get-Content 'c:\Users\Administrator\Downloads\DaisyDiverLoder\driver\build_all_status.txt' -Tail 15
  Write-Host ''
  Write-Host '--- WSL workers ---' -ForegroundColor Yellow
  wsl -d Ubuntu-22.04 -u root -- bash -c 'ps -eo pid,etime,args | head -25'
  Write-Host ''
  Write-Host '--- uni_*.ko built ---' -ForegroundColor Yellow
  (Get-ChildItem 'c:\Users\Administrator\Downloads\DaisyDiverLoder\app\src\main\assets\drivers' -Filter 'uni_*.ko' | Measure-Object).Count
  Start-Sleep -Seconds 10
}
