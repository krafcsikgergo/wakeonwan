package hu.krafcsikgergo.wakeonwan

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import hu.krafcsikgergo.wakeonwan.composables.IPTextField
import hu.krafcsikgergo.wakeonwan.composables.PortTextField
import hu.krafcsikgergo.wakeonwan.composables.UsernameInput
import hu.krafcsikgergo.wakeonwan.composables.isValidIPv4
import hu.krafcsikgergo.wakeonwan.composables.isValidPort
import hu.krafcsikgergo.wakeonwan.composables.topRow
import hu.krafcsikgergo.wakeonwan.services.ApiImplementation
import hu.krafcsikgergo.wakeonwan.services.DataStoreManager
import hu.krafcsikgergo.wakeonwan.services.KtorServerData
import hu.krafcsikgergo.wakeonwan.services.NetworkManager
import hu.krafcsikgergo.wakeonwan.services.SSHManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SenderScreen(navigate: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var isServerLive by remember { mutableStateOf(false) }
    var isKtorServerLive by remember { mutableStateOf(false) }
    var ipAddress by remember { mutableStateOf(KtorServerData.ipAddress) }
    var communicationPort by remember { mutableIntStateOf(KtorServerData.port) }


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

            IPTextField(ipAddress = ipAddress) {
                ipAddress = it
                coroutineScope.launch(Dispatchers.IO) {
                    DataStoreManager.getInstance(context).writeString("ktorIpAddress", it)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PortTextField(
                    port = communicationPort.toString(),
                    label = "Communication Port",
                    modifier = Modifier
                        .padding(all = 20.dp)
                        .width(150.dp)
                ) {
                    communicationPort = it
                    coroutineScope.launch(Dispatchers.IO) {
                        DataStoreManager.getInstance(context)
                            .writeString("communicationPort", it.toString())
                    }
                }

                Button(modifier = Modifier
                    .padding(start = 25.dp, top = 25.dp, end = 25.dp, bottom = 25.dp)
                    .width(200.dp)
                    .height(50.dp),
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            if (isValidIPv4(ipAddress) && isValidPort(communicationPort.toString())) {
                                coroutineScope.launch(Dispatchers.Main) {
                                    ApiImplementation.baseUrl =
                                        "http://$ipAddress:$communicationPort"
                                    val network = NetworkManager()
                                    network.wakeUpRemoteServer() { it: String ->
                                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                coroutineScope.launch(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        "Failed to send magic packet",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }) {
                    Text("Wake up Server")
                }


            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 25.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    modifier = Modifier
                        .padding(start = 20.dp)
                        .height(50.dp),

                    onClick = {
                        if (isValidPort(communicationPort.toString()) && isValidIPv4(ipAddress)) {
                            coroutineScope.launch(Dispatchers.IO) {
                                ApiImplementation.baseUrl = "http://$ipAddress:$communicationPort"
                                val network = NetworkManager()
                                isKtorServerLive = network.checkKtorServerHealth { it: String ->
                                    Toast.makeText(context, it, Toast.LENGTH_SHORT)
                                        .show()
                                }
                            }
                        }
                    }
                ) {
                    BadgedBox(badge = {
                        Badge(
                            containerColor = if (isKtorServerLive) {
                                Color.Green
                            } else {
                                Color.Red
                            }
                        ) {

                        }
                    }) {
                        Text("Test receiver status")
                    }
                }


                Button(
                    modifier = Modifier
                        .padding(end = 20.dp)
                        .height(50.dp),
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            if (isValidIPv4(ipAddress) && isValidPort(communicationPort.toString())) {
                                ApiImplementation.baseUrl = "http://$ipAddress:$communicationPort"
                                val network = NetworkManager()
                                isServerLive = network.getServerStatus() { it: String ->
                                    Toast.makeText(context, it, Toast.LENGTH_SHORT)
                                        .show()
                                }
                            }
                        }
                    }) {
                    BadgedBox(badge = {
                        Badge(
                            containerColor = if (isServerLive) {
                                Color.Green
                            } else {
                                Color.Red
                            }
                        ) {

                        }
                    }) {
                        Text("Test server status")
                    }
                }
            }

            Spacer(modifier = Modifier.height(250.dp))

            Button(modifier = Modifier
                .padding(25.dp)
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
    }
}