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
fun PortTextField(
    port: String,
    label: String,
    modifier: Modifier = Modifier
        .padding(all = 20.dp)
        .width(140.dp),
    onValueChange: (Int) -> Unit
) {
    var isError by remember { mutableStateOf(false) }

    TextField(
        modifier = modifier,
        value = port,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
        isError = isError,
        onValueChange = {
            if (isValidPort(it)) {
                isError = false
                onValueChange(it.toInt())
            } else {
                isError = true
            }
        }
    )
}

fun isValidPort(port: String): Boolean {
    val portNumber = port.toIntOrNull()
    return portNumber in 1..65535
}