# Launch universal .ko build as a PERSISTENT wsl client (survives: wsl.exe stays alive as foreground client)
$proj = "c:\Users\Administrator\Downloads\DaisyDiverLoder"
$out = "$proj\driver\launch_result.txt"
# LF, no-BOM copies on /mnt/c (script + versions list)
$src = "$proj\driver\build_all_versions.sh"
$lf = "$proj\driver\build_all_lf.sh"
$content = [IO.File]::ReadAllText($src).Replace("`r`n","`n")
[IO.File]::WriteAllText($lf, $content, [Text.UTF8Encoding]::new($false))
$vsrc = "$proj\driver\versions.txt"
$vlf = "$proj\driver\versions_lf.txt"
$vcontent = [IO.File]::ReadAllText($vsrc).Replace("`r`n","`n").TrimEnd("`n") + "`n"
[IO.File]::WriteAllText($vlf, $vcontent, [Text.UTF8Encoding]::new($false))
Copy-Item $vlf $vsrc -Force
# copy script into WSL /root
wsl -d Ubuntu-22.04 -u root -- bash -c "cp /mnt/c/Users/Administrator/Downloads/DaisyDiverLoder/driver/build_all_lf.sh /root/build_all.sh; chmod +x /root/build_all.sh"
# launch detached persistent wsl client (hidden window)
$so = "$proj\driver\wsl_build_out.log"
$se = "$proj\driver\wsl_build_err.log"
Start-Process -FilePath "wsl.exe" -ArgumentList @("-d","Ubuntu-22.04","-u","root","--","bash","/root/build_all.sh") -WindowStyle Hidden -RedirectStandardOutput $so -RedirectStandardError $se
"LAUNCHED-PERSISTENT" | Set-Content -Path $out -Encoding UTF8