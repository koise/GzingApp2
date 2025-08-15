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
import com.example.gzingapp.services.HistorySortOption
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
    
    // Filter and sort state
    private var currentSearchQuery: String = ""
    private var selectedStatuses: MutableSet<NavigationStatus> = mutableSetOf()
    private var currentSortOption: HistorySortOption = HistorySortOption.DATE_DESC

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

        // Check if user has an active session (either guest or authenticated)
        if (sessionManager.needsAuthentication()) {
            Log.d(TAG, "No active session found, redirecting to login")
            navigateToLogin()
            return
        } else {
            Log.d(TAG, "Active session found")
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
        
        // Add sort button to toolbar
        toolbar.inflateMenu(R.menu.menu_history_toolbar)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_sort -> {
                    showSortDialog()
                    true
                }
                R.id.action_stats -> {
                    showStatisticsDialog()
                    true
                }
                else -> false
            }
        }
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
        // History is no longer in bottom navigation - only accessible from drawer
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_routes -> {
                    startActivity(Intent(this, RoutesActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_places -> {
                    startActivity(Intent(this, PlacesActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
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
                
                // Use enhanced filtering directly from service
                filteredHistoryList = historyService.getFilteredNavigationHistory(
                    userId = userId,
                    statuses = if (selectedStatuses.isEmpty()) null else selectedStatuses,
                    searchQuery = currentSearchQuery.ifBlank { null },
                    sortBy = currentSortOption
                ).toMutableList()
                
                // Also keep the full list for reference
                historyList = historyService.getNavigationHistory(userId).toMutableList()
                
                // Update the adapter with filtered list
                historyAdapter.updateHistoryList(filteredHistoryList)
                
                progressBar.visibility = View.GONE
                updateEmptyState()
                
                Log.d(TAG, "Loaded ${historyList.size} total history items, ${filteredHistoryList.size} shown after filters")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading navigation history", e)
                progressBar.visibility = View.GONE
                updateEmptyState()
                Toast.makeText(this@HistoryActivity, "Error loading history: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun applyFilters() {
        lifecycleScope.launch {
            try {
                val userId = sessionManager.getUserId()
                
                // Use enhanced filtering from service
                filteredHistoryList = historyService.getFilteredNavigationHistory(
                    userId = userId,
                    statuses = if (selectedStatuses.isEmpty()) null else selectedStatuses,
                    searchQuery = currentSearchQuery.ifBlank { null },
                    sortBy = currentSortOption
                ).toMutableList()
                
                // Update the adapter with filtered list
                historyAdapter.updateHistoryList(filteredHistoryList)
                
                // Update empty state based on filtered results
                updateEmptyState()
                
                Log.d(TAG, "Applied filters: ${filteredHistoryList.size} items shown")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error applying filters", e)
                Toast.makeText(this@HistoryActivity, "Error applying filters", Toast.LENGTH_SHORT).show()
            }
        }
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
    
    private fun showSortDialog() {
        val sortOptions = arrayOf(
            "Newest First",
            "Oldest First", 
            "Longest Duration",
            "Shortest Duration",
            "Farthest Distance",
            "Shortest Distance",
            "By Status"
        )
        
        val currentIndex = when (currentSortOption) {
            HistorySortOption.DATE_DESC -> 0
            HistorySortOption.DATE_ASC -> 1
            HistorySortOption.DURATION_DESC -> 2
            HistorySortOption.DURATION_ASC -> 3
            HistorySortOption.DISTANCE_DESC -> 4
            HistorySortOption.DISTANCE_ASC -> 5
            HistorySortOption.STATUS -> 6
        }
        
        AlertDialog.Builder(this)
            .setTitle("Sort History")
            .setSingleChoiceItems(sortOptions, currentIndex) { dialog, which ->
                currentSortOption = when (which) {
                    0 -> HistorySortOption.DATE_DESC
                    1 -> HistorySortOption.DATE_ASC
                    2 -> HistorySortOption.DURATION_DESC
                    3 -> HistorySortOption.DURATION_ASC
                    4 -> HistorySortOption.DISTANCE_DESC
                    5 -> HistorySortOption.DISTANCE_ASC
                    6 -> HistorySortOption.STATUS
                    else -> HistorySortOption.DATE_DESC
                }
                applyFilters()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showStatisticsDialog() {
        lifecycleScope.launch {
            try {
                val userId = sessionManager.getUserId()
                val stats = historyService.getNavigationStatistics(userId)
                
                val message = buildString {
                    appendLine("Navigation Statistics")
                    appendLine()
                    appendLine("📊 Total Trips: ${stats.totalNavigations}")
                    appendLine("✅ Completed: ${stats.completedNavigations}")
                    appendLine("❌ Cancelled: ${stats.cancelledNavigations}")
                    appendLine("⚠️ Failed: ${stats.failedNavigations}")
                    appendLine("🔄 In Progress: ${stats.inProgressNavigations}")
                    appendLine()
                    appendLine("📏 Total Distance: ${String.format("%.1f km", stats.totalDistance)}")
                    appendLine("⏱️ Total Time: ${formatDuration(stats.totalDuration)}")
                    appendLine("📈 Average Time: ${formatDuration(stats.averageDuration)}")
                    appendLine("🚨 Total Alarms: ${stats.totalAlarms}")
                    appendLine()
                    if (!stats.mostFrequentDestination.isNullOrBlank()) {
                        appendLine("⭐ Frequent Destination: ${stats.mostFrequentDestination}")
                    }
                }
                
                AlertDialog.Builder(this@HistoryActivity)
                    .setTitle("Navigation Statistics")
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading statistics", e)
                Toast.makeText(this@HistoryActivity, "Error loading statistics", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun formatDuration(minutes: Int): String {
        return when {
            minutes < 60 -> "${minutes}m"
            minutes < 1440 -> "${minutes / 60}h ${minutes % 60}m"
            else -> "${minutes / 1440}d ${(minutes % 1440) / 60}h"
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_account -> {
                if (!sessionManager.needsAuthentication()) {
                    // User has a session (guest or authenticated)
                    {
                        startActivity(Intent(this, com.example.gzingapp.ui.profile.ProfileActivity::class.java))
                    }
                } else {
                    navigateToLogin()
                }
            }
            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            R.id.menu_history -> {
                // Already on history page
                Toast.makeText(this, "Already on History page", Toast.LENGTH_SHORT).show()
            }
            R.id.menu_logout -> {
                if (!sessionManager.needsAuthentication()) {
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
        if (!sessionManager.needsAuthentication()) {
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