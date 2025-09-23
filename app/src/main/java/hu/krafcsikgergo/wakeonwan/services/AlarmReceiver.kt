package hu.krafcsikgergo.wakeonwan.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import hu.krafcsikgergo.wakeonwan.services.receiver.ScheduleManager
import hu.krafcsikgergo.wakeonwan.services.receiver.WakeOnLanService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AlarmReceiver : BroadcastReceiver(), KoinComponent {
    
    private val dataStoreManager: DataStoreManager by inject()
    private val scheduleManager: ScheduleManager by inject()
    private val wakeOnLanService: WakeOnLanService by inject()
    
    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        
        when (intent?.action) {
            "android.intent.action.BOOT_COMPLETED" -> {
                Log.d("AlarmReceiver", "Device booted, rescheduling alarms")
                rescheduleAllAlarms(context)
            }
            
            else -> {
                // Regular alarm trigger
                val turnOn = intent?.getBooleanExtra("turnOn", true) ?: true
                val scheduleId = intent?.getIntExtra("scheduleId", -1) ?: -1
                
                Log.d("AlarmReceiver", "Alarm triggered - turnOn: $turnOn, scheduleId: $scheduleId")
                
                // Perform the action in a coroutine
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        performScheduledAction(context, turnOn)
                        // No need to reschedule - setRepeating handles this automatically!
                    } catch (e: Exception) {
                        Log.e("AlarmReceiver", "Error performing scheduled action", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
    
    private suspend fun performScheduledAction(context: Context, turnOn: Boolean) {
        try {
            val serverData = dataStoreManager.getServerData()
            
            if (serverData == null) {
                Log.e("AlarmReceiver", "Server data not found - cannot perform scheduled action")
                return
            }
            
            if (turnOn) {
                Log.d("AlarmReceiver", "Executing Wake-on-LAN for ${serverData.macAddress}")
                val result = wakeOnLanService.sendWakeOnLanPacket(
                    macAddress = serverData.macAddress,
                    ipAddress = serverData.ipAddress
                )
                result.fold(
                    onSuccess = { message -> Log.d("AlarmReceiver", "WOL success: $message") },
                    onFailure = { error -> Log.e("AlarmReceiver", "WOL failed: ${error.message}", error) }
                )
            } else {
                Log.d("AlarmReceiver", "Executing shutdown via SSH for ${serverData.ipAddress}")
                val result = wakeOnLanService.executeShutdownCommand(
                    ipAddress = serverData.ipAddress,
                    username = serverData.username,
                    password = serverData.password,
                    port = serverData.sshPort
                )
                result.fold(
                    onSuccess = { message -> Log.d("AlarmReceiver", "Shutdown success: $message") },
                    onFailure = { error -> Log.e("AlarmReceiver", "Shutdown failed: ${error.message}", error) }
                )
            }
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Failed to perform scheduled action", e)
        }
    }
    
    private fun rescheduleAllAlarms(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("AlarmReceiver", "Loading schedules from DataStore")
                val schedules = dataStoreManager.getSchedules()
                
                if (schedules.isEmpty()) {
                    Log.d("AlarmReceiver", "No schedules found to reschedule")
                    return@launch
                }
                
                Log.d("AlarmReceiver", "Rescheduling ${schedules.size} alarms after boot")
                scheduleManager.scheduleAlarms(context, schedules)
                Log.d("AlarmReceiver", "Successfully rescheduled all alarms")
                
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Failed to reschedule alarms after boot", e)
            }
        }
    }
}
