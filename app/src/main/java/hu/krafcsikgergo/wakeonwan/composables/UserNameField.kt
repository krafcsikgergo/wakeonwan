package hu.krafcsikgergo.wakeonwan.composables

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import java.util.regex.Pattern

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsernameInput(
    value: String,
    onValueChange: (String) -> Unit,
) {
    // Access context for Toast message
    val context = LocalContext.current

    var isErrorMessageDisplayed by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }
    // Display error message if the username is not valid
    if (isError && !isErrorMessageDisplayed) {
        Toast.makeText(context, "Username not correct!", Toast.LENGTH_SHORT).show()
        isErrorMessageDisplayed = true
    }

    // Clear error message flag when value changes
    if (!isError) {
        isErrorMessageDisplayed = false
    }

    TextField(
        modifier = Modifier
            .width(200.dp)
            .padding(20.dp),
        value = value,
        onValueChange = {
            // Check for valid characters as the user types
            if (isUsernameValid(it)) {
                onValueChange(it)
            }
        },
        label = { Text("Enter username") },
        isError = isError,
        keyboardOptions = KeyboardOptions.Default.copy(
            imeAction = ImeAction.Done
        ),
        singleLine = true
    )
}

// Function to validate Linux Ubuntu username
public fun isUsernameValid(username: String): Boolean {
    // Only allow lowercase letters, digits, hyphens, and underscores
    val pattern = Pattern.compile("^[a-z0-9_-]*$")
    return pattern.matcher(username).matches()
}