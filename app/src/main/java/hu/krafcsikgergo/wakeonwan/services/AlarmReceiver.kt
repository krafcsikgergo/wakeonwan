package hu.krafcsikgergo.wakeonwan.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import hu.krafcsikgergo.wakeonwan.services.DataStoreManager
import hu.krafcsikgergo.wakeonwan.services.DataStoreManagerImpl
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

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("AlarmReceiver", "onReceive called! context: $context, intent: $intent")
        
        try {
            context ?: return

            // Get application context for dependency resolution
            val appContext = context.applyicationContext
            
            when (intent?.action) {
                "android.intent.action.BOOT_COMPLETED" -> {
                    Log.d("AlarmReceiver", "Device booted, rescheduling alarms")
                    rescheduleAllAlarms(appContext)
                }

                "hu.krafcsikgergo.wakeonwan.ALARM_TRIGGER" -> {
                    // Regular alarm trigger
                    val turnOn = intent?.getBooleanExtra("turnOn", true) ?: true
                    val scheduleId = intent?.getIntExtra("scheduleId", -1) ?: -1

                    Log.d("AlarmReceiver", "Alarm triggered - turnOn: $turnOn, scheduleId: $scheduleId")
                    Log.d("AlarmReceiver", "Intent extras: ${intent?.extras}")

                    // Perform the action in a coroutine
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            Log.d("AlarmReceiver", "Starting performScheduledAction with turnOn: $turnOn")
                            performScheduledAction(appContext, turnOn)
                            // Reschedule the next weekly alarm
                            rescheduleNextAlarm(appContext, scheduleId)
                            Log.d("AlarmReceiver", "Completed performScheduledAction and rescheduled next alarm")
                        } catch (e: Exception) {
                            Log.e("AlarmReceiver", "Error performing scheduled action", e)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }

                else -> {
                    Log.w("AlarmReceiver", "Unknown intent action: ${intent?.action}")
                }
            }
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Error in onReceive", e)
        }
    }

    private suspend fun performScheduledAction(context: Context, turnOn: Boolean) {
        Log.d("AlarmReceiver", "performScheduledAction starting - turnOn: $turnOn")
        try {
            // Create services directly (BroadcastReceiver context has limitations with DI)
            val dataStoreManager = DataStoreManagerImpl(context)
            val sshManager = SSHManagerImpl()
            val wakeOnLanService = WakeOnLanServiceImpl(context, sshManager)

            val serverData = dataStoreManager.getServerData()
            Log.d("AlarmReceiver", "Retrieved server data: $serverData")

            if (serverData == null) {
                Log.e("AlarmReceiver", "Server data not found - cannot perform scheduled action")
                return
            }

            if (turnOn) {
                Log.d("AlarmReceiver", "Executing Wake-on-LAN for ${serverData.macAddress}")
                val result = wakeOnLanService.sendWakeOnLanPacket(serverData)
                result.fold(
                    onSuccess = { message -> Log.d("AlarmReceiver", "WOL success: $message") },
                    onFailure = { error ->
                        Log.e(
                            "AlarmReceiver",
                            "WOL failed: ${error.message}",
                            error
                        )
                    }
                )
            } else {
                Log.d("AlarmReceiver", "Executing shutdown via SSH for ${serverData.ipAddress}")
                val result = wakeOnLanService.executeShutdownCommand(serverData)
                result.fold(
                    onSuccess = { message -> Log.d("AlarmReceiver", "Shutdown success: $message") },
                    onFailure = { error ->
                        Log.e(
                            "AlarmReceiver",
                            "Shutdown failed: ${error.message}",
                            error
                        )
                    }
                )
            }
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Failed to perform scheduled action", e)
        }
    }

    private fun rescheduleAllAlarms(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Create services directly
                val dataStoreManager = DataStoreManagerImpl(context)
                val scheduleManager = ScheduleManagerImpl(dataStoreManager)

                Log.d("AlarmReceiver", "Initializing schedules from DataStore via ScheduleManager")
                val result = scheduleManager.initializeFromDataStore(context)
                
                result.fold(
                    onSuccess = {
                        Log.d("AlarmReceiver", "Successfully rescheduled all alarms")
                    },
                    onFailure = { error ->
                        Log.e("AlarmReceiver", "Failed to reschedule alarms after boot: ${error.message}", error)
                    }
                )
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Failed to reschedule alarms after boot", e)
            }
        }
    }

    private fun rescheduleNextAlarm(context: Context, scheduleId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Create services directly
                val dataStoreManager = DataStoreManagerImpl(context)
                val scheduleManager = ScheduleManagerImpl(dataStoreManager)

                Log.d("AlarmReceiver", "Rescheduling next alarm for schedule: $scheduleId")
                
                // Get the schedule and reschedule it for next week
                val schedules = scheduleManager.getAllSchedules()
                val schedule = schedules.find { it.id == scheduleId }
                
                if (schedule != null) {
                    // The scheduleAlarms method will calculate the next occurrence automatically
                    scheduleManager.scheduleAlarms(context, listOf(schedule))
                    Log.d("AlarmReceiver", "Successfully rescheduled next alarm for schedule $scheduleId")
                } else {
                    Log.w("AlarmReceiver", "Schedule $scheduleId not found for rescheduling")
                }
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Failed to reschedule next alarm", e)
            }
        }
    }
}
