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
import com.example.gzingapp.services.NavigationHistoryService
import com.example.gzingapp.models.NavigationHistory
import com.example.gzingapp.models.NavigationDestination
import com.example.gzingapp.ui.auth.LoginActivity
import com.example.gzingapp.ui.settings.SettingsActivity
import com.example.gzingapp.services.NavigationHelper
import com.example.gzingapp.receivers.GeofenceBroadcastReceiver
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
import com.example.gzingapp.ui.routes.RoutesActivity
import com.example.gzingapp.ui.places.PlacesActivity

class DashboardActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener, OnMapReadyCallback {

    companion object {
        private const val TAG = "DashboardActivity"
        private const val CARD_EXPANDED_KEY = "card_expanded"
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_account -> {
                if (isNavigating) {
                    Toast.makeText(this, getString(R.string.drawer_locked_message), Toast.LENGTH_SHORT).show()
                    return false
                }
                
                if (sessionManager.isAnonymous()) {
                    // Redirect guest users to login for account access
                    Toast.makeText(this, "Please sign in to access your account", Toast.LENGTH_LONG).show()
                    val intent = Intent(this, com.example.gzingapp.ui.auth.LoginActivity::class.java)
                    startActivity(intent)
                } else {
                    startActivity(Intent(this, com.example.gzingapp.ui.profile.ProfileActivity::class.java))
                }
            }
            R.id.menu_settings -> {
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
                
                if (sessionManager.isAnonymous()) {
                    // Redirect guest users to login for history access
                    Toast.makeText(this, "Please sign in to view your navigation history", Toast.LENGTH_LONG).show()
                    val intent = Intent(this, com.example.gzingapp.ui.auth.LoginActivity::class.java)
                    startActivity(intent)
                } else {
                    startActivity(Intent(this, com.example.gzingapp.ui.history.HistoryActivity::class.java))
                }
            }
            R.id.menu_logout -> {
                // Only show logout option for non-guest users
                if (!sessionManager.isAnonymous()) {
                    if (isNavigating) {
                        showLogoutDialog()
                    } else {
                        performLogout()
                    }
                } else {
                    Toast.makeText(this, "Already using guest mode", Toast.LENGTH_SHORT).show()
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

    private lateinit var authService: AuthService
    private lateinit var sessionManager: SessionManager
    private lateinit var notificationService: NotificationService
    private lateinit var locationHelper: LocationHelper
    private lateinit var geofenceHelper: GeofenceHelper
    private lateinit var navigationHelper: NavigationHelper
    private lateinit var navigationHistoryService: NavigationHistoryService

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

    // Animation objects for navigation status
    private var navigationStatusAnimation: ObjectAnimator? = null
    private var navigationStatusCollapsedAnimation: ObjectAnimator? = null

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
        setupNotificationButtons()
        setupMap()
        loadGeofenceSettings()
        checkGooglePlayServices()

        isCardExpanded = savedInstanceState?.getBoolean(CARD_EXPANDED_KEY, false) ?: false
        updateCardExpansionState(animate = false)

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        if (!sessionManager.isLoggedIn()) {
            navigateToLogin()
        }

        handleAlarmStoppedIntent()
        checkNavigationState()
        handlePlacePinIntent()
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
            currentMarker?.remove()
            currentPolyline?.remove()
            geofenceCircle?.remove()

            // Add marker with place name
            val title = "$name (from history)"
            currentMarker = mMap.addMarker(
                MarkerOptions()
                    .position(location)
                    .title(title)
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
            currentMarker?.remove()
            currentMarker = mMap.addMarker(
                MarkerOptions()
                    .position(destination)
                    .title("Navigation Destination")
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

        setupBottomNavigation()
        setSupportActionBar(toolbar)
    }

    private fun setupBottomNavigation() {
        bottomNavigation = findViewById(R.id.bottomNavigation)
        bottomNavigation.selectedItemId = R.id.nav_dashboard

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    true
                }
                R.id.nav_routes -> {
                    if (isNavigating) {
                        Toast.makeText(this, getString(R.string.drawer_locked_message), Toast.LENGTH_SHORT).show()
                        false
                    } else {
                        startActivity(Intent(this, RoutesActivity::class.java))
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
                        startActivity(Intent(this, PlacesActivity::class.java))
                        overridePendingTransition(0, 0)
                        finish()
                        true
                    }
                }
                else -> false
            }
        }
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
        
        // Hide logout option for guest users
        updateNavigationMenuForGuest()
    }
    
    private fun updateNavigationMenuForGuest() {
        val menu = navigationView.menu
        val logoutItem = menu.findItem(R.id.menu_logout)
        logoutItem?.isVisible = !sessionManager.isAnonymous()
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
        currentMarker?.remove()
        currentMarker = null
        
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
        routeInfoSection.visibility = View.GONE
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
                
                val userId = if (!sessionManager.isAnonymous()) {
                    sessionManager.getCurrentUser()?.id
                } else null
                
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

                    updateNavigationUI()
                    updateDrawerState()
                    updateToolbarState()
                    updateMapInteraction() // Re-enable pinning after navigation stops

                    navigationStatusAnimation?.cancel()
                    geofenceCircle?.remove()

                    // Clear navigation notifications and show cancelled notification
                    notificationService.clearNavigationNotifications()
                    notificationService.showNavigationCancelledNotification("Navigation cancelled by user")

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

                    // Clear navigation notifications and show cancelled notification
                    notificationService.clearNavigationNotifications()
                    notificationService.showNavigationCancelledNotification("Navigation cancelled due to error")

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
        val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val savedRadius = sharedPrefs.getFloat("geofence_radius", 100f)
        GeofenceHelper.setGeofenceRadius(savedRadius)
    }

    private fun handleAlarmStoppedIntent() {
        if (intent.getBooleanExtra("alarm_stopped", false)) {
            val stoppedAlarmId = intent.getIntExtra("stopped_alarm_id", -1)
            val navigationEnded = intent.getBooleanExtra("navigation_ended", false)

            if (navigationEnded) {
                lifecycleScope.launch {
                    stopNavigation()

                    Toast.makeText(this@DashboardActivity,
                        "You've reached your destination! Navigation ended.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            Log.d(TAG, "Alarm stopped handling completed, alarm ID: $stoppedAlarmId")
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
                    tvUserName.text = if (user.isAnonymous) "Guest" else "${user.firstName} ${user.lastName}"
                    tvUserEmail.text = if (user.isAnonymous) "Anonymous User" else user.email
                } else {
                    tvUserName.text = session["userName"] as String? ?: "User"
                    tvUserEmail.text = if (session["isAnonymous"] as Boolean? == true)
                        "Anonymous User" else "No Email"
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
                
                // Show alarm notification
                notificationService.showAlarmNotification(
                    "🚨 DESTINATION ALARM!",
                    "You have arrived at your destination!",
                    GeofenceBroadcastReceiver.ARRIVAL_ALARM_ID
                )
            }
        } else {
            val distanceToGeofence = distanceToDestination - geofenceRadius
            tvGeofenceStatus.text = "Outside geofence"
            tvGeofenceDistance.text = "Distance to geofence: ${locationHelper.formatDistance(distanceToGeofence)}"
        }
    }

    private fun setupNotificationButtons() {
        findViewById<Button>(R.id.btnTestNotification).setOnClickListener {
            notificationService.scheduleTestNotification()
            Toast.makeText(this, "Test notification scheduled (3 seconds)", Toast.LENGTH_SHORT).show()
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
        currentMarker?.remove()
        currentPolyline?.remove()
        geofenceCircle?.remove()

        currentMarker = mMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("Pinned Location (${GeofenceHelper.getGeofenceRadius().toInt()}m geofence)")
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
        loadGeofenceSettings()

        pinnedLocation?.let { pinned ->
            val radius = GeofenceHelper.getGeofenceRadius()
            addGeofenceVisualization(pinned, radius)
            currentMarker?.title = "Pinned Location (${radius.toInt()}m geofence)"
        }

        updateNavigationUI()
        updateDrawerState()
        updateToolbarState()

        checkNavigationState()
    }

    override fun onDestroy() {
        super.onDestroy()

        navigationStatusAnimation?.cancel()
        navigationStatusCollapsedAnimation?.cancel()
    }

    override fun onPause() {
        super.onPause()
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

    /**
     * Pin a location from PlacesActivity with automatic geofence creation
     */
    private fun pinLocationFromPlaces(location: LatLng, name: String, address: String, autoCreateGeofence: Boolean) {
        try {
            Log.d(TAG, "Pinning location from places: $name at $location")

            // Remove existing markers and polylines
            currentMarker?.remove()
            currentPolyline?.remove()
            geofenceCircle?.remove()

            // Add marker with place name
            val title = "$name (${GeofenceHelper.getGeofenceRadius().toInt()}m geofence)"
            currentMarker = mMap.addMarker(
                MarkerOptions()
                    .position(location)
                    .title(title)
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
}