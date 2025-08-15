package com.example.gzingapp.services

import android.util.Log
import com.example.gzingapp.models.NavigationHistory
import com.example.gzingapp.models.NavigationStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FirebaseHistoryService {
    
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val historyCollection = db.collection("navigation_history")
    
    companion object {
        private const val TAG = "FirebaseHistoryService"
        private const val MAX_HISTORY_ITEMS = 1000 // Higher limit for cloud storage
    }
    
    /**
     * Save navigation history to Firebase
     */
    suspend fun saveNavigationHistory(history: NavigationHistory): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.d(TAG, "User ID is null, skipping Firebase save")
                return@withContext Result.failure(Exception("User not authenticated for Firebase storage"))
            }
            
            // Ensure userId is set
            val historyWithUserId = history.copy(userId = currentUser.uid)
            
            // Convert LatLng to serializable format
            val historyData = historyWithUserId.toFirebaseMap()
            
            // Save to Firebase with the history ID as document ID
            historyCollection.document(historyWithUserId.id).set(historyData).await()
            
            Log.d(TAG, "Successfully saved navigation history to Firebase: ${historyWithUserId.id}")
            Result.success(historyWithUserId.id)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving navigation history to Firebase", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get navigation history from Firebase for current user
     */
    suspend fun getNavigationHistory(): Result<List<NavigationHistory>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.d(TAG, "No authenticated user, returning empty history")
                return@withContext Result.success(emptyList())
            }
            
            val querySnapshot = historyCollection
                .whereEqualTo("userId", currentUser.uid)
                .orderBy("startTime", Query.Direction.DESCENDING)
                .limit(MAX_HISTORY_ITEMS.toLong())
                .get()
                .await()
            
            val historyList = querySnapshot.documents.mapNotNull { document ->
                try {
                    NavigationHistory.fromFirebaseMap(document.data ?: emptyMap())
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing navigation history document: ${document.id}", e)
                    null
                }
            }
            
            Log.d(TAG, "Retrieved ${historyList.size} navigation history items from Firebase")
            Result.success(historyList)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving navigation history from Firebase", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete navigation history item from Firebase
     */
    suspend fun deleteNavigationHistory(historyId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                return@withContext Result.failure(Exception("User not authenticated"))
            }
            
            // Verify the document belongs to the current user before deleting
            val doc = historyCollection.document(historyId).get().await()
            if (!doc.exists()) {
                return@withContext Result.failure(Exception("History item not found"))
            }
            
            val userId = doc.getString("userId")
            if (userId != currentUser.uid) {
                return@withContext Result.failure(Exception("Not authorized to delete this history"))
            }
            
            historyCollection.document(historyId).delete().await()
            Log.d(TAG, "Successfully deleted navigation history from Firebase: $historyId")
            Result.success(true)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting navigation history from Firebase: $historyId", e)
            Result.failure(e)
        }
    }
    
    /**
     * Clear all navigation history for current user from Firebase
     */
    suspend fun clearNavigationHistory(): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                return@withContext Result.failure(Exception("User not authenticated"))
            }
            
            val querySnapshot = historyCollection
                .whereEqualTo("userId", currentUser.uid)
                .get()
                .await()
            
            val batch = db.batch()
            querySnapshot.documents.forEach { document ->
                batch.delete(document.reference)
            }
            
            batch.commit().await()
            Log.d(TAG, "Successfully cleared ${querySnapshot.size()} navigation history items from Firebase")
            Result.success(true)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing navigation history from Firebase", e)
            Result.failure(e)
        }
    }
    
    /**
     * Sync local history to Firebase (one-way sync for backup)
     */
    suspend fun syncLocalHistoryToFirebase(localHistory: List<NavigationHistory>): Result<Int> = withContext(Dispatchers.IO) {
        return@withContext try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.d(TAG, "Skipping Firebase sync for anonymous user")
                return@withContext Result.success(0)
            }
            
            // Get existing Firebase history to avoid duplicates
            val firebaseHistoryResult = getNavigationHistory()
            if (!firebaseHistoryResult.isSuccess) {
                return@withContext Result.failure(firebaseHistoryResult.exceptionOrNull() ?: Exception("Failed to get Firebase history"))
            }
            
            val existingFirebaseIds = firebaseHistoryResult.getOrNull()?.map { it.id }?.toSet() ?: emptySet()
            
            // Filter out items that already exist in Firebase
            val newHistoryItems = localHistory.filter { it.id !in existingFirebaseIds && it.userId == currentUser.uid }
            
            var syncedCount = 0
            val batch = db.batch()
            
            newHistoryItems.forEach { history ->
                val historyData = history.toFirebaseMap()
                val docRef = historyCollection.document(history.id)
                batch.set(docRef, historyData)
                syncedCount++
            }
            
            if (syncedCount > 0) {
                batch.commit().await()
                Log.d(TAG, "Successfully synced $syncedCount navigation history items to Firebase")
            } else {
                Log.d(TAG, "No new history items to sync to Firebase")
            }
            
            Result.success(syncedCount)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing local history to Firebase", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get navigation history statistics from Firebase
     */
    suspend fun getNavigationStatistics(): Result<Map<String, Any>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val historyResult = getNavigationHistory()
            if (!historyResult.isSuccess) {
                return@withContext Result.failure(historyResult.exceptionOrNull() ?: Exception("Failed to get history"))
            }
            
            val history = historyResult.getOrNull() ?: emptyList()
            
            val stats = mapOf(
                "totalNavigations" to history.size,
                "completedNavigations" to history.count { it.status == NavigationStatus.COMPLETED },
                "cancelledNavigations" to history.count { it.status == NavigationStatus.CANCELLED },
                "failedNavigations" to history.count { it.status == NavigationStatus.FAILED },
                "totalDistance" to history.sumOf { it.totalDistance ?: 0.0 },
                "totalDuration" to history.mapNotNull { it.actualDuration ?: it.estimatedDuration }.sum(),
                "averageDuration" to if (history.isNotEmpty()) {
                    history.mapNotNull { it.actualDuration ?: it.estimatedDuration }.average()
                } else 0.0,
                "totalAlarms" to history.sumOf { it.alarmsTriggered ?: 0 }
            )
            
            Log.d(TAG, "Generated navigation statistics from Firebase data")
            Result.success(stats)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating navigation statistics from Firebase", e)
            Result.failure(e)
        }
    }
}

/**
 * Extension functions to convert NavigationHistory to/from Firebase-compatible maps
 */
private fun NavigationHistory.toFirebaseMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "routeDescription" to routeDescription,
        "startTime" to startTime,
        "endTime" to endTime,
        "status" to status.name,
        "startLocation" to startLocation?.let { mapOf("latitude" to it.latitude, "longitude" to it.longitude) },
        "destinations" to destinations?.map { dest ->
            mapOf(
                "name" to dest.name,
                "address" to dest.address,
                "latitude" to dest.latLng.latitude,
                "longitude" to dest.latLng.longitude,
                "order" to dest.order
            )
        },
        "totalDistance" to totalDistance,
        "estimatedDuration" to estimatedDuration,
        "actualDuration" to actualDuration,
        "alarmsTriggered" to alarmsTriggered,
        "completedStops" to completedStops,
        "totalStops" to totalStops,
        "userId" to userId,
        "createdAt" to System.currentTimeMillis()
    )
}

private fun NavigationHistory.Companion.fromFirebaseMap(data: Map<String, Any?>): NavigationHistory {
    return NavigationHistory(
        id = data["id"] as? String ?: "",
        routeDescription = data["routeDescription"] as? String ?: "",
        startTime = (data["startTime"] as? Number)?.toLong() ?: 0L,
        endTime = (data["endTime"] as? Number)?.toLong(),
        status = try {
            NavigationStatus.valueOf(data["status"] as? String ?: "CANCELLED")
        } catch (e: Exception) {
            NavigationStatus.CANCELLED
        },
        startLocation = (data["startLocation"] as? Map<String, Any?>)?.let { loc ->
            val lat = (loc["latitude"] as? Number)?.toDouble() ?: 0.0
            val lng = (loc["longitude"] as? Number)?.toDouble() ?: 0.0
            com.google.android.gms.maps.model.LatLng(lat, lng)
        },
        destinations = (data["destinations"] as? List<Map<String, Any?>>)?.map { dest ->
            com.example.gzingapp.models.NavigationDestination(
                name = dest["name"] as? String ?: "",
                address = dest["address"] as? String ?: "",
                latLng = com.google.android.gms.maps.model.LatLng(
                    (dest["latitude"] as? Number)?.toDouble() ?: 0.0,
                    (dest["longitude"] as? Number)?.toDouble() ?: 0.0
                ),
                order = (dest["order"] as? Number)?.toInt() ?: 0
            )
        } ?: emptyList(),
        totalDistance = (data["totalDistance"] as? Number)?.toDouble() ?: 0.0,
        estimatedDuration = (data["estimatedDuration"] as? Number)?.toInt() ?: 0,
        actualDuration = (data["actualDuration"] as? Number)?.toInt(),
        alarmsTriggered = (data["alarmsTriggered"] as? Number)?.toInt() ?: 0,
        completedStops = (data["completedStops"] as? Number)?.toInt() ?: 0,
        totalStops = (data["totalStops"] as? Number)?.toInt() ?: 0,
        userId = data["userId"] as? String
    )
}











