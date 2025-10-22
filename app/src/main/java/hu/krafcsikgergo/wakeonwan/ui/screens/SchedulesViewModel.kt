package hu.krafcsikgergo.wakeonwan.ui.screens

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.krafcsikgergo.wakeonwan.services.DataStoreManager
import hu.krafcsikgergo.wakeonwan.services.LogManager
import hu.krafcsikgergo.wakeonwan.services.receiver.Schedule
import hu.krafcsikgergo.wakeonwan.services.receiver.ScheduleManager
import hu.krafcsikgergo.wakeonwan.services.sender.KtorServerData
import hu.krafcsikgergo.wakeonwan.services.sender.NetworkRepository
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * ViewModel for the Schedules screen that manages scheduled operations.
 * Supports two modes:
 * - Receiver mode: Manages local schedules using ScheduleManager
 * - Sender mode: Manages remote schedules via HTTP using NetworkRepository
 */
class SchedulesViewModel(
    private val dataStoreManager: DataStoreManager,
    private val scheduleManager: ScheduleManager,
    private val networkRepository: NetworkRepository,
    private val context: Context,
    private val logManager: LogManager
) : ViewModel() {

    // UI State
    var uiState by mutableStateOf(SchedulesUiState())
        private set

    /**
     * Initializes the ViewModel with the specified mode and optional server ID.
     * Must be called before using the ViewModel.
     */
    fun initialize(mode: String, serverId: String?) {
        viewModelScope.launch {
            if (mode == "sender" && serverId != null) {
                // Load server configuration
                val servers = dataStoreManager.getKtorServers()
                val server = servers.find { it.id == serverId }
                
                if (server == null) {
                    uiState = uiState.copy(
                        errorMessage = "Server not found",
                        mode = mode
                    )
                    return@launch
                }
                
                uiState = uiState.copy(
                    mode = mode,
                    serverName = server.name,
                    targetServer = server
                )
                logManager.d("SchedulesViewModel", "Initialized in sender mode for server: ${server.name}")
            } else {
                uiState = uiState.copy(mode = mode)
                checkAlarmPermissionStatus()
                logManager.d("SchedulesViewModel", "Initialized in receiver mode")
            }
            
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
     * @param operationName A human-readable name for the operation (e.g., "load schedules")
     * @param operation The suspend function that performs the HTTP operation
     * @param onSuccess A function that creates the new UI state from the operation result
     * @return true if operation succeeded, false otherwise
     */
    private suspend fun <T> executeServerOperation(
        operationName: String,
        operation: suspend (String) -> Result<T>,
        onSuccess: (T) -> SchedulesUiState
    ): Boolean {
        val baseUrl = getServerBaseUrl() ?: return false
        
        val result = operation(baseUrl)
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
     * Checks if the app can schedule exact alarms and updates UI state accordingly
     */
    private fun checkAlarmPermissionStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val canScheduleExact = alarmManager.canScheduleExactAlarms()
            
            uiState = uiState.copy(hasExactAlarmPermission = canScheduleExact)
            
            if (!canScheduleExact) {
                logManager.w("SchedulesViewModel", "SCHEDULE_EXACT_ALARM permission not granted")
                uiState = uiState.copy(
                    permissionWarning = "For precise scheduling, please grant 'Alarms & reminders' permission in Settings"
                )
            } else {
                logManager.d("SchedulesViewModel", "SCHEDULE_EXACT_ALARM permission granted")
            }
        } else {
            // Pre-Android 12 doesn't need permission
            uiState = uiState.copy(hasExactAlarmPermission = true)
        }
    }

    /**
     * Opens system settings to request exact alarm permission
     */
    fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                logManager.d("SchedulesViewModel", "Opened exact alarm permission settings")
            } catch (e: Exception) {
                logManager.e("SchedulesViewModel", "Failed to open exact alarm permission settings", e)
                uiState = uiState.copy(
                    errorMessage = "Failed to open permission settings. Please go to Settings > Apps > Special app access > Alarms & reminders manually."
                )
            }
        }
    }

    /**
     * Called when returning from permission settings to refresh permission status
     */
    fun onPermissionSettingsReturn() {
        checkAlarmPermissionStatus()
        if (uiState.hasExactAlarmPermission) {
            uiState = uiState.copy(
                lastOperationMessage = "Exact alarm permission granted! Schedules will now be more precise.",
                permissionWarning = null
            )
            // Re-schedule all alarms with exact timing if permission was granted
            rescheduleAllAlarms()
        }
    }

    /**
     * Re-schedules all existing alarms (useful after permission change)
     */
    private fun rescheduleAllAlarms() {
        viewModelScope.launch {
            try {
                val schedules = scheduleManager.getAllSchedules()
                if (schedules.isNotEmpty()) {
                    scheduleManager.scheduleAlarms(context, schedules)
                    logManager.d("SchedulesViewModel", "Re-scheduled ${schedules.size} alarms after permission change")
                }
            } catch (e: Exception) {
                logManager.e("SchedulesViewModel", "Failed to reschedule alarms", e)
            }
        }
    }

    /**
     * Loads all schedules from the configuration.
     * Uses local ScheduleManager in receiver mode or NetworkRepository in sender mode.
     */
    fun loadSchedules() {
        viewModelScope.launch {
            try {
                uiState = uiState.copy(isLoading = true, errorMessage = null)
                
                if (uiState.mode == "receiver") {
                    // Receiver mode: Load from local ScheduleManager
                    val schedules = scheduleManager.getAllSchedules()
                    // Create a new list instance to ensure Compose detects the change
                    uiState = uiState.copy(
                        schedules = schedules.toList(),
                        isLoading = false
                    )
                    logManager.d("SchedulesViewModel", "Loaded ${schedules.size} schedules in receiver mode")
                } else {
                    // Sender mode: Load from remote server via HTTP
                    executeServerOperation(
                        operationName = "load schedules",
                        operation = { baseUrl -> networkRepository.getSchedules(baseUrl) },
                        onSuccess = { scheduleList ->
                            uiState.copy(
                                schedules = scheduleList,
                                isLoading = false
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = "Failed to load schedules: ${e.message}"
                )
                logManager.e("SchedulesViewModel", "Error loading schedules", e)
            }
        }
    }

    /**
     * Creates a new schedule.
     * Uses local ScheduleManager in receiver mode or NetworkRepository in sender mode.
     */
    fun createSchedule(
        time: LocalTime,
        turnOn: Boolean,
        days: List<Boolean>
    ) {
        viewModelScope.launch {
            try {
                uiState = uiState.copy(isLoading = true, errorMessage = null)
                
                if (uiState.mode == "receiver") {
                    // Receiver mode: Create locally with ScheduleManager
                    val result = scheduleManager.createSchedule(time, turnOn, days, enabled = true)
                    
                    result.fold(
                        onSuccess = { newSchedule ->
                            // Schedule alarms for the new schedule
                            scheduleManager.scheduleAlarms(context, listOf(newSchedule))
                            
                            // Update local state
                            uiState = uiState.copy(
                                schedules = uiState.schedules + newSchedule,
                                lastOperationMessage = "Schedule created and alarm set successfully",
                                errorMessage = null,
                                isLoading = false
                            )
                            logManager.d("SchedulesViewModel", "Created schedule ${newSchedule.id} locally")
                        },
                        onFailure = { error ->
                            uiState = uiState.copy(
                                isLoading = false,
                                errorMessage = error.message ?: "Failed to create schedule"
                            )
                            logManager.e("SchedulesViewModel", "Failed to create schedule locally: ${error.message}")
                        }
                    )
                } else {
                    // Sender mode: Create on remote server via HTTP
                    // Create a schedule object to send
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
                        operation = { baseUrl -> networkRepository.createSchedule(baseUrl, schedule) },
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
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = "Failed to create schedule: ${e.message}"
                )
                logManager.e("SchedulesViewModel", "Error creating schedule", e)
            }
        }
    }

    /**
     * Updates an existing schedule.
     */
    fun updateSchedule(
        scheduleId: Int,
        time: LocalTime,
        turnOn: Boolean,
        days: List<Boolean>
    ) {
        viewModelScope.launch {
            try {
                val existingSchedule = uiState.schedules.find { it.id == scheduleId }
                if (existingSchedule == null) {
                    uiState = uiState.copy(
                        errorMessage = "Schedule not found"
                    )
                    return@launch
                }
                
                uiState = uiState.copy(isLoading = true, errorMessage = null)
                
                // Update schedule via ScheduleManager (includes validation and DataStore persistence)
                val result = scheduleManager.updateSchedule(scheduleId, time, turnOn, days, existingSchedule.enabled)
                
                result.fold(
                    onSuccess = { updatedSchedule ->
                        // Cancel old alarms and schedule new ones if enabled
                        scheduleManager.cancelAlarm(context, scheduleId)
                        if (updatedSchedule.enabled) {
                            scheduleManager.scheduleAlarms(context, listOf(updatedSchedule))
                        }
                        
                        // Update local state
                        uiState = uiState.copy(
                            schedules = uiState.schedules.map {
                                if (it.id == scheduleId) updatedSchedule else it
                            },
                            lastOperationMessage = "Schedule updated successfully",
                            errorMessage = null,
                            isLoading = false
                        )
                    },
                    onFailure = { error ->
                        uiState = uiState.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Failed to update schedule"
                        )
                    }
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = "Failed to update schedule: ${e.message}"
                )
            }
        }
    }

    /**
     * Deletes a schedule by ID.
     * Uses local ScheduleManager in receiver mode or NetworkRepository in sender mode.
     */
    fun deleteSchedule(scheduleId: Int) {
        viewModelScope.launch {
            try {
                uiState = uiState.copy(isLoading = true, errorMessage = null)
                
                if (uiState.mode == "receiver") {
                    // Receiver mode: Delete locally with ScheduleManager
                    val result = scheduleManager.deleteSchedule(scheduleId)
                    
                    result.fold(
                        onSuccess = {
                            // Cancel the alarms
                            scheduleManager.cancelAlarm(context, scheduleId)
                            
                            // Update local state
                            uiState = uiState.copy(
                                schedules = uiState.schedules.filter { it.id != scheduleId },
                                lastOperationMessage = "Schedule deleted and alarm cancelled successfully",
                                errorMessage = null,
                                isLoading = false
                            )
                            logManager.d("SchedulesViewModel", "Deleted schedule $scheduleId locally")
                        },
                        onFailure = { error ->
                            uiState = uiState.copy(
                                isLoading = false,
                                errorMessage = error.message ?: "Failed to delete schedule"
                            )
                            logManager.e("SchedulesViewModel", "Failed to delete schedule locally: ${error.message}")
                        }
                    )
                } else {
                    // Sender mode: Delete on remote server via HTTP
                    executeServerOperation(
                        operationName = "delete schedule",
                        operation = { baseUrl -> networkRepository.deleteSchedule(baseUrl, scheduleId) },
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
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = "Failed to delete schedule: ${e.message}"
                )
                logManager.e("SchedulesViewModel", "Error deleting schedule", e)
            }
        }
    }

    /**
     * Toggles a schedule between enabled and disabled.
     * Uses local ScheduleManager in receiver mode or NetworkRepository in sender mode.
     */
    fun toggleScheduleEnabled(scheduleId: Int) {
        viewModelScope.launch {
            try {
                val schedule = uiState.schedules.find { it.id == scheduleId }
                if (schedule == null) {
                    uiState = uiState.copy(errorMessage = "Schedule not found")
                    return@launch
                }
                
                uiState = uiState.copy(isLoading = true, errorMessage = null)
                
                // Create updated schedule with toggled enabled state
                val updatedSchedule = schedule.copy(enabled = !schedule.enabled)
                
                if (uiState.mode == "receiver") {
                    // Receiver mode: Update locally with ScheduleManager
                    val result = scheduleManager.updateSchedule(
                        scheduleId = schedule.id,
                        time = schedule.timeInLocalTime,
                        turnOn = schedule.turnOn,
                        days = schedule.days,
                        enabled = updatedSchedule.enabled
                    )
                    
                    result.fold(
                        onSuccess = { newSchedule ->
                            if (newSchedule.enabled) {
                                // Schedule alarms for the enabled schedule
                                scheduleManager.scheduleAlarms(context, listOf(newSchedule))
                            } else {
                                // Cancel alarms for the disabled schedule
                                scheduleManager.cancelAlarm(context, scheduleId)
                            }
                            
                            // Update local state
                            uiState = uiState.copy(
                                schedules = uiState.schedules.map {
                                    if (it.id == scheduleId) newSchedule else it
                                },
                                lastOperationMessage = if (newSchedule.enabled) 
                                    "Schedule enabled and alarms set" 
                                else 
                                    "Schedule disabled and alarms cancelled",
                                errorMessage = null,
                                isLoading = false
                            )
                        },
                        onFailure = { error ->
                            uiState = uiState.copy(
                                isLoading = false,
                                errorMessage = error.message ?: "Failed to toggle schedule"
                            )
                        }
                    )
                } else {
                    // Sender mode: Update on remote server via HTTP
                    executeServerOperation(
                        operationName = "toggle schedule",
                        operation = { baseUrl -> 
                            networkRepository.updateSchedule(baseUrl, scheduleId, updatedSchedule) 
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
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = "Failed to toggle schedule: ${e.message}"
                )
                logManager.e("SchedulesViewModel", "Error toggling schedule", e)
            }
        }
    }

    /**
     * Legacy toggle method - toggles turnOn property instead of enabled
     */
    fun toggleSchedule(scheduleId: Int) {
        val schedule = uiState.schedules.find { it.id == scheduleId }
        if (schedule != null) {
            // For now, just update the turnOn property as a toggle
            // In a real implementation, you might have an 'enabled' property
            updateSchedule(
                scheduleId = schedule.id,
                time = schedule.timeInLocalTime,
                turnOn = !schedule.turnOn,
                days = schedule.days
            )
        }
    }

    /**
     * Gets schedules filtered by day of week (0 = Monday, 6 = Sunday).
     */
    fun getSchedulesForDay(dayOfWeek: Int): List<Schedule> {
        return uiState.schedules.filter { schedule ->
            dayOfWeek < schedule.days.size && schedule.days[dayOfWeek]
        }
    }

    /**
     * Gets wake-up schedules only.
     */
    fun getWakeUpSchedules(): List<Schedule> {
        return uiState.schedules.filter { it.turnOn }
    }

    /**
     * Gets shutdown schedules only.
     */
    fun getShutdownSchedules(): List<Schedule> {
        return uiState.schedules.filter { !it.turnOn }
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

    /**
     * Clears the permission warning.
     */
    fun clearPermissionWarning() {
        uiState = uiState.copy(permissionWarning = null)
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
    val hasExactAlarmPermission: Boolean = true,
    val permissionWarning: String? = null,
    val mode: String = "receiver", // "receiver" or "sender"
    val serverName: String? = null, // Server name when in sender mode
    val targetServer: KtorServerData? = null // Target server configuration for sender mode
)