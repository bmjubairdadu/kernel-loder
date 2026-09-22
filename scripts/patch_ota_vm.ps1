# Surgical patch for DriverViewModel.autoLoadUniversal (OTA-first).
# The Kotlin editor chokes on this file's CRLF, so patch via script.
$proj = "c:\Users\Administrator\Downloads\DaisyDiverLoder"
$p = "$proj\app\src\main\java\com\kernelloader\driver\DriverViewModel.kt"
$t = [IO.File]::ReadAllText($p)
$old1 = "    fun autoLoadUniversal(context: Context) {"
$new1 = "    fun autoLoadUniversal(context: Context, preferOta: Boolean = true) {"
if (-not $t.Contains($old1)) { "PATCH-FAIL: anchor1" | Set-Content "$proj\driver\ota_patch.txt"; exit 1 }
$t = $t.Replace($old1, $new1)
$old2 = "        viewModelScope.launch(Dispatchers.IO) {" + "`n" + "            try {" + "`n" + "                withContext(Dispatchers.Main) {" + "`n" + "                    UniversalKernelLoader.autoLoad(context, this@DriverViewModel)" + "`n" + "                }"
$new2 = "        viewModelScope.launch(Dispatchers.IO) {" + "`n" + "            try {" + "`n" + "                if (preferOta) {" + "`n" + "                    val handled = runOtaLoad(context)" + "`n" + "                    if (handled) return@launch" + "`n" + "                }" + "`n" + "                withContext(Dispatchers.Main) {" + "`n" + "                    UniversalKernelLoader.autoLoad(context, this@DriverViewModel)" + "`n" + "                }"
if (-not $t.Contains($old2)) { "PATCH-FAIL: anchor2" | Set-Content "$proj\driver\ota_patch.txt"; exit 1 }
$t = $t.Replace($old2, $new2)
[IO.File]::WriteAllText($p, $t)
"PATCH-OK" | Set-Content "$proj\driver\ota_patch.txt"
