@echo off
REM ============================================================
REM Kernel Loder - one-time auto-start installer.
REM Double-click this file ONCE. From then on, every Windows boot
REM auto-starts the failure-watch loop hidden in the background.
REM Log: D:\DiverLoder\driver\watch.log
REM Remove: delete KernelLoderWatch.vbs from the Startup folder.
REM First: run  wsl gh auth login   (once, so the watcher can triage)
REM ============================================================
set "VBS=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\KernelLoderWatch.vbs"
(
echo Set sh = CreateObject^("Wscript.Shell"^)
echo sh.Run "wsl bash /mnt/d/DiverLoder/driver/failure_watch.sh watch-autofix 300", 0
) > "%VBS%"
echo Installed: %VBS%
echo Starting the watcher now (hidden)...
wscript "%VBS%"
echo Done. Check D:\DiverLoder\driver\watch.log in a minute.
pause
