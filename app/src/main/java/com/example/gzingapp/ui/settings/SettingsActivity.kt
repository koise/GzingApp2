package com.example.gzingapp.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import com.example.gzingapp.R
import com.example.gzingapp.services.GeofenceHelper
import com.example.gzingapp.services.SessionManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var rgGeofenceRadius: RadioGroup
    private lateinit var rbRadius50: RadioButton
    private lateinit var rbRadius100: RadioButton
    private lateinit var rbRadius150: RadioButton
    private lateinit var rbRadius200: RadioButton
    private lateinit var switchVoiceAnnouncements: SwitchCompat

    private lateinit var sessionManager: SessionManager
    private lateinit var geofenceHelper: GeofenceHelper

    companion object {
        private const val TAG = "SettingsActivity"
        private const val PREF_GEOFENCE_RADIUS = "geofence_radius"
        private const val PREF_VOICE_ANNOUNCEMENTS = "voice_announcements"
        private const val DEFAULT_RADIUS = 100f
        const val RESULT_SETTINGS_CHANGED = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        sessionManager = SessionManager(this)
        geofenceHelper = GeofenceHelper(this)

        setupUI()
        setupToolbar()
        loadCurrentSettings()
        setupListeners()
    }

    private fun setupUI() {
        toolbar = findViewById(R.id.toolbar)
        rgGeofenceRadius = findViewById(R.id.rgGeofenceRadius)
        rbRadius50 = findViewById(R.id.rbRadius50)
        rbRadius100 = findViewById(R.id.rbRadius100)
        rbRadius150 = findViewById(R.id.rbRadius150)
        rbRadius200 = findViewById(R.id.rbRadius200)
        switchVoiceAnnouncements = findViewById(R.id.switchVoiceAnnouncements)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = getString(R.string.settings)
        }
    }

    private fun loadCurrentSettings() {
        // Get current geofence radius from preferences
        val currentRadius = getCurrentGeofenceRadius()

        // Set the appropriate radio button
        when (currentRadius.toInt()) {
            50 -> rbRadius50.isChecked = true
            100 -> rbRadius100.isChecked = true
            150 -> rbRadius150.isChecked = true
            200 -> rbRadius200.isChecked = true
            else -> rbRadius100.isChecked = true // Default to 100m
        }

        // Load voice announcements setting
        val isVoiceEnabled = getVoiceAnnouncementsEnabled()
        switchVoiceAnnouncements.isChecked = isVoiceEnabled
    }

    private fun setupListeners() {
        rgGeofenceRadius.setOnCheckedChangeListener { _, checkedId ->
            val selectedRadius = when (checkedId) {
                R.id.rbRadius50 -> 50f
                R.id.rbRadius100 -> 100f
                R.id.rbRadius150 -> 150f
                R.id.rbRadius200 -> 200f
                else -> DEFAULT_RADIUS
            }

            // Save and apply the new radius
            saveGeofenceRadius(selectedRadius)
            applyGeofenceRadius(selectedRadius)

            Toast.makeText(
                this,
                getString(R.string.geofence_radius_updated, selectedRadius.toInt()),
                Toast.LENGTH_SHORT
            ).show()
        }

        switchVoiceAnnouncements.setOnCheckedChangeListener { _, isChecked ->
            saveVoiceAnnouncementsEnabled(isChecked)
            val message = if (isChecked) {
                "Voice announcements enabled"
            } else {
                "Voice announcements disabled"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun getCurrentGeofenceRadius(): Float {
        val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        return sharedPrefs.getFloat(PREF_GEOFENCE_RADIUS, DEFAULT_RADIUS)
    }

    private fun saveGeofenceRadius(radius: Float) {
        val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        sharedPrefs.edit()
            .putFloat(PREF_GEOFENCE_RADIUS, radius)
            .apply()
    }

    private fun applyGeofenceRadius(radius: Float) {
        // Update the global radius setting
        GeofenceHelper.setGeofenceRadius(radius)

        // If there's an existing geofence, update it with the new radius
        geofenceHelper.updateGeofenceRadius()

        // Set result to indicate settings were changed
        setResult(RESULT_SETTINGS_CHANGED)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // Finish with result to notify caller of changes
                finishWithResult()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        finishWithResult()
    }

    private fun finishWithResult() {
        // Ensure the final radius is applied
        val currentRadius = getCurrentGeofenceRadius()
        GeofenceHelper.setGeofenceRadius(currentRadius)

        // Create result intent with radius info
        val resultIntent = Intent().apply {
            putExtra("geofence_radius", currentRadius)
            putExtra("settings_changed", true)
        }
        setResult(RESULT_SETTINGS_CHANGED, resultIntent)
        finish()
    }

    private fun getVoiceAnnouncementsEnabled(): Boolean {
        val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        return sharedPrefs.getBoolean(PREF_VOICE_ANNOUNCEMENTS, false)
    }

    private fun saveVoiceAnnouncementsEnabled(enabled: Boolean) {
        val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        sharedPrefs.edit()
            .putBoolean(PREF_VOICE_ANNOUNCEMENTS, enabled)
            .apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure the radius is applied to GeofenceHelper when leaving settings
        val currentRadius = getCurrentGeofenceRadius()
        GeofenceHelper.setGeofenceRadius(currentRadius)
    }
}