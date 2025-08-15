package com.example.gzingapp.ui.places

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.gzingapp.R
import com.example.gzingapp.services.LocationHelper
import com.example.gzingapp.services.PlacesService
import com.example.gzingapp.services.SessionManager
import com.example.gzingapp.ui.auth.LoginActivity
import com.example.gzingapp.ui.dashboard.DashboardActivity
import com.example.gzingapp.ui.routes.RoutesActivity
import com.example.gzingapp.ui.routes.RoutePlannerActivity
import com.example.gzingapp.ui.settings.SettingsActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

class PlacesActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var placesRecyclerView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var searchView: SearchView

    private lateinit var sessionManager: SessionManager
    private lateinit var placesService: PlacesService
    private lateinit var locationHelper: LocationHelper
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var placesAdapter: PlacesAdapter

    private var currentLocation: LatLng? = null
    private var isLoadingPlaces = false
    
    // Debounce search variables
    private var searchJob: Job? = null
    private val searchDebounceDelay = 500L // 500ms delay

    /**
     * Get current location name for user-friendly messages
     */
    private fun getCurrentLocationName(): String {
        return try {
            if (currentLocation != null) {
                val location = currentLocation!!
                when {
                    // Check if in Antipolo area
                    location.latitude >= 14.580 && location.latitude <= 14.650 && 
                    location.longitude >= 121.160 && location.longitude <= 121.220 -> "Antipolo"
                    
                    // Check if in Marikina area
                    location.latitude >= 14.620 && location.latitude <= 14.680 && 
                    location.longitude >= 121.080 && location.longitude <= 121.130 -> "Marikina"
                    
                    else -> "your area"
                }
            } else {
                "your area"
            }
        } catch (e: Exception) {
            "your area"
        }
    }
    
    /**
     * Get location-based search hint
     */
    private fun getLocationBasedHint(): String {
        val locationName = getCurrentLocationName()
        return "Search places near $locationName"
    }
    
    /**
     * Update search view hint based on current location
     */
    private fun updateSearchViewHint() {
        try {
            searchView.queryHint = getLocationBasedHint()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating search view hint", e)
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 100
        private const val TAG = "PlacesActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_places)

        // Initialize services FIRST before UI setup
        sessionManager = SessionManager(this)
        placesService = PlacesService(this)
        locationHelper = LocationHelper(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        initializeViews()
        setupToolbar()
        setupNavigationDrawer()
        setupBottomNavigation()
        setupRecyclerView()
        setupSwipeRefresh()
        setupSearchView()

        // Check if user has an active session (either guest or authenticated)
        if (sessionManager.needsAuthentication()) {
            Log.d(TAG, "No active session found, redirecting to login")
            startActivity(Intent(this, com.example.gzingapp.ui.auth.LoginActivity::class.java))
            finish()
            return
        } else {
            Log.d(TAG, "Active session found")
        }

        // Check location permission and load places
        checkLocationPermissionAndLoadPlaces()
        
        // Setup route planner button
        setupRoutePlannerButton()
        
        // Setup map view FAB
        setupMapViewFAB()
        
        // Setup filter button
        setupFilterButton()
    }
    
    private fun setupRoutePlannerButton() {
        try {
            val btnRoutePlanner = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRoutePlanner)
            btnRoutePlanner?.setOnClickListener {
                // Navigate to route planner
                val intent = Intent(this, RoutePlannerActivity::class.java)
                startActivity(intent)
                Toast.makeText(this, "Opening Multi-Point Route Planner", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up route planner button", e)
        }
    }
    
    private fun setupMapViewFAB() {
        try {
            val fabMapView = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabMapView)
            fabMapView?.setOnClickListener {
                // Navigate to dashboard (map view)
                val intent = Intent(this, DashboardActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(intent)
                overridePendingTransition(0, 0)
                finish()
                Toast.makeText(this, "Opening Map View", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up map view FAB", e)
        }
    }
    
    private fun setupFilterButton() {
        try {
            val btnFilter = findViewById<android.widget.ImageView>(R.id.btnFilter)
            btnFilter?.setOnClickListener {
                showFilterDialog()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up filter button", e)
        }
    }
    
    private fun showFilterDialog() {
        val categories = arrayOf("All Places", "Restaurants", "Shopping", "Attractions", "Churches", "Parks", "Hotels")
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Filter Places by Category")
        builder.setItems(categories) { _, which ->
            val selectedCategory = categories[which]
            filterPlacesByCategory(selectedCategory)
            Toast.makeText(this, "Filtering by: $selectedCategory", Toast.LENGTH_SHORT).show()
        }
        builder.setNegativeButton("Cancel", null)
        builder.create().show()
    }
    
    private fun filterPlacesByCategory(category: String) {
        // This would filter the current places list by category
        // For now, we'll just refresh the places as a placeholder
        refreshPlaces()
        // TODO: Implement actual category filtering logic
    }

    private fun initializeViews() {
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        toolbar = findViewById(R.id.toolbar)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        placesRecyclerView = findViewById(R.id.placesRecyclerView)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        searchView = findViewById(R.id.searchView)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Places"
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
    


    private fun setupBottomNavigation() {
        bottomNavigation.selectedItemId = R.id.nav_places

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    Log.d(TAG, "Navigating to DashboardActivity")
                    val intent = Intent(this, DashboardActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(intent)
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_routes -> {
                    Log.d(TAG, "Navigating to RoutesActivity")
                    val intent = Intent(this, RoutesActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(intent)
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_places -> {
                    Log.d(TAG, "Already on places page")
                    // Already on places page
                    true
                }
                else -> {
                    Log.w(TAG, "Unknown navigation item selected: ${item.itemId}")
                    false
                }
            }
        }
        
        Log.d(TAG, "Bottom navigation setup completed successfully")
    }

    private fun setupRecyclerView() {
        placesRecyclerView.layoutManager = GridLayoutManager(this, 2)

        placesAdapter = PlacesAdapter(emptyList()) { place ->
            handlePlaceClick(place)
        }

        placesRecyclerView.adapter = placesAdapter
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            refreshPlaces()
        }
        swipeRefreshLayout.setColorSchemeResources(
            R.color.primary_brown,
            R.color.dark_brown,
            R.color.light_brown
        )
    }

    private fun setupSearchView() {
        // Set location-based hint instead of default
        updateSearchViewHint()
        
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrBlank()) {
                    searchPlacesWithDebounce(query, immediate = true)
                } else {
                    // If empty search, load nearby places instead of defaults
                    loadNearbyPlaces()
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // Implement debounced real-time search with nearby focus
                if (newText.isNullOrBlank()) {
                    // Show nearby places when search is cleared
                    loadNearbyPlaces()
                } else {
                    searchNearbyPlacesWithQuery(newText, immediate = false)
                }
                return true
            }
        })
        
        // Set up search view to focus on nearby results
        searchView.queryHint = getLocationBasedHint()
    }

    private fun checkLocationPermissionAndLoadPlaces() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            getCurrentLocationAndLoadPlaces()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun getCurrentLocationAndLoadPlaces() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        swipeRefreshLayout.isRefreshing = true

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    currentLocation = LatLng(location.latitude, location.longitude)
                    // Check if location is within Antipolo/Marikina bounds
                    if (isLocationInSupportedArea(currentLocation!!)) {
                        loadNearbyPlaces()
                        updateSearchViewHint() // Update search hint with actual location
                    } else {
                        // If outside supported area, use Antipolo center
                        currentLocation = LatLng(14.5995, 121.1817) // Antipolo coordinates
                        loadNearbyPlaces()
                        updateSearchViewHint() // Update search hint
                        showSnackbar("🗺️ Showing places in supported area (Antipolo/Marikina)")
                    }
                } else {
                    // Use default location (Antipolo area) if current location is not available
                    currentLocation = LatLng(14.5995, 121.1817) // Antipolo coordinates
                    loadNearbyPlaces()
                    updateSearchViewHint() // Update search hint
                    showSnackbar("📡 Using default location. Enable GPS for better results.")
                }
            }
            .addOnFailureListener { exception ->
                swipeRefreshLayout.isRefreshing = false
                showSnackbar("Failed to get location: ${exception.message}")
                // Load places for Antipolo/Marikina area
                loadAntipoloMarikinaSamplePlaces()
            }
    }

    private fun loadNearbyPlaces() {
        val location = currentLocation ?: run {
            // If no location yet, try to get it first
            checkLocationPermissionAndLoadPlaces()
            return
        }

        if (isLoadingPlaces) return
        isLoadingPlaces = true

        lifecycleScope.launch {
            try {
                // Enhanced nearby places loading with location priority
                val places = placesService.searchNearbyPlaces(location)
                
                // Sort places by distance from current location
                val sortedPlaces = places.sortedBy { place ->
                    if (place.latLng.latitude != 0.0 && place.latLng.longitude != 0.0) {
                        locationHelper.calculateDistance(location, place.latLng)
                    } else {
                        Double.MAX_VALUE
                    }
                }

                runOnUiThread {
                    swipeRefreshLayout.isRefreshing = false
                    isLoadingPlaces = false

                    if (sortedPlaces.isNotEmpty()) {
                        placesAdapter.updatePlaces(sortedPlaces)
                        val locationName = getCurrentLocationName()
                        showSnackbar("📍 Found ${sortedPlaces.size} places near $locationName")
                        updateSearchViewHint()
                    } else {
                        // Try to get more places with larger radius
                        loadNearbyPlacesWithLargerRadius(location)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    swipeRefreshLayout.isRefreshing = false
                    isLoadingPlaces = false
                    showSnackbar("Error loading nearby places: ${e.message}")
                    // Load sample data as fallback only if necessary
                    loadAntipoloMarikinaSamplePlaces()
                }
            }
        }
    }
    
    /**
     * Try to load places with a larger search radius
     */
    private fun loadNearbyPlacesWithLargerRadius(location: LatLng) {
        lifecycleScope.launch {
            try {
                // Search with larger radius (this would require PlacesService enhancement)
                val places = placesService.searchNearbyPlaces(location)
                
                runOnUiThread {
                    if (places.isNotEmpty()) {
                        placesAdapter.updatePlaces(places)
                        showSnackbar("📍 Found ${places.size} places in wider area")
                    } else {
                        loadAntipoloMarikinaSamplePlaces()
                        showSnackbar("🗺️ Showing local places in your area")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    loadAntipoloMarikinaSamplePlaces()
                    showSnackbar("🗺️ Showing local places")
                }
            }
        }
    }

    /**
     * Search nearby places with query - prioritizes location-based results
     */
    private fun searchNearbyPlacesWithQuery(query: String, immediate: Boolean = false) {
        // Cancel previous search job
        searchJob?.cancel()
        
        if (query.isBlank()) {
            // If search is empty, load nearby places
            loadNearbyPlaces()
            return
        }
        
        // If query is too short, don't search unless immediate
        if (query.length < 2 && !immediate) {
            return
        }
        
        searchJob = lifecycleScope.launch {
            if (!immediate) {
                delay(searchDebounceDelay)
            }
            
            // Perform location-aware search
            searchNearbyPlaces(query)
        }
    }
    
    /**
     * Legacy method for compatibility - now redirects to nearby search
     */
    private fun searchPlacesWithDebounce(query: String, immediate: Boolean = false) {
        searchNearbyPlacesWithQuery(query, immediate)
    }

    /**
     * Search nearby places with a specific query - location-aware search
     */
    private fun searchNearbyPlaces(query: String) {
        if (isLoadingPlaces) return
        isLoadingPlaces = true

        swipeRefreshLayout.isRefreshing = true

        lifecycleScope.launch {
            try {
                // Use current location or default to supported area
                val searchLocation = currentLocation ?: LatLng(14.5995, 121.1817) // Antipolo center
                
                val places = placesService.searchPlacesByText(query, searchLocation)
                
                // Filter and sort by distance from current location
                val filteredPlaces = places.filter { place ->
                    place.latLng.latitude != 0.0 && place.latLng.longitude != 0.0 &&
                    isLocationInSupportedArea(place.latLng)
                }.sortedBy { place ->
                    locationHelper.calculateDistance(searchLocation, place.latLng)
                }

                runOnUiThread {
                    swipeRefreshLayout.isRefreshing = false
                    isLoadingPlaces = false

                    if (filteredPlaces.isNotEmpty()) {
                        placesAdapter.updatePlaces(filteredPlaces)
                        val locationName = getCurrentLocationName()
                        showSnackbar("🔍 Found ${filteredPlaces.size} places for '$query' near $locationName")
                    } else {
                        // Show nearby sample places if no API results
                        loadAntipoloMarikinaSamplePlaces()
                        showSnackbar("🔍 No '$query' found nearby. Showing local places.")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    swipeRefreshLayout.isRefreshing = false
                    isLoadingPlaces = false
                    
                    // Fallback to sample places
                    loadAntipoloMarikinaSamplePlaces()
                    showSnackbar("Search failed. Showing local places.")
                }
            }
        }
    }
    
    /**
     * Legacy search method - now redirects to nearby search
     */
    private fun searchPlaces(query: String) {
        searchNearbyPlaces(query)
    }

    private fun refreshPlaces() {
        checkLocationPermissionAndLoadPlaces()
    }

    private fun loadSamplePlaces() {
        loadAntipoloMarikinaSamplePlaces()
    }

    private fun loadAntipoloMarikinaSamplePlaces() {
        // Sample data for places in Antipolo and Marikina only  
        val samplePlaces = listOf(
            PlaceItem(
                id = "sm_marikina",
                name = "SM Marikina", 
                category = "Shopping Mall", 
                location = "Marikina City", 
                address = "Marikina City",
                rating = 4.5f,
                priceLevel = 2,
                isOpen = true,
                photoUrl = null,
                latLng = LatLng(14.6408, 121.1078),
                imageRes = R.drawable.bg_shopping_placeholder
            ),
            PlaceItem(
                id = "riverbanks_mall",
                name = "Riverbanks Mall", 
                category = "Shopping Center", 
                location = "Marikina City", 
                address = "Marikina City",
                rating = 4.2f,
                priceLevel = 2,
                isOpen = true,
                photoUrl = null,
                latLng = LatLng(14.6298, 121.1028),
                imageRes = R.drawable.bg_shopping_placeholder
            ),
            PlaceItem(
                id = "hinulugang_taktak",
                name = "Hinulugang Taktak", 
                category = "Waterfall", 
                location = "Antipolo City", 
                address = "Antipolo City",
                rating = 4.3f,
                priceLevel = 1,
                isOpen = true,
                photoUrl = null,
                latLng = LatLng(14.5672, 121.1842),
                imageRes = R.drawable.bg_attraction_placeholder
            ),
            PlaceItem(
                id = "antipolo_cathedral",
                name = "Antipolo Cathedral", 
                category = "Cathedral", 
                location = "Antipolo City", 
                address = "Antipolo City",
                rating = 4.7f,
                priceLevel = null,
                isOpen = true,
                photoUrl = null,
                latLng = LatLng(14.5995, 121.1817),
                imageRes = R.drawable.bg_church_placeholder
            ),
            PlaceItem(
                id = "marikina_sports_park",
                name = "Marikina Sports Park", 
                category = "Recreation", 
                location = "Marikina City", 
                address = "Marikina City",
                rating = 4.3f,
                priceLevel = null,
                isOpen = true,
                photoUrl = null,
                latLng = LatLng(14.6475, 121.1121),
                imageRes = R.drawable.bg_attraction_placeholder
            ),
            PlaceItem(
                id = "cloud9_antipolo",
                name = "Cloud 9 Antipolo", 
                category = "Restaurant", 
                location = "Antipolo City", 
                address = "Antipolo City",
                rating = 4.1f,
                priceLevel = 3,
                isOpen = true,
                photoUrl = null,
                latLng = LatLng(14.5834, 121.1953),
                imageRes = R.drawable.bg_restaurant_placeholder
            ),
            PlaceItem(
                id = "pinto_art_museum",
                name = "Pinto Art Museum", 
                category = "Art Gallery", 
                location = "Antipolo City", 
                address = "Antipolo City",
                rating = 4.6f,
                priceLevel = 2,
                isOpen = true,
                photoUrl = null,
                latLng = LatLng(14.5639, 121.1906),
                imageRes = R.drawable.bg_attraction_placeholder
            ),
            PlaceItem(
                id = "marikina_river_park",
                name = "Marikina River Park", 
                category = "Park", 
                location = "Marikina City", 
                address = "Marikina City",
                rating = 4.1f,
                priceLevel = null,
                isOpen = true,
                photoUrl = null,
                latLng = LatLng(14.6531, 121.1025),
                imageRes = R.drawable.bg_attraction_placeholder
            ),
            PlaceItem(
                id = "crescent_moon_cafe",
                name = "Crescent Moon Cafe", 
                category = "Restaurant", 
                location = "Antipolo City", 
                address = "Antipolo City",
                rating = 4.4f,
                priceLevel = 2,
                isOpen = true,
                photoUrl = null,
                latLng = LatLng(14.5823, 121.1945),
                imageRes = R.drawable.bg_restaurant_placeholder
            ),
            PlaceItem(
                id = "shoe_museum",
                name = "Shoe Museum", 
                category = "Museum", 
                location = "Marikina City", 
                address = "Marikina City",
                rating = 4.2f,
                priceLevel = 1,
                isOpen = true,
                photoUrl = null,
                latLng = LatLng(14.6422, 121.1052),
                imageRes = R.drawable.bg_attraction_placeholder
            )
        )

        placesAdapter.updatePlaces(samplePlaces)
    }

    /**
     * Check if location is within Antipolo or Marikina bounds
     */
    private fun isLocationInSupportedArea(location: LatLng): Boolean {
        // Approximate bounds for Antipolo and Marikina
        val antipoloMinLat = 14.540
        val antipoloMaxLat = 14.720
        val antipoloMinLng = 121.120
        val antipoloMaxLng = 121.240
        
        val marikinMinLat = 14.620
        val marikinMaxLat = 14.670
        val marikinMinLng = 121.090
        val marikinMaxLng = 121.130

        // Check if location is in Antipolo bounds
        val inAntipolo = location.latitude >= antipoloMinLat && 
                        location.latitude <= antipoloMaxLat &&
                        location.longitude >= antipoloMinLng && 
                        location.longitude <= antipoloMaxLng

        // Check if location is in Marikina bounds
        val inMarikina = location.latitude >= marikinMinLat && 
                        location.latitude <= marikinMaxLat &&
                        location.longitude >= marikinMinLng && 
                        location.longitude <= marikinMaxLng

        return inAntipolo || inMarikina
    }

    private fun handlePlaceClick(place: PlaceItem) {
        // Handle place selection
        if (place.latLng.latitude != 0.0 && place.latLng.longitude != 0.0) {
            // If it's a real place with coordinates, navigate to dashboard and pin it
            val intent = Intent(this, DashboardActivity::class.java).apply {
                putExtra("pin_location", true)
                putExtra("destination_lat", place.latLng.latitude)
                putExtra("destination_lng", place.latLng.longitude)
                putExtra("destination_name", place.name)
                putExtra("destination_address", place.address)
                putExtra("place_category", place.category)
                putExtra("auto_create_geofence", true) // Automatically create geofence based on preferences
            }
            startActivity(intent)
            Toast.makeText(this, "Pinning ${place.name} on map...", Toast.LENGTH_SHORT).show()
        } else {
            // For sample places without coordinates
            Toast.makeText(this, "Selected: ${place.name}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getCurrentLocationAndLoadPlaces()
                } else {
                    showSnackbar("Location permission denied. Using default location.")
                    // Use default location
                    currentLocation = LatLng(14.5995, 121.1817) // Antipolo coordinates
                    loadNearbyPlaces()
                }
            }
        }
    }



    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_dashboard -> {
                startActivity(Intent(this, com.example.gzingapp.ui.dashboard.DashboardActivity::class.java))
            }
            R.id.nav_routes -> {
                startActivity(Intent(this, com.example.gzingapp.ui.routes.RoutesActivity::class.java))
            }
            R.id.nav_places -> {
                // Already in places activity
            }
            // Additional navigation items can be added here when the menu is expanded
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}