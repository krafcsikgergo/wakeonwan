package hu.krafcsikgergo.wakeonwan.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import hu.krafcsikgergo.wakeonwan.services.DataStoreManager
import hu.krafcsikgergo.wakeonwan.services.DataStoreManagerImpl
import hu.krafcsikgergo.wakeonwan.services.LogManager
import hu.krafcsikgergo.wakeonwan.services.LogManagerImpl
import hu.krafcsikgergo.wakeonwan.services.receiver.ScheduleManager
import hu.krafcsikgergo.wakeonwan.services.receiver.ScheduleManagerImpl
import hu.krafcsikgergo.wakeonwan.services.receiver.WakeOnLanService
import hu.krafcsikgergo.wakeonwan.services.receiver.WakeOnLanServiceImpl
import hu.krafcsikgergo.wakeonwan.services.receiver.SSHManager
import hu.krafcsikgergo.wakeonwan.services.receiver.SSHManagerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    private lateinit var logManager: LogManager

    override fun onReceive(context: Context?, intent: Intent?) {
        if (!::logManager.isInitialized) {
            logManager = LogManagerImpl()
        }
        
        logManager.d("AlarmReceiver", "onReceive called! context: $context, intent: $intent")
        
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
                    logManager.d("AlarmReceiver", "Intent extras: ${intent.extras}")

                    // Perform the action in a coroutine
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            logManager.d("AlarmReceiver", "Starting performScheduledAction with turnOn: $turnOn")
                            performScheduledAction(appContext, turnOn)
                            // Reschedule the next weekly alarm
                            rescheduleNextAlarm(appContext, scheduleId)
                            logManager.d("AlarmReceiver", "Completed performScheduledAction and rescheduled next alarm")
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

    private suspend fun performScheduledAction(context: Context, turnOn: Boolean) {
        logManager.d("AlarmReceiver", "performScheduledAction starting - turnOn: $turnOn")
        try {
            // Create services directly (BroadcastReceiver context has limitations with DI)
            val dataStoreManager = DataStoreManagerImpl(context)
            val logManager = LogManagerImpl()
            val sshManager = SSHManagerImpl(logManager)
            val wakeOnLanService = WakeOnLanServiceImpl(context, sshManager, logManager)

            val serverData = dataStoreManager.getServerData()
            logManager.d("AlarmReceiver", "Retrieved server data: $serverData")

            if (serverData == null) {
                logManager.e("AlarmReceiver", "Server data not found - cannot perform scheduled action")
                return
            }

            if (turnOn) {
                logManager.d("AlarmReceiver", "Executing Wake-on-LAN for ${serverData.macAddress}")
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
                logManager.d("AlarmReceiver", "Executing shutdown via SSH for ${serverData.ipAddress}")
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
                // Create services directly
                val dataStoreManager = DataStoreManagerImpl(context)
                val logManager = LogManagerImpl()
                val scheduleManager = ScheduleManagerImpl(dataStoreManager, logManager)

                logManager.d("AlarmReceiver", "Initializing schedules from DataStore via ScheduleManager")
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
                // Create services directly
                val dataStoreManager = DataStoreManagerImpl(context)
                val logManager = LogManagerImpl()
                val scheduleManager = ScheduleManagerImpl(dataStoreManager, logManager)

                logManager.d("AlarmReceiver", "Rescheduling next alarm for schedule: $scheduleId")
                
                // Get the schedule and reschedule it for next week
                val schedules = scheduleManager.getAllSchedules()
                val schedule = schedules.find { it.id == scheduleId }
                
                if (schedule != null) {
                    // The scheduleAlarms method will calculate the next occurrence automatically
                    scheduleManager.scheduleAlarms(context, listOf(schedule))
                    logManager.d("AlarmReceiver", "Successfully rescheduled next alarm for schedule $scheduleId")
                } else {
                    logManager.w("AlarmReceiver", "Schedule $scheduleId not found for rescheduling")
                }
            } catch (e: Exception) {
                logManager.e("AlarmReceiver", "Failed to reschedule next alarm", e)
            }
        }
    }
}
