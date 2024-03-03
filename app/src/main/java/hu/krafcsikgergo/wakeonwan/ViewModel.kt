package hu.krafcsikgergo.wakeonwan

import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class WANViewModel : ViewModel() {
    private val _appState = MutableStateFlow(AppState())
    val appState: StateFlow<AppState>
        get() = _appState.asStateFlow()

    sealed class WANEvent {
        object ToggleSenderReceiver : WANEvent()
    }

    fun onEvent(event: WANEvent) {
        when (event) {
            is WANEvent.ToggleSenderReceiver -> {
                _appState.update { AppState(!appState.value.state) }
                Log.d("Alma", "current state: ${_appState.value.state}")
            }
        }
    }

}

class AppState(
    val isSender: Boolean = true
) {
    var state by mutableStateOf(isSender)
}