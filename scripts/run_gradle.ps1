# Launch gradle build detached (survives foreground terminal churn), log to files
$proj = "c:\Users\Administrator\Downloads\DaisyDiverLoder"
Set-Location $proj
# fresh logs
Remove-Item "$proj\gradle_out.log","$proj\gradle_err.log" -ErrorAction SilentlyContinue
"RUNNING" | Set-Content "$proj\driver\launch_result.txt" -Encoding UTF8
Start-Process -FilePath "$proj\gradlew.bat" -ArgumentList @(":app:assembleDebug","--console=plain","--stacktrace") -WorkingDirectory $proj -WindowStyle Hidden -RedirectStandardOutput "$proj\gradle_out.log" -RedirectStandardError "$proj\gradle_err.log"
