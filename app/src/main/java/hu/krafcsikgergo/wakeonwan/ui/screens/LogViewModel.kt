package hu.krafcsikgergo.wakeonwan.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.krafcsikgergo.wakeonwan.services.LogEntry
import hu.krafcsikgergo.wakeonwan.services.LogManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ViewModel for the Logs screen that manages log display and filtering.
 * Collects logs from LogManager and provides UI state management.
 */
class LogViewModel(
    private val logManager: LogManager
) : ViewModel() {

    // UI State
    var uiState by mutableStateOf(LogUiState())
        private set

    init {
        // Collect logs from LogManager
        viewModelScope.launch {
            logManager.logs.collectLatest { logs ->
                uiState = uiState.copy(logs = logs)
            }
        }
    }

    /**
     * Clears all logs from memory and UI.
     */
    fun clearLogs() {
        logManager.clearLogs()
        uiState = uiState.copy(logs = emptyList())
    }

    /**
     * Filters logs by search query.
     */
    fun filterLogs(query: String) {
        uiState = uiState.copy(searchQuery = query)
    }

    /**
     * Filters logs by log level.
     */
    fun filterByLevel(level: String?) {
        uiState = uiState.copy(selectedLevel = level)
    }
}

/**
 * UI state for the Logs screen.
 */
data class LogUiState(
    val logs: List<LogEntry> = emptyList(),
    val searchQuery: String = "",
    val selectedLevel: String? = null
)