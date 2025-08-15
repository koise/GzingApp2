package com.example.gzingapp.ui.dashboard

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.example.gzingapp.R
import com.example.gzingapp.services.AuthService
import com.example.gzingapp.services.GeofenceHelper
import com.example.gzingapp.services.LocationHelper
import com.example.gzingapp.services.NotificationService
import com.example.gzingapp.services.SessionManager
import com.example.gzingapp.services.VoiceAnnouncementService
import com.example.gzingapp.services.NavigationHistoryService
import com.example.gzingapp.models.NavigationHistory
import com.example.gzingapp.models.NavigationDestination
import com.example.gzingapp.ui.auth.LoginActivity
import com.example.gzingapp.ui.settings.SettingsActivity
import com.example.gzingapp.services.NavigationHelper
import com.example.gzingapp.receivers.GeofenceBroadcastReceiver
import com.example.gzingapp.receivers.RealTimeLocationReceiver
import com.example.gzingapp.services.BackgroundLocationService
import com.example.gzingapp.receivers.GeofenceEventReceiver
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.RoundCap
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.example.gzingapp.ui.routes.RoutesActivity
import com.example.gzingapp.ui.places.PlacesActivity
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.LayoutInflater
import com.google.android.material.textfield.TextInputEditText
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.BitmapDescriptorFactory

class DashboardActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener, OnMapReadyCallback, RealTimeLocationReceiver.Companion.LocationUpdateListener {

    companion object {
        private const val TAG = "DashboardActivity"
        private const val CARD_EXPANDED_KEY = "card_expanded"
        private const val ROUTE_UPDATE_MIN_INTERVAL_MS = 5000L
        private const val ROUTE_UPDATE_MIN_DISTANCE_M = 20f
        
        // ENHANCED: Direct location handling constants
        private const val LOCATION_REFRESH_INTERVAL_MS = 2000L // 2 seconds for location refresh
        private const val LOADING_TIMEOUT_MS = 30000L // 30 seconds timeout for loading state
        
        // DEBUG: Log tags for easy filtering
        private const val LOCATION_TAG = "📍 LOCATION"
        private const val SERVICE_TAG = "🔧 SERVICE"
        private const val UI_TAG = "🎨 UI"
        private const val DEBUG_TAG = "🐛 DEBUG"
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_account -> {
                if (isNavigating) {
                    Toast.makeText(this, getString(R.string.drawer_locked_message), Toast.LENGTH_SHORT).show()
                    return false
                }
                
                startActivity(Intent(this, com.example.gzingapp.ui.profile.ProfileActivity::class.java))
            }
            R.id.menu_settings -> {
                if (isNavigating) {
                    Toast.makeText(this, getString(R.string.drawer_locked_message), Toast.LENGTH_SHORT).show()
                    return false
                }
                val settingsIntent = Intent(this, SettingsActivity::class.java)
                settingsLauncher.launch(settingsIntent)
            }
            R.id.menu_history -> {
                if (isNavigating) {
                    Toast.makeText(this, getString(R.string.drawer_locked_message), Toast.LENGTH_SHORT).show()
                    return false
                }
                
                startActivity(Intent(this, com.example.gzingapp.ui.history.HistoryActivity::class.java))
            }
            R.id.menu_logout -> {
                if (isNavigating) {
                    showLogoutDialog()
                } else {
                    performLogout()
                }
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var fabSOS: FloatingActionButton

    private lateinit var authService: AuthService
    private lateinit var sessionManager: SessionManager
    private lateinit var notificationService: NotificationService
    private lateinit var locationHelper: LocationHelper
    private lateinit var geofenceHelper: GeofenceHelper
    private lateinit var navigationHelper: NavigationHelper
    private lateinit var realTimeLocationReceiver: RealTimeLocationReceiver
    private lateinit var navigationHistoryService: NavigationHistoryService
    private lateinit var geofenceEventReceiver: GeofenceEventReceiver
    // Direct location handling - no more adapter needed
    
    // ENHANCED: Direct location UI components (using existing lateinit variables)
    private var tvCurrentLocationCoordinates: TextView? = null
    private var tvCurrentLocationCoordinatesCollapsed: TextView? = null
    
    // Location state management
    private var lastLocation: Location? = null
    private var isLocationLoading = false
    private var locationLoadingStartTime = 0L
    private var lastLocationRefreshTime = 0L
    private var locationRefreshCount = 0
    private var locationRefreshHandler: Handler? = null
    private var locationRefreshRunnable: Runnable? = null
    
    // ENHANCED: Current location change tracking with real-time updates
    private var lastProcessedLocation: LatLng? = null
    private var locationChangeHandler: Handler? = null
    private var locationChangeRunnable: Runnable? = null
    private val LOCATION_UPDATE_DELAY = 500L // 500ms for more responsive updates

    // Helper methods for navigation history
    private fun calculateDistance(start: LatLng?, end: LatLng): Double {
        if (start == null) return 0.0
        val earthRadius = 6371.0 // Earth's radius in kilometers
        val lat1Rad = Math.toRadians(start.latitude)
        val lat2Rad = Math.toRadians(end.latitude)
        val deltaLatRad = Math.toRadians(end.latitude - start.latitude)
        val deltaLngRad = Math.toRadians(end.longitude - start.longitude)
        
        val a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(deltaLngRad / 2) * Math.sin(deltaLngRad / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        
        return earthRadius * c
    }
    
    private fun calculateEstimatedTime(start: LatLng?, end: LatLng): Int {
        val distance = calculateDistance(start, end)
        // Estimate based on transport mode (walking ~5km/h, motorcycle ~30km/h, car ~25km/h in city)
        val speed = when (selectedTransportMode) {
            TransportMode.WALKING -> 5.0
            TransportMode.MOTORCYCLE -> 30.0
            TransportMode.CAR -> 25.0
        }
        return ((distance / speed) * 60).toInt() // Convert to minutes
    }

    // Map and location related variables
    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var pinnedLocationMarker: Marker? = null  // Red pin for destination
    private var currentPolyline: Polyline? = null
    private var geofenceCircle: Circle? = null
    private var locationPermissionGranted = false
    private var currentLocation: LatLng? = null
    private var pinnedLocation: LatLng? = null

    // Modal views
    private lateinit var modalOverlay: RelativeLayout
    private lateinit var modalCard: androidx.cardview.widget.CardView
    private lateinit var btnCloseModal: ImageButton
    private lateinit var btnCancel: TextView
    private lateinit var btnStartNavigation: LinearLayout
    private lateinit var tvNavigationText: TextView
    private lateinit var ivNavigationIcon: ImageView

    // Modal location info views
    private lateinit var tvModalLocationAddress: TextView
    private lateinit var tvModalLocationDistance: TextView
    private lateinit var tvModalTrafficCondition: TextView

    // Transportation option views
    private lateinit var transportWalking: LinearLayout
    private lateinit var transportMotorcycle: LinearLayout
    private lateinit var transportCar: LinearLayout
    private lateinit var tvWalkingTime: TextView
    private lateinit var tvMotorcycleTime: TextView
    private lateinit var tvCarTime: TextView

    private var selectedTransportMode: TransportMode = TransportMode.WALKING
    private var currentNavigationHistory: NavigationHistory? = null

    private val isNavigating: Boolean
        get() = navigationHelper.isNavigationActive()

    enum class TransportMode {
        WALKING, MOTORCYCLE, CAR
    }

    // Location card views
    private lateinit var tvCurrentLocationAddress: TextView
    private lateinit var tvPinnedLocationAddress: TextView
    private lateinit var tvEta: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvTrafficCondition: TextView
    private lateinit var tvNoPinMessage: TextView
    private lateinit var pinnedLocationSection: View
    private lateinit var routeInfoSection: View
    private lateinit var pinnedLocationDivider: View
    private lateinit var navigationStatusSection: View
    private lateinit var tvNavigationStatus: TextView
    private lateinit var btnToggleNavigation: Button
    private lateinit var progressNavigation: ProgressBar
    private lateinit var navigationInstructions: View
    
    // Geofence distance views
    private lateinit var geofenceDistanceInfo: LinearLayout
    private lateinit var tvGeofenceStatus: TextView
    private lateinit var tvGeofenceDistance: TextView

    // Expandable card views
    private lateinit var collapsedHeader: View
    private lateinit var expandableContent: View
    private lateinit var expandCollapseIcon: ImageView
    private lateinit var btnQuickNavigation: Button
    private lateinit var tvCurrentLocationSummary: TextView
    private lateinit var navigationStatusCollapsed: View
    private lateinit var tvNavigationStatusCollapsed: TextView
    private lateinit var navigationPulseCollapsed: View

    private var isCardExpanded = true
    private var lastRouteUpdateTime: Long = 0L
    private var lastRouteUpdateLocation: LatLng? = null
    private var isTrackingEnabled: Boolean = false

    // Animation objects for navigation status
    private var navigationStatusAnimation: ObjectAnimator? = null
    private var navigationStatusCollapsedAnimation: ObjectAnimator? = null
    
    // ENHANCED: Location service health monitoring
    private var locationServiceHealthHandler: Handler? = null
    private var locationServiceHealthRunnable: Runnable? = null
    


    // Background location permission request
    private val backgroundLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "Background location permission granted")
            pinnedLocation?.let {
                Log.d(TAG, "Retrying navigation setup with background permission")
                startNavigationInternal(it)
            }
        } else {
            Log.w(TAG, "Background location permission denied")
            pinnedLocationToNavigate?.let {
                startNavigationWithoutGeofence(it)
            }
        }
    }

    private var pinnedLocationToNavigate: LatLng? = null

    // Settings activity launcher to handle geofence radius changes
    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == SettingsActivity.RESULT_SETTINGS_CHANGED) {
            Log.d(TAG, "📱 Settings changed, refreshing geofence configuration...")
            
            // Refresh geofence settings
            loadGeofenceSettings()
            
            // Update existing geofence if one is active
            try {
                if (geofenceHelper.hasGeofence()) {
                    Log.d(TAG, "🔄 Updating existing geofence with new radius...")
                    geofenceHelper.updateGeofenceRadius()
                    
                    // Update geofence visualization
                    updateGeofenceCircle()
                    
                    Toast.makeText(this, "Geofence radius updated successfully", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error updating geofence after settings change", e)
            }
        }
    }

    // Location permission request
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        locationPermissionGranted = allGranted
        if (allGranted) {
            updateLocationUI()
            getDeviceLocation()
            // Ensure background location service starts once permissions are granted
            ensureBackgroundLocationServiceRunning()
        } else {
            Toast.makeText(
                this,
                getString(R.string.location_permission_denied),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Enhanced back button handling
    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            when {
                modalOverlay.visibility == View.VISIBLE -> {
                    hideModal()
                }
                drawerLayout.isDrawerOpen(GravityCompat.START) -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
                }
                isNavigating -> {
                    showStopNavigationDialog()
                }
                else -> {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        initializeServices()
        setupUI()
        setupNavigationDrawer()
        setupLocationCard()
        setupExpandableCard()
        setupModal()
        updateNavigationHeader()
        setupMap()
        loadGeofenceSettings()
        checkGooglePlayServices()
        // Try to populate UI quickly from last known background location
        tryPopulateFromLastKnownLocation()
        
        // Initialize location change tracking system
        initializeLocationChangeSystem()
        
        // Refresh navigation state after setup
        refreshNavigationState()

        isCardExpanded = savedInstanceState?.getBoolean(CARD_EXPANDED_KEY, false) ?: false
        updateCardExpansionState(animate = false)

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        // Only redirect to login if there's no active session at all (neither guest nor authenticated)
        if (sessionManager.needsAuthentication()) {
            Log.d(TAG, "No active session found, redirecting to login")
            navigateToLogin()
        } else {
            Log.d(TAG, "Active session found")
        }

        handleAlarmStoppedIntent()
        handleOpenedFromGeofenceIntent(intent)
        handleNavigationStoppedFromNotificationIntent()
        checkNavigationState()
        handlePlacePinIntent()
        handleMultiPointRouteIntent()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpenedFromGeofenceIntent(intent)
    }

    private fun handleOpenedFromGeofenceIntent(intent: Intent) {
        try {
            val reason = intent.getStringExtra("open_reason")
            val event = intent.getStringExtra("geofence_event")
            if (reason == "geofence") {
                Log.d(TAG, "Opened from geofence event: $event")

                // Ensure background location service is active for realtime updates
                ensureBackgroundLocationServiceRunning()

                // Refresh UI related to geofence status
                runOnUiThread {
                    try {
                        updateGeofenceDistanceInfo()
                        updateGeofenceCircle()
                        updateNavigationUI()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error refreshing UI after geofence open", e)
                    }
                }

                // Keep silent UI refresh (no automatic toast)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle opened-from-geofence intent", e)
        }
    }

    

    private fun initializeServices() {
        authService = AuthService()
        sessionManager = SessionManager(this)
        notificationService = NotificationService(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationHelper = LocationHelper(this)
        geofenceHelper = GeofenceHelper(this)
        navigationHelper = NavigationHelper(this)
        navigationHistoryService = NavigationHistoryService(this)
        realTimeLocationReceiver = RealTimeLocationReceiver()
        realTimeLocationReceiver.setLocationUpdateListener(this)
        geofenceEventReceiver = GeofenceEventReceiver()
        geofenceEventReceiver.setGeofenceEventListener(object : GeofenceEventReceiver.Companion.GeofenceEventListener {
            override fun onGeofenceEnter() {
                runOnUiThread {
                    Log.d(TAG, "In-app geofence ENTER received")
                    // Ensure UI reflects arrival and navigation state if needed
                    updateDistanceDisplay()
                    updateGeofenceCircle()
                }
            }

            override fun onGeofenceExit() {
                runOnUiThread {
                    Log.d(TAG, "In-app geofence EXIT received")
                    updateDistanceDisplay()
                    updateGeofenceCircle()
                }
            }

            override fun onGeofenceDwell() {
                runOnUiThread {
                    Log.d(TAG, "In-app geofence DWELL received")
                    updateDistanceDisplay()
                    updateGeofenceCircle()
                }
            }
            override fun onRealTimeGeofenceStatusUpdate(isInside: Boolean, distance: Float) {
                runOnUiThread {
                    try {
                        Log.d(TAG, "Real-time geofence status - Inside: $isInside, Distance: ${distance}m")
                        // Update concise UI elements if available
                        try { updateGeofenceDistanceInfo() } catch (_: Exception) {}
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating real-time geofence status UI", e)
                    }
                }
            }
        })
        
        // Register real-time location broadcast receiver
        val locationIntentFilter = android.content.IntentFilter().apply {
            addAction(RealTimeLocationReceiver.ACTION_LOCATION_UPDATE)
            addAction(RealTimeLocationReceiver.ACTION_DISTANCE_UPDATE)
            addAction(RealTimeLocationReceiver.ACTION_ROUTE_UPDATE)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // For Android 13+ you must specify whether the receiver is exported
            registerReceiver(
                realTimeLocationReceiver,
                locationIntentFilter,
                android.content.Context.RECEIVER_NOT_EXPORTED
            )
            // Register geofence event receiver
            val geofenceIntentFilter = android.content.IntentFilter().apply {
                addAction(com.example.gzingapp.receivers.GeofenceEventReceiver.ACTION_GEOFENCE_ENTER)
                addAction(com.example.gzingapp.receivers.GeofenceEventReceiver.ACTION_GEOFENCE_EXIT)
                addAction(com.example.gzingapp.receivers.GeofenceEventReceiver.ACTION_GEOFENCE_DWELL)
            }
            registerReceiver(
                geofenceEventReceiver,
                geofenceIntentFilter,
                android.content.Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(realTimeLocationReceiver, locationIntentFilter)
            val geofenceIntentFilter = android.content.IntentFilter().apply {
                addAction(com.example.gzingapp.receivers.GeofenceEventReceiver.ACTION_GEOFENCE_ENTER)
                addAction(com.example.gzingapp.receivers.GeofenceEventReceiver.ACTION_GEOFENCE_EXIT)
                addAction(com.example.gzingapp.receivers.GeofenceEventReceiver.ACTION_GEOFENCE_DWELL)
            }
            registerReceiver(geofenceEventReceiver, geofenceIntentFilter)
        }

        // Proactively ensure background service runs for real-time updates when activity starts
        ensureBackgroundLocationServiceRunning()
        
        // Check for pending service starts on app launch
        BackgroundLocationService.startPendingServiceIfNeeded(this)
        
        // No more notification spam - notifications are disabled
        
        // ENHANCED: Start location service health monitoring
        startLocationServiceHealthMonitoring()
    }
    
    /**
     * Clear any pending service start notifications
     * DISABLED: No more notification spam
     */
    private fun clearPendingNotifications() {
        try {
            // DISABLED: No more notification spam
            Log.d(TAG, "📱 Notification clearing DISABLED (no more spam)")
        } catch (e: Exception) {
            Log.e(TAG, "Error in disabled notification clearing", e)
        }
    }
    
    /**
     * Safe method to manually start location service from UI
     */
    fun startLocationServiceSafely() {
        try {
            Log.d(TAG, "🚀 Starting location service from main activity")
            BackgroundLocationService.startService(this)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting location service", e)
            Toast.makeText(this, "Failed to start location tracking: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * ENHANCED: Force refresh location and restart service if needed
     */
    fun forceRefreshLocation() {
        try {
            Log.d(TAG, "🔄 Force refreshing location...")
            
            // Check if service is running
            val isRunning = BackgroundLocationService.isServiceRunning(this)
            if (!isRunning) {
                Log.d(TAG, "⚠️ Location service not running - starting it...")
                BackgroundLocationService.startService(this)
                return
            }
            
            // Check if we have recent location data
            val lastLocation = BackgroundLocationService.getLastLocation(this)
            if (lastLocation == null) {
                Log.w(TAG, "⚠️ No location data available - restarting service...")
                // ENHANCED: Don't stop service immediately, just restart it
                android.os.Handler(Looper.getMainLooper()).postDelayed({
                    BackgroundLocationService.startService(this)
                }, 1000)
                return
            }
            
            val locationAge = System.currentTimeMillis() - lastLocation.time
            if (locationAge > 30000L) {
                Log.w(TAG, "⚠️ Location data is stale (${locationAge}ms old) - restarting service...")
                // ENHANCED: Don't stop service immediately, just restart it
                android.os.Handler(Looper.getMainLooper()).postDelayed({
                    BackgroundLocationService.startService(this)
                }, 1000)
                return
            }
            
            // Update UI with current location
            val latLng = LatLng(lastLocation.latitude, lastLocation.longitude)
            currentLocation = latLng
            updateCurrentLocationInfo(latLng)
            // User location is handled by Google Maps blue dot
            
            Log.d(TAG, "✅ Location refreshed successfully")
            Toast.makeText(this, "Location refreshed", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error force refreshing location", e)
            Toast.makeText(this, "Error refreshing location: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * ENHANCED: Start monitoring location service health
     */
    private fun startLocationServiceHealthMonitoring() {
        try {
            locationServiceHealthHandler = Handler(Looper.getMainLooper())
            locationServiceHealthRunnable = Runnable {
                try {
                    // Check location service health every 30 seconds
                    val isRunning = BackgroundLocationService.isServiceRunning(this)
                    val lastLocation = BackgroundLocationService.getLastLocation(this)
                    
                    if (!isRunning) {
                        Log.w(TAG, "⚠️ Location service health check: Service not running - restarting...")
                        BackgroundLocationService.startService(this)
                    } else if (lastLocation != null) {
                        val locationAge = System.currentTimeMillis() - lastLocation.time
                        if (locationAge > 60000L) { // 1 minute
                            Log.w(TAG, "⚠️ Location service health check: Location stale (${locationAge}ms old) - restarting...")
                            // ENHANCED: Don't stop service immediately, just restart it
                            android.os.Handler(Looper.getMainLooper()).postDelayed({
                                BackgroundLocationService.startService(this)
                            }, 1000)
                        } else {
                            Log.d(TAG, "✅ Location service health check: Service healthy, location age: ${locationAge}ms")
                        }
                    } else {
                        Log.w(TAG, "⚠️ Location service health check: Service running but no location data - restarting...")
                        // ENHANCED: Don't stop service immediately, just restart it
                        android.os.Handler(Looper.getMainLooper()).postDelayed({
                            BackgroundLocationService.startService(this)
                        }, 1000)
                    }
                    
                    // Schedule next health check
                    locationServiceHealthHandler?.postDelayed(locationServiceHealthRunnable!!, 30000L)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error in location service health check", e)
                    // Continue monitoring even if there's an error
                    locationServiceHealthHandler?.postDelayed(locationServiceHealthRunnable!!, 30000L)
                }
            }
            
            // Start the first health check after 10 seconds
            locationServiceHealthHandler?.postDelayed(locationServiceHealthRunnable!!, 10000L)
            Log.d(TAG, "✅ Location service health monitoring started")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start location service health monitoring", e)
        }
    }
    
    /**
     * ENHANCED: Stop location service health monitoring
     */
    private fun stopLocationServiceHealthMonitoring() {
        try {
            locationServiceHealthRunnable?.let { runnable ->
                locationServiceHealthHandler?.removeCallbacks(runnable)
            }
            locationServiceHealthHandler = null
            locationServiceHealthRunnable = null
            Log.d(TAG, "✅ Location service health monitoring stopped")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping location service health monitoring", e)
        }
    }
    
    /**
     * ENHANCED: Start direct location handling
     */
    private fun startDirectLocationHandling() {
        try {
            Log.d("$TAG $SERVICE_TAG", "🚀 Starting direct location handling...")
            Log.d("$TAG $DEBUG_TAG", "📊 Current state: isLocationLoading=$isLocationLoading, lastLocation=${lastLocation != null}")
            
            // Show initial state
            updateLocationUI("📍 Waiting for GPS signal...", "Getting location...", "Please wait...")
            Log.d("$TAG $UI_TAG", "🎨 Initial UI state set: Waiting for GPS signal")
            
            // Start location refresh cycle
            startLocationRefreshCycle()
            
            Log.d("$TAG $SERVICE_TAG", "✅ Direct location handling started successfully")
        } catch (e: Exception) {
            Log.e("$TAG $SERVICE_TAG", "❌ Error starting direct location handling", e)
        }
    }
    
    /**
     * ENHANCED: Start location refresh cycle
     */
    private fun startLocationRefreshCycle() {
        try {
            Log.d("$TAG $SERVICE_TAG", "🔄 Starting 2-second location refresh cycle...")
            Log.d("$TAG $DEBUG_TAG", "📊 Handler: ${locationRefreshHandler != null}, Runnable: ${locationRefreshHandler != null}")
            
            // Initialize refresh handler if needed
            if (locationRefreshHandler == null) {
                locationRefreshHandler = Handler(Looper.getMainLooper())
                Log.d("$TAG $DEBUG_TAG", "🔧 Created new location refresh handler")
            }
            
            // Create refresh runnable
            locationRefreshRunnable = object : Runnable {
                override fun run() {
                    try {
                        locationRefreshCount++
                        val currentTime = System.currentTimeMillis()
                        
                        Log.d("$TAG $LOCATION_TAG", "🔄 Location refresh #$locationRefreshCount at ${android.text.format.DateFormat.format("HH:mm:ss", currentTime)}")
                        Log.d("$TAG $DEBUG_TAG", "📊 Refresh stats: count=$locationRefreshCount, handler=${locationRefreshHandler != null}")
                        
                        // Update loading text with refresh count
                        val refreshText = "🔄 Refreshing location... (${locationRefreshCount})"
                        updateLocationUI(refreshText, "GPS signal searching...", "Please wait while we locate you...")
                        Log.d("$TAG $UI_TAG", "🎨 UI updated with refresh count: $refreshText")
                        
                        // Try to get fresh location from service
                        Log.d("$TAG $SERVICE_TAG", "🔍 Requesting location from BackgroundLocationService...")
                        val freshLocation = BackgroundLocationService.getLastLocation(this@DashboardActivity)
                        
                        if (freshLocation != null) {
                            Log.d("$TAG $LOCATION_TAG", "✅ Got fresh location on refresh #$locationRefreshCount")
                            Log.d("$TAG $DEBUG_TAG", "📊 Location details: lat=${String.format("%.6f", freshLocation.latitude)}, lng=${String.format("%.6f", freshLocation.longitude)}, accuracy=${String.format("%.1f", freshLocation.accuracy)}m, time=${android.text.format.DateFormat.format("HH:mm:ss", freshLocation.time)}")
                            
                            stopLocationRefreshCycle()
                            Log.d("$TAG $SERVICE_TAG", "🔄 Calling onLocationUpdate with fresh location...")
                            onLocationUpdate(freshLocation.latitude, freshLocation.longitude, freshLocation.accuracy, freshLocation.time)
                        } else {
                            Log.w("$TAG $LOCATION_TAG", "⚠️ No location on refresh #$locationRefreshCount - continuing cycle")
                            Log.d("$TAG $DEBUG_TAG", "📊 BackgroundLocationService returned null location")
                            
                            // ENHANCED: Try to force a location update from the service
                            Log.d("$TAG $SERVICE_TAG", "🔄 Attempting to force location update from service...")
                            try {
                                val intent = Intent("com.example.gzingapp.FORCE_LOCATION_UPDATE")
                                sendBroadcast(intent)
                                Log.d("$TAG $DEBUG_TAG", "📡 Sent force location update broadcast")
                            } catch (e: Exception) {
                                Log.e("$TAG $SERVICE_TAG", "❌ Error sending force location broadcast", e)
                            }
                            
                            // Check if we should stop refreshing (too many attempts)
                            if (locationRefreshCount >= 30) { // 1 minute of refreshing (30 * 2 seconds)
                                Log.w("$TAG $SERVICE_TAG", "⚠️ Too many refresh attempts ($locationRefreshCount) - stopping cycle")
                                stopLocationRefreshCycle()
                                showLocationError()
                            } else {
                                // Continue refresh cycle
                                Log.d("$TAG $DEBUG_TAG", "⏰ Scheduling next refresh in ${LOCATION_REFRESH_INTERVAL_MS}ms")
                                locationRefreshHandler?.postDelayed(this, LOCATION_REFRESH_INTERVAL_MS)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("$TAG $LOCATION_TAG", "❌ Error in location refresh cycle #$locationRefreshCount", e)
                        Log.d("$TAG $DEBUG_TAG", "⏰ Scheduling retry in ${LOCATION_REFRESH_INTERVAL_MS}ms despite error")
                        locationRefreshHandler?.postDelayed(this, LOCATION_REFRESH_INTERVAL_MS)
                    }
                }
            }
            
            // Start the first refresh
            Log.d("$TAG $DEBUG_TAG", "🚀 Posting first location refresh...")
            locationRefreshHandler?.post(locationRefreshRunnable!!)
            
        } catch (e: Exception) {
            Log.e("$TAG $SERVICE_TAG", "❌ Error starting location refresh cycle", e)
        }
    }
    
    /**
     * ENHANCED: Stop location refresh cycle
     */
    private fun stopLocationRefreshCycle() {
        try {
            Log.d("$TAG $SERVICE_TAG", "🛑 Stopping location refresh cycle...")
            Log.d("$TAG $DEBUG_TAG", "📊 Before stop: handler=${locationRefreshHandler != null}, runnable=${locationRefreshRunnable != null}, count=$locationRefreshCount")
            
            locationRefreshRunnable?.let { runnable ->
                locationRefreshHandler?.removeCallbacks(runnable)
                Log.d("$TAG $DEBUG_TAG", "🔧 Removed callbacks from handler")
            }
            locationRefreshRunnable = null
            Log.d("$TAG $SERVICE_TAG", "✅ Location refresh cycle stopped successfully")
            Log.d("$TAG $DEBUG_TAG", "📊 After stop: handler=${locationRefreshHandler != null}, runnable=${locationRefreshRunnable != null}")
        } catch (e: Exception) {
            Log.e("$TAG $SERVICE_TAG", "❌ Error stopping location refresh cycle", e)
        }
    }
    
    /**
     * ENHANCED: Show location error state
     */
    private fun showLocationError() {
        try {
            val errorText = "❌ Location unavailable"
            updateLocationUI(errorText, "GPS signal not found", "Please check your location settings")
            
            Log.w(TAG, "❌ Location error state displayed")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing location error", e)
        }
    }
    
    /**
     * ENHANCED: Update location UI directly
     */
    private fun updateLocationUI(coordinatesText: String, summaryText: String, addressText: String) {
        try {
            Log.d("$TAG $UI_TAG", "🎨 Updating location UI...")
            Log.d("$TAG $DEBUG_TAG", "📊 UI update: coordinates='$coordinatesText', summary='$summaryText', address='$addressText'")
            Log.d("$TAG $DEBUG_TAG", "📊 TextView references: coordinates=${tvCurrentLocationCoordinates != null}, collapsed=${tvCurrentLocationCoordinatesCollapsed != null}, summary=${tvCurrentLocationSummary != null}, address=${tvCurrentLocationAddress != null}")
            
            runOnUiThread {
                try {
                    tvCurrentLocationCoordinates?.text = coordinatesText
                    tvCurrentLocationCoordinatesCollapsed?.text = coordinatesText
                    tvCurrentLocationSummary?.text = summaryText
                    tvCurrentLocationAddress?.text = addressText
                    
                    Log.d("$TAG $UI_TAG", "✅ Location UI updated successfully on main thread")
                    Log.d("$TAG $DEBUG_TAG", "📊 UI state after update: coordinates='${tvCurrentLocationCoordinates?.text}', collapsed='${tvCurrentLocationCoordinatesCollapsed?.text}', summary='${tvCurrentLocationSummary?.text}', address='${tvCurrentLocationAddress?.text}'")
                } catch (e: Exception) {
                    Log.e("$TAG $UI_TAG", "❌ Error updating UI elements on main thread", e)
                }
            }
        } catch (e: Exception) {
            Log.e("$TAG $UI_TAG", "❌ Error in updateLocationUI", e)
            Log.d("$TAG $DEBUG_TAG", "📊 Error context: coordinates='$coordinatesText', summary='$summaryText', address='$addressText'")
        }
    }
    
    /**
     * ENHANCED: Get address for location using LocationHelper
     */
    private fun getAddressForLocation(location: Location) {
        // Create LatLng in outer scope for error logging
        val latLng = LatLng(location.latitude, location.longitude)
        
        try {
            Log.d("$TAG $SERVICE_TAG", "🏠 Starting address resolution for location...")
            Log.d("$TAG $DEBUG_TAG", "📊 Location: lat=${String.format("%.6f", location.latitude)}, lng=${String.format("%.6f", location.longitude)}, accuracy=${String.format("%.1f", location.accuracy)}m")
            
            lifecycleScope.launch {
                try {
                    Log.d("$TAG $DEBUG_TAG", "🔄 Address resolution coroutine started")
                    Log.d("$TAG $DEBUG_TAG", "🔧 Created LatLng: $latLng")
                    
                    Log.d("$TAG $SERVICE_TAG", "🔍 Calling locationHelper.getAddressFromLocation...")
                    val address = locationHelper.getAddressFromLocation(latLng)
                    Log.d("$TAG $DEBUG_TAG", "📊 Address result: '$address' (length: ${address.length})")
                    
                    if (address.isNotEmpty()) {
                        Log.d("$TAG $SERVICE_TAG", "✅ Address resolved successfully: $address")
                        
                        // Update UI with actual address
                        val accuracyText = if (location.accuracy > 0f) {
                            " (±${location.accuracy.toInt()}m)"
                        } else {
                            " (accuracy unknown)"
                        }
                        
                        val velocityText = if (location.hasSpeed() && location.speed > 0.1f) {
                            val speedKmh = location.speed * 3.6f
                            " • ${String.format("%.1f", speedKmh)}km/h"
                        } else ""
                        
                        val fullAddressText = "📍 $address$accuracyText$velocityText"
                        Log.d("$TAG $UI_TAG", "🎨 Updating UI with full address: $fullAddressText")
                        updateLocationUI(fullAddressText, address, address)
                        
                        Log.d("$TAG $LOCATION_TAG", "✅ Address update completed: $address")
                    } else {
                        Log.w("$TAG $SERVICE_TAG", "⚠️ No address found for location - empty result")
                        Log.d("$TAG $DEBUG_TAG", "📊 Empty address result for coordinates: $latLng")
                    }
                } catch (e: Exception) {
                    Log.e("$TAG $SERVICE_TAG", "❌ Error getting address for location", e)
                    Log.d("$TAG $DEBUG_TAG", "📊 Error context: latLng=$latLng, location=$location")
                }
            }
            Log.d("$TAG $DEBUG_TAG", "🚀 Address resolution coroutine launched")
        } catch (e: Exception) {
            Log.e("$TAG $SERVICE_TAG", "❌ Error in getAddressForLocation", e)
            Log.d("$TAG $DEBUG_TAG", "📊 Error context: location=$location, latLng=$latLng")
        }
    }

    private fun checkNavigationState() {
        // First check if we have navigation history data from intent
        if (intent.getBooleanExtra("from_history", false)) {
            handleNavigationFromHistory()
            return
        }
        
        // Otherwise check for active navigation
        if (navigationHelper.isNavigationActive()) {
            Log.d(TAG, "Navigation was active, restoring state")

            val destination = navigationHelper.getCurrentDestination()
            if (destination != null) {
                pinnedLocation = destination

                updateNavigationUI()
                updateDrawerState()
                updateToolbarState()
                addNavigationStateVisualFeedback()

                lifecycleScope.launch {
                    delay(1000)
                    restoreNavigationVisualization(destination)
                }

                Toast.makeText(this, "Navigation resumed", Toast.LENGTH_SHORT).show()
            } else {
                Log.w(TAG, "Navigation active but no destination found, stopping navigation")
                // Stop alarms and navigation properly
                stopAllAlarmsAndNotifications()
                navigationHelper.stopNavigation()
            }
        }
    }

    /**
     * Handle navigation from history - displays a route from navigation history
     */
    private fun handleNavigationFromHistory() {
        try {
            val historyId = intent.getStringExtra("history_id")
            val destLat = intent.getDoubleExtra("destination_lat", 0.0)
            val destLng = intent.getDoubleExtra("destination_lng", 0.0)
            val destName = intent.getStringExtra("destination_name") ?: "History Location"
            
            Log.d(TAG, "Handling navigation from history: $historyId, destination: $destName")
            
            if (destLat != 0.0 && destLng != 0.0) {
                val location = LatLng(destLat, destLng)
                
                // Pin the location on map
                if (::mMap.isInitialized) {
                    pinLocationFromHistory(location, destName)
                } else {
                    // Store for later when map is ready
                    pendingPinLocation = PendingPinData(location, destName, "", false)
                }
                
                Toast.makeText(this, "Showing route to $destName", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Could not load route details", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling navigation from history", e)
            Toast.makeText(this, "Error loading route details", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Pin a location from History with visualization
     */
    private fun pinLocationFromHistory(location: LatLng, name: String) {
        try {
            Log.d(TAG, "Pinning location from history: $name at $location")

            // Remove existing markers and polylines
            pinnedLocationMarker?.remove()
            currentPolyline?.remove()
            geofenceCircle?.remove()

            // Add marker with place name
            val title = "$name (from history)"
            pinnedLocationMarker = mMap.addMarker(
                MarkerOptions()
                    .position(location)
                    .title(title)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)) // Red pin for destination
            )

            // Set pinned location
            pinnedLocation = location

            // Move camera to the location
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 16f))

            // Update pinned location info in the UI
            updatePinnedLocationInfo(location)

            // Draw route if current location is available
            currentLocation?.let { current ->
                drawRealRoute(current, location, selectedTransportMode)
            }

            Log.d(TAG, "Successfully pinned location from history: $name")

        } catch (e: Exception) {
            Log.e(TAG, "Error pinning location from history", e)
            Toast.makeText(this, "Error showing location: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(CARD_EXPANDED_KEY, isCardExpanded)
    }

    private fun setupExpandableCard() {
        val locationCard = findViewById<View>(R.id.locationCardInclude)

        collapsedHeader = locationCard.findViewById(R.id.collapsedHeader)
        expandableContent = locationCard.findViewById(R.id.expandableContent)
        expandCollapseIcon = locationCard.findViewById(R.id.expandCollapseIcon)

        btnQuickNavigation = locationCard.findViewById(R.id.btnQuickNavigation)
        tvCurrentLocationSummary = locationCard.findViewById(R.id.tvCurrentLocationSummary)
        navigationStatusCollapsed = locationCard.findViewById(R.id.navigationStatusCollapsed)
        tvNavigationStatusCollapsed = locationCard.findViewById(R.id.tvNavigationStatusCollapsed)
        navigationPulseCollapsed = locationCard.findViewById(R.id.navigationPulseCollapsed)

        collapsedHeader.setOnClickListener {
            toggleCardExpansion()
        }

        btnQuickNavigation.setOnClickListener {
            handleQuickNavigation()
        }

        tvCurrentLocationSummary.text = getString(R.string.calculating)
        updateQuickNavigationButton()
    }

    private fun toggleCardExpansion() {
        isCardExpanded = !isCardExpanded
        updateCardExpansionState(animate = true)
    }

    private fun updateCardExpansionState(animate: Boolean = true) {
        if (animate) {
            animateCardExpansion(isCardExpanded)
        } else {
            expandableContent.visibility = if (isCardExpanded) View.VISIBLE else View.GONE
            expandCollapseIcon.rotation = if (isCardExpanded) 180f else 0f
        }
    }

    private fun animateCardExpansion(expand: Boolean) {
        val targetRotation = if (expand) 180f else 0f
        val iconAnimator = ObjectAnimator.ofFloat(expandCollapseIcon, "rotation", targetRotation)
        iconAnimator.duration = 300
        iconAnimator.interpolator = AccelerateDecelerateInterpolator()

        if (expand) {
            expandableContent.visibility = View.VISIBLE
            expandableContent.alpha = 0f

            val alphaAnimator = ObjectAnimator.ofFloat(expandableContent, "alpha", 0f, 1f)
            alphaAnimator.duration = 200
            alphaAnimator.startDelay = 100

            val animatorSet = AnimatorSet()
            animatorSet.playTogether(iconAnimator, alphaAnimator)
            animatorSet.start()
        } else {
            val alphaAnimator = ObjectAnimator.ofFloat(expandableContent, "alpha", 1f, 0f)
            alphaAnimator.duration = 200

            alphaAnimator.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    expandableContent.visibility = View.GONE
                }
            })

            val animatorSet = AnimatorSet()
            animatorSet.playTogether(iconAnimator, alphaAnimator)
            animatorSet.start()
        }
    }

    private fun handleQuickNavigation() {
        if (pinnedLocation == null) {
            Toast.makeText(this, "Please pin a location first", Toast.LENGTH_SHORT).show()
            if (!isCardExpanded) {
                isCardExpanded = true
                updateCardExpansionState(animate = true)
            }
            return
        }

        if (isNavigating) {
            stopNavigation()
        } else {
            startNavigation()
        }
    }

    private fun updateQuickNavigationButton() {
        if (isNavigating) {
            btnQuickNavigation.text = getString(R.string.stop_navigation)
            btnQuickNavigation.setBackgroundColor(ContextCompat.getColor(this, R.color.navigation_error))
        } else {
            btnQuickNavigation.text = getString(R.string.start_navigation)
            btnQuickNavigation.setBackgroundColor(ContextCompat.getColor(this, R.color.primary_brown))
        }

        btnQuickNavigation.isEnabled = pinnedLocation != null || isNavigating
        btnQuickNavigation.alpha = if (btnQuickNavigation.isEnabled) 1.0f else 0.6f
    }

    private fun updateCollapsedNavigationStatus() {
        if (isNavigating) {
            navigationStatusCollapsed.visibility = View.VISIBLE
            tvNavigationStatusCollapsed.text = getString(R.string.navigation_active)

            navigationStatusCollapsedAnimation?.cancel()
            navigationStatusCollapsedAnimation = ObjectAnimator.ofFloat(navigationPulseCollapsed, "alpha", 1f, 0.3f, 1f)
            navigationStatusCollapsedAnimation?.apply {
                duration = 1500
                repeatCount = ObjectAnimator.INFINITE
                start()
            }
        } else {
            navigationStatusCollapsed.visibility = View.GONE
            navigationStatusCollapsedAnimation?.cancel()
        }
    }

    private fun restoreNavigationVisualization(destination: LatLng) {
        if (::mMap.isInitialized) {
            pinnedLocationMarker?.remove()
            pinnedLocationMarker = mMap.addMarker(
                MarkerOptions()
                    .position(destination)
                    .title("📍 Navigation Destination")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)) // Red pin for destination
            )

            val radius = GeofenceHelper.getGeofenceRadius()
            addGeofenceVisualization(destination, radius)
            updatePinnedLocationInfo(destination)

            currentLocation?.let { current ->
                drawRealRoute(current, destination, selectedTransportMode)
            }
        }
    }

    private fun checkGooglePlayServices() {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(this)

        when (resultCode) {
            ConnectionResult.SUCCESS -> {
                Log.d(TAG, "Google Play Services is available and up to date")
            }
            ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> {
                Log.w(TAG, "Google Play Services needs to be updated")
                if (googleApiAvailability.isUserResolvableError(resultCode)) {
                    googleApiAvailability.getErrorDialog(this, resultCode, 1001)?.show()
                }
            }
            ConnectionResult.SERVICE_MISSING -> {
                Log.e(TAG, "Google Play Services is missing")
                Toast.makeText(
                    this,
                    "Google Play Services is required for location features",
                    Toast.LENGTH_LONG
                ).show()
            }
            else -> {
                Log.e(TAG, "Google Play Services error: $resultCode")
                if (googleApiAvailability.isUserResolvableError(resultCode)) {
                    googleApiAvailability.getErrorDialog(this, resultCode, 1001)?.show()
                }
            }
        }
    }

    private fun setupUI() {
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        toolbar = findViewById(R.id.toolbar)
        fabSOS = findViewById(R.id.fabSOS)

        setupBottomNavigation()
        setupSOSFAB()
        setSupportActionBar(toolbar)
    }

    private fun setupBottomNavigation() {
        bottomNavigation = findViewById(R.id.bottomNavigation)
        bottomNavigation.selectedItemId = R.id.nav_dashboard

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    Log.d(TAG, "Dashboard selected")
                    true
                }
                R.id.nav_routes -> {
                    if (isNavigating) {
                        Toast.makeText(this, getString(R.string.drawer_locked_message), Toast.LENGTH_SHORT).show()
                        false
                    } else {
                        Log.d(TAG, "Navigating to Routes")
                        val intent = Intent(this, RoutesActivity::class.java)
                        startActivity(intent)
                        overridePendingTransition(0, 0)
                        finish()
                        true
                    }
                }
                R.id.nav_places -> {
                    if (isNavigating) {
                        Toast.makeText(this, getString(R.string.drawer_locked_message), Toast.LENGTH_SHORT).show()
                        false
                    } else {
                        Log.d(TAG, "Navigating to Places")
                        val intent = Intent(this, PlacesActivity::class.java)
                        startActivity(intent)
                        overridePendingTransition(0, 0)
                        finish()
                        true
                    }
                }
                else -> false
            }
        }
    }

    private fun setupSOSFAB() {
        var sosHandler: Handler? = null
        var isLongPressing = false
        var pressStartTime = 0L
        
        fabSOS.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isLongPressing = false
                    pressStartTime = System.currentTimeMillis()
                    
                    // Start the 5-second countdown
                    sosHandler = Handler(Looper.getMainLooper())
                    sosHandler?.postDelayed({
                        if (System.currentTimeMillis() - pressStartTime >= 5000) {
                            isLongPressing = true
                            handleSOSActivation()
                        }
                    }, 5000)
                    
                    // Show visual feedback
                    fabSOS.animate()
                        .scaleX(1.1f)
                        .scaleY(1.1f)
                        .setDuration(200)
                        .start()
                    
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val pressDuration = System.currentTimeMillis() - pressStartTime
                    
                    // Cancel the SOS countdown if released before 5 seconds
                    sosHandler?.removeCallbacksAndMessages(null)
                    
                    // Reset visual feedback
                    fabSOS.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(200)
                        .start()
                    
                    if (!isLongPressing && pressDuration < 5000) {
                        // Show instruction if short press
                        Toast.makeText(
                            this,
                            "Hold for 5 seconds to activate SOS emergency mode",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    
                    isLongPressing = false
                    true
                }
                else -> false
            }
        }
    }

    private fun handleSOSActivation() {
        // Visual and audio feedback for SOS activation
        fabSOS.animate()
            .scaleX(1.2f)
            .scaleY(1.2f)
            .setDuration(300)
            .withEndAction {
                fabSOS.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(300)
                    .start()
            }
            .start()
        
        // Trigger enhanced SOS vibration
        notificationService.triggerSOSVibration()
        
        // Show SOS activation dialog (mock UI)
        showSOSActivationDialog()
    }
    
    private fun showSOSActivationDialog() {
        AlertDialog.Builder(this)
            .setTitle("🚨 SOS ACTIVATED")
            .setMessage("Emergency mode activated!\n\n" +
                    "Mock SMS would be sent to emergency contacts:\n" +
                    "• Contact 1: +1234567890\n" +
                    "• Contact 2: +0987654321\n\n" +
                    "Message: \"EMERGENCY! I need help. My location: [Current GPS Location]\"")
            .setPositiveButton("Send Emergency SMS") { _, _ ->
                sendMockEmergencySMS()
            }
            .setNegativeButton("Cancel SOS") { _, _ ->
                Toast.makeText(this, "SOS cancelled", Toast.LENGTH_SHORT).show()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun sendMockEmergencySMS() {
        // Mock SMS sending functionality
        Toast.makeText(this, "🚨 Emergency SMS sent to contacts (MOCK)", Toast.LENGTH_LONG).show()
        
        // Show emergency notification
        notificationService.showNotification(
            "🚨 Emergency SOS Activated",
            "Emergency SMS sent to your contacts. Help is on the way.",
            9999,
            true // Include stop action
        )
        
        Log.d(TAG, "SOS Emergency activated - Mock SMS sent to emergency contacts")
    }

    private fun setupNavigationDrawer() {
        toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navigationView.setNavigationItemSelectedListener(this)
        
        // Setup navigation menu
    }
    

    
    /**
     * Refresh navigation state to sync UI with current session
     * Call this when session state might have changed
     */
    private fun refreshNavigationState() {
        Log.d(TAG, "Refreshing navigation state")
        
        // Update navigation header
        updateNavigationHeader()
        
        // Log current session state for debugging
        val session = sessionManager.getSession()
        Log.d(TAG, "Current session: isLoggedIn=${session["isLoggedIn"]}, userId=${session["userId"]}")
    }

    private fun setupModal() {
        modalOverlay = findViewById(R.id.modal_overlay)
        modalCard = findViewById(R.id.modal_card)
        btnCloseModal = findViewById(R.id.btn_close_modal)
        btnCancel = findViewById(R.id.btn_cancel)
        btnStartNavigation = findViewById(R.id.btn_start_navigation)
        tvNavigationText = findViewById(R.id.tv_navigation_text)
        ivNavigationIcon = findViewById(R.id.iv_navigation_icon)

        tvModalLocationAddress = findViewById(R.id.tv_location_address)
        tvModalLocationDistance = findViewById(R.id.tv_location_distance)
        tvModalTrafficCondition = findViewById(R.id.tv_traffic_condition)

        transportWalking = findViewById(R.id.transport_walking)
        transportMotorcycle = findViewById(R.id.transport_motorcycle)
        transportCar = findViewById(R.id.transport_car)
        tvWalkingTime = findViewById(R.id.tv_walking_time)
        tvMotorcycleTime = findViewById(R.id.tv_motorcycle_time)
        tvCarTime = findViewById(R.id.tv_car_time)

        setupModalClickListeners()
        updateTransportSelection(TransportMode.WALKING)
    }

    private fun setupModalClickListeners() {
        modalOverlay.setOnClickListener { hideModal() }
        btnCloseModal.setOnClickListener { hideModal() }
        btnCancel.setOnClickListener { hideModal() }

        transportWalking.setOnClickListener {
            updateTransportSelection(TransportMode.WALKING)
            updateRouteForSelectedTransport()
        }
        transportMotorcycle.setOnClickListener {
            updateTransportSelection(TransportMode.MOTORCYCLE)
            updateRouteForSelectedTransport()
        }
        transportCar.setOnClickListener {
            updateTransportSelection(TransportMode.CAR)
            updateRouteForSelectedTransport()
        }

        btnStartNavigation.setOnClickListener {
            if (isNavigating) {
                stopNavigation()
            } else {
                startNavigation()
            }
        }
    }

    private fun showModal() {
        modalOverlay.visibility = View.VISIBLE

        val translateY = ObjectAnimator.ofFloat(modalCard, "translationY", 100f, 0f)
        val alpha = ObjectAnimator.ofFloat(modalCard, "alpha", 0f, 1f)

        AnimatorSet().apply {
            playTogether(translateY, alpha)
            duration = 300
            start()
        }
    }

    private fun hideModal() {
        val translateY = ObjectAnimator.ofFloat(modalCard, "translationY", 0f, 100f)
        val alpha = ObjectAnimator.ofFloat(modalCard, "alpha", 1f, 0f)

        AnimatorSet().apply {
            playTogether(translateY, alpha)
            duration = 300
            start()
        }.also { animator ->
            animator.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    modalOverlay.visibility = View.GONE
                    resetNavigationState()
                }
            })
        }
    }

    private fun resetNavigationState() {
        selectedTransportMode = TransportMode.WALKING
        updateTransportSelection(TransportMode.WALKING)
        updateNavigationButton()
        progressNavigation.visibility = View.GONE
        btnToggleNavigation.isEnabled = true
        pinnedLocationToNavigate = null
        
        // Only clear visualization if not navigating
        if (!isNavigating) {
            clearMapVisualization()
        }
    }
    
    private fun clearMapVisualization() {
        Log.d(TAG, "Clearing map visualization (markers, routes, geofence)")
        
        // Remove visual elements from map
        pinnedLocationMarker?.remove()
        pinnedLocationMarker = null
        
        currentPolyline?.remove()
        currentPolyline = null
        
        geofenceCircle?.remove()
        geofenceCircle = null
        
        // Remove actual geofence
        geofenceHelper.removeGeofence(
            onSuccess = { 
                Log.d(TAG, "Geofence removed successfully when closing modal")
            },
            onFailure = { error ->
                Log.w(TAG, "Failed to remove geofence when closing modal", error)
            }
        )
        
        // Reset pinned location
        pinnedLocation = null
        
        // Update UI to reflect no pinned location
        pinnedLocationSection.visibility = View.GONE
        pinnedLocationDivider.visibility = View.GONE
        tvNoPinMessage.visibility = View.VISIBLE
    }
    
    /**
     * Check if location is within Marikina/Antipolo service area
     */
    private fun isLocationWithinServiceArea(location: LatLng): Boolean {
        // Service area bounds for Marikina/Antipolo
        val minLat = 14.5500 // South boundary
        val maxLat = 14.6500 // North boundary  
        val minLng = 121.1000 // West boundary
        val maxLng = 121.2500 // East boundary
        
        return location.latitude in minLat..maxLat &&
                location.longitude in minLng..maxLng
    }
    
    /**
     * Find nearest valid location (Marikina or Antipolo center)
     */
    private fun getNearestValidLocation(location: LatLng): String {
        val antipoloCenter = LatLng(14.5995, 121.1817)
        val marikinaCenter = LatLng(14.6507, 121.1029)
        
        val distanceToAntipolo = locationHelper.calculateDistance(location, antipoloCenter)
        val distanceToMarikina = locationHelper.calculateDistance(location, marikinaCenter)
        
        return if (distanceToAntipolo < distanceToMarikina) "antipolo" else "marikina"
    }

    private fun startNavigation() {
        pinnedLocation?.let { location ->
            Log.d(TAG, "Starting navigation to: $location")

            progressNavigation.visibility = View.VISIBLE
            btnToggleNavigation.isEnabled = false
            tvNavigationText.text = "Starting Navigation..."
            tvNavigationText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))

            pinnedLocationToNavigate = location
            checkAndRequestBackgroundLocationPermission(location)

        } ?: run {
            Toast.makeText(this, "Please pin a location first", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndRequestBackgroundLocationPermission(latLng: LatLng) {
        Log.d(TAG, "Checking background location permission...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                Log.d(TAG, "Background location permission already granted")
                startNavigationInternal(latLng)
            } else {
                Log.d(TAG, "Requesting background location permission")
                backgroundLocationPermissionLauncher.launch(
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
            }
        } else {
            Log.d(TAG, "Background location permission not required (API < 29)")
            startNavigationInternal(latLng)
        }
    }

    private fun startNavigationInternal(destination: LatLng) {
        // Get destination address for notifications
        lifecycleScope.launch {
            val destinationAddress = try {
                locationHelper.getAddressFromLocation(destination)
            } catch (e: Exception) {
                "Selected location"
            }
            
                    // ENHANCED: Direct navigation state handling (no more adapter needed)
        Log.d(TAG, "🚀 Navigation started to destination: ${destination.latitude}, ${destination.longitude}")

            // Start navigation history tracking
            try {
                val destinations = listOf(
                    NavigationDestination(
                        name = destinationAddress,
                        address = destinationAddress,
                        latLng = destination,
                        order = 0
                    )
                )
                
                val userId = sessionManager.getCurrentUser()?.id
                
                currentNavigationHistory = navigationHistoryService.startNavigation(
                    routeDescription = "Navigation to $destinationAddress",
                    startLocation = currentLocation,
                    destinations = destinations,
                    totalDistance = calculateDistance(currentLocation, destination),
                    estimatedDuration = calculateEstimatedTime(currentLocation, destination),
                    userId = userId
                )
                
                Log.d(TAG, "Navigation history tracking started: ${currentNavigationHistory?.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start navigation history tracking", e)
            }

            // Show navigation started notification
            notificationService.showNavigationStartedNotification(destinationAddress)

            navigationHelper.startNavigation(
                destination = destination,
                onSuccess = {
                    Log.d(TAG, "Navigation started successfully!")

                    runOnUiThread {
                        hideModal()
                        progressNavigation.visibility = View.GONE
                        btnToggleNavigation.isEnabled = true

                        updateNavigationUI()
                        updateDrawerState()
                        updateToolbarState()
                        updateMapInteraction() // Disable pinning during navigation
                        addNavigationStateVisualFeedback()

                        addGeofenceVisualization(destination, GeofenceHelper.getGeofenceRadius())

                        // Show ongoing navigation notification with enhanced alarm system
                        notificationService.showNavigationOngoingNotification(destinationAddress)

                        Toast.makeText(
                            this@DashboardActivity,
                            "Navigation started! Enhanced alarms enabled for arrival.",
                            Toast.LENGTH_SHORT
                        ).show()

                        Log.d(TAG, "Navigation UI updated successfully")
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to start navigation", error)

                    // Mark navigation as failed in history
                    currentNavigationHistory?.let { history ->
                        lifecycleScope.launch {
                            try {
                                navigationHistoryService.failNavigation(history.id)
                                currentNavigationHistory = null
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to update navigation history on failure", e)
                            }
                        }
                    }

                    runOnUiThread {
                        progressNavigation.visibility = View.GONE
                        btnToggleNavigation.isEnabled = true
                        updateNavigationButton()

                        val errorMessage = when {
                            error.message?.contains("Google Play Services") == true -> {
                                "Google Play Services issue. Using fallback navigation."
                            }
                            error.message?.contains("Location permission") == true -> {
                                "Background location limited. Using basic navigation mode."
                            }
                            error.message?.contains("temporarily unavailable") == true -> {
                                "Location services unavailable. Using fallback navigation."
                            }
                            else -> {
                                "Geofence unavailable. Using alternative navigation."
                            }
                        }

                        Toast.makeText(this@DashboardActivity, errorMessage, Toast.LENGTH_LONG).show()
                        startNavigationWithoutGeofence(destination)
                    }
                }
            )
        }
    }

    private fun startNavigationWithoutGeofence(destination: LatLng) {
        Log.d(TAG, "Starting fallback navigation without geofence")

        hideModal()
        progressNavigation.visibility = View.GONE
        btnToggleNavigation.isEnabled = true

        updateNavigationUI()
        updateDrawerState()
        updateToolbarState()

        Toast.makeText(
            this,
            "Navigation started (limited location monitoring)",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun stopNavigation() {
        Log.d(TAG, "Stopping navigation...")

        progressNavigation.visibility = View.VISIBLE
        btnToggleNavigation.isEnabled = false

        navigationHelper.stopNavigation(
            onSuccess = {
                Log.d(TAG, "Navigation stopped successfully")

                // Update navigation history as cancelled
                currentNavigationHistory?.let { history ->
                    lifecycleScope.launch {
                        try {
                            val actualDuration = ((System.currentTimeMillis() - history.startTime) / 60000).toInt()
                            navigationHistoryService.cancelNavigation(history.id, actualDuration)
                            currentNavigationHistory = null
                            Log.d(TAG, "Navigation history marked as cancelled")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to update navigation history on cancellation", e)
                        }
                    }
                }

                runOnUiThread {
                    progressNavigation.visibility = View.GONE
                    btnToggleNavigation.isEnabled = true

                            // ENHANCED: Direct navigation state handling (no more adapter needed)
        Log.d(TAG, "🛑 Navigation stopped")

                    updateNavigationUI()
                    updateDrawerState()
                    updateToolbarState()
                    updateMapInteraction() // Re-enable pinning after navigation stops

                    navigationStatusAnimation?.cancel()
                    geofenceCircle?.remove()

                    // Stop all alarms when user stops navigation
                    stopAllAlarmsAndNotifications()

                    Toast.makeText(
                        this@DashboardActivity,
                        "Navigation stopped",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onFailure = { error ->
                Log.e(TAG, "Failed to stop navigation cleanly", error)

                runOnUiThread {
                    progressNavigation.visibility = View.GONE
                    btnToggleNavigation.isEnabled = true

                    updateNavigationUI()
                    updateDrawerState()
                    updateToolbarState()
                    updateMapInteraction() // Re-enable pinning after navigation stops

                    navigationStatusAnimation?.cancel()
                    geofenceCircle?.remove()

                    // Stop all alarms when navigation stops due to error
                    stopAllAlarmsAndNotifications()

                    Toast.makeText(
                        this@DashboardActivity,
                        "Navigation stopped (cleanup error)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    private fun pauseNavigation() {
        Log.d(TAG, "Pausing navigation")
        navigationHelper.pauseNavigation()

        updateNavigationUI()
        updateDrawerState()
        updateToolbarState()

        Toast.makeText(this, "Navigation paused", Toast.LENGTH_SHORT).show()
    }

    private fun resumeNavigation() {
        Log.d(TAG, "Resuming navigation")

        // Use regular function call instead of suspend
        val resumed = navigationHelper.resumeNavigation { isLoading ->
            runOnUiThread {
                if (isLoading) {
                    progressNavigation.visibility = View.VISIBLE
                    btnToggleNavigation.isEnabled = false
                } else {
                    progressNavigation.visibility = View.GONE
                    btnToggleNavigation.isEnabled = true
                }
            }
        }

        runOnUiThread {
            if (resumed) {
                updateNavigationUI()
                updateDrawerState()
                updateToolbarState()
                addNavigationStateVisualFeedback()

                Toast.makeText(this@DashboardActivity, "Navigation resumed", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@DashboardActivity, "Cannot resume navigation - no destination set", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadModalLocationData(latLng: LatLng) {
        lifecycleScope.launch {
            try {
                val address = locationHelper.getAddressFromLocation(latLng)
                tvModalLocationAddress.text = address

                currentLocation?.let { userLoc ->
                    val distance = locationHelper.calculateDistance(userLoc, latLng)
                    tvModalLocationDistance.text =
                        "Distance: ${locationHelper.formatDistance(distance)}"
                } ?: run {
                    tvModalLocationDistance.text = "Distance: Calculating..."
                }

                val trafficCondition = locationHelper.estimateTrafficCondition()
                tvModalTrafficCondition.text = trafficCondition
                updateModalTrafficColor(trafficCondition)

                calculateRealModalTravelTimes()

            } catch (e: Exception) {
                Log.e(TAG, "Error loading modal location data", e)
                tvModalLocationAddress.text = "Error loading address"
                tvModalLocationDistance.text = "Distance unavailable"
            }
        }
    }

    private fun calculateRealModalTravelTimes() {
        pinnedLocation?.let { location ->
            currentLocation?.let { userLoc ->
                lifecycleScope.launch {
                    try {
                        val travelTimes = locationHelper.getTravelTimesForAllModes(userLoc, location)

                        tvWalkingTime.text = travelTimes["walking"] ?: run {
                            val distance = locationHelper.calculateDistance(userLoc, location)
                            val time = locationHelper.estimateTravelTime(distance * 2.5, "Light")
                            locationHelper.formatTravelTime(time)
                        }

                        tvMotorcycleTime.text = travelTimes["bicycling"] ?: run {
                            val distance = locationHelper.calculateDistance(userLoc, location)
                            val time = locationHelper.estimateTravelTime(distance * 0.8, "Light")
                            locationHelper.formatTravelTime(time)
                        }

                        tvCarTime.text = travelTimes["driving"] ?: run {
                            val distance = locationHelper.calculateDistance(userLoc, location)
                            val time = locationHelper.estimateTravelTime(
                                distance,
                                locationHelper.estimateTrafficCondition()
                            )
                            locationHelper.formatTravelTime(time)
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "Error calculating real travel times", e)
                        calculateEstimatedTravelTimes()
                    }
                }
            }
        }
    }

    private fun calculateEstimatedTravelTimes() {
        pinnedLocation?.let { location ->
            currentLocation?.let { userLoc ->
                val distance = locationHelper.calculateDistance(userLoc, location)
                val trafficCondition = tvModalTrafficCondition.text.toString()

                val walkingTime = locationHelper.estimateTravelTime(distance * 2.5, "Light")
                val motorcycleTime = locationHelper.estimateTravelTime(distance * 0.8, trafficCondition)
                val carTime = locationHelper.estimateTravelTime(distance, trafficCondition)

                tvWalkingTime.text = locationHelper.formatTravelTime(walkingTime)
                tvMotorcycleTime.text = locationHelper.formatTravelTime(motorcycleTime)
                tvCarTime.text = locationHelper.formatTravelTime(carTime)
            }
        }
    }

    private fun updateModalTrafficColor(condition: String) {
        val color = when (condition) {
            "Light" -> ContextCompat.getColor(this, R.color.success)
            "Moderate" -> ContextCompat.getColor(this, R.color.warning)
            "Heavy" -> ContextCompat.getColor(this, R.color.error)
            else -> ContextCompat.getColor(this, R.color.text_secondary)
        }
        tvModalTrafficCondition.setTextColor(color)
    }

    private fun updateTransportSelection(mode: TransportMode) {
        transportWalking.isSelected = false
        transportMotorcycle.isSelected = false
        transportCar.isSelected = false

        selectedTransportMode = mode
        when (mode) {
            TransportMode.WALKING -> transportWalking.isSelected = true
            TransportMode.MOTORCYCLE -> transportMotorcycle.isSelected = true
            TransportMode.CAR -> transportCar.isSelected = true
        }
    }

    private fun updateRouteForSelectedTransport() {
        currentLocation?.let { origin ->
            pinnedLocation?.let { destination ->
                drawRealRoute(origin, destination, selectedTransportMode)
            }
        }
    }

    private fun updateNavigationUI() {
        navigationStatusAnimation?.cancel()

        if (isNavigating) {
            navigationStatusSection.visibility = View.VISIBLE
            navigationInstructions.visibility = View.VISIBLE
            tvNavigationStatus.text = getString(R.string.navigation_active)
            tvNavigationStatus.setTextColor(ContextCompat.getColor(this, R.color.navigation_active))
            btnToggleNavigation.text = getString(R.string.stop_navigation)
            btnToggleNavigation.setBackgroundColor(
                ContextCompat.getColor(this, R.color.navigation_error)
            )
            tvNoPinMessage.visibility = View.GONE
            
            // Update geofence distance information
            updateGeofenceDistanceInfo()
        } else {
            navigationStatusSection.visibility = View.GONE
            navigationInstructions.visibility = View.GONE
            geofenceDistanceInfo.visibility = View.GONE
            btnToggleNavigation.text = getString(R.string.start_navigation)
            btnToggleNavigation.setBackgroundColor(
                ContextCompat.getColor(this, R.color.primary_brown)
            )

            if (pinnedLocation == null) {
                tvNoPinMessage.visibility = View.VISIBLE
            }
        }

        updateNavigationButton()
        updateQuickNavigationButton()
        updateCollapsedNavigationStatus()
    }

    private fun updateNavigationButton() {
        if (isNavigating) {
            tvNavigationText.text = getString(R.string.stop_navigation)
            tvNavigationText.setTextColor(ContextCompat.getColor(this, R.color.navigation_error))
            ivNavigationIcon.setImageResource(R.drawable.ic_stop)
            ivNavigationIcon.setColorFilter(ContextCompat.getColor(this, R.color.navigation_error))
        } else {
            tvNavigationText.text = getString(R.string.start_navigation)
            tvNavigationText.setTextColor(ContextCompat.getColor(this, R.color.primary_brown))
            ivNavigationIcon.setImageResource(R.drawable.ic_navigation)
            ivNavigationIcon.setColorFilter(ContextCompat.getColor(this, R.color.primary_brown))
        }
    }

    private fun updateMapInteraction() {
        if (::mMap.isInitialized) {
            if (isNavigating) {
                mMap.setOnMapClickListener {
                    Toast.makeText(
                        this,
                        getString(R.string.navigation_locked_message),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                mMap.uiSettings.isZoomControlsEnabled = false
                mMap.uiSettings.isCompassEnabled = false
                mMap.uiSettings.isMapToolbarEnabled = false

            } else {
                mMap.setOnMapClickListener { latLng ->
                    // Check if location is within Marikina/Antipolo service area
                    if (isLocationWithinServiceArea(latLng)) {
                        addOrUpdateMarkerWithGeofence(latLng)
                        updatePinnedLocationInfo(latLng)

                        currentLocation?.let { current ->
                            drawRealRoute(current, latLng, selectedTransportMode)
                        }

                        pinnedLocation = latLng
                        showModal()
                        loadModalLocationData(latLng)
                    } else {
                        // Show toast message for invalid location
                        Toast.makeText(
                            this@DashboardActivity,
                            "You can only pin locations within Marikina and Antipolo area",
                            Toast.LENGTH_LONG
                        ).show()
                        
                        // Optionally, show the nearest valid location
                        val nearestValidLocation = getNearestValidLocation(latLng)
                        val message = "Nearest supported area: ${if (nearestValidLocation == "marikina") "Marikina" else "Antipolo"}"
                        
                        // Show as a snackbar for better UX
                        findViewById<View>(android.R.id.content)?.let { view ->
                            com.google.android.material.snackbar.Snackbar.make(view, message, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
                        }
                    }
                }

                mMap.uiSettings.isZoomControlsEnabled = true
                mMap.uiSettings.isCompassEnabled = true
                mMap.uiSettings.isMapToolbarEnabled = true
            }
        }
    }

    private fun updateDrawerState() {
        if (isNavigating) {
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            toggle.isDrawerIndicatorEnabled = false

            updateBottomNavigationState(false)

            supportActionBar?.title = "Navigating..."
            supportActionBar?.setDisplayHomeAsUpEnabled(false)

        } else {
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            toggle.isDrawerIndicatorEnabled = true

            updateBottomNavigationState(true)

            supportActionBar?.title = getString(R.string.app_name)
            supportActionBar?.setDisplayHomeAsUpEnabled(false)
            toggle.syncState()
        }
    }

    private fun updateBottomNavigationState(enabled: Boolean) {
        if (::bottomNavigation.isInitialized) {
            val menu = bottomNavigation.menu
            for (i in 0 until menu.size()) {
                menu.getItem(i).isEnabled = enabled
            }

            if (enabled) {
                bottomNavigation.alpha = 1.0f
            } else {
                bottomNavigation.alpha = 0.6f
            }
        }
    }

    private fun updateToolbarState() {
        if (isNavigating) {
            toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.navigation_active))
            bottomNavigation.setBackgroundColor(ContextCompat.getColor(this, R.color.navigation_active))
            supportActionBar?.subtitle = getString(R.string.navigation_active)

        } else {
            toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.primary_brown))
            bottomNavigation.setBackgroundColor(ContextCompat.getColor(this, R.color.primary_brown))
            supportActionBar?.subtitle = null
        }
    }

    private fun addNavigationStateVisualFeedback() {
        if (isNavigating && ::tvNavigationStatus.isInitialized) {
            navigationStatusAnimation = ObjectAnimator.ofFloat(tvNavigationStatus, "alpha", 1f, 0.5f, 1f)
            navigationStatusAnimation?.apply {
                duration = 1500
                repeatCount = ObjectAnimator.INFINITE
                start()
            }
        }
    }

    private fun showStopNavigationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Stop Navigation")
            .setMessage(getString(R.string.navigation_confirm_exit))
            .setPositiveButton(getString(R.string.stop_and_exit)) { _, _ ->
                stopNavigation()
                lifecycleScope.launch {
                    delay(500)
                    finish()
                }
            }
            .setNegativeButton(getString(R.string.continue_navigation), null)
            .setNeutralButton(getString(R.string.stop_navigation)) { _, _ ->
                stopNavigation()
            }
            .show()
    }

    private fun drawRealRoute(
        start: LatLng,
        end: LatLng,
        transportMode: TransportMode = selectedTransportMode
    ) {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@DashboardActivity, "Loading route...", Toast.LENGTH_SHORT).show()

                val apiMode = when (transportMode) {
                    TransportMode.WALKING -> "walking"
                    TransportMode.MOTORCYCLE -> "bicycling"
                    TransportMode.CAR -> "driving"
                }

                val routeResult = locationHelper.getRealRoute(start, end, apiMode)

                if (routeResult != null && routeResult.polylinePoints.isNotEmpty()) {
                    currentPolyline?.remove()

                    val polylineOptions = PolylineOptions()
                        .addAll(routeResult.polylinePoints)
                        .width(12f)
                        .color(ContextCompat.getColor(this@DashboardActivity, R.color.primary_brown))
                        .geodesic(true)
                        .jointType(JointType.ROUND)
                        .startCap(RoundCap())
                        .endCap(RoundCap())

                    currentPolyline = mMap.addPolyline(polylineOptions)

                    updateRealRouteInfo(routeResult)

                    Toast.makeText(
                        this@DashboardActivity,
                        "Route loaded: ${routeResult.distance}, ${routeResult.duration}",
                        Toast.LENGTH_SHORT
                    ).show()

                } else {
                    drawStraightLineRoute(start, end)
                    Toast.makeText(
                        this@DashboardActivity,
                        "Using direct route (API unavailable)",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error drawing real route", e)
                drawStraightLineRoute(start, end)
                Toast.makeText(
                    this@DashboardActivity,
                    "Error loading route, using direct path",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun drawStraightLineRoute(start: LatLng, end: LatLng) {
        currentPolyline?.remove()

        val polylineOptions = PolylineOptions()
            .add(start)
            .add(end)
            .width(12f)
            .color(ContextCompat.getColor(this, R.color.primary_brown))
            .geodesic(true)
            .jointType(JointType.ROUND)
            .startCap(RoundCap())
            .endCap(RoundCap())

        currentPolyline = mMap.addPolyline(polylineOptions)

        currentLocation?.let { current ->
            updateRouteInfo(current, end)
        }
    }

    private fun updateRealRouteInfo(routeResult: LocationHelper.RouteResult) {
        routeInfoSection.visibility = View.VISIBLE

        tvDistance.text = routeResult.distance
        tvEta.text = routeResult.duration

        val trafficCondition = estimateTrafficFromRoute(routeResult)
        tvTrafficCondition.text = trafficCondition
        updateTrafficColor(trafficCondition)
    }

    private fun estimateTrafficFromRoute(routeResult: LocationHelper.RouteResult): String {
        val idealMinutes = (routeResult.distanceKm * 1.0).toInt()
        val actualMinutes = routeResult.durationMinutes

        return when {
            actualMinutes <= idealMinutes * 1.2 -> "Light"
            actualMinutes <= idealMinutes * 1.8 -> "Moderate"
            else -> "Heavy"
        }
    }

    private fun updateTrafficColor(condition: String) {
        val color = when (condition) {
            "Light" -> ContextCompat.getColor(this, R.color.success)
            "Moderate" -> ContextCompat.getColor(this, R.color.warning)
            "Heavy" -> ContextCompat.getColor(this, R.color.error)
            else -> ContextCompat.getColor(this, R.color.text_secondary)
        }
        tvTrafficCondition.setTextColor(color)
    }

    private fun loadGeofenceSettings() {
        try {
            // Load geofence radius from preferences using the enhanced method
            val savedRadius = GeofenceHelper.loadGeofenceRadiusFromPreferences(this)
            GeofenceHelper.setGeofenceRadius(savedRadius)
            Log.d(TAG, "📱 Loaded geofence radius from preferences: ${savedRadius}m")
            
            // Update geofence visualization if one exists
            try {
                updateGeofenceCircle()
            } catch (e: Exception) {
                Log.d(TAG, "No geofence circle to update yet")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading geofence settings", e)
        }
    }

    private fun handleAlarmStoppedIntent() {
        if (intent.getBooleanExtra("alarm_stopped", false)) {
            val stoppedAlarmId = intent.getIntExtra("stopped_alarm_id", -1)
            val navigationEnded = intent.getBooleanExtra("navigation_ended", false)
            val allowNewPin = intent.getBooleanExtra("allow_new_pin", false)
            val showSaveLocationModal = intent.getBooleanExtra("show_save_location_modal", false)

            Log.d(TAG, "🔄 Processing alarm stopped intent - Alarm ID: $stoppedAlarmId, Navigation ended: $navigationEnded")

            // ALWAYS deactivate navigation when alarm is stopped
            if (isNavigating) {
                Log.d(TAG, "🛑 Deactivating navigation due to alarm stop")
                lifecycleScope.launch {
                    try {
                        // Stop navigation through NavigationHelper
                        navigationHelper.stopNavigation(
                            onSuccess = {
                                Log.d(TAG, "✅ Navigation deactivated successfully after alarm stop")
                                runOnUiThread {
                                    Toast.makeText(this@DashboardActivity,
                                        "Navigation deactivated - alarm stopped",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            onFailure = { error ->
                                Log.e(TAG, "❌ Failed to deactivate navigation after alarm stop", error)
                                runOnUiThread {
                                    Toast.makeText(this@DashboardActivity,
                                        "Error deactivating navigation",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Exception while deactivating navigation after alarm stop", e)
                    }
                }
            }

            if (navigationEnded) {
                lifecycleScope.launch {
                    // Clear any remaining navigation state
                    if (isNavigating) {
                        stopNavigation()
                    }
                    
                    // Clear current pin and visualization
                    clearMapVisualization()
                    
                    // Reset UI to allow new pins
                    resetUIForNewNavigation()

                    // Check if user is inside geofence and show save location modal
                    if (showSaveLocationModal) {
                        lifecycleScope.launch {
                            delay(1000) // Small delay to ensure UI is ready
                            showSaveLocationModal()
                        }
                    } else {
                        Toast.makeText(this@DashboardActivity,
                            "🎉 You've reached your destination! Ready for a new journey.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } else if (allowNewPin) {
                // Alarm was stopped but navigation might continue - just enable pinning
                // However, since we're deactivating navigation above, this will be handled
                Log.d(TAG, "📍 Alarm stopped - navigation deactivated, allowing new pins")
                
                lifecycleScope.launch {
                    // Clear current pin and visualization to allow new pins
                    clearMapVisualization()
                    
                    // Reset UI to allow new pins
                    resetUIForNewNavigation()
                    
                    Toast.makeText(this@DashboardActivity,
                        "Alarm stopped. Navigation deactivated. You can now pin new locations.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            Log.d(TAG, "✅ Alarm stopped handling completed - Alarm ID: $stoppedAlarmId, Navigation ended: $navigationEnded, Show save modal: $showSaveLocationModal")
        }
    }
    
    /**
     * Handle navigation stopped from notification
     */
    private fun handleNavigationStoppedFromNotificationIntent() {
        if (intent.getBooleanExtra("navigation_stopped_from_notification", false)) {
            Log.d(TAG, "Navigation was stopped from notification")
            
            lifecycleScope.launch {
                // Clear any remaining navigation state
                if (isNavigating) {
                    stopNavigation()
                }
                
                // Clear current pin and visualization
                clearMapVisualization()
                
                // Reset UI to allow new pins
                resetUIForNewNavigation()

                Toast.makeText(this@DashboardActivity,
                    "Navigation stopped from notification. Ready for new destination.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    /**
     * Reset UI components to allow for new navigation
     */
    private fun resetUIForNewNavigation() {
        try {
            // Clear pinned location
            pinnedLocation = null
            
            // Clear markers and routes from map
            pinnedLocationMarker?.remove()
            pinnedLocationMarker = null
            currentPolyline?.remove()
            currentPolyline = null
            geofenceCircle?.remove()
            geofenceCircle = null
            
            // Reset UI components
            pinnedLocationSection.visibility = View.GONE
            pinnedLocationDivider.visibility = View.GONE
            navigationStatusSection.visibility = View.GONE
            navigationInstructions.visibility = View.GONE
            geofenceDistanceInfo.visibility = View.GONE
            routeInfoSection.visibility = View.GONE
            tvNoPinMessage.visibility = View.VISIBLE
            
            // Stop any navigation animations
            navigationStatusAnimation?.cancel()
            navigationStatusCollapsedAnimation?.cancel()
            
            // Reset modal state
            if (modalOverlay.visibility == View.VISIBLE) {
                hideModal()
            }
            
            // Update navigation buttons and UI
            updateNavigationUI()
            updateQuickNavigationButton()
            updateCollapsedNavigationStatus()
            updateDrawerState()
            updateToolbarState()
            updateMapInteraction() // Re-enable map interactions for pinning
            
            Log.d(TAG, "UI reset for new navigation completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting UI for new navigation", e)
        }
    }

    /**
     * Handle multi-point route navigation from RoutePlannerActivity
     */
    private fun handleMultiPointRouteIntent() {
        if (intent.getBooleanExtra("multi_point_route", false)) {
            Log.d(TAG, "Handling multi-point route navigation")
            
            val routeDescription = intent.getStringExtra("route_description") ?: "Multi-point route"
            val alarmEachStop = intent.getBooleanExtra("alarm_each_stop", true)
            val voiceAnnouncements = intent.getBooleanExtra("voice_announcements", true)
            val routePointsCount = intent.getIntExtra("route_points_count", 0)
            
            // Get first destination to start navigation
            val destinationLat = intent.getDoubleExtra("destination_lat", 0.0)
            val destinationLng = intent.getDoubleExtra("destination_lng", 0.0)
            val destinationName = intent.getStringExtra("destination_name") ?: "Destination"
            val destinationAddress = intent.getStringExtra("destination_address") ?: destinationName
            
            if (destinationLat != 0.0 && destinationLng != 0.0) {
                val destination = LatLng(destinationLat, destinationLng)
                
                // Start navigation history tracking for multi-point route
                lifecycleScope.launch {
                    try {
                        val destinations = mutableListOf<NavigationDestination>()
                        
                        // Add the first destination (we'll update with full route later if needed)
                        destinations.add(NavigationDestination(
                            name = destinationName,
                            address = destinationAddress,
                            latLng = destination,
                            order = 0
                        ))
                        
                        val userId = sessionManager.getCurrentUser()?.id
                        
                        currentNavigationHistory = navigationHistoryService.startNavigation(
                            routeDescription = routeDescription,
                            startLocation = currentLocation,
                            destinations = destinations,
                            totalDistance = calculateDistance(currentLocation, destination),
                            estimatedDuration = calculateEstimatedTime(currentLocation, destination),
                            userId = userId
                        )
                        
                        Log.d(TAG, "Multi-point route navigation history started: ${currentNavigationHistory?.id}")
                        
                        runOnUiThread {
                            // Pin the first destination and start navigation
                            pinnedLocation = destination
                            updateNavigationUI()
                            startNavigation()
                            
                            Toast.makeText(this@DashboardActivity, 
                                "Starting multi-point route: $routeDescription ($routePointsCount stops)", 
                                Toast.LENGTH_LONG).show()
                        }
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start multi-point route navigation history", e)
                        runOnUiThread {
                            Toast.makeText(this@DashboardActivity, 
                                "Error starting route: ${e.message}", 
                                Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                Log.w(TAG, "Invalid destination coordinates in multi-point route intent")
                Toast.makeText(this, "Invalid route destination", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handlePlacePinIntent() {
        if (intent.getBooleanExtra("pin_location", false)) {
            val lat = intent.getDoubleExtra("destination_lat", 0.0)
            val lng = intent.getDoubleExtra("destination_lng", 0.0)
            val name = intent.getStringExtra("destination_name") ?: "Selected Place"
            val address = intent.getStringExtra("destination_address") ?: ""
            val category = intent.getStringExtra("place_category") ?: ""
            val autoCreateGeofence = intent.getBooleanExtra("auto_create_geofence", false)

            if (lat != 0.0 && lng != 0.0) {
                val location = LatLng(lat, lng)
                
                // Wait for map to be ready, then pin the location
                if (::mMap.isInitialized) {
                    pinLocationFromPlaces(location, name, address, autoCreateGeofence)
                } else {
                    // Store for later when map is ready
                    pendingPinLocation = PendingPinData(location, name, address, autoCreateGeofence)
                }
                
                Log.d(TAG, "Place pin intent handled: $name at $location")
            }
        }
    }

    private data class PendingPinData(
        val location: LatLng,
        val name: String,
        val address: String,
        val autoCreateGeofence: Boolean
    )

    private var pendingPinLocation: PendingPinData? = null

    private fun setupLocationCard() {
        val locationCard = findViewById<View>(R.id.locationCardInclude)

        tvCurrentLocationAddress = locationCard.findViewById(R.id.tvCurrentLocationAddress)
        tvPinnedLocationAddress = locationCard.findViewById(R.id.tvPinnedLocationAddress)
        tvEta = locationCard.findViewById(R.id.tvEta)
        tvDistance = locationCard.findViewById(R.id.tvDistance)
        tvTrafficCondition = locationCard.findViewById(R.id.tvTrafficCondition)
        tvNoPinMessage = locationCard.findViewById(R.id.tvNoPinMessage)
        pinnedLocationSection = locationCard.findViewById(R.id.pinnedLocationSection)
        routeInfoSection = locationCard.findViewById(R.id.routeInfoSection)
        navigationStatusSection = locationCard.findViewById(R.id.navigationStatusSection)
        tvNavigationStatus = locationCard.findViewById(R.id.tvNavigationStatus)
        btnToggleNavigation = locationCard.findViewById(R.id.btnToggleNavigation)
        progressNavigation = locationCard.findViewById(R.id.progressNavigation)

        // Initialize geofence distance views
        geofenceDistanceInfo = locationCard.findViewById(R.id.geofenceDistanceInfo)
        tvGeofenceStatus = locationCard.findViewById(R.id.tvGeofenceStatus)
        tvGeofenceDistance = locationCard.findViewById(R.id.tvGeofenceDistance)
        
        // ENHANCED: Initialize direct location handling components
        Log.d("$TAG $UI_TAG", "🔧 Initializing location UI components...")
        
        tvCurrentLocationSummary = locationCard.findViewById(R.id.tvCurrentLocationSummary)
        tvCurrentLocationAddress = locationCard.findViewById(R.id.tvCurrentLocationAddress)
        tvCurrentLocationCoordinates = locationCard.findViewById(R.id.tvCurrentLocationCoordinates)
        tvCurrentLocationCoordinatesCollapsed = locationCard.findViewById(R.id.tvCurrentLocationCoordinatesCollapsed)
        
        Log.d("$TAG $DEBUG_TAG", "📊 TextView bindings: summary=${tvCurrentLocationSummary != null}, address=${tvCurrentLocationAddress != null}, coordinates=${tvCurrentLocationCoordinates != null}, collapsed=${tvCurrentLocationCoordinatesCollapsed != null}")
        
        // Start direct location handling
        Log.d("$TAG $SERVICE_TAG", "🚀 Starting direct location handling...")
        startDirectLocationHandling()
        Log.d("$TAG $SERVICE_TAG", "📍 Direct location handling initialized in DashboardActivity")
        
        // DEBUG: Log initial system state
        Log.d("$TAG $DEBUG_TAG", "🔍 Logging initial location system state...")
        debugLocationSystem()
        
        // TEST: Try to force a location update after a delay to see if direct service communication works
        android.os.Handler(Looper.getMainLooper()).postDelayed({
            Log.d("$TAG $DEBUG_TAG", "🧪 Testing direct location update after 3 seconds...")
            forceLocationUpdate()
        }, 3000)

        pinnedLocationDivider = locationCard.findViewById(R.id.pinnedLocationDivider) ?: View(this)
        navigationInstructions = locationCard.findViewById(R.id.navigationInstructions) ?: View(this)

        tvCurrentLocationAddress.text = getString(R.string.calculating)

        pinnedLocationSection.visibility = View.GONE
        routeInfoSection.visibility = View.GONE
        pinnedLocationDivider.visibility = View.GONE
        navigationStatusSection.visibility = View.GONE
        navigationInstructions.visibility = View.GONE
        geofenceDistanceInfo.visibility = View.GONE
        tvNoPinMessage.visibility = View.VISIBLE

        btnToggleNavigation.setOnClickListener {
            if (pinnedLocation == null) {
                Toast.makeText(this, "Please pin a location first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isNavigating) {
                stopNavigation()
            } else {
                startNavigation()
            }
        }
    }

    private fun updateNavigationHeader() {
        val headerView = navigationView.getHeaderView(0)
        val tvUserName = headerView.findViewById<TextView>(R.id.tvUserName)
        val tvUserEmail = headerView.findViewById<TextView>(R.id.tvUserEmail)

        lifecycleScope.launch {
            val session = sessionManager.getSession()
            val userId = session["userId"] as String?

            if (userId != null) {
                val user = sessionManager.fetchCurrentUserData(userId)

                if (user != null) {
                    tvUserName.text = "${user.firstName} ${user.lastName}"
                    tvUserEmail.text = user.email
                } else {
                    tvUserName.text = session["userName"] as String? ?: "User"
                    tvUserEmail.text = session["email"] as String? ?: "no-email@user.com"
                }
            }
        }
    }
    
    /**
     * Updates the geofence distance information in the location card
     * Shows distance to geofence and changes status when inside geofence
     */
    private fun updateGeofenceDistanceInfo() {
        if (currentLocation == null || pinnedLocation == null || !isNavigating) {
            geofenceDistanceInfo.visibility = View.GONE
            return
        }
        
        // Show the geofence distance info section
        geofenceDistanceInfo.visibility = View.VISIBLE
        
        // Calculate distance to destination
        val distanceToDestination = locationHelper.calculateDistance(currentLocation!!, pinnedLocation!!)
        
        // Get geofence radius
        val geofenceRadius = GeofenceHelper.getGeofenceRadius() / 1000.0 // Convert to km
        
        // Determine if user is inside geofence
        val isInsideGeofence = distanceToDestination <= geofenceRadius
        
        // Update UI based on location relative to geofence
        if (isInsideGeofence) {
            tvGeofenceStatus.text = "You are arriving to the destination"
            tvGeofenceDistance.text = "Inside geofence zone"
            
            // Get geofence helper to check if we need to trigger the alarm manually
            val geofenceHelper = GeofenceHelper(this)
            
            // If we're inside the geofence but the alarm hasn't been triggered yet,
            // manually trigger the alarm notification
            if (!geofenceHelper.isUserInsideGeofence()) {
                Log.d(TAG, "User inside geofence but status not set - triggering alarm manually")
                geofenceHelper.setUserInsideGeofence(true)
                
                // Show alarm notification with clean destination name  
                val destinationAddress = "your destination" // Will be updated async
                
                notificationService.showAlarmNotification(
                    "🚨 DESTINATION REACHED!",
                    "You have arrived at $destinationAddress!",
                    GeofenceBroadcastReceiver.ARRIVAL_ALARM_ID
                )
                
                // Trigger voice announcement if enabled
                val voiceService = VoiceAnnouncementService(this)
                if (isVoiceAnnouncementEnabled()) {
                    voiceService.announceArrival(destinationAddress)
                }
            }
        } else {
            val distanceToGeofence = distanceToDestination - geofenceRadius
            tvGeofenceStatus.text = "Outside geofence"
            tvGeofenceDistance.text = "Distance to geofence: ${locationHelper.formatDistance(distanceToGeofence)}"
        }
    }



    private fun setupMap() {
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        checkLocationPermission()

        try {
            if (locationPermissionGranted) {
                updateLocationUI()
                getDeviceLocation()
            }

            updateMapInteraction()

            // Handle pending pin location if map is now ready
            pendingPinLocation?.let { pendingData ->
                pinLocationFromPlaces(
                    pendingData.location,
                    pendingData.name,
                    pendingData.address,
                    pendingData.autoCreateGeofence
                )
                pendingPinLocation = null
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException in onMapReady", e)
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionGranted = true
            // Ensure real-time location updates are running when permissions are granted
            ensureBackgroundLocationServiceRunning()
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun ensureBackgroundLocationServiceRunning() {
        try {
            val isRunning = BackgroundLocationService.isServiceRunning(this)
            val currentTime = android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis())
            
            Log.d(TAG, "🔍 Checking BackgroundLocationService status at $currentTime: isRunning=$isRunning")
            
            if (!isRunning) {
                Log.d(TAG, "🚀 STARTING BackgroundLocationService for real-time updates...")
                BackgroundLocationService.startService(this)
                
                // Give it a moment to start
                android.os.Handler(Looper.getMainLooper()).postDelayed({
                    val nowRunning = BackgroundLocationService.isServiceRunning(this)
                    if (nowRunning) {
                        Log.d(TAG, "✅ BackgroundLocationService successfully started")
                        // Force a location update after service starts
                        forceLocationUpdate()
                    } else {
                        Log.e(TAG, "❌ BackgroundLocationService failed to start!")
                        // Try again with more aggressive approach
                        android.os.Handler(Looper.getMainLooper()).postDelayed({
                            Log.d(TAG, "🔄 Retrying BackgroundLocationService start...")
                            BackgroundLocationService.startService(this)
                        }, 3000)
                    }
                }, 2000)
            } else {
                Log.d(TAG, "✅ BackgroundLocationService already running")
                
                // ENHANCED: Check if we're getting location updates, if not, restart
                val lastLocation = BackgroundLocationService.getLastLocation(this)
                val locationAge = if (lastLocation != null) {
                    System.currentTimeMillis() - lastLocation.time
                } else {
                    Long.MAX_VALUE
                }
                
                // If location is older than 15 seconds (reduced from 30s), restart the service
                if (locationAge > 15000L) {
                    Log.w(TAG, "⚠️ Location service running but location is stale (${locationAge}ms old) - restarting...")
                    // ENHANCED: Don't stop service immediately, just restart it
                    android.os.Handler(Looper.getMainLooper()).postDelayed({
                        BackgroundLocationService.startService(this)
                        Log.d(TAG, "🔄 Location service restarted due to stale location")
                    }, 1000)
                } else {
                    Log.d(TAG, "✅ Location service healthy, location age: ${locationAge}ms")
                    // Force a location update to ensure fresh data
                    forceLocationUpdate()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to ensure BackgroundLocationService is running", e)
            // Try to start service despite error
            try {
                BackgroundLocationService.startService(this)
            } catch (startError: Exception) {
                Log.e(TAG, "❌ Failed to start service after error", startError)
            }
        }
    }

    private fun tryPopulateFromLastKnownLocation() {
        try {
            val last = BackgroundLocationService.getLastLocation(this)
            if (last != null) {
                val latLng = LatLng(last.latitude, last.longitude)
                currentLocation = latLng
                try { updateCurrentLocationInfo(latLng) } catch (_: Exception) {}
                // User location is handled by Google Maps blue dot
                Log.d(TAG, "Populated UI from last known location: ${last.latitude}, ${last.longitude}")
            } else {
                Log.d(TAG, "No last known background location available yet")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error populating from last known location", e)
        }
    }

    private fun addGeofenceVisualization(latLng: LatLng, radiusInMeters: Float) {
        geofenceCircle?.remove()

        geofenceCircle = mMap.addCircle(
            CircleOptions()
                .center(latLng)
                .radius(radiusInMeters.toDouble())
                .strokeColor(ContextCompat.getColor(this, R.color.primary_brown))
                .strokeWidth(3f)
                .fillColor(Color.argb(50, 139, 69, 19))
        )
    }

    private fun updateLocationUI() {
        try {
            if (locationPermissionGranted) {
                mMap.isMyLocationEnabled = true
                mMap.uiSettings.isMyLocationButtonEnabled = true
            } else {
                mMap.isMyLocationEnabled = false
                mMap.uiSettings.isMyLocationButtonEnabled = false
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException in updateLocationUI", e)
        }
    }

    private fun getDeviceLocation() {
        try {
            if (locationPermissionGranted) {
                val cancellationToken = CancellationTokenSource()
                val currentLocationTask = fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationToken.token
                )

                currentLocationTask.addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val location = task.result
                        if (location != null) {
                            currentLocation = LatLng(location.latitude, location.longitude)

                            mMap.moveCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                    currentLocation!!,
                                    15f
                                )
                            )

                            updateCurrentLocationInfo(currentLocation!!)

                            pinnedLocation?.let { pinned ->
                                drawRealRoute(currentLocation!!, pinned, selectedTransportMode)
                            }
                        }
                    } else {
                        mMap.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(0.0, 0.0),
                                2f
                            )
                        )
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException in getDeviceLocation", e)
        }
    }

    private fun addOrUpdateMarkerWithGeofence(latLng: LatLng) {
        // SAFEGUARD: Only allow updating if no location is already pinned, or if explicitly requested
        if (pinnedLocation != null && pinnedLocation != latLng) {
            Log.w(TAG, "⚠️ Attempted to change pinned location from $pinnedLocation to $latLng - this should not happen during normal operation")
            // Don't change the pinned location unless explicitly intended
            return
        }
        
        pinnedLocationMarker?.remove()
        currentPolyline?.remove()
        geofenceCircle?.remove()

        pinnedLocationMarker = mMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("📍 Destination (${GeofenceHelper.getGeofenceRadius().toInt()}m geofence)")
                .snippet("Tap to start navigation")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)) // Red pin for destination
        )

        pinnedLocation = latLng
        mMap.animateCamera(CameraUpdateFactory.newLatLng(latLng))

        addGeofenceVisualization(latLng, GeofenceHelper.getGeofenceRadius())
    }

    private fun updateCurrentLocationInfo(latLng: LatLng) {
        lifecycleScope.launch {
            try {
                val address = locationHelper.getAddressFromLocation(latLng)
                tvCurrentLocationAddress.text = address
                tvCurrentLocationSummary.text = address
                
                // Update geofence distance information
                updateGeofenceDistanceInfo()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating current location info", e)
                tvCurrentLocationAddress.text = "Location unavailable"
                tvCurrentLocationSummary.text = "Location unavailable"
            }
        }
    }

    private fun updatePinnedLocationInfo(latLng: LatLng) {
        pinnedLocationSection.visibility = View.VISIBLE
        pinnedLocationDivider.visibility = View.VISIBLE
        tvNoPinMessage.visibility = View.GONE

        tvPinnedLocationAddress.text = getString(R.string.calculating)

        lifecycleScope.launch {
            try {
                val address = locationHelper.getAddressFromLocation(latLng)
                tvPinnedLocationAddress.text = address
            } catch (e: Exception) {
                Log.e(TAG, "Error updating pinned location info", e)
                tvPinnedLocationAddress.text = "Location unavailable"
            }
        }
    }

    private fun updateRouteInfo(startLatLng: LatLng, endLatLng: LatLng) {
        routeInfoSection.visibility = View.VISIBLE

        val distance = locationHelper.calculateDistance(startLatLng, endLatLng)
        val trafficCondition = locationHelper.estimateTrafficCondition()
        val travelTime = locationHelper.estimateTravelTime(distance, trafficCondition)

        tvDistance.text = locationHelper.formatDistance(distance)
        tvTrafficCondition.text = trafficCondition
        tvEta.text = locationHelper.formatTravelTime(travelTime)

        updateTrafficColor(trafficCondition)
    }

    private fun logout() {
        lifecycleScope.launch {
            try {
                if (isNavigating) {
                    stopNavigation()
                }

                sessionManager.clearSession()
                authService.signOut()
                navigateToLogin()

            } catch (e: Exception) {
                Log.e(TAG, "Error during logout", e)
                Toast.makeText(this@DashboardActivity, "Error during logout", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        
        Log.d("$TAG $SERVICE_TAG", "🔄 App resumed - checking location service status...")
        
        // CRITICAL: Try to start any pending background services
        // This fixes the ForegroundServiceStartNotAllowedException
        BackgroundLocationService.startPendingServiceIfNeeded(this)
        
        Log.d("$TAG $SERVICE_TAG", "✅ App resumed - checked for pending service starts")
        
        // ENHANCED: Ensure background location service is running for real-time updates
        ensureBackgroundLocationServiceRunning()
        
        // ENHANCED: Try to populate UI from last known location immediately
        tryPopulateFromLastKnownLocation()
        
        loadGeofenceSettings()

        // CRITICAL FIX: Ensure GeofenceHelper is synchronized with saved pinnedLocation
        pinnedLocation?.let { pinned ->
            // Check if GeofenceHelper has the correct location
            val geofenceLocation = geofenceHelper.getCurrentGeofenceLocation()
            if (geofenceLocation == null || geofenceLocation != pinned) {
                Log.d(TAG, "🔄 Syncing GeofenceHelper with saved pinnedLocation: $pinned")
                // Use efficient sync method first
                geofenceHelper.syncGeofenceLocation(pinned)
                
                // If no geofence exists at all, create one
                if (!geofenceHelper.hasGeofence()) {
                    Log.d(TAG, "📌 No geofence exists, creating new one")
                    geofenceHelper.addGeofence(pinned,
                        onSuccess = { Log.d(TAG, "✅ Geofence created with correct location") },
                        onFailure = { e -> Log.e(TAG, "❌ Failed to create geofence", e) }
                    )
                }
            }
            
            val radius = GeofenceHelper.getGeofenceRadius()
            addGeofenceVisualization(pinned, radius)
            pinnedLocationMarker?.title = "Pinned Location (${radius.toInt()}m geofence)"
        }

        updateNavigationUI()
        updateDrawerState()
        updateToolbarState()
        
        // Refresh navigation state in case session changed while away
        refreshNavigationState()

        checkNavigationState()
        
        // DEBUG: Log system state on resume
        Log.d(TAG, "🔍 Logging location system state on resume...")
        debugLocationSystem()
        
        // ENHANCED: Start periodic location refresh for real-time updates while app is in foreground
        startPeriodicLocationRefresh()
        
        // ENHANCED: Refresh geofence settings in case they were changed from another activity
        refreshGeofenceSettingsIfNeeded()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Stop all alarms when activity is destroyed
        if (isNavigating) {
            stopAllAlarmsAndNotifications()
        }

        // ENHANCED: Stop direct location handling
        try {
            stopLocationRefreshCycle()
        } catch (_: Exception) {}
        
        // ENHANCED: Clean up location change handlers
        cleanupLocationChangeHandlers()
        
        // ENHANCED: Stop periodic location refresh
        stopPeriodicLocationRefresh()
        
        // ENHANCED: Stop location service health monitoring
        stopLocationServiceHealthMonitoring()

        // Unregister broadcast receivers
        try {
            unregisterReceiver(realTimeLocationReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering real-time location receiver", e)
        }
        try {
            unregisterReceiver(geofenceEventReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering geofence event receiver", e)
        }

        navigationStatusAnimation?.cancel()
        navigationStatusCollapsedAnimation?.cancel()
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "Activity paused, navigation continues in background")
        
        // ENHANCED: Stop periodic location refresh when app goes to background
        stopPeriodicLocationRefresh()
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Confirm Logout")
            .setMessage("You are currently navigating. Do you want to stop navigation and logout?")
            .setPositiveButton("Logout") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performLogout() {
        logout()
    }

    /**
     * Stop all alarms and clear notifications
     */
    private fun stopAllAlarmsAndNotifications() {
        Log.d(TAG, "🛑 Stopping all alarms and clearing notifications")
        
        // Stop alarm sound and vibration
        NotificationService.stopAllAlarms()
        notificationService.clearAlarmNotifications()
        notificationService.clearNavigationNotifications()
        
        // ALWAYS deactivate navigation when alarms are stopped
        if (isNavigating) {
            Log.d(TAG, "🛑 Deactivating navigation due to alarm stop")
            lifecycleScope.launch {
                try {
                    // Stop navigation through NavigationHelper
                    navigationHelper.stopNavigation(
                        onSuccess = {
                            Log.d(TAG, "✅ Navigation deactivated successfully after alarm stop")
                            runOnUiThread {
                                // Clear current pin and visualization
                                clearMapVisualization()
                                
                                // Reset UI to allow new pins
                                resetUIForNewNavigation()
                                
                                Toast.makeText(this@DashboardActivity,
                                    "Navigation deactivated - alarms stopped",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onFailure = { error ->
                            Log.e(TAG, "❌ Failed to deactivate navigation after alarm stop", error)
                            runOnUiThread {
                                Toast.makeText(this@DashboardActivity,
                                    "Error deactivating navigation",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Exception while deactivating navigation after alarm stop", e)
                }
            }
        }
        
        Log.d(TAG, "✅ All alarms stopped and navigation deactivated")
    }
    
    /**
     * Show save location modal when user is inside geofence
     */
    private fun showSaveLocationModal() {
        Log.d(TAG, "Showing save location modal - user is inside geofence")
        
        // Get current location for saving
        val currentLocation = currentLocation
        if (currentLocation == null) {
            Log.w(TAG, "Cannot show save location modal - current location is null")
            Toast.makeText(this, "Location not available for saving", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Get session information for logging
        val session = sessionManager.getSession()
        val userId = session["userId"] as String?
        val sessionType = "user"
        val userName = session["userName"] as String? ?: "Unknown"
        val userEmail = session["email"] as String? ?: "no-email@user.com"
        
        Log.d(TAG, "Save location modal - Session type: $sessionType, UserId: $userId, UserName: $userName, UserEmail: $userEmail")
        
        // Create dialog for saving location
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_save_location, null)
        
        val etLocationName = dialogView.findViewById<TextInputEditText>(R.id.etLocationName)
        val etLocationDescription = dialogView.findViewById<TextInputEditText>(R.id.etLocationDescription)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSave)
        
        // Pre-fill with current address
        lifecycleScope.launch {
            try {
                val address = locationHelper.getAddressFromLocation(currentLocation)
                etLocationName.setText("Saved Location")
                etLocationDescription.setText(address)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting address for save location modal", e)
                etLocationName.setText("Saved Location")
                etLocationDescription.setText("Location at ${currentLocation.latitude}, ${currentLocation.longitude}")
            }
        }
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("💾 Save This Location")
            .setMessage("You've arrived at a location. Would you like to save it for future reference?")
            .setView(dialogView)
            .setCancelable(false)
            .create()
        
        btnCancel.setOnClickListener {
            Log.d(TAG, "User cancelled saving location - Session type: $sessionType, UserId: $userId")
            dialog.dismiss()
        }
        
        btnSave.setOnClickListener {
            val locationName = etLocationName.text.toString().trim()
            val locationDescription = etLocationDescription.text.toString().trim()
            
            if (locationName.isEmpty()) {
                Toast.makeText(this, "Please enter a location name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Log the saved location credentials
            Log.i(TAG, "=== LOCATION SAVED ===")
            Log.i(TAG, "Session Type: $sessionType")
            Log.i(TAG, "User ID: $userId")
            Log.i(TAG, "User Name: $userName")
            Log.i(TAG, "User Email: $userEmail")
            Log.i(TAG, "Location Name: $locationName")
            Log.i(TAG, "Location Description: $locationDescription")
            Log.i(TAG, "Location Coordinates: ${currentLocation.latitude}, ${currentLocation.longitude}")
            Log.i(TAG, "Timestamp: ${System.currentTimeMillis()}")
            Log.i(TAG, "=======================")
            
            // TODO: Implement Firebase save functionality here
            // For now, just show success message
            Toast.makeText(this, "Location saved successfully!", Toast.LENGTH_SHORT).show()
            
            Log.d(TAG, "Location saved successfully - Session type: $sessionType, UserId: $userId, Location: $locationName")
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    /**
     * Pin a location from PlacesActivity with automatic geofence creation
     */
    private fun pinLocationFromPlaces(location: LatLng, name: String, address: String, autoCreateGeofence: Boolean) {
        try {
            Log.d(TAG, "Pinning location from places: $name at $location")

            // Remove existing markers and polylines
            pinnedLocationMarker?.remove()
            currentPolyline?.remove()
            geofenceCircle?.remove()

            // Add marker with place name
            val title = "$name (${GeofenceHelper.getGeofenceRadius().toInt()}m geofence)"
            pinnedLocationMarker = mMap.addMarker(
                MarkerOptions()
                    .position(location)
                    .title(title)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)) // Red pin for destination
            )

            // Set pinned location
            pinnedLocation = location

            // Move camera to the location
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 16f))

            // Add geofence visualization
            addGeofenceVisualization(location, GeofenceHelper.getGeofenceRadius())

            // Update pinned location info in the UI
            updatePinnedLocationInfo(location)

            // Auto-create geofence if enabled in preferences
            if (autoCreateGeofence) {
                val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
                val autoGeofenceEnabled = sharedPrefs.getBoolean("auto_create_geofence", true) // Default to true
                
                if (autoGeofenceEnabled) {
                    // Create automatic geofence (passive mode)
                    geofenceHelper.addAutomaticGeofence(location)
                    Toast.makeText(
                        this,
                        "Location pinned with automatic geofence",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this,
                        "Location pinned. Tap the pin to start navigation.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            // Draw route if current location is available
            currentLocation?.let { current ->
                drawRealRoute(current, location, selectedTransportMode)
            }

            // Show modal with place information
            loadModalLocationData(location)
            showModal()

            Log.d(TAG, "Successfully pinned location: $name")

        } catch (e: Exception) {
            Log.e(TAG, "Error pinning location from places", e)
            Toast.makeText(this, "Error pinning location: ${e.message}", Toast.LENGTH_SHORT).show()
         }
    }

    // RealTimeLocationReceiver.LocationUpdateListener implementation
    override fun onLocationUpdate(latitude: Double, longitude: Double, accuracy: Float, timestamp: Long) {
        Log.d("$TAG $LOCATION_TAG", "🎆 DASHBOARD RECEIVED LOCATION UPDATE: lat=${String.format("%.6f", latitude)}, lng=${String.format("%.6f", longitude)}, accuracy=${String.format("%.1f", accuracy)}m, timestamp=$timestamp")
        
        runOnUiThread {
            try {
                // Create new location object (NEVER overwrite pinnedLocation)
                val newLocation = LatLng(latitude, longitude)
                Log.d("$TAG $LOCATION_TAG", "📍 Received new location, processing with 2-second delay system")
                
                // ENHANCED: Provide immediate UI updates for better responsiveness
                // Update coordinates immediately for real-time feedback
                val coordsText = "📍 ${String.format("%.4f", latitude)}, ${String.format("%.4f", longitude)} (±${accuracy.toInt()}m)"
                updateLocationUI(coordsText, "Location updated", "Getting address...")
                
                // Update current location immediately
                currentLocation = newLocation
                
                // User location is handled by Google Maps blue dot
                
                // Use the location change handling system for additional updates
                handleLocationChange(newLocation)
                
                // Enhanced real-time route polyline updates
                try {
                    if (pinnedLocation != null) {
                        // More responsive accuracy threshold
                        val adaptiveAccuracyThreshold = 100f // Increased threshold to allow more updates
                        
                        if (currentPolyline == null) {
                            // Always draw initial route
                            Log.d(TAG, "🗺️ Drawing initial route")
                            drawRealRoute(newLocation, pinnedLocation!!, selectedTransportMode)
                        } else {
                            val now = System.currentTimeMillis()
                            val movedMeters = lastRouteUpdateLocation?.let { prev ->
                                (locationHelper.calculateDistance(prev, newLocation) * 1000.0).toFloat()
                            } ?: Float.MAX_VALUE
                            
                            // Enhanced adaptive update strategy
                            val timeSinceLastUpdate = now - lastRouteUpdateTime
                            
                            // More responsive route update logic
                            val shouldUpdate = when {
                                // Excellent accuracy - update with small movements
                                accuracy <= 10f && movedMeters >= 3f -> {
                                    Log.d(TAG, "✨ Excellent accuracy route update: ${String.format("%.1f", movedMeters)}m moved")
                                    true
                                }
                                
                                // Good accuracy - update with moderate movements
                                accuracy <= 25f && (movedMeters >= 8f || timeSinceLastUpdate >= 10000L) -> {
                                    Log.d(TAG, "🎯 Good accuracy route update: ${String.format("%.1f", movedMeters)}m moved, ${timeSinceLastUpdate}ms elapsed")
                                    true
                                }
                                
                                // Medium accuracy - update with reasonable movements
                                accuracy <= 50f && (movedMeters >= 15f || timeSinceLastUpdate >= 15000L) -> {
                                    Log.d(TAG, "📍 Medium accuracy route update: ${String.format("%.1f", movedMeters)}m moved, ${timeSinceLastUpdate}ms elapsed")
                                    true
                                }
                                
                                // Force update periodically even with poor accuracy
                                timeSinceLastUpdate >= 20000L -> {
                                    Log.d(TAG, "⏰ Time-forced route update: ${timeSinceLastUpdate}ms elapsed")
                                    true
                                }
                                
                                // Large movement regardless of accuracy
                                movedMeters >= 30f -> {
                                    Log.d(TAG, "🚀 Large movement route update: ${String.format("%.1f", movedMeters)}m moved")
                                    true
                                }
                                
                                else -> {
                                    Log.d(TAG, "⏭️ Route update skipped: accuracy=${String.format("%.1f", accuracy)}m, moved=${String.format("%.1f", movedMeters)}m, time=${timeSinceLastUpdate}ms")
                                    false
                                }
                            }
                            
                            if (shouldUpdate) {
                                updateRoutePolyline()
                                lastRouteUpdateTime = now
                                lastRouteUpdateLocation = newLocation
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating route polyline", e)
                }
                
                // Update distance display
                try { updateDistanceDisplay() } catch (_: Exception) {}

                // Update geofence distance UI if present
                try { updateGeofenceDistanceInfo() } catch (_: Exception) {}
                
                // ENHANCED: Direct location UI update (no more adapter needed)
                Log.d("$TAG $LOCATION_TAG", "📋 Updating location UI directly...")
                Log.d("$TAG $DEBUG_TAG", "📊 Received: lat=${String.format("%.6f", latitude)}, lng=${String.format("%.6f", longitude)}, accuracy=${String.format("%.1f", accuracy)}m, time=${android.text.format.DateFormat.format("HH:mm:ss", timestamp)}")
                
                try {
                    // Stop any ongoing refresh cycle since we got a location
                    Log.d("$TAG $SERVICE_TAG", "🛑 Stopping refresh cycle due to new location...")
                    stopLocationRefreshCycle()
                    
                    // Create a Location object for processing
                    val location = Location("gps").apply {
                        this.latitude = latitude
                        this.longitude = longitude
                        this.accuracy = accuracy
                        this.time = timestamp
                    }
                    Log.d("$TAG $DEBUG_TAG", "🔧 Created Location object: ${location.provider}, time=${android.text.format.DateFormat.format("HH:mm:ss", location.time)}")
                    
                    // Update the last location
                    val previousLocation = lastLocation
                    lastLocation = location
                    Log.d("$TAG $DEBUG_TAG", "📊 Updated lastLocation: previous=${previousLocation != null}, new=${location != null}")
                    
                    // Update the UI directly with coordinates
                    val coordsText = "📍 ${String.format("%.4f", latitude)}, ${String.format("%.4f", longitude)} (±${accuracy.toInt()}m)"
                    Log.d("$TAG $UI_TAG", "🎨 Updating UI with coordinates: $coordsText")
                    updateLocationUI(coordsText, "Location updated", "Getting address...")
                    
                    // Try to get address for better user experience
                    Log.d("$TAG $SERVICE_TAG", "🏠 Starting address resolution...")
                    getAddressForLocation(location)
                    
                    Log.d("$TAG $LOCATION_TAG", "✅ Direct location UI update completed successfully")
                } catch (e: Exception) {
                    Log.e("$TAG $LOCATION_TAG", "❌ Error in direct location UI update", e)
                    Log.d("$TAG $DEBUG_TAG", "📊 Error context: lat=$latitude, lng=$longitude, accuracy=$accuracy")
                }
                
                Log.d(TAG, "✅ Real-time location update processing COMPLETE - Lat: ${String.format("%.6f", latitude)}, Lng: ${String.format("%.6f", longitude)}, Accuracy: ${String.format("%.1f", accuracy)}m")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating location in UI", e)
            }
        }
    }

    // Receiver interface callback for accuracy changes
    override fun onLocationAccuracyChanged(accuracy: Float, accuracyLevel: String) {
        try {
            Log.d(TAG, "Location accuracy changed: ${accuracy}m ($accuracyLevel)")
            // Optionally reflect accuracy in UI summary if available
            if (::tvCurrentLocationSummary.isInitialized) {
                // Keep existing text; could append accuracy info if needed
            }
        } catch (_: Exception) {}
    }
    
    override fun onDistanceUpdate(distance: Float, radius: Float, isInside: Boolean) {
        runOnUiThread {
            try {
                // Update distance display with real-time data if helper exists
                // Fallback to existing method
                updateDistanceDisplay(distance, radius, isInside)
                
                // Update geofence circle color based on status
                updateGeofenceCircle()
                
                // ENHANCED: Direct distance handling (no more adapter needed)
                Log.d(TAG, "📏 Distance update: ${distance}m, radius: ${radius}m, inside: $isInside")
                
                Log.d(TAG, "Real-time distance updated - Distance: ${distance}m, Radius: ${radius}m, Inside: $isInside")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating distance in UI", e)
            }
        }
    }

    // Implement enhanced geofence status callback from receiver interface
    override fun onGeofenceStatusChanged(isInside: Boolean, distance: Float) {
        runOnUiThread {
            try {
                Log.d(TAG, "Geofence status changed - Inside: $isInside, Distance: ${distance}m")
                try { updateGeofenceDistanceInfo() } catch (_: Exception) {}
                
                // ENHANCED: Direct geofence status handling (no more adapter needed)
                Log.d(TAG, "📍 Geofence status: inside=$isInside, distance=${distance}m")
                
                if (isInside && isNavigating) {
                    try { ensureArrivalAlarmIsTriggered() } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling geofence status change", e)
            }
        }
    }
    
    // Implement route update callback from receiver interface
    override fun onRouteUpdate(latitude: Double, longitude: Double, accuracy: Float, timestamp: Long) {
        runOnUiThread {
            try {
                Log.d(TAG, "🛣️ Route update received: lat=${String.format("%.6f", latitude)}, lng=${String.format("%.6f", longitude)}, accuracy=${String.format("%.1f", accuracy)}m")
                
                // Update current location if it's significantly different
                val newLocation = LatLng(latitude, longitude)
                val hasSignificantChange = currentLocation?.let { current ->
                    val distance = locationHelper.calculateDistance(current, newLocation) * 1000
                    distance >= 5.0f // Update if moved more than 5 meters
                } ?: true
                
                if (hasSignificantChange) {
                    Log.d(TAG, "🔄 Route update: Location changed significantly, updating route...")
                    
                    // Update current location
                    currentLocation = newLocation
                    
                    // Update route if we have a destination
                    pinnedLocation?.let { destination ->
                        updateRouteForLocationChange(newLocation, destination)
                    }
                    
                                    // User location is handled by Google Maps blue dot
                    
                    // Update geofence distance info
                    updateGeofenceDistanceInfo()
                    
                    Log.d(TAG, "✅ Route update completed successfully")
                } else {
                    Log.d(TAG, "⏭️ Route update: Location change too small, skipping route update")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error handling route update", e)
            }
        }
    }

    // Minimal stub to satisfy enhanced monitoring flow; alarms are triggered by receivers/services
    private fun ensureArrivalAlarmIsTriggered() { /* handled by GeofenceEventReceiver/BackgroundLocationService */ }

    /**
     * Update distance display with real-time data
     */
    private fun updateDistanceDisplay(distance: Float? = null, radius: Float? = null, isInside: Boolean? = null) {
        try {
            if (::tvDistance.isInitialized) {
                val currentDistance = distance ?: calculateDistanceToGeofence()
                val currentRadius = radius ?: GeofenceHelper.getGeofenceRadius()
                val currentIsInside = isInside ?: geofenceHelper.isUserInsideGeofence()
                
                val distanceString = if (currentDistance < 1000) {
                    "${currentDistance.toInt()}m"
                } else {
                    "%.1fkm".format(currentDistance / 1000)
                }
                
                tvDistance.text = distanceString
                
                // Update background color and detailed geofence info based on proximity
                if (::geofenceDistanceInfo.isInitialized) {
                    val color = when {
                        currentIsInside -> ContextCompat.getColor(this, R.color.success_green)
                        currentDistance <= currentRadius * 1.5 -> ContextCompat.getColor(this, R.color.warning_orange)
                        else -> ContextCompat.getColor(this, R.color.primary_brown)
                    }
                    geofenceDistanceInfo.setBackgroundColor(color)

                    if (::tvGeofenceStatus.isInitialized && ::tvGeofenceDistance.isInitialized) {
                        if (currentIsInside) {
                            tvGeofenceStatus.text = "🎯 Inside destination zone"
                            tvGeofenceDistance.text = "Inside geofence zone"
                        } else {
                            val metersLeft = (currentDistance - currentRadius).coerceAtLeast(0f)
                            tvGeofenceStatus.text = "📍 Outside destination zone"
                            tvGeofenceDistance.text = if (metersLeft < 1000f) {
                                "Meters to zone: ${metersLeft.toInt()}m"
                            } else {
                                "Meters to zone: ${String.format("%.1fkm", metersLeft / 1000f)}"
                            }
                        }
                    }
                }
                
                Log.d(TAG, "Distance display updated - Distance: ${currentDistance}m, Inside: $currentIsInside")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating distance display", e)
        }
    }


    

    


    /**
     * Determines if the route should be updated based on time, distance, speed and accuracy
     * This helps stabilize updates when GPS jitters or accuracy is poor
     */
    /**
     * Enhanced route update logic with real-time responsiveness
     */
    private fun shouldUpdateRoute(timeSinceLastUpdate: Long, movedMeters: Float, speed: Float, accuracy: Float): Boolean {
        // Force update if it's been too long (20 seconds)
        if (timeSinceLastUpdate >= 20000L) {
            Log.d(TAG, "🔄 Forcing route update after interval: ${timeSinceLastUpdate}ms")
            return true
        }
        
        // Enhanced adaptive thresholds based on accuracy and movement
        val shouldUpdate = when {
            // Excellent accuracy (≤8m) - very responsive
            accuracy <= 8f -> {
                movedMeters >= 2f || timeSinceLastUpdate >= 5000L
            }
            
            // Good accuracy (8-20m) - responsive
            accuracy <= 20f -> {
                movedMeters >= 5f || timeSinceLastUpdate >= 8000L
            }
            
            // Medium accuracy (20-40m) - moderate responsiveness
            accuracy <= 40f -> {
                movedMeters >= 10f || timeSinceLastUpdate >= 12000L
            }
            
            // Lower accuracy (40-80m) - conservative but still responsive
            accuracy <= 80f -> {
                movedMeters >= 20f || timeSinceLastUpdate >= 15000L
            }
            
            // Poor accuracy (>80m) - very conservative
            else -> {
                movedMeters >= 40f || timeSinceLastUpdate >= 18000L
            }
        }
        
        // Override with speed considerations
        if (speed > 1.0f) { // If moving faster than walking speed
            val speedBasedUpdate = when {
                speed > 8f -> movedMeters >= 15f // Driving speed
                speed > 3f -> movedMeters >= 8f  // Cycling speed
                else -> movedMeters >= 5f        // Fast walking
            }
            
            if (speedBasedUpdate && timeSinceLastUpdate >= 3000L) {
                Log.d(TAG, "🏃 Speed-based route update: speed=${String.format("%.1f", speed)}m/s, distance=${String.format("%.1f", movedMeters)}m")
                return true
            }
        }
        
        if (shouldUpdate) {
            Log.d(TAG, "✅ Route update criteria met: time=${timeSinceLastUpdate}ms, distance=${String.format("%.1f", movedMeters)}m, speed=${String.format("%.1f", speed)}m/s, accuracy=${String.format("%.1f", accuracy)}m")
        }
        
        return shouldUpdate
    }
    
    /**
     * Update route polyline with real-time location
     */
    private fun updateRoutePolyline() {
        try {
            if (currentLocation != null && pinnedLocation != null && currentPolyline != null) {
                // Recalculate route with current location using selected transport mode
                lifecycleScope.launch {
                    try {
                        val apiMode = when (selectedTransportMode) {
                            TransportMode.WALKING -> "walking"
                            TransportMode.MOTORCYCLE -> "bicycling"
                            TransportMode.CAR -> "driving"
                        }
                        
                        Log.d(TAG, "Updating route with transport mode: $apiMode")
                        val newRoute = locationHelper.getRealRoute(currentLocation!!, pinnedLocation!!, apiMode)
                        
                        if (newRoute != null && newRoute.polylinePoints.isNotEmpty()) {
                            // Smoothly update polyline points and info
                            runOnUiThread {
                                currentPolyline?.points = newRoute.polylinePoints
                                updateRealRouteInfo(newRoute)
                                
                                // Update route color based on transport mode
                                val routeColor = when (selectedTransportMode) {
                                    TransportMode.WALKING -> R.color.success
                                    TransportMode.MOTORCYCLE -> R.color.warning
                                    TransportMode.CAR -> R.color.primary_brown
                                }
                                currentPolyline?.color = ContextCompat.getColor(this@DashboardActivity, routeColor)
                                
                                Log.d(TAG, "Route polyline and info updated with real-time location (${apiMode})")
                            }
                        } else {
                            // Fallback to straight line update when API route unavailable
                            Log.w(TAG, "Real route unavailable during update; drawing straight line fallback")
                            runOnUiThread {
                                drawStraightLineRoute(currentLocation!!, pinnedLocation!!)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating route polyline", e)
                        runOnUiThread {
                            // Fallback to straight line on error
                            drawStraightLineRoute(currentLocation!!, pinnedLocation!!)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating route polyline", e)
        }
    }

    /**
     * Calculate distance to geofence center
     */
    private fun calculateDistanceToGeofence(): Float {
        return try {
            val currentLoc = currentLocation
            val geofenceLocation = geofenceHelper.getCurrentGeofenceLocation()
            if (currentLoc != null && geofenceLocation != null) {
                locationHelper.calculateDistance(currentLoc, geofenceLocation).toFloat() * 1000 // Convert to meters
            } else {
                0f
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating distance to geofence", e)
            0f
        }
    }

    /**
     * Update geofence circle on map
     */
    private fun updateGeofenceCircle() {
        try {
            if (geofenceCircle != null && geofenceHelper.getCurrentGeofenceLocation() != null) {
                val geofenceLocation = geofenceHelper.getCurrentGeofenceLocation()!!
                val radius = GeofenceHelper.getGeofenceRadius()
                val isInside = geofenceHelper.isUserInsideGeofence()
                
                // Update circle radius and color
                geofenceCircle?.radius = radius.toDouble()
                geofenceCircle?.fillColor = if (isInside) {
                    ContextCompat.getColor(this, R.color.success_green_alpha)
                } else {
                    ContextCompat.getColor(this, R.color.primary_brown_alpha)
                }
                
                Log.d(TAG, "Geofence circle updated - Radius: ${radius}m, Inside: $isInside")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating geofence circle", e)
        }
    }
    
    /**
     * Check if voice announcements are enabled
     */
    private fun isVoiceAnnouncementEnabled(): Boolean {
        return try {
            val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
            sharedPrefs.getBoolean("voice_announcements", false)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking voice announcement setting", e)
            false
        }
    }



    // ========================================
    // ENHANCED: Direct location handling (no more adapter needed)
    // ========================================

    // All location updates now come directly from RealTimeLocationReceiver

    // All navigation and geofence updates now come directly from RealTimeLocationReceiver
    
    /**
     * DEBUG: Get comprehensive location system status
     * Call this method to debug location issues
     */
    fun debugLocationSystem() {
        try {
            Log.d("$TAG $DEBUG_TAG", "🔍 ===== LOCATION SYSTEM DEBUG REPORT =====")
            
            // Service status
            val isServiceRunning = BackgroundLocationService.isServiceRunning(this)
            Log.d("$TAG $DEBUG_TAG", "🔧 BackgroundLocationService: running=$isServiceRunning")
            
            // Location state
            Log.d("$TAG $DEBUG_TAG", "📍 Location state: lastLocation=${lastLocation != null}, isLocationLoading=$isLocationLoading")
            lastLocation?.let { loc ->
                Log.d("$TAG $DEBUG_TAG", "📊 Last location: lat=${String.format("%.6f", loc.latitude)}, lng=${String.format("%.6f", loc.longitude)}, accuracy=${String.format("%.1f", loc.accuracy)}m, time=${android.text.format.DateFormat.format("HH:mm:ss", loc.time)}")
            }
            
            // Refresh cycle state
            Log.d("$TAG $DEBUG_TAG", "🔄 Refresh cycle: count=$locationRefreshCount, handler=${locationRefreshHandler != null}, runnable=${locationRefreshRunnable != null}")
            
            // UI component status
            Log.d("$TAG $DEBUG_TAG", "🎨 UI components: summary=${tvCurrentLocationSummary != null}, address=${tvCurrentLocationAddress != null}, coordinates=${tvCurrentLocationCoordinates != null}, collapsed=${tvCurrentLocationCoordinatesCollapsed != null}")
            
            // Current UI text values
            Log.d("$TAG $DEBUG_TAG", "📝 Current UI text: summary='${tvCurrentLocationSummary?.text}', address='${tvCurrentLocationAddress?.text}', coordinates='${tvCurrentLocationCoordinates?.text}', collapsed='${tvCurrentLocationCoordinatesCollapsed?.text}'")
            
            // Background service location
            try {
                val serviceLocation = BackgroundLocationService.getLastLocation(this)
                if (serviceLocation != null) {
                    Log.d("$TAG $DEBUG_TAG", "🔧 Service location: lat=${String.format("%.6f", serviceLocation.latitude)}, lng=${String.format("%.6f", serviceLocation.longitude)}, accuracy=${String.format("%.1f", serviceLocation.accuracy)}m")
                } else {
                    Log.d("$TAG $DEBUG_TAG", "🔧 Service location: NULL")
                }
            } catch (e: Exception) {
                Log.d("$TAG $DEBUG_TAG", "📊 Service location: Error - ${e.message}")
            }
            
            Log.d("$TAG $DEBUG_TAG", "🔍 ===== END DEBUG REPORT =====")
            
        } catch (e: Exception) {
            Log.e("$TAG $DEBUG_TAG", "❌ Error in debugLocationSystem", e)
        }
    }
    
    /**
     * MANUAL: Force a location update from the background service
     * Use this when the broadcast receiver isn't working
     */
    fun forceLocationUpdate() {
        try {
            Log.d("$TAG $SERVICE_TAG", "🚀 Manually forcing location update...")
            
            // Try to get location directly from service
            val serviceLocation = BackgroundLocationService.getLastLocation(this)
            if (serviceLocation != null) {
                Log.d("$TAG $LOCATION_TAG", "✅ Got location directly from service")
                onLocationUpdate(serviceLocation.latitude, serviceLocation.longitude, serviceLocation.accuracy, serviceLocation.time)
            } else {
                Log.w("$TAG $SERVICE_TAG", "⚠️ Service returned null location")
                
                // Try to restart the service
                Log.d("$TAG $SERVICE_TAG", "🔄 Attempting to restart location service...")
                // ENHANCED: Don't stop service immediately, just restart it
                android.os.Handler(Looper.getMainLooper()).postDelayed({
                    BackgroundLocationService.startService(this)
                    Log.d("$TAG $SERVICE_TAG", "🔄 Location service restarted")
                }, 1000)
            }
            
            // Also try to send a broadcast to trigger update
            try {
                val intent = Intent("com.example.gzingapp.FORCE_LOCATION_UPDATE")
                sendBroadcast(intent)
                Log.d("$TAG $DEBUG_TAG", "📡 Sent manual force location broadcast")
            } catch (e: Exception) {
                Log.e("$TAG $SERVICE_TAG", "❌ Error sending manual broadcast", e)
            }
            
        } catch (e: Exception) {
            Log.e("$TAG $SERVICE_TAG", "❌ Error in forceLocationUpdate", e)
        }
    }

    /**
     * Handle location changes with real-time updates for responsive UI
     */
    private fun handleLocationChange(newLocation: LatLng) {
        // Cancel any pending location change updates
        locationChangeHandler?.removeCallbacksAndMessages(null)
        
        // Check if location has actually changed (reduced threshold for more responsive updates)
        val hasSignificantChange = lastProcessedLocation?.let { last ->
            val distance = locationHelper.calculateDistance(last, newLocation) * 1000 // Convert to meters
            distance >= 1.0f // Only update if moved more than 1 meter (reduced from 5m)
        } ?: true // Always update if no previous location
        
        if (hasSignificantChange) {
            Log.d(TAG, "📍 Location changed significantly, scheduling update in ${LOCATION_UPDATE_DELAY}ms")
            
            // Schedule the location update after a short delay for smooth UI updates
            locationChangeHandler = Handler(Looper.getMainLooper())
            locationChangeRunnable = Runnable {
                try {
                    Log.d(TAG, "🔄 Processing delayed location update: $newLocation")
                    
                    // Update the processed location
                    lastProcessedLocation = newLocation
                    
                    // Update current location
                    currentLocation = newLocation
                    
                    // Update UI components
                    updateCurrentLocationInfo(newLocation)
                    // User location is handled by Google Maps blue dot
                    
                    // Update route if we have a destination
                    pinnedLocation?.let { destination ->
                        updateRouteForLocationChange(newLocation, destination)
                    }
                    
                    // Update geofence distance info
                    updateGeofenceDistanceInfo()
                    
                    Log.d(TAG, "✅ Delayed location update completed")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error in delayed location update", e)
                }
            }
            
            locationChangeHandler?.postDelayed(locationChangeRunnable!!, LOCATION_UPDATE_DELAY)
        } else {
            Log.d(TAG, "⏭️ Location change too small (${String.format("%.1f", lastProcessedLocation?.let { last -> locationHelper.calculateDistance(last, newLocation) * 1000 } ?: 0f)}m), skipping update")
        }
    }
    
    /**
     * Update route when location changes
     */
    private fun updateRouteForLocationChange(newLocation: LatLng, destination: LatLng) {
        try {
            // Only update route if we have a significant change
            val shouldUpdateRoute = lastRouteUpdateLocation?.let { last ->
                val distance = locationHelper.calculateDistance(last, newLocation) * 1000
                distance >= 10.0f // Update route if moved more than 10 meters
            } ?: true
            
            if (shouldUpdateRoute) {
                Log.d(TAG, "🛣️ Updating route due to location change")
                drawRealRoute(newLocation, destination, selectedTransportMode)
                lastRouteUpdateLocation = newLocation
                lastRouteUpdateTime = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating route for location change", e)
        }
    }

    /**
     * Clean up location change handlers
     */
    private fun cleanupLocationChangeHandlers() {
        locationChangeHandler?.removeCallbacksAndMessages(null)
        locationChangeRunnable = null
    }

    /**
     * Initialize location change tracking system
     */
    private fun initializeLocationChangeSystem() {
        // Initialize handlers
        locationChangeHandler = Handler(Looper.getMainLooper())
        
        // Set initial processed location if we have one
        currentLocation?.let { location ->
            lastProcessedLocation = location
            Log.d(TAG, "📍 Location change system initialized with current location: $location")
        }
        
        Log.d(TAG, "🔄 Location change tracking system initialized")
    }

    /**
     * Force immediate location update (bypasses delay for real-time responsiveness)
     */
    fun forceImmediateLocationUpdate() {
        try {
            Log.d(TAG, "🚀 Force immediate location update requested")
            
            // Get the latest location from the service
            val serviceLocation = BackgroundLocationService.getLastLocation(this)
            if (serviceLocation != null) {
                Log.d(TAG, "✅ Got service location, updating immediately")
                
                // Update UI immediately without delay
                val newLocation = LatLng(serviceLocation.latitude, serviceLocation.longitude)
                currentLocation = newLocation
                lastProcessedLocation = newLocation
                
                // Update UI components immediately
                updateCurrentLocationInfo(newLocation)
                // User location is handled by Google Maps blue dot
                
                // Update route if we have a destination
                pinnedLocation?.let { destination ->
                    updateRouteForLocationChange(newLocation, destination)
                }
                
                // Update geofence distance info
                updateGeofenceDistanceInfo()
                
                Log.d(TAG, "✅ Immediate location update completed")
            } else {
                Log.w(TAG, "⚠️ No service location available for immediate update")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in immediate location update", e)
        }
    }
    
    // ENHANCED: Periodic location refresh for real-time updates while app is in foreground
    private var periodicLocationRefreshHandler: Handler? = null
    private var periodicLocationRefreshRunnable: Runnable? = null
    private val PERIODIC_LOCATION_REFRESH_INTERVAL = 1000L // 1 second for real-time updates
    
    /**
     * Start periodic location refresh for real-time updates while app is in foreground
     */
    private fun startPeriodicLocationRefresh() {
        try {
            Log.d(TAG, "🔄 Starting periodic location refresh for real-time updates...")
            
            // Stop any existing refresh cycle
            stopPeriodicLocationRefresh()
            
            // Initialize handler
            periodicLocationRefreshHandler = Handler(Looper.getMainLooper())
            
            // Create refresh runnable
            periodicLocationRefreshRunnable = object : Runnable {
                override fun run() {
                    try {
                        // Get latest location from service
                        val serviceLocation = BackgroundLocationService.getLastLocation(this@DashboardActivity)
                        if (serviceLocation != null) {
                            val newLocation = LatLng(serviceLocation.latitude, serviceLocation.longitude)
                            
                            // Check if location has changed significantly
                            val hasChanged = lastProcessedLocation?.let { last ->
                                val distance = locationHelper.calculateDistance(last, newLocation) * 1000
                                distance >= 0.5f // Update if moved more than 0.5 meters (very responsive)
                            } ?: true
                            
                            if (hasChanged) {
                                Log.d(TAG, "🔄 Periodic refresh: Location changed, updating UI...")
                                
                                // Update location immediately
                                currentLocation = newLocation
                                lastProcessedLocation = newLocation
                                
                                                // Update UI components
                updateCurrentLocationInfo(newLocation)
                // User location is handled by Google Maps blue dot
                
                // Update route if we have a destination
                                pinnedLocation?.let { destination ->
                                    updateRouteForLocationChange(newLocation, destination)
                                }
                                
                                // Update geofence distance info
                                updateGeofenceDistanceInfo()
                                
                                // Enhanced: Check geofence status with real-time location
                                try {
                                    val isInside = geofenceHelper.checkGeofenceStatusWithLocation(newLocation, serviceLocation.accuracy)
                                    Log.d(TAG, "🎯 Periodic refresh: Geofence status checked - inside=$isInside")
                                } catch (e: Exception) {
                                    Log.e(TAG, "❌ Error checking geofence status in periodic refresh", e)
                                }
                                
                                Log.d(TAG, "✅ Periodic refresh: UI updated successfully")
                            }
                        }
                        
                        // Schedule next refresh
                        periodicLocationRefreshHandler?.postDelayed(this, PERIODIC_LOCATION_REFRESH_INTERVAL)
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error in periodic location refresh", e)
                        // Continue refresh cycle despite error
                        periodicLocationRefreshHandler?.postDelayed(this, PERIODIC_LOCATION_REFRESH_INTERVAL)
                    }
                }
            }
            
            // Start the first refresh
            periodicLocationRefreshHandler?.post(periodicLocationRefreshRunnable!!)
            Log.d(TAG, "✅ Periodic location refresh started successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting periodic location refresh", e)
        }
    }
    
    /**
     * Stop periodic location refresh
     */
    private fun stopPeriodicLocationRefresh() {
        try {
            Log.d(TAG, "🛑 Stopping periodic location refresh...")
            
            periodicLocationRefreshRunnable?.let { runnable ->
                periodicLocationRefreshHandler?.removeCallbacks(runnable)
            }
            periodicLocationRefreshRunnable = null
            periodicLocationRefreshHandler = null
            
            Log.d(TAG, "✅ Periodic location refresh stopped successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping periodic location refresh", e)
        }
    }
    
    /**
     * Refresh geofence settings if they were changed from another activity
     */
    private fun refreshGeofenceSettingsIfNeeded() {
        try {
            val currentRadius = GeofenceHelper.getGeofenceRadius()
            val savedRadius = GeofenceHelper.loadGeofenceRadiusFromPreferences(this)
            
            if (currentRadius != savedRadius) {
                Log.d(TAG, "🔄 Geofence radius changed from ${currentRadius}m to ${savedRadius}m, updating...")
                
                // Update the radius
                GeofenceHelper.setGeofenceRadius(savedRadius)
                
                // Update existing geofence if one is active
                if (geofenceHelper.hasGeofence()) {
                    geofenceHelper.updateGeofenceRadius()
                    updateGeofenceCircle()
                    Log.d(TAG, "✅ Existing geofence updated with new radius")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error refreshing geofence settings", e)
        }
    }
}