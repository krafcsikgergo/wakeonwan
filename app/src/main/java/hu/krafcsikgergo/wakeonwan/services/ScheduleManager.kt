package hu.krafcsikgergo.wakeonwan.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.time.ZonedDateTime
import java.time.temporal.ChronoField

object ScheduleManager {
    fun scheduleAlarms(context: Context, schedules: List<Schedule>) {
        Log.d("ScheduleManager", "Scheduling alarms")
        schedules.forEach { schedule ->
            Log.d(
                "ScheduleManager", "Scheduling alarm for schedule: $schedule"
            )
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("turnOn", schedule.turnOn)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                schedule.id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Calculate the next alarm time
            val nextAlarmTime = calculateNextAlarmTime(schedule) ?: return
            Log.d("ScheduleManager", "Next alarm time: $nextAlarmTime")

            // Schedule the alarm
            scheduleExactAlarm(context, nextAlarmTime, pendingIntent)
        }
    }

    fun scheduleExactAlarm(context: Context, alarmTime: Long, pendingIntent: PendingIntent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Cancel the existing alarm if it exists
        alarmManager.cancel(pendingIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                Log.d("ScheduleManager", "Scheduling exact alarm")
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent)
            } else {
                Log.d("ScheduleManager", "Requesting permission to schedule exact alarms")
                // Request permission from the user
                val intent =
                    Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                context.startActivity(intent)
            }
        } else {
            Log.d("ScheduleManager", "Scheduling exact alarm")
            // For versions below Android 12, schedule the alarm without permission check
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent)
        }
    }

    /**
     * Calculates the next alarm time based on a Schedule.
     *
     * @param schedule The schedule to calculate the next alarm time for.
     * @return The epoch milli time for the next alarm, or null if it cannot be calculated.
     */
    fun calculateNextAlarmTime(schedule: Schedule): Long? {
        val now = ZonedDateTime.now()

        // Get the current day of week (1 = Monday, 7 = Sunday)
        val todayIndex = now.dayOfWeek.value % 7

        // Try to find the next day starting from today
        for (i in 0..6) {
            val nextDayIndex = (todayIndex + i) % 7
            if (schedule.days[nextDayIndex]) {
                // Found the next day to schedule
                var nextAlarm = now.with(ChronoField.DAY_OF_WEEK, ((nextDayIndex + 1) % 7).toLong())
                nextAlarm = nextAlarm.withHour(schedule.time.hour).withMinute(schedule.time.minute)
                    .withSecond(0).withNano(0)

                // If the calculated next alarm is before the current time, it means the alarm time for today has passed.
                // Add 7 days if it's for today but already passed, or if it's a future day, no need to add.
                if (nextAlarm.isBefore(now) || i == 0) {
                    nextAlarm = nextAlarm.plusWeeks(1)
                }

                return nextAlarm.toInstant().toEpochMilli()
            }
        }
        // If we've found no suitable day (which should be impossible in practical scenarios), return null
        return null
    }


}