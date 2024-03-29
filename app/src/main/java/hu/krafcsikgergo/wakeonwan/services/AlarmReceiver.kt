package hu.krafcsikgergo.wakeonwan.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val turnOn = intent?.getBooleanExtra("turnOn", true) ?: true
        // Execute your function based on the turnOn parameter
        if (turnOn) {
            // Trigger the 'turn on' action
            Log.d("AlarmReceiver", "Turn on")
        } else {
            // Trigger the 'turn off' action
            Log.d("AlarmReceiver", "Turn off")
        }
    }
}
