@echo off
REM Kernel Loder - Auto Build Startup
REM This runs on Windows boot to ensure WSL build is always running.
REM Checks if build already active; if not, launches it silently.

setlocal
set PROJ=c:\Users\Administrator\Downloads\DaisyDiverLoder
set SCRIPT=%PROJ%\driver\auto_build.ps1

echo [%time%] Kernel Loder auto-start: checking build status...

REM Check if build is already running via WSL
for /f "tokens=*" %%a in ('wsl -d Ubuntu-22.04 -u root -- bash -c "pgrep -af 'build_all.sh|build_watchdog.sh' 2>/dev/null | wc -l" 2^>nul') do set PROC_COUNT=%%a

if "%PROC_COUNT%"=="0" (
    echo [%time%] No build running - launching auto_build...
    powershell -ExecutionPolicy Bypass -File "%SCRIPT%"
) else (
    echo [%time%] Build already running (%PROC_COUNT% process(es)). Skipping.
)

REM Log to file
echo [%time%] Auto-start check complete. Build running: %PROC_COUNT% >> "%PROJ%\driver\logs\startup.log" 2>nul
endlocal
