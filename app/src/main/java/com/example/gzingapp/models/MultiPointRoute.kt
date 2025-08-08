package com.example.gzingapp.models

import java.util.UUID

data class MultiPointRoute(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val points: MutableList<RoutePoint> = mutableListOf(),
    val isActive: Boolean = false,
    var currentPointIndex: Int = 0,
    val totalEstimatedTime: Int = 0, // in minutes
    val totalDistance: Double = 0.0, // in kilometers
    val alarmForEachStop: Boolean = true,
    val voiceAnnouncementsEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    
    val currentPoint: RoutePoint?
        get() = if (currentPointIndex < points.size) points[currentPointIndex] else null
    
    val nextPoint: RoutePoint?
        get() = if (currentPointIndex + 1 < points.size) points[currentPointIndex + 1] else null
    
    val remainingPoints: List<RoutePoint>
        get() = points.drop(currentPointIndex + 1)
    
    val completedPoints: List<RoutePoint>
        get() = points.take(currentPointIndex)
    
    val isCompleted: Boolean
        get() = currentPointIndex >= points.size
    
    val progress: Float
        get() = if (points.isEmpty()) 0f else currentPointIndex.toFloat() / points.size.toFloat()
    
    fun addPoint(place: com.example.gzingapp.models.PlaceItem): RoutePoint {
        val routePoint = RoutePoint(
            place = place,
            order = points.size,
            alarmEnabled = alarmForEachStop
        )
        points.add(routePoint)
        return routePoint
    }
    
    fun removePoint(pointId: String) {
        val index = points.indexOfFirst { it.id == pointId }
        if (index != -1) {
            points.removeAt(index)
            // Reorder remaining points
            points.forEachIndexed { newIndex, point ->
                points[newIndex] = point.copy(order = newIndex)
            }
        }
    }
    
    fun moveToNextPoint(): Boolean {
        if (currentPointIndex < points.size - 1) {
            // Mark current point as completed
            if (currentPointIndex >= 0 && currentPointIndex < points.size) {
                points[currentPointIndex] = points[currentPointIndex].copy(isCompleted = true)
            }
            currentPointIndex++
            return true
        }
        return false
    }
    
    fun getRouteDescription(): String {
        return when (points.size) {
            0 -> "No destinations"
            1 -> "Single destination: ${points[0].place.name}"
            2 -> "${points[0].place.name} → ${points[1].place.name}"
            3 -> "${points[0].place.name} → ${points[1].place.name} → ${points[2].place.name}"
            else -> "${points[0].place.name} → ${points[1].place.name} → ... → ${points.last().place.name}"
        }
    }
}