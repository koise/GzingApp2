package com.example.gzingapp.services

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*

class VoiceAnnouncementService(private val context: Context) : TextToSpeech.OnInitListener {
    
    companion object {
        private const val TAG = "VoiceAnnouncementService"
        private const val PREF_VOICE_ANNOUNCEMENTS = "voice_announcements"
    }
    
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    
    init {
        initTextToSpeech()
    }
    
    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(context, this)
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.let { tts ->
                val result = tts.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Language not supported for TTS")
                } else {
                    isInitialized = true
                    Log.d(TAG, "TextToSpeech initialized successfully")
                }
            }
        } else {
            Log.e(TAG, "TextToSpeech initialization failed")
        }
    }
    
    /**
     * Check if voice announcements are enabled in settings
     */
    private fun isVoiceAnnouncementsEnabled(): Boolean {
        val sharedPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        return sharedPrefs.getBoolean(PREF_VOICE_ANNOUNCEMENTS, false)
    }
    
    /**
     * Announce arrival at destination
     */
    fun announceArrival(destinationName: String, distanceMeters: Int) {
        if (!isVoiceAnnouncementsEnabled() || !isInitialized) {
            Log.d(TAG, "Voice announcements disabled or TTS not initialized")
            return
        }
        
        val message = "Arriving at $destinationName in $distanceMeters meters"
        speak(message)
        Log.d(TAG, "Voice announcement: $message")
    }
    
    /**
     * Announce arrival at destination without distance
     */
    fun announceArrival(destinationName: String) {
        Log.d(TAG, "announceArrival called for: $destinationName")
        Log.d(TAG, "Voice enabled: ${isVoiceAnnouncementsEnabled()}, TTS initialized: $isInitialized")
        
        // Get GeofenceHelper to check if announcement was already triggered
        val geofenceHelper = GeofenceHelper(context)
        if (geofenceHelper.isVoiceAnnouncementTriggered()) {
            Log.d(TAG, "Voice announcement already triggered for this geofence, skipping")
            return
        }
        
        if (!isVoiceAnnouncementsEnabled()) {
            Log.d(TAG, "Voice announcements are disabled in settings")
            return
        }
        
        if (!isInitialized) {
            Log.e(TAG, "TTS not initialized, attempting to reinitialize")
            initTextToSpeech()
            // Try again after initialization
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (isInitialized) {
                    announceArrival(destinationName)
                } else {
                    Log.e(TAG, "Failed to reinitialize TTS for arrival announcement")
                }
            }, 1000)
            return
        }
        
        val message = "You have arrived at $destinationName"
        speak(message)
        Log.d(TAG, "Voice announcement triggered: $message")
        
        // Mark that voice announcement has been triggered
        geofenceHelper.setVoiceAnnouncementTriggered(true)
    }
    
    /**
     * General announcement method
     */
    fun announce(message: String) {
        if (!isVoiceAnnouncementsEnabled() || !isInitialized) {
            Log.d(TAG, "Voice announcements disabled or TTS not initialized")
            return
        }
        
        speak(message)
        Log.d(TAG, "Voice announcement: $message")
    }
    
    /**
     * Speak the given text with enhanced error handling
     */
    private fun speak(text: String) {
        try {
            textToSpeech?.let { tts ->
                Log.d(TAG, "Attempting to speak: $text")
                val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TTS_REQUEST_${System.currentTimeMillis()}")
                when (result) {
                    TextToSpeech.SUCCESS -> Log.d(TAG, "TTS request successful for: $text")
                    TextToSpeech.ERROR -> {
                        Log.e(TAG, "TTS request failed with ERROR for: $text")
                        // Try to reinitialize TTS
                        initTextToSpeech()
                    }
                    else -> Log.w(TAG, "TTS request returned: $result for: $text")
                }
            } ?: run {
                Log.e(TAG, "TextToSpeech instance is null, attempting to reinitialize")
                initTextToSpeech()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception while speaking text: $text", e)
        }
    }
    
    /**
     * Stop any ongoing speech
     */
    fun stop() {
        try {
            textToSpeech?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS: ${e.message}")
        }
    }
    
    /**
     * Release TTS resources
     */
    fun shutdown() {
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
            isInitialized = false
            Log.d(TAG, "TextToSpeech shut down")
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS: ${e.message}")
        }
    }
    
    /**
     * Check if TTS is available and ready
     */
    fun isAvailable(): Boolean {
        return isInitialized && textToSpeech != null
    }
}