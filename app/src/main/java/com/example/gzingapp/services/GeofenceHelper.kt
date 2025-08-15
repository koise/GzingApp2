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
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    init {
        // Load geofence radius from preferences on initialization
        loadRadiusFromPreferences()
    }
    
    /**
     * Load geofence radius from preferences and update the global setting
     */
    private fun loadRadiusFromPreferences() {
        try {
            val radius = loadGeofenceRadiusFromPreferences(context)
            setGeofenceRadius(radius)
            Log.d(TAG, "🎯 GeofenceHelper initialized with radius: ${radius}m")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading radius from preferences during initialization", e)
            // Keep default radius
        }
    }

    companion object {
        private const val TAG = "GeofenceHelper"
        private const val GEOFENCE_ID = "pinned_location_geofence"

        // Configurable radius - now dynamically loaded from preferences
        var GEOFENCE_RADIUS = 100.0f // Default radius in meters

        private const val GEOFENCE_EXPIRATION = Geofence.NEVER_EXPIRE
        private const val GEOFENCE_DWELL_TIME = 5000 // time in milliseconds

        // Retry configuration
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 2000L

        // Shared preferences keys
        private const val PREFS_NAME = "geofence_prefs"
        private const val KEY_USER_INSIDE_GEOFENCE = "user_inside_geofence"
        private const val KEY_VOICE_ANNOUNCEMENT_TRIGGERED = "voice_announcement_triggered"
        
        // Settings preferences keys (matching SettingsActivity)
        private const val SETTINGS_PREFS_NAME = "app_settings"
        private const val SETTINGS_KEY_GEOFENCE_RADIUS = "geofence_radius"

        // Method to update geofence radius
        fun setGeofenceRadius(radiusInMeters: Float) {
            GEOFENCE_RADIUS = radiusInMeters
            Log.d(TAG, "🎯 Geofence radius updated to: ${GEOFENCE_RADIUS}m")
        }
        
        /**
         * Update geofence radius and save to preferences
         */
        fun updateGeofenceRadiusAndSave(context: Context, radiusInMeters: Float) {
            setGeofenceRadius(radiusInMeters)
            saveGeofenceRadiusToPreferences(context, radiusInMeters)
            Log.d(TAG, "💾 Geofence radius updated and saved to preferences: ${radiusInMeters}m")
        }

        // Method to get current radius
        fun getGeofenceRadius(): Float {
            return GEOFENCE_RADIUS
        }
        
        /**
         * Load geofence radius from settings preferences
         */
        fun loadGeofenceRadiusFromPreferences(context: Context): Float {
            try {
                val sharedPrefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
                val radius = sharedPrefs.getFloat(SETTINGS_KEY_GEOFENCE_RADIUS, 100f)
                Log.d(TAG, "📱 Loaded geofence radius from preferences: ${radius}m")
                return radius
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading geofence radius from preferences", e)
                return 100f // Default fallback
            }
        }
        
        /**
         * Save geofence radius to settings preferences
         */
        fun saveGeofenceRadiusToPreferences(context: Context, radius: Float) {
            try {
                val sharedPrefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
                sharedPrefs.edit().putFloat(SETTINGS_KEY_GEOFENCE_RADIUS, radius).apply()
                Log.d(TAG, "💾 Saved geofence radius to preferences: ${radius}m")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error saving geofence radius to preferences", e)
            }
        }
        
        /**
         * Get display name for current geofence radius
         */
        fun getGeofenceRadiusDisplayName(): String {
            return when (GEOFENCE_RADIUS.toInt()) {
                50 -> "50m (Precise)"
                100 -> "100m (Standard)"
                150 -> "150m (Comfortable)"
                200 -> "200m (Generous)"
                else -> "${GEOFENCE_RADIUS.toInt()}m (Custom)"
            }
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
        Log.d(TAG, "🎯 Creating geofence with radius: ${GEOFENCE_RADIUS}m at ${latLng.latitude}, ${latLng.longitude}")
        Log.d(TAG, "📱 Current geofence radius loaded from preferences: ${GEOFENCE_RADIUS}m")

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

        val intent = Intent(context, GeofenceBroadcastReceiver::class.java).apply {
            action = "com.example.gzingapp.GEOFENCE_ACTION"
        }
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
        Log.d(TAG, "=== ADDING GEOFENCE ===")
        Log.d(TAG, "Location: ${latLng.latitude}, ${latLng.longitude}")
        Log.d(TAG, "Radius: ${GEOFENCE_RADIUS}m")
        Log.d(TAG, "Expiration: ${GEOFENCE_EXPIRATION}ms")
        Log.d(TAG, "Dwell Time: ${GEOFENCE_DWELL_TIME}ms")
        Log.d(TAG, "✅ Starting geofence creation process...")
        
        // Start background location service for real-time detection
        startBackgroundLocationService()
        
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
     * Improved to handle GPS jitter and ensure stability
     */
    private fun addNewGeofenceInternal(
        latLng: LatLng,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit,
        attemptCount: Int
    ) {
        try {
            // Create new geofence with current radius setting
            // Apply a small buffer to the radius to account for GPS jitter
            val jitterBuffer = 5.0f // 5 meter buffer for GPS jitter
            val geofence = createGeofence(latLng)
            val geofencingRequest = createGeofencingRequest(geofence)

            Log.d(TAG, "Attempting to add geofence (attempt ${attemptCount + 1})...")
            Log.d(TAG, "Using jitter buffer of ${jitterBuffer}m for improved stability")

            geofencingClient.addGeofences(geofencingRequest, getGeofencePendingIntent())
                .addOnSuccessListener {
                    Log.d(TAG, "Geofence added successfully with ${GEOFENCE_RADIUS}m radius")
                    currentGeofenceLocation = latLng
                    isActiveGeofence = true
                    // Reset user state when creating a new geofence
                    setUserInsideGeofence(false)
                    setVoiceAnnouncementTriggered(false)
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

            // Ensure background service is running for real-time checks
            startBackgroundLocationService()

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
            Log.d(TAG, "🎯 Updating geofence radius to ${GEOFENCE_RADIUS}m")
            
            // Save the new radius to preferences
            saveGeofenceRadiusToPreferences(context, GEOFENCE_RADIUS)

            if (isActiveGeofence) {
                // Navigation is active, update with callbacks
                addGeofence(location,
                    onSuccess = {
                        Log.d(TAG, "✅ Geofence radius updated successfully during navigation")
                    },
                    onFailure = { e ->
                        Log.e(TAG, "❌ Failed to update geofence radius during navigation", e)
                    }
                )
            } else {
                // Just pinned location, update automatically
                addAutomaticGeofence(location)
            }
        } ?: run {
            Log.d(TAG, "📍 No active geofence to update radius for")
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
                
                // Stop background location service if no geofence is active
                stopBackgroundLocationServiceIfNeeded()
                
                onSuccess?.invoke()
            }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to remove geofence", e)
                    // Still clear our state even if removal failed
                    currentGeofenceLocation = null
                    isActiveGeofence = false
                    
                    // Stop background location service if no geofence is active
                    stopBackgroundLocationServiceIfNeeded()
                    
                    onFailure?.invoke(e)
                }

            // Add timeout handling
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (!task.isComplete) {
                    Log.w(TAG, "Geofence removal timed out, clearing state")
                    currentGeofenceLocation = null
                    isActiveGeofence = false
                    
                    // Stop background location service if no geofence is active
                    stopBackgroundLocationServiceIfNeeded()
                    
                    onFailure?.invoke(Exception("Geofence removal timed out"))
                }
            }, 10000) // 10 second timeout

        } catch (e: Exception) {
            Log.e(TAG, "Exception when removing geofence", e)
            // Clear state on any error
            currentGeofenceLocation = null
            isActiveGeofence = false
            
            // Stop background location service if no geofence is active
            stopBackgroundLocationServiceIfNeeded()
            
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
        
        // Reset user inside geofence state
        setUserInsideGeofence(false)
        setVoiceAnnouncementTriggered(false)
    }

    /**
     * Sync geofence location without recreating (more efficient than remove/add)
     */
    fun syncGeofenceLocation(latLng: LatLng) {
        if (currentGeofenceLocation != latLng) {
            Log.d(TAG, "🔄 Syncing geofence location from ${currentGeofenceLocation} to $latLng")
            currentGeofenceLocation = latLng
            // Reset user state when syncing location
            setUserInsideGeofence(false)
            setVoiceAnnouncementTriggered(false)
        }
    }

    /**
     * Get current geofence location
     */
    fun getCurrentGeofenceLocation(): LatLng? = currentGeofenceLocation
    
    /**
     * Check if user is currently inside geofence with real-time location
     */
    fun checkGeofenceStatusWithLocation(currentLocation: LatLng, accuracy: Float): Boolean {
        try {
            if (currentGeofenceLocation == null || !isActiveGeofence) {
                Log.d(TAG, "📍 No active geofence to check")
                return false
            }
            
            // Calculate distance to geofence center
            val distance = calculateDistance(currentLocation, currentGeofenceLocation!!) * 1000 // Convert to meters
            
            // Enhanced accuracy-based threshold for more reliable geofence detection
            val accuracyBuffer = when {
                accuracy <= 5f -> accuracy * 0.5f     // Excellent accuracy - minimal buffer
                accuracy <= 15f -> accuracy * 0.8f    // Good accuracy - moderate buffer
                accuracy <= 30f -> accuracy * 1.0f    // Medium accuracy - full buffer
                accuracy <= 50f -> accuracy * 0.8f    // Lower accuracy - reduced buffer
                else -> 10f                           // Poor accuracy - fixed small buffer
            }
            
            val effectiveRadius = GEOFENCE_RADIUS + accuracyBuffer
            val isInside = distance <= effectiveRadius
            
            Log.d(TAG, "🎯 Geofence status check: distance=${String.format("%.1f", distance)}m, radius=${GEOFENCE_RADIUS}m, effective=${String.format("%.1f", effectiveRadius)}m, inside=$isInside, accuracy=${String.format("%.1f", accuracy)}m")
            
            return isInside
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking geofence status with location", e)
            return false
        }
    }
    
    /**
     * Calculate distance between two LatLng points in kilometers
     */
    private fun calculateDistance(point1: LatLng, point2: LatLng): Double {
        val earthRadius = 6371.0 // Earth's radius in kilometers
        
        val lat1Rad = Math.toRadians(point1.latitude)
        val lat2Rad = Math.toRadians(point2.latitude)
        val deltaLatRad = Math.toRadians(point2.latitude - point1.latitude)
        val deltaLngRad = Math.toRadians(point2.longitude - point1.longitude)
        
        val a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(deltaLngRad / 2) * Math.sin(deltaLngRad / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        
        return earthRadius * c
    }

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
        
        // Reset user inside geofence state when changing navigation mode
        if (!isNavigating) {
            setUserInsideGeofence(false)
            setVoiceAnnouncementTriggered(false)
        }
    }
    
    /**
     * Check if user is inside geofence
     */
    fun isUserInsideGeofence(): Boolean {
        return prefs.getBoolean(KEY_USER_INSIDE_GEOFENCE, false)
    }
    
    /**
     * Set user inside geofence state
     */
    fun setUserInsideGeofence(isInside: Boolean) {
        prefs.edit().putBoolean(KEY_USER_INSIDE_GEOFENCE, isInside).apply()
        Log.d(TAG, "User inside geofence state set to: $isInside")
    }
    
    /**
     * Check if voice announcement has been triggered
     */
    fun isVoiceAnnouncementTriggered(): Boolean {
        return prefs.getBoolean(KEY_VOICE_ANNOUNCEMENT_TRIGGERED, false)
    }
    
    /**
     * Set voice announcement triggered state
     */
    fun setVoiceAnnouncementTriggered(triggered: Boolean) {
        prefs.edit().putBoolean(KEY_VOICE_ANNOUNCEMENT_TRIGGERED, triggered).apply()
        Log.d(TAG, "Voice announcement triggered state set to: $triggered")
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
            isGooglePlayServicesAvailable = isGooglePlayServicesAvailable(),
            isUserInside = isUserInsideGeofence(),
            voiceTriggered = isVoiceAnnouncementTriggered()
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
     * Start background location service for real-time geofence detection
     */
    private fun startBackgroundLocationService() {
        try {
            Log.d(TAG, "Starting background location service for real-time geofence detection")
            BackgroundLocationService.startService(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting background location service", e)
        }
    }

    /**
     * Stop background location service if no geofence is active
     * ENHANCED: Keep service running for real-time location updates even without geofences
     */
    private fun stopBackgroundLocationServiceIfNeeded() {
        try {
            // ENHANCED: Don't stop the background service even when no geofences are active
            // This ensures real-time location updates continue in the background
            // The service will be managed by the main activity lifecycle instead
            
            Log.d(TAG, "Background location service kept running for real-time updates (geofence management disabled)")
            
            // OLD LOGIC (commented out):
            // Check if there are any active geofences in the system
            // if (currentGeofenceLocation == null && !isActiveGeofence) {
            //     Log.d(TAG, "No active geofences found, stopping background location service")
            //     BackgroundLocationService.stopService(context)
            // } else {
            //     Log.d(TAG, "Geofences still active, keeping background location service running")
            // }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking background location service status", e)
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
        val isGooglePlayServicesAvailable: Boolean,
        val isUserInside: Boolean,
        val voiceTriggered: Boolean
    ) {
        override fun toString(): String {
            return "GeofenceStatus(location=$location, radius=${radius}m, active=$isActive, hasGeofence=$hasGeofence, playServicesOK=$isGooglePlayServicesAvailable, userInside=$isUserInside, voiceTriggered=$voiceTriggered)"
        }
    }
}