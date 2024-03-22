package hu.krafcsikgergo.wakeonwan

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import hu.krafcsikgergo.wakeonwan.composables.IPTextField
import hu.krafcsikgergo.wakeonwan.composables.PortTextField
import hu.krafcsikgergo.wakeonwan.composables.isValidIPv4
import hu.krafcsikgergo.wakeonwan.composables.isValidPort
import hu.krafcsikgergo.wakeonwan.composables.topRow
import hu.krafcsikgergo.wakeonwan.services.ApiImplementation
import hu.krafcsikgergo.wakeonwan.services.DataStoreManager
import hu.krafcsikgergo.wakeonwan.services.KtorServerData
import hu.krafcsikgergo.wakeonwan.services.NetworkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class Status {
    LIVE, DEAD, UNKNOWN, LOADING
}

@Composable
fun SenderScreen(navigate: () -> Unit, navigteToSchedules: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var isServerLive by remember { mutableStateOf(Status.UNKNOWN) }
    var isKtorServerLive by remember { mutableStateOf(Status.UNKNOWN) }
    var ipAddress by remember { mutableStateOf(KtorServerData.ipAddress) }
    var communicationPort by remember { mutableIntStateOf(KtorServerData.port) }
    var sendingMagicPackageInProgress by remember { mutableStateOf(false) }

    fun testKtorServerStatus() {
        isKtorServerLive = Status.LOADING
        if (isValidPort(communicationPort.toString()) && isValidIPv4(ipAddress)) {
            coroutineScope.launch(Dispatchers.IO) {
                ApiImplementation.baseUrl = "http://$ipAddress:$communicationPort"
                Log.d("KtorServer", "Testing Ktor server status at: $ipAddress:$communicationPort")
                val network = NetworkManager()
                val ktorServerLiveResponse = network.checkKtorServerHealth()
                isKtorServerLive = if (ktorServerLiveResponse) {
                    Status.LIVE
                } else {
                    Status.DEAD
                }
            }
        }
    }

    fun testServerStatus() {
        isServerLive = Status.LOADING
        coroutineScope.launch(Dispatchers.IO) {
            if (isValidIPv4(ipAddress) && isValidPort(communicationPort.toString())) {
                ApiImplementation.baseUrl =
                    "http://$ipAddress:$communicationPort"
                val network = NetworkManager()
                val serverIsLive = network.getServerStatus()
                isServerLive = if (serverIsLive) {
                    Status.LIVE
                } else {
                    Status.DEAD
                }
            }
        }
    }

    fun wakeUpServer() {
        sendingMagicPackageInProgress = true
        coroutineScope.launch(Dispatchers.IO) {
            if (isValidIPv4(ipAddress) && isValidPort(communicationPort.toString())) {
                coroutineScope.launch(Dispatchers.Main) {
                    ApiImplementation.baseUrl =
                        "http://$ipAddress:$communicationPort"
                    val network = NetworkManager()
                    network.wakeUpRemoteServer { it: String ->
                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                        sendingMagicPackageInProgress = false
                    }
                }
            } else {
                coroutineScope.launch(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Failed to send magic packet",
                        Toast.LENGTH_SHORT
                    ).show()
                    sendingMagicPackageInProgress = false
                }
            }
        }
    }

    // Run server status tests on start
    LaunchedEffect(key1 = Unit) {
        testKtorServerStatus()
        testServerStatus()
    }

    Column {
        topRow(true) {
            navigate()
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(750.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "Receiver device settings",
                fontSize = 22.sp,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            IPTextField(ipAddress = ipAddress) {
                ipAddress = it
                coroutineScope.launch(Dispatchers.IO) {
                    DataStoreManager.getInstance(context).writeString("ktorIpAddress", it)
                }
            }

            PortTextField(
                port = communicationPort.toString(),
                label = "Communication Port",
                modifier = Modifier
                    .padding(all = 20.dp)
                    .width(200.dp)
            ) {
                communicationPort = it
                coroutineScope.launch(Dispatchers.IO) {
                    DataStoreManager.getInstance(context)
                        .writeString("communicationPort", it.toString())
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 25.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Button(modifier = Modifier
                    .height(50.dp),
                    enabled = !sendingMagicPackageInProgress,
                    onClick = {
                        wakeUpServer()
                    }) {
                    Text("Wake up Server")
                }

                Button(modifier = Modifier
                    .height(50.dp),
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            if (isValidIPv4(ipAddress) && isValidPort(communicationPort.toString())) {
                                ApiImplementation.baseUrl = "http://$ipAddress:$communicationPort"
                                val network = NetworkManager()
                                network.shutDownRemoteServer { it: String ->
                                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }) {
                    Text("Shut down server")
                }

            }

            Spacer(modifier = Modifier.height(100.dp))

            Button(modifier = Modifier
                .height(50.dp),
                enabled = !sendingMagicPackageInProgress,
                onClick = {
                    navigteToSchedules()
                }) {
                Text("Schedules")
            }

            Spacer(modifier = Modifier.height(100.dp))

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
                StatusChecker("Ktor server", isKtorServerLive, ::testKtorServerStatus)
                StatusChecker("Main server", isServerLive, ::testServerStatus)
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