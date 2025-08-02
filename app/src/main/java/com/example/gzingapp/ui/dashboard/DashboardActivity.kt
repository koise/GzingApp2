package com.example.gzingapp.ui.dashboard

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
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
import com.example.gzingapp.ui.auth.LoginActivity
import com.example.gzingapp.ui.settings.SettingsActivity
import com.example.gzingapp.services.NavigationHelper
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

class DashboardActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener, OnMapReadyCallback {

    companion object {
        private const val TAG = "DashboardActivity"
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_account -> {
                if (isNavigating) {
                    Toast.makeText(this, getString(R.string.drawer_locked_message), Toast.LENGTH_SHORT).show()
                    return false
                }
                Toast.makeText(this, "Account", Toast.LENGTH_SHORT).show()
            }
            R.id.menu_settings -> {
                // Check if navigation is active
                if (isNavigating) {
                    Toast.makeText(this, getString(R.string.drawer_locked_message), Toast.LENGTH_SHORT).show()
                    return false
                }
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            R.id.menu_history -> {
                if (isNavigating) {
                    Toast.makeText(this, getString(R.string.drawer_locked_message), Toast.LENGTH_SHORT).show()
                    return false
                }
                Toast.makeText(this, "History", Toast.LENGTH_SHORT).show()
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

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var toggle: ActionBarDrawerToggle

    private lateinit var authService: AuthService
    private lateinit var sessionManager: SessionManager
    private lateinit var notificationService: NotificationService
    private lateinit var locationHelper: LocationHelper
    private lateinit var geofenceHelper: GeofenceHelper
    private lateinit var navigationHelper: NavigationHelper

    // Map and location related variables
    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentMarker: Marker? = null
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

    // State variables
    private var selectedTransportMode: TransportMode = TransportMode.WALKING

    // Navigation state is now handled by NavigationHelper
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

    // Animation objects for navigation status
    private var navigationStatusAnimation: ObjectAnimator? = null

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

    // Temporary storage for navigation location during permission request
    private var pinnedLocationToNavigate: LatLng? = null

    // Location permission request
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        locationPermissionGranted = allGranted
        if (allGranted) {
            updateLocationUI()
            getDeviceLocation()
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
                    // Show confirmation dialog when navigating
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
        setupModal()
        updateNavigationHeader()
        setupNotificationButtons()
        setupMap()
        loadGeofenceSettings()
        checkGooglePlayServices()

        // Register back press callback
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        // Check if user is logged in
        if (!sessionManager.isLoggedIn()) {
            navigateToLogin()
        }

        // Handle alarm stopped intent
        handleAlarmStoppedIntent()

        // Check navigation state on startup
        checkNavigationState()
    }

    private fun initializeServices() {
        authService = AuthService()
        sessionManager = SessionManager(this)
        notificationService = NotificationService(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationHelper = LocationHelper(this)
        geofenceHelper = GeofenceHelper(this)
        navigationHelper = NavigationHelper(this) // Initialize NavigationHelper
    }

    private fun checkNavigationState() {
        // Check if navigation was active when activity was recreated
        if (navigationHelper.isNavigationActive()) {
            Log.d(TAG, "Navigation was active, restoring state")

            // Get current destination
            val destination = navigationHelper.getCurrentDestination()
            if (destination != null) {
                pinnedLocation = destination

                // Restore navigation UI
                updateNavigationUI()
                updateDrawerState()
                updateToolbarState()
                addNavigationStateVisualFeedback()

                // Restore map markers and geofence visualization
                lifecycleScope.launch {
                    delay(1000) // Wait for map to be ready
                    restoreNavigationVisualization(destination)
                }

                Toast.makeText(this, "Navigation resumed", Toast.LENGTH_SHORT).show()
            } else {
                Log.w(TAG, "Navigation active but no destination found, stopping navigation")
                navigationHelper.stopNavigation()
            }
        }
    }

    private fun restoreNavigationVisualization(destination: LatLng) {
        if (::mMap.isInitialized) {
            // Add marker
            currentMarker?.remove()
            currentMarker = mMap.addMarker(
                MarkerOptions()
                    .position(destination)
                    .title("Navigation Destination")
            )

            // Add geofence visualization
            val radius = GeofenceHelper.getGeofenceRadius()
            addGeofenceVisualization(destination, radius)

            // Update pinned location info
            updatePinnedLocationInfo(destination)

            // Draw route if current location is available
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

        // Set toolbar as ActionBar
        setSupportActionBar(toolbar)
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
    }

    private fun setupModal() {
        // Modal views
        modalOverlay = findViewById(R.id.modal_overlay)
        modalCard = findViewById(R.id.modal_card)
        btnCloseModal = findViewById(R.id.btn_close_modal)
        btnCancel = findViewById(R.id.btn_cancel)
        btnStartNavigation = findViewById(R.id.btn_start_navigation)
        tvNavigationText = findViewById(R.id.tv_navigation_text)
        ivNavigationIcon = findViewById(R.id.iv_navigation_icon)

        // Modal location info views
        tvModalLocationAddress = findViewById(R.id.tv_location_address)
        tvModalLocationDistance = findViewById(R.id.tv_location_distance)
        tvModalTrafficCondition = findViewById(R.id.tv_traffic_condition)

        // Transportation views
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
        // Modal close buttons
        modalOverlay.setOnClickListener { hideModal() }
        btnCloseModal.setOnClickListener { hideModal() }
        btnCancel.setOnClickListener { hideModal() }

        // Transportation mode selection
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

        // Navigation button
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

        // Animate modal entrance
        val translateY = ObjectAnimator.ofFloat(modalCard, "translationY", 100f, 0f)
        val alpha = ObjectAnimator.ofFloat(modalCard, "alpha", 0f, 1f)

        AnimatorSet().apply {
            playTogether(translateY, alpha)
            duration = 300
            start()
        }
    }

    private fun hideModal() {
        // Animate modal exit
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
        // Reset transport selection to default
        selectedTransportMode = TransportMode.WALKING
        updateTransportSelection(TransportMode.WALKING)

        // Reset navigation button state
        updateNavigationButton()

        // Clear any loading states
        progressNavigation.visibility = View.GONE
        btnToggleNavigation.isEnabled = true

        // Clear temp storage
        pinnedLocationToNavigate = null
    }

    // Updated navigation methods using NavigationHelper
    private fun startNavigation() {
        pinnedLocation?.let { location ->
            Log.d(TAG, "Starting navigation to: $location")

            // Show loading state
            progressNavigation.visibility = View.VISIBLE
            btnToggleNavigation.isEnabled = false
            tvNavigationText.text = "Starting Navigation..."
            tvNavigationText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))

            // Store location for permission callback
            pinnedLocationToNavigate = location

            // Check background location permission first
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
        navigationHelper.startNavigation(
            destination = destination,
            onSuccess = {
                Log.d(TAG, "Navigation started successfully!")

                // Update UI state
                runOnUiThread {
                    hideModal()
                    progressNavigation.visibility = View.GONE
                    btnToggleNavigation.isEnabled = true

                    // Update navigation UI
                    updateNavigationUI()
                    updateDrawerState()
                    updateToolbarState()
                    addNavigationStateVisualFeedback()

                    // Add visual feedback on map
                    addGeofenceVisualization(destination, GeofenceHelper.getGeofenceRadius())

                    Toast.makeText(
                        this@DashboardActivity,
                        "Navigation started! You'll get an alarm when you arrive.",
                        Toast.LENGTH_SHORT
                    ).show()

                    Log.d(TAG, "Navigation UI updated successfully")
                }
            },
            onFailure = { error ->
                Log.e(TAG, "Failed to start navigation", error)

                runOnUiThread {
                    progressNavigation.visibility = View.GONE
                    btnToggleNavigation.isEnabled = true
                    updateNavigationButton()

                    // Determine appropriate fallback based on error
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

                    // Try fallback navigation
                    startNavigationWithoutGeofence(destination)
                }
            }
        )
    }

    private fun startNavigationWithoutGeofence(destination: LatLng) {
        Log.d(TAG, "Starting fallback navigation without geofence")

        // For now, just set navigation state without geofence
        // You could implement location-based monitoring here as an alternative

        hideModal()
        progressNavigation.visibility = View.GONE
        btnToggleNavigation.isEnabled = true

        // Manual navigation state management for fallback
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

                runOnUiThread {
                    progressNavigation.visibility = View.GONE
                    btnToggleNavigation.isEnabled = true

                    // Update UI state
                    updateNavigationUI()
                    updateDrawerState()
                    updateToolbarState()

                    // Remove visual feedback
                    navigationStatusAnimation?.cancel()
                    geofenceCircle?.remove()

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

                    // Still update UI since navigation is considered stopped
                    updateNavigationUI()
                    updateDrawerState()
                    updateToolbarState()

                    navigationStatusAnimation?.cancel()
                    geofenceCircle?.remove()

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

        if (navigationHelper.resumeNavigation()) {
            updateNavigationUI()
            updateDrawerState()
            updateToolbarState()
            addNavigationStateVisualFeedback()

            Toast.makeText(this, "Navigation resumed", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Cannot resume navigation - no destination set", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadModalLocationData(latLng: LatLng) {
        lifecycleScope.launch {
            try {
                // Load address
                val address = locationHelper.getAddressFromLocation(latLng)
                tvModalLocationAddress.text = address

                // Calculate distance if user location is available
                currentLocation?.let { userLoc ->
                    val distance = locationHelper.calculateDistance(userLoc, latLng)
                    tvModalLocationDistance.text =
                        "Distance: ${locationHelper.formatDistance(distance)}"
                } ?: run {
                    tvModalLocationDistance.text = "Distance: Calculating..."
                }

                // Load traffic condition
                val trafficCondition = locationHelper.estimateTrafficCondition()
                tvModalTrafficCondition.text = trafficCondition
                updateModalTrafficColor(trafficCondition)

                // Calculate travel times for all modes
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
                        // Get travel times for all transport modes
                        val travelTimes =
                            locationHelper.getTravelTimesForAllModes(userLoc, location)

                        // Update UI with real times or fallback to estimates
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
                        // Fallback to estimated times
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

                // Calculate times for each transport mode with different multipliers
                val walkingTime = locationHelper.estimateTravelTime(distance * 2.5, "Light")
                val motorcycleTime =
                    locationHelper.estimateTravelTime(distance * 0.8, trafficCondition)
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
        // Reset all selections
        transportWalking.isSelected = false
        transportMotorcycle.isSelected = false
        transportCar.isSelected = false

        // Set selected mode
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
        // Stop any existing animation
        navigationStatusAnimation?.cancel()

        // Update location card navigation status
        if (isNavigating) {
            navigationStatusSection.visibility = View.VISIBLE
            navigationInstructions.visibility = View.VISIBLE
            tvNavigationStatus.text = getString(R.string.navigation_active)
            tvNavigationStatus.setTextColor(ContextCompat.getColor(this, R.color.navigation_active))
            btnToggleNavigation.text = getString(R.string.stop_navigation)
            btnToggleNavigation.setBackgroundColor(
                ContextCompat.getColor(
                    this,
                    R.color.navigation_error
                )
            )
            tvNoPinMessage.visibility = View.GONE
        } else {
            navigationStatusSection.visibility = View.GONE
            navigationInstructions.visibility = View.GONE
            btnToggleNavigation.text = getString(R.string.start_navigation)
            btnToggleNavigation.setBackgroundColor(
                ContextCompat.getColor(
                    this,
                    R.color.primary_brown
                )
            )

            if (pinnedLocation == null) {
                tvNoPinMessage.visibility = View.VISIBLE
            }
        }

        // Update modal navigation button
        updateNavigationButton()
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
        // Enable/disable map click based on navigation state
        if (::mMap.isInitialized) {
            if (isNavigating) {
                // Disable map clicking when navigating
                mMap.setOnMapClickListener {
                    Toast.makeText(
                        this,
                        getString(R.string.navigation_locked_message),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                // Disable map controls for cleaner navigation experience
                mMap.uiSettings.isZoomControlsEnabled = false
                mMap.uiSettings.isCompassEnabled = false
                mMap.uiSettings.isMapToolbarEnabled = false

            } else {
                // Enable normal map interaction
                mMap.setOnMapClickListener { latLng ->
                    addOrUpdateMarkerWithGeofence(latLng)
                    updatePinnedLocationInfo(latLng)

                    currentLocation?.let { current ->
                        drawRealRoute(current, latLng, selectedTransportMode)
                    }

                    // Show modal with real route data
                    pinnedLocation = latLng
                    showModal()
                    loadModalLocationData(latLng)
                }

                // Re-enable map controls
                mMap.uiSettings.isZoomControlsEnabled = true
                mMap.uiSettings.isCompassEnabled = true
                mMap.uiSettings.isMapToolbarEnabled = true
            }
        }
    }

    private fun updateDrawerState() {
        if (isNavigating) {
            // Disable drawer opening during navigation
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            toggle.isDrawerIndicatorEnabled = false

            // Change toolbar title to show navigation status
            supportActionBar?.title = "Navigating..."

            // Optionally disable the hamburger menu icon
            supportActionBar?.setDisplayHomeAsUpEnabled(false)

        } else {
            // Re-enable drawer
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            toggle.isDrawerIndicatorEnabled = true

            // Restore normal title
            supportActionBar?.title = getString(R.string.app_name)

            // Re-enable hamburger menu
            supportActionBar?.setDisplayHomeAsUpEnabled(false)
            toggle.syncState()
        }
    }

    private fun updateToolbarState() {
        if (isNavigating) {
            // Change toolbar color to indicate navigation mode
            toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.navigation_active))

            // Add navigation indicator to toolbar
            supportActionBar?.subtitle = getString(R.string.navigation_active)

        } else {
            // Restore normal toolbar color
            toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.primary_brown))

            // Remove subtitle
            supportActionBar?.subtitle = null
        }
    }

    private fun addNavigationStateVisualFeedback() {
        if (isNavigating && ::tvNavigationStatus.isInitialized) {
            // Add a subtle pulse animation to the navigation status
            navigationStatusAnimation =
                ObjectAnimator.ofFloat(tvNavigationStatus, "alpha", 1f, 0.5f, 1f)
            navigationStatusAnimation?.apply {
                duration = 1500
                repeatCount = ObjectAnimator.INFINITE
                start()
            }
        }
    }

    private fun showStopNavigationDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Stop Navigation")
            .setMessage(getString(R.string.navigation_confirm_exit))
            .setPositiveButton(getString(R.string.stop_and_exit)) { _, _ ->
                stopNavigation()
                // Small delay to ensure navigation stops before exiting
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

    /**
     * Draw real road route using Google Directions API
     */
    private fun drawRealRoute(
        start: LatLng,
        end: LatLng,
        transportMode: TransportMode = selectedTransportMode
    ) {
        lifecycleScope.launch {
            try {
                // Show loading state
                Toast.makeText(this@DashboardActivity, "Loading route...", Toast.LENGTH_SHORT)
                    .show()

                // Convert transport mode to API string
                val apiMode = when (transportMode) {
                    TransportMode.WALKING -> "walking"
                    TransportMode.MOTORCYCLE -> "bicycling" // Use bicycling as closest option for motorcycle
                    TransportMode.CAR -> "driving"
                }

                // Get real route from Directions API
                val routeResult = locationHelper.getRealRoute(start, end, apiMode)

                if (routeResult != null && routeResult.polylinePoints.isNotEmpty()) {
                    // Remove existing polyline
                    currentPolyline?.remove()

                    // Create polyline with all the route points
                    val polylineOptions = PolylineOptions()
                        .addAll(routeResult.polylinePoints)
                        .width(12f)
                        .color(
                            ContextCompat.getColor(
                                this@DashboardActivity,
                                R.color.primary_brown
                            )
                        )
                        .geodesic(true)
                        .jointType(JointType.ROUND)
                        .startCap(RoundCap())
                        .endCap(RoundCap())

                    currentPolyline = mMap.addPolyline(polylineOptions)

                    // Update route info with real data
                    updateRealRouteInfo(routeResult)

                    Toast.makeText(
                        this@DashboardActivity,
                        "Route loaded: ${routeResult.distance}, ${routeResult.duration}",
                        Toast.LENGTH_SHORT
                    ).show()

                } else {
                    // Fallback to straight line if API fails
                    drawStraightLineRoute(start, end)
                    Toast.makeText(
                        this@DashboardActivity,
                        "Using direct route (API unavailable)",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error drawing real route", e)
                // Fallback to straight line
                drawStraightLineRoute(start, end)
                Toast.makeText(
                    this@DashboardActivity,
                    "Error loading route, using direct path",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Fallback method for straight line route
     */
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

        // Update with estimated info
        currentLocation?.let { current ->
            updateRouteInfo(current, end)
        }
    }

    /**
     * Update route info section with real route data
     */
    private fun updateRealRouteInfo(routeResult: LocationHelper.RouteResult) {
        routeInfoSection.visibility = View.VISIBLE

        tvDistance.text = routeResult.distance
        tvEta.text = routeResult.duration

        // Determine traffic condition based on duration vs straight-line distance
        val trafficCondition = estimateTrafficFromRoute(routeResult)
        tvTrafficCondition.text = trafficCondition
        updateTrafficColor(trafficCondition)
    }

    /**
     * Estimate traffic condition from real route data
     */
    private fun estimateTrafficFromRoute(routeResult: LocationHelper.RouteResult): String {
        // Compare actual travel time vs ideal time
        val idealMinutes = (routeResult.distanceKm * 1.0).toInt() // 1 min per km ideal
        val actualMinutes = routeResult.durationMinutes

        return when {
            actualMinutes <= idealMinutes * 1.2 -> "Light"
            actualMinutes <= idealMinutes * 1.8 -> "Moderate"
            else -> "Heavy"
        }
    }

    /**
     * Update traffic condition color
     */
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
        val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val savedRadius = sharedPrefs.getFloat("geofence_radius", 100f)
        GeofenceHelper.setGeofenceRadius(savedRadius)
    }

    private fun handleAlarmStoppedIntent() {
        if (intent.getBooleanExtra("alarm_stopped", false)) {
            val stoppedAlarmId = intent.getIntExtra("stopped_alarm_id", -1)
            val navigationEnded = intent.getBooleanExtra("navigation_ended", false)

            if (navigationEnded) {
                // Navigation was already ended by the alarm stop action
                lifecycleScope.launch {
                    // Stop navigation completely
                    stopNavigation()

                    // Show completion message
                    Toast.makeText(this@DashboardActivity,
                        "You've reached your destination! Navigation ended.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            Log.d(TAG, "Alarm stopped handling completed, alarm ID: $stoppedAlarmId")
        }
    }

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

        // Initialize pinnedLocationDivider and navigationInstructions
        pinnedLocationDivider = locationCard.findViewById(R.id.pinnedLocationDivider) ?: View(this)
        navigationInstructions = locationCard.findViewById(R.id.navigationInstructions) ?: View(this)

        tvCurrentLocationAddress.text = getString(R.string.calculating)

        pinnedLocationSection.visibility = View.GONE
        routeInfoSection.visibility = View.GONE
        pinnedLocationDivider.visibility = View.GONE
        navigationStatusSection.visibility = View.GONE
        navigationInstructions.visibility = View.GONE
        tvNoPinMessage.visibility = View.VISIBLE

        // Setup location card navigation button
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
                    tvUserName.text =
                        if (user.isAnonymous) "Guest" else "${user.firstName} ${user.lastName}"
                    tvUserEmail.text = if (user.isAnonymous) "Anonymous User" else user.email
                } else {
                    tvUserName.text = session["userName"] as String? ?: "User"
                    tvUserEmail.text = if (session["isAnonymous"] as Boolean? == true)
                        "Anonymous User" else "No Email"
                }
            }
        }
    }

    private fun setupNotificationButtons() {
        findViewById<Button>(R.id.btnTestNotification).setOnClickListener {
            notificationService.scheduleTestNotification()
            Toast.makeText(this, "Test notification scheduled (3 seconds)", Toast.LENGTH_SHORT)
                .show()
        }

        findViewById<Button>(R.id.btnTestAlarm).setOnClickListener {
            notificationService.scheduleTestAlarm()
            Toast.makeText(this, "Test alarm scheduled (5 seconds)", Toast.LENGTH_SHORT).show()
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

            // Set up map interaction based on navigation state
            updateMapInteraction()

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
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
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
        // Remove existing elements
        currentMarker?.remove()
        currentPolyline?.remove()
        geofenceCircle?.remove()

        // Add new marker
        currentMarker = mMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("Pinned Location (${GeofenceHelper.getGeofenceRadius().toInt()}m geofence)")
        )

        pinnedLocation = latLng
        mMap.animateCamera(CameraUpdateFactory.newLatLng(latLng))

        // Add geofence visualization (but don't activate geofence yet)
        addGeofenceVisualization(latLng, GeofenceHelper.getGeofenceRadius())
    }

    private fun updateCurrentLocationInfo(latLng: LatLng) {
        lifecycleScope.launch {
            try {
                val address = locationHelper.getAddressFromLocation(latLng)
                tvCurrentLocationAddress.text = address
            } catch (e: Exception) {
                Log.e(TAG, "Error updating current location info", e)
                tvCurrentLocationAddress.text = "Location unavailable"
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
                // Stop navigation if active
                if (isNavigating) {
                    stopNavigation()
                }

                // Clear session
                sessionManager.clearSession()

                // Sign out from auth service
                authService.signOut()

                // Navigate to login
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
        loadGeofenceSettings()

        // Update geofence visualization with new radius if pinned location exists
        pinnedLocation?.let { pinned ->
            val radius = GeofenceHelper.getGeofenceRadius()
            addGeofenceVisualization(pinned, radius)
            currentMarker?.title = "Pinned Location (${radius.toInt()}m geofence)"
        }

        // Ensure navigation state UI is correct
        updateNavigationUI()
        updateDrawerState()
        updateToolbarState()

        // Check if navigation state changed while app was in background
        checkNavigationState()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Clean up animations
        navigationStatusAnimation?.cancel()

        // NavigationHelper will handle geofence cleanup automatically
        // Don't manually stop navigation here as it should persist across activity lifecycle
    }

    override fun onPause() {
        super.onPause()

        // Don't stop navigation when pausing - let it continue in background
        Log.d(TAG, "Activity paused, navigation continues in background")
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
}