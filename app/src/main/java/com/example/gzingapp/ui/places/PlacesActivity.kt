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

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 100
        private const val TAG = "PlacesActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_places)

        initializeViews()
        setupToolbar()
        setupNavigationDrawer()
        setupBottomNavigation()
        setupRecyclerView()
        setupSwipeRefresh()
        setupSearchView()

        sessionManager = SessionManager(this)
        placesService = PlacesService(this)
        locationHelper = LocationHelper(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Check if user is logged in
        if (!sessionManager.isLoggedIn()) {
            Log.d(TAG, "User not logged in, redirecting to login")
            navigateToLogin()
            return
        } else {
            Log.d(TAG, "User is logged in, proceeding with Places activity")
        }

        // Check location permission and load places
        checkLocationPermissionAndLoadPlaces()
        
        // Setup route planner button
        setupRoutePlannerButton()
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
        
        // Hide logout option for guest users
        updateNavigationMenuForGuest()
    }
    
    private fun updateNavigationMenuForGuest() {
        val menu = navigationView.menu
        val logoutItem = menu.findItem(R.id.menu_logout)
        logoutItem?.isVisible = !sessionManager.isAnonymous()
    }

    private fun setupBottomNavigation() {
        try {
            // Make sure bottomNavigation is properly initialized before using it
            if (!::bottomNavigation.isInitialized) {
                Log.e(TAG, "Bottom navigation not initialized")
                return
            }
            
            // Set the selected item safely
            try {
                bottomNavigation.selectedItemId = R.id.nav_places
            } catch (e: Exception) {
                Log.e(TAG, "Error setting selected item in bottom navigation", e)
            }

            bottomNavigation.setOnItemSelectedListener { item ->
                try {
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
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling bottom navigation selection", e)
                    false
                }
            }
            
            Log.d(TAG, "Bottom navigation setup completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up bottom navigation", e)
            // Try to continue without bottom navigation if it fails
            try {
                bottomNavigation.visibility = View.GONE
            } catch (e2: Exception) {
                Log.e(TAG, "Could not hide bottom navigation", e2)
            }
        }
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
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrBlank()) {
                    searchPlacesWithDebounce(query, immediate = true)
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // Implement debounced real-time search
                searchPlacesWithDebounce(newText ?: "", immediate = false)
                return true
            }
        })
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
                    } else {
                        // If outside supported area, use Antipolo center
                        currentLocation = LatLng(14.5995, 121.1817) // Antipolo coordinates
                        loadNearbyPlaces()
                        showSnackbar("Showing places in supported area (Antipolo/Marikina)")
                    }
                } else {
                    // Use default location (Antipolo area) if current location is not available
                    currentLocation = LatLng(14.5995, 121.1817) // Antipolo coordinates
                    loadNearbyPlaces()
                    showSnackbar("Using default location. Enable GPS for better results.")
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
        val location = currentLocation ?: return

        if (isLoadingPlaces) return
        isLoadingPlaces = true

        lifecycleScope.launch {
            try {
                val places = placesService.searchNearbyPlaces(location)

                runOnUiThread {
                    swipeRefreshLayout.isRefreshing = false
                    isLoadingPlaces = false

                    if (places.isNotEmpty()) {
                        placesAdapter.updatePlaces(places)
                        showSnackbar("Found ${places.size} places nearby")
                    } else {
                        // Load sample data if no places found
                        loadSamplePlaces()
                        showSnackbar("No places found nearby. Showing sample data.")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    swipeRefreshLayout.isRefreshing = false
                    isLoadingPlaces = false
                    showSnackbar("Error loading places: ${e.message}")
                    // Load sample data as fallback
                    loadSamplePlaces()
                }
            }
        }
    }

    private fun searchPlacesWithDebounce(query: String, immediate: Boolean = false) {
        // Cancel previous search job
        searchJob?.cancel()
        
        if (query.isBlank()) {
            // If search is empty, load nearby places
            loadNearbyPlaces()
            return
        }
        
        // If query is too short, don't search
        if (query.length < 2 && !immediate) {
            return
        }
        
        searchJob = lifecycleScope.launch {
            if (!immediate) {
                delay(searchDebounceDelay)
            }
            
            // Perform the search
            searchPlaces(query)
        }
    }

    private fun searchPlaces(query: String) {
        if (isLoadingPlaces) return
        isLoadingPlaces = true

        swipeRefreshLayout.isRefreshing = true

        lifecycleScope.launch {
            try {
                // Limit search to Antipolo/Marikina area
                val restrictedLocation = if (currentLocation != null && isLocationInSupportedArea(currentLocation!!)) {
                    currentLocation
                } else {
                    LatLng(14.5995, 121.1817) // Default to Antipolo center
                }
                
                val places = placesService.searchPlacesByText(query, restrictedLocation)
                
                // Filter results to only include places in supported areas
                val filteredPlaces = places.filter { place ->
                    place.latLng.latitude != 0.0 && place.latLng.longitude != 0.0 &&
                    isLocationInSupportedArea(place.latLng)
                }

                runOnUiThread {
                    swipeRefreshLayout.isRefreshing = false
                    isLoadingPlaces = false

                    if (filteredPlaces.isNotEmpty()) {
                        placesAdapter.updatePlaces(filteredPlaces)
                        showSnackbar("Found ${filteredPlaces.size} places for '$query' in Antipolo/Marikina")
                    } else {
                        // Show sample places for Antipolo/Marikina if no API results
                        loadAntipoloMarikinaSamplePlaces()
                        showSnackbar("No places found for '$query'. Showing local places.")
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

    private fun refreshPlaces() {
        getCurrentLocationAndLoadPlaces()
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
                imageRes = R.drawable.ic_places
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
                imageRes = R.drawable.ic_places
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
                imageRes = R.drawable.ic_places
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
                imageRes = R.drawable.ic_places
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
                imageRes = R.drawable.ic_places
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
                imageRes = R.drawable.ic_places
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
                imageRes = R.drawable.ic_places
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
                imageRes = R.drawable.ic_places
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
                imageRes = R.drawable.ic_places
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
                imageRes = R.drawable.ic_places
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
            R.id.menu_account -> {
                if (sessionManager.isAnonymous()) {
                    // Redirect guest users to login for account access
                    Toast.makeText(this, "Please sign in to access your account", Toast.LENGTH_LONG).show()
                    val intent = Intent(this, LoginActivity::class.java)
                    startActivity(intent)
                } else {
                    startActivity(Intent(this, com.example.gzingapp.ui.profile.ProfileActivity::class.java))
                }
            }
            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            R.id.menu_logout -> {
                // Show confirmation dialog before logout
                showLogoutConfirmationDialog()
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
    
    private fun showLogoutConfirmationDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Confirm Logout")
        builder.setMessage("Are you sure you want to log out?")
        builder.setPositiveButton("Yes") { _, _ ->
            performLogout()
        }
        builder.setNegativeButton("No") { dialog, _ ->
            dialog.dismiss()
        }
        builder.create().show()
    }
    
    private fun performLogout() {
        try {
            sessionManager.logout()
            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
            navigateToLogin()
        } catch (e: Exception) {
            Log.e(TAG, "Error during logout", e)
            Toast.makeText(this, "Logout failed. Please try again.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
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