package com.example.gzingapp.ui.settings

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.activity.result.contract.ActivityResultContracts
import com.example.gzingapp.R
import com.example.gzingapp.services.GeofenceHelper
import com.example.gzingapp.services.SessionManager
import com.example.gzingapp.services.NotificationService
import com.google.android.material.button.MaterialButton
import com.example.gzingapp.services.VoiceAnnouncementService
import android.media.RingtoneManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var rgGeofenceRadius: RadioGroup
    private lateinit var rbRadius50: RadioButton
    private lateinit var rbRadius100: RadioButton
    private lateinit var rbRadius150: RadioButton
    private lateinit var rbRadius200: RadioButton
    private lateinit var switchVoiceAnnouncements: SwitchCompat
    
    // Alarm Settings UI Components
    private lateinit var switchVibration: SwitchCompat
    private lateinit var btnTestAlarm: MaterialButton
    
    // Alarm Sound Settings UI Components
    private lateinit var rgAlarmSound: RadioGroup
    private lateinit var rbAlarmSoundDefault: RadioButton
    private lateinit var rbAlarmSoundCustom: RadioButton
    
    // System Sound Selection UI Components
    private lateinit var btnSelectAlarmSound: MaterialButton
    private lateinit var tvSelectedAlarmSound: TextView
    private lateinit var btnSelectNotificationSound: MaterialButton
    private lateinit var tvSelectedNotificationSound: TextView
    
    // Custom Sound Section Views
    private lateinit var customAlarmSoundSection: LinearLayout
    private lateinit var customNotificationSoundSection: LinearLayout
    
    // Notification Sound Settings UI Components
    private lateinit var rgNotificationSound: RadioGroup
    private lateinit var rbNotificationSoundDefault: RadioButton
    private lateinit var rbNotificationSoundCustom: RadioButton
    


    private lateinit var sessionManager: SessionManager
    private lateinit var geofenceHelper: GeofenceHelper
    private lateinit var notificationService: NotificationService
    private lateinit var voiceService: VoiceAnnouncementService
    
    // System sound picker launcher
    private val alarmSoundPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "🔍 DEBUG: Alarm sound picker result received")
        Log.d(TAG, "🔍 DEBUG: Result code: ${result.resultCode}")
        Log.d(TAG, "🔍 DEBUG: Result data: ${result.data}")
        
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            Log.d(TAG, "🔍 DEBUG: Result OK, checking data...")
            
            // Try different possible keys for the selected sound URI
            var selectedSoundUri: android.net.Uri? = null
            
            // Method 1: Try the standard key
            selectedSoundUri = data?.getParcelableExtra<android.net.Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            Log.d(TAG, "🔍 DEBUG: Method 1 - EXTRA_RINGTONE_PICKED_URI: $selectedSoundUri")
            
            // Method 2: Try the alternative key
            if (selectedSoundUri == null) {
                selectedSoundUri = data?.getParcelableExtra<android.net.Uri>("android.intent.extra.RINGTONE_PICKED")
                Log.d(TAG, "🔍 DEBUG: Method 2 - android.intent.extra.RINGTONE_PICKED: $selectedSoundUri")
            }
            
            // Method 3: Try the legacy key
            if (selectedSoundUri == null) {
                selectedSoundUri = data?.getParcelableExtra<android.net.Uri>("android.intent.extra.RINGTONE_PICKED_URI")
                Log.d(TAG, "🔍 DEBUG: Method 3 - android.intent.extra.RINGTONE_PICKED_URI: $selectedSoundUri")
            }
            
            // Method 4: Try to get from data URI
            if (selectedSoundUri == null) {
                selectedSoundUri = data?.data
                Log.d(TAG, "🔍 DEBUG: Method 4 - data.data: $selectedSoundUri")
            }
            
            if (selectedSoundUri != null) {
                Log.d(TAG, "✅ DEBUG: Sound URI successfully retrieved: $selectedSoundUri")
                saveCustomAlarmSound(selectedSoundUri)
                updateSelectedAlarmSoundDisplay(selectedSoundUri)
                
                // Also ensure the custom radio button is selected
                rbAlarmSoundCustom.isChecked = true
                saveAlarmSound(SOUND_TYPE_CUSTOM)
                
                Toast.makeText(this, "Custom alarm sound selected successfully", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "✅ DEBUG: Custom alarm sound saved and UI updated")
            } else {
                Log.e(TAG, "❌ DEBUG: No sound URI found in result data")
                Toast.makeText(this, "No sound selected", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.d(TAG, "🔍 DEBUG: Result not OK, user cancelled or error occurred")
        }
    }
    
    private val notificationSoundPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "🔍 DEBUG: Notification sound picker result received")
        Log.d(TAG, "🔍 DEBUG: Result code: ${result.resultCode}")
        Log.d(TAG, "🔍 DEBUG: Result data: ${result.data}")
        
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            Log.d(TAG, "🔍 DEBUG: Result OK, checking data...")
            
            // Try different possible keys for the selected sound URI
            var selectedSoundUri: android.net.Uri? = null
            
            // Method 1: Try the standard key
            selectedSoundUri = data?.getParcelableExtra<android.net.Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            Log.d(TAG, "🔍 DEBUG: Method 1 - EXTRA_RINGTONE_PICKED_URI: $selectedSoundUri")
            
            // Method 2: Try the alternative key
            if (selectedSoundUri == null) {
                selectedSoundUri = data?.getParcelableExtra<android.net.Uri>("android.intent.extra.RINGTONE_PICKED")
                Log.d(TAG, "🔍 DEBUG: Method 2 - android.intent.extra.RINGTONE_PICKED: $selectedSoundUri")
            }
            
            // Method 3: Try the legacy key
            if (selectedSoundUri == null) {
                selectedSoundUri = data?.getParcelableExtra<android.net.Uri>("android.intent.extra.RINGTONE_PICKED_URI")
                Log.d(TAG, "🔍 DEBUG: Method 3 - android.intent.extra.RINGTONE_PICKED_URI: $selectedSoundUri")
            }
            
            // Method 4: Try to get from data URI
            if (selectedSoundUri == null) {
                selectedSoundUri = data?.data
                Log.d(TAG, "🔍 DEBUG: Method 4 - data.data: $selectedSoundUri")
            }
            
            if (selectedSoundUri != null) {
                Log.d(TAG, "✅ DEBUG: Notification sound URI successfully retrieved: $selectedSoundUri")
                saveCustomNotificationSound(selectedSoundUri)
                updateSelectedNotificationSoundDisplay(selectedSoundUri)
                
                // Also ensure the custom radio button is selected
                rbNotificationSoundCustom.isChecked = true
                saveNotificationSound(SOUND_TYPE_CUSTOM)
                
                Toast.makeText(this, "Custom notification sound selected successfully", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "✅ DEBUG: Custom notification sound saved and UI updated")
            } else {
                Log.e(TAG, "❌ DEBUG: No notification sound URI found in result data")
                Toast.makeText(this, "No sound selected", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.d(TAG, "🔍 DEBUG: Result not OK, user cancelled or error occurred")
        }
    }

    companion object {
        private const val TAG = "SettingsActivity"
        private const val PREF_GEOFENCE_RADIUS = "geofence_radius"
        private const val PREF_VOICE_ANNOUNCEMENTS = "voice_announcements"
        private const val PREF_VIBRATION = "vibration"
        private const val PREF_ALARM_SOUND = "alarm_sound"
        private const val PREF_NOTIFICATION_SOUND = "notification_sound"
        private const val PREF_CUSTOM_ALARM_SOUND_URI = "custom_alarm_sound_uri"
        private const val PREF_CUSTOM_NOTIFICATION_SOUND_URI = "custom_notification_sound_uri"
        private const val DEFAULT_RADIUS = 100f
        const val RESULT_SETTINGS_CHANGED = 1001
        
        // Sound Types - Simplified to only Default and Custom
        const val SOUND_TYPE_DEFAULT = "default"
        const val SOUND_TYPE_CUSTOM = "custom"
        

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        sessionManager = SessionManager(this)
        geofenceHelper = GeofenceHelper(this)
        notificationService = NotificationService(this)
        voiceService = VoiceAnnouncementService(this)

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
        
        // Initialize alarm settings UI components
        switchVibration = findViewById(R.id.switchVibration)
        btnTestAlarm = findViewById(R.id.btnTestAlarm)
        
        // Initialize alarm sound UI components - Simplified
        rgAlarmSound = findViewById(R.id.rgAlarmSound)
        rbAlarmSoundDefault = findViewById(R.id.rbAlarmSoundDefault)
        rbAlarmSoundCustom = findViewById(R.id.rbAlarmSoundCustom)
        
        // Initialize notification sound UI components - Simplified
        rgNotificationSound = findViewById(R.id.rgNotificationSound)
        rbNotificationSoundDefault = findViewById(R.id.rbNotificationSoundDefault)
        rbNotificationSoundCustom = findViewById(R.id.rbNotificationSoundCustom)
        

        
        // Initialize sound selection buttons and text views
        btnSelectAlarmSound = findViewById(R.id.btnSelectAlarmSound)
        tvSelectedAlarmSound = findViewById(R.id.tvSelectedAlarmSound)
        btnSelectNotificationSound = findViewById(R.id.btnSelectNotificationSound)
        tvSelectedNotificationSound = findViewById(R.id.tvSelectedNotificationSound)
        
        // Initialize custom sound section views
        customAlarmSoundSection = findViewById(R.id.customAlarmSoundSection)
        customNotificationSoundSection = findViewById(R.id.customNotificationSoundSection)
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
        Log.d(TAG, "🔍 DEBUG: === LOADING CURRENT SETTINGS ===")
        
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
        
        // Load alarm settings
        loadAlarmSettings()
        
        // Load custom sound selections
        loadCustomSoundSelections()
        
        // Debug: Dump all preferences
        debugDumpAllPreferences()
        
        Log.d(TAG, "🔍 DEBUG: === SETTINGS LOADING COMPLETE ===")
    }
    
    /**
     * Debug method to dump all preferences
     */
    private fun debugDumpAllPreferences() {
        try {
            Log.d(TAG, "🔍 DEBUG: === ALL PREFERENCES DUMP ===")
            val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
            
            // Get all preferences
            val allPrefs = sharedPrefs.all
            Log.d(TAG, "🔍 DEBUG: Total preferences in 'app_settings': ${allPrefs.size}")
            
            // Log all preferences
            allPrefs.forEach { (key, value) ->
                Log.d(TAG, "🔍 DEBUG: Preference '$key' = '$value' (${value?.javaClass?.simpleName})")
            }
            
            // Specifically check sound preferences
            Log.d(TAG, "🔍 DEBUG: === SOUND PREFERENCES CHECK ===")
            val alarmSound = sharedPrefs.getString(PREF_ALARM_SOUND, "NOT_FOUND")
            val notificationSound = sharedPrefs.getString(PREF_NOTIFICATION_SOUND, "NOT_FOUND")
            val customAlarmUri = sharedPrefs.getString(PREF_CUSTOM_ALARM_SOUND_URI, "NOT_FOUND")
            val customNotificationUri = sharedPrefs.getString(PREF_CUSTOM_NOTIFICATION_SOUND_URI, "NOT_FOUND")
            
            Log.d(TAG, "🔍 DEBUG: PREF_ALARM_SOUND: '$alarmSound'")
            Log.d(TAG, "🔍 DEBUG: PREF_NOTIFICATION_SOUND: '$notificationSound'")
            Log.d(TAG, "🔍 DEBUG: PREF_CUSTOM_ALARM_SOUND_URI: '$customAlarmUri'")
            Log.d(TAG, "🔍 DEBUG: PREF_CUSTOM_NOTIFICATION_SOUND_URI: '$customNotificationUri'")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ DEBUG: Error dumping preferences", e)
        }
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

            val displayName = when (selectedRadius.toInt()) {
                50 -> "50m (Precise)"
                100 -> "100m (Standard)"
                150 -> "150m (Comfortable)"
                200 -> "200m (Generous)"
                else -> "${selectedRadius.toInt()}m"
            }
            
            Toast.makeText(
                this,
                "Geofence radius updated to $displayName",
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
            
            // Test voice announcement when enabled
            if (isChecked && voiceService.isAvailable()) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    voiceService.announce("Voice announcements are now enabled")
                }, 500)
            }
        }
        
        // Vibration listener
        switchVibration.setOnCheckedChangeListener { _, isChecked ->
            saveVibrationEnabled(isChecked)
            val message = if (isChecked) "Vibration enabled" else "Vibration disabled"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
        
        // Test alarm button listener
        btnTestAlarm.setOnClickListener {
            testCurrentAlarmSettings()
        }
        
        // Sound selection button listeners
        btnSelectAlarmSound.setOnClickListener {
            launchAlarmSoundPicker()
        }
        
        btnSelectNotificationSound.setOnClickListener {
            launchNotificationSoundPicker()
        }
        
        // Alarm sound selection listener - Simplified
        rgAlarmSound.setOnCheckedChangeListener { _, checkedId ->
            val selectedSound = when (checkedId) {
                R.id.rbAlarmSoundDefault -> SOUND_TYPE_DEFAULT
                R.id.rbAlarmSoundCustom -> SOUND_TYPE_CUSTOM
                else -> SOUND_TYPE_DEFAULT
            }
            
            saveAlarmSound(selectedSound)
            updateCustomAlarmSoundSectionVisibility(selectedSound == SOUND_TYPE_CUSTOM)
            
            Toast.makeText(this, "Alarm sound updated to: ${getSoundTypeDisplayName(selectedSound)}", Toast.LENGTH_SHORT).show()
        }
        
        // Notification sound selection listener - Simplified
        rgNotificationSound.setOnCheckedChangeListener { _, checkedId ->
            val selectedSound = when (checkedId) {
                R.id.rbNotificationSoundDefault -> SOUND_TYPE_DEFAULT
                R.id.rbNotificationSoundCustom -> SOUND_TYPE_CUSTOM
                else -> SOUND_TYPE_DEFAULT
            }
            
            saveNotificationSound(selectedSound)
            updateCustomNotificationSoundSectionVisibility(selectedSound == SOUND_TYPE_CUSTOM)
            
            Toast.makeText(this, "Notification sound updated to: ${getSoundTypeDisplayName(selectedSound)}", Toast.LENGTH_SHORT).show()
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
        // Update the global radius setting and save to preferences
        GeofenceHelper.updateGeofenceRadiusAndSave(this, radius)

        // If there's an existing geofence, update it with the new radius
        geofenceHelper.updateGeofenceRadius()

        // Set result to indicate settings were changed
        setResult(RESULT_SETTINGS_CHANGED)
        
        Log.d(TAG, "🎯 Geofence radius applied: ${radius}m")
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
    
    // Alarm Settings Methods
    
    /**
     * Load and display custom sound selections
     */
    private fun loadCustomSoundSelections() {
        try {
            // Load custom alarm sound
            getCustomAlarmSoundUri()?.let { uri ->
                updateSelectedAlarmSoundDisplay(uri)
            } ?: run {
                tvSelectedAlarmSound.text = "No custom sound selected"
            }
            
            // Load custom notification sound
            getCustomNotificationSoundUri()?.let { uri ->
                updateSelectedNotificationSoundDisplay(uri)
            } ?: run {
                tvSelectedNotificationSound.text = "No custom sound selected"
            }
            
            Log.d(TAG, "📱 Custom sound selections loaded")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading custom sound selections", e)
        }
    }
    
    private fun loadAlarmSettings() {
        // Load vibration setting
        val isVibrationEnabled = getVibrationEnabled()
        switchVibration.isChecked = isVibrationEnabled
        
        // Load alarm sound setting - Simplified
        val alarmSound = getAlarmSound()
        when (alarmSound) {
            SOUND_TYPE_DEFAULT -> rbAlarmSoundDefault.isChecked = true
            SOUND_TYPE_CUSTOM -> rbAlarmSoundCustom.isChecked = true
            else -> rbAlarmSoundDefault.isChecked = true
        }
        
        // Load notification sound setting - Simplified
        val notificationSound = getNotificationSound()
        when (notificationSound) {
            SOUND_TYPE_DEFAULT -> rbNotificationSoundDefault.isChecked = true
            SOUND_TYPE_CUSTOM -> rbNotificationSoundCustom.isChecked = true
            else -> rbNotificationSoundDefault.isChecked = true
        }
        
        // Update custom sound section visibility
        updateCustomAlarmSoundSectionVisibility(alarmSound == SOUND_TYPE_CUSTOM)
        updateCustomNotificationSoundSectionVisibility(notificationSound == SOUND_TYPE_CUSTOM)
    }
    
    private fun getVibrationEnabled(): Boolean {
        val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        return sharedPrefs.getBoolean(PREF_VIBRATION, true)
    }
    
    private fun saveVibrationEnabled(enabled: Boolean) {
        val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        sharedPrefs.edit()
            .putBoolean(PREF_VIBRATION, enabled)
            .apply()
    }
    
    // Alarm Sound Settings Methods
    private fun getAlarmSound(): String {
        val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        return sharedPrefs.getString(PREF_ALARM_SOUND, SOUND_TYPE_DEFAULT) ?: SOUND_TYPE_DEFAULT
    }
    
    private fun saveAlarmSound(sound: String) {
        Log.d(TAG, "💾 DEBUG: Saving alarm sound: '$sound'")
        val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val editor = sharedPrefs.edit()
        editor.putString(PREF_ALARM_SOUND, sound)
        val success = editor.apply()
        Log.d(TAG, "💾 DEBUG: Alarm sound save operation initiated. Success: $success")
        
        // Verify the save
        val savedValue = sharedPrefs.getString(PREF_ALARM_SOUND, "NOT_FOUND")
        Log.d(TAG, "💾 DEBUG: Verification - saved value: '$savedValue'")
    }
    
    private fun getNotificationSound(): String {
        val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        return sharedPrefs.getString(PREF_NOTIFICATION_SOUND, SOUND_TYPE_DEFAULT) ?: SOUND_TYPE_DEFAULT
    }
    
    private fun saveNotificationSound(sound: String) {
        Log.d(TAG, "💾 DEBUG: Saving notification sound: '$sound'")
        val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val editor = sharedPrefs.edit()
        editor.putString(PREF_NOTIFICATION_SOUND, sound)
        val success = editor.apply()
        Log.d(TAG, "💾 DEBUG: Notification sound save operation initiated. Success: $success")
        
        // Verify the save
        val savedValue = sharedPrefs.getString(PREF_NOTIFICATION_SOUND, "NOT_FOUND")
        Log.d(TAG, "💾 DEBUG: Verification - saved value: '$savedValue'")
    }
    

    
    // Custom Sound URI Methods
    private fun getCustomAlarmSoundUri(): android.net.Uri? {
        val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val uriString = sharedPrefs.getString(PREF_CUSTOM_ALARM_SOUND_URI, null)
        return if (uriString != null) android.net.Uri.parse(uriString) else null
    }
    
    private fun saveCustomAlarmSound(uri: android.net.Uri) {
        Log.d(TAG, "💾 DEBUG: Saving custom alarm sound URI: $uri")
        val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val editor = sharedPrefs.edit()
        editor.putString(PREF_CUSTOM_ALARM_SOUND_URI, uri.toString())
        val success = editor.apply()
        Log.d(TAG, "💾 DEBUG: Custom alarm sound URI save operation initiated. Success: $success")
        
        // Verify the save
        val savedValue = sharedPrefs.getString(PREF_CUSTOM_ALARM_SOUND_URI, "NOT_FOUND")
        Log.d(TAG, "💾 DEBUG: Verification - saved custom alarm URI: '$savedValue'")
        
        // Also ensure the alarm sound type is set to custom
        saveAlarmSound(SOUND_TYPE_CUSTOM)
    }
    
    private fun getCustomNotificationSoundUri(): android.net.Uri? {
        val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val uriString = sharedPrefs.getString(PREF_CUSTOM_NOTIFICATION_SOUND_URI, null)
        return if (uriString != null) android.net.Uri.parse(uriString) else null
    }
    
    private fun saveCustomNotificationSound(uri: android.net.Uri) {
        Log.d(TAG, "💾 DEBUG: Saving custom notification sound URI: $uri")
        val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val editor = sharedPrefs.edit()
        editor.putString(PREF_CUSTOM_NOTIFICATION_SOUND_URI, uri.toString())
        val success = editor.apply()
        Log.d(TAG, "💾 DEBUG: Custom notification sound URI save operation initiated. Success: $success")
        
        // Verify the save
        val savedValue = sharedPrefs.getString(PREF_CUSTOM_NOTIFICATION_SOUND_URI, "NOT_FOUND")
        Log.d(TAG, "💾 DEBUG: Verification - saved custom notification URI: '$savedValue'")
        
        // Also ensure the notification sound type is set to custom
        saveNotificationSound(SOUND_TYPE_CUSTOM)
    }
    
    private fun getSoundTypeDisplayName(soundType: String): String {
        return when (soundType) {
            SOUND_TYPE_DEFAULT -> "Default"
            SOUND_TYPE_CUSTOM -> "Custom"
            else -> "Default"
        }
    }
    
    /**
     * Launch system alarm sound picker
     */
    private fun launchAlarmSoundPicker() {
        try {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                
                // Set current selection if available
                getCustomAlarmSoundUri()?.let { uri ->
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uri)
                }
            }
            
            alarmSoundPickerLauncher.launch(intent)
            Log.d(TAG, "🎵 Launching system alarm sound picker")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error launching alarm sound picker", e)
            Toast.makeText(this, "Error opening sound picker", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Launch system notification sound picker
     */
    private fun launchNotificationSoundPicker() {
        try {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                
                // Set current selection if available
                getCustomNotificationSoundUri()?.let { uri ->
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uri)
                }
            }
            
            notificationSoundPickerLauncher.launch(intent)
            Log.d(TAG, "🔔 Launching system notification sound picker")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error launching notification sound picker", e)
            Toast.makeText(this, "Error opening sound picker", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Update the display of selected alarm sound
     */
    private fun updateSelectedAlarmSoundDisplay(uri: android.net.Uri) {
        try {
            val ringtone = RingtoneManager.getRingtone(this, uri)
            val soundName = ringtone?.getTitle(this) ?: "Custom Sound"
            tvSelectedAlarmSound.text = "Selected: $soundName"
            Log.d(TAG, "📱 Updated alarm sound display: $soundName")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating alarm sound display", e)
            tvSelectedAlarmSound.text = "Selected: Custom Sound"
        }
    }
    
    /**
     * Update the display of selected notification sound
     */
    private fun updateSelectedNotificationSoundDisplay(uri: android.net.Uri) {
        try {
            val ringtone = RingtoneManager.getRingtone(this, uri)
            val soundName = ringtone?.getTitle(this) ?: "Custom Sound"
            tvSelectedNotificationSound.text = "Selected: $soundName"
            Log.d(TAG, "📱 Updated notification sound display: $soundName")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating notification sound display", e)
            tvSelectedNotificationSound.text = "Selected: Custom Sound"
        }
    }
    
    /**
     * Update custom alarm sound section visibility
     */
    private fun updateCustomAlarmSoundSectionVisibility(show: Boolean) {
        customAlarmSoundSection.visibility = if (show) View.VISIBLE else View.GONE
    }
    
    /**
     * Update custom notification sound section visibility
     */
    private fun updateCustomNotificationSoundSectionVisibility(show: Boolean) {
        customNotificationSoundSection.visibility = if (show) View.VISIBLE else View.GONE
    }
    
    /**
     * Get system sound information for display
     */
    private fun getSystemSoundInfo(soundType: String): String {
        return when (soundType) {
            SOUND_TYPE_CUSTOM -> {
                getCustomAlarmSoundUri()?.let { uri ->
                    try {
                        val ringtone = RingtoneManager.getRingtone(this, uri)
                        ringtone?.getTitle(this) ?: "Custom Sound"
                    } catch (e: Exception) {
                        "Custom Sound"
                    }
                } ?: "Custom Sound"
            }
            else -> "System Sound"
        }
    }
    
    private fun testCurrentAlarmSettings() {
        val alarmSound = getAlarmSound()
        val notificationSound = getNotificationSound()
        val isVibrationEnabled = getVibrationEnabled()
        
        Log.d(TAG, "🔊 Testing alarm with settings: sound=$alarmSound, vibration=$isVibrationEnabled")
        
        val settingsInfo = "Sound: ${getSoundTypeDisplayName(alarmSound)}, Vibration: ${if (isVibrationEnabled) "Enabled" else "Disabled"}"
        Toast.makeText(this, "Testing alarm: $settingsInfo", Toast.LENGTH_LONG).show()
        
        // Test alarm notification
        notificationService.showAlarmNotification(
            "⏰ Test Alarm",
            "This is how your arrival alarm sounds",
            8888
        )
        
        // Test custom alarm sound if enabled
        if (alarmSound != SOUND_TYPE_DEFAULT) {
            notificationService.playCustomAlarmSound(alarmSound, 0.8f)
        }
        
        // Test vibration if enabled
        if (isVibrationEnabled) {
            notificationService.triggerAlarmVibration()
        }
        
        // Test voice announcement if enabled
        if (getVoiceAnnouncementsEnabled()) {
            voiceService.testArrivalAnnouncement()
        } else {
            Toast.makeText(this, "Enable voice announcements to test", Toast.LENGTH_SHORT).show()
        }
        
        // Auto-dismiss test notifications after 3 seconds
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                NotificationService.stopAllAlarms()
                androidx.core.app.NotificationManagerCompat.from(this).apply {
                    cancel(8888)
                }
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }, 3000)
    }
}