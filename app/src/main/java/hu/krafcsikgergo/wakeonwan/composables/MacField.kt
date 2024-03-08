package hu.krafcsikgergo.wakeonwan.composables

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
fun MacAddressTextField(macAddress: String, onValueChange: (String) -> Unit) {

    var isError by remember { mutableStateOf(false) }

    TextField(
        modifier = Modifier
            .padding(all = 20.dp)
            .width(200.dp),
        value = macAddress,
        label = { Text("MAC address") },
        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Password),
        isError = isError,
        onValueChange = {
            isError = !isValidMacAddress(it)
            onValueChange(it)
        }

    )
}

fun isValidMacAddress(mac: String): Boolean {
    val macRegex = "^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$".toRegex()
    return mac.matches(macRegex)
}