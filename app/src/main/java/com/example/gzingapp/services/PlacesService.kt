package com.example.gzingapp.services

import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import com.example.gzingapp.ui.places.PlaceItem
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.*
import com.google.android.libraries.places.api.net.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.*

class PlacesService(private val context: Context) {

    private var placesClient: PlacesClient? = null
    private val geocoder = Geocoder(context, Locale.getDefault())

    companion object {
        private const val TAG = "PlacesService"
        private const val EARTH_RADIUS_KM = 6371.0
        private const val SEARCH_RADIUS_METERS = 5000 // 5km radius

        // Antipolo-Marikina service area bounds
        private const val MIN_LAT = 14.5500 // South boundary
        private const val MAX_LAT = 14.6500 // North boundary
        private const val MIN_LNG = 121.1000 // West boundary
        private const val MAX_LNG = 121.2500 // East boundary

        // Default locations
        private val ANTIPOLO_CENTER = LatLng(14.5995, 121.1817)
        private val MARIKINA_CENTER = LatLng(14.6507, 121.1029)
    }

    init {
        initializePlacesApi()
    }

    private fun initializePlacesApi() {
        try {
            if (!Places.isInitialized()) {
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
                    Log.w(TAG, "Google Maps API key not found or is placeholder")
                }
            } else {
                placesClient = Places.createClient(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Places API", e)
        }
    }

    /**
     * Search for nearby places using Google Places API with Antipolo-Marikina filtering
     */
    suspend fun searchNearbyPlaces(location: LatLng): List<PlaceItem> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Searching nearby places at: $location")

            // Check if location is within service area
            if (!isWithinServiceArea(location)) {
                Log.d(TAG, "Location outside service area, using nearest service center")
                val nearestCenter = getNearestServiceCenter(location)
                return@withContext searchNearbyPlaces(nearestCenter)
            }

            val client = placesClient
            return@withContext if (client != null) {
                val placeFields = listOf(
                    Place.Field.ID,
                    Place.Field.NAME,
                    Place.Field.LAT_LNG,
                    Place.Field.ADDRESS,
                    Place.Field.PHONE_NUMBER,
                    Place.Field.RATING,
                    Place.Field.PRICE_LEVEL,
                    Place.Field.OPENING_HOURS,
                    Place.Field.PHOTO_METADATAS,
                    Place.Field.TYPES,
                    Place.Field.BUSINESS_STATUS
                )

                val request = FindCurrentPlaceRequest.newInstance(placeFields)
                val places = mutableListOf<PlaceItem>()

                try {
                    val response = suspendCancellableCoroutine<FindCurrentPlaceResponse> { continuation ->
                        client.findCurrentPlace(request)
                            .addOnSuccessListener { response ->
                                continuation.resume(response)
                            }
                            .addOnFailureListener { exception ->
                                Log.e(TAG, "Places API error", exception)
                                continuation.resume(FindCurrentPlaceResponse.newInstance(emptyList()))
                            }
                    }

                    response.placeLikelihoods.forEach { placeLikelihood ->
                        val place = placeLikelihood.place
                        val placeLocation = place.latLng

                        if (placeLocation != null && isWithinServiceArea(placeLocation)) {
                            val distance = calculateDistance(location, placeLocation)
                            if (distance <= SEARCH_RADIUS_METERS / 1000.0) { // Convert to km
                                val placeItem = createPlaceItem(place, location)
                                if (placeItem != null) {
                                    places.add(placeItem)
                                }
                            }
                        }
                    }

                    // If no places found from API, use nearby search
                    if (places.isEmpty()) {
                        places.addAll(performNearbySearch(location))
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error in findCurrentPlace", e)
                    places.addAll(performNearbySearch(location))
                }

                // Sort by distance and limit results
                val sortedPlaces = places.sortedBy { it.distance ?: Double.MAX_VALUE }.take(20)
                Log.d(TAG, "Found ${sortedPlaces.size} places nearby")

                if (sortedPlaces.isNotEmpty()) {
                    sortedPlaces
                } else {
                    getSamplePlacesNearLocation(location)
                }
            } else {
                Log.w(TAG, "Places client not initialized, returning sample data")
                getSamplePlacesNearLocation(location)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching nearby places", e)
            return@withContext getSamplePlacesNearLocation(location)
        }
    }

    /**
     * Perform nearby search using text-based queries
     */
    private suspend fun performNearbySearch(location: LatLng): List<PlaceItem> {
        val places = mutableListOf<PlaceItem>()
        val searchQueries = listOf(
            "restaurants", "shopping malls", "hospitals", "schools",
            "parks", "churches", "gas stations", "banks"
        )

        for (query in searchQueries) {
            try {
                val queryPlaces = searchPlacesByText("$query near me", location)
                places.addAll(queryPlaces.take(5)) // Limit per category
            } catch (e: Exception) {
                Log.e(TAG, "Error searching for $query", e)
            }
        }

        return places.distinctBy { it.id }.take(20)
    }

    /**
     * Get photo URL from Google Places API
     */
    private suspend fun getPlacePhotoUrl(place: Place): String? {
        return try {
            val photoMetadatas = place.photoMetadatas
            if (photoMetadatas != null && photoMetadatas.isNotEmpty()) {
                val photoMetadata = photoMetadatas.first()

                val client = placesClient
                if (client != null) {
                    val photoRequest = FetchPhotoRequest.builder(photoMetadata)
                        .setMaxWidth(400) // Reasonable size for list items
                        .setMaxHeight(300)
                        .build()

                    val photoResponse = suspendCancellableCoroutine<FetchPhotoResponse> { continuation ->
                        client.fetchPhoto(photoRequest)
                            .addOnSuccessListener { response ->
                                continuation.resume(response)
                            }
                            .addOnFailureListener { exception ->
                                Log.e(TAG, "Error fetching photo", exception)
                                continuation.resume(FetchPhotoResponse.newInstance(null))
                            }
                    }

                    val bitmap = photoResponse.bitmap
                    if (bitmap != null) {
                        // Convert bitmap to base64 string for easy storage/transmission
                        val byteArrayOutputStream = java.io.ByteArrayOutputStream()
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
                        val byteArray = byteArrayOutputStream.toByteArray()
                        val base64String = android.util.Base64.encodeToString(byteArray, android.util.Base64.DEFAULT)
                        Log.d(TAG, "Successfully fetched photo for ${place.name}")
                        return "data:image/jpeg;base64,$base64String"
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting place photo", e)
            null
        }
    }

    /**
     * Search places by text query with location bias
     */
    suspend fun searchPlacesByText(query: String, location: LatLng?): List<PlaceItem> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Searching places by text: $query")

            val client = placesClient
            return@withContext if (client != null) {
                val placeFields = listOf(
                    Place.Field.ID,
                    Place.Field.NAME,
                    Place.Field.LAT_LNG,
                    Place.Field.ADDRESS,
                    Place.Field.RATING,
                    Place.Field.PRICE_LEVEL,
                    Place.Field.OPENING_HOURS,
                    Place.Field.PHOTO_METADATAS,
                    Place.Field.TYPES,
                    Place.Field.BUSINESS_STATUS
                )

                val requestBuilder = FindAutocompletePredictionsRequest.builder()
                    .setQuery(query)
                    .setCountries("PH") // Philippines only

                // Add location bias to prioritize Antipolo/Marikina area
                val locationBias = if (location != null && isWithinServiceArea(location)) {
                    // If user location is within service area, create bias around it
                    RectangularBounds.newInstance(
                        LatLng(location.latitude - 0.02, location.longitude - 0.02),
                        LatLng(location.latitude + 0.02, location.longitude + 0.02)
                    )
                } else {
                    // Create bias covering the entire Antipolo/Marikina area
                    RectangularBounds.newInstance(
                        LatLng(MIN_LAT, MIN_LNG),
                        LatLng(MAX_LAT, MAX_LNG)
                    )
                }
                requestBuilder.setLocationBias(locationBias)

                val request = requestBuilder.build()

                val predictions = suspendCancellableCoroutine<FindAutocompletePredictionsResponse> { continuation ->
                    client.findAutocompletePredictions(request)
                        .addOnSuccessListener { response ->
                            Log.d(TAG, "Autocomplete predictions received: ${response.autocompletePredictions.size}")
                            continuation.resume(response)
                        }
                        .addOnFailureListener { exception ->
                            Log.e(TAG, "Autocomplete error for query: $query", exception)
                            continuation.resume(FindAutocompletePredictionsResponse.newInstance(emptyList()))
                        }
                }

                val places = mutableListOf<PlaceItem>()
                val validPredictions = predictions.autocompletePredictions
                    .filter { prediction ->
                        val address = prediction.getFullText(null).toString().lowercase()
                        val isValid = isAddressInServiceArea(address)
                        Log.d(TAG, "Prediction: $address, Valid: $isValid")
                        isValid
                    }
                    .take(15) // Increased limit for better search results

                for (prediction in validPredictions) {
                    try {
                        val placeId = prediction.placeId
                        val fetchPlaceRequest = FetchPlaceRequest.newInstance(placeId, placeFields)

                        val placeResponse = suspendCancellableCoroutine<FetchPlaceResponse> { continuation ->
                            client.fetchPlace(fetchPlaceRequest)
                                .addOnSuccessListener { response ->
                                    continuation.resume(response)
                                }
                                .addOnFailureListener { exception ->
                                    Log.e(TAG, "Fetch place error for ID: $placeId", exception)
                                    continuation.resume(FetchPlaceResponse.newInstance(null))
                                }
                        }

                        val place = placeResponse.place
                        if (place != null) {
                            val placeLatLng = place.latLng
                            if (placeLatLng != null && isWithinServiceArea(placeLatLng)) {
                                val placeItem = createPlaceItem(place, location ?: ANTIPOLO_CENTER)
                                if (placeItem != null) {
                                    places.add(placeItem)
                                    Log.d(TAG, "Added place: ${place.name} at ${place.latLng}")
                                }
                            } else {
                                Log.d(TAG, "Filtered out place: ${place.name} (outside service area)")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching place details", e)
                    }
                }

                places.sortedBy { it.distance ?: Double.MAX_VALUE }
            } else {
                Log.w(TAG, "Places client not initialized for text search")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in text search", e)
            return@withContext emptyList()
        }
    }

    /**
     * Create PlaceItem from Google Places API Place object
     */
    private suspend fun createPlaceItem(place: Place, userLocation: LatLng): PlaceItem? {
        return try {
            val placeLocation = place.latLng ?: return null
            val distance = calculateDistance(userLocation, placeLocation)

            // Get address using reverse geocoding
            val address = getAddressFromLocation(placeLocation)

            // Determine category from place types
            val category = determineCategoryFromTypes(place.types ?: emptyList())

            // Get photo URL if available
            val photoUrl = getPlacePhotoUrl(place)

            PlaceItem(
                id = place.id ?: "",
                name = place.name ?: "Unknown Place",
                category = category,
                location = extractCityFromAddress(address),
                address = address,
                rating = place.rating?.toFloat(),
                priceLevel = place.priceLevel,
                isOpen = determineOpenStatus(place.openingHours),
                photoUrl = photoUrl,
                latLng = placeLocation,
                distance = distance
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creating place item", e)
            null
        }
    }

    /**
     * Get address from coordinates using reverse geocoding
     */
    private suspend fun getAddressFromLocation(latLng: LatLng): String = withContext(Dispatchers.IO) {
        try {
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val addressResult = suspendCancellableCoroutine<List<Address>?> { continuation ->
                    geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1) { addresses ->
                        continuation.resume(addresses)
                    }
                }

                val firstAddress = addressResult?.firstOrNull()
                if (firstAddress != null) {
                    formatAddress(firstAddress)
                } else {
                    "Unknown Address"
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                val firstAddress = addresses?.firstOrNull()
                if (firstAddress != null) {
                    formatAddress(firstAddress)
                } else {
                    "Unknown Address"
                }
            }
            return@withContext result
        } catch (e: IOException) {
            Log.e(TAG, "Geocoding error", e)
            return@withContext "Unknown Address"
        }
    }

    /**
     * Format address from Geocoder result
     */
    private fun formatAddress(address: Address): String {
        val parts = mutableListOf<String>()

        address.subThoroughfare?.let { parts.add(it) }
        address.thoroughfare?.let { parts.add(it) }
        address.locality?.let { parts.add(it) }
        address.adminArea?.let { parts.add(it) }

        return if (parts.isNotEmpty()) {
            parts.joinToString(", ")
        } else {
            "Unknown Address"
        }
    }

    /**
     * Extract city name from full address
     */
    private fun extractCityFromAddress(address: String): String {
        return when {
            address.contains("Antipolo", ignoreCase = true) -> "Antipolo City"
            address.contains("Marikina", ignoreCase = true) -> "Marikina City"
            address.contains("Pasig", ignoreCase = true) -> "Pasig City"
            else -> {
                val parts = address.split(",")
                if (parts.size > 1) {
                    parts[1].trim()
                } else {
                    "Unknown City"
                }
            }
        }
    }

    /**
     * Determine category from Google Places types
     */
    private fun determineCategoryFromTypes(types: List<Place.Type>): String {
        return when {
            types.contains(Place.Type.RESTAURANT) || types.contains(Place.Type.FOOD) -> "Restaurant"
            types.contains(Place.Type.SHOPPING_MALL) || types.contains(Place.Type.STORE) -> "Shopping Mall"
            types.contains(Place.Type.HOSPITAL) || types.contains(Place.Type.PHARMACY) -> "Healthcare"
            types.contains(Place.Type.SCHOOL) || types.contains(Place.Type.UNIVERSITY) -> "Education"
            types.contains(Place.Type.PARK) || types.contains(Place.Type.TOURIST_ATTRACTION) -> "Recreation"
            types.contains(Place.Type.CHURCH) || types.contains(Place.Type.PLACE_OF_WORSHIP) -> "Religious Site"
            types.contains(Place.Type.GAS_STATION) -> "Gas Station"
            types.contains(Place.Type.BANK) || types.contains(Place.Type.ATM) -> "Financial"
            types.contains(Place.Type.LODGING) -> "Accommodation"
            else -> "Place"
        }
    }

    /**
     * Determine if place is currently open - Fixed method
     */
    private fun determineOpenStatus(openingHours: OpeningHours?): Boolean? {
        if (openingHours == null) return null

        return try {
            // Try to access isOpenNow via reflection as the safest approach
            try {
                val field = openingHours.javaClass.getDeclaredField("isOpenNow")
                field.isAccessible = true
                val result = field.get(openingHours) as? Boolean
                result
            } catch (e: Exception) {
                Log.d(TAG, "Could not access isOpenNow field: ${e.message}")
                // Fallback: try to determine from periods if available
                try {
                    val periodsField = openingHours.javaClass.getDeclaredField("periods")
                    periodsField.isAccessible = true
                    val periods = periodsField.get(openingHours)
                    // If periods exist, assume it's open (simplified logic)
                    if (periods != null) {
                        true
                    } else {
                        null
                    }
                } catch (e2: Exception) {
                    Log.d(TAG, "Could not determine open status: ${e2.message}")
                    null // Cannot determine
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot determine open status", e)
            null
        }
    }

    /**
     * Check if location is within Antipolo-Marikina service area
     */
    private fun isWithinServiceArea(location: LatLng): Boolean {
        return location.latitude in MIN_LAT..MAX_LAT &&
                location.longitude in MIN_LNG..MAX_LNG
    }

    /**
     * Check if address text indicates Antipolo-Marikina area
     */
    private fun isAddressInServiceArea(address: String): Boolean {
        val lowerAddress = address.lowercase()
        return lowerAddress.contains("antipolo") ||
                lowerAddress.contains("marikina") ||
                lowerAddress.contains("rizal") ||
                lowerAddress.contains("metro manila")
    }

    /**
     * Get nearest service center if user is outside area
     */
    private fun getNearestServiceCenter(location: LatLng): LatLng {
        val distanceToAntipolo = calculateDistance(location, ANTIPOLO_CENTER)
        val distanceToMarikina = calculateDistance(location, MARIKINA_CENTER)

        return if (distanceToAntipolo < distanceToMarikina) {
            ANTIPOLO_CENTER
        } else {
            MARIKINA_CENTER
        }
    }

    /**
     * Calculate distance between two points using Haversine formula
     */
    private fun calculateDistance(start: LatLng, end: LatLng): Double {
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
     * Get sample places near a location (fallback data)
     */
    private fun getSamplePlacesNearLocation(location: LatLng): List<PlaceItem> {
        val isNearAntipolo = calculateDistance(location, ANTIPOLO_CENTER) <
                calculateDistance(location, MARIKINA_CENTER)

        return if (isNearAntipolo) {
            getAntipoloSamplePlaces()
        } else {
            getMarikinaSamplePlaces()
        }
    }

    /**
     * Sample places for Antipolo area
     */
    private fun getAntipoloSamplePlaces(): List<PlaceItem> {
        return listOf(
            PlaceItem(
                id = "antipolo_cathedral",
                name = "Antipolo Cathedral",
                category = "Religious Site",
                location = "Antipolo City",
                address = "Cathedral Road, Antipolo City",
                rating = 4.5f,
                priceLevel = null,
                isOpen = true,
                photoUrl = null,
                latLng = LatLng(14.5995, 121.1817),
                distance = 0.5
            ),
            PlaceItem(
                id = "hinulugang_taktak",
                name = "Hinulugang Taktak",
                category = "Recreation",
                location = "Antipolo City",
                address = "Taktak Road, Antipolo City",
                rating = 4.2f,
                priceLevel = null,
                isOpen = true,
                photoUrl = null,
                latLng = LatLng(14.6145, 121.1736),
                distance = 1.2
            ),
            PlaceItem(
                id = "robinsons_antipolo",
                name = "Robinsons Place Antipolo",
                category = "Shopping Mall",
                location = "Antipolo City",
                address = "Circumferential Road, Antipolo City",
                rating = 4.3f,
                priceLevel = 2,
                isOpen = true,
                photoUrl = null,
                latLng = LatLng(14.5956, 121.1783),
                distance = 0.8
            ),
            PlaceItem(
                id = "cloud9_antipolo",
                name = "Cloud 9",
                category = "Restaurant",
                location = "Antipolo City",
                address = "Sumulong Highway, Antipolo City",
                rating = 4.1f,
                priceLevel = 3,
                isOpen = true,
                photoUrl = null,
                latLng = LatLng(14.6012, 121.1889),
                distance = 1.5
            )
        )
    }

    /**
     * Sample places for Marikina area
     */
    private fun getMarikinaSamplePlaces(): List<PlaceItem> {
        return listOf(
            PlaceItem(
                id = "sm_marikina",
                name = "SM City Marikina",
                category = "Shopping Mall",
                location = "Marikina City",
                address = "Shoe Avenue, Marikina City",
                rating = 4.4f,
                priceLevel = 2,
                isOpen = true,
                photoUrl = null,
                latLng = LatLng(14.6507, 121.1029),
                distance = 0.3
            ),
            PlaceItem(
                id = "riverbanks_mall",
                name = "Riverbanks Mall",
                category = "Shopping Center",
                location = "Marikina City",
                address = "A. Bonifacio Avenue, Marikina City",
                rating = 4.2f,
                priceLevel = 2,
                isOpen = true,
                photoUrl = null,
                latLng = LatLng(14.6398, 121.1167),
                distance = 0.7
            ),
            PlaceItem(
                id = "marikina_sports_park",
                name = "Marikina Sports Park",
                category = "Recreation",
                location = "Marikina City",
                address = "Shoe Avenue, Marikina City",
                rating = 4.3f,
                priceLevel = null,
                isOpen = true,
                photoUrl = null,
                latLng = LatLng(14.6445, 121.1078),
                distance = 0.9
            ),
            PlaceItem(
                id = "marikina_river_park",
                name = "Marikina River Park",
                category = "Park",
                location = "Marikina City",
                address = "Riverbank Road, Marikina City",
                rating = 4.1f,
                priceLevel = null,
                isOpen = true,
                photoUrl = null,
                latLng = LatLng(14.6523, 121.1012),
                distance = 1.1
            )
        )
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
}