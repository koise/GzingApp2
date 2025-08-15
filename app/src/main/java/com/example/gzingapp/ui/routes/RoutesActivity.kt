package com.example.gzingapp.ui.routes

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.gzingapp.R
import com.example.gzingapp.services.RouteStorageService
import com.example.gzingapp.services.SessionManager
import com.example.gzingapp.ui.auth.LoginActivity
import com.example.gzingapp.ui.dashboard.DashboardActivity
import com.example.gzingapp.ui.places.PlacesActivity
import com.example.gzingapp.ui.settings.SettingsActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.launch

class RoutesActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var routesRecyclerView: RecyclerView
    private lateinit var sessionManager: SessionManager
    private lateinit var routeStorageService: RouteStorageService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_routes)

        try {
            // Initialize services FIRST before UI setup
            sessionManager = SessionManager(this)
            routeStorageService = RouteStorageService(this)

            initializeViews()
            setupToolbar()
            setupNavigationDrawer()
            setupBottomNavigation()
            setupRecyclerView()

            // Check if user has an active session (either guest or authenticated)
            if (sessionManager.needsAuthentication()) {
                Log.d("RoutesActivity", "No active session found, redirecting to login")
                navigateToLogin()
                return
            } else {
                Log.d("RoutesActivity", "Active session found")
            }
        } catch (e: Exception) {
            Log.e("RoutesActivity", "Error in onCreate", e)
            // If there's a critical error during setup, redirect to login
            try {
                navigateToLogin()
            } catch (e2: Exception) {
                Log.e("RoutesActivity", "Could not navigate to login", e2)
                finish()
            }
        }
    }

    private fun initializeViews() {
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        toolbar = findViewById(R.id.toolbar)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        routesRecyclerView = findViewById(R.id.routesRecyclerView)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Routes"
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
        try {
            // Make sure bottomNavigation is properly initialized before using it
            if (!::bottomNavigation.isInitialized) {
                Log.e("RoutesActivity", "Bottom navigation not initialized")
                return
            }
            
            // Set the selected item safely
            try {
                bottomNavigation.selectedItemId = R.id.nav_routes
            } catch (e: Exception) {
                Log.e("RoutesActivity", "Error setting selected item in bottom navigation", e)
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
                            // Already on routes page
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
                    Log.e("RoutesActivity", "Error handling bottom navigation selection", e)
                    false
                }
            }
        } catch (e: Exception) {
            Log.e("RoutesActivity", "Error setting up bottom navigation", e)
            // Try to continue without bottom navigation if it fails
            try {
                bottomNavigation.visibility = View.GONE
            } catch (e2: Exception) {
                Log.e("RoutesActivity", "Could not hide bottom navigation", e2)
            }
        }
    }

    private fun setupRecyclerView() {
        routesRecyclerView.layoutManager = LinearLayoutManager(this)
        loadAndDisplayRoutes()
    }
    
    private fun loadAndDisplayRoutes() {
        lifecycleScope.launch {
            try {
                // Load saved routes
                val savedRoutes = routeStorageService.getSavedRoutesAsItems()
                
                // Load sample routes
                val sampleRoutes = createSampleRoutes()
                
                // Combine saved routes (at the top) with sample routes
                val allRoutes = savedRoutes + sampleRoutes
                
                runOnUiThread {
                    val adapter = RoutesAdapter(allRoutes) { route ->
                        // Handle route click - show route details with A->B->C->D pattern
                        showRouteDetails(route)
                    }
                    
                    routesRecyclerView.adapter = adapter
                    
                    if (savedRoutes.isNotEmpty()) {
                        Toast.makeText(this@RoutesActivity, "Showing ${savedRoutes.size} saved routes and ${sampleRoutes.size} sample routes", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("RoutesActivity", "Error loading routes", e)
                runOnUiThread {
                    // Fallback to sample routes only
                    val sampleRoutes = createSampleRoutes()
                    val adapter = RoutesAdapter(sampleRoutes) { route ->
                        showRouteDetails(route)
                    }
                    routesRecyclerView.adapter = adapter
                }
            }
        }
    }
    
    private fun createSampleRoutes(): List<RouteItem> {
        // Import PlaceItem from models, not from ui.places
        val antipoloCenter = com.example.gzingapp.models.PlaceItem(
            id = "antipolo_center",
            name = "Antipolo City Center", 
            category = "City Center", 
            location = "Antipolo City", 
            address = "Antipolo City",
            rating = 4.5f,
            priceLevel = null,
            isOpen = true,
            photoUrl = null,
            latLng = com.google.android.gms.maps.model.LatLng(14.5995, 121.1817),
            imageRes = com.example.gzingapp.R.drawable.ic_places
        )
        
        val smMarikina = com.example.gzingapp.models.PlaceItem(
            id = "sm_marikina",
            name = "SM Marikina", 
            category = "Shopping Mall", 
            location = "Marikina City", 
            address = "Marikina City",
            rating = 4.5f,
            priceLevel = 2,
            isOpen = true,
            photoUrl = null,
            latLng = com.google.android.gms.maps.model.LatLng(14.6408, 121.1078),
            imageRes = com.example.gzingapp.R.drawable.ic_places
        )
        
        val pintoArt = com.example.gzingapp.models.PlaceItem(
            id = "pinto_art",
            name = "Pinto Art Museum", 
            category = "Art Gallery", 
            location = "Antipolo City", 
            address = "Antipolo City",
            rating = 4.6f,
            priceLevel = 2,
            isOpen = true,
            photoUrl = null,
            latLng = com.google.android.gms.maps.model.LatLng(14.5639, 121.1906),
            imageRes = com.example.gzingapp.R.drawable.ic_places
        )
        
        val riverbanks = com.example.gzingapp.models.PlaceItem(
            id = "riverbanks",
            name = "Riverbanks Mall", 
            category = "Shopping Center", 
            location = "Marikina City", 
            address = "Marikina City",
            rating = 4.2f,
            priceLevel = 2,
            isOpen = true,
            photoUrl = null,
            latLng = com.google.android.gms.maps.model.LatLng(14.6298, 121.1028),
            imageRes = com.example.gzingapp.R.drawable.ic_places
        )
        
        val cathedral = com.example.gzingapp.models.PlaceItem(
            id = "cathedral",
            name = "Antipolo Cathedral", 
            category = "Cathedral", 
            location = "Antipolo City", 
            address = "Antipolo City",
            rating = 4.7f,
            priceLevel = null,
            isOpen = true,
            photoUrl = null,
            latLng = com.google.android.gms.maps.model.LatLng(14.5995, 121.1817),
            imageRes = com.example.gzingapp.R.drawable.ic_places
        )

        return listOf(
            RouteItem(
                id = "route_1",
                title = "Daily Commute Route",
                routePoints = listOf(
                    com.example.gzingapp.models.RoutePoint(place = antipoloCenter, order = 0),
                    com.example.gzingapp.models.RoutePoint(place = smMarikina, order = 1)
                ),
                duration = "25 min",
                distance = "5.2 km",
                isActive = false
            ),
            RouteItem(
                id = "route_2", 
                title = "Shopping & Art Tour",
                routePoints = listOf(
                    com.example.gzingapp.models.RoutePoint(place = smMarikina, order = 0),
                    com.example.gzingapp.models.RoutePoint(place = pintoArt, order = 1),
                    com.example.gzingapp.models.RoutePoint(place = riverbanks, order = 2)
                ),
                duration = "45 min",
                distance = "12.8 km",
                isActive = true
            ),
            RouteItem(
                id = "route_3",
                title = "Heritage & Faith Route", 
                routePoints = listOf(
                    com.example.gzingapp.models.RoutePoint(place = antipoloCenter, order = 0),
                    com.example.gzingapp.models.RoutePoint(place = cathedral, order = 1),
                    com.example.gzingapp.models.RoutePoint(place = pintoArt, order = 2),
                    com.example.gzingapp.models.RoutePoint(place = riverbanks, order = 3)
                ),
                duration = "1h 15min",
                distance = "18.5 km",
                isActive = false
            ),
            RouteItem(
                id = "route_4",
                title = "Mall Hopping Route",
                routePoints = listOf(
                    com.example.gzingapp.models.RoutePoint(place = smMarikina, order = 0),
                    com.example.gzingapp.models.RoutePoint(place = riverbanks, order = 1)
                ),
                duration = "12 min",
                distance = "2.1 km",
                isActive = false
            )
        )
    }
    
    private fun showRouteDetails(route: RouteItem) {
        val detailsMessage = buildString {
            appendLine("Route: ${route.title}")
            appendLine("Points: ${route.detailedPointsDescription}")
            appendLine("Duration: ${route.duration}")
            appendLine("Distance: ${route.distance}")
            if (route.isActive) {
                appendLine("Status: Currently Active")
            }
        }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Route Details")
            .setMessage(detailsMessage)
            .setPositiveButton("Start Route") { _, _ ->
                startRoute(route)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun startRoute(route: RouteItem) {
        Toast.makeText(this, "Starting route: ${route.title}", Toast.LENGTH_LONG).show()
        // TODO: Integrate with navigation system to start the multi-point route
        // This would connect to the MultiPointRoute system and start navigation
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_account -> {
                startActivity(Intent(this, com.example.gzingapp.ui.profile.ProfileActivity::class.java))
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
            Log.e("RoutesActivity", "Error during logout", e)
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