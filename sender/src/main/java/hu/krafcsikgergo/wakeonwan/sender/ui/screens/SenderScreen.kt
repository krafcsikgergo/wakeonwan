package hu.krafcsikgergo.wakeonwan.sender.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import hu.krafcsikgergo.wakeonwan.common.model.defaultKtorPort
import hu.krafcsikgergo.wakeonwan.common.ui.composables.IPTextField
import hu.krafcsikgergo.wakeonwan.common.ui.composables.PortTextField
import hu.krafcsikgergo.wakeonwan.common.ui.composables.isValidIPv4
import hu.krafcsikgergo.wakeonwan.common.ui.composables.TopRow
import hu.krafcsikgergo.wakeonwan.sender.services.KtorServerData
import org.koin.androidx.compose.koinViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SenderScreen(
    navigateToLogs: () -> Unit,
    navigateToSchedules: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel = koinViewModel<SenderViewModel>()
    val uiState = viewModel.uiState

    // Dialog state
    var showAddServerDialog by remember { mutableStateOf(false) }

    // Show toast messages for operation results
    LaunchedEffect(uiState.lastOperationMessage) {
        uiState.lastOperationMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearLastOperationMessage()
        }
    }

    // Show toast messages for errors
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
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
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Server Selection Section
            ServerSelectorSection(
                selectedServer = uiState.selectedKtorServer,
                servers = uiState.ktorServers,
                onServerSelected = { server ->
                    viewModel.selectKtorServer(server.id)
                },
                onAddServerClick = {
                    showAddServerDialog = true
                },
                onDeleteServer = { serverId ->
                    viewModel.deleteKtorServer(serverId)
                }
            )

            // Action Buttons Section
            ActionButtonsSection(
                isWakeUpInProgress = uiState.isWakeUpInProgress,
                isShutdownInProgress = uiState.isShutdownInProgress,
                onWakeUpClick = { viewModel.wakeUpServer() },
                onShutdownClick = { viewModel.shutdownServer() }
            )

            // Status Checkers Section
            StatusCheckersSection(
                ktorServerStatus = uiState.ktorServerStatus,
                serverPingStatus = uiState.serverPingStatus,
                serverSshStatus = uiState.serverSshStatus,
                onKtorStatusCheck = { viewModel.testKtorServerStatus() },
                onServerStatusCheck = { viewModel.testServerStatus() }
            )

            // Schedules Management Button
            ManageSchedulesButton(
                serverName = uiState.selectedKtorServer.name,
                enabled = uiState.selectedKtorServer.ipAddress.isNotEmpty(),
                onClick = {
                    navigateToSchedules(uiState.selectedKtorServer.id)
                }
            )
        }
    }

    // Add Server Dialog
    if (showAddServerDialog) {
        AddServerDialog(
            onDismiss = { showAddServerDialog = false },
            onConfirm = { name, ipAddress, port ->
                viewModel.addKtorServer(name, ipAddress, port)
                showAddServerDialog = false
            }
        )
    }
}

@Composable
fun StatusIndicator(isServerLive: ServerStatus) {
    val color = when (isServerLive) {
        ServerStatus.LIVE -> Color.Green
        ServerStatus.DEAD -> Color.Red
        ServerStatus.LOADING -> Color.Yellow
        else -> Color.Gray
    }
    val text = when (isServerLive) {
        ServerStatus.LIVE -> "LIVE"
        ServerStatus.DEAD -> "DEAD"
        ServerStatus.LOADING -> "LOADING"
        else -> "UNKNOWN"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.padding(top = 10.dp)
    ) {
        Icon(
            when (isServerLive) {
                ServerStatus.LIVE -> Icons.Default.CheckCircle
                ServerStatus.LOADING -> Icons.Default.Refresh
                ServerStatus.DEAD -> Icons.Default.Warning
                ServerStatus.UNKNOWN -> Icons.Default.Warning
            }, contentDescription = "Status", tint = color
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text)
    }
}


@Composable
fun ServerSelectorSection(
    selectedServer: KtorServerData,
    servers: List<KtorServerData>,
    onServerSelected: (KtorServerData) -> Unit,
    onAddServerClick: () -> Unit,
    onDeleteServer: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var serverToDelete by remember { mutableStateOf<KtorServerData?>(null) }

    // Calculate the available width (screen width - padding)
    val screenWidth = LocalWindowInfo.current.containerSize.width.dp
    val cardWidth = screenWidth - 40.dp // 20dp padding on each side

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedServer.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${selectedServer.ipAddress}:${selectedServer.port}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Select server"
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(cardWidth)
        ) {
            servers.forEach { server ->
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = server.name,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "${server.ipAddress}:${server.port}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    serverToDelete = server
                                    showDeleteConfirmDialog = true
                                    expanded = false
                                },
                                modifier = Modifier.size(48.dp) // Even larger touch target
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete server",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(28.dp) // Much larger icon
                                )
                            }
                        }
                    },
                    onClick = {
                        onServerSelected(server)
                        expanded = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp) // More height for better spacing
                )
            }
            if (servers.isNotEmpty()) {
                androidx.compose.material3.HorizontalDivider()
            }
            DropdownMenuItem(
                text = {
                    Text(
                        "Add New Server",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add server",
                        modifier = Modifier.size(24.dp)
                    )
                },
                onClick = {
                    onAddServerClick()
                    expanded = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp) // Consistent height
            )
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog && serverToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmDialog = false
                serverToDelete = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Server",
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text("Delete Server")
            },
            text = {
                Text("Are you sure you want to delete server '${serverToDelete?.name}'? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        serverToDelete?.let { server ->
                            onDeleteServer(server.id)
                        }
                        showDeleteConfirmDialog = false
                        serverToDelete = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        serverToDelete = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ActionButtonsSection(
    isWakeUpInProgress: Boolean,
    isShutdownInProgress: Boolean,
    onWakeUpClick: () -> Unit,
    onShutdownClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Wake Up Button
            ActionButton(
                onClick = onWakeUpClick,
                enabled = !isWakeUpInProgress,
                icon = Icons.Default.PlayArrow,
                text = "Wake Up",
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                isLoading = isWakeUpInProgress
            )

            // Shutdown Button
            ActionButton(
                onClick = onShutdownClick,
                enabled = !isShutdownInProgress,
                icon = Icons.Default.Close,
                text = "Shutdown",
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                isLoading = isShutdownInProgress
            )
        }
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
    isLoading: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        modifier = Modifier
            .height(72.dp)
            .width(160.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isLoading) Icons.Default.Refresh else icon,
                contentDescription = text,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
fun StatusCheckersSection(
    ktorServerStatus: ServerStatus,
    serverPingStatus: ServerStatus,
    serverSshStatus: ServerStatus,
    onKtorStatusCheck: () -> Unit,
    onServerStatusCheck: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatusChecker(
                title = "Mobile App Server",
                status = ktorServerStatus,
                onCheck = onKtorStatusCheck,
            )

            StatusChecker(
                title = "Target Server (Ping)",
                status = serverPingStatus,
                onCheck = onServerStatusCheck,
            )

            StatusChecker(
                title = "Target Server (SSH)",
                status = serverSshStatus,
                onCheck = onServerStatusCheck,
            )
        }
    }
}

@Composable
fun StatusChecker(
    title: String,
    status: ServerStatus,
    onCheck: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(180.dp)
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            StatusIndicator(isServerLive = status)

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onCheck,
                enabled = status != ServerStatus.LOADING,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Check")
            }
        }
    }
}

@Composable
fun AddServerDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, ipAddress: String, port: Int) -> Unit
) {
    var serverName by remember { mutableStateOf("") }
    var ipAddress by remember { mutableStateOf("") }
    var portString by remember { mutableStateOf(defaultKtorPort.toString()) }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Server"
            )
        },
        title = {
            Text("Add New Server")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = serverName,
                    onValueChange = {
                        serverName = it
                        isError = false
                    },
                    label = { Text("Server Name") },
                    placeholder = { Text("e.g., Home PC") },
                    singleLine = true,
                    isError = isError && serverName.isBlank(),
                    modifier = Modifier.fillMaxWidth()
                )

                // Use the existing IPTextField component
                IPTextField(
                    ipAddress = ipAddress,
                    onValueChange = {
                        ipAddress = it
                        isError = false
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Use the existing PortTextField component
                PortTextField(
                    port = portString,
                    label = "Port",
                    modifier = Modifier.fillMaxWidth(),
                    onValueChange = { newPortString ->
                        portString = newPortString
                        isError = false
                    }
                )

                if (isError) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Validate inputs using the existing validation functions
                    val portInt = portString.toIntOrNull()
                    when {
                        serverName.isBlank() -> {
                            isError = true
                            errorMessage = "Server name is required"
                        }

                        !isValidIPv4(ipAddress) -> {
                            isError = true
                            errorMessage = "Invalid IP address format"
                        }

                        portInt == null || portInt !in 1..65535 -> {
                            isError = true
                            errorMessage = "Invalid port number (1-65535)"
                        }

                        else -> {
                            onConfirm(serverName, ipAddress, portInt)
                        }
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ManageSchedulesButton(
    serverName: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Manage Schedules",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Configure wake/sleep schedules for $serverName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = "Manage Schedules",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Schedules")
            }
        }
    }
}
