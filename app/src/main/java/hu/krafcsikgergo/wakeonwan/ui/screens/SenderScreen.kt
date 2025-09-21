package hu.krafcsikgergo.wakeonwan.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.krafcsikgergo.wakeonwan.ui.composables.IPTextField
import hu.krafcsikgergo.wakeonwan.ui.composables.PortTextField
import hu.krafcsikgergo.wakeonwan.ui.composables.topRow
import hu.krafcsikgergo.wakeonwan.services.DataStoreManager
import hu.krafcsikgergo.wakeonwan.services.sender.KtorServerData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

// Legacy Status enum - keeping for compatibility with existing StatusChecker
enum class Status {
    LIVE, DEAD, UNKNOWN, LOADING
}

// Extension to convert new ServerStatus to legacy Status
fun ServerStatus.toLegacyStatus(): Status = when (this) {
    ServerStatus.LIVE -> Status.LIVE
    ServerStatus.DEAD -> Status.DEAD
    ServerStatus.UNKNOWN -> Status.UNKNOWN
    ServerStatus.LOADING -> Status.LOADING
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SenderScreen(navigate: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val viewModel = koinViewModel<SenderViewModel>()
    val uiState = viewModel.uiState

    // Show toast messages for operation results
    LaunchedEffect(uiState.lastOperationMessage) {
        uiState.lastOperationMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
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
        topRow(true) {
            navigate()
        }

        Column(
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceAround,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Receiver device settings
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Receiver device settings",
                    fontSize = 22.sp,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                IPTextField(ipAddress = uiState.selectedKtorServer.ipAddress) {
                    viewModel.updateServerIpAddress(it)
                }

                PortTextField(
                    port = uiState.selectedKtorServer.port.toString(),
                    label = "Communication Port",
                    modifier = Modifier
                        .padding(all = 20.dp)
                        .width(200.dp)
                ) {
                    viewModel.updateCommunicationPort(it)
                }

                // Save button
                Button(
                    modifier = Modifier
                        .height(50.dp),
                    onClick = {
                        val newKtorServer = KtorServerData(
                            ipAddress = uiState.selectedKtorServer.ipAddress,
                            port = uiState.selectedKtorServer.port,
                            name = "Saved at ${System.currentTimeMillis()}"
                        )
                        viewModel.saveNewKtorServer(newKtorServer)
                    }) {
                    Text("Save")
                }
            }

            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 25.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Button(
                    modifier = Modifier.height(50.dp),
                    enabled = !uiState.isWakeUpInProgress,
                    onClick = {
                        viewModel.wakeUpServer()
                    }
                ) {
                    Text("Wake up Server")
                }

                Button(
                    modifier = Modifier.height(50.dp),
                    enabled = !uiState.isShutdownInProgress,
                    onClick = {
                        viewModel.shutdownServer()
                    }
                ) {
                    Text("Shut down server")
                }
            }

            // List of saved connections
            SavedServersList(
                servers = uiState.ktorServers,
                onDeleteClick = { server ->
                    viewModel.deleteKtorServer(server.id)
                },
                onItemClick = { server ->
                    viewModel.selectKtorServer(server.id)
                }
            )

            // Status checkers
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Status title
                Text(
                    text = "Servers status",
                    fontSize = 24.sp
                )

                // Status check buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 25.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    StatusChecker(
                        title = "Ktor server",
                        isServerLive = uiState.ktorServerStatus.toLegacyStatus(),
                        testServerStatus = { viewModel.testKtorServerStatus() }
                    )
                    StatusChecker(
                        title = "Main server",
                        isServerLive = uiState.serverStatus.toLegacyStatus(),
                        testServerStatus = { viewModel.testServerStatus() }
                    )
                }
            }
        }
    }
}

@Composable
fun StatusChecker(title: String, isServerLive: Status, testServerStatus: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontSize = 20.sp, modifier = Modifier.padding(bottom = 10.dp))
        Button(
            modifier = Modifier
                .height(50.dp),
            enabled = isServerLive != Status.LOADING,
            onClick = {
                testServerStatus()
            }
        ) {
            Text("Test server status")

        }
        StatusIndicator(isServerLive)
    }
}

@Composable
fun StatusIndicator(isServerLive: Status) {
    val color = when (isServerLive) {
        Status.LIVE -> Color.Green
        Status.DEAD -> Color.Red
        Status.LOADING -> Color.Yellow
        else -> Color.Gray
    }
    val text = when (isServerLive) {
        Status.LIVE -> "LIVE"
        Status.DEAD -> "DEAD"
        Status.LOADING -> "LOADING"
        else -> "UNKNOWN"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.padding(top = 10.dp)
    ) {
        Icon(
            when (isServerLive) {
                Status.LIVE -> Icons.Default.CheckCircle
                Status.LOADING -> Icons.Default.Refresh
                Status.DEAD -> Icons.Default.Warning
                Status.UNKNOWN -> Icons.Default.Warning
            }, contentDescription = "Status", tint = color
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text)
    }
}


@Composable
fun SavedServersList(
    servers: List<KtorServerData>,
    onDeleteClick: (KtorServerData) -> Unit,
    onItemClick: (KtorServerData) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(top = 25.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Saved servers",
            fontSize = 24.sp
        )

        LazyColumn {
            servers.forEachIndexed { i, server ->
                item {
                    Row(
                        modifier = Modifier
                            .clickable { onItemClick(server) }
                            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 10.dp))
                    {
                        Text(
                            text = "${i + 1}:",
                            fontSize = 18.sp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "${server.ipAddress}:${server.port}",
                            fontSize = 18.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier
                                .clickable { onDeleteClick(server) }
                                .padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}