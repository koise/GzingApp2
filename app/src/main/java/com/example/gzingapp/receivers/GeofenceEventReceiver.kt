package com.example.gzingapp.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.gzingapp.services.GeofenceHelper
import com.example.gzingapp.services.NotificationService
import com.example.gzingapp.services.VoiceAnnouncementService
import com.example.gzingapp.services.LocationHelper
import com.example.gzingapp.services.BackgroundLocationService
import com.google.android.gms.maps.model.LatLng
import com.example.gzingapp.ui.dashboard.DashboardActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GeofenceEventReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "GeofenceEventReceiver"
        
        // Actions for in-app geofence events
        const val ACTION_GEOFENCE_ENTER = "com.example.gzingapp.GEOFENCE_ENTER"
        const val ACTION_GEOFENCE_EXIT = "com.example.gzingapp.GEOFENCE_EXIT"
        const val ACTION_GEOFENCE_DWELL = "com.example.gzingapp.GEOFENCE_DWELL"
        
        // Action for real-time geofence check
        const val ACTION_REALTIME_GEOFENCE_CHECK = "com.example.gzingapp.REALTIME_GEOFENCE_CHECK"

        interface GeofenceEventListener {
            fun onGeofenceEnter()
            fun onGeofenceExit()
            fun onGeofenceDwell()
            fun onRealTimeGeofenceStatusUpdate(isInside: Boolean, distance: Float)
        }
        
        // Static reference to listener for real-time updates
        private var globalListener: GeofenceEventListener? = null
        
        fun setGlobalListener(listener: GeofenceEventListener?) {
            globalListener = listener
        }
    }

    private var geofenceEventListener: GeofenceEventListener? = null

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received geofence event: ${intent.action}")
        
        when (intent.action) {
            ACTION_GEOFENCE_ENTER -> {
                Log.d(TAG, "🎯 Processing GEOFENCE ENTER event")
                
                // Trigger immediate alarm and voice announcement
                triggerArrivalAlarm(context)
                
                // Notify listeners
                geofenceEventListener?.onGeofenceEnter()
                globalListener?.onGeofenceEnter()

                // Log current state as INSIDE
                logCurrentGeofenceState(context, forcedInside = true)
            }
            
            ACTION_GEOFENCE_EXIT -> {
                Log.d(TAG, "🚶 Processing GEOFENCE EXIT event")
                geofenceEventListener?.onGeofenceExit()
                globalListener?.onGeofenceExit()

                // Log current state as OUTSIDE
                logCurrentGeofenceState(context, forcedInside = false)
            }
            
            ACTION_GEOFENCE_DWELL -> {
                Log.d(TAG, "🏠 Processing GEOFENCE DWELL event")
                geofenceEventListener?.onGeofenceDwell()
                globalListener?.onGeofenceDwell()

                // Log current state as INSIDE (dwelling implies inside)
                logCurrentGeofenceState(context, forcedInside = true)
            }
            
            ACTION_REALTIME_GEOFENCE_CHECK -> {
                Log.d(TAG, "🔄 Processing REAL-TIME GEOFENCE CHECK")
                handleRealTimeGeofenceCheck(context, intent)
            }
        }
    }

    /**
     * Handle real-time geofence status checking with enhanced responsiveness
     */
    private fun handleRealTimeGeofenceCheck(context: Context, intent: Intent) {
        try {
            val latitude = intent.getDoubleExtra("latitude", 0.0)
            val longitude = intent.getDoubleExtra("longitude", 0.0)
            val accuracy = intent.getFloatExtra("accuracy", 0f)
            val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
            
            if (latitude == 0.0 || longitude == 0.0) {
                Log.w(TAG, "Invalid coordinates in real-time geofence check")
                return
            }
            
            val currentLocation = LatLng(latitude, longitude)
            val geofenceHelper = GeofenceHelper(context)
            val geofenceLocation = geofenceHelper.getCurrentGeofenceLocation()
            
            if (geofenceLocation == null) {
                Log.d(TAG, "No active geofence to check against")
                return
            }
            
            // Calculate distance to geofence center
            val locationHelper = LocationHelper(context)
            val distance = locationHelper.calculateDistance(currentLocation, geofenceLocation).toFloat() * 1000 // Convert to meters
            val radius = GeofenceHelper.getGeofenceRadius()
            
            // Enhanced accuracy-based threshold for more reliable geofence detection
            val accuracyBuffer = when {
                accuracy <= 5f -> accuracy * 0.5f     // Excellent accuracy - minimal buffer
                accuracy <= 15f -> accuracy * 0.8f    // Good accuracy - moderate buffer
                accuracy <= 30f -> accuracy * 1.0f    // Medium accuracy - full buffer
                accuracy <= 50f -> accuracy * 0.8f    // Lower accuracy - reduced buffer to avoid false positives
                else -> 10f                           // Poor accuracy - fixed small buffer
            }
            
            val wasInside = geofenceHelper.isUserInsideGeofence()
            
            // Enhanced geofence detection with hysteresis to prevent flapping
            val hysteresisBuffer = if (wasInside) {
                radius * 0.1f  // 10% buffer when inside (harder to exit)
            } else {
                -radius * 0.05f // 5% smaller radius when outside (easier to enter)
            }
            
            val effectiveRadius = radius + hysteresisBuffer + accuracyBuffer
            val isCurrentlyInside = distance <= effectiveRadius
            
            // Enhanced structured state log
            logGeofenceState(
                isInside = isCurrentlyInside,
                distanceMeters = distance,
                radiusMeters = radius,
                accuracyMeters = accuracy,
                wasInside = wasInside,
                source = "REALTIME_CHECK",
                effectiveRadius = effectiveRadius,
                hysteresisBuffer = hysteresisBuffer,
                accuracyBuffer = accuracyBuffer
            )
            
            // Update geofence status
            geofenceHelper.setUserInsideGeofence(isCurrentlyInside)
            
            // Notify listeners about status update
            geofenceEventListener?.onRealTimeGeofenceStatusUpdate(isCurrentlyInside, distance)
            globalListener?.onRealTimeGeofenceStatusUpdate(isCurrentlyInside, distance)
            
            // Check for geofence transitions with enhanced logging
            if (isCurrentlyInside && !wasInside) {
                Log.i(TAG, "🎯 REAL-TIME GEOFENCE ENTER DETECTED! Distance: ${String.format("%.1f", distance)}m, Effective Radius: ${String.format("%.1f", effectiveRadius)}m")
                
                // Send enter broadcast
                val enterIntent = Intent(ACTION_GEOFENCE_ENTER).apply {
                    putExtra("distance", distance)
                    putExtra("accuracy", accuracy)
                    putExtra("timestamp", timestamp)
                }
                context.sendBroadcast(enterIntent)
                
                // Trigger immediate alarm with enhanced notification
                triggerArrivalAlarm(context)
                
                // Notify listeners
                geofenceEventListener?.onGeofenceEnter()
                globalListener?.onGeofenceEnter()
                
                // Optionally bring app to foreground to update UI
                try {
                    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    val autoOpen = prefs.getBoolean("auto_open_on_geofence", false)
                    if (autoOpen) {
                        val openIntent = Intent(context, DashboardActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("open_reason", "geofence")
                            putExtra("geofence_event", "enter")
                            putExtra("distance", distance)
                            putExtra("accuracy", accuracy)
                            putExtra("timestamp", timestamp)
                        }
                        context.startActivity(openIntent)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error attempting to bring app to foreground on geofence enter", e)
                }
                
            } else if (!isCurrentlyInside && wasInside) {
                Log.i(TAG, "🚶 REAL-TIME GEOFENCE EXIT DETECTED! Distance: ${String.format("%.1f", distance)}m, Effective Radius: ${String.format("%.1f", effectiveRadius)}m")
                
                // Send exit broadcast
                val exitIntent = Intent(ACTION_GEOFENCE_EXIT).apply {
                    putExtra("distance", distance)
                    putExtra("accuracy", accuracy)
                    putExtra("timestamp", timestamp)
                }
                context.sendBroadcast(exitIntent)
                
                // Notify listeners
                geofenceEventListener?.onGeofenceExit()
                globalListener?.onGeofenceExit()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in real-time geofence check", e)
        }
    }

    /**
     * Enhanced geofence state logging with additional parameters
     */
    private fun logGeofenceState(
        isInside: Boolean,
        distanceMeters: Float,
        radiusMeters: Float,
        accuracyMeters: Float,
        wasInside: Boolean,
        source: String,
        effectiveRadius: Float = radiusMeters,
        hysteresisBuffer: Float = 0f,
        accuracyBuffer: Float = 0f
    ) {
        val timestamp = System.currentTimeMillis()
        val status = if (isInside) "INSIDE" else "OUTSIDE"
        val transition = when {
            isInside && !wasInside -> "ENTERED"
            !isInside && wasInside -> "EXITED"
            else -> "NO_CHANGE"
        }
        
        // Concise status line
        Log.i(TAG, "🎯 GEOFENCE: $status | Dist: ${String.format("%.1f", distanceMeters)}m | EffR: ${String.format("%.1f", effectiveRadius)}m | Acc: ${String.format("%.1f", accuracyMeters)}m | $transition | $source")
        
        // Detailed state block
        Log.d(TAG, "=== GEOFENCE STATE DETAILS ===")
        Log.d(TAG, "Timestamp: $timestamp")
        Log.d(TAG, "Source: $source")
        Log.d(TAG, "Current Status: $status")
        Log.d(TAG, "Previous Status: ${if (wasInside) "INSIDE" else "OUTSIDE"}")
        Log.d(TAG, "Transition: $transition")
        Log.d(TAG, "Distance to Center: ${String.format("%.2f", distanceMeters)}m")
        Log.d(TAG, "Base Radius: ${String.format("%.1f", radiusMeters)}m")
        Log.d(TAG, "Effective Radius: ${String.format("%.2f", effectiveRadius)}m")
        Log.d(TAG, "Accuracy Buffer: ${String.format("%.2f", accuracyBuffer)}m")
        Log.d(TAG, "Hysteresis Buffer: ${String.format("%.2f", hysteresisBuffer)}m")
        Log.d(TAG, "GPS Accuracy: ${String.format("%.1f", accuracyMeters)}m")
        Log.d(TAG, "Distance to Boundary: ${String.format("%.2f", distanceMeters - effectiveRadius)}m")
        Log.d(TAG, "==============================")
    }
    
    /**
     * Log a concise, structured geofence state line and a detailed block (backward compatibility).
     */
    private fun logGeofenceState(
        isInside: Boolean,
        distanceMeters: Float,
        radiusMeters: Float,
        accuracyMeters: Float,
        wasInside: Boolean,
        source: String
    ) {
        val transition = when {
            isInside && !wasInside -> "ENTER"
            !isInside && wasInside -> "EXIT"
            isInside -> "INSIDE"
            else -> "OUTSIDE"
        }
        // Single-line structured log for easy grepping
        Log.d(
            TAG,
            "GEOFENCE_STATE src=$source state=${if (isInside) "INSIDE" else "OUTSIDE"} dist=${"%.1f".format(distanceMeters)}m radius=${radiusMeters}m acc=${"%.1f".format(accuracyMeters)}m transition=$transition"
        )

        // Expanded block for context
        Log.d(TAG, "--- Geofence State Details ---")
        Log.d(TAG, "Source: $source")
        Log.d(TAG, "Inside: $isInside  (WasInside: $wasInside)")
        Log.d(TAG, "Distance: ${"%.1f".format(distanceMeters)} m  |  Radius: ${radiusMeters} m  |  Accuracy: ${"%.1f".format(accuracyMeters)} m")
        Log.d(TAG, "Transition: $transition  |  Timestamp: ${System.currentTimeMillis()}")
        Log.d(TAG, "--------------------------------")
    }

    /**
     * Compute current distance and log state using last known location; allows forced inside/outside flag.
     */
    private fun logCurrentGeofenceState(context: Context, forcedInside: Boolean? = null) {
        try {
            val geofenceHelper = GeofenceHelper(context)
            val geofenceLocation = geofenceHelper.getCurrentGeofenceLocation() ?: return
            val last = BackgroundLocationService.getLastLocation(context)
            if (last == null) {
                Log.d(TAG, "GEOFENCE_STATE src=EVENT state=${if (forcedInside == true) "INSIDE" else if (forcedInside == false) "OUTSIDE" else "UNKNOWN"} dist=? radius=${GeofenceHelper.getGeofenceRadius()}m acc=? transition=NONE")
                return
            }
            val distance = LocationHelper(context).calculateDistance(
                LatLng(last.latitude, last.longitude), geofenceLocation
            ).toFloat() * 1000f
            val radius = GeofenceHelper.getGeofenceRadius()
            val acc = last.accuracy
            val wasInside = geofenceHelper.isUserInsideGeofence()
            val isInside = forcedInside ?: (distance <= (radius + acc))
            logGeofenceState(isInside, distance, radius, acc, wasInside, source = "EVENT")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging current geofence state", e)
        }
    }

    /**
     * Trigger arrival alarm with comprehensive notification system
     */
    private fun triggerArrivalAlarm(context: Context) {
        try {
            Log.d(TAG, "🚨 TRIGGERING ARRIVAL ALARM")
            
            val notificationService = NotificationService(context)
            val voiceService = VoiceAnnouncementService(context)
            val geofenceHelper = GeofenceHelper(context)
            
            // Check if navigation is active
            val isNavigationActive = isNavigationModeActive(context)
            val isVoiceEnabled = isVoiceAnnouncementEnabled(context)
            
            Log.d(TAG, "Alarm context - Navigation: $isNavigationActive, Voice: $isVoiceEnabled")
            
            if (isNavigationActive) {
                // Navigation mode - show destination reached alarm
                Log.d(TAG, "🎯 NAVIGATION MODE ALARM")
                
                notificationService.showAlarmNotification(
                    "🚨 DESTINATION REACHED!",
                    "You have arrived at your destination! Tap STOP ALARM to end navigation.",
                    GeofenceBroadcastReceiver.ARRIVAL_ALARM_ID
                )
                
                if (isVoiceEnabled) {
                    voiceService.announceArrival("your destination")
                    geofenceHelper.setVoiceAnnouncementTriggered(true)
                }
                
            } else {
                // Passive mode - show location reached alarm
                Log.d(TAG, "📍 PASSIVE MODE ALARM")
                
                notificationService.showAlarmNotification(
                    "📍 Location Reached!",
                    "You have arrived at your pinned location! Tap STOP ALARM to dismiss.",
                    GeofenceBroadcastReceiver.ARRIVAL_ALARM_ID
                )
                
                if (isVoiceEnabled) {
                    voiceService.announceArrival("your pinned location")
                    geofenceHelper.setVoiceAnnouncementTriggered(true)
                }
            }
            
            // Always play alarm sound and vibration
            notificationService.playAlarmSound()
            notificationService.triggerAlarmVibration()
            
            // Also attempt to surface the app if user enabled auto-open on geofence
            try {
                val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                val autoOpen = prefs.getBoolean("auto_open_on_geofence", false)
                if (autoOpen) {
                    val openIntent = Intent(context, DashboardActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("open_reason", "geofence")
                        putExtra("geofence_event", "enter")
                    }
                    context.startActivity(openIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error attempting to bring app to foreground after alarm", e)
            }
            
            Log.d(TAG, "✅ Arrival alarm sequence completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error triggering arrival alarm", e)
        }
    }

    /**
     * Check if navigation mode is currently active
     */
    private fun isNavigationModeActive(context: Context): Boolean {
        val prefs = context.getSharedPreferences("navigation_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("navigation_active", false)
    }

    /**
     * Check if voice announcements are enabled in settings
     */
    private fun isVoiceAnnouncementEnabled(context: Context): Boolean {
        val sharedPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        return sharedPrefs.getBoolean("voice_announcements", false)
    }

    fun setGeofenceEventListener(listener: GeofenceEventListener) {
        geofenceEventListener = listener
        globalListener = listener
        Log.d(TAG, "Geofence event listener set")
    }

    fun removeGeofenceEventListener() {
        geofenceEventListener = null
        globalListener = null
        Log.d(TAG, "Geofence event listener removed")
    }
}