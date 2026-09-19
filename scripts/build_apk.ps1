# Build the Kernel Loder debug APK (Windows / PowerShell)
# Usage:  powershell -ExecutionPolicy Bypass -File scripts/build_apk.ps1
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

# Prefer Android Studio's bundled JDK when JAVA_HOME is not set
if (-not $env:JAVA_HOME) {
    $candidates = @(
        "$env:ProgramFiles\Android\Android Studio\jbr",
        "$env:LOCALAPPDATA\Programs\Android Studio\jbr"
    )
    foreach ($c in $candidates) {
        if (Test-Path $c) { $env:JAVA_HOME = $c; break }
    }
}
if (-not $env:JAVA_HOME) {
    Write-Error "JAVA_HOME is not set and no Android Studio JBR was found. Install JDK 17+."
}
Write-Host "JAVA_HOME = $env:JAVA_HOME"

& "$root\gradlew.bat" :app:assembleDebug --console=plain
$code = $LASTEXITCODE

$apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
if ($code -eq 0 -and (Test-Path $apk)) {
    Write-Host ""
    Write-Host "BUILD SUCCESSFUL" -ForegroundColor Green
    Get-Item $apk | Select-Object FullName, Length, LastWriteTime | Format-List
} else {
    Write-Host "BUILD FAILED (exit $code)" -ForegroundColor Red
    exit $code
}