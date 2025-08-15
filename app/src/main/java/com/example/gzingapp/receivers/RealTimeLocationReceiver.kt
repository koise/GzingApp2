package com.example.gzingapp.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.util.Log

class RealTimeLocationReceiver : BroadcastReceiver() {
    
    private var locationUpdateListener: Companion.LocationUpdateListener? = null
    
    companion object {
        private const val TAG = "RealTimeLocationReceiver"
        const val ACTION_LOCATION_UPDATE = "com.example.gzingapp.LOCATION_UPDATE"
        const val ACTION_DISTANCE_UPDATE = "com.example.gzingapp.DISTANCE_UPDATE"
        const val ACTION_ROUTE_UPDATE = "com.example.gzingapp.ROUTE_UPDATE"
        
        interface LocationUpdateListener {
            fun onLocationUpdate(latitude: Double, longitude: Double, accuracy: Float, timestamp: Long)
            fun onLocationAccuracyChanged(accuracy: Float, accuracyLevel: String)
            fun onDistanceUpdate(distance: Float, radius: Float, isInside: Boolean)
            fun onGeofenceStatusChanged(isInside: Boolean, distance: Float)
            fun onRouteUpdate(latitude: Double, longitude: Double, accuracy: Float, timestamp: Long)
        }
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "📨 BroadcastReceiver onReceive called with action: ${intent.action}")
        Log.d(TAG, "🎯 Listener registered: ${locationUpdateListener != null}")
        
        when (intent.action) {
            ACTION_LOCATION_UPDATE -> {
                Log.d(TAG, "📍 Processing LOCATION_UPDATE broadcast")
                handleLocationUpdate(intent)
            }
            ACTION_DISTANCE_UPDATE -> {
                Log.d(TAG, "📏 Processing DISTANCE_UPDATE broadcast")
                handleDistanceUpdate(intent)
            }
            ACTION_ROUTE_UPDATE -> {
                Log.d(TAG, "🛣️ Processing ROUTE_UPDATE broadcast")
                handleRouteUpdate(intent)
            }
            else -> {
                Log.w(TAG, "⚠️ Unknown action received: ${intent.action}")
            }
        }
    }
    
    private fun handleLocationUpdate(intent: Intent) {
        try {
            val latitude = intent.getDoubleExtra("latitude", 0.0)
            val longitude = intent.getDoubleExtra("longitude", 0.0)
            val accuracy = intent.getFloatExtra("accuracy", 0f)
            val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
            val speed = intent.getFloatExtra("speed", 0f)
            val bearing = intent.getFloatExtra("bearing", 0f)
            
            Log.d(TAG, "🌟 LOCATION UPDATE PARSED: lat=${String.format("%.6f", latitude)}, lng=${String.format("%.6f", longitude)}, accuracy=${String.format("%.1f", accuracy)}m, speed=${String.format("%.1f", speed)}m/s")
            
            // Determine accuracy level
            val accuracyLevel = when {
                accuracy <= 10f -> "HIGH"
                accuracy <= 30f -> "MEDIUM"
                accuracy <= 50f -> "LOW"
                else -> "POOR"
            }
            
            Log.d(TAG, "🎯 Accuracy level: $accuracyLevel")
            
            if (locationUpdateListener != null) {
                Log.d(TAG, "📤 Calling listener.onLocationUpdate()...")
                locationUpdateListener?.onLocationUpdate(latitude, longitude, accuracy, timestamp)
                Log.d(TAG, "📤 Calling listener.onLocationAccuracyChanged()...")
                locationUpdateListener?.onLocationAccuracyChanged(accuracy, accuracyLevel)
                Log.d(TAG, "✅ Location update callbacks completed")
            } else {
                Log.e(TAG, "❌ No location update listener registered! Cannot forward location update.")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling location update", e)
        }
    }
    
    private fun handleDistanceUpdate(intent: Intent) {
        try {
            val distance = intent.getFloatExtra("distance", 0f)
            val radius = intent.getFloatExtra("radius", 0f)
            val isInside = intent.getBooleanExtra("isInside", false)
            
            Log.d(TAG, "Received distance update: distance=${distance}m, radius=${radius}m, inside=$isInside")
            
            locationUpdateListener?.onDistanceUpdate(distance, radius, isInside)
            locationUpdateListener?.onGeofenceStatusChanged(isInside, distance)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling distance update", e)
        }
    }
    
    private fun handleRouteUpdate(intent: Intent) {
        try {
            val latitude = intent.getDoubleExtra("latitude", 0.0)
            val longitude = intent.getDoubleExtra("longitude", 0.0)
            val accuracy = intent.getFloatExtra("accuracy", 0f)
            val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
            
            Log.d(TAG, "🛣️ Route update: lat=${String.format("%.6f", latitude)}, lng=${String.format("%.6f", longitude)}, accuracy=${String.format("%.1f", accuracy)}m")
            
            // Notify listener about route update
            locationUpdateListener?.onRouteUpdate(latitude, longitude, accuracy, timestamp)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling route update", e)
        }
    }
    
    fun setLocationUpdateListener(listener: Companion.LocationUpdateListener) {
        this.locationUpdateListener = listener
        Log.d(TAG, "✅ Location update listener SET: ${listener::class.java.simpleName}")
    }
    
    fun removeLocationUpdateListener() {
        this.locationUpdateListener = null
        Log.d(TAG, "Location update listener removed")
    }
}
