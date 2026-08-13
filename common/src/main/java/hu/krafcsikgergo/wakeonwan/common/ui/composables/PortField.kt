package hu.krafcsikgergo.wakeonwan.common.ui.composables

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun PortTextField(
    port: String,
    label: String = "Port",
    modifier: Modifier = Modifier.fillMaxWidth(),
    onValueChange: (String) -> Unit
) {
    var isError by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = port,
        onValueChange = { newValue ->
            // Always update the UI state
            onValueChange(newValue)

            // Update error state based on validation
            isError = if (newValue.isEmpty()) {
                false // Empty is allowed (user is typing)
            } else {
                !isValidPort(newValue)
            }
        },
        label = { Text(label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
        placeholder = { Text("e.g., 9753") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        isError = isError,
        modifier = modifier
    )
}

fun isValidPort(port: String): Boolean {
    val portNumber = port.toIntOrNull()
    return portNumber in 1..65535
}
