package com.example.gzingapp.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import com.example.gzingapp.services.NotificationService
import com.example.gzingapp.services.GeofenceHelper
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "GeofenceBroadcastReceiver"
        private const val PREFS_NAME = "navigation_prefs"
        private const val KEY_NAVIGATION_ACTIVE = "navigation_active"

        // Notification IDs for different types
        const val ARRIVAL_ALARM_ID = 2001
        private const val DWELL_NOTIFICATION_ID = 2002
        private const val EXIT_NOTIFICATION_ID = 2003
    }

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)

        if (geofencingEvent == null) {
            Log.e(TAG, "Geofencing event is null")
            return
        }

        if (geofencingEvent.hasError()) {
            val errorMessage = "Geofence error: ${geofencingEvent.errorCode}"
            Log.e(TAG, errorMessage)
            return
        }

        // Get the transition type
        val geofenceTransition = geofencingEvent.geofenceTransition

        // Test that the reported transition was of interest
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ||
            geofenceTransition == Geofence.GEOFENCE_TRANSITION_DWELL ||
            geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {

            // Get the geofences that were triggered
            val triggeringGeofences = geofencingEvent.triggeringGeofences

            if (triggeringGeofences != null) {
                // Get the transition details
                val geofenceTransitionDetails = getGeofenceTransitionDetails(
                    geofenceTransition,
                    triggeringGeofences
                )

                Log.d(TAG, geofenceTransitionDetails)

                // Check if user is in navigation mode
                val isNavigationActive = isNavigationModeActive(context)
                Log.d(TAG, "Navigation mode active: $isNavigationActive")

                // Handle notifications based on navigation mode and transition type
                handleGeofenceTransition(context, geofenceTransition, isNavigationActive)
            }
        } else {
            // Log the error
            Log.e(TAG, "Invalid transition type: $geofenceTransition")
        }
    }

    /**
     * Handle geofence transitions with appropriate notifications based on navigation mode
     */
    private fun handleGeofenceTransition(context: Context, geofenceTransition: Int, isNavigationActive: Boolean) {
        val notificationService = NotificationService(context)

        when (geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                if (isNavigationActive) {
                    // Navigation mode: Show ALARM for arrival
                    Log.d(TAG, "Navigation active - showing arrival ALARM")
                    notificationService.showAlarmNotification(
                        "🚨 DESTINATION REACHED!",
                        "You have arrived at your destination! Tap STOP ALARM to end navigation.",
                        ARRIVAL_ALARM_ID
                    )
                    
                    // Ensure the alarm is loud and persistent
                    notificationService.playAlarmSound()
                    notificationService.triggerAlarmVibration()
                } else {
                    // Passive mode: Show regular notification
                    Log.d(TAG, "Passive mode - showing arrival notification")
                    notificationService.showNotification(
                        "📍 You've Arrived!",
                        "You have reached your pinned location",
                        ARRIVAL_ALARM_ID
                    )
                }
            }

            Geofence.GEOFENCE_TRANSITION_DWELL -> {
                if (isNavigationActive) {
                    // Navigation mode: Show notification that user is staying at destination
                    Log.d(TAG, "Navigation active - showing dwell notification")
                    notificationService.showNotification(
                        "🏁 At Destination",
                        "You are now at your destination location",
                        DWELL_NOTIFICATION_ID
                    )
                } else {
                    // Passive mode: Show regular dwell notification
                    Log.d(TAG, "Passive mode - showing dwell notification")
                    notificationService.showNotification(
                        "📍 At Location",
                        "You are currently at your pinned location",
                        DWELL_NOTIFICATION_ID
                    )
                }
            }

            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                if (isNavigationActive) {
                    // Navigation mode: Show notification that user left destination
                    Log.d(TAG, "Navigation active - showing exit notification")
                    notificationService.showNotification(
                        "🚶 Left Destination",
                        "You have left your destination area",
                        EXIT_NOTIFICATION_ID
                    )

                    // Optional: Turn off navigation mode when user leaves
                    // setNavigationMode(context, false)
                } else {
                    // Passive mode: Show regular exit notification
                    Log.d(TAG, "Passive mode - showing exit notification")
                    notificationService.showNotification(
                        "📍 Left Location",
                        "You have left your pinned location area",
                        EXIT_NOTIFICATION_ID
                    )
                }
            }
        }
    }

    /**
     * Check if navigation mode is currently active
     */
    private fun isNavigationModeActive(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_NAVIGATION_ACTIVE, false)
    }

    /**
     * Set navigation mode state (call this from your navigation activity)
     */
    fun setNavigationMode(context: Context, isActive: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(prefs.edit()) {
            putBoolean(KEY_NAVIGATION_ACTIVE, isActive)
            apply()
        }
        Log.d(TAG, "Navigation mode set to: $isActive")
    }

    /**
     * Gets transition details and returns them as a formatted string.
     */
    private fun getGeofenceTransitionDetails(
        geofenceTransition: Int,
        triggeringGeofences: List<Geofence>
    ): String {
        val geofenceTransitionString = getTransitionString(geofenceTransition)

        // Get the Ids of each geofence that was triggered
        val triggeringGeofencesIdsList = mutableListOf<String>()
        for (geofence in triggeringGeofences) {
            triggeringGeofencesIdsList.add(geofence.requestId)
        }

        val triggeringGeofencesIdsString = triggeringGeofencesIdsList.joinToString(", ")

        return "$geofenceTransitionString: $triggeringGeofencesIdsString"
    }

    /**
     * Maps geofence transition types to their human-readable equivalents.
     */
    private fun getTransitionString(transitionType: Int): String {
        return when (transitionType) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "Entered"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "Exited"
            Geofence.GEOFENCE_TRANSITION_DWELL -> "Dwelling"
            else -> "Unknown Transition"
        }
    }
}