package hu.krafcsikgergo.wakeonwan.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import hu.krafcsikgergo.wakeonwan.ui.composables.IPTextField
import hu.krafcsikgergo.wakeonwan.ui.composables.MacAddressTextField
import hu.krafcsikgergo.wakeonwan.ui.composables.PasswordInputField
import hu.krafcsikgergo.wakeonwan.ui.composables.PortTextField
import hu.krafcsikgergo.wakeonwan.ui.composables.UsernameInput
import hu.krafcsikgergo.wakeonwan.ui.composables.TopRow
import hu.krafcsikgergo.wakeonwan.services.receiver.KtorServerService
import org.koin.androidx.compose.koinViewModel

@Composable
fun ReceiverScreen(navigateToSender: () -> Unit, navigateToSchedules: () -> Unit) {
    val context = LocalContext.current
    val viewModel = koinViewModel<ReceiverViewModel>()
    val uiState = viewModel.uiState

    // Handle toast messages
    uiState.lastOperationMessage?.let { message ->
        LaunchedEffect(message) {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            // Clear message after showing
            viewModel.clearError() // Assuming this clears operation messages too
        }
    }

    // Handle error messages
    uiState.errorMessage?.let { message ->
        LaunchedEffect(message) {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    Column {
        TopRow(
            title = "Receiver",
            switchToText = "Switch to Sender",
            onNavigate = navigateToSender
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(750.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            IPTextField(
                ipAddress = uiState.serverData.ipAddress,
                onValueChange = {
                    viewModel.updateServerIpAddress(it)
                }
            )

            MacAddressTextField(uiState.serverData.macAddress) {
                viewModel.updateMacAddress(it)
            }

            PortTextField(
                port = uiState.serverData.sshPort.toString(),
                label = "SSH port",
                onValueChange = { portString ->
                    val portInt = portString.toIntOrNull()
                    if (portInt != null && portInt in 1..65535) {
                        viewModel.updateSshPort(portInt)
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
                    value = uiState.serverData.username,
                    onValueChange = {
                        viewModel.updateUsername(it)
                    }
                )

                PasswordInputField(
                    password = uiState.serverData.password,
                    onPasswordChanged = {
                        viewModel.updatePassword(it)
                    },
                )
            }

            Button(modifier = Modifier
                .width(350.dp)
                .height(100.dp)
                .padding(all = 20.dp),
                enabled = !uiState.isKtorServerOperationInProgress,
                onClick = {
                    // Stop any running instance of KtorServerService
                    val stopIntent = Intent(context, KtorServerService::class.java)
                    context.stopService(stopIntent)

                    // Start a new instance of KtorServerService
                    val intent = Intent(context, KtorServerService::class.java)
                    context.startService(intent)

                    viewModel.startKtorServer()
                }
            ) {
                Text(
                    if (uiState.isKtorServerOperationInProgress) 
                        "Starting..." 
                    else 
                        "Start listening for remote requests"
                )
            }

            Button(modifier = Modifier
                .height(50.dp),
                onClick = {
                    navigateToSchedules()
                }) {
                Text("Schedules")
            }

            Spacer(modifier = Modifier.height(100.dp))

            Text(
                text = "Only works on local network!",
            )

            Button(modifier = Modifier
                .width(250.dp)
                .height(85.dp)
                .padding(all = 20.dp),
                enabled = !uiState.isTestWakeOnLanInProgress,
                onClick = {
                    viewModel.sendTestWakeOnLanPacket()
                }
            ) {
                Text(if (uiState.isTestWakeOnLanInProgress) "Sending..." else "Send magic packet")
            }
        }
    }
}