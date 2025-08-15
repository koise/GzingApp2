package com.example.gzingapp.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Looper
import android.util.Log
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import java.io.IOException
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LocationHelper(private val context: Context) {

    private var placesClient: PlacesClient? = null
    private val directionsService: DirectionsService = DirectionsService(context)
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private var continuousCallback: LocationCallback? = null
    private var isContinuousUpdating: Boolean = false

    init {
        // Initialize Places API - the key will be read from manifest
        if (!Places.isInitialized()) {
            try {
                // Get API key from manifest
                val appInfo = context.packageManager.getApplicationInfo(
                    context.packageName,
                    PackageManager.GET_META_DATA
                )
                val apiKey = appInfo.metaData?.getString("com.google.android.geo.API_KEY")

                if (apiKey != null && apiKey.isNotEmpty() && !apiKey.startsWith("\${")) {
                    Places.initialize(context, apiKey)
                    placesClient = Places.createClient(context)
                    Log.d(TAG, "Places API initialized successfully")
                } else {
                    Log.w(TAG, "Google Maps API key not found or is placeholder in manifest")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing Places API", e)
            }
        } else {
            placesClient = Places.createClient(context)
        }
    }

    companion object {
        private const val TAG = "LocationHelper"
        private const val EARTH_RADIUS_KM = 6371.0 // Earth's radius in kilometers

        // Traffic condition thresholds in minutes per kilometer
        private const val LIGHT_TRAFFIC_MIN = 0.8
        private const val LIGHT_TRAFFIC_MAX = 1.2
        private const val MODERATE_TRAFFIC_MIN = 1.2
        private const val MODERATE_TRAFFIC_MAX = 2.0
        private const val HEAVY_TRAFFIC_MIN = 2.0
        private const val HEAVY_TRAFFIC_MAX = 4.0
        
        // Real-time location update intervals
        private const val REALTIME_UPDATE_INTERVAL = 5000L // 5 seconds
        private const val FASTEST_REALTIME_UPDATE_INTERVAL = 2000L // 2 seconds
    }

    /**
     * Data class for route information
     */
    data class RouteResult(
        val polylinePoints: List<LatLng>,
        val distance: String,
        val duration: String,
        val distanceKm: Double,
        val durationMinutes: Int,
        val instructions: List<String>
    )

    /**
     * Get real road route between two points
     */
    suspend fun getRealRoute(
        origin: LatLng,
        destination: LatLng,
        transportMode: String = "driving"
    ): RouteResult? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting real route from $origin to $destination with mode $transportMode")

            val routeInfo = directionsService.getRoute(origin, destination, transportMode)

            if (routeInfo != null) {
                val result = RouteResult(
                    polylinePoints = routeInfo.polylinePoints,
                    distance = routeInfo.distance,
                    duration = routeInfo.duration,
                    distanceKm = routeInfo.distanceValue / 1000.0,
                    durationMinutes = routeInfo.durationValue / 60,
                    instructions = routeInfo.instructions
                )

                Log.d(TAG, "Route result: ${result.distance}, ${result.duration}, ${result.polylinePoints.size} points")
                return@withContext result
            } else {
                Log.w(TAG, "No route info returned from directions service")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting real route", e)
            return@withContext null
        }
    }

    /**
     * Get travel times for multiple transport modes
     */
    suspend fun getTravelTimesForAllModes(
        origin: LatLng,
        destination: LatLng
    ): Map<String, String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting travel times for all modes from $origin to $destination")

            val routes = directionsService.getMultipleRouteOptions(origin, destination)
            val travelTimes = mutableMapOf<String, String>()

            routes.forEach { (mode, routeInfo) ->
                if (routeInfo != null) {
                    travelTimes[mode] = routeInfo.duration
                    Log.d(TAG, "Travel time for $mode: ${routeInfo.duration}")
                } else {
                    // Fallback to estimated time
                    val distance = calculateDistance(origin, destination)
                    val estimatedTime = when (mode) {
                        "walking" -> estimateTravelTime(distance * 2.5, "Light")
                        "bicycling" -> estimateTravelTime(distance * 1.2, "Light")
                        "driving" -> estimateTravelTime(distance, estimateTrafficCondition())
                        else -> estimateTravelTime(distance, "Moderate")
                    }
                    val fallbackTime = formatTravelTime(estimatedTime)
                    travelTimes[mode] = fallbackTime
                    Log.d(TAG, "Fallback travel time for $mode: $fallbackTime")
                }
            }

            return@withContext travelTimes
        } catch (e: Exception) {
            Log.e(TAG, "Error getting travel times for all modes", e)
            return@withContext emptyMap()
        }
    }

    /**
     * Get address from latitude and longitude using Google Maps API and Geocoder
     */
    suspend fun getAddressFromLocation(latLng: LatLng): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🏠 Getting address for location: $latLng")
            
            // Try Google Maps API first (more reliable)
            val googleMapsAddress = getAddressFromGoogleMapsAPI(latLng)
            if (googleMapsAddress.isNotEmpty() && googleMapsAddress != "Location unavailable") {
                Log.d(TAG, "✅ Google Maps API address: $googleMapsAddress")
                return@withContext googleMapsAddress
            }
            
            // Fallback to Geocoder
            Log.d(TAG, "🔄 Google Maps API failed, trying Geocoder...")
            val geocoderAddress = getAddressFromGeocoder(latLng)
            if (geocoderAddress.isNotEmpty() && geocoderAddress != "Location unavailable") {
                Log.d(TAG, "✅ Geocoder address: $geocoderAddress")
                return@withContext geocoderAddress
            }
            
            // Final fallback to coordinates
            Log.d(TAG, "⚠️ Both methods failed, returning coordinates")
            return@withContext "📍 ${String.format("%.4f", latLng.latitude)}, ${String.format("%.4f", latLng.longitude)}"
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting address", e)
            return@withContext "📍 ${String.format("%.4f", latLng.latitude)}, ${String.format("%.4f", latLng.longitude)}"
        }
    }
    
    /**
     * Get address using Google Maps API (Places API)
     */
    private suspend fun getAddressFromGoogleMapsAPI(latLng: LatLng): String = withContext(Dispatchers.IO) {
        try {
            if (placesClient == null) {
                Log.w(TAG, "⚠️ Places client not initialized")
                return@withContext ""
            }
            
            Log.d(TAG, "🔍 Using Google Maps API for address resolution...")
            
            // For now, we'll use the Geocoder as the primary method since Places API
            // reverse geocoding requires additional setup and may not be available
            // This method can be enhanced later with proper Places API implementation
            return@withContext ""
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error with Google Maps API", e)
            return@withContext ""
        }
    }

    /**
     * Get address using Geocoder
     */
    private suspend fun getAddressFromGeocoder(latLng: LatLng): String = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            var addressText = ""

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Use the new API for Android 13+
                val addressResult = suspendCancellableCoroutine<List<Address>?> { continuation ->
                    geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1) { addresses ->
                        continuation.resume(addresses)
                    }
                }

                if (addressResult != null && addressResult.isNotEmpty()) {
                    val address = addressResult[0]
                    addressText = formatAddress(address)
                }
            } else {
                // Use the old API for Android 12 and below
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                if (addresses != null && addresses.isNotEmpty()) {
                    val address = addresses[0]
                    addressText = formatAddress(address)
                }
            }

            return@withContext if (addressText.isEmpty()) {
                "Location unavailable"
            } else {
                addressText
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error getting address with geocoder", e)
            return@withContext "Location unavailable"
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error getting address", e)
            return@withContext "Location unavailable"
        }
    }

    /**
     * Format address into a readable string
     */
    private fun formatAddress(address: Address): String {
        val parts = mutableListOf<String>()

        // Add feature name (usually the most detailed part like building name)
        if (!address.featureName.isNullOrEmpty() &&
            address.featureName != address.thoroughfare &&
            address.featureName != address.subThoroughfare) {
            parts.add(address.featureName)
        }

        // Add thoroughfare (street name)
        if (!address.thoroughfare.isNullOrEmpty()) {
            if (!address.subThoroughfare.isNullOrEmpty()) {
                parts.add("${address.subThoroughfare} ${address.thoroughfare}")
            } else {
                parts.add(address.thoroughfare)
            }
        }

        // Add locality (city)
        if (!address.locality.isNullOrEmpty()) {
            parts.add(address.locality)
        }

        // Add admin area (state/province)
        if (!address.adminArea.isNullOrEmpty() &&
            address.adminArea != address.locality) {
            parts.add(address.adminArea)
        }

        // Add country
        if (!address.countryName.isNullOrEmpty()) {
            parts.add(address.countryName)
        }

        return parts.joinToString(", ").ifEmpty { "Unknown location" }
    }

    /**
     * Calculate distance between two points using Haversine formula
     * @return distance in kilometers
     */
    fun calculateDistance(startLatLng: LatLng, endLatLng: LatLng): Double {
        val startLatRad = Math.toRadians(startLatLng.latitude)
        val endLatRad = Math.toRadians(endLatLng.latitude)
        val latDiffRad = Math.toRadians(endLatLng.latitude - startLatLng.latitude)
        val lngDiffRad = Math.toRadians(endLatLng.longitude - startLatLng.longitude)

        val a = sin(latDiffRad / 2).pow(2) +
                cos(startLatRad) * cos(endLatRad) *
                sin(lngDiffRad / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS_KM * c
    }

    /**
     * Estimate travel time between two points (fallback method)
     * @return time in minutes
     */
    fun estimateTravelTime(distance: Double, trafficCondition: String): Int {
        // Base time calculation using the traffic condition
        val minutesPerKm = when (trafficCondition) {
            "Light" -> Random.nextDouble(LIGHT_TRAFFIC_MIN, LIGHT_TRAFFIC_MAX)
            "Moderate" -> Random.nextDouble(MODERATE_TRAFFIC_MIN, MODERATE_TRAFFIC_MAX)
            "Heavy" -> Random.nextDouble(HEAVY_TRAFFIC_MIN, HEAVY_TRAFFIC_MAX)
            else -> 1.0 // Default to average
        }

        return (distance * minutesPerKm).toInt().coerceAtLeast(1)
    }

    /**
     * Estimate traffic condition based on time of day and randomness
     */
    fun estimateTrafficCondition(): String {
        // Get current hour
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val random = Random.nextFloat()

        // Rush hour conditions (7-9 AM and 5-7 PM)
        val isRushHour = (currentHour in 7..9) || (currentHour in 17..19)

        return when {
            isRushHour -> {
                when {
                    random < 0.2 -> "Light"
                    random < 0.6 -> "Moderate"
                    else -> "Heavy"
                }
            }
            currentHour in 22..6 -> { // Late night/early morning
                when {
                    random < 0.8 -> "Light"
                    else -> "Moderate"
                }
            }
            else -> { // Regular hours
                when {
                    random < 0.5 -> "Light"
                    random < 0.8 -> "Moderate"
                    else -> "Heavy"
                }
            }
        }
    }

    /**
     * Format distance for display
     */
    fun formatDistance(distanceKm: Double): String {
        return if (distanceKm < 1.0) {
            "${(distanceKm * 1000).toInt()} m"
        } else {
            String.format("%.1f km", distanceKm)
        }
    }

    /**
     * Format travel time for display
     */
    fun formatTravelTime(timeMinutes: Int): String {
        return if (timeMinutes < 60) {
            "$timeMinutes min"
        } else {
            val hours = timeMinutes / 60
            val minutes = timeMinutes % 60
            if (minutes == 0) {
                "$hours h"
            } else {
                "$hours h $minutes min"
            }
        }
    }

    /**
     * Convert transport mode enum to API string
     */
    fun transportModeToApiString(transportMode: Any): String {
        return when (transportMode.toString().uppercase()) {
            "WALKING" -> "walking"
            "MOTORCYCLE", "BICYCLING" -> "bicycling"
            "CAR", "DRIVING" -> "driving"
            else -> "driving"
        }
    }

    /**
     * Get real-time location updates with high accuracy
     */
    suspend fun getRealTimeLocation(): android.location.Location? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting real-time location with high accuracy")
            
            // Use the background location service if available
            try {
                val backgroundLocation = BackgroundLocationService.getLastLocation(context)
                if (backgroundLocation != null) {
                    val timeDiff = System.currentTimeMillis() - backgroundLocation.time
                    if (timeDiff < 30000) { // Use if less than 30 seconds old
                        Log.d(TAG, "Using recent background location: ${backgroundLocation.latitude}, ${backgroundLocation.longitude}")
                        return@withContext backgroundLocation
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Background location service not available, using fallback", e)
            }
            
            // Fallback: actively request a fresh high-accuracy reading
            suspendCancellableCoroutine { continuation ->
                try {
                    val tokenSource = CancellationTokenSource()
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token)
                        .addOnSuccessListener { location ->
                            if (location != null) {
                                Log.d(TAG, "Fresh location obtained: ${location.latitude}, ${location.longitude}")
                                continuation.resume(location)
                            } else {
                                Log.w(TAG, "Fresh location is null")
                                continuation.resume(null)
                            }
                        }
                        .addOnFailureListener { exception ->
                            Log.e(TAG, "Failed to get fresh location", exception)
                            continuation.resume(null)
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting fresh location", e)
                    continuation.resume(null)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getRealTimeLocation", e)
            null
        }
    }

    /**
     * Check if real-time location services are available
     */
    fun isRealTimeLocationAvailable(): Boolean {
        return try {
            // Check if location permissions are granted
            val hasLocationPermission = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                    context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            hasLocationPermission
        } catch (e: Exception) {
            Log.e(TAG, "Error checking real-time location availability", e)
            false
        }
    }

    /**
     * Get location accuracy information
     */
    fun getLocationAccuracyInfo(location: android.location.Location): String {
        return when {
            location.hasAccuracy() -> {
                val accuracy = location.accuracy
                when {
                    accuracy <= 5 -> "High accuracy (±${accuracy.toInt()}m)"
                    accuracy <= 20 -> "Good accuracy (±${accuracy.toInt()}m)"
                    accuracy <= 50 -> "Fair accuracy (±${accuracy.toInt()}m)"
                    else -> "Low accuracy (±${accuracy.toInt()}m)"
                }
            }
            else -> "Unknown accuracy"
        }
    }

    /**
     * Start continuous high-accuracy location updates.
     * The provided callback receives each new Location. Caller must call stopContinuousLocationUpdates.
     */
    fun startContinuousLocationUpdates(
        intervalMs: Long = REALTIME_UPDATE_INTERVAL,
        fastestIntervalMs: Long = FASTEST_REALTIME_UPDATE_INTERVAL,
        onLocation: (Location) -> Unit
    ) {
        if (isContinuousUpdating) {
            Log.d(TAG, "Continuous updates already running")
            return
        }

        val hasFine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            Log.w(TAG, "Missing location permission; cannot start continuous updates")
            return
        }

        val request = LocationRequest.Builder(intervalMs)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMinUpdateIntervalMillis(fastestIntervalMs)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation
                if (location != null) {
                    onLocation(location)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                callback,
                Looper.getMainLooper()
            )
            continuousCallback = callback
            isContinuousUpdating = true
            Log.d(TAG, "Started continuous high-accuracy location updates")
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing permissions for continuous updates", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start continuous updates", e)
        }
    }

    /** Stop continuous updates started via startContinuousLocationUpdates */
    fun stopContinuousLocationUpdates() {
        val callback = continuousCallback ?: return
        try {
            fusedLocationClient.removeLocationUpdates(callback)
            Log.d(TAG, "Stopped continuous high-accuracy location updates")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop continuous updates", e)
        } finally {
            continuousCallback = null
            isContinuousUpdating = false
        }
    }
}

/*
    package com.example.gzingapp.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.*

class LocationHelper(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    companion object {
        private const val TAG = "LocationHelper"
        private const val EARTH_RADIUS_KM = 6371.0
    }

    /**
     * Calculate distance between two LatLng points using Haversine formula
     * @param start Starting point
     * @param end Ending point
     * @return Distance in kilometers
     */
    fun calculateDistance(start: LatLng, end: LatLng): Double {
        val lat1Rad = Math.toRadians(start.latitude)
        val lat2Rad = Math.toRadians(end.latitude)
        val deltaLat = Math.toRadians(end.latitude - start.latitude)
        val deltaLng = Math.toRadians(end.longitude - start.longitude)

        val a = sin(deltaLat / 2).pow(2) +
                cos(lat1Rad) * cos(lat2Rad) * sin(deltaLng / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS_KM * c
    }

    /**
     * Calculate bearing from start to end point
     * @param start Starting point
     * @param end Ending point
     * @return Bearing in degrees
     */
    fun calculateBearing(start: LatLng, end: LatLng): Double {
        val lat1Rad = Math.toRadians(start.latitude)
        val lat2Rad = Math.toRadians(end.latitude)
        val deltaLng = Math.toRadians(end.longitude - start.longitude)

        val y = sin(deltaLng) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(deltaLng)

        val bearingRad = atan2(y, x)
        return (Math.toDegrees(bearingRad) + 360) % 360
    }

    /**
     * Get current location asynchronously
     */
    suspend fun getCurrentLocation(): LatLng? = suspendCancellableCoroutine { continuation ->
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission not granted")
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        val latLng = LatLng(location.latitude, location.longitude)
                        Log.d(TAG, "Current location: $latLng")
                        continuation.resume(latLng)
                    } else {
                        Log.w(TAG, "Current location is null")
                        continuation.resume(null)
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Failed to get current location", exception)
                    continuation.resumeWithException(exception)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current location", e)
            continuation.resumeWithException(e)
        }
    }

    /**
     * Check if location permissions are granted
     */
    fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Format distance for display
     */
    fun formatDistance(distanceKm: Double): String {
        return if (distanceKm < 1.0) {
            "${(distanceKm * 1000).toInt()} m"
        } else {
            "${String.format("%.1f", distanceKm)} km"
        }
    }

    /**
     * Check if a location is within Antipolo-Marikina area (approximate bounds)
     */
    fun isWithinServiceArea(location: LatLng): Boolean {
        // Approximate bounds for Antipolo-Marikina area
        val minLat = 14.5500 // South boundary
        val maxLat = 14.6500 // North boundary
        val minLng = 121.1000 // West boundary
        val maxLng = 121.2500 // East boundary

        return location.latitude in minLat..maxLat &&
               location.longitude in minLng..maxLng
    }

    /**
     * Get default location (Center of Antipolo-Marikina area)
     */
    fun getDefaultLocation(): LatLng {
        return LatLng(14.5995, 121.1817) // Antipolo coordinates
    }
}
 */