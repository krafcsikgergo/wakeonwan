package hu.krafcsikgergo.wakeonwan.composables

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IPTextField(ipAddress: String, onValueChange: (String) -> Unit) {

    var isError by remember { mutableStateOf(false) }

    TextField(
        modifier = Modifier
            .width(200.dp),
        value = ipAddress,
        label = { Text("IP address") },
        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
        isError = isError,
        onValueChange = {
            isError = !isValidIPv4(it)
            onValueChange(it)
        }
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