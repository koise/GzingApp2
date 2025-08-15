package com.example.gzingapp.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import com.example.gzingapp.R
import com.example.gzingapp.ui.dashboard.DashboardActivity
import com.example.gzingapp.services.LocationHelper
import com.google.android.gms.location.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

class BackgroundLocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var isServiceRunning = false
    private var lastGeofenceCheckTime = 0L
    private var lastLocationUpdateTime = 0L
    private var lastRouteUpdateTime = 0L
    private val periodicLogHandler = android.os.Handler(Looper.getMainLooper())
    private var periodicLogRunnable: Runnable? = null
    private var lastEmittedLocation: Location? = null
    private var locationUpdateCount = 0
    
    // Location helper for reverse geocoding
    private lateinit var locationHelper: LocationHelper

    companion object {
        private const val TAG = "BackgroundLocationService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "gzing_background_location"
        private const val CHANNEL_NAME = "Gzing Background Location"
        private const val CHANNEL_DESCRIPTION = "Shows when Gzing app is running in background"

        // More reliable location settings for consistent tracking
        private const val LOCATION_UPDATE_INTERVAL = 5000L // 5 seconds (increased for reliability)
        private const val FASTEST_LOCATION_UPDATE_INTERVAL = 2000L // 2 seconds minimum
        private const val GEOFENCE_CHECK_INTERVAL = 3000L // 3 seconds for geofence checks
        private const val MIN_DISTANCE_METERS = 2f // Accept 2m movements (increased for reliability)

        // Service actions
        const val ACTION_START = "START_BACKGROUND_LOCATION"
        const val ACTION_STOP = "STOP_BACKGROUND_LOCATION"

        // Shared preferences keys
        private const val PREFS_NAME = "background_location_prefs"
        private const val KEY_SERVICE_RUNNING = "service_running"
        private const val KEY_LAST_LOCATION_LAT = "last_location_lat"
        private const val KEY_LAST_LOCATION_LNG = "last_location_lng"
        private const val KEY_LAST_LOCATION_TIME = "last_location_time"
        private const val KEY_LAST_LOCATION_ACCURACY = "last_location_accuracy"

        /**
         * SAFE method to start background location service
         * Handles Android restrictions on background service starts
         */
        fun startService(context: Context) {
            try {
                val intent = Intent(context, BackgroundLocationService::class.java).apply {
                    action = ACTION_START
                }

                // Check if we can start foreground service safely
                if (canStartForegroundService(context)) {
                    Log.d(TAG, "✅ Safe to start foreground service")

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                } else {
                    Log.w(TAG, "⚠️ Cannot start foreground service - app not in allowed state")
                    Log.w(TAG, "🔄 Will attempt to start when app becomes foreground")

                    // Store intention to start service when possible
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("pending_service_start", true).apply()
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error starting background location service", e)

                // Store intention to start service when possible
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putBoolean("pending_service_start", true).apply()
            }
        }

        /**
         * Check if we can safely start a foreground service
         */
        private fun canStartForegroundService(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ - Check if app is in foreground or has special permissions
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val appProcesses = activityManager.runningAppProcesses

                appProcesses?.any { processInfo ->
                    processInfo.processName == context.packageName &&
                            processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                } ?: false
            } else {
                // Pre-Android 12 - More lenient
                true
            }
        }

        /**
         * Try to start pending service if app is now in foreground
         * Call this from your main activity's onResume()
         */
        fun startPendingServiceIfNeeded(context: Context) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val hasPendingStart = prefs.getBoolean("pending_service_start", false)

                if (hasPendingStart && canStartForegroundService(context)) {
                    Log.d(TAG, "🚀 Starting pending background location service")
                    prefs.edit().remove("pending_service_start").apply()
                    startService(context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting pending service", e)
            }
        }

        /**
         * Stop background location service
         */
        fun stopService(context: Context) {
            val intent = Intent(context, BackgroundLocationService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        /**
         * Check if service is running
         */
        fun isServiceRunning(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_SERVICE_RUNNING, false)
        }

        /**
         * Get last known location
         */
        fun getLastLocation(context: Context): Location? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lat = prefs.getFloat(KEY_LAST_LOCATION_LAT, 0f)
            val lng = prefs.getFloat(KEY_LAST_LOCATION_LNG, 0f)
            val time = prefs.getLong(KEY_LAST_LOCATION_TIME, 0L)
            val accuracy = prefs.getFloat(KEY_LAST_LOCATION_ACCURACY, 0f)

            return if (lat != 0f && lng != 0f) {
                Location("BackgroundLocationService").apply {
                    latitude = lat.toDouble()
                    longitude = lng.toDouble()
                    this.time = time
                    this.accuracy = accuracy
                }
            } else {
                null
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BackgroundLocationService created")

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationHelper = LocationHelper(this)
        createLocationCallback()
        createNotificationChannel()
        checkLocationServices()
    }

    /**
     * Check if location services are enabled and provide detailed status
     */
    private fun checkLocationServices() {
        try {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            val isPassiveEnabled = locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)

            Log.d(TAG, "📍 Location Services Status:")
            Log.d(TAG, "   GPS Provider: ${if (isGpsEnabled) "✅ Enabled" else "❌ Disabled"}")
            Log.d(TAG, "   Network Provider: ${if (isNetworkEnabled) "✅ Enabled" else "❌ Disabled"}")
            Log.d(TAG, "   Passive Provider: ${if (isPassiveEnabled) "✅ Enabled" else "❌ Disabled"}")

            // Check for mock location settings
            checkMockLocationSettings()
            checkDeviceLocationMode()

            if (!isGpsEnabled && !isNetworkEnabled) {
                Log.w(TAG, "⚠️ No location providers are enabled!")
                Log.w(TAG, "💡 User needs to enable location services in device settings")
                
                // Try to get last known location as fallback
                tryGetLastKnownLocation()
            } else {
                Log.d(TAG, "✅ Location services are available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking location services", e)
        }
    }

    /**
     * Check if mock location is enabled (developer settings)
     */
    private fun checkMockLocationSettings() {
        try {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            
            // Check if this app is in mock location mode (if available)
            try {
                val mockLocationEnabled = locationManager.javaClass.getMethod("isMockLocationEnabled").invoke(locationManager) as? Boolean
                if (mockLocationEnabled == true) {
                    Log.w(TAG, "⚠️ MOCK LOCATION MODE ENABLED for this app!")
                    Log.w(TAG, "💡 This will prevent real GPS data from being received")
                } else {
                    Log.d(TAG, "✅ Mock location mode is disabled")
                }
            } catch (e: Exception) {
                Log.d(TAG, "ℹ️ Could not check mock location mode: ${e.message}")
            }
            
        } catch (e: Exception) {
            Log.d(TAG, "ℹ️ Could not check mock location settings: ${e.message}")
        }
    }

    /**
     * Check device location mode and provide comprehensive diagnostics
     */
    private fun checkDeviceLocationMode() {
        try {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            
            // Check if we have any recent location data from any provider
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            
            for (provider in providers) {
                try {
                    val lastKnownLocation = locationManager.getLastKnownLocation(provider)
                    if (lastKnownLocation != null) {
                        val age = System.currentTimeMillis() - lastKnownLocation.time
                        val ageSeconds = age / 1000
                        Log.d(TAG, "📍 $provider last known: age=${ageSeconds}s, accuracy=${String.format("%.1f", lastKnownLocation.accuracy)}m")
                    } else {
                        Log.d(TAG, "📍 $provider: no last known location")
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "📍 $provider: error checking last known location")
                }
            }
            
        } catch (e: Exception) {
            Log.d(TAG, "ℹ️ Could not check device location mode: ${e.message}")
        }
    }

    /**
     * Try to get last known location as fallback when services are disabled
     */
    private fun tryGetLastKnownLocation() {
        try {
            Log.d(TAG, "🔄 Attempting to get last known location...")
            
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        Log.d(TAG, "✅ Last known location obtained: ${location.latitude}, ${location.longitude}")
                        handleLocationUpdate(location)
                    } else {
                        Log.w(TAG, "⚠️ No last known location available")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Failed to get last known location", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting last known location", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "🚀 BackgroundLocationService onStartCommand: ${intent?.action}, startId: $startId, flags: $flags")
        Log.d(TAG, "📊 Service status - isServiceRunning: $isServiceRunning, updateCount: $locationUpdateCount")

        // CRITICAL: Start as foreground service IMMEDIATELY to avoid timeout exception
        try {
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "🔔 Started as foreground service immediately in onStartCommand")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start foreground service immediately", e)
            // If we can't start as foreground, stop the service
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_START -> {
                Log.d(TAG, "🟢 Starting background location service")
                startLocationUpdates()
                return START_STICKY
            }
            ACTION_STOP -> {
                Log.d(TAG, "🔴 Stopping background location service")
                stopLocationUpdates()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                Log.d(TAG, "🔄 Restarting background location service (system restart)")
                startLocationUpdates()
                return START_STICKY
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "BackgroundLocationService destroyed")
        stopLocationUpdates()
        isServiceRunning = false
        saveServiceState(false)
    }

    /**
     * Create location callback for real-time updates
     */
    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val currentTime = System.currentTimeMillis()
                val timeSinceLastUpdate = currentTime - lastLocationUpdateTime
                val timeString = android.text.format.DateFormat.format("HH:mm:ss", currentTime)

                locationResult.lastLocation?.let { location ->
                    locationUpdateCount++
                    Log.d(TAG, "🌟 GPS LOCATION RECEIVED #$locationUpdateCount at $timeString: lat=${String.format("%.6f", location.latitude)}, lng=${String.format("%.6f", location.longitude)}, accuracy=${String.format("%.1f", location.accuracy)}m, provider=${location.provider}, timeSinceLastUpdate=${timeSinceLastUpdate}ms")

                    // Enhanced jitter filtering with detailed logging
                    val shouldProcess = shouldProcessLocationUpdate(location)

                    if (shouldProcess) {
                        Log.d(TAG, "✅ PROCESSING GPS location update #$locationUpdateCount")
                        lastEmittedLocation = location
                        handleLocationUpdate(location)
                    } else {
                        Log.d(TAG, "⏭️ SKIPPING GPS location update #$locationUpdateCount (filtered by jitter filter)")
                    }
                } ?: run {
                    Log.w(TAG, "⚠️ LocationResult received but lastLocation is NULL at $timeString")
                }
            }

            override fun onLocationAvailability(locationAvailability: LocationAvailability) {
                val currentTime = android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis())
                Log.d(TAG, "📶 Location availability changed at $currentTime: available=${locationAvailability.isLocationAvailable}")

                if (!locationAvailability.isLocationAvailable) {
                    Log.e(TAG, "❌ GPS LOCATION SERVICES UNAVAILABLE! This will stop all location updates.")

                    // Try to restart location updates after a delay
                    android.os.Handler(Looper.getMainLooper()).postDelayed({
                        Log.d(TAG, "🔄 Attempting to restart location updates after GPS unavailability")
                        if (!isServiceRunning) {
                            startLocationUpdates()
                        }
                    }, 5000) // Wait 5 seconds
                } else {
                    Log.d(TAG, "✅ GPS LOCATION SERVICES ARE AVAILABLE")
                }
            }
        }
    }

    /**
     * Enhanced location filtering for real-time responsiveness
     */
    private fun shouldProcessLocationUpdate(newLocation: Location): Boolean {
        val prev = lastEmittedLocation

        // Always process first location
        if (prev == null) {
            Log.d(TAG, "🆕 First location - processing")
            return true
        }

        val movedMeters = prev.distanceTo(newLocation)
        val timeDiff = newLocation.time - prev.time
        val accuracyImproved = newLocation.accuracy > 0 && newLocation.accuracy < prev.accuracy - 2f

        // Simplified and more permissive filtering logic
        return when {
            // Always process if accuracy significantly improved
            accuracyImproved -> {
                Log.d(TAG, "✨ Accuracy improved from ${String.format("%.1f", prev.accuracy)}m to ${String.format("%.1f", newLocation.accuracy)}m")
                true
            }

            // Always process if significant time has passed (force updates)
            timeDiff > 10000 -> { // 10 seconds instead of 15
                Log.d(TAG, "⏰ Force update due to time: ${timeDiff}ms since last update")
                true
            }

            // Always process if significant movement detected
            movedMeters >= 3f -> { // Reduced threshold from complex logic
                Log.d(TAG, "🚶 Significant movement detected: ${String.format("%.1f", movedMeters)}m")
                true
            }

            // Process if accuracy is good and some movement detected
            newLocation.accuracy <= 25f && movedMeters >= 1f -> {
                Log.d(TAG, "📍 Good accuracy with movement: acc=${String.format("%.1f", newLocation.accuracy)}m, moved=${String.format("%.1f", movedMeters)}m")
                true
            }

            // Process if accuracy is excellent regardless of movement
            newLocation.accuracy <= 10f -> {
                Log.d(TAG, "🎯 Excellent accuracy: ${String.format("%.1f", newLocation.accuracy)}m")
                true
            }

            // Default: process if some movement detected
            else -> {
                val shouldProcess = movedMeters >= 0.5f // Very low threshold
                Log.d(TAG, "📌 Default case: moved ${String.format("%.1f", movedMeters)}m, threshold 0.5m -> $shouldProcess")
                shouldProcess
            }
        }
    }

    /**
     * Handle location updates and check geofence status
     */
    private fun handleLocationUpdate(location: Location) {
        val currentTime = System.currentTimeMillis()
        val age = currentTime - location.time

        Log.d(TAG, "🌟 LOCATION UPDATE RECEIVED: Lat=${String.format("%.6f", location.latitude)}, Lng=${String.format("%.6f", location.longitude)}, Acc=${String.format("%.1f", location.accuracy)}m, Speed=${if (location.hasSpeed()) String.format("%.1f", location.speed) else "N/A"}m/s, Age=${age}ms")

        // Save last location with accuracy
        Log.d(TAG, "💾 Saving location to SharedPreferences...")
        saveLastLocation(location)

        // Update notification with current location info
        Log.d(TAG, "🔔 Updating notification with location info...")
        updateNotification(location)

        // Check geofence status with real-time location (throttled for performance)
        if (currentTime - lastGeofenceCheckTime >= GEOFENCE_CHECK_INTERVAL) {
            Log.d(TAG, "📍 Checking geofence status (throttled)...")
            lastGeofenceCheckTime = currentTime
            checkGeofenceStatus(location)
        } else {
            Log.d(TAG, "⏳ Skipping geofence check (throttled) - last check was ${currentTime - lastGeofenceCheckTime}ms ago")
        }

        // Broadcast location update for real-time UI updates
        Log.d(TAG, "📡 Broadcasting location update to UI...")
        broadcastLocationUpdate(location)

        // Store location for faster access by UI components
        lastEmittedLocation = location
        Log.d(TAG, "💾 Stored location in lastEmittedLocation variable")

        lastLocationUpdateTime = currentTime
        Log.d(TAG, "✅ Location update processed successfully - total processing time: ${System.currentTimeMillis() - currentTime}ms")
    }

    /**
     * Check if user is inside geofence using real-time location
     */
    private fun checkGeofenceStatus(location: Location) {
        try {
            Log.d(TAG, "🎯 Checking geofence status with real-time location...")
            
            // Get current geofence location from GeofenceHelper
            val geofenceHelper = GeofenceHelper(this)
            val geofenceLocation = geofenceHelper.getCurrentGeofenceLocation()
            
            if (geofenceLocation == null) {
                Log.d(TAG, "📍 No active geofence to check")
                return
            }
            
            // Calculate distance to geofence center
            val currentLatLng = com.google.android.gms.maps.model.LatLng(location.latitude, location.longitude)
            val distance = locationHelper.calculateDistance(currentLatLng, geofenceLocation) * 1000 // Convert to meters
            val radius = GeofenceHelper.getGeofenceRadius()
            
            // Enhanced accuracy-based threshold for more reliable geofence detection
            val accuracyBuffer = when {
                location.accuracy <= 5f -> location.accuracy * 0.5f     // Excellent accuracy - minimal buffer
                location.accuracy <= 15f -> location.accuracy * 0.8f    // Good accuracy - moderate buffer
                location.accuracy <= 30f -> location.accuracy * 1.0f    // Medium accuracy - full buffer
                location.accuracy <= 50f -> location.accuracy * 0.8f    // Lower accuracy - reduced buffer
                else -> 10f                                              // Poor accuracy - fixed small buffer
            }
            
            val wasInside = geofenceHelper.isUserInsideGeofence()
            val effectiveRadius = radius + accuracyBuffer
            val isCurrentlyInside = distance <= effectiveRadius
            
            Log.d(TAG, "🎯 Geofence status: distance=${String.format("%.1f", distance)}m, radius=${radius}m, effective=${String.format("%.1f", effectiveRadius)}m, inside=$isCurrentlyInside, wasInside=$wasInside")
            
            // Update geofence status
            geofenceHelper.setUserInsideGeofence(isCurrentlyInside)
            
            // Check for geofence transitions
            if (isCurrentlyInside && !wasInside) {
                Log.i(TAG, "🎯 GEOFENCE ENTER DETECTED! Distance: ${String.format("%.1f", distance)}m")
                
                // Send real-time geofence check broadcast for immediate UI updates
                val geofenceIntent = Intent("com.example.gzingapp.REALTIME_GEOFENCE_CHECK")
                geofenceIntent.putExtra("latitude", location.latitude)
                geofenceIntent.putExtra("longitude", location.longitude)
                geofenceIntent.putExtra("accuracy", location.accuracy)
                geofenceIntent.putExtra("timestamp", location.time)
                sendBroadcast(geofenceIntent)
                
                // Also send distance update broadcast for UI
                val distanceIntent = Intent("com.example.gzingapp.DISTANCE_UPDATE")
                distanceIntent.putExtra("distance", distance)
                distanceIntent.putExtra("radius", radius)
                distanceIntent.putExtra("isInside", true)
                sendBroadcast(distanceIntent)
                
            } else if (!isCurrentlyInside && wasInside) {
                Log.i(TAG, "🚶 GEOFENCE EXIT DETECTED! Distance: ${String.format("%.1f", distance)}m")
                
                // Send distance update broadcast for UI
                val distanceIntent = Intent("com.example.gzingapp.DISTANCE_UPDATE")
                distanceIntent.putExtra("distance", distance)
                distanceIntent.putExtra("radius", radius)
                distanceIntent.putExtra("isInside", false)
                sendBroadcast(distanceIntent)
            }
            
                    // Always send distance update for continuous UI updates
        if (isCurrentlyInside != wasInside || System.currentTimeMillis() % 5000 < 1000) { // Update every 5 seconds or on state change
            val distanceIntent = Intent("com.example.gzingapp.DISTANCE_UPDATE")
            distanceIntent.putExtra("distance", distance)
            distanceIntent.putExtra("radius", radius)
            distanceIntent.putExtra("isInside", isCurrentlyInside)
            sendBroadcast(distanceIntent)
        }
        
        // Check if route should be updated
        checkRouteUpdate(location)
        
    } catch (e: Exception) {
        Log.e(TAG, "❌ Error checking geofence status", e)
    }
}

/**
 * Check if route should be updated based on location change
 */
private fun checkRouteUpdate(location: Location) {
    try {
        val currentTime = System.currentTimeMillis()
        
        // Update route every 10 seconds or on significant movement
        if (currentTime - lastRouteUpdateTime >= 10000L) { // 10 seconds
            Log.d(TAG, "🛣️ Checking route update...")
            lastRouteUpdateTime = currentTime
            
            // Send route update broadcast for UI
            val routeIntent = Intent("com.example.gzingapp.ROUTE_UPDATE")
            routeIntent.putExtra("latitude", location.latitude)
            routeIntent.putExtra("longitude", location.longitude)
            routeIntent.putExtra("accuracy", location.accuracy)
            routeIntent.putExtra("timestamp", location.time)
            sendBroadcast(routeIntent)
            
            Log.d(TAG, "✅ Route update broadcast sent")
        }
        
    } catch (e: Exception) {
        Log.e(TAG, "❌ Error checking route update", e)
    }
}

    /**
     * Start real-time location updates with enhanced settings
     */
    private fun startLocationUpdates() {
        val currentTime = android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis())

        if (isServiceRunning) {
            Log.d(TAG, "⚠️ Location updates already running at $currentTime - skipping restart")
            return
        }

        try {
            Log.d(TAG, "🚀 STARTING BACKGROUND LOCATION SERVICE at $currentTime")
            Log.d(TAG, "📊 Current state - updateCount: $locationUpdateCount, lastUpdate: ${lastLocationUpdateTime}")

            // Check location services first
            checkLocationServices()

            // Try multiple location request strategies
            tryHighAccuracyLocationRequest()
            
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ SECURITY EXCEPTION - Location permission likely revoked at $currentTime", e)
            Log.e(TAG, "💡 User needs to grant location permissions in app settings")
            isServiceRunning = false
            saveServiceState(false)
            
            // Try to handle permission issues gracefully
            handleLocationPermissionIssue()
        } catch (e: Exception) {
            Log.e(TAG, "❌ UNEXPECTED ERROR starting location updates at $currentTime", e)
            isServiceRunning = false
            saveServiceState(false)
        }
    }

    /**
     * Try high accuracy location request first
     */
    private fun tryHighAccuracyLocationRequest() {
        try {
            Log.d(TAG, "🎯 Attempting HIGH ACCURACY location request...")
            
            val highAccuracyRequest = LocationRequest.Builder(3000L) // 3 seconds for high accuracy
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMinUpdateIntervalMillis(1000L)
                .setMinUpdateDistanceMeters(1f)
                .setWaitForAccurateLocation(false)
                .setMaxUpdates(Int.MAX_VALUE)
                .build()

            fusedLocationClient.requestLocationUpdates(
                highAccuracyRequest,
                locationCallback,
                Looper.getMainLooper()
            ).addOnSuccessListener {
                Log.d(TAG, "✅ HIGH ACCURACY location updates started successfully")
                isServiceRunning = true
                saveServiceState(true)
                startPeriodicLogging()
                
                // Also request immediate location
                requestImmediateLocation()
                
            }.addOnFailureListener { e ->
                Log.w(TAG, "⚠️ High accuracy location request failed, trying balanced...", e)
                tryBalancedLocationRequest()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in high accuracy location request", e)
            tryBalancedLocationRequest()
        }
    }

    /**
     * Try balanced power accuracy location request
     */
    private fun tryBalancedLocationRequest() {
        try {
            Log.d(TAG, "🎯 Attempting BALANCED POWER location request...")
            
            val balancedRequest = LocationRequest.Builder(LOCATION_UPDATE_INTERVAL)
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setMinUpdateIntervalMillis(FASTEST_LOCATION_UPDATE_INTERVAL)
                .setMinUpdateDistanceMeters(MIN_DISTANCE_METERS)
                .setWaitForAccurateLocation(false)
                .setMaxUpdates(Int.MAX_VALUE)
                .build()

            fusedLocationClient.requestLocationUpdates(
                balancedRequest,
                locationCallback,
                Looper.getMainLooper()
            ).addOnSuccessListener {
                Log.d(TAG, "✅ BALANCED POWER location updates started successfully")
                isServiceRunning = true
                saveServiceState(true)
                startPeriodicLogging()
                
                // Also request immediate location
                requestImmediateLocation()
                
            }.addOnFailureListener { e ->
                Log.w(TAG, "⚠️ Balanced power location request failed, trying low power...", e)
                tryLowPowerLocationRequest()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in balanced power location request", e)
            tryLowPowerLocationRequest()
        }
    }

    /**
     * Try low power location request as final fallback
     */
    private fun tryLowPowerLocationRequest() {
        try {
            Log.d(TAG, "🎯 Attempting LOW POWER location request (final fallback)...")
            
            val lowPowerRequest = LocationRequest.Builder(10000L) // 10 seconds
                .setPriority(Priority.PRIORITY_LOW_POWER)
                .setMinUpdateIntervalMillis(5000L)
                .setMinUpdateDistanceMeters(5f)
                .setWaitForAccurateLocation(false)
                .setMaxUpdates(Int.MAX_VALUE)
                .build()

            fusedLocationClient.requestLocationUpdates(
                lowPowerRequest,
                locationCallback,
                Looper.getMainLooper()
            ).addOnSuccessListener {
                Log.d(TAG, "✅ LOW POWER location updates started successfully")
                isServiceRunning = true
                saveServiceState(true)
                startPeriodicLogging()
                
                // Also request immediate location
                requestImmediateLocation()
                
            }.addOnFailureListener { e ->
                Log.e(TAG, "❌ ALL location request strategies failed!", e)
                isServiceRunning = false
                saveServiceState(false)
                
                // Try to restart after a delay with a different approach
                android.os.Handler(Looper.getMainLooper()).postDelayed({
                    Log.d(TAG, "🔄 Retrying location updates with different approach...")
                    tryAlternativeLocationStrategy()
                }, 10000) // Wait 10 seconds
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in low power location request", e)
            isServiceRunning = false
            saveServiceState(false)
        }
    }

    /**
     * Try alternative location strategy when all standard methods fail
     */
    private fun tryAlternativeLocationStrategy() {
        try {
            Log.d(TAG, "🔄 Trying alternative location strategy...")
            
            // Try to get last known location first
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        Log.d(TAG, "✅ Alternative strategy: Got last known location")
                        handleLocationUpdate(location)
                        
                        // Try to start updates again with a simpler approach
                        trySimpleLocationRequest()
                    } else {
                        Log.w(TAG, "⚠️ Alternative strategy: No last known location")
                        trySimpleLocationRequest()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Alternative strategy: Failed to get last known location", e)
                    trySimpleLocationRequest()
                }
                
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in alternative location strategy", e)
            trySimpleLocationRequest()
        }
    }

    /**
     * Try a very simple location request with minimal settings
     */
    private fun trySimpleLocationRequest() {
        try {
            Log.d(TAG, "🎯 Trying simple location request with minimal settings...")
            
            val simpleRequest = LocationRequest.Builder(15000L) // 15 seconds
                .setPriority(Priority.PRIORITY_LOW_POWER)
                .setMinUpdateIntervalMillis(10000L)
                .setMinUpdateDistanceMeters(10f)
                .setWaitForAccurateLocation(false)
                .setMaxUpdates(Int.MAX_VALUE)
                .build()

            fusedLocationClient.requestLocationUpdates(
                simpleRequest,
                locationCallback,
                Looper.getMainLooper()
            ).addOnSuccessListener {
                Log.d(TAG, "✅ Simple location request started successfully")
                isServiceRunning = true
                saveServiceState(true)
                startPeriodicLogging()
                
            }.addOnFailureListener { e ->
                Log.e(TAG, "❌ Even simple location request failed - this suggests a deeper issue", e)
                isServiceRunning = false
                saveServiceState(false)
                
                // Final attempt: try to restart the entire service
                android.os.Handler(Looper.getMainLooper()).postDelayed({
                    Log.d(TAG, "🔄 Final attempt: restarting entire service...")
                    stopSelf()
                    startLocationUpdates()
                }, 30000) // Wait 30 seconds
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in simple location request", e)
            isServiceRunning = false
            saveServiceState(false)
        }
    }

    /**
     * Handle location permission issues gracefully
     */
    private fun handleLocationPermissionIssue() {
        try {
            Log.d(TAG, "🔄 Attempting to handle location permission issue...")
            
            // Check if we have any location permissions at all
            val hasLocationPermission = checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            
            if (!hasLocationPermission) {
                Log.e(TAG, "❌ No location permissions granted - cannot continue")
                // Stop the service since we can't get location
                stopSelf()
            } else {
                Log.w(TAG, "⚠️ Location permissions exist but service failed - will retry later")
                // Try to restart after a longer delay
                android.os.Handler(Looper.getMainLooper()).postDelayed({
                    Log.d(TAG, "🔄 Retrying location updates after permission issue")
                    startLocationUpdates()
                }, 30000) // Wait 30 seconds
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling location permission issue", e)
            stopSelf()
        }
    }

    /**
     * Request immediate location reading with multiple priority strategies
     */
    private fun requestImmediateLocation() {
        try {
            Log.d(TAG, "🎯 Requesting immediate location with HIGH ACCURACY priority...")
            
            val tokenSource = com.google.android.gms.tasks.CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        Log.d(TAG, "✅ Immediate HIGH ACCURACY location obtained: ${loc.latitude}, ${loc.longitude}, accuracy: ${loc.accuracy}m")
                        handleLocationUpdate(loc)
                    } else {
                        Log.w(TAG, "⚠️ Immediate HIGH ACCURACY location is null, trying BALANCED...")
                        tryImmediateLocationBalanced()
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "⚠️ Failed to get immediate HIGH ACCURACY location, trying BALANCED...", e)
                    tryImmediateLocationBalanced()
                }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error requesting immediate HIGH ACCURACY location", e)
            tryImmediateLocationBalanced()
        }
    }

    /**
     * Try immediate location with balanced priority
     */
    private fun tryImmediateLocationBalanced() {
        try {
            Log.d(TAG, "🎯 Requesting immediate location with BALANCED priority...")
            
            val tokenSource = com.google.android.gms.tasks.CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, tokenSource.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        Log.d(TAG, "✅ Immediate BALANCED location obtained: ${loc.latitude}, ${loc.longitude}, accuracy: ${loc.accuracy}m")
                        handleLocationUpdate(loc)
                    } else {
                        Log.w(TAG, "⚠️ Immediate BALANCED location is null, trying LOW POWER...")
                        tryImmediateLocationLowPower()
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "⚠️ Failed to get immediate BALANCED location, trying LOW POWER...", e)
                    tryImmediateLocationLowPower()
                }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error requesting immediate BALANCED location", e)
            tryImmediateLocationLowPower()
        }
    }

    /**
     * Try immediate location with low power priority
     */
    private fun tryImmediateLocationLowPower() {
        try {
            Log.d(TAG, "🎯 Requesting immediate location with LOW POWER priority...")
            
            val tokenSource = com.google.android.gms.tasks.CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_LOW_POWER, tokenSource.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        Log.d(TAG, "✅ Immediate LOW POWER location obtained: ${loc.latitude}, ${loc.longitude}, accuracy: ${loc.accuracy}m")
                        handleLocationUpdate(loc)
                    } else {
                        Log.w(TAG, "⚠️ All immediate location requests returned null")
                        Log.w(TAG, "💡 This suggests the device may not have a recent GPS fix")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ All immediate location requests failed", e)
                    Log.w(TAG, "💡 This suggests a deeper issue with location services")
                }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error requesting immediate LOW POWER location", e)
        }
    }



    /**
     * Stop location updates
     */
    private fun stopLocationUpdates() {
        if (!isServiceRunning) {
            Log.d(TAG, "Location updates not running")
            return
        }

        try {
            Log.d(TAG, "🛑 Stopping location updates...")

            fusedLocationClient.removeLocationUpdates(locationCallback)
                .addOnSuccessListener {
                    Log.d(TAG, "✅ Location updates stopped successfully")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Failed to stop location updates", e)
                }

            isServiceRunning = false
            saveServiceState(false)
            stopPeriodicLogging()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping location updates", e)
            isServiceRunning = false
            saveServiceState(false)
        }
    }

    /**
     * Enhanced periodic logging with better status monitoring and less aggressive restarts
     */
    private fun startPeriodicLogging() {
        if (periodicLogRunnable != null) return
        periodicLogRunnable = Runnable {
            try {
                if (!isServiceRunning) return@Runnable

                val last = getLastLocation(this)
                val currentTime = System.currentTimeMillis()

                if (last != null) {
                    val ageMs = currentTime - last.time
                    val ageSeconds = ageMs / 1000
                    
                    Log.d(
                        TAG,
                        "📊 HEARTBEAT #$locationUpdateCount: lat=${last.latitude.format(6)} lng=${last.longitude.format(6)} acc=${"%.1f".format(last.accuracy)}m age=${ageSeconds}s"
                    )

                    // More intelligent stale location detection with better diagnostics
                    when {
                        ageMs > 120000L -> { // 2 minutes - very stale
                            Log.e(TAG, "🚨 LOCATION VERY STALE! Age: ${ageSeconds}s - investigating issue...")
                            investigateLocationIssue()
                        }
                        ageMs > 60000L -> { // 1 minute - stale
                            Log.w(TAG, "⚠️ Location is stale: age=${ageSeconds}s - monitoring...")
                            // Don't force restart immediately, just monitor
                        }
                        ageMs > 30000L -> { // 30 seconds - getting stale
                            Log.i(TAG, "ℹ️ Location getting stale: age=${ageSeconds}s")
                        }
                        else -> {
                            Log.d(TAG, "✅ Location is fresh: age=${ageSeconds}s")
                        }
                    }
                } else {
                    Log.d(TAG, "📊 HEARTBEAT: No location data available")
                    
                    // Only force restart if we've been running for a while without data
                    if (isServiceRunning && locationUpdateCount == 0) {
                        val serviceStartTime = currentTime - lastLocationUpdateTime
                        if (serviceStartTime > 60000L) { // 1 minute instead of 30 seconds
                            Log.e(TAG, "🚨 NO LOCATION DATA AVAILABLE after ${serviceStartTime/1000}s - investigating...")
                            investigateLocationIssue()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Periodic log error", e)
            } finally {
                periodicLogHandler.postDelayed(periodicLogRunnable!!, 10000L) // Every 10 seconds
            }
        }
        periodicLogHandler.postDelayed(periodicLogRunnable!!, 10000L)
    }

    /**
     * Investigate location issues instead of immediately forcing restarts
     */
    private fun investigateLocationIssue() {
        try {
            Log.d(TAG, "🔍 Investigating location issue...")
            
            // Check location services status
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            
            Log.d(TAG, "📍 Current location services status:")
            Log.d(TAG, "   GPS Provider: ${if (isGpsEnabled) "✅ Enabled" else "❌ Disabled"}")
            Log.d(TAG, "   Network Provider: ${if (isNetworkEnabled) "✅ Enabled" else "❌ Disabled"}")
            
            // Check for mock location and device settings
            checkMockLocationSettings()
            checkDeviceLocationMode()
            
            // Check if we have any recent location data
            val lastKnownLocation = fusedLocationClient.lastLocation
            lastKnownLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val age = System.currentTimeMillis() - location.time
                    Log.d(TAG, "📍 Last known location age: ${age/1000}s")
                    
                    if (age < 300000L) { // 5 minutes
                        Log.d(TAG, "✅ Last known location is recent, attempting to restart updates...")
                        forceRestartLocationUpdates("Recent last known location available")
                    } else {
                        Log.w(TAG, "⚠️ Last known location is also stale (${age/1000}s)")
                        Log.w(TAG, "💡 This suggests a deeper issue with location services")
                        
                        // Try to restart with a different strategy
                        android.os.Handler(Looper.getMainLooper()).postDelayed({
                            Log.d(TAG, "🔄 Attempting restart with different strategy...")
                            tryAlternativeLocationStrategy()
                        }, 15000) // Wait 15 seconds
                    }
                } else {
                    Log.w(TAG, "⚠️ No last known location available")
                    Log.w(TAG, "💡 This suggests location services may be completely unavailable")
                    
                    // Try to restart with a different strategy
                    android.os.Handler(Looper.getMainLooper()).postDelayed({
                        Log.d(TAG, "🔄 Attempting restart with different strategy...")
                        tryAlternativeLocationStrategy()
                    }, 15000) // Wait 15 seconds
                }
            }.addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to check last known location", e)
                
                // Try to restart with a different strategy
                android.os.Handler(Looper.getMainLooper()).postDelayed({
                    Log.d(TAG, "🔄 Attempting restart with different strategy...")
                    tryAlternativeLocationStrategy()
                }, 15000) // Wait 15 seconds
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error investigating location issue", e)
            
            // Fallback to force restart
            android.os.Handler(Looper.getMainLooper()).postDelayed({
                Log.d(TAG, "🔄 Fallback: forcing location restart...")
                forceRestartLocationUpdates("Investigation failed")
            }, 10000) // Wait 10 seconds
        }
    }

    private fun stopPeriodicLogging() {
        periodicLogRunnable?.let { periodicLogHandler.removeCallbacks(it) }
        periodicLogRunnable = null
    }

    /**
     * Force restart location updates when GPS becomes stale or unresponsive
     */
    private fun forceRestartLocationUpdates(reason: String) {
        Log.d(TAG, "🔄 FORCE RESTARTING location updates - Reason: $reason")

        try {
            // Stop current location updates
            if (isServiceRunning) {
                Log.d(TAG, "🛑 Stopping stale location updates...")
                fusedLocationClient.removeLocationUpdates(locationCallback)
                    .addOnCompleteListener {
                        Log.d(TAG, "✅ Stopped stale location updates, restarting...")

                        // Wait a moment then restart
                        android.os.Handler(Looper.getMainLooper()).postDelayed({
                            Log.d(TAG, "🚀 Restarting location updates after force stop...")
                            startLocationUpdates()
                        }, 2000) // Wait 2 seconds
                    }
            } else {
                // Service thinks it's not running but we need location updates
                Log.d(TAG, "🚀 Service not marked as running, starting fresh...")
                startLocationUpdates()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during force restart of location updates", e)

            // Fallback: try starting fresh after a delay
            android.os.Handler(Looper.getMainLooper()).postDelayed({
                Log.d(TAG, "🔄 Fallback restart attempt...")
                try {
                    startLocationUpdates()
                } catch (fallbackError: Exception) {
                    Log.e(TAG, "❌ Fallback restart also failed", fallbackError)
                }
            }, 5000) // Wait 5 seconds for fallback
        }
    }

    /**
     * Create notification channel for Android 8.0+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Create foreground notification with enhanced info
     */
    private fun createNotification(location: Location? = null): Notification {
        val intent = Intent(this, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val locationText = if (location != null) {
            getLocationText(location)
        } else {
            "📡 Getting location..."
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🛰️ Gzing Tracking Active")
            .setContentText(locationText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation) // Use system icon as fallback
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Create notification with custom address text
     */
    private fun createNotification(location: Location, address: String): Notification {
        val intent = Intent(this, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val accuracyText = if (location.accuracy > 0f) {
            " (±${location.accuracy.toInt()}m)"
        } else {
            " (accuracy unknown)"
        }

        val locationText = "📍 $address$accuracyText"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🛰️ Gzing Tracking Active")
            .setContentText(locationText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Update notification with current location
     */
    private fun updateNotification(location: Location) {
        try {
            val notification = createNotification(location)
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification", e)
        }
    }

    /**
     * Save service running state
     */
    private fun saveServiceState(isRunning: Boolean) {
        try {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_SERVICE_RUNNING, isRunning).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving service state", e)
        }
    }

    /**
     * Save last known location with accuracy
     */
    private fun saveLastLocation(location: Location) {
        try {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putFloat(KEY_LAST_LOCATION_LAT, location.latitude.toFloat())
                .putFloat(KEY_LAST_LOCATION_LNG, location.longitude.toFloat())
                .putLong(KEY_LAST_LOCATION_TIME, location.time)
                .putFloat(KEY_LAST_LOCATION_ACCURACY, location.accuracy)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving location", e)
        }
    }

    /**
     * Broadcast location update for real-time UI updates
     */
    private fun broadcastLocationUpdate(location: Location) {
        try {
            Log.d(TAG, "📡 Creating location broadcast intent...")
            val intent = Intent("com.example.gzingapp.LOCATION_UPDATE")
            intent.putExtra("latitude", location.latitude)
            intent.putExtra("longitude", location.longitude)
            intent.putExtra("accuracy", location.accuracy)
            intent.putExtra("timestamp", location.time)
            intent.putExtra("speed", location.speed)
            intent.putExtra("bearing", location.bearing)

            Log.d(TAG, "📤 Sending location broadcast: action=${intent.action}, lat=${String.format("%.6f", location.latitude)}, lng=${String.format("%.6f", location.longitude)}, accuracy=${String.format("%.1f", location.accuracy)}m")
            sendBroadcast(intent)
            Log.d(TAG, "✅ Location broadcast sent successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error broadcasting location update", e)
        }
    }

    /**
     * Get location text with actual place name instead of coordinates
     */
    private fun getLocationText(location: Location): String {
        return try {
            // Try to get the actual place name first
            val latLng = com.google.android.gms.maps.model.LatLng(location.latitude, location.longitude)
            
            // Use a coroutine to get the address asynchronously
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val address = locationHelper.getAddressFromLocation(latLng)
                    if (address.isNotEmpty()) {
                        // Update notification with actual place name
                        val notification = createNotification(location, address)
                        val notificationManager = getSystemService(NotificationManager::class.java)
                        notificationManager.notify(NOTIFICATION_ID, notification)
                        Log.d(TAG, "✅ Notification updated with place name: $address")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting address for notification", e)
                }
            }
            
            // Return coordinates initially, will be updated with place name
            "📍 ${location.latitude.format(4)}, ${location.longitude.format(4)} (±${location.accuracy.toInt()}m)"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location name", e)
            "📍 ${location.latitude.format(4)}, ${location.longitude.format(4)} (±${location.accuracy.toInt()}m)"
        }
    }

    /**
     * Extension function to format double to specified decimal places
     */
    private fun Double.format(digits: Int) = "%.${digits}f".format(this)
}