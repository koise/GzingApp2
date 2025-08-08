package com.example.gzingapp.models

import com.google.android.gms.maps.model.LatLng
import java.util.UUID
import com.example.gzingapp.models.PlaceItem

data class RoutePoint(
    val id: String = UUID.randomUUID().toString(),
    val place: PlaceItem,
    val order: Int = 0,
    val alarmEnabled: Boolean = true,
    val estimatedTimeFromPrevious: Int = 0, // in minutes
    val distanceFromPrevious: Double = 0.0, // in kilometers
    val isCompleted: Boolean = false
) {
    val pointLabel: String
        get() = when (order) {
            0 -> "A"
            1 -> "B"
            2 -> "C"
            3 -> "D"
            4 -> "E"
            5 -> "F"
            6 -> "G"
            7 -> "H"
            8 -> "I"
            9 -> "J"
            else -> (order + 1).toString()
        }
    
    val latLng: LatLng
        get() = place.latLng
}