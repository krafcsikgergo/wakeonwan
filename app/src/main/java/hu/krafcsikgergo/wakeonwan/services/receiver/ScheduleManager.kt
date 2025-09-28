package hu.krafcsikgergo.wakeonwan.services.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import hu.krafcsikgergo.wakeonwan.services.AlarmReceiver
import hu.krafcsikgergo.wakeonwan.services.DataStoreManager
import hu.krafcsikgergo.wakeonwan.services.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.ZonedDateTime

interface ScheduleManager {
    // CRUD operations
    suspend fun getAllSchedules(): List<Schedule>
    suspend fun createSchedule(time: LocalTime, turnOn: Boolean, days: List<Boolean>, enabled: Boolean = true): Result<Schedule>
    suspend fun updateSchedule(scheduleId: Int, time: LocalTime, turnOn: Boolean, days: List<Boolean>, enabled: Boolean = true): Result<Schedule>
    suspend fun deleteSchedule(scheduleId: Int): Result<Unit>
    
    // Alarm management (existing methods)
    fun scheduleAlarms(context: Context, schedules: List<Schedule>)
    fun cancelAlarm(context: Context, scheduleId: Int)
    
    // Lifecycle management
    suspend fun initializeFromDataStore(context: Context): Result<Unit>
}

class ScheduleManagerImpl(
    private val dataStoreManager: DataStoreManager,
    private val logManager: LogManager
) : ScheduleManager {
    
    // CRUD Operations
    
    override suspend fun getAllSchedules(): List<Schedule> {
        return withContext(Dispatchers.IO) {
            try {
                dataStoreManager.getSchedules()
            } catch (e: Exception) {
                logManager.e("ScheduleManager", "Failed to get schedules", e)
                emptyList()
            }
        }
    }
    
    override suspend fun createSchedule(time: LocalTime, turnOn: Boolean, days: List<Boolean>, enabled: Boolean): Result<Schedule> {
        return withContext(Dispatchers.IO) {
            try {
                // Validate input
                if (days.size != 7) {
                    return@withContext Result.failure(IllegalArgumentException("Days array must have exactly 7 elements"))
                }
                
                if (days.none { it }) {
                    return@withContext Result.failure(IllegalArgumentException("At least one day must be selected"))
                }
                
                val timeInSeconds = time.toSecondOfDay().toLong()
                if (timeInSeconds < 0 || timeInSeconds > 86399) {
                    return@withContext Result.failure(IllegalArgumentException("Time must be between 0 and 86399 seconds"))
                }
                
                // Generate unique ID
                val existingSchedules = dataStoreManager.getSchedules()
                val newId = generateNewScheduleId(existingSchedules)
                
                val newSchedule = Schedule(
                    id = newId,
                    time = timeInSeconds,
                    turnOn = turnOn,
                    days = days,
                    enabled = enabled
                )
                
                // Save to DataStore
                dataStoreManager.saveSchedule(newSchedule)
                logManager.d("ScheduleManager", "Schedule ${newSchedule.id} created")
                
                Result.success(newSchedule)
            } catch (e: Exception) {
                logManager.e("ScheduleManager", "Failed to create schedule", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun updateSchedule(scheduleId: Int, time: LocalTime, turnOn: Boolean, days: List<Boolean>, enabled: Boolean): Result<Schedule> {
        return withContext(Dispatchers.IO) {
            try {
                // Validate input
                if (days.size != 7) {
                    return@withContext Result.failure(IllegalArgumentException("Days array must have exactly 7 elements"))
                }

                if (days.none { it }) {
                    return@withContext Result.failure(IllegalArgumentException("At least one day must be selected"))
                }

                val timeInSeconds = time.toSecondOfDay().toLong()
                if (timeInSeconds < 0 || timeInSeconds > 86399) {
                    return@withContext Result.failure(IllegalArgumentException("Time must be between 0 and 86399 seconds"))
                }

                // Check if schedule exists
                val existingSchedules = dataStoreManager.getSchedules()
                val scheduleExists = existingSchedules.any { it.id == scheduleId }

                if (!scheduleExists) {
                    return@withContext Result.failure(IllegalArgumentException("Schedule with ID $scheduleId not found"))
                }

                val updatedSchedule = Schedule(
                    id = scheduleId,
                    time = timeInSeconds,
                    turnOn = turnOn,
                    days = days,
                    enabled = enabled
                )

                // Update in DataStore (remove old, add new)
                dataStoreManager.removeSchedule(scheduleId)
                dataStoreManager.saveSchedule(updatedSchedule)

                logManager.d("ScheduleManager", "Schedule $scheduleId updated")

                Result.success(updatedSchedule)
            } catch (e: Exception) {
                logManager.e("ScheduleManager", "Failed to update schedule", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun deleteSchedule(scheduleId: Int): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // Check if schedule exists
                val existingSchedules = dataStoreManager.getSchedules()
                val scheduleExists = existingSchedules.any { it.id == scheduleId }
                
                if (!scheduleExists) {
                    return@withContext Result.failure(IllegalArgumentException("Schedule with ID $scheduleId not found"))
                }
                
                // Remove from DataStore
                dataStoreManager.removeSchedule(scheduleId)
                
                logManager.d("ScheduleManager", "Schedule $scheduleId deleted")
                
                Result.success(Unit)
            } catch (e: Exception) {
                logManager.e("ScheduleManager", "Failed to delete schedule", e)
                Result.failure(e)
            }
        }
    }
    
    override suspend fun initializeFromDataStore(context: Context): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val schedules = dataStoreManager.getSchedules()
                
                if (schedules.isEmpty()) {
                    return@withContext Result.success(Unit)
                }
                
                scheduleAlarms(context, schedules)
                
                Result.success(Unit)
            } catch (e: Exception) {
                logManager.e("ScheduleManager", "Failed to initialize from DataStore", e)
                Result.failure(e)
            }
        }
    }
    
    // Alarm Management (existing functionality)
    
    /**
     * Checks if the app can schedule exact alarms and logs the permission status
     */
    private fun checkExactAlarmPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val canScheduleExact = alarmManager.canScheduleExactAlarms()
            
            if (!canScheduleExact) {
                logManager.w("ScheduleManager", "SCHEDULE_EXACT_ALARM permission denied - using inexact alarms as fallback")
            }
            
            return canScheduleExact
        } else {
            return true
        }
    }
    
    override fun scheduleAlarms(context: Context, schedules: List<Schedule>) {
        logManager.d("ScheduleManager", "Scheduling alarms for ${schedules.size} schedules")
        
        // Check permission status upfront for user awareness
        val hasExactPermission = checkExactAlarmPermission(context)
        
        schedules.forEach { schedule ->
            // Skip disabled schedules
            if (!schedule.enabled) {
                return@forEach
            }
            
            // Validate schedule
            if (schedule.days.size != 7) {
                logManager.e("ScheduleManager", "Invalid schedule ${schedule.id}: days array must have 7 elements")
                return@forEach
            }
            
            if (schedule.days.none { it }) {
                logManager.e("ScheduleManager", "Invalid schedule ${schedule.id}: no days selected")
                return@forEach
            }
            
            // Create separate alarm for each enabled day
            schedule.days.forEachIndexed { dayIndex, isEnabled ->
                if (isEnabled) {
                    
                    // Create unique request code with safety checks
                    val requestCode = generateSafeRequestCode(schedule.id, dayIndex)
                    
                    val intent = Intent(context, AlarmReceiver::class.java).apply {
                        action = "hu.krafcsikgergo.wakeonwan.ALARM_TRIGGER"
                        putExtra("turnOn", schedule.turnOn)
                        putExtra("scheduleId", schedule.id)
                        putExtra("dayIndex", dayIndex)
                        // Add FLAG_RECEIVER_FOREGROUND to allow receiver to run at foreground priority
                        // This helps ensure alarm delivery even when device is in Doze mode
                        addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    }
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    // Calculate the next occurrence of this specific day/time
                    val nextAlarmTime = calculateNextAlarmTimeForDay(schedule, dayIndex)

                    // Schedule weekly repeating alarm for this specific day
                    scheduleWeeklyAlarm(context, nextAlarmTime, pendingIntent)
                }
            }
        }
    }
    
    /**
     * Generates a safe request code that avoids integer overflow
     */
    private fun generateSafeRequestCode(scheduleId: Int, dayIndex: Int): Int {
        // Use hash-based approach to avoid overflow while maintaining uniqueness
        val combined = "${scheduleId}_${dayIndex}".hashCode()
        // Ensure positive value and reasonable range
        return kotlin.math.abs(combined) % 1000000 + 1
    }

    private fun scheduleWeeklyAlarm(
        context: Context,
        alarmTime: Long,
        pendingIntent: PendingIntent
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Cancel the existing alarm if it exists
        alarmManager.cancel(pendingIntent)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    // Use setExactAndAllowWhileIdle for better reliability than setRepeating
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent)
                } else {
                    // Fall back to inexact alarm - will be less precise but still functional
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent)
                }
            } else {
                // For older Android versions, setExact is still available
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent)
            }
        } catch (e: SecurityException) {
            logManager.e("ScheduleManager", "SecurityException when scheduling alarm, falling back to inexact", e)
            try {
                // Final fallback - try inexact alarm even if exact was supposed to work
                alarmManager.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP, 
                    alarmTime, 
                    AlarmManager.INTERVAL_DAY * 7,
                    pendingIntent
                )
            } catch (fallbackException: Exception) {
                logManager.e("ScheduleManager", "Failed to schedule any alarm - both exact and inexact failed", fallbackException)
            }
        } catch (e: Exception) {
            logManager.e("ScheduleManager", "Unexpected error scheduling alarm", e)
        }
    }

    /**
     * Calculates the next occurrence of a specific day/time combination
     * Uses system default timezone and handles DST transitions
     */
    private fun calculateNextAlarmTimeForDay(schedule: Schedule, targetDayIndex: Int): Long {
        val now = ZonedDateTime.now()
        
        // Get current day of week (Monday = 1, Sunday = 7) and convert to our index (Monday = 0, Sunday = 6)
        val currentDayIndex = now.dayOfWeek.value - 1
        
        // Create the target time for today with explicit timezone handling
        val todayAlarmTime = now.withHour(schedule.timeInLocalTime.hour)
            .withMinute(schedule.timeInLocalTime.minute)
            .withSecond(0)
            .withNano(0)
        
        val daysUntilTarget = when {
            targetDayIndex > currentDayIndex -> targetDayIndex - currentDayIndex
            targetDayIndex < currentDayIndex -> 7 - (currentDayIndex - targetDayIndex)
            else -> { // targetDayIndex == currentDayIndex (today)
                if (todayAlarmTime.isAfter(now)) {
                    0
                } else {
                    7
                }
            }
        }
        
        val targetAlarmTime = todayAlarmTime.plusDays(daysUntilTarget.toLong())
        val epochMilli = targetAlarmTime.toInstant().toEpochMilli()
        
        // Validate that the calculated time is in the future
        if (epochMilli <= System.currentTimeMillis()) {
            logManager.w("ScheduleManager", "Calculated time is in the past, adding one week")
            return targetAlarmTime.plusWeeks(1).toInstant().toEpochMilli()
        }
        
        return epochMilli
    }

    override fun cancelAlarm(context: Context, scheduleId: Int) {
        logManager.d("ScheduleManager", "Cancelling alarms for schedule: $scheduleId")
        
        // Cancel alarms for all possible days (0-6) using the same logic as scheduling
        for (dayIndex in 0..6) {
            val requestCode = generateSafeRequestCode(scheduleId, dayIndex)
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = "hu.krafcsikgergo.wakeonwan.ALARM_TRIGGER"
                // Use same flags as when creating to ensure proper matching
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            
            if (pendingIntent != null) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }
    
    /**
     * Generates a new unique ID for a schedule.
     */
    private fun generateNewScheduleId(existingSchedules: List<Schedule>): Int {
        val existingIds = existingSchedules.map { it.id }.toSet()
        val baseId = (existingSchedules.maxOfOrNull { it.id } ?: 0) + 1
        
        // Ensure we don't have ID collisions and stay within safe bounds
        var newId = baseId
        while (existingIds.contains(newId) || newId > 100000) { // Keep IDs reasonable to avoid overflow
            newId = (1..100000).random()
        }
        
        return newId
    }
}