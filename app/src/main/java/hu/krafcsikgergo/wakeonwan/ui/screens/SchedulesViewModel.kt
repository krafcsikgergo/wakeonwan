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
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * ViewModel for the Schedules screen that manages scheduled operations.
 * Handles creation, editing, and deletion of wake/sleep schedules.
 */
class SchedulesViewModel(
    private val dataStoreManager: DataStoreManager,
    private val scheduleManager: ScheduleManager,
    private val context: Context,
    private val logManager: LogManager
) : ViewModel() {

    // UI State
    var uiState by mutableStateOf(SchedulesUiState())
        private set

    init {
        loadSchedules()
        checkAlarmPermissionStatus()
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
     */
    fun loadSchedules() {
        viewModelScope.launch {
            try {
                uiState = uiState.copy(isLoading = true, errorMessage = null)
                val schedules = scheduleManager.getAllSchedules()
                uiState = uiState.copy(
                    schedules = schedules,
                    isLoading = false
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = "Failed to load schedules: ${e.message}"
                )
            }
        }
    }

    /**
     * Creates a new schedule.
     */
    fun createSchedule(
        time: LocalTime,
        turnOn: Boolean,
        days: List<Boolean>
    ) {
        viewModelScope.launch {
            try {
                uiState = uiState.copy(isLoading = true, errorMessage = null)
                
                // Create schedule via ScheduleManager (includes validation and DataStore persistence)
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
                    },
                    onFailure = { error ->
                        uiState = uiState.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Failed to create schedule"
                        )
                    }
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = "Failed to create schedule: ${e.message}"
                )
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
     */
    fun deleteSchedule(scheduleId: Int) {
        viewModelScope.launch {
            try {
                uiState = uiState.copy(isLoading = true, errorMessage = null)
                
                // Delete schedule via ScheduleManager (includes validation and DataStore persistence)
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
                    },
                    onFailure = { error ->
                        uiState = uiState.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Failed to delete schedule"
                        )
                    }
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = "Failed to delete schedule: ${e.message}"
                )
            }
        }
    }

    /**
     * Toggles a schedule between enabled and disabled.
     */
    fun toggleScheduleEnabled(scheduleId: Int) {
        viewModelScope.launch {
            try {
                val schedule = uiState.schedules.find { it.id == scheduleId }
                if (schedule != null) {
                    uiState = uiState.copy(isLoading = true, errorMessage = null)
                    
                    // Toggle the enabled state
                    val result = scheduleManager.updateSchedule(
                        scheduleId = schedule.id,
                        time = schedule.timeInLocalTime,
                        turnOn = schedule.turnOn,
                        days = schedule.days,
                        enabled = !schedule.enabled
                    )
                    
                    result.fold(
                        onSuccess = { updatedSchedule ->
                            if (updatedSchedule.enabled) {
                                // Schedule alarms for the enabled schedule
                                scheduleManager.scheduleAlarms(context, listOf(updatedSchedule))
                            } else {
                                // Cancel alarms for the disabled schedule
                                scheduleManager.cancelAlarm(context, scheduleId)
                            }
                            
                            // Update local state
                            uiState = uiState.copy(
                                schedules = uiState.schedules.map {
                                    if (it.id == scheduleId) updatedSchedule else it
                                },
                                lastOperationMessage = if (updatedSchedule.enabled) 
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
                    uiState = uiState.copy(
                        errorMessage = "Schedule not found"
                    )
                }
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = "Failed to toggle schedule: ${e.message}"
                )
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
    val permissionWarning: String? = null
)