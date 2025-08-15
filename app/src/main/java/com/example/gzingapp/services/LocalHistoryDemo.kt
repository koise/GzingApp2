package com.example.gzingapp.services

import android.content.Context
import android.location.Location
import android.util.Log
import com.example.gzingapp.models.NavigationDestination
import com.example.gzingapp.models.NavigationHistory
import com.example.gzingapp.models.NavigationStatus
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Demo class to show real-time location updates and local history saving
 * This creates sample navigation sessions to demonstrate the functionality
 */
class LocalHistoryDemo(private val context: Context) {

    companion object {
        private const val TAG = "LocalHistoryDemo"
    }

    private val navigationHistoryService = NavigationHistoryService(context)
    private val locationHelper = LocationHelper(context)
    private val sessionManager = SessionManager(context) // Add SessionManager instance

    /**
     * Create a sample navigation session and save it locally
     */
    fun createSampleNavigationSession() {
        GlobalScope.launch {
            try {
                Log.d(TAG, "🎯 Creating sample navigation session...")

                // Get current location for demo
                val currentLocation = getCurrentLocationForDemo()

                // Create sample destinations
                val destinations = listOf(
                    NavigationDestination(
                        name = "Sample Destination 1",
                        address = "123 Demo Street, Sample City",
                        latLng = LatLng(14.6042, 121.0893), // Sample coordinates
                        order = 1,
                        isCompleted = true,
                        arrivalTime = System.currentTimeMillis() - 300000, // 5 minutes ago
                        alarmTriggered = true
                    ),
                    NavigationDestination(
                        name = "Final Destination",
                        address = "456 Test Avenue, Demo Town",
                        latLng = LatLng(14.6100, 121.0950), // Sample coordinates
                        order = 2,
                        isCompleted = false,
                        arrivalTime = null,
                        alarmTriggered = false
                    )
                )

                // Start a sample navigation
                val startTime = System.currentTimeMillis() - 600000 // 10 minutes ago
                val navigation = navigationHistoryService.startNavigation(
                    routeDescription = "Demo Navigation: Real-time Location Test",
                    startLocation = currentLocation,
                    destinations = destinations,
                    totalDistance = 5.2, // 5.2 km
                    estimatedDuration = 12, // 12 minutes
                    userId = "demo_user"
                )

                Log.d(TAG, "✅ Sample navigation started: ${navigation.id}")

                // Simulate some progress
                val updatedNav = navigationHistoryService.updateNavigation(
                    historyId = navigation.id,
                    completedStops = 1,
                    alarmsTriggered = 1,
                    updatedDestinations = destinations
                )

                Log.d(TAG, "✅ Navigation updated with progress: ${updatedNav?.completedStops}/${updatedNav?.totalStops}")

                // Complete the navigation after a delay
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    GlobalScope.launch {
                        val completedNav = navigationHistoryService.completeNavigation(
                            historyId = navigation.id,
                            actualDuration = 8 // Actually took 8 minutes
                        )

                        Log.d(TAG, "🏁 Navigation completed: ${completedNav?.status}")

                        // Show the saved history
                        showLocalHistorySummary()
                    }
                }, 2000) // Complete after 2 seconds

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error creating sample navigation session", e)
            }
        }
    }

    /**
     * Start recording a real navigation session with current location
     */
    fun startRealNavigationSession(destination: LatLng, destinationName: String) {
        GlobalScope.launch {
            try {
                Log.d(TAG, "🚀 Starting real navigation session to: $destinationName")

                val currentLocation = getCurrentLocationForDemo()

                val destinations = listOf(
                    NavigationDestination(
                        name = destinationName,
                        address = "Real destination address",
                        latLng = destination,
                        order = 1,
                        isCompleted = false,
                        arrivalTime = null,
                        alarmTriggered = false
                    )
                )

                // Calculate estimated distance and duration
                val distanceKm = if (currentLocation != null) {
                    locationHelper.calculateDistance(currentLocation, destination)
                } else {
                    2.0 // Default 2km
                }

                val estimatedMinutes = (distanceKm * 3).toInt() // Rough estimate: 3 minutes per km

                val navigation = navigationHistoryService.startNavigation(
                    routeDescription = "Navigation to $destinationName",
                    startLocation = currentLocation,
                    destinations = destinations,
                    totalDistance = distanceKm,
                    estimatedDuration = estimatedMinutes,
                    userId = sessionManager.getCurrentUserId() ?: "anonymous" // Fixed: Use instance method
                )

                Log.d(TAG, "✅ Real navigation session started: ${navigation.id}")
                Log.d(TAG, "📍 Distance: ${String.format("%.1f", distanceKm)}km, Estimated time: ${estimatedMinutes} minutes")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error starting real navigation session", e)
            }
        }
    }

    /**
     * Stop current navigation session
     */
    fun stopCurrentNavigationSession() {
        GlobalScope.launch {
            try {
                val currentNav = navigationHistoryService.getCurrentNavigation()
                if (currentNav != null) {
                    Log.d(TAG, "🛑 Stopping navigation session: ${currentNav.id}")

                    navigationHistoryService.completeNavigation(
                        historyId = currentNav.id,
                        actualDuration = ((System.currentTimeMillis() - currentNav.startTime) / 60000).toInt()
                    )

                    Log.d(TAG, "✅ Navigation session stopped and saved")
                } else {
                    Log.w(TAG, "⚠️ No current navigation session to stop")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error stopping navigation session", e)
            }
        }
    }

    /**
     * Show current local history summary
     */
    fun showLocalHistorySummary() {
        GlobalScope.launch {
            try {
                val history = navigationHistoryService.getNavigationHistory()
                val stats = navigationHistoryService.getNavigationStatistics()

                Log.d(TAG, "📊 LOCAL HISTORY SUMMARY")
                Log.d(TAG, "═══════════════════════")
                Log.d(TAG, "📈 Total navigations: ${stats.totalNavigations}")
                Log.d(TAG, "✅ Completed: ${stats.completedNavigations}")
                Log.d(TAG, "❌ Cancelled: ${stats.cancelledNavigations}")
                Log.d(TAG, "📍 Total distance: ${String.format("%.1f", stats.totalDistance)}km")
                Log.d(TAG, "⏱️ Total time: ${stats.totalDuration} minutes")
                Log.d(TAG, "🚨 Total alarms: ${stats.totalAlarms}")
                Log.d(TAG, "═══════════════════════")

                if (history.isNotEmpty()) {
                    Log.d(TAG, "📋 Recent navigation sessions:")
                    history.take(5).forEachIndexed { index, nav ->
                        val time = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(nav.startTime))
                        Log.d(TAG, "${index + 1}. ${nav.routeDescription} - ${nav.getStatusDisplayText()} at $time")
                    }
                } else {
                    Log.d(TAG, "📋 No navigation history found")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error showing history summary", e)
            }
        }
    }

    /**
     * Get current location for demo purposes
     */
    private suspend fun getCurrentLocationForDemo(): LatLng? {
        return try {
            // Try to get real-time location first
            val location = locationHelper.getRealTimeLocation()
            if (location != null) {
                Log.d(TAG, "📍 Got real location: ${location.latitude}, ${location.longitude}")
                LatLng(location.latitude, location.longitude)
            } else {
                // Use Antipolo center as fallback
                Log.d(TAG, "📍 Using fallback location (Antipolo center)")
                LatLng(14.5995, 121.1817)
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Could not get location for demo, using default", e)
            LatLng(14.5995, 121.1817) // Antipolo coordinates
        }
    }

    /**
     * Demonstrate real-time location tracking
     */
    fun demonstrateRealTimeLocation() {
        Log.d(TAG, "🎬 Starting real-time location demonstration...")

        // Start continuous location updates
        locationHelper.startContinuousLocationUpdates(
            intervalMs = 3000L,
            fastestIntervalMs = 1000L
        ) { location ->
            val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            Log.d(TAG, "📱 REAL-TIME LOCATION at $currentTime:")
            Log.d(TAG, "   📍 Lat: ${String.format("%.6f", location.latitude)}")
            Log.d(TAG, "   📍 Lng: ${String.format("%.6f", location.longitude)}")
            Log.d(TAG, "   🎯 Accuracy: ${String.format("%.1f", location.accuracy)}m")
            if (location.hasSpeed()) {
                Log.d(TAG, "   🚀 Speed: ${String.format("%.1f", location.speed * 3.6f)}km/h")
            }
        }

        // Stop demonstration after 30 seconds
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            Log.d(TAG, "🛑 Stopping real-time location demonstration")
            locationHelper.stopContinuousLocationUpdates()
        }, 30000)
    }

    /**
     * Clear all local history (for testing)
     */
    fun clearLocalHistory() {
        GlobalScope.launch {
            try {
                val result = navigationHistoryService.clearHistory()
                if (result) {
                    Log.d(TAG, "🗑️ Local history cleared successfully")
                } else {
                    Log.e(TAG, "❌ Failed to clear local history")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error clearing local history", e)
            }
        }
    }
}




