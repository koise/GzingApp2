package com.example.gzingapp.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.gzingapp.models.MultiPointRoute
import com.example.gzingapp.ui.routes.RouteItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.reflect.Type

class RouteStorageService(private val context: Context) {
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val gson = Gson()
    
    companion object {
        private const val TAG = "RouteStorageService"
        private const val PREFS_NAME = "saved_routes"
        private const val KEY_ROUTES_LIST = "routes_list"
        private const val KEY_FAVORITE_ROUTES = "favorite_routes"
        private const val MAX_SAVED_ROUTES = 50
    }
    
    /**
     * Save a user-created route to local storage
     */
    suspend fun saveRoute(route: MultiPointRoute, routeName: String = ""): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val routeToSave = if (routeName.isNotBlank()) {
                route.copy(name = routeName)
            } else {
                route.copy(name = route.getRouteDescription())
            }
            
            val savedRoutes = getSavedRoutes().toMutableList()
            
            // Remove if already exists (update scenario)
            savedRoutes.removeAll { it.id == routeToSave.id }
            
            // Add to beginning
            savedRoutes.add(0, routeToSave)
            
            // Trim to max size
            if (savedRoutes.size > MAX_SAVED_ROUTES) {
                val trimmed = savedRoutes.take(MAX_SAVED_ROUTES)
                saveRoutesList(trimmed)
                Log.d(TAG, "Trimmed routes list to $MAX_SAVED_ROUTES items")
            } else {
                saveRoutesList(savedRoutes)
            }
            
            Log.i(TAG, "Successfully saved route: ${routeToSave.name} (ID: ${routeToSave.id})")
            Result.success(routeToSave.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving route", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get all saved routes
     */
    suspend fun getSavedRoutes(userId: String? = null): List<MultiPointRoute> = withContext(Dispatchers.IO) {
        return@withContext try {
            val routesJson = sharedPreferences.getString(KEY_ROUTES_LIST, null)
            if (routesJson != null) {
                val listType: Type = object : TypeToken<List<MultiPointRoute>>() {}.type
                val allRoutes: List<MultiPointRoute> = gson.fromJson(routesJson, listType)
                
                // Return routes sorted by creation date (newest first)
                allRoutes.sortedByDescending { it.createdAt }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading saved routes", e)
            emptyList()
        }
    }
    
    /**
     * Convert saved routes to RouteItem format for display in RoutesActivity
     */
    suspend fun getSavedRoutesAsItems(): List<RouteItem> = withContext(Dispatchers.IO) {
        return@withContext try {
            val savedRoutes = getSavedRoutes()
            savedRoutes.map { multiPointRoute ->
                RouteItem(
                    id = multiPointRoute.id,
                    title = if (multiPointRoute.name.isNotBlank()) multiPointRoute.name else multiPointRoute.getRouteDescription(),
                    routePoints = multiPointRoute.points,
                    duration = "${multiPointRoute.totalEstimatedTime} min",
                    distance = String.format("%.1f km", multiPointRoute.totalDistance),
                    isActive = multiPointRoute.isActive,
                    createdAt = multiPointRoute.createdAt
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting routes to items", e)
            emptyList()
        }
    }
    
    /**
     * Delete a specific route
     */
    suspend fun deleteRoute(routeId: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val savedRoutes = getSavedRoutes().toMutableList()
            val updated = savedRoutes.filterNot { it.id == routeId }
            
            if (updated.size != savedRoutes.size) {
                saveRoutesList(updated)
                Log.d(TAG, "Deleted route: $routeId")
                true
            } else {
                Log.w(TAG, "Route not found for deletion: $routeId")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting route: $routeId", e)
            false
        }
    }
    
    /**
     * Update an existing route
     */
    suspend fun updateRoute(route: MultiPointRoute): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val savedRoutes = getSavedRoutes().toMutableList()
            val index = savedRoutes.indexOfFirst { it.id == route.id }
            
            if (index != -1) {
                savedRoutes[index] = route
                saveRoutesList(savedRoutes)
                Log.d(TAG, "Updated route: ${route.name} (ID: ${route.id})")
                true
            } else {
                Log.w(TAG, "Route not found for update: ${route.id}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating route: ${route.id}", e)
            false
        }
    }
    
    /**
     * Add route to favorites
     */
    suspend fun addToFavorites(routeId: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val favorites = getFavoriteRouteIds().toMutableSet()
            favorites.add(routeId)
            saveFavoriteRouteIds(favorites)
            Log.d(TAG, "Added route to favorites: $routeId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error adding route to favorites: $routeId", e)
            false
        }
    }
    
    /**
     * Remove route from favorites
     */
    suspend fun removeFromFavorites(routeId: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val favorites = getFavoriteRouteIds().toMutableSet()
            val removed = favorites.remove(routeId)
            if (removed) {
                saveFavoriteRouteIds(favorites)
                Log.d(TAG, "Removed route from favorites: $routeId")
            }
            removed
        } catch (e: Exception) {
            Log.e(TAG, "Error removing route from favorites: $routeId", e)
            false
        }
    }
    
    /**
     * Get favorite routes
     */
    suspend fun getFavoriteRoutes(): List<MultiPointRoute> = withContext(Dispatchers.IO) {
        return@withContext try {
            val favoriteIds = getFavoriteRouteIds()
            val allRoutes = getSavedRoutes()
            allRoutes.filter { it.id in favoriteIds }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting favorite routes", e)
            emptyList()
        }
    }
    
    /**
     * Check if route is in favorites
     */
    fun isRouteFavorite(routeId: String): Boolean {
        return try {
            val favorites = getFavoriteRouteIds()
            routeId in favorites
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if route is favorite: $routeId", e)
            false
        }
    }
    
    /**
     * Clear all saved routes
     */
    suspend fun clearAllRoutes(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            sharedPreferences.edit()
                .remove(KEY_ROUTES_LIST)
                .remove(KEY_FAVORITE_ROUTES)
                .apply()
            Log.d(TAG, "Cleared all saved routes")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing all routes", e)
            false
        }
    }
    
    /**
     * Get route statistics
     */
    suspend fun getRouteStatistics(): RouteStatistics = withContext(Dispatchers.IO) {
        return@withContext try {
            val routes = getSavedRoutes()
            val favorites = getFavoriteRouteIds()
            
            val totalRoutes = routes.size
            val favoriteCount = favorites.size
            val totalDistance = routes.sumOf { it.totalDistance }
            val totalTime = routes.sumOf { it.totalEstimatedTime }
            val avgStopsPerRoute = if (routes.isNotEmpty()) routes.map { it.points.size }.average() else 0.0
            
            val mostPopularDestination = routes
                .flatMap { it.points }
                .groupBy { it.place.name }
                .maxByOrNull { it.value.size }?.key
            
            RouteStatistics(
                totalRoutes = totalRoutes,
                favoriteRoutes = favoriteCount,
                totalDistance = totalDistance,
                totalEstimatedTime = totalTime,
                averageStopsPerRoute = avgStopsPerRoute,
                mostPopularDestination = mostPopularDestination
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating route statistics", e)
            RouteStatistics()
        }
    }
    
    private fun saveRoutesList(routes: List<MultiPointRoute>) {
        try {
            val json = gson.toJson(routes)
            sharedPreferences.edit().putString(KEY_ROUTES_LIST, json).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving routes list", e)
        }
    }
    
    private fun getFavoriteRouteIds(): Set<String> {
        return try {
            val favoritesJson = sharedPreferences.getString(KEY_FAVORITE_ROUTES, null)
            if (favoritesJson != null) {
                val setType: Type = object : TypeToken<Set<String>>() {}.type
                gson.fromJson(favoritesJson, setType)
            } else {
                emptySet()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading favorite route IDs", e)
            emptySet()
        }
    }
    
    private fun saveFavoriteRouteIds(favorites: Set<String>) {
        try {
            val json = gson.toJson(favorites)
            sharedPreferences.edit().putString(KEY_FAVORITE_ROUTES, json).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving favorite route IDs", e)
        }
    }
}

/**
 * Data class for route statistics
 */
data class RouteStatistics(
    val totalRoutes: Int = 0,
    val favoriteRoutes: Int = 0,
    val totalDistance: Double = 0.0,
    val totalEstimatedTime: Int = 0,
    val averageStopsPerRoute: Double = 0.0,
    val mostPopularDestination: String? = null
)















