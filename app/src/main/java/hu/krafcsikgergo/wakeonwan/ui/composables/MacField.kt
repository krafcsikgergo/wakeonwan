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
fun MacAddressTextField(
    macAddress: String, 
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    var isError by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = macAddress,
        onValueChange = {
            isError = !isValidMacAddress(it)
            onValueChange(it)
        },
        label = { Text("MAC Address", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
        placeholder = { Text("e.g., 00:1A:2B:3C:4D:5E") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        singleLine = true,
        isError = isError,
        modifier = modifier
    )
}

fun isValidMacAddress(mac: String): Boolean {
    val macRegex = "^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$".toRegex()
    return mac.matches(macRegex)
}