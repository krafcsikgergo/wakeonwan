package hu.krafcsikgergo.wakeonwan.common.ui.composables

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TopRow(
    title: String,
    switchToText: String,
    onNavigate: () -> Unit,
    onNavigateToLogs: (() -> Unit)? = null,
    icon: ImageVector = Icons.Default.SwapHoriz
) {
    var clickCount by remember { mutableIntStateOf(0) }
    var lastClickTime by remember { mutableLongStateOf(0L) }
    val tripleClickThreshold = 1000L

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
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastClickTime > tripleClickThreshold) {
                    // Reset click count if too much time has passed
                    clickCount = 1
                } else {
                    clickCount++
                }
                lastClickTime = currentTime

                if (clickCount == 3) {
                    // Triple click detected
                    clickCount = 0 // Reset for next triple click
                    onNavigateToLogs?.invoke()
                } else {
                    // Reset click count after delay if not triple click
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(tripleClickThreshold)
                        if (clickCount < 3) {
                            clickCount = 0
                        }
                    }
                }
            }
        )

        Row(
            modifier = Modifier.clickable { onNavigate() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
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
