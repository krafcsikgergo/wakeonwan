package hu.krafcsikgergo.wakeonwan.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.krafcsikgergo.wakeonwan.services.DataStoreManager
import hu.krafcsikgergo.wakeonwan.services.receiver.Schedule
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * ViewModel for the Schedules screen that manages scheduled operations.
 * Handles creation, editing, and deletion of wake/sleep schedules.
 */
class SchedulesViewModel(
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    // UI State
    var uiState by mutableStateOf(SchedulesUiState())
        private set

    init {
        loadSchedules()
    }

    /**
     * Loads all schedules from the configuration.
     */
    fun loadSchedules() {
        viewModelScope.launch {
            try {
                uiState = uiState.copy(isLoading = true, errorMessage = null)
                val schedules = dataStoreManager.getSchedules()
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
        val epochMillis = time.toSecondOfDay() * 1000L
        viewModelScope.launch {
            try {
                val newId = generateNewScheduleId()
                val newSchedule = Schedule(
                    id = newId,
                    time = epochMillis,
                    turnOn = turnOn,
                    days = days
                )

                dataStoreManager.saveSchedule(newSchedule)

                // Update local state
                uiState = uiState.copy(
                    schedules = uiState.schedules + newSchedule,
                    lastOperationMessage = "Schedule created successfully",
                    errorMessage = null
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
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
        val epochMillis = time.toSecondOfDay() * 1000L
        viewModelScope.launch {
            try {
                val updatedSchedule = Schedule(
                    id = scheduleId,
                    time = epochMillis,
                    turnOn = turnOn,
                    days = days
                )

                dataStoreManager.removeSchedule(scheduleId)
                dataStoreManager.saveSchedule(updatedSchedule)

                // Update local state
                uiState = uiState.copy(
                    schedules = uiState.schedules.map {
                        if (it.id == scheduleId) updatedSchedule else it
                    },
                    lastOperationMessage = "Schedule updated successfully",
                    errorMessage = null
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
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
                dataStoreManager.removeSchedule(scheduleId)

                // Update local state
                uiState = uiState.copy(
                    schedules = uiState.schedules.filter { it.id != scheduleId },
                    lastOperationMessage = "Schedule deleted successfully",
                    errorMessage = null
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    errorMessage = "Failed to delete schedule: ${e.message}"
                )
            }
        }
    }

    /**
     * Toggles a schedule between enabled and disabled.
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
     * Generates a new unique ID for a schedule.
     */
    private fun generateNewScheduleId(): Int {
        return (uiState.schedules.maxOfOrNull { it.id } ?: 0) + 1
    }
}

/**
 * UI state for the Schedules screen.
 */
data class SchedulesUiState(
    val schedules: List<Schedule> = emptyList(),
    val isLoading: Boolean = false,
    val lastOperationMessage: String? = null,
    val errorMessage: String? = null
)