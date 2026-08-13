package hu.krafcsikgergo.wakeonwan.sender.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.krafcsikgergo.wakeonwan.common.model.Schedule
import hu.krafcsikgergo.wakeonwan.common.services.LogManager
import hu.krafcsikgergo.wakeonwan.sender.services.DataStoreManager
import hu.krafcsikgergo.wakeonwan.sender.services.KtorServerData
import hu.krafcsikgergo.wakeonwan.sender.services.NetworkRepository
import hu.krafcsikgergo.wakeonwan.sender.services.PairingTokenStore
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * ViewModel for the Schedules screen on the sender device.
 * Manages wake/sleep schedules on a remote receiver over HTTP.
 */
class SchedulesViewModel(
    private val dataStoreManager: DataStoreManager,
    private val networkRepository: NetworkRepository,
    private val pairingTokenStore: PairingTokenStore,
    private val logManager: LogManager
) : ViewModel() {

    // UI State
    var uiState by mutableStateOf(SchedulesUiState())
        private set

    /**
     * Initializes the ViewModel with the target server. Must be called before use.
     */
    fun initialize(serverId: String) {
        viewModelScope.launch {
            val servers = dataStoreManager.getKtorServers()
            val server = servers.find { it.id == serverId }

            if (server == null) {
                uiState = uiState.copy(errorMessage = "Server not found")
                return@launch
            }

            uiState = uiState.copy(
                serverName = server.name,
                targetServer = server
            )
            logManager.d("SchedulesViewModel", "Initialized for server: ${server.name}")

            loadSchedules()
        }
    }

    /**
     * Helper function to get the server's base URL.
     * Returns null if server is not configured and updates error state.
     */
    private fun getServerBaseUrl(): String? {
        val server = uiState.targetServer
        if (server == null) {
            uiState = uiState.copy(
                isLoading = false,
                errorMessage = "Server configuration not found"
            )
            return null
        }
        return "http://${server.ipAddress}:${server.port}"
    }

    /**
     * Helper function to execute server operations with consistent error handling.
     */
    private suspend fun <T> executeServerOperation(
        operationName: String,
        operation: suspend (String, String?) -> Result<T>,
        onSuccess: (T) -> SchedulesUiState
    ): Boolean {
        val baseUrl = getServerBaseUrl() ?: return false
        val token = uiState.targetServer?.let { pairingTokenStore.getToken(it.id) }

        val result = operation(baseUrl, token)
        result.fold(
            onSuccess = { data ->
                uiState = onSuccess(data)
                logManager.d("SchedulesViewModel", "Successfully completed: $operationName")
            },
            onFailure = { error ->
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = "Failed to $operationName: ${error.message}"
                )
                logManager.e("SchedulesViewModel", "Failed to $operationName: ${error.message}")
            }
        )
        return result.isSuccess
    }

    /**
     * Loads all schedules from the remote server.
     */
    fun loadSchedules() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)
            executeServerOperation(
                operationName = "load schedules",
                operation = { baseUrl, token -> networkRepository.getSchedules(baseUrl, token) },
                onSuccess = { scheduleList ->
                    uiState.copy(
                        schedules = scheduleList,
                        isLoading = false
                    )
                }
            )
        }
    }

    /**
     * Creates a new schedule on the remote server.
     */
    fun createSchedule(
        time: LocalTime,
        turnOn: Boolean,
        days: List<Boolean>
    ) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)

            val timeInSeconds = time.toSecondOfDay().toLong()
            val schedule = Schedule(
                id = 0, // Server will assign actual ID
                time = timeInSeconds,
                turnOn = turnOn,
                days = days,
                enabled = true
            )

            executeServerOperation(
                operationName = "create schedule",
                operation = { baseUrl, token -> networkRepository.createSchedule(baseUrl, schedule, token) },
                onSuccess = { createdSchedule ->
                    uiState.copy(
                        schedules = uiState.schedules + createdSchedule,
                        lastOperationMessage = "Schedule created on server successfully",
                        errorMessage = null,
                        isLoading = false
                    )
                }
            )
        }
    }

    /**
     * Deletes a schedule from the remote server.
     */
    fun deleteSchedule(scheduleId: Int) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)
            executeServerOperation(
                operationName = "delete schedule",
                operation = { baseUrl, token -> networkRepository.deleteSchedule(baseUrl, scheduleId, token) },
                onSuccess = {
                    uiState.copy(
                        schedules = uiState.schedules.filter { it.id != scheduleId },
                        lastOperationMessage = "Schedule deleted from server successfully",
                        errorMessage = null,
                        isLoading = false
                    )
                }
            )
        }
    }

    /**
     * Toggles a schedule between enabled and disabled on the remote server.
     */
    fun toggleScheduleEnabled(scheduleId: Int) {
        viewModelScope.launch {
            val schedule = uiState.schedules.find { it.id == scheduleId }
            if (schedule == null) {
                uiState = uiState.copy(errorMessage = "Schedule not found")
                return@launch
            }

            uiState = uiState.copy(isLoading = true, errorMessage = null)

            val updatedSchedule = schedule.copy(enabled = !schedule.enabled)

            executeServerOperation(
                operationName = "toggle schedule",
                operation = { baseUrl, token ->
                    networkRepository.updateSchedule(baseUrl, scheduleId, updatedSchedule, token)
                },
                onSuccess = { resultSchedule ->
                    uiState.copy(
                        schedules = uiState.schedules.map {
                            if (it.id == scheduleId) resultSchedule else it
                        },
                        lastOperationMessage = if (resultSchedule.enabled)
                            "Schedule enabled on server"
                        else
                            "Schedule disabled on server",
                        errorMessage = null,
                        isLoading = false
                    )
                }
            )
        }
    }

    /**
     * Clears any error messages.
     */
    fun clearError() {
        uiState = uiState.copy(errorMessage = null)
    }

    /**
     * Clears the last operation message.
     */
    fun clearOperationMessage() {
        uiState = uiState.copy(lastOperationMessage = null)
    }
}

/**
 * UI state for the Schedules screen.
 */
data class SchedulesUiState(
    val schedules: List<Schedule> = emptyList(),
    val isLoading: Boolean = false,
    val lastOperationMessage: String? = null,
    val errorMessage: String? = null,
    val serverName: String? = null,
    val targetServer: KtorServerData? = null
)
