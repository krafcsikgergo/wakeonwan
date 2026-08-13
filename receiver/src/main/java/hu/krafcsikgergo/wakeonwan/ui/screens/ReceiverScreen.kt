package hu.krafcsikgergo.wakeonwan.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import hu.krafcsikgergo.wakeonwan.common.services.LogManager
import hu.krafcsikgergo.wakeonwan.common.ui.composables.IPTextField
import hu.krafcsikgergo.wakeonwan.common.ui.composables.MacAddressTextField
import hu.krafcsikgergo.wakeonwan.common.ui.composables.PortTextField
import hu.krafcsikgergo.wakeonwan.common.ui.composables.UsernameInput
import hu.krafcsikgergo.wakeonwan.common.ui.composables.TopRow
import hu.krafcsikgergo.wakeonwan.services.receiver.KtorServerService
import hu.krafcsikgergo.wakeonwan.ui.generateQrCodeBitmap
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

@Composable
fun ReceiverScreen(navigateToSchedules: () -> Unit, navigateToLogs: () -> Unit) {
    val context = LocalContext.current
    val viewModel = koinViewModel<ReceiverViewModel>()
    val uiState = viewModel.uiState

    // Handle toast messages
    uiState.lastOperationMessage?.let { message ->
        LaunchedEffect(message) {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT)
                .show()
            viewModel.clearLastOperationMessage()
        }
    }

    // Handle error messages
    uiState.errorMessage?.let { message ->
        LaunchedEffect(message) {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT)
                .show()
            viewModel.clearError()
        }
    }

    Column {
        TopRow(
            title = "Wake on WAN",
            switchToText = "Logs",
            onNavigate = navigateToLogs,
            icon = Icons.Default.List
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (uiState.isKtorServerRunning) {
                // Server Running Display
                ServerRunningSection(
                    serverData = uiState.serverData,
                    serverStarted = uiState.serverStarted,
                    onStopServer = {
                        val stopIntent = Intent(context, KtorServerService::class.java)
                        context.stopService(stopIntent)
                        viewModel.stopKtorServer()
                    },
                    isStoppingInProgress = uiState.isKtorServerOperationInProgress,
                    onCheckConnection = { viewModel.checkConnection() },
                    isCheckingConnection = uiState.isCheckingConnection
                )
            } else {
                // Configuration Input Sections
                NetworkConfigurationSection(
                    serverData = uiState.serverData,
                    onIpAddressChange = { viewModel.updateServerIpAddress(it) },
                    onMacAddressChange = { viewModel.updateMacAddress(it) },
                    onTestWakeOnLanClick = { viewModel.sendTestWakeOnLanPacket() },
                    isTestWakeOnLanInProgress = uiState.isTestWakeOnLanInProgress
                )

                SSHConfigurationSection(
                    serverData = uiState.serverData,
                    onPortChange = { portString ->
                        val portInt = portString.toIntOrNull()
                        if (portInt != null && portInt in 1..65535) {
                            viewModel.updateSshPort(portInt)
                        }
                    },
                    onUsernameChange = { viewModel.updateUsername(it) },
                    onTestSshClick = { viewModel.testSshConnection() },
                    isTestSshInProgress = uiState.isTestSshInProgress,
                    hasSshKey = uiState.hasSshKey,
                    sshPublicKey = uiState.sshPublicKey,
                    isGeneratingSshKey = uiState.isGeneratingSshKey,
                    onGenerateSshKeyClick = { viewModel.generateSshKey() }
                )

            }

            // Action Buttons Section
            ActionButtonsSection(
                onSchedulesClick = navigateToSchedules,
                onStartServerClick = if (!uiState.isKtorServerRunning) {
                    {
                        // Stop any running instance of KtorServerService
                        val stopIntent = Intent(context, KtorServerService::class.java)
                        context.stopService(stopIntent)

                        // Start a new instance of KtorServerService
                        val intent = Intent(context, KtorServerService::class.java)
                        context.startService(intent)

                        viewModel.startKtorServer()
                    }
                } else null,
                isStartServerInProgress = uiState.isKtorServerOperationInProgress
            )
        }
    }
}

@Composable
fun ServerRunningSection(
    serverData: hu.krafcsikgergo.wakeonwan.services.receiver.ServerData,
    serverStarted: Long?,
    onStopServer: () -> Unit,
    isStoppingInProgress: Boolean,
    onCheckConnection: () -> Unit,
    isCheckingConnection: Boolean
) {
    var elapsedTime by remember { mutableLongStateOf(0L) }

    // Timer effect - calculate actual elapsed time from server start
    LaunchedEffect(serverStarted) {
        while (true) {
            if (serverStarted != null) {
                elapsedTime = (System.currentTimeMillis() - serverStarted) / 1000
            } else {
                elapsedTime = 0L
            }
            delay(1000)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = "Server Running",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Server is Running",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Text(
                text = formatElapsedTime(elapsedTime),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "IP Address",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = serverData.ipAddress,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "MAC Address",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = serverData.macAddress,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionButton(
                    onClick = onCheckConnection,
                    enabled = !isCheckingConnection,
                    icon = Icons.Default.NetworkCheck,
                    text = if (isCheckingConnection) "Checking..." else "Check Connection",
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    isLoading = isCheckingConnection,
                    modifier = Modifier.width(160.dp)
                )

                ActionButton(
                    onClick = onStopServer,
                    enabled = !isStoppingInProgress,
                    icon = Icons.Default.Stop,
                    text = if (isStoppingInProgress) "Stopping..." else "Stop Server",
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    isLoading = isStoppingInProgress,
                    modifier = Modifier.width(160.dp)
                )
            }
        }
    }
}

@Composable
fun NetworkConfigurationSection(
    serverData: hu.krafcsikgergo.wakeonwan.services.receiver.ServerData,
    onIpAddressChange: (String) -> Unit,
    onMacAddressChange: (String) -> Unit,
    onTestWakeOnLanClick: () -> Unit,
    isTestWakeOnLanInProgress: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header row with title and test button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Network Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onTestWakeOnLanClick,
                    enabled = !isTestWakeOnLanInProgress,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    ),
                    modifier = Modifier.height(36.dp)
                ) {
                    if (isTestWakeOnLanInProgress) {
                        Text("Testing...", style = MaterialTheme.typography.labelSmall)
                    } else {
                        Text("Test WOL", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            IPTextField(
                ipAddress = serverData.ipAddress,
                onValueChange = onIpAddressChange
            )

            MacAddressTextField(
                macAddress = serverData.macAddress,
                onValueChange = onMacAddressChange
            )
        }
    }
}

@Composable
fun SSHConfigurationSection(
    serverData: hu.krafcsikgergo.wakeonwan.services.receiver.ServerData,
    onPortChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onTestSshClick: () -> Unit,
    isTestSshInProgress: Boolean,
    hasSshKey: Boolean,
    sshPublicKey: String?,
    isGeneratingSshKey: Boolean,
    onGenerateSshKeyClick: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header row with title and test button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SSH Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onTestSshClick,
                    enabled = !isTestSshInProgress,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    modifier = Modifier.height(36.dp)
                ) {
                    if (isTestSshInProgress) {
                        Text("Testing...", style = MaterialTheme.typography.labelSmall)
                    } else {
                        Text("Test SSH", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            PortTextField(
                port = serverData.sshPort.toString(),
                label = "SSH Port",
                onValueChange = onPortChange
            )

            UsernameInput(
                value = serverData.username,
                onValueChange = onUsernameChange,
                modifier = Modifier.fillMaxWidth()
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (hasSshKey) "SSH key configured" else "No SSH key",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (hasSshKey) {
                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(sshPublicKey.orEmpty()))
                                android.widget.Toast.makeText(
                                    context,
                                    "Public key copied to clipboard",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                        ) {
                            Text(
                                text = "Copy Key",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                    }

                    Button(
                        onClick = onGenerateSshKeyClick,
                        enabled = !isGeneratingSshKey,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                    ) {
                        Text(
                            text = when {
                                isGeneratingSshKey -> "Generating..."
                                hasSshKey -> "Regenerate"
                                else -> "Generate Key"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActionButtonsSection(
    onSchedulesClick: () -> Unit,
    onStartServerClick: (() -> Unit)? = null,
    isStartServerInProgress: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (onStartServerClick != null) Arrangement.SpaceEvenly else Arrangement.Center
    ) {
        if (onStartServerClick != null) {
            ActionButton(
                onClick = onStartServerClick,
                enabled = !isStartServerInProgress,
                icon = Icons.Default.PlayArrow,
                text = if (isStartServerInProgress) "Starting..." else "Start Server",
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                isLoading = isStartServerInProgress,
                modifier = Modifier.width(140.dp)
            )
        }

        ActionButton(
            onClick = onSchedulesClick,
            enabled = true,
            icon = Icons.Default.Schedule,
            text = "Schedules",
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
            modifier = Modifier.width(140.dp)
        )
    }
}

@Composable
fun ActionButton(
    onClick: () -> Unit,
    enabled: Boolean,
    icon: ImageVector,
    text: String,
    containerColor: Color,
    contentColor: Color,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        modifier = modifier.height(72.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun formatElapsedTime(seconds: Long): String {
    val days = seconds / 86400  // 24 * 60 * 60
    val hours = (seconds % 86400) / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return String.format("%02d:%02d:%02d:%02d", days, hours, minutes, secs)
}
