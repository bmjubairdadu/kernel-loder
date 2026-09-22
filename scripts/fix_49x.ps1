# Fix the broken KernelCoverage.kt insertion (stray lines) + rebuild + regen doc.
$proj = "c:\Users\Administrator\Downloads\DaisyDiverLoder"
$k = "$proj\app\src\main\java\com\kernelloader\driver\KernelCoverage.kt"
$t = [IO.File]::ReadAllText($k)
$bad = "        ),`r`n        `"Linux 4.9.x (full series)`" to (1..337).map { `"4.9.`$it`" },`r`n            `"4.20.17`"`r`n        ),"
$good = "        ),`r`n        `"Linux 4.9.x (full series)`" to (1..337).map { `"4.9.`$it`" },"
if ($t.Contains($bad)) {
  $t = $t.Replace($bad, $good)
  $t = $t.Replace("`"4.15.18`", `"4.16.18`", `"4.17.19`", `"4.18.20`", `"4.19.127`", `"4.19.325`",", "`"4.15.18`", `"4.16.18`", `"4.17.19`", `"4.18.20`", `"4.19.127`", `"4.19.325`", `"4.20.17`",")
  [IO.File]::WriteAllText($k, $t)
  "FIXED KernelCoverage.kt" | Set-Content -Path "$proj\driver\fix49x_result.txt" -Encoding UTF8
} else {
  "PATTERN NOT FOUND" | Set-Content -Path "$proj\driver\fix49x_result.txt" -Encoding UTF8
}
