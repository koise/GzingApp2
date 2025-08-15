package com.example.gzingapp.ui.routes

import com.example.gzingapp.models.RoutePoint

data class RouteItem(
    val id: String,
    val title: String,
    val routePoints: List<RoutePoint>, // A->B->C->D pattern
    val duration: String,
    val distance: String,
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    
    /**
     * Get formatted route description in A -> B -> C -> D format
     */
    val routeDescription: String
        get() {
            return when (routePoints.size) {
                0 -> "No destinations"
                1 -> "${routePoints[0].pointLabel}: ${routePoints[0].place.name}"
                2 -> "${routePoints[0].pointLabel}: ${routePoints[0].place.name} → ${routePoints[1].pointLabel}: ${routePoints[1].place.name}"
                3 -> "${routePoints[0].pointLabel}: ${routePoints[0].place.name} → ${routePoints[1].pointLabel}: ${routePoints[1].place.name} → ${routePoints[2].pointLabel}: ${routePoints[2].place.name}"
                else -> "${routePoints[0].pointLabel}: ${routePoints[0].place.name} → ${routePoints[1].pointLabel}: ${routePoints[1].place.name} → ... → ${routePoints.last().pointLabel}: ${routePoints.last().place.name}"
            }
        }
    
    /**
     * Get short route description for displaying in lists
     */
    val shortDescription: String
        get() {
            return when (routePoints.size) {
                0 -> "No destinations"
                1 -> "Single stop: ${routePoints[0].place.name}"
                2 -> "${routePoints[0].place.name} → ${routePoints[1].place.name}"
                else -> "${routePoints[0].place.name} → ... → ${routePoints.last().place.name} (${routePoints.size} stops)"
            }
        }
    
    /**
     * Get detailed route points description
     */
    val detailedPointsDescription: String
        get() {
            return routePoints.mapIndexed { index, point ->
                "${point.pointLabel}: ${point.place.name}"
            }.joinToString(" → ")
        }
}