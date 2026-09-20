param(
    [string]$SourceDir = "C:\Users\Administrator\Downloads\assets\RT",
    [string]$OutDir = "C:\Users\Administrator\Downloads\DaisyDiverLoder\app\src\main\assets\drivers"
)

# Convert RT/QX installer .sh scripts (which carry the kernel module as a
# base64 blob in MODULE_BASE64="...") into raw .ko ELF files the app can
# insmod directly. Output: rt_<version>.ko in the app assets.
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

Get-ChildItem -Path $SourceDir -Filter *.sh | ForEach-Object {
    $name = $_.BaseName                 # e.g. 4.9.186, 4.19.191-note12pro
    $text = [IO.File]::ReadAllText($_.FullName)

    # Longest run of base64 chars / whitespace (the .ko blob)
    $m = [regex]::Matches($text, '[A-Za-z0-9+/=\s]{4096,}') |
         Sort-Object { $_.Value.Length } -Descending | Select-Object -First 1
    if (-not $m) {
        Write-Host "SKIP  $($_.Name): no base64 blob found"
        return
    }
    $b64 = ($m.Value -replace '\s', '')
    $b64 = $b64.Substring(0, $b64.Length - ($b64.Length % 4))
    try {
        $bytes = [Convert]::FromBase64String($b64)
    } catch {
        Write-Host "SKIP  $($_.Name): base64 decode failed ($($_.Exception.Message))"
        return
    }
    if ($bytes.Length -lt 4 -or $bytes[0] -ne 0x7F -or
        $bytes[1] -ne 0x45 -or $bytes[2] -ne 0x4C -or $bytes[3] -ne 0x46) {
        Write-Host "SKIP  $($_.Name): decoded data is not ELF"
        return
    }
    $out = Join-Path $OutDir "rt_$name.ko"
    [IO.File]::WriteAllBytes($out, $bytes)
    Write-Host ("OK    rt_{0}.ko  ({1:N0} bytes)" -f $name, $bytes.Length)
}
Write-Host "Done. Output: $OutDir"