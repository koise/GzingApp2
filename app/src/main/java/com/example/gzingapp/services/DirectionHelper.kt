package com.example.gzingapp.services

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class DirectionsService(private val context: Context) {

    companion object {
        private const val TAG = "DirectionsService"
        private const val DIRECTIONS_API_BASE_URL = "https://maps.googleapis.com/maps/api/directions/json"
    }

    data class RouteInfo(
        val polylinePoints: List<LatLng>,
        val distance: String,
        val duration: String,
        val distanceValue: Int, // in meters
        val durationValue: Int, // in seconds
        val instructions: List<String>
    )

    private fun getApiKey(): String? {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            val apiKey = appInfo.metaData?.getString("com.google.android.geo.API_KEY")

            if (apiKey != null && apiKey.isNotEmpty() && !apiKey.startsWith("\${")) {
                apiKey
            } else {
                Log.w(TAG, "Google Maps API key not found or is placeholder")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting API key", e)
            null
        }
    }

    /**
     * Get route information between two points
     */
    suspend fun getRoute(
        origin: LatLng,
        destination: LatLng,
        mode: String = "driving"
    ): RouteInfo? = withContext(Dispatchers.IO) {
        try {
            val apiKey = getApiKey() ?: return@withContext null

            val originStr = "${origin.latitude},${origin.longitude}"
            val destinationStr = "${destination.latitude},${destination.longitude}"

            val urlString = buildString {
                append(DIRECTIONS_API_BASE_URL)
                append("?origin=").append(URLEncoder.encode(originStr, "UTF-8"))
                append("&destination=").append(URLEncoder.encode(destinationStr, "UTF-8"))
                append("&mode=").append(mode)
                append("&key=").append(apiKey)
            }

            Log.d(TAG, "Requesting route: $urlString")

            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            Log.d(TAG, "Response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.use { it.readText() }

                Log.d(TAG, "API Response received, length: ${response.length}")
                return@withContext parseDirectionsResponse(response)
            } else {
                val errorStream = connection.errorStream
                if (errorStream != null) {
                    val errorReader = BufferedReader(InputStreamReader(errorStream))
                    val errorResponse = errorReader.use { it.readText() }
                    Log.e(TAG, "API Error: $errorResponse")
                }
                Log.e(TAG, "HTTP error: $responseCode")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting route", e)
            return@withContext null
        }
    }

    /**
     * Get routes for multiple transport modes
     */
    suspend fun getMultipleRouteOptions(
        origin: LatLng,
        destination: LatLng
    ): Map<String, RouteInfo?> = withContext(Dispatchers.IO) {
        try {
            val modes = listOf("walking", "bicycling", "driving")
            val results = mutableMapOf<String, RouteInfo?>()

            modes.forEach { mode ->
                results[mode] = getRoute(origin, destination, mode)
            }

            return@withContext results
        } catch (e: Exception) {
            Log.e(TAG, "Error getting multiple route options", e)
            return@withContext emptyMap()
        }
    }

    /**
     * Parse the JSON response from Google Directions API
     */
    private fun parseDirectionsResponse(jsonResponse: String): RouteInfo? {
        try {
            val jsonObject = JSONObject(jsonResponse)
            val status = jsonObject.getString("status")

            if (status != "OK") {
                Log.e(TAG, "Directions API returned status: $status")
                return null
            }

            val routes = jsonObject.getJSONArray("routes")
            if (routes.length() == 0) {
                Log.e(TAG, "No routes found in response")
                return null
            }

            val route = routes.getJSONObject(0)
            val legs = route.getJSONArray("legs")

            if (legs.length() == 0) {
                Log.e(TAG, "No legs found in route")
                return null
            }

            val leg = legs.getJSONObject(0)

            // Extract distance and duration
            val distance = leg.getJSONObject("distance")
            val duration = leg.getJSONObject("duration")

            val distanceText = distance.getString("text")
            val durationText = duration.getString("text")
            val distanceValue = distance.getInt("value")
            val durationValue = duration.getInt("value")

            // Extract polyline points
            val overviewPolyline = route.getJSONObject("overview_polyline")
            val encodedPolyline = overviewPolyline.getString("points")
            val polylinePoints = decodePolyline(encodedPolyline)

            // Extract step-by-step instructions
            val instructions = mutableListOf<String>()
            val steps = leg.getJSONArray("steps")

            for (i in 0 until steps.length()) {
                val step = steps.getJSONObject(i)
                val htmlInstructions = step.getString("html_instructions")
                // Remove HTML tags
                val cleanInstructions = htmlInstructions.replace(Regex("<[^>]*>"), "")
                instructions.add(cleanInstructions)
            }

            Log.d(TAG, "Route parsed successfully: $distanceText, $durationText, ${polylinePoints.size} points")

            return RouteInfo(
                polylinePoints = polylinePoints,
                distance = distanceText,
                duration = durationText,
                distanceValue = distanceValue,
                durationValue = durationValue,
                instructions = instructions
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing directions response", e)
            return null
        }
    }

    /**
     * Decode Google's encoded polyline format
     * Based on Google's algorithm: https://developers.google.com/maps/documentation/utilities/polylinealgorithm
     */
    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = mutableListOf<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0

            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)

            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0

            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)

            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val latLng = LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5)
            poly.add(latLng)
        }

        return poly
    }
}