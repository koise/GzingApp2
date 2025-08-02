package com.example.gzingapp.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.gzingapp.services.NotificationService

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "AlarmReceiver triggered")

        val title = intent.getStringExtra("title") ?: "GzingApp Alert"
        val message = intent.getStringExtra("message") ?: "Proximity alert triggered"
        val notificationId = intent.getIntExtra("notificationId", 1)
        val isAlarm = intent.getBooleanExtra("isAlarm", false)

        val notificationService = NotificationService(context)

        if (isAlarm) {
            // Show alarm notification with sound, vibration, and stop button
            notificationService.showAlarmNotification(title, message, notificationId)
            Log.d(TAG, "Alarm notification shown: $title")
        } else {
            // Show regular notification with vibration
            notificationService.showNotification(title, message, notificationId)
            Log.d(TAG, "Regular notification shown: $title")
        }
    }
}