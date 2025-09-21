package hu.krafcsikgergo.wakeonwan.ui.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TopRow(
    title: String,
    switchToText: String,
    onNavigate: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(100.dp)
            .fillMaxWidth()
            .padding(top = 32.dp, bottom = 8.dp, start = 20.dp, end = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium
        )
        
        Row(
            modifier = Modifier.clickable { onNavigate() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = "Switch to $switchToText",
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = switchToText,
                fontSize = 16.sp
            )
        }
    }
}