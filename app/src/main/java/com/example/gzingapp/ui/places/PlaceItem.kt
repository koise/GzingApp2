package com.example.gzingapp.ui.places

import com.google.android.gms.maps.model.LatLng

data class PlaceItem(
    val id: String,
    val name: String,
    val category: String,
    val location: String,
    val address: String,
    val rating: Float?,
    val priceLevel: Int?,
    val isOpen: Boolean?,
    val photoUrl: String?,
    val latLng: LatLng,
    val distance: Double? = null,
    val imageRes: Int = com.example.gzingapp.R.drawable.ic_places // fallback image
) {
    // Secondary constructor for backward compatibility
    constructor(name: String, category: String, location: String, imageRes: Int) : this(
        id = "",
        name = name,
        category = category,
        location = location,
        address = location,
        rating = null,
        priceLevel = null,
        isOpen = null,
        photoUrl = null,
        latLng = LatLng(0.0, 0.0),
        distance = null,
        imageRes = imageRes
    )

    // Constructor with LatLng coordinates
    constructor(name: String, category: String, location: String, imageRes: Int, latLng: LatLng) : this(
        id = "",
        name = name,
        category = category,
        location = location,
        address = location,
        rating = null,
        priceLevel = null,
        isOpen = null,
        photoUrl = null,
        latLng = latLng,
        distance = null,
        imageRes = imageRes
    )

    /**
     * Get formatted rating text for display
     */
    fun getFormattedRating(): String? {
        return rating?.let {
            "★ ${String.format("%.1f", it)}"
        }
    }

    /**
     * Get formatted distance text for display
     */
    fun getFormattedDistance(): String? {
        return distance?.let { distanceKm ->
            if (distanceKm < 1.0) {
                "${(distanceKm * 1000).toInt()} m"
            } else {
                String.format("%.1f km", distanceKm)
            }
        }
    }

    /**
     * Get formatted price level for display
     */
    fun getFormattedPriceLevel(): String? {
        return priceLevel?.let { level ->
            when (level) {
                0 -> "Free"
                1 -> "₱"
                2 -> "₱₱"
                3 -> "₱₱₱"
                4 -> "₱₱₱₱"
                else -> null
            }
        }
    }

    /**
     * Get open status text
     */
    fun getOpenStatusText(): String? {
        return when (isOpen) {
            true -> "Open"
            false -> "Closed"
            null -> null
        }
    }
}