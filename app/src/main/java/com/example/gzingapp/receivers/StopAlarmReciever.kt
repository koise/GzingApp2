package com.example.gzingapp.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.example.gzingapp.services.NotificationService
import com.example.gzingapp.services.NavigationHistoryService
import com.example.gzingapp.services.GeofenceHelper
import com.example.gzingapp.services.NavigationHelper
import com.example.gzingapp.services.SessionManager
import com.example.gzingapp.ui.dashboard.DashboardActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class StopAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "StopAlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "StopAlarmReceiver triggered with action: ${intent.action}")

        when (intent.action) {
            NotificationService.STOP_ALARM_ACTION -> {
                handleStopAlarmAction(context, intent)
            }
            NotificationService.STOP_NAVIGATION_ACTION -> {
                handleStopNavigationAction(context, intent)
            }
            else -> {
                Log.w(TAG, "Unknown action received: ${intent.action}")
            }
        }
    }

    private fun handleStopAlarmAction(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra("notificationId", -1)
        Log.d(TAG, "Processing stop alarm for notification ID: $notificationId")

        try {
            // Get the notification title and message before updating
            val notificationTitle = intent.getStringExtra("notificationTitle") ?: "Alarm"
            val notificationMessage = intent.getStringExtra("notificationMessage") ?: "Navigation alert"
            
            // Update the notification to show "Alarm stopped" instead of dismissing it
            val notificationService = NotificationService(context)
            notificationService.updateAlarmNotificationToStopped(notificationId, notificationTitle, notificationMessage)
            Log.d(TAG, "Notification $notificationId updated to show 'stopped' status")

            // Stop alarm gradually with fade out effects
            NotificationService.stopAllAlarmsGradually()
            Log.d(TAG, "Gradual alarm stop initiated for notification ID: $notificationId")

            // Get shared preferences for navigation mode
            val prefs = context.getSharedPreferences("navigation_prefs", Context.MODE_PRIVATE)
            
            // Handle navigation completion and cleanup
            if (notificationId == GeofenceBroadcastReceiver.ARRIVAL_ALARM_ID) {
                Log.d(TAG, "Processing destination arrival alarm stop - ending navigation")
                handleNavigationCompletion(context, true)
            } else {
                Log.d(TAG, "Processing non-arrival alarm stop - checking if navigation should continue")
                // For other alarm types, just clear the notifications but keep navigation active
                notificationService.clearAlarmNotifications()
            }

            // Open the app with specific flags based on the situation
            val appIntent = Intent(context, DashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("alarm_stopped", true)
                putExtra("stopped_alarm_id", notificationId)
                putExtra("navigation_ended", notificationId == GeofenceBroadcastReceiver.ARRIVAL_ALARM_ID)
                putExtra("allow_new_pin", true) // Allow user to pin new locations
                
                // Check if user is inside geofence and show save location modal
                val geofenceHelper = GeofenceHelper(context)
                if (geofenceHelper.isUserInsideGeofence()) {
                    putExtra("show_save_location_modal", true)
                    Log.d(TAG, "User is inside geofence - will show save location modal")
                }
            }

            context.startActivity(appIntent)
            Log.d(TAG, "App opened for stopped alarm notification ID: $notificationId")

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

    private fun handleStopNavigationAction(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra("notificationId", -1)
        Log.d(TAG, "Processing stop navigation for notification ID: $notificationId")

        try {
            // IMMEDIATELY dismiss the notification that was tapped
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.cancel(notificationId)
            Log.d(TAG, "Navigation notification $notificationId immediately dismissed")

            // Get session information for logging
            val sessionManager = SessionManager(context)
            val session = sessionManager.getSession()
            val userId = session["userId"] as String?
            val sessionType = "user"
            
            Log.d(TAG, "Stop navigation action - Session type: $sessionType, UserId: $userId")
            
            // Stop navigation using NavigationHelper
            val navigationHelper = NavigationHelper(context)
            navigationHelper.stopNavigation(
                onSuccess = {
                    Log.d(TAG, "Navigation stopped successfully from notification - Session type: $sessionType, UserId: $userId")
                    
                    // Clear all navigation notifications
                    val notificationService = NotificationService(context)
                    notificationService.clearNavigationNotifications()
                    notificationService.clearAlarmNotifications()
                    notificationService.showNavigationStoppedNotification("Navigation stopped from notification")
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to stop navigation from notification - Session type: $sessionType, UserId: $userId", error)
                },
                userId = userId,
                sessionType = sessionType
            )

            // Open the app
            val appIntent = Intent(context, DashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("navigation_stopped_from_notification", true)
                putExtra("allow_new_pin", true)
            }

            context.startActivity(appIntent)
            Log.d(TAG, "App opened for stopped navigation notification ID: $notificationId")

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping navigation from notification", e)
        }
    }

    private fun stopAlarmCompletely(context: Context, notificationId: Int) {
        Log.d(TAG, "Starting complete alarm stop procedure for notification ID: $notificationId")
        
        // Step 1: Stop alarm sound and vibration using static methods
        NotificationService.stopAllAlarms()
        Log.d(TAG, "Step 1: Alarm sound and vibration stopped")

        // Step 2: Dismiss the specific notification
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(notificationId)
        Log.d(TAG, "Step 2: Specific notification $notificationId dismissed")

        // Step 3: Clear all related notifications aggressively
        try {
            // Clear all alarm-related notifications
            notificationManager.cancel(GeofenceBroadcastReceiver.ARRIVAL_ALARM_ID)
            notificationManager.cancel(NotificationService.NAVIGATION_ONGOING_ID)
            notificationManager.cancel(NotificationService.NAVIGATION_STARTED_ID)
            notificationManager.cancel(NotificationService.NAVIGATION_ARRIVED_ID)
            Log.d(TAG, "Step 3: All related notifications cleared")
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing related notifications", e)
        }

        // Step 4: Small delay to ensure cleanup
        Thread.sleep(100)

        // Step 5: Double-check by calling stop methods again
        NotificationService.stopAllAlarms()
        Log.d(TAG, "Step 5: Final alarm stop verification completed")

        Log.d(TAG, "Complete alarm stop procedure executed successfully")
    }

    private fun forceStopAllAlarms(context: Context, notificationId: Int) {
        Log.d(TAG, "Starting force stop all alarms procedure for notification ID: $notificationId")
        
        // Multiple attempts to ensure everything stops
        repeat(3) { attempt ->
            Log.d(TAG, "Force stop attempt ${attempt + 1}")

            try {
                // Stop all alarm sounds and vibrations
                NotificationService.stopAllAlarms()

                // Clear notifications aggressively
                val notificationManager = NotificationManagerCompat.from(context)
                
                // Clear specific notification
                notificationManager.cancel(notificationId)
                
                // Clear all related notification IDs
                notificationManager.cancel(GeofenceBroadcastReceiver.ARRIVAL_ALARM_ID)
                notificationManager.cancel(NotificationService.NAVIGATION_ONGOING_ID)
                notificationManager.cancel(NotificationService.NAVIGATION_STARTED_ID)
                notificationManager.cancel(NotificationService.NAVIGATION_ARRIVED_ID)
                notificationManager.cancel(NotificationService.NAVIGATION_STOPPED_ID)

                if (attempt == 2) {
                    // Nuclear option on final attempt - clear ALL notifications
                    Log.d(TAG, "Final attempt: Clearing ALL notifications")
                    notificationManager.cancelAll()
                }

                Log.d(TAG, "Force stop attempt ${attempt + 1} completed successfully")
                Thread.sleep(50)
            } catch (e: Exception) {
                Log.e(TAG, "Force stop attempt ${attempt + 1} failed", e)
            }
        }
        
        Log.d(TAG, "Force stop all alarms procedure completed")
    }
    
    /**
     * Handle navigation completion when alarm is stopped
     */
    private fun handleNavigationCompletion(context: Context, wasSuccessful: Boolean) {
        Log.d(TAG, "Handling navigation completion - Success: $wasSuccessful")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get session information for logging
                val sessionManager = SessionManager(context)
                val session = sessionManager.getSession()
                val userId = session["userId"] as String?
                val sessionType = "user"
                
                Log.d(TAG, "Navigation completion - Session type: $sessionType, UserId: $userId")
                
                // Complete navigation history
                val historyService = NavigationHistoryService(context)
                val currentNavigation = historyService.getCurrentNavigation()
                
                if (currentNavigation != null) {
                    val actualDuration = ((System.currentTimeMillis() - currentNavigation.startTime) / 60000).toInt()
                    
                    if (wasSuccessful) {
                        historyService.completeNavigation(currentNavigation.id, actualDuration)
                        Log.d(TAG, "Navigation history marked as completed successfully - Session type: $sessionType, UserId: $userId")
                    } else {
                        historyService.cancelNavigation(currentNavigation.id, actualDuration, "Alarm stopped by user")
                        Log.d(TAG, "Navigation history marked as cancelled by user - Session type: $sessionType, UserId: $userId")
                    }
                } else {
                    Log.w(TAG, "No current navigation found to complete - Session type: $sessionType, UserId: $userId")
                }
                
                // Stop navigation using NavigationHelper
                try {
                    val navigationHelper = NavigationHelper(context)
                    navigationHelper.stopNavigation(
                        onSuccess = {
                            Log.d(TAG, "Navigation stopped successfully after alarm dismissal - Session type: $sessionType, UserId: $userId")
                            
                            // IMMEDIATELY clear all navigation-related notifications
                            val notificationService = NotificationService(context)
                            notificationService.clearNavigationNotifications()
                            notificationService.clearAlarmNotifications()
                            
                            // Also clear any remaining notifications manually
                            try {
                                val notificationManager = NotificationManagerCompat.from(context)
                                notificationManager.cancel(GeofenceBroadcastReceiver.ARRIVAL_ALARM_ID)
                                notificationManager.cancel(NotificationService.NAVIGATION_ONGOING_ID)
                                notificationManager.cancel(NotificationService.NAVIGATION_STARTED_ID)
                                notificationManager.cancel(NotificationService.NAVIGATION_ARRIVED_ID)
                                Log.d(TAG, "All remaining notifications cleared after navigation completion")
                            } catch (e: Exception) {
                                Log.w(TAG, "Error clearing remaining notifications", e)
                            }
                            
                            if (wasSuccessful) {
                                notificationService.showNavigationStoppedNotification("🎉 Navigation completed - destination reached!")
                            } else {
                                notificationService.showNavigationStoppedNotification("Navigation stopped - ready for new destination")
                            }
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Failed to stop navigation after alarm dismissal - Session type: $sessionType, UserId: $userId", error)
                        },
                        userId = userId,
                        sessionType = sessionType
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping navigation - Session type: $sessionType, UserId: $userId", e)
                }
                
                // Clear navigation mode and reset geofence state
                val prefs = context.getSharedPreferences("navigation_prefs", Context.MODE_PRIVATE)
                with(prefs.edit()) {
                    putBoolean("navigation_active", false)
                    putBoolean("geofence_active", false)
                    apply()
                }
                
                // Reset geofence helper state
                try {
                    val geofenceHelper = GeofenceHelper(context)
                    geofenceHelper.setUserInsideGeofence(false)
                    geofenceHelper.removeGeofence(
                        onSuccess = { 
                            Log.d(TAG, "Geofence cleared successfully - Session type: $sessionType, UserId: $userId") 
                        },
                        onFailure = { error -> 
                            Log.w(TAG, "Failed to clear geofence - Session type: $sessionType, UserId: $userId", error) 
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error clearing geofence - Session type: $sessionType, UserId: $userId", e)
                }
                
                Log.d(TAG, "Navigation completion handling finished - ready for new pins - Session type: $sessionType, UserId: $userId")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in navigation completion handling", e)
            }
        }
    }
}