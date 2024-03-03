package hu.krafcsikgergo.wakeonwan.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PasswordInputField(
    password: String,
    onPasswordChanged: (String) -> Unit,
) {
    var isPasswordVisible by remember { mutableStateOf(false) }

    TextField(
        value = password,
        onValueChange = {
            onPasswordChanged(it)
        },
        label = {
            Text("Password")
        },
        keyboardOptions = KeyboardOptions.Default.copy(
            keyboardType = KeyboardType.Password
        ),
        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            PasswordVisibilityToggle(
                isPasswordVisible = isPasswordVisible,
                onToggle = {
                    isPasswordVisible = it
                }
            )
        },
        singleLine = true,
        modifier = Modifier
            .width(200.dp)
            .padding(20.dp)
    )
}

@Composable
fun PasswordVisibilityToggle(
    isPasswordVisible: Boolean,
    onToggle: (Boolean) -> Unit
) {
    IconButton(
        onClick = { onToggle(!isPasswordVisible) }
    ) {
        Icon(
            imageVector = if (isPasswordVisible) Icons.Default.Lock else Icons.Default.Lock,
            contentDescription = if (isPasswordVisible) "Hide Password" else "Show Password",
            tint = Color.Gray
        )
    }
}