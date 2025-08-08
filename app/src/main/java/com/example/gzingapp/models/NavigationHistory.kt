package com.example.gzingapp.models

import com.google.android.gms.maps.model.LatLng
import java.text.SimpleDateFormat
import java.util.*

data class NavigationHistory(
    val id: String = UUID.randomUUID().toString(),
    val routeDescription: String,
    val startTime: Long,
    val endTime: Long?,
    val status: NavigationStatus,
    val startLocation: LatLng?,
    val destinations: List<NavigationDestination>,
    val totalDistance: Double, // in kilometers
    val estimatedDuration: Int, // in minutes
    val actualDuration: Int?, // in minutes
    val alarmsTriggered: Int = 0,
    val completedStops: Int = 0,
    val totalStops: Int,
    val userId: String? = null
) {
    fun getFormattedStartTime(): String {
        val formatter = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
        return formatter.format(Date(startTime))
    }
    
    fun getFormattedDuration(): String {
        val duration = actualDuration ?: estimatedDuration
        return when {
            duration < 60 -> "${duration}m"
            duration < 1440 -> "${duration / 60}h ${duration % 60}m"
            else -> "${duration / 1440}d ${(duration % 1440) / 60}h"
        }
    }
    
    fun getStatusDisplayText(): String {
        return when (status) {
            NavigationStatus.IN_PROGRESS -> "In Progress"
            NavigationStatus.COMPLETED -> "Completed"
            NavigationStatus.CANCELLED -> "Cancelled"
            NavigationStatus.FAILED -> "Failed"
        }
    }
    
    fun getCompletionPercentage(): Int {
        return if (totalStops > 0) {
            (completedStops * 100) / totalStops
        } else 0
    }
}

enum class NavigationStatus {
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    FAILED
}

data class NavigationDestination(
    val name: String,
    val address: String,
    val latLng: LatLng,
    val order: Int,
    val isCompleted: Boolean = false,
    val arrivalTime: Long? = null,
    val alarmTriggered: Boolean = false
)