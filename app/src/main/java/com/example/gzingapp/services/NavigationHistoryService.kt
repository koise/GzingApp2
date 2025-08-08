package com.example.gzingapp.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.gzingapp.models.NavigationHistory
import com.example.gzingapp.models.NavigationStatus
import com.example.gzingapp.models.NavigationDestination
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.reflect.Type

class NavigationHistoryService(private val context: Context) {
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val gson = Gson()
    
    companion object {
        private const val TAG = "NavigationHistoryService"
        private const val PREFS_NAME = "navigation_history"
        private const val KEY_HISTORY_LIST = "history_list"
        private const val KEY_CURRENT_NAVIGATION = "current_navigation"
        private const val MAX_HISTORY_ITEMS = 100
    }
    
    /**
     * Start tracking a new navigation session
     */
    suspend fun startNavigation(
        routeDescription: String,
        startLocation: LatLng?,
        destinations: List<NavigationDestination>,
        totalDistance: Double,
        estimatedDuration: Int,
        userId: String? = null
    ): NavigationHistory = withContext(Dispatchers.IO) {
        val history = NavigationHistory(
            routeDescription = routeDescription,
            startTime = System.currentTimeMillis(),
            endTime = null,
            status = NavigationStatus.IN_PROGRESS,
            startLocation = startLocation,
            destinations = destinations,
            totalDistance = totalDistance,
            estimatedDuration = estimatedDuration,
            actualDuration = null,
            totalStops = destinations.size,
            userId = userId
        )
        
        // Save as current navigation
        saveCurrentNavigation(history)
        
        Log.d(TAG, "Started navigation tracking: ${history.routeDescription}")
        return@withContext history
    }
    
    /**
     * Update current navigation with progress
     */
    suspend fun updateNavigation(
        historyId: String,
        completedStops: Int = 0,
        alarmsTriggered: Int = 0,
        updatedDestinations: List<NavigationDestination>? = null
    ): NavigationHistory? = withContext(Dispatchers.IO) {
        val currentNav = getCurrentNavigation()
        
        if (currentNav?.id == historyId) {
            val updatedNav = currentNav.copy(
                completedStops = completedStops,
                alarmsTriggered = alarmsTriggered,
                destinations = updatedDestinations ?: currentNav.destinations
            )
            
            saveCurrentNavigation(updatedNav)
            Log.d(TAG, "Updated navigation: $completedStops/$${updatedNav.totalStops} stops completed")
            return@withContext updatedNav
        }
        
        Log.w(TAG, "Navigation with ID $historyId not found in current session")
        return@withContext null
    }
    
    /**
     * Complete navigation session successfully
     */
    suspend fun completeNavigation(
        historyId: String,
        actualDuration: Int? = null
    ): NavigationHistory? = withContext(Dispatchers.IO) {
        finishNavigation(historyId, NavigationStatus.COMPLETED, actualDuration)
    }
    
    /**
     * Cancel navigation session with improved logging
     */
    suspend fun cancelNavigation(
        historyId: String,
        actualDuration: Int? = null,
        reason: String? = null
    ): NavigationHistory? = withContext(Dispatchers.IO) {
        Log.d(TAG, "Cancelling navigation: $historyId, Reason: ${reason ?: "User cancelled"}") 
        finishNavigation(historyId, NavigationStatus.CANCELLED, actualDuration, reason)
    }
    
    /**
     * Mark navigation as failed
     */
    suspend fun failNavigation(
        historyId: String,
        actualDuration: Int? = null
    ): NavigationHistory? = withContext(Dispatchers.IO) {
        finishNavigation(historyId, NavigationStatus.FAILED, actualDuration)
    }
    
    private suspend fun finishNavigation(
        historyId: String,
        status: NavigationStatus,
        actualDuration: Int?,
        reason: String? = null
    ): NavigationHistory? = withContext(Dispatchers.IO) {
        try {
            val currentNav = getCurrentNavigation()
            
            if (currentNav?.id == historyId) {
                val calculatedDuration = actualDuration ?: run {
                    val elapsedTime = System.currentTimeMillis() - currentNav.startTime
                    (elapsedTime / 60000).toInt() // Convert to minutes
                }
                
                val finishedNav = currentNav.copy(
                    endTime = System.currentTimeMillis(),
                    status = status,
                    actualDuration = calculatedDuration
                )
                
                // Save to history
                saveToHistory(finishedNav)
                
                // Clear current navigation
                clearCurrentNavigation()
                
                val sessionType = if (currentNav.userId != null) "user" else "guest"
                Log.i(TAG, "Finished navigation: ${status.name} - ${finishedNav.routeDescription} - Session type: $sessionType, UserId: ${currentNav.userId ?: "guest"}, Reason: ${reason ?: "Not specified"}")
                return@withContext finishedNav
            }
            
            Log.w(TAG, "Navigation with ID $historyId not found in current session")
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error finishing navigation: $historyId, Status: $status", e)
            return@withContext null
        }
    }
    
    /**
     * Get all navigation history
     */
    suspend fun getNavigationHistory(userId: String? = null): List<NavigationHistory> = withContext(Dispatchers.IO) {
        try {
            val historyJson = sharedPreferences.getString(KEY_HISTORY_LIST, null)
            if (historyJson != null) {
                val listType: Type = object : TypeToken<List<NavigationHistory>>() {}.type
                val allHistory: List<NavigationHistory> = gson.fromJson(historyJson, listType)
                
                // Filter by user if specified
                val filteredHistory = if (userId != null) {
                    allHistory.filter { it.userId == userId }
                } else {
                    allHistory
                }
                
                return@withContext filteredHistory.sortedByDescending { it.startTime }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading navigation history", e)
        }
        
        return@withContext emptyList()
    }
    
    /**
     * Get current active navigation
     */
    fun getCurrentNavigation(): NavigationHistory? {
        return try {
            val navJson = sharedPreferences.getString(KEY_CURRENT_NAVIGATION, null)
            if (navJson != null) {
                gson.fromJson(navJson, NavigationHistory::class.java)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error loading current navigation", e)
            null
        }
    }
    
    /**
     * Clear navigation history
     */
    suspend fun clearHistory(): Boolean = withContext(Dispatchers.IO) {
        try {
            sharedPreferences.edit().remove(KEY_HISTORY_LIST).apply()
            Log.d(TAG, "Navigation history cleared")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing navigation history", e)
            return@withContext false
        }
    }
    
    /**
     * Delete specific navigation from history
     */
    suspend fun deleteNavigation(historyId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val history = getNavigationHistory().toMutableList()
            val updated = history.filterNot { it.id == historyId }
            
            if (updated.size != history.size) {
                saveHistoryList(updated)
                Log.d(TAG, "Deleted navigation: $historyId")
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting navigation: $historyId", e)
        }
        
        return@withContext false
    }
    
    /**
     * Alias for deleteNavigation to maintain compatibility with existing code
     */
    suspend fun deleteNavigationHistory(historyId: String): Boolean = deleteNavigation(historyId)
    
    private fun saveCurrentNavigation(navigation: NavigationHistory) {
        try {
            val json = gson.toJson(navigation)
            sharedPreferences.edit().putString(KEY_CURRENT_NAVIGATION, json).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving current navigation", e)
        }
    }
    
    private fun clearCurrentNavigation() {
        sharedPreferences.edit().remove(KEY_CURRENT_NAVIGATION).apply()
    }
    
    private suspend fun saveToHistory(navigation: NavigationHistory) = withContext(Dispatchers.IO) {
        try {
            val sessionType = if (navigation.userId != null) "user" else "guest"
            Log.d(TAG, "Saving navigation to history - Session type: $sessionType, UserId: ${navigation.userId ?: "guest"}, Status: ${navigation.status}")
            
            val history = getNavigationHistory().toMutableList()
            
            // Remove if already exists (update scenario)
            history.removeAll { it.id == navigation.id }
            
            // Add to beginning
            history.add(0, navigation)
            
            // Trim to max size
            if (history.size > MAX_HISTORY_ITEMS) {
                val trimmed = history.take(MAX_HISTORY_ITEMS)
                saveHistoryList(trimmed)
                Log.d(TAG, "Trimmed history list to $MAX_HISTORY_ITEMS items")
            } else {
                saveHistoryList(history)
            }
            
            Log.i(TAG, "Successfully saved navigation to history - ID: ${navigation.id}, Session type: $sessionType")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving to navigation history - ID: ${navigation.id}, Status: ${navigation.status}", e)
            // Try to save again with minimal information to prevent data loss
            try {
                val minimalHistory = NavigationHistory(
                    id = navigation.id,
                    routeDescription = navigation.routeDescription,
                    startTime = navigation.startTime,
                    endTime = navigation.endTime ?: System.currentTimeMillis(),
                    status = navigation.status,
                    startLocation = navigation.startLocation,
                    destinations = navigation.destinations ?: emptyList(),
                    totalDistance = navigation.totalDistance ?: 0.0,
                    estimatedDuration = navigation.estimatedDuration ?: 0,
                    actualDuration = navigation.actualDuration,
                    alarmsTriggered = navigation.alarmsTriggered ?: 0,
                    completedStops = navigation.completedStops ?: 0,
                    totalStops = navigation.totalStops ?: 0,
                    userId = navigation.userId
                )
                
                val history = getNavigationHistory().toMutableList()
                history.removeAll { it.id == minimalHistory.id }
                history.add(0, minimalHistory)
                saveHistoryList(history)
                
                Log.w(TAG, "Saved minimal navigation history after error - ID: ${navigation.id}")
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Critical error: Failed to save even minimal navigation history", fallbackError)
            }
        }
    }
    
    private fun saveHistoryList(history: List<NavigationHistory>) {
        try {
            val json = gson.toJson(history)
            sharedPreferences.edit().putString(KEY_HISTORY_LIST, json).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving history list", e)
        }
    }
}