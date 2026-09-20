package com.kernelloader

import android.os.Bundle
import androidx.compose.ui.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import com.kernelloader.driver.DriverViewModel
import com.kernelloader.root.RootChecker
import com.kernelloader.ui.MainRoute
import com.kernelloader.ui.SafetyWarningDialog
import com.kernelloader.ui.WarningRoute
import com.kernelloader.ui.theme.KernelLoderTheme
import com.topjohnwu.superuser.Shell
import com.kernelloader.ui.CreditsRoute
import com.kernelloader.ui.CreditsScreen
import com.kernelloader.ui.ConsoleRoute
import com.kernelloader.ui.ConsoleScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shell.setDefaultBuilder(Shell.Builder.create()
            .setFlags(Shell.FLAG_REDIRECT_STDERR)
            .setTimeout(10)
        )
        enableEdgeToEdge()
        setContent {
            KernelLoderTheme {
                val backStack = remember { NavBackStack<NavKey>(MainRoute) }
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    entryProvider = { key ->
                        when (key) {
                            is WarningRoute -> NavEntry<NavKey>(key) {
                                SafetyWarningDialog(
                                    onDismiss = { finish() },
                                    onConfirm = { backStack.add(MainRoute) }
                                )
                            }
                            is MainRoute -> NavEntry<NavKey>(key) {
                                MainScreen(
                                    onNavigateToCredits = { backStack.add(CreditsRoute) },
                                    onNavigateToConsole = { backStack.add(ConsoleRoute) }
                                )
                            }
                            is CreditsRoute -> NavEntry<NavKey>(key) {
                                CreditsScreen(onBack = { backStack.removeLastOrNull() })
                            }
                            is ConsoleRoute -> NavEntry<NavKey>(key) {
                                ConsoleScreen(
                                    viewModel = viewModel(),
                                    onBack = { backStack.removeLastOrNull() }
                                )
                            }
                            else -> error("Unknown key: $key")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun MainScreen(
    viewModel: DriverViewModel = viewModel(),
    onNavigateToCredits: () -> Unit,
    onNavigateToConsole: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val rootAvailable = remember { RootChecker.isRootAvailable() }
    val kernelVersion = remember { RootChecker.getKernelVersion() }
    val compatMsg = remember { RootChecker.getCompatibilityMessage() }
    val compatOk = remember { compatMsg.startsWith("OK") }
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let { viewModel.onFilePicked(context, it) } }
    )
    val busy = viewModel.isBusy.value
    val busyStep = viewModel.busyStep.value
    val logs = viewModel.logs

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.app_logo),
                            contentDescription = "Kernel Loder logo",
                            modifier = Modifier.size(48.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Universal Kernel Module Loader",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (rootAvailable) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                            tonalElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(14.dp), tint = if (rootAvailable) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (rootAvailable) "ROOT: OK" else "ROOT: MISSING",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (rootAvailable) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            tonalElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "KERNEL: $kernelVersion",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        IconButton(onClick = onNavigateToConsole) { Icon(Icons.Default.Terminal, contentDescription = stringResource(R.string.console)) }
                        IconButton(onClick = onNavigateToCredits) { Icon(Icons.Default.Info, contentDescription = stringResource(R.string.credits)) }
                    }
                    if (!compatOk && compatMsg.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                            Text(text = compatMsg, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    } else if (compatMsg.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                            Text(text = compatMsg, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer, tonalElevation = 4.dp) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = stringResource(R.string.kernel_loader), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = stringResource(R.string.kernel_version, kernelVersion), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = stringResource(R.string.no_builtin_drivers), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { viewModel.autoLoadUniversal(context) }, modifier = Modifier.weight(1f), enabled = rootAvailable && !busy, shape = MaterialTheme.shapes.medium) {
                            Icon(Icons.Default.Bolt, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(R.string.load_kernel), fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(onClick = { pickerLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium) {
                            Icon(Icons.Default.FileOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(R.string.import_ko))
                        }
                    }
                    if (busy) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = ">> $busyStep", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = Color(0xFFFFAB40))
                    }
                }
            }
            viewModel.verificationResult.value?.let { result ->
                Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.tertiaryContainer, tonalElevation = 4.dp) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = stringResource(R.string.verification_results), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Surface(shape = MaterialTheme.shapes.small, color = if (result.deviceNodeFound) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer) {
                                Text(text = if (result.deviceNodeFound) "VERIFIED" else "FAILED", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = if (result.deviceNodeFound) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = stringResource(R.string.device_node_check) + " ${if (result.deviceNodeFound) stringResource(R.string.found) else stringResource(R.string.not_found)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = if (result.deviceNodeFound) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        if (result.lsmod.isNotEmpty()) { Spacer(modifier = Modifier.height(6.dp)); Text(result.lsmod.take(5).joinToString("\n"), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp)) }
                        if (result.dmesgLogs.isNotEmpty()) { Spacer(modifier = Modifier.height(6.dp)); Text(result.dmesgLogs.joinToString("\n"), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp)) }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(R.string.diagnostic_log_console), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                Row {
                    IconButton(onClick = { clipboardManager.setText(AnnotatedString(viewModel.getLogsText())) }) { Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy_logs)) }
                    IconButton(onClick = { viewModel.clearLogs() }) { Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear_logs)) }
                }
            }
            Surface(modifier = Modifier.fillMaxWidth().weight(1f), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 2.dp) {
                if (logs.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                        items(logs) { entry ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(text = "[${entry.timestamp}] $ ${entry.command}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold))
                                if (entry.stdout.isNotEmpty()) { Text(text = entry.stdout.joinToString("\n"), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp)) }
                                if (entry.stderr.isNotEmpty()) { Text(text = entry.stderr.joinToString("\n"), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.error)) }
                                Text(text = stringResource(R.string.exit_code, entry.exitCode), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = if (entry.exitCode == 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error))
                                HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "No logs yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Text(text = "Press [Load Kernel] to start", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}
