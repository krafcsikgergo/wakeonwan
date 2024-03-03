package hu.krafcsikgergo.wakeonwan.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun topRow(isSender: Boolean = true, onSwitch: () -> Unit) {

    Row(
        modifier = Modifier
            .height(100.dp)
            .fillMaxWidth()
            .padding(20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Spacer(modifier = Modifier.size(0.dp))
        Text(
            text = if (isSender) "Sender" else "Receiver",
            fontSize = 20.sp
        )
        Switch(
            checked = isSender,
            onCheckedChange = {
                onSwitch()
            }
        )
    }
}