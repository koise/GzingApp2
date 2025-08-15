package com.example.gzingapp.ui.routes

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.gzingapp.R
import com.example.gzingapp.models.MultiPointRoute
import com.example.gzingapp.models.RoutePoint
import com.example.gzingapp.services.LocationHelper
import com.example.gzingapp.services.PlacesService
import com.example.gzingapp.services.RouteStorageService
import com.example.gzingapp.services.SessionManager
import com.example.gzingapp.ui.auth.LoginActivity
import com.example.gzingapp.ui.dashboard.DashboardActivity
import com.example.gzingapp.ui.places.PlaceItem
import com.example.gzingapp.models.PlaceItem as ModelPlaceItem
import com.example.gzingapp.ui.places.PlacesActivity
import com.example.gzingapp.ui.settings.SettingsActivity
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.launch

class RoutePlannerActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var bottomNavigation: BottomNavigationView
    
    private lateinit var recyclerRoutePoints: RecyclerView
    private lateinit var recyclerAvailablePlaces: RecyclerView
    private lateinit var searchPlaces: SearchView
    private lateinit var fabStartRoute: ExtendedFloatingActionButton
    private lateinit var emptyStateRoute: View
    private lateinit var tvTotalStops: TextView
    private lateinit var tvEstimatedTime: TextView
    private lateinit var switchAlarmEachStop: SwitchCompat
    private lateinit var switchVoiceAlarms: SwitchCompat

    private lateinit var sessionManager: SessionManager
    private lateinit var placesService: PlacesService
    private lateinit var locationHelper: LocationHelper
    private lateinit var routeStorageService: RouteStorageService
    
    private lateinit var routePointAdapter: RoutePointAdapter
    private lateinit var placeSelectableAdapter: PlaceSelectableAdapter
    
    private var currentRoute = MultiPointRoute()
    private var availablePlaces: List<PlaceItem> = emptyList()

    companion object {
        private const val TAG = "RoutePlannerActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_planner)

        // Initialize services FIRST before UI setup
        sessionManager = SessionManager(this)
        placesService = PlacesService(this)
        locationHelper = LocationHelper(this)
        routeStorageService = RouteStorageService(this)

        initializeViews()
        setupToolbar()
        setupNavigationDrawer()
        setupBottomNavigation()
        setupRecyclerViews()
        setupSearchView()
        setupSwitches()
        setupFAB()

        // Check if user has an active session (either guest or authenticated)
        if (sessionManager.needsAuthentication()) {
            Log.d(TAG, "No active session found, redirecting to login")
            navigateToLogin()
            return
        } else {
            Log.d(TAG, "Active session found")
        }

        loadAvailablePlaces()
        updateUI()
    }

    private fun initializeViews() {
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        toolbar = findViewById(R.id.toolbar)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        recyclerRoutePoints = findViewById(R.id.recyclerRoutePoints)
        recyclerAvailablePlaces = findViewById(R.id.recyclerAvailablePlaces)
        searchPlaces = findViewById(R.id.searchPlaces)
        fabStartRoute = findViewById(R.id.fabStartRoute)
        emptyStateRoute = findViewById(R.id.emptyStateRoute)
        tvTotalStops = findViewById(R.id.tvTotalStops)
        tvEstimatedTime = findViewById(R.id.tvEstimatedTime)
        switchAlarmEachStop = findViewById(R.id.switchAlarmEachStop)
        switchVoiceAlarms = findViewById(R.id.switchVoiceAlarms)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Route Planner"
    }

    private fun setupNavigationDrawer() {
        toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navigationView.setNavigationItemSelectedListener(this)
        
        // Setup navigation menu
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
                bottomNavigation.selectedItemId = R.id.nav_routes
            } catch (e: Exception) {
                Log.e(TAG, "Error setting selected item in bottom navigation", e)
            }

            bottomNavigation.setOnItemSelectedListener { item ->
                try {
                    when (item.itemId) {
                        R.id.nav_dashboard -> {
                            val intent = Intent(this, DashboardActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            startActivity(intent)
                            overridePendingTransition(0, 0)
                            finish()
                            true
                        }
                        R.id.nav_routes -> {
                            val intent = Intent(this, RoutesActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            startActivity(intent)
                            overridePendingTransition(0, 0)
                            finish()
                            true
                        }
                        R.id.nav_places -> {
                            val intent = Intent(this, PlacesActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            startActivity(intent)
                            overridePendingTransition(0, 0)
                            finish()
                            true
                        }
                        else -> false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling bottom navigation selection", e)
                    false
                }
            }
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

    private fun setupRecyclerViews() {
        // Setup route points recycler
        routePointAdapter = RoutePointAdapter(
            onRemovePoint = { routePoint ->
                removeRoutePoint(routePoint)
            },
            onAlarmToggle = { routePoint, enabled ->
                updateRoutePointAlarm(routePoint, enabled)
            }
        )
        recyclerRoutePoints.layoutManager = LinearLayoutManager(this)
        recyclerRoutePoints.adapter = routePointAdapter

        // Setup available places recycler
        placeSelectableAdapter = PlaceSelectableAdapter(
            onAddToRoute = { place ->
                addPlaceToRoute(place)
            }
        )
        recyclerAvailablePlaces.layoutManager = LinearLayoutManager(this)
        recyclerAvailablePlaces.adapter = placeSelectableAdapter
    }

    private fun setupSearchView() {
        searchPlaces.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { searchPlacesInArea(it) }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterAvailablePlaces(newText ?: "")
                return true
            }
        })
    }

    private fun setupSwitches() {
        switchAlarmEachStop.setOnCheckedChangeListener { _, isChecked ->
            currentRoute = currentRoute.copy(alarmForEachStop = isChecked)
            // Update all existing points
            currentRoute.points.forEachIndexed { index, point ->
                currentRoute.points[index] = point.copy(alarmEnabled = isChecked)
            }
            routePointAdapter.updateRoutePoints(currentRoute.points)
        }

        switchVoiceAlarms.setOnCheckedChangeListener { _, isChecked ->
            currentRoute = currentRoute.copy(voiceAnnouncementsEnabled = isChecked)
        }
    }

    private fun setupFAB() {
        fabStartRoute.setOnClickListener {
            showRouteActionDialog()
        }
    }

    private fun loadAvailablePlaces() {
        lifecycleScope.launch {
            try {
                // Use current location or default to Antipolo/Marikina area
                val defaultLocation = LatLng(14.5995, 121.1817) // Antipolo coordinates
                val places = placesService.searchNearbyPlaces(defaultLocation)
                
                availablePlaces = places.map { place ->
                    PlaceItem(
                        id = place.id,
                        name = place.name,
                        category = place.category,
                        location = place.location,
                        address = place.address,
                        rating = place.rating,
                        priceLevel = place.priceLevel,
                        isOpen = place.isOpen,
                        photoUrl = place.photoUrl,
                        latLng = place.latLng,
                        distance = place.distance,
                        imageRes = R.drawable.ic_places
                    )
                }
                
                runOnUiThread {
                    placeSelectableAdapter.updatePlaces(availablePlaces)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading places", e)
                // Load sample places as fallback
                loadSamplePlaces()
            }
        }
    }

    private fun loadSamplePlaces() {
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
            )
        )
        
        availablePlaces = samplePlaces
        placeSelectableAdapter.updatePlaces(availablePlaces)
    }

    private fun addPlaceToRoute(place: PlaceItem) {
        // Convert UI PlaceItem to model PlaceItem
        val modelPlaceItem = ModelPlaceItem(place)
        val routePoint = currentRoute.addPoint(modelPlaceItem)
        routePointAdapter.addRoutePoint(routePoint)
        updateUI()
        Toast.makeText(this, "Added ${place.name} to route", Toast.LENGTH_SHORT).show()
    }

    private fun removeRoutePoint(routePoint: RoutePoint) {
        currentRoute.removePoint(routePoint.id)
        routePointAdapter.removeRoutePoint(routePoint)
        updateUI()
        Toast.makeText(this, "Removed ${routePoint.place.name} from route", Toast.LENGTH_SHORT).show()
    }

    private fun updateRoutePointAlarm(routePoint: RoutePoint, enabled: Boolean) {
        val index = currentRoute.points.indexOfFirst { it.id == routePoint.id }
        if (index != -1) {
            currentRoute.points[index] = routePoint.copy(alarmEnabled = enabled)
        }
    }

    private fun searchPlacesInArea(query: String) {
        lifecycleScope.launch {
            try {
                val defaultLocation = LatLng(14.5995, 121.1817)
                val searchResults = placesService.searchPlacesByText(query, defaultLocation)
                
                val places = searchResults.map { place ->
                    PlaceItem(
                        id = place.id,
                        name = place.name,
                        category = place.category,
                        location = place.location,
                        address = place.address,
                        rating = place.rating,
                        priceLevel = place.priceLevel,
                        isOpen = place.isOpen,
                        photoUrl = place.photoUrl,
                        latLng = place.latLng,
                        distance = place.distance,
                        imageRes = R.drawable.ic_places
                    )
                }
                
                runOnUiThread {
                    placeSelectableAdapter.updatePlaces(places)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error searching places", e)
                runOnUiThread {
                    Toast.makeText(this@RoutePlannerActivity, "Search failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun filterAvailablePlaces(query: String) {
        val filteredPlaces = if (query.isBlank()) {
            availablePlaces
        } else {
            availablePlaces.filter { 
                it.name.contains(query, ignoreCase = true) || 
                it.category.contains(query, ignoreCase = true) 
            }
        }
        placeSelectableAdapter.updatePlaces(filteredPlaces)
    }

    private fun updateUI() {
        val pointCount = currentRoute.points.size
        
        // Update stops count
        tvTotalStops.text = when (pointCount) {
            0 -> "No stops planned"
            1 -> "1 stop planned"
            else -> "$pointCount stops planned"
        }
        
        // Update estimated time (simplified calculation)
        val estimatedTime = pointCount * 15 // Assume 15 minutes per stop
        tvEstimatedTime.text = "~$estimatedTime min"
        
        // Show/hide empty state
        emptyStateRoute.visibility = if (pointCount == 0) View.VISIBLE else View.GONE
        
        // Show/hide start route FAB
        fabStartRoute.visibility = if (pointCount > 0) View.VISIBLE else View.GONE
    }

    private fun showRouteActionDialog() {
        if (currentRoute.points.isEmpty()) {
            Toast.makeText(this, "Please add at least one destination", Toast.LENGTH_SHORT).show()
            return
        }

        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Route Actions")
        builder.setMessage("What would you like to do with this route?\n\n${currentRoute.getRouteDescription()}")
        
        builder.setPositiveButton("Start Navigation") { _, _ ->
            startMultiPointRoute()
        }
        
        builder.setNeutralButton("Save Route") { _, _ ->
            showSaveRouteDialog()
        }
        
        builder.setNegativeButton("Cancel", null)
        builder.create().show()
    }
    
    private fun showSaveRouteDialog() {
        val input = android.widget.EditText(this)
        input.hint = "Enter route name (optional)"
        input.setText(currentRoute.getRouteDescription())
        
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Save Route")
        builder.setMessage("Give your route a name:")
        builder.setView(input)
        
        builder.setPositiveButton("Save") { _, _ ->
            val routeName = input.text.toString().trim()
            saveRoute(routeName)
        }
        
        builder.setNegativeButton("Cancel", null)
        builder.create().show()
    }
    
    private fun saveRoute(routeName: String = "") {
        lifecycleScope.launch {
            try {
                val result = routeStorageService.saveRoute(currentRoute, routeName)
                
                runOnUiThread {
                    if (result.isSuccess) {
                        val savedName = if (routeName.isNotBlank()) routeName else currentRoute.getRouteDescription()
                        Toast.makeText(this@RoutePlannerActivity, "Route saved: $savedName", Toast.LENGTH_LONG).show()
                        
                        // Optionally ask if they want to start navigation now
                        androidx.appcompat.app.AlertDialog.Builder(this@RoutePlannerActivity)
                            .setTitle("Route Saved")
                            .setMessage("Route saved successfully! Would you like to start navigation now?")
                            .setPositiveButton("Start Navigation") { _, _ ->
                                startMultiPointRoute()
                            }
                            .setNegativeButton("Stay Here", null)
                            .show()
                    } else {
                        Toast.makeText(this@RoutePlannerActivity, "Failed to save route: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@RoutePlannerActivity, "Error saving route: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startMultiPointRoute() {
        if (currentRoute.points.isEmpty()) {
            Toast.makeText(this, "Please add at least one destination", Toast.LENGTH_SHORT).show()
            return
        }

        // Pass route data to DashboardActivity for navigation
        val intent = Intent(this, DashboardActivity::class.java).apply {
            putExtra("multi_point_route", true)
            putExtra("route_description", currentRoute.getRouteDescription())
            putExtra("alarm_each_stop", currentRoute.alarmForEachStop)
            putExtra("voice_announcements", currentRoute.voiceAnnouncementsEnabled)
            
            // Pass first destination to start navigation
            val firstPoint = currentRoute.points.first()
            putExtra("destination_lat", firstPoint.latLng.latitude)
            putExtra("destination_lng", firstPoint.latLng.longitude)
            putExtra("destination_name", firstPoint.place.name)
            putExtra("destination_address", firstPoint.place.location) // Using location instead of address
            
            // Pass route points for history logging
            putExtra("route_points_count", currentRoute.points.size)
            putExtra("route_start_time", System.currentTimeMillis())
        }
        
        startActivity(intent)
        Toast.makeText(this, "Starting multi-point route: ${currentRoute.getRouteDescription()}", Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_account -> {
                startActivity(Intent(this, com.example.gzingapp.ui.profile.ProfileActivity::class.java))
            }
            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            R.id.menu_history -> {
                startActivity(Intent(this, com.example.gzingapp.ui.history.HistoryActivity::class.java))
            }
            R.id.menu_logout -> {
                performLogout()
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun performLogout() {
        lifecycleScope.launch {
            sessionManager.clearSession()
            navigateToLogin()
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