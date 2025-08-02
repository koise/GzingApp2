package com.example.gzingapp.ui.auth

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.gzingapp.R
import com.example.gzingapp.services.AuthService
import com.example.gzingapp.services.SessionManager
import com.example.gzingapp.ui.adapters.AuthViewPagerAdapter
import com.example.gzingapp.ui.dashboard.DashboardActivity
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var authService: AuthService
    private lateinit var sessionManager: SessionManager
    
    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions.entries.any { 
            it.key.contains("location") && it.value 
        }
        
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else {
            true
        }
        
        if (!locationGranted) {
            Toast.makeText(
                this,
                R.string.location_permission_denied,
                Toast.LENGTH_LONG
            ).show()
        }
        
        // Continue with app initialization
        initializeApp()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        
        authService = AuthService()
        sessionManager = SessionManager(this)
        
        // Check permissions first
        checkAndRequestPermissions()
    }
    
    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        // Add notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        val permissionsNeeded = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (permissionsNeeded.isNotEmpty()) {
            // Show rationale dialog
            AlertDialog.Builder(this)
                .setTitle(R.string.permissions_required)
                .setMessage(R.string.permissions_explanation)
                .setPositiveButton(R.string.ok) { _, _ ->
                    requestPermissionLauncher.launch(permissionsNeeded.toTypedArray())
                }
                .setCancelable(false)
                .show()
        } else {
            // All permissions granted, proceed with app initialization
            initializeApp()
        }
    }
    
    private fun initializeApp() {
        // Check if already logged in
        if (sessionManager.isLoggedIn()) {
            navigateToDashboard()
            return
        }
        
        setupUI()
        setupViewPager()
        setupListeners()
    }
    
    private fun setupUI() {
        tabLayout = findViewById(R.id.tabLayout)
        viewPager = findViewById(R.id.viewPager)
    }
    
    private fun setupViewPager() {
        val adapter = AuthViewPagerAdapter(this)
        viewPager.adapter = adapter
        
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.login)
                1 -> getString(R.string.signup)
                else -> null
            }
        }.attach()
    }
    
    private fun setupListeners() {
        findViewById<android.widget.Button>(R.id.btnContinueAsGuest).setOnClickListener {
            signInAnonymously()
        }
    }
    
    private fun signInAnonymously() {
        lifecycleScope.launch {
            try {
                val result = authService.signInAnonymously()
                
                if (result.isSuccess) {
                    val user = result.getOrNull()
                    if (user != null) {
                        // Save anonymous user session
                        sessionManager.saveSession(
                            userId = user.uid,
                            userName = "Guest",
                            isAnonymous = true
                        )
                        
                        navigateToDashboard()
                    }
                } else {
                    showError("Failed to sign in anonymously")
                }
            } catch (e: Exception) {
                showError("Error: ${e.message}")
            }
        }
    }
    
    fun navigateToDashboard() {
        val intent = Intent(this, DashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    fun showError(message: String) {
        Snackbar.make(
            findViewById(android.R.id.content),
            message,
            Snackbar.LENGTH_LONG
        ).show()
    }
} 