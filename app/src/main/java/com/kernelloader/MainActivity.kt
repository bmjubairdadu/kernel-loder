package com.kernelloader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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

import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.ui.res.stringResource
import com.kernelloader.R
import com.kernelloader.ui.CreditsRoute
import com.kernelloader.ui.CreditsScreen
import com.kernelloader.ui.ConsoleRoute
import com.kernelloader.ui.ConsoleScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize libsu
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
    // Universal: green when an exact driver matches this kernel, else warning colour.
    // Device / model / brand is never checked - only the kernel release matters.
    val compatOk = remember { compatMsg.startsWith("OK") }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Surface(
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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.app_logo),
                                contentDescription = "Kernel Loder logo",
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                        IconButton(onClick = onNavigateToConsole) {
                            Icon(Icons.Default.Terminal, contentDescription = stringResource(R.string.console))
                        }
                        IconButton(onClick = onNavigateToCredits) {
                            Icon(Icons.Default.Info, contentDescription = stringResource(R.string.credits))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (rootAvailable) stringResource(R.string.root_ok) else stringResource(R.string.root_missing),
                            color = if (rootAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = stringResource(R.string.kernel_version, kernelVersion),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = compatMsg,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = if (compatOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
    // Driver Picker - now just shows kernel info + Load button
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Terminal, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.kernel_loader), style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.kernel_version, kernelVersion),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.load_kernel_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.autoLoadUniversal(context) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = rootAvailable && !viewModel.isBusy.value,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.load_kernel), fontWeight = FontWeight.Bold)
                    }
                    if (viewModel.isBusy.value) {
                        Text(
                            text = ">> ${viewModel.busyStep.value}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = Color(0xFFFFAB40)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Spacer(modifier = Modifier.height(16.dp))

            // Verification Action
            Button(
                onClick = { viewModel.verifyModule() },
                modifier = Modifier.fillMaxWidth(),
                enabled = rootAvailable,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.verify_module))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Verification Results
            viewModel.verificationResult.value?.let { result ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.verification_results) + " (${result.timestamp})",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.device_node_check) + " ${if (result.deviceNodeFound) stringResource(R.string.found) else stringResource(R.string.not_found)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (result.deviceNodeFound) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        if (result.lsmod.isNotEmpty()) {
                            Text(text = stringResource(R.string.lsmod_output), style = MaterialTheme.typography.labelSmall)
                            Text(
                                text = result.lsmod.take(3).joinToString("\n") + if (result.lsmod.size > 3) "\n..." else "",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                            )
                        }
                        if (result.dmesgLogs.isNotEmpty()) {
                            Text(text = stringResource(R.string.dmesg_logs), style = MaterialTheme.typography.labelSmall)
                            Text(
                                text = result.dmesgLogs.joinToString("\n"),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Log Console
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = stringResource(R.string.diagnostic_log_console), style = MaterialTheme.typography.titleMedium)
                Row {
                    IconButton(onClick = { clipboardManager.setText(AnnotatedString(viewModel.getLogsText())) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy_logs))
                    }
                    IconButton(onClick = { viewModel.clearLogs() }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear_logs))
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    items(viewModel.logs) { entry ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                text = "[${entry.timestamp}] $ ${entry.command}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                            if (entry.stdout.isNotEmpty()) {
                                Text(
                                    text = entry.stdout.joinToString("\n"),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                            if (entry.stderr.isNotEmpty()) {
                                Text(
                                    text = entry.stderr.joinToString("\n"),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                )
                            }
                            Text(
                                text = stringResource(R.string.exit_code, entry.exitCode),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = if (entry.exitCode == 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                                )
                            )
                            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}
