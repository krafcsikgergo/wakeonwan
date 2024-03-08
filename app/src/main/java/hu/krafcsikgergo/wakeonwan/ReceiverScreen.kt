package hu.krafcsikgergo.wakeonwan

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import hu.krafcsikgergo.wakeonwan.composables.IPTextField
import hu.krafcsikgergo.wakeonwan.composables.MacAddressTextField
import hu.krafcsikgergo.wakeonwan.composables.PasswordInputField
import hu.krafcsikgergo.wakeonwan.composables.PortTextField
import hu.krafcsikgergo.wakeonwan.composables.UsernameInput
import hu.krafcsikgergo.wakeonwan.composables.isValidIPv4
import hu.krafcsikgergo.wakeonwan.composables.isValidMacAddress
import hu.krafcsikgergo.wakeonwan.composables.topRow
import hu.krafcsikgergo.wakeonwan.services.DataStoreManager
import hu.krafcsikgergo.wakeonwan.services.KtorServerService
import hu.krafcsikgergo.wakeonwan.services.ServerData
import hu.krafcsikgergo.wakeonwan.services.sendWakeOnLANPacket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ReceiverScreen(navigate: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var ipAddress by remember { mutableStateOf(ServerData.ipAddress) }
    var macAddress by remember { mutableStateOf(ServerData.macAddress) }
    var sshPort by remember { mutableIntStateOf(ServerData.sshPort) }
    var username by remember { mutableStateOf(ServerData.username) }
    var password by remember { mutableStateOf(ServerData.password) }


    Column {
        topRow(false) {
            navigate()
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(750.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            IPTextField(ipAddress) {
                ipAddress = it
                coroutineScope.launch(Dispatchers.IO) {
                    DataStoreManager.getInstance(context).writeString("serverIpAddress", it)
                }
            }

            MacAddressTextField(macAddress) {
                macAddress = it
                coroutineScope.launch(Dispatchers.IO) {
                    DataStoreManager.getInstance(context).writeString("serverMacAddress", it)
                }
            }

            PortTextField(
                port = sshPort.toString(),
                label = "SSH port",
                onValueChange = { it: Int ->
                    sshPort = it
                    coroutineScope.launch(Dispatchers.IO) {
                        DataStoreManager.getInstance(context).writeString("serverSSHPort", it.toString())
                    }
                },
                modifier = Modifier
                    .padding(all = 20.dp)
                    .width(200.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth(),
            ) {
                UsernameInput(
                    value = username,
                    onValueChange = {
                        username = it
                        coroutineScope.launch(Dispatchers.IO) {
                            DataStoreManager.getInstance(context).writeString("serverUsername", it)
                        }
                    }
                )

                PasswordInputField(
                    password = password,
                    onPasswordChanged = {
                        password = it
                        coroutineScope.launch(Dispatchers.IO) {
                            DataStoreManager.getInstance(context).writeString("serverPassword", it)
                        }
                    },
                )
            }

            Button(modifier = Modifier
                .width(350.dp)
                .height(100.dp)
                .padding(all = 20.dp),
                onClick = {
                    ServerData.ipAddress = ipAddress
                    ServerData.macAddress = macAddress
                    ServerData.sshPort = sshPort
                    ServerData.username = username
                    ServerData.password = password
                    val intent = Intent(context, KtorServerService::class.java)
                    context.startService(intent)
                    Toast.makeText(
                        context,
                        "Server started with server data: ${ipAddress + macAddress}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            ) {
                Text("Start listening for remote requests")
            }

            Spacer(modifier = Modifier.height(100.dp))

            Text(
                text = "Only works on local network!",
            )

            Button(modifier = Modifier
                .width(250.dp)
                .height(85.dp)
                .padding(all = 20.dp),
                onClick = {
                    if (isValidIPv4(ipAddress) && isValidMacAddress(macAddress)) {
                        // Send magic packet using coroutines
                        coroutineScope.launch(Dispatchers.IO) {
                            sendWakeOnLANPacket(ipAddress, macAddress)
                        }
                    } else {
                        Toast.makeText(context, "Invalid IP or MAC address", Toast.LENGTH_SHORT)
                            .show()
                    }

                }) {
                Text("Send magic packet")
            }
        }
    }
}