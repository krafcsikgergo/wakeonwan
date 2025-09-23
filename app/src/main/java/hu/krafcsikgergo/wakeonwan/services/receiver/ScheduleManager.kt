package hu.krafcsikgergo.wakeonwan.services.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import hu.krafcsikgergo.wakeonwan.services.AlarmReceiver
import java.time.ZonedDateTime
import java.time.temporal.ChronoField

interface ScheduleManager {
    fun scheduleAlarms(context: Context, schedules: List<Schedule>)
    fun cancelAlarm(context: Context, scheduleId: Int)
}

class ScheduleManagerImpl : ScheduleManager {
    override fun scheduleAlarms(context: Context, schedules: List<Schedule>) {
        Log.d("ScheduleManager", "Scheduling alarms for ${schedules.size} schedules")
        
        schedules.forEach { schedule ->
            // Validate schedule
            if (schedule.days.size != 7) {
                Log.e("ScheduleManager", "Invalid schedule ${schedule.id}: days array must have 7 elements")
                return@forEach
            }
            
            if (schedule.days.none { it }) {
                Log.e("ScheduleManager", "Invalid schedule ${schedule.id}: no days selected")
                return@forEach
            }
            
            // Create separate alarm for each enabled day
            schedule.days.forEachIndexed { dayIndex, isEnabled ->
                if (isEnabled) {
                    val dayName = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[dayIndex]
                    Log.d("ScheduleManager", "Scheduling alarm for schedule ${schedule.id} on $dayName at ${schedule.timeInLocalTime}")
                    
                    // Create unique request code with safety checks
                    val requestCode = generateSafeRequestCode(schedule.id, dayIndex)
                    
                    val intent = Intent(context, AlarmReceiver::class.java).apply {
                        putExtra("turnOn", schedule.turnOn)
                        putExtra("scheduleId", schedule.id)
                        putExtra("dayIndex", dayIndex)
                    }
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    // Calculate the next occurrence of this specific day/time
                    val nextAlarmTime = calculateNextAlarmTimeForDay(schedule, dayIndex)
                    Log.d("ScheduleManager", "Next alarm time for $dayName: $nextAlarmTime (${java.util.Date(nextAlarmTime)})")

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

        // Weekly interval in milliseconds (7 days)
        val weeklyInterval = 7L * 24L * 60L * 60L * 1000L

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                Log.d("ScheduleManager", "Scheduling weekly repeating alarm")
                alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, alarmTime, weeklyInterval, pendingIntent)
            } else {
                Log.d("ScheduleManager", "Requesting permission to schedule exact alarms")
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        } else {
            Log.d("ScheduleManager", "Scheduling weekly repeating alarm")
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, alarmTime, weeklyInterval, pendingIntent)
        }
    }

    /**
     * Calculates the next occurrence of a specific day/time combination
     * Uses system default timezone and handles DST transitions
     */
    private fun calculateNextAlarmTimeForDay(schedule: Schedule, targetDayIndex: Int): Long {
        val now = ZonedDateTime.now()
        Log.d("ScheduleManager", "Calculating next alarm time - Current: $now, Target day: $targetDayIndex")
        
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
                    Log.d("ScheduleManager", "Target time is later today")
                    0
                } else {
                    Log.d("ScheduleManager", "Target time has passed today, scheduling for next week")
                    7
                }
            }
        }
        
        val targetAlarmTime = todayAlarmTime.plusDays(daysUntilTarget.toLong())
        val epochMilli = targetAlarmTime.toInstant().toEpochMilli()
        
        Log.d("ScheduleManager", "Target alarm time: $targetAlarmTime ($epochMilli)")
        
        // Validate that the calculated time is in the future
        if (epochMilli <= System.currentTimeMillis()) {
            Log.w("ScheduleManager", "Calculated time is in the past, adding one week")
            return targetAlarmTime.plusWeeks(1).toInstant().toEpochMilli()
        }
        
        return epochMilli
    }

    override fun cancelAlarm(context: Context, scheduleId: Int) {
        Log.d("ScheduleManager", "Cancelling all alarms for schedule: $scheduleId")
        
        // Cancel alarms for all possible days (0-6) using the same logic as scheduling
        for (dayIndex in 0..6) {
            val requestCode = generateSafeRequestCode(scheduleId, dayIndex)
            val intent = Intent(context, AlarmReceiver::class.java)
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
                Log.d("ScheduleManager", "Cancelled alarm for schedule $scheduleId, day $dayIndex (requestCode: $requestCode)")
            } else {
                Log.d("ScheduleManager", "No alarm found for schedule $scheduleId, day $dayIndex")
            }
        }
    }
}