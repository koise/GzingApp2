package com.example.gzingapp.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

/**
 * Receives geofence transition events from the system and relays them
 * to in-app handlers via [GeofenceEventReceiver] actions.
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "GeofenceBroadcastReceiver"

        // Notification ID used across the app for arrival alarm notifications
        const val ARRIVAL_ALARM_ID: Int = 1001
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            val geofencingEvent = GeofencingEvent.fromIntent(intent)
            if (geofencingEvent == null) {
                Log.w(TAG, "GeofencingEvent is null")
                return
            }

            if (geofencingEvent.hasError()) {
                Log.e(TAG, "GeofencingEvent error code: ${geofencingEvent.errorCode}")
                return
            }

            when (geofencingEvent.geofenceTransition) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> {
                    Log.d(TAG, "GEOFENCE_TRANSITION_ENTER")
                    handleGeofenceEnter(context)
                    // Broadcast to in-app receiver
                    context.sendBroadcast(Intent(GeofenceEventReceiver.ACTION_GEOFENCE_ENTER))
                }
                Geofence.GEOFENCE_TRANSITION_DWELL -> {
                    Log.d(TAG, "GEOFENCE_TRANSITION_DWELL")
                    context.sendBroadcast(Intent(GeofenceEventReceiver.ACTION_GEOFENCE_DWELL))
                }
                Geofence.GEOFENCE_TRANSITION_EXIT -> {
                    Log.d(TAG, "GEOFENCE_TRANSITION_EXIT")
                    context.sendBroadcast(Intent(GeofenceEventReceiver.ACTION_GEOFENCE_EXIT))
                }
                else -> {
                    Log.w(TAG, "Unknown geofence transition: ${geofencingEvent.geofenceTransition}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling geofence broadcast", e)
        }
    }
    
    /**
     * Handle geofence enter with voice announcement
     */
    private fun handleGeofenceEnter(context: Context) {
        try {
            // Check if voice announcements are enabled
            val sharedPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val voiceEnabled = sharedPrefs.getBoolean("voice_announcements", false)
            
            if (voiceEnabled) {
                val voiceService = com.example.gzingapp.services.VoiceAnnouncementService(context)
                voiceService.announceArrival("your destination")
                Log.d(TAG, "Voice announcement triggered for geofence enter")
            } else {
                Log.d(TAG, "Voice announcements disabled - no announcement for geofence enter")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering voice announcement on geofence enter", e)
        }
    }
}







