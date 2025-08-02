package com.example.gzingapp.services

import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
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
     * Get address from latitude and longitude using Geocoder
     */
    suspend fun getAddressFromLocation(latLng: LatLng): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting address for location: $latLng")
            val address = getAddressFromGeocoder(latLng)
            Log.d(TAG, "Address result: $address")
            return@withContext address
        } catch (e: Exception) {
            Log.e(TAG, "Error getting address", e)
            return@withContext "Location unavailable"
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
}