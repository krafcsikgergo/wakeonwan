package hu.krafcsikgergo.wakeonwan.ui.composables

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
fun IPTextField(
    ipAddress: String, 
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    var isError by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = ipAddress,
        onValueChange = {
            isError = !isValidIPv4(it)
            onValueChange(it)
        },
        label = { Text("IP Address", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
        placeholder = { Text("e.g., 192.168.1.100") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        isError = isError,
        modifier = modifier
    )
}

fun isValidIPv4(ip: String): Boolean {
    val parts = ip.split(".")
    if (parts.size != 4) return false

    for (part in parts) {
        val num = part.toIntOrNull()
        if (num == null || num < 0 || num > 255) {
            return false
        }
    }

    return true
}