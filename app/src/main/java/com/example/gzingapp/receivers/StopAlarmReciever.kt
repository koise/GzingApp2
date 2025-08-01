package com.example.gzingapp.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.example.gzingapp.services.NotificationService
import com.example.gzingapp.ui.dashboard.DashboardActivity

class StopAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "StopAlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "StopAlarmReceiver triggered with action: ${intent.action}")

        when (intent.action) {
            NotificationService.STOP_ALARM_ACTION -> {
                val notificationId = intent.getIntExtra("notificationId", -1)
                val redirectToApp = intent.getBooleanExtra("redirect_to_app", true)
                Log.d(TAG, "Processing stop alarm for notification ID: $notificationId, redirect: $redirectToApp")

                try {
                    // Force stop all alarm components
                    stopAlarmCompletely(context, notificationId)

                    Log.d(TAG, "Alarm stopped successfully for notification ID: $notificationId")

                    // Get shared preferences for navigation mode
                    val prefs = context.getSharedPreferences("navigation_prefs", Context.MODE_PRIVATE)
                    
                    // Check if this was a destination arrival alarm
                    if (notificationId == 2001) { // ARRIVAL_ALARM_ID
                        // Clear navigation mode when alarm is stopped
                        with(prefs.edit()) {
                            putBoolean("navigation_active", false)
                            remove("destination_lat")
                            remove("destination_lng")
                            remove("navigation_start_time")
                            apply()
                        }
                        Log.d(TAG, "Navigation mode and destination cleared after arrival alarm stop")
                    }

                    // Open the app if redirect flag is set
                    if (redirectToApp) {
                        val appIntent = Intent(context, DashboardActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra("alarm_stopped", true)
                            putExtra("stopped_alarm_id", notificationId)
                            putExtra("navigation_arrived", true)
                            putExtra("arrival_completed", true)
                        }

                        context.startActivity(appIntent)
                        Log.d(TAG, "App opened after navigation arrival - alarm stopped for ID: $notificationId")
                    } else {
                        Log.d(TAG, "Alarm stopped without app redirection for ID: $notificationId")
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping alarm", e)

                    // Critical fallback - force stop everything
                    try {
                        forceStopAllAlarms(context, notificationId)
                        Log.d(TAG, "Fallback alarm stop successful")
                    } catch (fallbackError: Exception) {
                        Log.e(TAG, "Fallback alarm stop also failed", fallbackError)
                    }
                }
            }
            else -> {
                Log.w(TAG, "Unknown action received: ${intent.action}")
            }
        }
    }

    private fun stopAlarmCompletely(context: Context, notificationId: Int) {
        // Step 1: Stop alarm sound and vibration using static methods
        NotificationService.stopAllAlarms()

        // Step 2: Dismiss the notification
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(notificationId)

        // Step 3: Small delay to ensure cleanup
        Thread.sleep(100)

        // Step 4: Double-check by calling stop methods again
        NotificationService.stopAllAlarms()

        Log.d(TAG, "Complete alarm stop procedure executed")
    }

    private fun forceStopAllAlarms(context: Context, notificationId: Int) {
        // Multiple attempts to ensure everything stops
        repeat(3) { attempt ->
            Log.d(TAG, "Force stop attempt ${attempt + 1}")

            try {
                NotificationService.stopAllAlarms()

                // Clear notifications
                val notificationManager = NotificationManagerCompat.from(context)
                notificationManager.cancel(notificationId)

                if (attempt == 2) {
                    // Nuclear option on final attempt
                    notificationManager.cancelAll()
                }

                Thread.sleep(50)
            } catch (e: Exception) {
                Log.e(TAG, "Force stop attempt ${attempt + 1} failed", e)
            }
        }
    }
}