package com.example.gzingapp.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.gzingapp.services.GeofenceHelper
import com.google.android.gms.maps.model.LatLng
class NavigationHelper(private val context: Context) {

    companion object {
        private const val TAG = "NavigationHelper"
        private const val PREFS_NAME = "navigation_prefs"
        private const val KEY_NAVIGATION_ACTIVE = "navigation_active"
        private const val KEY_DESTINATION_LAT = "destination_lat"
        private const val KEY_DESTINATION_LNG = "destination_lng"
        private const val KEY_NAVIGATION_START_TIME = "navigation_start_time"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val geofenceHelper: GeofenceHelper by lazy { GeofenceHelper(context) }

    /**
     * Start navigation to a destination with enhanced error handling
     */
    fun startNavigation(destination: LatLng, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        Log.d(TAG, "Starting navigation to: $destination")

        try {
            // Validate destination
            if (destination.latitude == 0.0 && destination.longitude == 0.0) {
                onFailure(IllegalArgumentException("Invalid destination coordinates"))
                return
            }

            // First, set up the geofence with navigation mode
            geofenceHelper.addGeofence(
                destination,
                onSuccess = {
                    try {
                        // Geofence created successfully, now activate navigation mode
                        setNavigationMode(true, destination)
                        Log.d(TAG, "Navigation started successfully")
                        onSuccess()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting navigation mode after geofence creation", e)
                        onFailure(e)
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to start navigation", error)
                    onFailure(error)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Critical error in startNavigation", e)
            onFailure(e)
        }
    }

    /**
     * Stop navigation with enhanced error handling
     */
    fun stopNavigation(onSuccess: (() -> Unit)? = null, onFailure: ((Exception) -> Unit)? = null) {
        Log.d(TAG, "Stopping navigation")

        try {
            // Turn off navigation mode first
            setNavigationMode(false)

            // Remove geofence (or keep it as passive monitoring)
            geofenceHelper.removeGeofence(
                onSuccess = {
                    Log.d(TAG, "Navigation stopped successfully")
                    onSuccess?.invoke()
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to stop navigation cleanly", error)
                    // Still consider navigation stopped even if geofence removal failed
                    onFailure?.invoke(error)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Critical error in stopNavigation", e)
            // Ensure navigation is marked as stopped even on error
            try {
                setNavigationMode(false)
            } catch (setModeError: Exception) {
                Log.e(TAG, "Failed to set navigation mode to false", setModeError)
            }
            onFailure?.invoke(e)
        }
    }

    /**
     * Pause navigation with error handling
     */
    fun pauseNavigation() {
        try {
            Log.d(TAG, "Pausing navigation")
            setNavigationMode(false)

            // Update geofence to passive mode
            geofenceHelper.setNavigationMode(false)
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing navigation", e)
        }
    }

    /**
     * Resume navigation with validation
     */
    fun resumeNavigation(): Boolean {
        return try {
            val destination = getCurrentDestination()

            if (destination != null && geofenceHelper.hasGeofence()) {
                Log.d(TAG, "Resuming navigation to: $destination")
                setNavigationMode(true, destination)
                geofenceHelper.setNavigationMode(true)
                true
            } else {
                Log.w(TAG, "Cannot resume navigation - no destination or geofence")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming navigation", e)
            false
        }
    }

    /**
     * Check if navigation is currently active with error handling
     */
    fun isNavigationActive(): Boolean {
        return try {
            prefs.getBoolean(KEY_NAVIGATION_ACTIVE, false)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking navigation active state", e)
            false
        }
    }

    /**
     * Get current destination with validation
     */
    fun getCurrentDestination(): LatLng? {
        return try {
            if (!isNavigationActive()) return null

            val lat = prefs.getFloat(KEY_DESTINATION_LAT, 0f).toDouble()
            val lng = prefs.getFloat(KEY_DESTINATION_LNG, 0f).toDouble()

            if (lat != 0.0 && lng != 0.0) {
                LatLng(lat, lng)
            } else {
                Log.w(TAG, "Invalid destination coordinates in preferences")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current destination", e)
            null
        }
    }

    /**
     * Get navigation duration with error handling
     */
    fun getNavigationDuration(): Long {
        return try {
            if (!isNavigationActive()) return 0

            val startTime = prefs.getLong(KEY_NAVIGATION_START_TIME, 0)
            if (startTime > 0) {
                System.currentTimeMillis() - startTime
            } else {
                0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting navigation duration", e)
            0
        }
    }

    /**
     * Get comprehensive navigation status with error handling
     */
    fun getNavigationStatus(): NavigationStatus {
        return try {
            val isActive = isNavigationActive()
            val destination = getCurrentDestination()
            val duration = getNavigationDuration()
            val geofenceStatus = geofenceHelper.getGeofenceStatus()

            NavigationStatus(
                isActive = isActive,
                destination = destination,
                durationMs = duration,
                geofenceRadius = geofenceStatus.radius,
                hasGeofence = geofenceStatus.hasGeofence,
                isGeofenceActive = geofenceStatus.isActive
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting navigation status", e)
            NavigationStatus(
                isActive = false,
                destination = null,
                durationMs = 0,
                geofenceRadius = 100f,
                hasGeofence = false,
                isGeofenceActive = false
            )
        }
    }

    /**
     * Internal method to set navigation mode state with error handling
     */
    private fun setNavigationMode(isActive: Boolean, destination: LatLng? = null) {
        try {
            with(prefs.edit()) {
                putBoolean(KEY_NAVIGATION_ACTIVE, isActive)

                if (isActive && destination != null) {
                    putFloat(KEY_DESTINATION_LAT, destination.latitude.toFloat())
                    putFloat(KEY_DESTINATION_LNG, destination.longitude.toFloat())
                    putLong(KEY_NAVIGATION_START_TIME, System.currentTimeMillis())
                } else if (!isActive) {
                    // Clear destination data when stopping navigation
                    remove(KEY_DESTINATION_LAT)
                    remove(KEY_DESTINATION_LNG)
                    remove(KEY_NAVIGATION_START_TIME)
                }

                apply()
            }

            // Update geofence helper
            geofenceHelper.setNavigationMode(isActive)

            Log.d(TAG, "Navigation mode set to: $isActive" +
                    if (destination != null) " for destination: $destination" else "")

        } catch (e: Exception) {
            Log.e(TAG, "Error setting navigation mode", e)
            throw e
        }
    }

    /**
     * Data class for navigation status
     */
    data class NavigationStatus(
        val isActive: Boolean,
        val destination: LatLng?,
        val durationMs: Long,
        val geofenceRadius: Float,
        val hasGeofence: Boolean,
        val isGeofenceActive: Boolean
    ) {
        fun getDurationString(): String {
            if (durationMs <= 0) return "Not started"

            val minutes = durationMs / 60000
            val seconds = (durationMs % 60000) / 1000

            return if (minutes > 0) {
                "${minutes}m ${seconds}s"
            } else {
                "${seconds}s"
            }
        }

        override fun toString(): String {
            return "NavigationStatus(active=$isActive, destination=$destination, " +
                    "duration=${getDurationString()}, radius=${geofenceRadius}m, " +
                    "hasGeofence=$hasGeofence, geofenceActive=$isGeofenceActive)"
        }
    }
}