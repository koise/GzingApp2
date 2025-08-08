package com.example.gzingapp.models

import com.google.android.gms.maps.model.LatLng

/**
 * PlaceItem model for the models package
 * This is a wrapper around the UI PlaceItem to avoid circular dependencies
 */
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
    // Constructor to create from UI PlaceItem
    constructor(uiPlaceItem: com.example.gzingapp.ui.places.PlaceItem) : this(
        id = uiPlaceItem.id,
        name = uiPlaceItem.name,
        category = uiPlaceItem.category,
        location = uiPlaceItem.location,
        address = uiPlaceItem.address,
        rating = uiPlaceItem.rating,
        priceLevel = uiPlaceItem.priceLevel,
        isOpen = uiPlaceItem.isOpen,
        photoUrl = uiPlaceItem.photoUrl,
        latLng = uiPlaceItem.latLng,
        distance = uiPlaceItem.distance,
        imageRes = uiPlaceItem.imageRes
    )

    // Convert to UI PlaceItem
    fun toUiPlaceItem(): com.example.gzingapp.ui.places.PlaceItem {
        return com.example.gzingapp.ui.places.PlaceItem(
            id = id,
            name = name,
            category = category,
            location = location,
            address = address,
            rating = rating,
            priceLevel = priceLevel,
            isOpen = isOpen,
            photoUrl = photoUrl,
            latLng = latLng,
            distance = distance,
            imageRes = imageRes
        )
    }
}