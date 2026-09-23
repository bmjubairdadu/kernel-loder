@echo off
REM ============================================================
REM Kernel Loder Watch - background launcher (runs hidden via VBS).
REM Fetches the GitHub token from the Windows Credential Manager
REM (same Git Bash login) and passes it into WSL - no manual login.
REM ============================================================
setlocal
set IN=%TEMP%\kl_cred_in.txt
echo protocol=https> "%IN%"
echo host=github.com>> "%IN%"
set GH_TOKEN=
for /f "tokens=1* delims==" %%a in ('git credential fill ^< "%IN%" 2^>nul') do if "%%a"=="password" set GH_TOKEN=%%b
del "%IN%" 2>nul
if "%GH_TOKEN%"=="" (
  echo [%date% %time%] no GitHub token - open Git Bash once and push anything 1>&2
  exit /b 1
)
set WSLENV=GH_TOKEN
wsl bash /mnt/d/DiverLoder/driver/failure_watch.sh watch-autofix 300
