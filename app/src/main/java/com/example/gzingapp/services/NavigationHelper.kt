package com.example.gzingapp.services

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.gzingapp.services.GeofenceHelper
import com.google.android.gms.maps.model.LatLng
import java.util.concurrent.atomic.AtomicBoolean

class NavigationHelper(private val context: Context) {

    companion object {
        private const val TAG = "NavigationHelper"
        private const val PREFS_NAME = "navigation_prefs"
        private const val KEY_NAVIGATION_ACTIVE = "navigation_active"
        private const val KEY_DESTINATION_LAT = "destination_lat"
        private const val KEY_DESTINATION_LNG = "destination_lng"
        private const val KEY_NAVIGATION_START_TIME = "navigation_start_time"

        // Timeout constants
        private const val NAVIGATION_START_TIMEOUT = 30000L // 30 seconds
        private const val NAVIGATION_STOP_TIMEOUT = 15000L // 15 seconds
        private const val GEOFENCE_SETUP_TIMEOUT = 20000L // 20 seconds
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val geofenceHelper: GeofenceHelper by lazy { GeofenceHelper(context) }
    private val mainHandler = Handler(Looper.getMainLooper())

    // Loading state management
    private val isStartingNavigation = AtomicBoolean(false)
    private val isStoppingNavigation = AtomicBoolean(false)
    private val isPausingNavigation = AtomicBoolean(false)
    private val isResumingNavigation = AtomicBoolean(false)

    // Timeout handlers
    private var startNavigationTimeoutHandler: Runnable? = null
    private var stopNavigationTimeoutHandler: Runnable? = null

    /**
     * Navigation state enum for better state management
     */
    enum class NavigationState {
        IDLE,
        STARTING,
        ACTIVE,
        STOPPING,
        PAUSED,
        RESUMING,
        ERROR
    }

    private var currentState = NavigationState.IDLE

    /**
     * Start navigation to a destination with enhanced error handling, loading states, and timeout
     */
    fun startNavigation(
        destination: LatLng,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit,
        onLoadingStateChanged: ((Boolean) -> Unit)? = null
    ) {
        Log.d(TAG, "Starting navigation to: $destination")

        // Check if already starting navigation
        if (!isStartingNavigation.compareAndSet(false, true)) {
            onFailure(IllegalStateException("Navigation is already being started"))
            return
        }

        // Update state and notify loading
        currentState = NavigationState.STARTING
        onLoadingStateChanged?.invoke(true)

        // Set up timeout for navigation start
        startNavigationTimeoutHandler = Runnable {
            if (isStartingNavigation.get() && currentState == NavigationState.STARTING) {
                Log.e(TAG, "Navigation start timeout reached")
                handleNavigationStartFailure(
                    Exception("Navigation start timeout - operation took too long"),
                    onFailure,
                    onLoadingStateChanged
                )
            }
        }
        mainHandler.postDelayed(startNavigationTimeoutHandler!!, NAVIGATION_START_TIMEOUT)

        try {
            // Validate destination
            if (destination.latitude == 0.0 && destination.longitude == 0.0) {
                handleNavigationStartFailure(
                    IllegalArgumentException("Invalid destination coordinates"),
                    onFailure,
                    onLoadingStateChanged
                )
                return
            }

            // Validate current state
            if (currentState == NavigationState.ACTIVE) {
                handleNavigationStartFailure(
                    IllegalStateException("Navigation is already active"),
                    onFailure,
                    onLoadingStateChanged
                )
                return
            }

            // First, set up the geofence with navigation mode
            geofenceHelper.addGeofence(
                destination,
                onSuccess = {
                    try {
                        // Cancel timeout as we're making progress
                        startNavigationTimeoutHandler?.let { mainHandler.removeCallbacks(it) }

                        // Geofence created successfully, now activate navigation mode
                        setNavigationMode(true, destination)

                        // Update state
                        currentState = NavigationState.ACTIVE
                        isStartingNavigation.set(false)
                        onLoadingStateChanged?.invoke(false)

                        Log.d(TAG, "Navigation started successfully")
                        onSuccess()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting navigation mode after geofence creation", e)
                        handleNavigationStartFailure(e, onFailure, onLoadingStateChanged)
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to start navigation - geofence creation failed", error)
                    handleNavigationStartFailure(error, onFailure, onLoadingStateChanged)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Critical error in startNavigation", e)
            handleNavigationStartFailure(e, onFailure, onLoadingStateChanged)
        }
    }

    /**
     * Stop navigation with enhanced error handling, loading states, and timeout
     */
    fun stopNavigation(
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null,
        onLoadingStateChanged: ((Boolean) -> Unit)? = null
    ) {
        Log.d(TAG, "Stopping navigation")

        // Check if already stopping navigation
        if (!isStoppingNavigation.compareAndSet(false, true)) {
            onFailure?.invoke(IllegalStateException("Navigation is already being stopped"))
            return
        }

        // Update state and notify loading
        currentState = NavigationState.STOPPING
        onLoadingStateChanged?.invoke(true)

        // Set up timeout for navigation stop
        stopNavigationTimeoutHandler = Runnable {
            if (isStoppingNavigation.get() && currentState == NavigationState.STOPPING) {
                Log.e(TAG, "Navigation stop timeout reached")
                handleNavigationStopFailure(
                    Exception("Navigation stop timeout - operation took too long"),
                    onFailure,
                    onLoadingStateChanged
                )
            }
        }
        mainHandler.postDelayed(stopNavigationTimeoutHandler!!, NAVIGATION_STOP_TIMEOUT)

        try {
            // Turn off navigation mode first
            setNavigationMode(false)

            // Remove geofence (or keep it as passive monitoring)
            geofenceHelper.removeGeofence(
                onSuccess = {
                    // Cancel timeout
                    stopNavigationTimeoutHandler?.let { mainHandler.removeCallbacks(it) }

                    // Update state
                    currentState = NavigationState.IDLE
                    isStoppingNavigation.set(false)
                    onLoadingStateChanged?.invoke(false)

                    Log.d(TAG, "Navigation stopped successfully")
                    onSuccess?.invoke()
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to stop navigation cleanly", error)
                    // Still consider navigation stopped even if geofence removal failed
                    handleNavigationStopFailure(error, onFailure, onLoadingStateChanged)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Critical error in stopNavigation", e)
            handleNavigationStopFailure(e, onFailure, onLoadingStateChanged)
        }
    }

    /**
     * Pause navigation with loading state and error handling
     */
    fun pauseNavigation(onLoadingStateChanged: ((Boolean) -> Unit)? = null) {
        if (!isPausingNavigation.compareAndSet(false, true)) {
            Log.w(TAG, "Navigation is already being paused")
            return
        }

        try {
            Log.d(TAG, "Pausing navigation")
            onLoadingStateChanged?.invoke(true)

            currentState = NavigationState.PAUSED
            setNavigationMode(false)

            // Update geofence to passive mode
            geofenceHelper.setNavigationMode(false)

            // Simulate brief loading for UI feedback
            mainHandler.postDelayed({
                isPausingNavigation.set(false)
                onLoadingStateChanged?.invoke(false)
            }, 1000)

        } catch (e: Exception) {
            Log.e(TAG, "Error pausing navigation", e)
            isPausingNavigation.set(false)
            onLoadingStateChanged?.invoke(false)
        }
    }

    /**
     * Resume navigation with validation and loading state
     */
    fun resumeNavigation(onLoadingStateChanged: ((Boolean) -> Unit)? = null): Boolean {
        if (!isResumingNavigation.compareAndSet(false, true)) {
            Log.w(TAG, "Navigation is already being resumed")
            return false
        }

        return try {
            onLoadingStateChanged?.invoke(true)
            val destination = getCurrentDestination()

            if (destination != null && geofenceHelper.hasGeofence()) {
                Log.d(TAG, "Resuming navigation to: $destination")

                currentState = NavigationState.RESUMING
                setNavigationMode(true, destination)
                geofenceHelper.setNavigationMode(true)

                // Simulate brief loading for UI feedback
                mainHandler.postDelayed({
                    currentState = NavigationState.ACTIVE
                    isResumingNavigation.set(false)
                    onLoadingStateChanged?.invoke(false)
                }, 1500)

                true
            } else {
                Log.w(TAG, "Cannot resume navigation - no destination or geofence")
                isResumingNavigation.set(false)
                onLoadingStateChanged?.invoke(false)
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming navigation", e)
            isResumingNavigation.set(false)
            onLoadingStateChanged?.invoke(false)
            false
        }
    }

    /**
     * Check if navigation is currently active with error handling
     */
    fun isNavigationActive(): Boolean {
        return try {
            prefs.getBoolean(KEY_NAVIGATION_ACTIVE, false) && currentState == NavigationState.ACTIVE
        } catch (e: Exception) {
            Log.e(TAG, "Error checking navigation active state", e)
            false
        }
    }

    /**
     * Get current navigation state
     */
    fun getNavigationState(): NavigationState {
        return currentState
    }

    /**
     * Check if any navigation operation is in progress
     */
    fun isOperationInProgress(): Boolean {
        return isStartingNavigation.get() || isStoppingNavigation.get() ||
                isPausingNavigation.get() || isResumingNavigation.get() ||
                currentState in listOf(NavigationState.STARTING, NavigationState.STOPPING, NavigationState.RESUMING)
    }

    /**
     * Get current destination with validation
     */
    fun getCurrentDestination(): LatLng? {
        return try {
            if (!isNavigationActive() && currentState != NavigationState.PAUSED) return null

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
            if (!isNavigationActive() && currentState != NavigationState.PAUSED) return 0

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
                isGeofenceActive = geofenceStatus.isActive,
                navigationState = currentState,
                isOperationInProgress = isOperationInProgress()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting navigation status", e)
            NavigationStatus(
                isActive = false,
                destination = null,
                durationMs = 0,
                geofenceRadius = 100f,
                hasGeofence = false,
                isGeofenceActive = false,
                navigationState = NavigationState.ERROR,
                isOperationInProgress = false
            )
        }
    }

    /**
     * Cancel any ongoing operation (useful for cleanup)
     */
    fun cancelOngoingOperations() {
        try {
            // Cancel timeouts
            startNavigationTimeoutHandler?.let { mainHandler.removeCallbacks(it) }
            stopNavigationTimeoutHandler?.let { mainHandler.removeCallbacks(it) }

            // Reset loading states
            isStartingNavigation.set(false)
            isStoppingNavigation.set(false)
            isPausingNavigation.set(false)
            isResumingNavigation.set(false)

            // If we were in a transitional state, try to determine the actual state
            when (currentState) {
                NavigationState.STARTING -> {
                    currentState = if (prefs.getBoolean(KEY_NAVIGATION_ACTIVE, false)) {
                        NavigationState.ACTIVE
                    } else {
                        NavigationState.IDLE
                    }
                }
                NavigationState.STOPPING -> {
                    currentState = NavigationState.IDLE
                }
                NavigationState.RESUMING -> {
                    currentState = if (prefs.getBoolean(KEY_NAVIGATION_ACTIVE, false)) {
                        NavigationState.ACTIVE
                    } else {
                        NavigationState.PAUSED
                    }
                }
                else -> { /* Keep current state */ }
            }

            Log.d(TAG, "Cancelled ongoing operations, current state: $currentState")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling ongoing operations", e)
        }
    }

    /**
     * Handle navigation start failure with cleanup
     */
    private fun handleNavigationStartFailure(
        error: Exception,
        onFailure: (Exception) -> Unit,
        onLoadingStateChanged: ((Boolean) -> Unit)?
    ) {
        // Cancel timeout
        startNavigationTimeoutHandler?.let { mainHandler.removeCallbacks(it) }

        // Reset state
        currentState = NavigationState.ERROR
        isStartingNavigation.set(false)
        onLoadingStateChanged?.invoke(false)

        // Ensure navigation is marked as stopped
        try {
            setNavigationMode(false)
        } catch (setModeError: Exception) {
            Log.e(TAG, "Failed to set navigation mode to false during failure cleanup", setModeError)
        }

        currentState = NavigationState.IDLE
        onFailure(error)
    }

    /**
     * Handle navigation stop failure with cleanup
     */
    private fun handleNavigationStopFailure(
        error: Exception,
        onFailure: ((Exception) -> Unit)?,
        onLoadingStateChanged: ((Boolean) -> Unit)?
    ) {
        // Cancel timeout
        stopNavigationTimeoutHandler?.let { mainHandler.removeCallbacks(it) }

        // Ensure navigation is marked as stopped even on error
        try {
            setNavigationMode(false)
        } catch (setModeError: Exception) {
            Log.e(TAG, "Failed to set navigation mode to false during stop failure", setModeError)
        }

        // Reset state
        currentState = NavigationState.IDLE
        isStoppingNavigation.set(false)
        onLoadingStateChanged?.invoke(false)

        onFailure?.invoke(error)
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
     * Clean up resources
     */
    fun cleanup() {
        cancelOngoingOperations()
    }

    /**
     * Enhanced data class for navigation status
     */
    data class NavigationStatus(
        val isActive: Boolean,
        val destination: LatLng?,
        val durationMs: Long,
        val geofenceRadius: Float,
        val hasGeofence: Boolean,
        val isGeofenceActive: Boolean,
        val navigationState: NavigationState,
        val isOperationInProgress: Boolean
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

        fun getStateDescription(): String {
            return when (navigationState) {
                NavigationState.IDLE -> "Ready"
                NavigationState.STARTING -> "Starting navigation..."
                NavigationState.ACTIVE -> "Navigation active"
                NavigationState.STOPPING -> "Stopping navigation..."
                NavigationState.PAUSED -> "Navigation paused"
                NavigationState.RESUMING -> "Resuming navigation..."
                NavigationState.ERROR -> "Error occurred"
            }
        }

        override fun toString(): String {
            return "NavigationStatus(active=$isActive, destination=$destination, " +
                    "duration=${getDurationString()}, radius=${geofenceRadius}m, " +
                    "hasGeofence=$hasGeofence, geofenceActive=$isGeofenceActive, " +
                    "state=$navigationState, operationInProgress=$isOperationInProgress)"
        }
    }
}