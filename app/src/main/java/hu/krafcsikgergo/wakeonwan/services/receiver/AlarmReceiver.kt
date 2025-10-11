package hu.krafcsikgergo.wakeonwan.services.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import hu.krafcsikgergo.wakeonwan.services.DataStoreManager
import hu.krafcsikgergo.wakeonwan.services.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AlarmReceiver : BroadcastReceiver(), KoinComponent {

    // Use Koin dependency injection
    private val dataStoreManager: DataStoreManager by inject()
    private val logManager: LogManager by inject()
    private val wakeOnLanService: WakeOnLanService by inject()
    private val scheduleManager: ScheduleManager by inject()

    override fun onReceive(context: Context?, intent: Intent?) {
        try {
            context ?: return

            // Get application context for dependency resolution
            val appContext = context.applicationContext
            
            when (intent?.action) {
                "android.intent.action.BOOT_COMPLETED" -> {
                    logManager.d("AlarmReceiver", "Device booted, rescheduling alarms")
                    rescheduleAllAlarms(appContext)
                }

                "hu.krafcsikgergo.wakeonwan.ALARM_TRIGGER" -> {
                    // Regular alarm trigger
                    val turnOn = intent.getBooleanExtra("turnOn", true)
                    val scheduleId = intent.getIntExtra("scheduleId", -1)

                    logManager.d("AlarmReceiver", "Alarm triggered - turnOn: $turnOn, scheduleId: $scheduleId")

                    // Perform the action in a coroutine
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            performScheduledAction( turnOn)
                            // Reschedule the next weekly alarm
                            rescheduleNextAlarm(appContext, scheduleId)
                        } catch (e: Exception) {
                            logManager.e("AlarmReceiver", "Error performing scheduled action", e)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }

                else -> {
                    logManager.w("AlarmReceiver", "Unknown intent action: ${intent?.action}")
                }
            }
        } catch (e: Exception) {
            logManager.e("AlarmReceiver", "Error in onReceive", e)
        }
    }

    private suspend fun performScheduledAction(turnOn: Boolean) {
        try {
            // Use injected dependencies instead of creating new instances
            val serverData = dataStoreManager.getServerData()

            if (serverData == null) {
                logManager.e("AlarmReceiver", "Server data not found - cannot perform scheduled action")
                return
            }

            if (turnOn) {
                val result = wakeOnLanService.sendWakeOnLanPacket(serverData)
                result.fold(
                    onSuccess = { message -> logManager.d("AlarmReceiver", "WOL success: $message") },
                    onFailure = { error ->
                        logManager.e(
                            "AlarmReceiver",
                            "WOL failed: ${error.message}",
                            error
                        )
                    }
                )
            } else {
                val result = wakeOnLanService.executeShutdownCommand(serverData)
                result.fold(
                    onSuccess = { message -> logManager.d("AlarmReceiver", "Shutdown success: $message") },
                    onFailure = { error ->
                        logManager.e(
                            "AlarmReceiver",
                            "Shutdown failed: ${error.message}",
                            error
                        )
                    }
                )
            }
        } catch (e: Exception) {
            logManager.e("AlarmReceiver", "Failed to perform scheduled action", e)
        }
    }

    private fun rescheduleAllAlarms(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Use injected dependencies instead of creating new instances
                val result = scheduleManager.initializeFromDataStore(context)
                
                result.fold(
                    onSuccess = {
                        logManager.d("AlarmReceiver", "Successfully rescheduled all alarms")
                    },
                    onFailure = { error ->
                        logManager.e("AlarmReceiver", "Failed to reschedule alarms after boot: ${error.message}", error)
                    }
                )
            } catch (e: Exception) {
                logManager.e("AlarmReceiver", "Failed to reschedule alarms after boot", e)
            }
        }
    }

    private fun rescheduleNextAlarm(context: Context, scheduleId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Use injected dependencies instead of creating new instances
                // Get the schedule and reschedule it for next week
                val schedules = scheduleManager.getAllSchedules()
                val schedule = schedules.find { it.id == scheduleId }
                
                if (schedule != null) {
                    // The scheduleAlarms method will calculate the next occurrence automatically
                    scheduleManager.scheduleAlarms(context, listOf(schedule))
                } else {
                    logManager.w("AlarmReceiver", "Schedule $scheduleId not found for rescheduling")
                }
            } catch (e: Exception) {
                logManager.e("AlarmReceiver", "Failed to reschedule next alarm", e)
            }
        }
    }
}
