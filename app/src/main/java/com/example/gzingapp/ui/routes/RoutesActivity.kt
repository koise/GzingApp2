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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_routes)

        try {
            initializeViews()
            setupToolbar()
            setupNavigationDrawer()
            setupBottomNavigation()
            setupRecyclerView()

            sessionManager = SessionManager(this)

            // Check if user is logged in
            if (!sessionManager.isLoggedIn()) {
                Log.d("RoutesActivity", "User not logged in, redirecting to login")
                navigateToLogin()
                return
            } else {
                Log.d("RoutesActivity", "User is logged in, proceeding with Routes activity")
                // Update UI based on user type (guest or registered)
                updateNavigationMenuForGuest()
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

        // Sample data for routes - you can replace with actual data
        val sampleRoutes = listOf(
            RouteItem("Home to Work", "Antipolo to Marikina", "25 min", "5.2 km"),
            RouteItem("Work to Mall", "Marikina to SM Marikina", "8 min", "1.8 km"),
            RouteItem("Home to School", "Antipolo to University", "30 min", "7.1 km"),
            RouteItem("Mall to Restaurant", "SM Marikina to Restaurant", "12 min", "2.5 km")
        )

        val adapter = RoutesAdapter(sampleRoutes) { route ->
            // Handle route click
            Toast.makeText(this, "Selected: ${route.title}", Toast.LENGTH_SHORT).show()
        }

        routesRecyclerView.adapter = adapter
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