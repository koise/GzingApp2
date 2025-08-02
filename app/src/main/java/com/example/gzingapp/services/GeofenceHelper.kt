package com.example.gzingapp.services

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.gzingapp.receivers.GeofenceBroadcastReceiver
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng

class GeofenceHelper(private val context: Context) {

    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)
    private var geofencePendingIntent: PendingIntent? = null
    private var currentGeofenceLocation: LatLng? = null
    private var isActiveGeofence = false

    companion object {
        private const val TAG = "GeofenceHelper"
        private const val GEOFENCE_ID = "pinned_location_geofence"

        // Configurable radius - static for now, can be made dynamic later
        var GEOFENCE_RADIUS = 100.0f // radius in meters (changeable)

        private const val GEOFENCE_EXPIRATION = Geofence.NEVER_EXPIRE
        private const val GEOFENCE_DWELL_TIME = 5000 // time in milliseconds

        // Retry configuration
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 2000L

        // Method to update geofence radius
        fun setGeofenceRadius(radiusInMeters: Float) {
            GEOFENCE_RADIUS = radiusInMeters
            Log.d(TAG, "Geofence radius updated to: ${GEOFENCE_RADIUS}m")
        }

        // Method to get current radius
        fun getGeofenceRadius(): Float {
            return GEOFENCE_RADIUS
        }
    }

    /**
     * Check if Google Play Services is available and updated
     */
    private fun isGooglePlayServicesAvailable(): Boolean {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)

        if (resultCode != ConnectionResult.SUCCESS) {
            Log.e(TAG, "Google Play Services not available. Error code: $resultCode")
            return false
        }

        Log.d(TAG, "Google Play Services is available")
        return true
    }

    /**
     * Create a geofencing request for the pinned location
     */
    private fun createGeofencingRequest(geofence: Geofence): GeofencingRequest {
        return GeofencingRequest.Builder()
            .setInitialTrigger(
                GeofencingRequest.INITIAL_TRIGGER_ENTER
                        or GeofencingRequest.INITIAL_TRIGGER_DWELL
            )
            .addGeofence(geofence)
            .build()
    }

    /**
     * Create a geofence at the specified location with current radius setting
     */
    private fun createGeofence(latLng: LatLng): Geofence {
        Log.d(TAG, "Creating geofence with radius: ${GEOFENCE_RADIUS}m at ${latLng.latitude}, ${latLng.longitude}")

        return Geofence.Builder()
            .setRequestId(GEOFENCE_ID)
            .setCircularRegion(
                latLng.latitude,
                latLng.longitude,
                GEOFENCE_RADIUS
            )
            .setExpirationDuration(GEOFENCE_EXPIRATION)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER
                        or Geofence.GEOFENCE_TRANSITION_DWELL
                        or Geofence.GEOFENCE_TRANSITION_EXIT
            )
            .setLoiteringDelay(GEOFENCE_DWELL_TIME)
            .build()
    }

    /**
     * Get the pending intent for geofence transitions
     */
    private fun getGeofencePendingIntent(): PendingIntent {
        if (geofencePendingIntent != null) {
            return geofencePendingIntent!!
        }

        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        geofencePendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return geofencePendingIntent!!
    }

    /**
     * Add geofence around the pinned location with improved error handling and retry logic
     */
    fun addGeofence(latLng: LatLng, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        addGeofenceWithRetry(latLng, onSuccess, onFailure, 0)
    }

    /**
     * Internal method with retry logic - FIXED VERSION
     */
    private fun addGeofenceWithRetry(
        latLng: LatLng,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit,
        attemptCount: Int
    ) {
        try {
            // First check if Google Play Services is available
            if (!isGooglePlayServicesAvailable()) {
                val error = Exception("Google Play Services is not available or needs to be updated")
                Log.e(TAG, "Google Play Services check failed")
                onFailure(error)
                return
            }

            // Remove any existing geofence first
            removeGeofence(
                onSuccess = {
                    // Success callback for removal
                    Log.d(TAG, "Previous geofence removed, adding new one (attempt ${attemptCount + 1})")
                    addNewGeofenceInternal(latLng, onSuccess, onFailure, attemptCount)
                },
                onFailure = { removeError ->
                    Log.w(TAG, "Failed to remove previous geofence, continuing anyway", removeError)
                    addNewGeofenceInternal(latLng, onSuccess, onFailure, attemptCount)
                }
            )

        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception when adding geofence", e)
            onFailure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error when adding geofence", e)
            onFailure(e)
        }
    }

    /**
     * Internal method to add new geofence after cleanup - FIXED VERSION
     */
    private fun addNewGeofenceInternal(
        latLng: LatLng,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit,
        attemptCount: Int
    ) {
        try {
            // Create new geofence with current radius setting
            val geofence = createGeofence(latLng)
            val geofencingRequest = createGeofencingRequest(geofence)

            Log.d(TAG, "Attempting to add geofence (attempt ${attemptCount + 1})...")

            geofencingClient.addGeofences(geofencingRequest, getGeofencePendingIntent())
                .addOnSuccessListener {
                    Log.d(TAG, "Geofence added successfully with ${GEOFENCE_RADIUS}m radius")
                    currentGeofenceLocation = latLng
                    isActiveGeofence = true
                    onSuccess()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to add geofence (attempt ${attemptCount + 1})", e)

                    // Check if we should retry
                    if (attemptCount < MAX_RETRY_ATTEMPTS - 1 && shouldRetry(e)) {
                        Log.d(TAG, "Retrying geofence creation in ${RETRY_DELAY_MS}ms...")

                        // Use Handler instead of coroutines (FIXED)
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            addGeofenceWithRetry(latLng, onSuccess, onFailure, attemptCount + 1)
                        }, RETRY_DELAY_MS)
                    } else {
                        // Provide more detailed error information
                        val detailedError = when {
                            e.message?.contains("1000") == true -> {
                                Exception("Location services are temporarily unavailable. Please ensure location is enabled and GPS is working.", e)
                            }
                            e.message?.contains("SecurityException") == true -> {
                                Exception("Location permission denied. Please grant background location access in Settings.", e)
                            }
                            e.message?.contains("2") == true -> {
                                Exception("Geofence service is not available. Please try again later.", e)
                            }
                            else -> {
                                Exception("Failed to create geofence after ${attemptCount + 1} attempts: ${e.message}", e)
                            }
                        }

                        currentGeofenceLocation = null
                        isActiveGeofence = false
                        onFailure(detailedError)
                    }
                }

        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception in addNewGeofenceInternal", e)
            currentGeofenceLocation = null
            isActiveGeofence = false
            onFailure(Exception("Location permission denied. Please grant background location access.", e))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in addNewGeofenceInternal", e)
            currentGeofenceLocation = null
            isActiveGeofence = false
            onFailure(e)
        }
    }

    /**
     * Determine if we should retry based on the error
     */
    private fun shouldRetry(error: Throwable): Boolean {
        val message = error.message?.lowercase() ?: ""
        return when {
            message.contains("1000") -> true // GEOFENCE_NOT_AVAILABLE - temporary issue
            message.contains("temporarily unavailable") -> true
            message.contains("service not available") -> true
            message.contains("network") -> true
            message.contains("timeout") -> true
            else -> false
        }
    }

    /**
     * Add geofence automatically when location is pinned (without navigation)
     */
    fun addAutomaticGeofence(latLng: LatLng) {
        try {
            // First check if Google Play Services is available
            if (!isGooglePlayServicesAvailable()) {
                Log.w(TAG, "Cannot add automatic geofence - Google Play Services not available")
                return
            }

            // Remove any existing geofence first
            removeGeofence()

            // Create new geofence with current radius setting
            val geofence = createGeofence(latLng)
            val geofencingRequest = createGeofencingRequest(geofence)

            geofencingClient.addGeofences(geofencingRequest, getGeofencePendingIntent())
                .addOnSuccessListener {
                    Log.d(TAG, "Automatic geofence added successfully with ${GEOFENCE_RADIUS}m radius")
                    currentGeofenceLocation = latLng
                    isActiveGeofence = false // Not in navigation mode
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to add automatic geofence", e)
                    currentGeofenceLocation = null
                    isActiveGeofence = false
                }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception when adding automatic geofence", e)
            currentGeofenceLocation = null
            isActiveGeofence = false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error when adding automatic geofence", e)
            currentGeofenceLocation = null
            isActiveGeofence = false
        }
    }

    /**
     * Update existing geofence with new radius settings
     */
    fun updateGeofenceRadius() {
        currentGeofenceLocation?.let { location ->
            Log.d(TAG, "Updating geofence radius to ${GEOFENCE_RADIUS}m")

            if (isActiveGeofence) {
                // Navigation is active, update with callbacks
                addGeofence(location,
                    onSuccess = {
                        Log.d(TAG, "Geofence radius updated successfully during navigation")
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Failed to update geofence radius during navigation", e)
                    }
                )
            } else {
                // Just pinned location, update automatically
                addAutomaticGeofence(location)
            }
        }
    }

    /**
     * Remove any active geofence with improved error handling and timeout
     */
    fun removeGeofence(onSuccess: (() -> Unit)? = null, onFailure: ((Exception) -> Unit)? = null) {
        try {
            Log.d(TAG, "Attempting to remove geofence...")

            val task = geofencingClient.removeGeofences(getGeofencePendingIntent())

            task.addOnSuccessListener {
                Log.d(TAG, "Geofence removed successfully")
                currentGeofenceLocation = null
                isActiveGeofence = false
                onSuccess?.invoke()
            }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to remove geofence", e)
                    // Still clear our state even if removal failed
                    currentGeofenceLocation = null
                    isActiveGeofence = false
                    onFailure?.invoke(e)
                }

            // Add timeout handling
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (!task.isComplete) {
                    Log.w(TAG, "Geofence removal timed out, clearing state")
                    currentGeofenceLocation = null
                    isActiveGeofence = false
                    onFailure?.invoke(Exception("Geofence removal timed out"))
                }
            }, 10000) // 10 second timeout

        } catch (e: Exception) {
            Log.e(TAG, "Exception when removing geofence", e)
            // Clear state on any error
            currentGeofenceLocation = null
            isActiveGeofence = false
            onFailure?.invoke(e)
        }
    }

    /**
     * Force clear geofence state (use when geofence removal fails)
     */
    fun forceCleanup() {
        Log.d(TAG, "Force cleaning up geofence state")
        currentGeofenceLocation = null
        isActiveGeofence = false

        // Try to recreate pending intent to clear any stuck references
        geofencePendingIntent?.cancel()
        geofencePendingIntent = null
    }

    /**
     * Get current geofence location
     */
    fun getCurrentGeofenceLocation(): LatLng? = currentGeofenceLocation

    /**
     * Check if geofence is active (navigation mode)
     */
    fun isGeofenceActive(): Boolean = isActiveGeofence

    /**
     * Check if there's any geofence (active or passive)
     */
    fun hasGeofence(): Boolean = currentGeofenceLocation != null

    /**
     * Set navigation mode for existing geofence
     */
    fun setNavigationMode(isNavigating: Boolean) {
        isActiveGeofence = isNavigating
        Log.d(TAG, "Geofence navigation mode set to: $isNavigating")
    }

    /**
     * Get comprehensive geofence status info
     */
    fun getGeofenceStatus(): GeofenceStatus {
        return GeofenceStatus(
            location = currentGeofenceLocation,
            radius = GEOFENCE_RADIUS,
            isActive = isActiveGeofence,
            hasGeofence = currentGeofenceLocation != null,
            isGooglePlayServicesAvailable = isGooglePlayServicesAvailable()
        )
    }

    /**
     * Test geofence functionality
     */
    fun testGeofenceCapability(onResult: (Boolean, String) -> Unit) {
        try {
            // Check Google Play Services
            if (!isGooglePlayServicesAvailable()) {
                onResult(false, "Google Play Services not available")
                return
            }

            // Try to create a test geofence at a dummy location
            val testLocation = LatLng(0.0, 0.0)
            val testGeofence = createGeofence(testLocation)
            val testRequest = createGeofencingRequest(testGeofence)

            // Just test the creation, don't actually add it
            Log.d(TAG, "Geofence capability test passed")
            onResult(true, "Geofence capability available")

        } catch (e: SecurityException) {
            onResult(false, "Location permission required: ${e.message}")
        } catch (e: Exception) {
            onResult(false, "Geofence test failed: ${e.message}")
        }
    }

    /**
     * Data class for geofence status
     */
    data class GeofenceStatus(
        val location: LatLng?,
        val radius: Float,
        val isActive: Boolean,
        val hasGeofence: Boolean,
        val isGooglePlayServicesAvailable: Boolean
    ) {
        override fun toString(): String {
            return "GeofenceStatus(location=$location, radius=${radius}m, active=$isActive, hasGeofence=$hasGeofence, playServicesOK=$isGooglePlayServicesAvailable)"
        }
    }
}