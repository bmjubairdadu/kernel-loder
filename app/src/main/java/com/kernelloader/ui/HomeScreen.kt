package com.kernelloader.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kernelloader.R
import com.kernelloader.BuildConfig
import com.kernelloader.driver.DriverViewModel
import com.kernelloader.driver.SupportContact
import com.kernelloader.driver.OtaDriverStore
import com.kernelloader.root.RootChecker

// ---------------------------------------------------------------------------
// HOME - lightweight OTA loader screen
//   logo + name -> status chips -> animated circle button -> console
//   -> supported kernels (live from the GitHub driver database)
// ---------------------------------------------------------------------------
@Composable
fun HomeScreen(
    viewModel: DriverViewModel,
    onNavigateToConsole: () -> Unit,
    onNavigateToCredits: () -> Unit,
    onPickFile: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scroll = rememberScrollState()
    val listState = rememberLazyListState()

    val rootAvailable = remember { RootChecker.isRootAvailable() }
    val kernelVersion = remember { RootChecker.getKernelVersion() }
    val kernelRelease = remember { RootChecker.getKernelRelease() ?: kernelVersion }

    val manifest = viewModel.remoteManifest.value
    val manifestStatus = viewModel.manifestStatus.value
    val busy = viewModel.isBusy.value
    val busyStep = viewModel.busyStep.value
    val autoOk = viewModel.autoLoadOk.value
    val autoStatus = viewModel.autoLoadStatus.value
    val terminalLines = viewModel.terminalLines
    val updateStatus = viewModel.updateStatus.value
    val updateProgress = viewModel.updateProgress.value
    val appUpdate = viewModel.appUpdate.value

    LaunchedEffect(Unit) {
        viewModel.refreshManifest()
        viewModel.checkForAppUpdate()
    }
    LaunchedEffect(terminalLines.size) {
        if (terminalLines.isNotEmpty()) listState.animateScrollToItem(terminalLines.size - 1)
    }

    val exact = manifest?.let { OtaDriverStore.exactFor(it, kernelRelease) }
    val supported = manifest?.let { OtaDriverStore.supportedEntries(it) } ?: emptyList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(10.dp))

        // ---------------- header: logo + name + quick actions ----------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "Kernel Loder logo",
                modifier = Modifier.size(46.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Kernel Loder",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "OTA Kernel Module Loader · v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            IconButton(onClick = onPickFile) {
                Icon(Icons.Default.FolderOpen, contentDescription = "Pick .ko file")
            }
            IconButton(onClick = onNavigateToConsole) {
                Icon(Icons.Default.Terminal, contentDescription = "Full console")
            }
            IconButton(onClick = onNavigateToCredits) {
                Icon(Icons.Default.Info, contentDescription = "Credits")
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---------------- status chips ----------------
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(
                text = if (rootAvailable) "ROOT OK" else "ROOT MISSING",
                ok = rootAvailable,
                modifier = Modifier.weight(1f)
            )
            StatusChip(
                text = "KERNEL $kernelVersion",
                ok = true,
                modifier = Modifier.weight(2f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusChip(
                text = when {
                    exact != null -> "SUPPORTED: YES"
                    manifestStatus == "LOADING" -> "CHECKING DB..."
                    manifestStatus == "OK" -> "SUPPORTED: NO"
                    else -> "DB: $manifestStatus"
                },
                ok = exact != null,
                modifier = Modifier.weight(2f)
            )
            IconButton(onClick = { viewModel.refreshManifest() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh driver database")
            }
        }

        // ---------------- auto-update banner (GitHub Releases) ----------------
        when {
            updateStatus == "AVAILABLE" && appUpdate != null -> {
                val pulse = rememberInfiniteTransition(label = "updatePulse")
                val glow by pulse.animateFloat(
                    initialValue = 0.55f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(700, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "updateGlow"
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clickable { viewModel.installAppUpdate(context) },
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1B2A1F)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, Color(0xFF4CAF50).copy(alpha = glow)
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "UPDATE v${appUpdate.versionCode} · ${appUpdate.versionName}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "tap to download & install (${appUpdate.apkSize / 1024} KB)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        Text(
                            text = "INSTALL ↗",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            updateStatus == "DOWNLOADING" -> {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2A1F))
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(
                            text = "DOWNLOADING UPDATE... ${if (updateProgress >= 0) "$updateProgress%" else ""}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { if (updateProgress >= 0) updateProgress / 100f else 0f },
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // ---------------- animated circle button ----------------
        val transition = rememberInfiniteTransition(label = "loadBtn")
        val angle by transition.animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
            label = "angle"
        )
        val pulse by transition.animateFloat(
            initialValue = 0.95f, targetValue = 1.05f,
            animationSpec = infiniteRepeatable(tween(750, easing = LinearEasing), RepeatMode.Reverse),
            label = "pulse"
        )
        val ringColor = when {
            busy -> Color(0xFFFFB74D)
            autoOk == true -> Color(0xFF4CAF50)
            autoOk == false -> Color(0xFFEF5350)
            else -> MaterialTheme.colorScheme.primary
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.size(230.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                    val diameter = size.minDimension - stroke.width
                    val topLeft = androidx.compose.ui.geometry.Offset(
                        (size.width - diameter) / 2f, (size.height - diameter) / 2f
                    )
                    val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)
                    rotate(angle) {
                        drawArc(
                            color = ringColor.copy(alpha = 0.9f),
                            startAngle = 0f, sweepAngle = 130f, useCenter = false,
                            topLeft = topLeft, size = arcSize, style = stroke
                        )
                        drawArc(
                            color = ringColor.copy(alpha = 0.35f),
                            startAngle = 180f, sweepAngle = 100f, useCenter = false,
                            topLeft = topLeft, size = arcSize, style = stroke
                        )
                    }
                }
                Button(
                    onClick = { viewModel.autoLoadUniversal(context, preferOta = true) },
                    enabled = !busy && rootAvailable,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.size(160.dp).scale(pulse)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(34.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (busy) "WORKING" else "LOAD\nKERNEL",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = when {
                    busyStep.isNotBlank() -> busyStep
                    autoStatus.isNotBlank() -> autoStatus
                    exact != null -> "Ready: ${exact.version} loader on GitHub"
                    else -> "Tap to detect kernel + download loader"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(12.dp))

        // ---------------- console ----------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CONSOLE",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { clipboard.setText(AnnotatedString(viewModel.getTerminalText())) }) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy console")
            }
            IconButton(onClick = { viewModel.clearTerminal() }) {
                Icon(Icons.Default.Delete, contentDescription = "Clear console")
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth().height(250.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            if (terminalLines.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(38.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Tap the circle to start",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(10.dp)
                ) {
                    items(terminalLines) { line ->
                        Text(
                            text = "[${line.time}] ${line.text}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            ),
                            color = consoleLineColor(line.type),
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // ---------------- supported kernels (GitHub database) ----------------
        Text(
            text = when {
                manifestStatus == "OK" -> "SUPPORTED KERNELS (${supported.size}) · LATEST " +
                        (supported.firstOrNull()?.buildDate?.take(10) ?: "n/a")
                manifestStatus == "LOADING" -> "FETCHING DATABASE..."
                else -> "SUPPORTED KERNELS (offline)"
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "DB updated: ${manifest?.updated?.take(10) ?: "-"}  ·  " +
                    "github.com/bmjubairdadu/kernel-loder (drivers branch)",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 2.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(OtaDriverStore.manifestUrl))
                        )
                    } catch (_: Exception) { /* no browser installed */ }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "open driver database ↗",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF64B5F6),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "raw drivers.json",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            when {
                manifestStatus == "OK" -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(190.dp).padding(10.dp)
                ) {
                    itemsIndexed(supported) { idx, e ->
                        val v = e.version
                        val isThis = RootChecker.kernelShortVersion(v) ==
                                RootChecker.kernelShortVersion(kernelRelease)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "kernel $v",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                ),
                                color = if (isThis) Color(0xFF4CAF50)
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                modifier = Modifier.weight(1f)
                            )
                            if (e.buildDate.isNotBlank()) {
                                Text(
                                    text = e.buildDate.take(10),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            if (isThis) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "THIS DEVICE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF4CAF50),
                                    fontWeight = FontWeight.Bold
                                )
                            } else if (idx == 0) {
                                Text(
                                    text = "LATEST",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFFB74D),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 1.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    }
                }
                manifestStatus == "LOADING" -> Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalArrangement = Arrangement.Center
                ) { CircularProgressIndicator(modifier = Modifier.size(22.dp)) }
                else -> Text(
                    text = "Internet connection paoa jacche na - driver database dekha jacche na.\n" +
                            "Connect hoye refresh button chapon.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(18.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ---------------- support: custom loader via WhatsApp ----------------
        SupportCard(kernelRelease = kernelRelease, loadFailed = autoOk == false)
    }
}

/**
 * Contact card: kernel match hoy nai ba load fail hole user WhatsApp e
 * message korte parbe - pre-filled message e device model + kernel version
 * age theke jabe, tai custom loader banano easy hoy.
 */
@Composable
private fun SupportCard(kernelRelease: String, loadFailed: Boolean) {
    val context = LocalContext.current
    // User nijer moto extra kotha likhte parbe - sheta auto message er sathe jabe.
    var customText by remember { mutableStateOf("") }
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (loadFailed) Color(0xFF3E2723)
                             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = if (loadFailed) "LOAD FAILED - custom loader lagbe?"
                       else "Kernel match hoyni? Custom loader lagbe?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (loadFailed) Color(0xFFFF8A65) else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Apnar device model ar kernel version auto message e chole jabe - " +
                        "amra exact kernel er jonno loader banie dei.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 4.dp)
            )

            // ---------------- optional custom text ----------------
            OutlinedTextField(
                value = customText,
                onValueChange = { if (it.length <= 300) customText = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text("Extra text (optional)", style = MaterialTheme.typography.labelMedium) },
                placeholder = {
                    Text(
                        "e.g. phone bootloop hocche, ei app er jonno loader lagbe",
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                minLines = 2,
                maxLines = 4,
                textStyle = MaterialTheme.typography.bodySmall
            )
            Text(
                text = if (customText.isBlank()) "Auto message pathano hobe (${kernelRelease})"
                       else "Auto message + apnar text (${customText.trim().length}/300)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 3.dp)
            )

            Button(
                onClick = {
                    try {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(SupportContact.waLink(kernelRelease, customText))
                            )
                        )
                    } catch (_: Exception) { /* WhatsApp/browser nai */ }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_whatsapp),
                    contentDescription = "WhatsApp",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "WhatsApp: ${SupportContact.WHATSAPP_DISPLAY}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
@Composable
private fun StatusChip(text: String, ok: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = if (ok) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer,
        tonalElevation = 2.dp
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

private fun consoleLineColor(type: String): Color = when (type) {
    "OK" -> Color(0xFF4CAF50)
    "ERR" -> Color(0xFFEF5350)
    "WARN" -> Color(0xFFFFB74D)
    "CMD" -> Color(0xFF64B5F6)
    "FIX" -> Color(0xFF26C6DA)
    "OUT" -> Color(0xFF90A4AE)
    else -> Color(0xFFB0BEC5)
}
