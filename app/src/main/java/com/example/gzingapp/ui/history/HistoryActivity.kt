package com.example.gzingapp.ui.history

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.gzingapp.R
import com.example.gzingapp.models.NavigationHistory
import com.example.gzingapp.models.NavigationStatus
import com.example.gzingapp.services.NavigationHistoryService
import com.example.gzingapp.services.SessionManager
import com.example.gzingapp.ui.auth.LoginActivity
import com.example.gzingapp.ui.dashboard.DashboardActivity
import com.example.gzingapp.ui.places.PlacesActivity
import com.example.gzingapp.ui.routes.RoutePlannerActivity
import com.example.gzingapp.ui.routes.RoutesActivity
import com.example.gzingapp.ui.settings.SettingsActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.navigation.NavigationView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var bottomNavigation: BottomNavigationView
    
    private lateinit var recyclerHistory: RecyclerView
    private lateinit var emptyStateHistory: View
    private lateinit var tvEmptyStateTitle: TextView
    private lateinit var tvEmptyStateDescription: TextView
    private lateinit var progressBar: ProgressBar
    
    // Filter UI components
    private lateinit var chipGroupFilter: ChipGroup
    private lateinit var chipAll: Chip
    private lateinit var chipCompleted: Chip
    private lateinit var chipInProgress: Chip
    private lateinit var chipCancelled: Chip
    private lateinit var etSearch: TextInputEditText
    private lateinit var btnClearSearch: ImageButton

    private lateinit var sessionManager: SessionManager
    private lateinit var historyService: NavigationHistoryService
    private lateinit var historyAdapter: NavigationHistoryAdapter
    
    private var historyList: MutableList<NavigationHistory> = mutableListOf()
    private var filteredHistoryList: MutableList<NavigationHistory> = mutableListOf()
    
    // Filter state
    private var currentSearchQuery: String = ""
    private var selectedStatuses: MutableSet<NavigationStatus> = mutableSetOf()

    companion object {
        private const val TAG = "HistoryActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        initializeViews()
        setupToolbar()
        setupNavigationDrawer()
        setupBottomNavigation()
        setupRecyclerView()
        setupFilters()

        sessionManager = SessionManager(this)
        historyService = NavigationHistoryService(this)

        // Check if user is logged in
        if (!sessionManager.isLoggedIn()) {
            Log.d(TAG, "User not logged in, redirecting to login")
            navigateToLogin()
            return
        } else {
            Log.d(TAG, "User is logged in, loading navigation history")
        }

        loadNavigationHistory()
    }

    private fun initializeViews() {
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        toolbar = findViewById(R.id.toolbar)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        recyclerHistory = findViewById(R.id.recyclerHistory)
        emptyStateHistory = findViewById(R.id.emptyStateHistory)
        tvEmptyStateTitle = findViewById(R.id.tvEmptyStateTitle)
        tvEmptyStateDescription = findViewById(R.id.tvEmptyStateDescription)
        progressBar = findViewById(R.id.progressBar)
        
        // Initialize filter views
        chipGroupFilter = findViewById(R.id.chipGroupFilter)
        chipAll = findViewById(R.id.chipAll)
        chipCompleted = findViewById(R.id.chipCompleted)
        chipInProgress = findViewById(R.id.chipInProgress)
        chipCancelled = findViewById(R.id.chipCancelled)
        etSearch = findViewById(R.id.etSearch)
        btnClearSearch = findViewById(R.id.btnClearSearch)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Navigation History"
    }

    private fun setupNavigationDrawer() {
        toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        navigationView.setNavigationItemSelectedListener(this)
    }

    private fun setupBottomNavigation() {
        bottomNavigation.selectedItemId = R.id.nav_history
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    true
                }
                R.id.nav_routes -> {
                    startActivity(Intent(this, RoutesActivity::class.java))
                    true
                }
                R.id.nav_history -> {
                    // Already on history page
                    true
                }
                R.id.nav_places -> {
                    startActivity(Intent(this, PlacesActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRecyclerView() {
        recyclerHistory.layoutManager = LinearLayoutManager(this)
        historyAdapter = NavigationHistoryAdapter(
            filteredHistoryList,
            onHistoryClick = { history ->
                // Handle history item click
                Toast.makeText(this, "Clicked on ${history.routeDescription}", Toast.LENGTH_SHORT).show()
            },
            onDeleteClick = { history ->
                deleteHistory(history)
            }
        )
        recyclerHistory.adapter = historyAdapter
    }
    
    private fun setupFilters() {
        // Set up chip selection listeners
        chipAll.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedStatuses.clear() // Clear all filters when "All" is selected
            }
            applyFilters()
        }
        
        chipCompleted.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedStatuses.add(NavigationStatus.COMPLETED)
                chipAll.isChecked = false
            } else {
                selectedStatuses.remove(NavigationStatus.COMPLETED)
            }
            applyFilters()
        }
        
        chipInProgress.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedStatuses.add(NavigationStatus.IN_PROGRESS)
                chipAll.isChecked = false
            } else {
                selectedStatuses.remove(NavigationStatus.IN_PROGRESS)
            }
            applyFilters()
        }
        
        chipCancelled.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedStatuses.add(NavigationStatus.CANCELLED)
                selectedStatuses.add(NavigationStatus.FAILED) // Include FAILED with CANCELLED
                chipAll.isChecked = false
            } else {
                selectedStatuses.remove(NavigationStatus.CANCELLED)
                selectedStatuses.remove(NavigationStatus.FAILED)
            }
            applyFilters()
        }
        
        // Set up search functionality
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                currentSearchQuery = s.toString().trim().lowercase()
                btnClearSearch.visibility = if (currentSearchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                applyFilters()
            }
        })
        
        btnClearSearch.setOnClickListener {
            etSearch.setText("")
            currentSearchQuery = ""
            btnClearSearch.visibility = View.GONE
            applyFilters()
        }
    }

    private fun loadNavigationHistory() {
        progressBar.visibility = View.VISIBLE
        emptyStateHistory.visibility = View.GONE
        recyclerHistory.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val userId = sessionManager.getUserId()
                historyList = historyService.getNavigationHistory(userId).toMutableList()
                
                // Apply filters to the loaded history
                applyFilters()
                
                progressBar.visibility = View.GONE
                updateEmptyState()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading navigation history", e)
                progressBar.visibility = View.GONE
                updateEmptyState()
                Toast.makeText(this@HistoryActivity, "Error loading history: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun applyFilters() {
        filteredHistoryList = if (selectedStatuses.isEmpty() && currentSearchQuery.isEmpty()) {
            // No filters applied, show all
            historyList.toMutableList()
        } else {
            historyList.filter { history ->
                // Filter by status if any status filters are selected
                val statusMatch = if (selectedStatuses.isEmpty()) {
                    true // No status filter
                } else {
                    history.status in selectedStatuses
                }
                
                // Filter by search query
                val searchMatch = if (currentSearchQuery.isEmpty()) {
                    true // No search query
                } else {
                    // Match route description or any destination name
                    history.routeDescription.lowercase().contains(currentSearchQuery) ||
                    history.destinations.any { it.name.lowercase().contains(currentSearchQuery) }
                }
                
                statusMatch && searchMatch
            }.toMutableList()
        }
        
        // Update the adapter with filtered list
        historyAdapter.updateHistoryList(filteredHistoryList)
        
        // Update empty state based on filtered results
        updateEmptyState()
    }

    private fun updateEmptyState() {
        if (filteredHistoryList.isEmpty()) {
            recyclerHistory.visibility = View.GONE
            emptyStateHistory.visibility = View.VISIBLE
            
            // Show different messages based on whether we have history but it's filtered out
            if (historyList.isEmpty()) {
                // No history at all
                tvEmptyStateTitle.text = "No Navigation History"
                tvEmptyStateDescription.text = "You haven't completed any navigation routes yet. Plan a route to get started."
            } else {
                // We have history but it's filtered out
                tvEmptyStateTitle.text = "No Matching History"
                tvEmptyStateDescription.text = "No navigation history matches your current filters. Try adjusting your search or filter criteria."
            }
        } else {
            recyclerHistory.visibility = View.VISIBLE
            emptyStateHistory.visibility = View.GONE
        }
    }

    private fun deleteHistory(history: NavigationHistory) {
        AlertDialog.Builder(this)
            .setTitle("Delete History")
            .setMessage("Are you sure you want to delete this navigation history?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    try {
                        historyService.deleteNavigationHistory(history.id)
                        // Remove from the main list first
                        historyList.removeIf { it.id == history.id }
                        // Then apply filters to update the filtered list
                        applyFilters()
                        Toast.makeText(this@HistoryActivity, "History deleted", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deleting history", e)
                        Toast.makeText(this@HistoryActivity, "Error deleting history: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_account -> {
                if (sessionManager.isLoggedIn()) {
                    Toast.makeText(this, "You are already logged in", Toast.LENGTH_SHORT).show()
                } else {
                    navigateToLogin()
                }
            }
            R.id.menu_account -> {
                if (sessionManager.isLoggedIn()) {
                    startActivity(Intent(this, com.example.gzingapp.ui.profile.ProfileActivity::class.java))
                } else {
                    Toast.makeText(this, "Please log in to view profile", Toast.LENGTH_SHORT).show()
                    navigateToLogin()
                }
            }
            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            R.id.nav_history -> {
                // Already on history page
                Toast.makeText(this, "Already on History page", Toast.LENGTH_SHORT).show()
            }
            R.id.menu_logout -> {
                if (sessionManager.isLoggedIn()) {
                    logout()
                } else {
                    Toast.makeText(this, "You are not logged in", Toast.LENGTH_SHORT).show()
                }
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun logout() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                sessionManager.clearSession()
                Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
                navigateToLogin()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        if (sessionManager.isLoggedIn()) {
            loadNavigationHistory()
        }
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}