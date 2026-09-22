# Kernel Loder - Auto Build Launcher (Windows)
# Usage: .\auto_build.ps1 [-stop|-status]

param([switch]$Stop,[switch]$Status)
$ErrorActionPreference = 'Stop'
$proj   = 'c:\Users\Administrator\Downloads\DaisyDiverLoder'
$driver = "$proj\driver"
$statusFile = "$driver\build_all_status.txt"
$wslQueue   = '/root/kernel-all/queue.txt'
$projWsl    = '/mnt/c/Users/Administrator/Downloads/DaisyDiverLoder'

# ---------- helpers ----------
function Show-Status {
  Write-Host "`n===== Kernel Loder Build Status =====" -ForegroundColor Cyan
  $q = & wsl -d Ubuntu-22.04 -u root -- bash -c "wc -l < $wslQueue 2>/dev/null || echo 0"
  Write-Host "Queue remaining (WSL): $q versions" -ForegroundColor Yellow
  $lock = & wsl -d Ubuntu-22.04 -u root -- bash -c 'flock -n /root/kernel-all/queue.lock echo LOCKED || echo FREE'
  Write-Host "Queue lock: $lock"
  $procs = & wsl -d Ubuntu-22.04 -u root -- bash -c 'pgrep -af "build_all.sh" 2>/dev/null || echo none'
  Write-Host "Build process:`n$procs"
  $wd = & wsl -d Ubuntu-22.04 -u root -- bash -c 'pgrep -af "build_watchdog.sh" 2>/dev/null || echo none'
  Write-Host "Watchdog:`n$wd"
  if (Test-Path $statusFile) {
    $lines = (Get-Content $statusFile | Measure-Object -Line).Lines
    $last = Get-Content $statusFile -Tail 1
    Write-Host "Status file: $lines lines | Last: $last" -ForegroundColor Gray
  }
  $ko = (Get-ChildItem "$proj\app\src\main\assets\drivers" -Filter "*.ko" -ErrorAction SilentlyContinue | Measure-Object).Count
  Write-Host "Assets .ko files: $ko"
  Write-Host "==========================================`n"
}

function Stop-Build {
  Write-Host "Stopping WSL build processes..." -ForegroundColor Yellow
  & wsl -d Ubuntu-22.04 -u root -- bash -c 'pkill -f "build_all.sh" 2>/dev/null; pkill -f "build_watchdog.sh" 2>/dev/null; echo STOPPED'
  Write-Host "Stop command sent." -ForegroundColor Green
}

function Start-Build {
  Write-Host "Launching Kernel Loder build pipeline..." -ForegroundColor Cyan

  # Check WSL
  $check = wsl -d Ubuntu-22.04 -u root -- bash -c 'echo OK' 2>&1
  if ($check -notlike '*OK*') {
    Write-Host "ERROR: WSL Ubuntu-22.04 not accessible." -ForegroundColor Red
    Write-Host "Run: wsl --set-default Ubuntu-22.04" -ForegroundColor Gray
    return
  }

  # Copy watchdog script to WSL
  $watchdogWin = "$driver\watchdog.sh"
  $watchdogWsl = '/root/build_watchdog.sh'
  if (Test-Path $watchdogWin) {
    wsl -d Ubuntu-22.04 -u root -- bash -c "cp $watchdogWin $watchdogWsl; chmod +x $watchdogWsl; echo WATCHDOG_COPIED"
    Write-Host "Watchdog copied to WSL." -ForegroundColor Green
  } else {
    Write-Host "WARNING: watchdog.sh not found at $watchdogWin" -ForegroundColor Yellow
  }

  # Check if build already running
  $existing = & wsl -d Ubuntu-22.04 -u root -- bash -c 'pgrep -af "build_all.sh" 2>/dev/null | wc -l'
  if ($existing -gt 0) {
    Write-Host "Build already running ($existing process(es)). Not launching new." -ForegroundColor Yellow
    Show-Status
    return
  }

  # Re-init queue if empty
  $qCount = & wsl -d Ubuntu-22.04 -u root -- bash -c "wc -l < $wslQueue 2>/dev/null || echo 0"
  if ($qCount -eq 0 -or $qCount -eq '') {
    Write-Host "Queue empty - re-initializing from versions.txt..." -ForegroundColor Yellow
    & wsl -d Ubuntu-22.04 -u root -- bash -c "tr -d '\`r' < $projWsl/driver/versions.txt > $wslQueue; echo QUEUE_REINIT"
  }

  # Launch watchdog (it will launch build_all.sh internally if needed)
  Write-Host "Launching watchdog (auto-recovery) + build pipeline..." -ForegroundColor Green
  $logDir = "$driver\logs"
  if (!(Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir -Force | Out-Null }

  $wdLog = "$logDir\watchdog_out.log"
  $wdErr = "$logDir\watchdog_err.log"

  Start-Process -FilePath 'wsl.exe' `
    -ArgumentList @('-d','Ubuntu-22.04','-u','root','--','bash',$watchdogWsl) `
    -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput $wdLog -RedirectStandardError $wdErr

  Start-Sleep -Seconds 2

  $wdRunning = & wsl -d Ubuntu-22.04 -u root -- bash -c 'pgrep -af "build_watchdog.sh" 2>/dev/null | wc -l'
  if ($wdRunning -gt 0) {
    Write-Host "Watchdog launched successfully." -ForegroundColor Green
    Write-Host "Watchdog will: monitor build, auto-restart on crash, handle internet drops." -ForegroundColor Gray
  } else {
    Write-Host "WARNING: Watchdog may not have launched." -ForegroundColor Yellow
  }

  Start-Sleep -Seconds 1
  Show-Status
}

# ---------- main ----------
if ($Stop)      { Stop-Build }
elseif ($Status){ Show-Status }
else            { Start-Build }
