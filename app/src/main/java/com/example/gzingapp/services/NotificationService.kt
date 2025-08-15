package com.example.gzingapp.services

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.gzingapp.R
import com.example.gzingapp.receivers.AlarmReceiver
import com.example.gzingapp.receivers.StopAlarmReceiver
import com.example.gzingapp.ui.dashboard.DashboardActivity
import java.util.Calendar
import com.example.gzingapp.receivers.GeofenceBroadcastReceiver

class NotificationService(private val context: Context) {

    /**
     * Clean up location names for better readability in notifications
     */
    private fun getCleanLocationName(address: String): String {
        return try {
            when {
                // Remove coordinates pattern like "14.123456, 121.789012" 
                address.matches(Regex("^\\d+\\.\\d+,\\s*\\d+\\.\\d+$")) -> {
                    "Selected Location"
                }
                // Remove long addresses by taking first significant part
                address.length > 50 -> {
                    val parts = address.split(",")
                    val mainPart = parts.firstOrNull { part ->
                        val trimmed = part.trim()
                        trimmed.isNotBlank() && 
                        !trimmed.matches(Regex("^\\d+$")) && // Skip pure numbers
                        !trimmed.matches(Regex("^\\d{4}$")) && // Skip zip codes
                        trimmed.length > 3
                    }
                    mainPart?.trim()?.take(40) ?: "Destination"
                }
                // Keep normal addresses as-is
                else -> address
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning location name: $address", e)
            "Destination"
        }
    }

    companion object {
        const val CHANNEL_ID = "GzingAppChannel"
        const val ALARM_CHANNEL_ID = "GzingAppAlarmChannel"
        const val NAVIGATION_CHANNEL_ID = "GzingAppNavigationChannel"
        const val CHANNEL_NAME = "GzingApp Notifications"
        const val ALARM_CHANNEL_NAME = "GzingApp Alarms"
        const val NAVIGATION_CHANNEL_NAME = "GzingApp Navigation"
        const val CHANNEL_DESCRIPTION = "Notifications from GzingApp"
        const val ALARM_CHANNEL_DESCRIPTION = "Critical alarm notifications from GzingApp"
        const val NAVIGATION_CHANNEL_DESCRIPTION = "Navigation status and updates from GzingApp"
        private const val TAG = "NotificationService"
        const val STOP_ALARM_ACTION = "STOP_ALARM_ACTION"
        const val STOP_NAVIGATION_ACTION = "STOP_NAVIGATION_ACTION"

        // Notification IDs
        const val NAVIGATION_ONGOING_ID = 3001
        const val NAVIGATION_STARTED_ID = 3002
        const val NAVIGATION_STOPPED_ID = 3003
        const val NAVIGATION_ARRIVED_ID = 3004
        const val NAVIGATION_CANCELLED_ID = 3005
        
        // Request codes
        const val RESTART_NAVIGATION_REQUEST_CODE = 4001

        // Static references for stopping alarm from anywhere
        private var currentMediaPlayer: MediaPlayer? = null
        private var currentVibrator: Vibrator? = null
        private var isAlarmPlaying = false

        // Enhanced static method to stop alarm sound
        fun stopAlarmSound() {
            try {
                currentMediaPlayer?.let { player ->
                    Log.d(TAG, "Stopping alarm sound - isPlaying: ${player.isPlaying}")

                    // Force stop if playing
                    if (player.isPlaying) {
                        player.stop()
                        Log.d(TAG, "MediaPlayer stopped")
                    }

                    // Reset and release with error handling
                    try {
                        player.reset()
                        Log.d(TAG, "MediaPlayer reset")
                    } catch (e: Exception) {
                        Log.w(TAG, "Error during MediaPlayer reset", e)
                    }

                    try {
                        player.release()
                        Log.d(TAG, "MediaPlayer released")
                    } catch (e: Exception) {
                        Log.w(TAG, "Error during MediaPlayer release", e)
                    }

                    // Clear reference
                    currentMediaPlayer = null
                    isAlarmPlaying = false
                    Log.d(TAG, "Static: Alarm sound stopped completely")
                } ?: run {
                    Log.d(TAG, "No MediaPlayer to stop")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Static: Error stopping alarm sound", e)

                // Force cleanup even on error
                try {
                    currentMediaPlayer = null
                    isAlarmPlaying = false
                    Log.d(TAG, "Forced cleanup completed")
                } catch (cleanupError: Exception) {
                    Log.e(TAG, "Error during forced cleanup", cleanupError)
                }
            }
        }

        /**
         * Stop alarm sound gradually with fade out effect
         */
        fun stopAlarmSoundGradually() {
            try {
                currentMediaPlayer?.let { player ->
                    Log.d(TAG, "Starting gradual alarm sound stop with fade out")
                    
                    if (player.isPlaying) {
                        // Create a gradual volume reduction effect
                        val fadeOutDuration = 2000L // 2 seconds fade out
                        val fadeOutSteps = 20
                        val volumeStep = 1.0f / fadeOutSteps
                        val stepDelay = fadeOutDuration / fadeOutSteps
                        
                        var currentVolume = 1.0f
                        
                        val fadeOutRunnable = object : Runnable {
                            override fun run() {
                                if (currentVolume > 0f && isAlarmPlaying) {
                                    currentVolume -= volumeStep
                                    if (currentVolume < 0f) currentVolume = 0f
                                    
                                    try {
                                        player.setVolume(currentVolume, currentVolume)
                                        Log.d(TAG, "Fade out step: volume = ${String.format("%.2f", currentVolume)}")
                                        
                                        if (currentVolume > 0f) {
                                            // Schedule next fade step
                                            android.os.Handler(android.os.Looper.getMainLooper())
                                                .postDelayed(this, stepDelay)
                                        } else {
                                            // Volume is now 0, stop the player
                                            Log.d(TAG, "Fade out complete, stopping player")
                                            player.stop()
                                            player.reset()
                                            player.release()
                                            currentMediaPlayer = null
                                            isAlarmPlaying = false
                                            Log.d(TAG, "Gradual alarm sound stop completed")
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error during fade out step", e)
                                        // Fallback to immediate stop
                                        stopAlarmSound()
                                    }
                                }
                            }
                        }
                        
                        // Start the fade out process
                        android.os.Handler(android.os.Looper.getMainLooper())
                            .post(fadeOutRunnable)
                            
                    } else {
                        Log.d(TAG, "Player not playing, stopping immediately")
                        stopAlarmSound()
                    }
                } ?: run {
                    Log.d(TAG, "No MediaPlayer to stop gradually")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in gradual alarm sound stop", e)
                // Fallback to immediate stop
                stopAlarmSound()
            }
        }

        /**
         * Stop alarm vibration gradually
         */
        fun stopAlarmVibrationGradually() {
            try {
                currentVibrator?.let { vibrator ->
                    Log.d(TAG, "Starting gradual vibration stop")
                    
                    // Create a gradual vibration reduction effect
                    val fadeOutDuration = 1500L // 1.5 seconds fade out
                    val fadeOutSteps = 15
                    val stepDelay = fadeOutDuration / fadeOutSteps
                    
                    var currentStep = 0
                    
                    val fadeOutRunnable = object : Runnable {
                        override fun run() {
                            if (currentStep < fadeOutSteps && isAlarmPlaying) {
                                currentStep++
                                
                                // Reduce vibration intensity gradually
                                val intensity = 1.0f - (currentStep.toFloat() / fadeOutSteps.toFloat())
                                
                                try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        val effect = android.os.VibrationEffect.createOneShot(
                                            stepDelay,
                                            (intensity * 255).toInt()
                                        )
                                        vibrator.vibrate(effect)
                                    } else {
                                        @Suppress("DEPRECATION")
                                        vibrator.vibrate(stepDelay)
                                    }
                                    
                                    Log.d(TAG, "Fade out vibration step: $currentStep/$fadeOutSteps, intensity: ${String.format("%.2f", intensity)}")
                                    
                                    // Schedule next fade step
                                    android.os.Handler(android.os.Looper.getMainLooper())
                                        .postDelayed(this, stepDelay)
                                        
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error during vibration fade step", e)
                                    // Fallback to immediate stop
                                    stopAlarmVibration()
                                }
                            } else {
                                // Fade out complete, stop vibration
                                Log.d(TAG, "Vibration fade out complete, stopping")
                                vibrator.cancel()
                                currentVibrator = null
                                Log.d(TAG, "Gradual vibration stop completed")
                            }
                        }
                    }
                    
                    // Start the fade out process
                    android.os.Handler(android.os.Looper.getMainLooper())
                        .post(fadeOutRunnable)
                        
                } ?: run {
                    Log.d(TAG, "No vibrator to stop gradually")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in gradual vibration stop", e)
                // Fallback to immediate stop
                stopAlarmVibration()
            }
        }

        /**
         * Stop all alarms gradually with fade out effects
         */
        fun stopAllAlarmsGradually() {
            Log.d(TAG, "Stopping all alarms gradually with fade out effects")
            stopAlarmSoundGradually()
            stopAlarmVibrationGradually()
            Log.d(TAG, "Gradual alarm stop initiated")
        }

        fun stopAlarmVibration() {
            try {
                currentVibrator?.let { vibrator ->
                    vibrator.cancel()
                    Log.d(TAG, "Vibration cancelled")
                    currentVibrator = null
                    Log.d(TAG, "Static: Alarm vibration stopped")
                } ?: run {
                    Log.d(TAG, "No vibrator to stop")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Static: Error stopping alarm vibration", e)

                // Force cleanup
                currentVibrator = null
            }
        }

        fun stopAllAlarms() {
            Log.d(TAG, "Stopping all alarms (sound + vibration)")
            stopAlarmSound()
            stopAlarmVibration()
            Log.d(TAG, "All alarms stopped")
        }
    }

    private var localVibrator: Vibrator? = null

    init {
        createNotificationChannels()
        initializeVibrator()
    }

    /**
     * Initialize vibrator with proper checks
     */
    private fun initializeVibrator() {
        if (localVibrator != null) return // Already initialized
        
        try {
            localVibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager?
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
            }

            // Also set the static reference
            currentVibrator = localVibrator
            
            // Check if vibrator is available
            if (localVibrator?.hasVibrator() == true) {
                Log.d(TAG, "Vibrator initialized successfully")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && localVibrator?.hasAmplitudeControl() == true) {
                    Log.d(TAG, "Device supports amplitude control vibration")
                }
            } else {
                Log.w(TAG, "Device does not have vibrator capability")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing vibrator", e)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Regular notification channel
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500)
                setShowBadge(true)
            }

            // Alarm notification channel
            val alarmChannel = NotificationChannel(ALARM_CHANNEL_ID, ALARM_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = ALARM_CHANNEL_DESCRIPTION
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC

                // Set system alarm sound
                val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()

                setSound(alarmSound, audioAttributes)
            }

            // Navigation notification channel
            val navigationChannel = NotificationChannel(NAVIGATION_CHANNEL_ID, NAVIGATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = NAVIGATION_CHANNEL_DESCRIPTION
                enableLights(false)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 200)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(channel)
            notificationManager.createNotificationChannel(alarmChannel)
            notificationManager.createNotificationChannel(navigationChannel)

            Log.d(TAG, "Notification channels created")
        }
    }

    fun showNotification(title: String, message: String, notificationId: Int, includeStopAction: Boolean = false) {
        val intent = Intent(context, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setFullScreenIntent(pendingIntent, true)

        // Add stop action if requested
        if (includeStopAction) {
            // Create Stop action
            val stopIntent = Intent(context, StopAlarmReceiver::class.java).apply {
                action = STOP_ALARM_ACTION
                putExtra("notificationId", notificationId)
            }

            val stopPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 1000,
                stopIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            builder.addAction(
                R.drawable.ic_stop,
                "Stop",
                stopPendingIntent
            )
        }

        // Trigger vibration manually
        triggerVibration(longArrayOf(0, 500, 250, 500))

        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            ) {
                notify(notificationId, builder.build())
                Log.d(TAG, "Notification shown with ID: $notificationId")
            } else {
                Log.e(TAG, "POST_NOTIFICATIONS permission not granted")
            }
        }
    }

    /**
     * Show ongoing navigation notification
     */
    fun showNavigationOngoingNotification(destination: String, duration: String = "") {
        val intent = Intent(context, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Create Stop Navigation action
        val stopNavigationIntent = Intent(context, StopAlarmReceiver::class.java).apply {
            action = STOP_NAVIGATION_ACTION
            putExtra("notificationId", NAVIGATION_ONGOING_ID)
        }

        val stopNavigationPendingIntent = PendingIntent.getBroadcast(
            context,
            NAVIGATION_ONGOING_ID + 1000,
            stopNavigationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val cleanDestination = getCleanLocationName(destination)
        val contentText = if (duration.isNotEmpty()) {
            "Navigating to: $cleanDestination • $duration"
        } else {
            "Navigating to: $cleanDestination"
        }

        val builder = NotificationCompat.Builder(context, NAVIGATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_navigation)
            .setContentTitle("🧭 Navigation Active")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                R.drawable.ic_stop,
                "Stop Navigation",
                stopNavigationPendingIntent
            )
            .setColor(context.getColor(R.color.primary_brown))

        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            ) {
                notify(NAVIGATION_ONGOING_ID, builder.build())
                Log.d(TAG, "Navigation ongoing notification shown")
            } else {
                Log.e(TAG, "POST_NOTIFICATIONS permission not granted")
            }
        }
    }

    /**
     * Show navigation started notification
     */
    fun showNavigationStartedNotification(destination: String) {
        val intent = Intent(context, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Create Stop Navigation action
        val stopNavigationIntent = Intent(context, StopAlarmReceiver::class.java).apply {
            action = STOP_NAVIGATION_ACTION
            putExtra("notificationId", NAVIGATION_STARTED_ID)
        }

        val stopNavigationPendingIntent = PendingIntent.getBroadcast(
            context,
            NAVIGATION_STARTED_ID + 1000,
            stopNavigationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, NAVIGATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_navigation)
            .setContentTitle("🧭 Navigation Started")
            .setContentText("Navigating to: $destination")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Navigation has started to: $destination\n\nYou'll receive an alarm notification when you arrive at your destination."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                R.drawable.ic_stop,
                "Stop Navigation",
                stopNavigationPendingIntent
            )
            .setColor(context.getColor(R.color.navigation_active))

        // Trigger gentle vibration for navigation start
        triggerVibration(longArrayOf(0, 200, 100, 200))

        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            ) {
                notify(NAVIGATION_STARTED_ID, builder.build())
                Log.d(TAG, "Navigation started notification shown")

                // Auto-dismiss after 5 seconds to avoid clutter
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    cancel(NAVIGATION_STARTED_ID)
                }, 5000)
            } else {
                Log.e(TAG, "POST_NOTIFICATIONS permission not granted")
            }
        }
    }

    /**
     * Show navigation stopped notification
     */
    fun showNavigationStoppedNotification(reason: String = "Navigation stopped") {
        val intent = Intent(context, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, NAVIGATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stop)
            .setContentTitle("🛑 Navigation Stopped")
            .setContentText(reason)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(context.getColor(R.color.text_secondary))

        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            ) {
                notify(NAVIGATION_STOPPED_ID, builder.build())
                Log.d(TAG, "Navigation stopped notification shown")

                // Auto-dismiss after 3 seconds
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    cancel(NAVIGATION_STOPPED_ID)
                }, 3000)
            } else {
                Log.e(TAG, "POST_NOTIFICATIONS permission not granted")
            }
        }
    }
    
    /**
     * Show navigation cancelled notification with restart option
     */
    fun showNavigationCancelledNotification(message: String) {
        Log.d(TAG, "Showing navigation cancelled notification: $message")
        
        val mainIntent = Intent(context, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("restart_navigation", true)
        }
        
        val mainPendingIntent = PendingIntent.getActivity(
            context,
            RESTART_NAVIGATION_REQUEST_CODE,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val builder = NotificationCompat.Builder(context, NAVIGATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stop)
            .setContentTitle("🚫 Navigation Cancelled")
            .setContentText(message)
            .setContentIntent(mainPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(context.getColor(R.color.navigation_error))
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .addAction(
                R.drawable.ic_navigation,
                "Restart Navigation",
                mainPendingIntent
            )

        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            ) {
                notify(NAVIGATION_CANCELLED_ID, builder.build())
                Log.d(TAG, "Navigation cancelled notification displayed")
            } else {
                Log.w(TAG, "Cannot show notification - missing POST_NOTIFICATIONS permission")
            }
        }
    }

    /**
     * Show destination arrived notification
     */
    fun showDestinationArrivedNotification(destination: String) {
        val intent = Intent(context, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Create Stop action
        val stopIntent = Intent(context, StopAlarmReceiver::class.java).apply {
            action = STOP_ALARM_ACTION
            putExtra("notificationId", NAVIGATION_ARRIVED_ID)
        }

        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            NAVIGATION_ARRIVED_ID + 1000,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, NAVIGATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_navigation)
            .setContentTitle("🎯 Destination Reached!")
            .setContentText("You have arrived at: $destination")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Congratulations! You have successfully arrived at your destination: $destination"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(context.getColor(R.color.success))
            .addAction(
                R.drawable.ic_stop,
                "Stop",
                stopPendingIntent
            )

        // Celebration vibration pattern
        triggerVibration(longArrayOf(0, 300, 100, 300, 100, 300))

        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            ) {
                notify(NAVIGATION_ARRIVED_ID, builder.build())
                Log.d(TAG, "Destination arrived notification shown")
            } else {
                Log.e(TAG, "POST_NOTIFICATIONS permission not granted")
            }
        }
    }

    /**
     * Update ongoing navigation notification with new info
     */
    fun updateNavigationOngoingNotification(destination: String, duration: String, distance: String = "") {
        val contentText = buildString {
            append("To: $destination")
            if (duration.isNotEmpty()) append(" • $duration")
            if (distance.isNotEmpty()) append(" • $distance")
        }

        showNavigationOngoingNotification(destination, if (duration.isNotEmpty()) "$duration${if (distance.isNotEmpty()) " • $distance" else ""}" else distance)
    }

    /**
     * Clear navigation notifications
     */
    fun clearNavigationNotifications() {
        try {
            val notificationManager = NotificationManagerCompat.from(context)
            
            // Clear all navigation notification IDs
            notificationManager.cancel(NAVIGATION_ONGOING_ID)
            notificationManager.cancel(NAVIGATION_STARTED_ID)
            notificationManager.cancel(NAVIGATION_STOPPED_ID)
            notificationManager.cancel(NAVIGATION_ARRIVED_ID)
            
            // Also clear any alarm notifications that might be related to navigation
            notificationManager.cancel(GeofenceBroadcastReceiver.ARRIVAL_ALARM_ID)
            
            Log.d(TAG, "All navigation notifications cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing navigation notifications", e)
        }
    }

    /**
     * Clear all navigation related notifications
     */
    fun clearAllNavigationNotifications() {
        try {
            val notificationManager = NotificationManagerCompat.from(context)
            
            // Clear all navigation notification IDs
            notificationManager.cancel(NAVIGATION_ONGOING_ID)
            notificationManager.cancel(NAVIGATION_STARTED_ID)
            notificationManager.cancel(NAVIGATION_STOPPED_ID)
            notificationManager.cancel(NAVIGATION_ARRIVED_ID)
            
            // Also clear any alarm notifications that might be related to navigation
            notificationManager.cancel(GeofenceBroadcastReceiver.ARRIVAL_ALARM_ID)
            
            Log.d(TAG, "All navigation notifications cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing all navigation notifications", e)
        }
    }

    /**
     * Clear alarm notifications
     */
    fun clearAlarmNotifications() {
        try {
            val notificationManager = NotificationManagerCompat.from(context)
            
            // Clear all alarm notification IDs
            notificationManager.cancel(NAVIGATION_ARRIVED_ID)
            notificationManager.cancel(GeofenceBroadcastReceiver.ARRIVAL_ALARM_ID)
            
            // Clear any other alarm-related notifications
            notificationManager.cancel(9999) // SOS notification ID
            
            Log.d(TAG, "All alarm notifications cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing alarm notifications", e)
        }
    }

    /**
     * Clear all notifications immediately
     */
    fun clearAllNotifications() {
        try {
            val notificationManager = NotificationManagerCompat.from(context)
            
            // Clear all specific notification IDs
            notificationManager.cancel(NAVIGATION_ONGOING_ID)
            notificationManager.cancel(NAVIGATION_STARTED_ID)
            notificationManager.cancel(NAVIGATION_STOPPED_ID)
            notificationManager.cancel(NAVIGATION_ARRIVED_ID)
            
            // Clear alarm notifications
            notificationManager.cancel(GeofenceBroadcastReceiver.ARRIVAL_ALARM_ID)
            
            // Clear any other notifications by ID
            notificationManager.cancel(9999) // SOS notification ID
            
            // Nuclear option - clear ALL notifications
            notificationManager.cancelAll()
            
            Log.d(TAG, "All notifications cleared immediately")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing all notifications", e)
        }
    }

    fun showAlarmNotification(title: String, message: String, notificationId: Int) {
        Log.d(TAG, "🚨 DEBUG: Starting showAlarmNotification()")
        Log.d(TAG, "🚨 DEBUG: Parameters - title: '$title', message: '$message', notificationId: $notificationId")
        
        // Debug: Dump all sound preferences
        debugDumpSoundPreferences()
        
        // Check user's alarm sound preferences
        val alarmSound = getAlarmSound()
        val notificationSound = getNotificationSound()
        val isVibrationEnabled = getStrongVibrationEnabled()
        
        Log.d(TAG, "🔊 DEBUG: Alarm settings retrieved:")
        Log.d(TAG, "🔊 DEBUG:   - alarm_sound: '$alarmSound'")
        Log.d(TAG, "🔊 DEBUG:   - notification_sound: '$notificationSound'")
        Log.d(TAG, "🔊 DEBUG:   - vibration_enabled: $isVibrationEnabled")
        
        val intent = Intent(context, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Create Stop Alarm action
        val stopAlarmIntent = Intent(context, StopAlarmReceiver::class.java).apply {
            action = STOP_ALARM_ACTION
            putExtra("notificationId", notificationId)
            putExtra("notificationTitle", title)
            putExtra("notificationMessage", message)
        }

        val stopAlarmPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1000,
            stopAlarmIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        Log.d(TAG, "🔔 DEBUG: Building notification with ALARM_CHANNEL_ID")
        val builder = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .addAction(
                R.drawable.ic_stop,
                "Stop Alarm",
                stopAlarmPendingIntent
            )

        // Apply alarm sound settings based on user preferences
        Log.d(TAG, "🎵 DEBUG: Applying alarm sound settings...")
        if (alarmSound == "custom") {
            // Play custom alarm sound
            val alarmVolume = getAlarmVolume()
            Log.d(TAG, "🎵 DEBUG: Alarm sound is 'custom', calling playCustomAlarmSound()")
            Log.d(TAG, "🎵 DEBUG: Alarm volume: $alarmVolume")
            playCustomAlarmSound(alarmSound, alarmVolume)
        } else {
            // Play default system alarm sound
            Log.d(TAG, "🔔 DEBUG: Alarm sound is not 'custom' (value: '$alarmSound'), calling playAlarmSound()")
            playAlarmSound()
        }
        
        // Apply vibration if enabled
        if (isVibrationEnabled) {
            Log.d(TAG, "📳 DEBUG: Vibration is enabled, triggering alarm vibration")
            triggerAlarmVibration()
        } else {
            Log.d(TAG, "📳 DEBUG: Vibration is disabled")
        }

        Log.d(TAG, "🔔 DEBUG: Showing notification with NotificationManagerCompat")
        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            ) {
                notify(notificationId, builder.build())
                Log.d(TAG, "✅ DEBUG: Alarm notification shown successfully with ID: $notificationId")
            } else {
                Log.e(TAG, "❌ DEBUG: POST_NOTIFICATIONS permission not granted, still trying to show notification")
                // Try to show anyway for older Android versions
                notify(notificationId, builder.build())
                Log.d(TAG, "⚠️ DEBUG: Notification shown without permission (older Android version)")
            }
        }
        
        Log.d(TAG, "🚨 DEBUG: showAlarmNotification() completed")
    }

    /**
     * Update alarm notification to show "Alarm stopped" status
     */
    fun updateAlarmNotificationToStopped(notificationId: Int, title: String, message: String) {
        try {
            Log.d(TAG, "🔄 Updating alarm notification $notificationId to show 'stopped' status")
            
            val intent = Intent(context, DashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            // Create a new notification showing "Alarm stopped"
            val builder = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stop)
                .setContentTitle("🔇 $title")
                .setContentText("$message - Alarm stopped")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOngoing(false)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(NotificationCompat.DEFAULT_NONE)
                .setColor(android.graphics.Color.GRAY)

            // Show the updated notification
            with(NotificationManagerCompat.from(context)) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                ) {
                    notify(notificationId, builder.build())
                    Log.d(TAG, "✅ Alarm notification updated to 'stopped' status with ID: $notificationId")
                } else {
                    Log.e(TAG, "❌ POST_NOTIFICATIONS permission not granted")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating alarm notification to stopped status", e)
        }
    }

    /**
     * Show alarm notification with enhanced settings
     */
    fun playAlarmSound() {
        try {
            Log.d(TAG, "Attempting to play alarm sound...")
            
            // Stop any existing sound using static method
            stopAlarmSound()

            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            Log.d(TAG, "Using alarm URI: $alarmUri")

            currentMediaPlayer = MediaPlayer().apply {
                try {
                    setDataSource(context, alarmUri)
                    isLooping = true // Loop the alarm sound
                    setVolume(1.0f, 1.0f) // Max volume

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                                .build()
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        setAudioStreamType(android.media.AudioManager.STREAM_ALARM)
                    }

                    prepareAsync()
                    setOnPreparedListener {
                        start()
                        isAlarmPlaying = true
                        Log.d(TAG, "Alarm sound started successfully!")
                    }
                    setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                        isAlarmPlaying = false
                        false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting up MediaPlayer", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing alarm sound", e)
        }
    }

    private fun triggerVibration(pattern: LongArray) {
        localVibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(
                    android.os.VibrationEffect.createWaveform(pattern, -1),
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(pattern, -1)
            }
            Log.d(TAG, "Vibration triggered")
        }
    }

    /**
     * Trigger enhanced SOS emergency vibration pattern
     */
    fun triggerSOSVibration() {
        try {
            // SOS pattern in Morse code: ... --- ... (short-short-short long-long-long short-short-short)
            val sosPattern = longArrayOf(
                0,    // Initial delay
                200, 100, 200, 100, 200, 300,  // ... (SOS start)
                600, 200, 600, 200, 600, 300,  // --- (middle)
                200, 100, 200, 100, 200, 1000  // ... (SOS end)
            )

            initializeVibrator()
            currentVibrator?.let { vibrator ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Check if device supports amplitude control
                    if (vibrator.hasAmplitudeControl()) {
                        val amplitudes = intArrayOf(
                            0,    // Initial delay
                            255, 0, 255, 0, 255, 0,     // High amplitude dots
                            200, 0, 200, 0, 200, 0,     // Medium amplitude dashes
                            255, 0, 255, 0, 255, 0      // High amplitude dots
                        )
                        
                        vibrator.vibrate(
                            android.os.VibrationEffect.createWaveform(sosPattern, amplitudes, 2), // Repeat from position 2
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                                .build()
                        )
                        Log.d(TAG, "SOS vibration with amplitude control triggered")
                    } else {
                        vibrator.vibrate(
                            android.os.VibrationEffect.createWaveform(sosPattern, 2), // Repeat from position 2
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                                .build()
                        )
                        Log.d(TAG, "SOS vibration triggered (no amplitude control)")
                    }
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(sosPattern, 2) // Repeat pattern from position 2
                    Log.d(TAG, "SOS vibration triggered (legacy)")
                }
            } ?: run {
                Log.w(TAG, "No vibrator available for SOS")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering SOS vibration", e)
        }
    }

    fun triggerAlarmVibration() {
        val alarmPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000, 500, 1000)

        initializeVibrator()
        // Use static reference so it can be stopped from receiver
        currentVibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(
                    android.os.VibrationEffect.createWaveform(alarmPattern, 0), // Repeat pattern
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()
                )
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(alarmPattern, 0) // Repeat pattern
            }
            Log.d(TAG, "Alarm vibration triggered (static reference)")
        }
    }

    fun dismissAlarmNotification(notificationId: Int) {
        // Use static methods to ensure alarm stops
        stopAlarmSound()
        stopAlarmVibration()

        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(notificationId)

        Log.d(TAG, "Alarm notification dismissed: $notificationId")
    }

    fun scheduleAlarm(timeInMillis: Long, title: String, message: String, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("title", title)
            putExtra("message", message)
            putExtra("notificationId", requestCode)
            putExtra("isAlarm", true)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Set exact alarm based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    timeInMillis,
                    pendingIntent
                )
                Log.d(TAG, "Exact alarm scheduled for: $timeInMillis")
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    timeInMillis,
                    pendingIntent
                )
                Log.d(TAG, "Inexact alarm scheduled for: $timeInMillis (no exact alarm permission)")
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                timeInMillis,
                pendingIntent
            )
            Log.d(TAG, "Exact alarm scheduled for: $timeInMillis")
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                timeInMillis,
                pendingIntent
            )
            Log.d(TAG, "Exact alarm scheduled for: $timeInMillis")
        }
    }

    fun scheduleTestAlarm() {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = System.currentTimeMillis()
        calendar.add(Calendar.SECOND, 5)

        Log.d(TAG, "Scheduling test alarm for: ${calendar.timeInMillis}")

        scheduleAlarm(
            calendar.timeInMillis,
            "🚨 PROXIMITY ALARM!",
            "You are approaching the restricted area!",
            1001
        )
    }

    fun scheduleTestNotification() {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = System.currentTimeMillis()
        calendar.add(Calendar.SECOND, 3)

        Log.d(TAG, "Scheduling test notification for: ${calendar.timeInMillis}")

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("title", "📱 Test Notification")
            putExtra("message", "This is a test notification with vibration!")
            putExtra("notificationId", 1002)
            putExtra("isAlarm", false)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1002,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
    }

    fun cancelAlarm(requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Alarm canceled: $requestCode")
        } else {
            Log.d(TAG, "No alarm found to cancel with ID: $requestCode")
        }
    }
    
    // Alarm preference helper methods
    
    private fun getStrongVibrationEnabled(): Boolean {
        val sharedPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        return sharedPrefs.getBoolean("strong_vibration", true)
    }
    
    // Enhanced alarm sound and volume methods
    private fun getAlarmSound(): String {
        val sharedPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val alarmSound = sharedPrefs.getString("alarm_sound", "default") ?: "default"
        Log.d(TAG, "🔍 DEBUG: getAlarmSound() - retrieved: '$alarmSound' from preferences")
        return alarmSound
    }
    
    private fun getAlarmVolume(): Float {
        val sharedPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val alarmVolume = sharedPrefs.getFloat("alarm_volume", 0.6f)
        Log.d(TAG, "🔍 DEBUG: getAlarmVolume() - retrieved: $alarmVolume from preferences")
        return alarmVolume
    }
    
    private fun getNotificationSound(): String {
        val sharedPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val notificationSound = sharedPrefs.getString("notification_sound", "default") ?: "default"
        Log.d(TAG, "🔍 DEBUG: getNotificationSound() - retrieved: '$notificationSound' from preferences")
        return notificationSound
    }
    
    private fun getNotificationVolume(): Float {
        val sharedPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val notificationVolume = sharedPrefs.getFloat("notification_volume", 0.6f)
        Log.d(TAG, "🔍 DEBUG: getNotificationVolume() - retrieved: $notificationVolume from preferences")
        return notificationVolume
    }
    
    /**
     * Play custom alarm sound based on user preferences
     */
    fun playCustomAlarmSound(soundType: String, volume: Float) {
        try {
            Log.d(TAG, "🔊 DEBUG: Starting playCustomAlarmSound()")
            Log.d(TAG, "🔊 DEBUG: Parameters - soundType: '$soundType', volume: $volume")
            
            // Stop any currently playing sound
            Log.d(TAG, "🛑 DEBUG: Stopping any currently playing alarm sound")
            stopAlarmSound()
            
            // Get the appropriate sound URI
            Log.d(TAG, "🔍 DEBUG: Determining sound URI for type: '$soundType'")
            val soundUri = if (soundType == "custom") {
                Log.d(TAG, "🎵 DEBUG: Sound type is 'custom', calling getCustomAlarmUri()")
                getCustomAlarmUri()
            } else {
                Log.d(TAG, "🔔 DEBUG: Sound type is not 'custom', using default system alarm")
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            }
            
            Log.d(TAG, "🎵 DEBUG: Final sound URI selected: $soundUri")
            
            // Verify URI is not null
            if (soundUri == null) {
                Log.e(TAG, "❌ DEBUG: Sound URI is null, cannot play sound")
                return
            }
            
            Log.d(TAG, "🎵 DEBUG: Creating MediaPlayer with URI: $soundUri")
            currentMediaPlayer = MediaPlayer().apply {
                setDataSource(context, soundUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setVolume(volume, volume)
                isLooping = true
                Log.d(TAG, "🎵 DEBUG: MediaPlayer configured, preparing...")
                prepare()
                Log.d(TAG, "🎵 DEBUG: MediaPlayer prepared, starting playback...")
                start()
            }
            
            isAlarmPlaying = true
            Log.d(TAG, "✅ DEBUG: Custom alarm sound started successfully")
            Log.d(TAG, "✅ DEBUG: isAlarmPlaying set to: $isAlarmPlaying")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ DEBUG: Exception in playCustomAlarmSound()", e)
            Log.d(TAG, "🔄 DEBUG: Falling back to default alarm sound")
            // Fallback to default alarm sound
            playAlarmSound()
        }
    }
    
    /**
     * Get system alarm sound URI based on sound type
     */
    private fun getSystemAlarmUri(soundType: String): android.net.Uri {
        return try {
            when (soundType) {
                "custom" -> {
                    // Try to get a custom alarm sound if available
                    getCustomAlarmUri()
                }
                else -> {
                    // Default to system alarm sound
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting system alarm URI for type: $soundType", e)
            // Fallback to default alarm sound
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        }
    }
    
    /**
     * Get custom alarm URI from system or app resources
     */
    private fun getCustomAlarmUri(): android.net.Uri {
        return try {
            Log.d(TAG, "🔍 DEBUG: Starting getCustomAlarmUri()")
            
            // First try to get a custom alarm from user preferences
            val sharedPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            Log.d(TAG, "🔍 DEBUG: SharedPreferences initialized: $sharedPrefs")
            
            val customAlarmUriString = sharedPrefs.getString("custom_alarm_sound_uri", null)
            Log.d(TAG, "🔍 DEBUG: Retrieved custom_alarm_sound_uri from preferences: '$customAlarmUriString'")
            
            if (customAlarmUriString != null) {
                val customUri = android.net.Uri.parse(customAlarmUriString)
                Log.d(TAG, "🎵 DEBUG: Successfully parsed custom alarm URI: $customUri")
                
                // Verify the URI is accessible
                try {
                    val ringtone = RingtoneManager.getRingtone(context, customUri)
                    if (ringtone != null) {
                        val title = ringtone.getTitle(context)
                        Log.d(TAG, "✅ DEBUG: Custom alarm sound is accessible, title: '$title'")
                        return customUri
                    } else {
                        Log.w(TAG, "⚠️ DEBUG: Custom alarm URI exists but RingtoneManager.getRingtone returned null")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ DEBUG: Error accessing custom alarm URI: $customUri", e)
                }
            } else {
                Log.w(TAG, "⚠️ DEBUG: No custom alarm sound URI found in preferences")
            }
            
            // Fallback to default system alarm
            val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            Log.d(TAG, "🔄 DEBUG: Falling back to default system alarm URI: $defaultUri")
            return defaultUri
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ DEBUG: Exception in getCustomAlarmUri()", e)
            val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            Log.d(TAG, "🔄 DEBUG: Exception fallback to default URI: $fallbackUri")
            return fallbackUri
        }
    }
    
    /**
     * Play custom notification sound based on user preferences
     */
    fun playCustomNotificationSound(soundType: String, volume: Float) {
        try {
            Log.d(TAG, "🔔 Playing custom notification sound: $soundType with volume: $volume")
            
            // Get the appropriate sound URI
            val soundUri = if (soundType == "custom") {
                getCustomNotificationUri()
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }
            
            Log.d(TAG, "🔔 Using notification sound URI: $soundUri")
            
            val mediaPlayer = MediaPlayer().apply {
                setDataSource(context, soundUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setVolume(volume, volume)
                prepare()
                start()
            }
            
            // Auto-release after playback
            mediaPlayer.setOnCompletionListener {
                mediaPlayer.release()
            }
            
            Log.d(TAG, "✅ Custom notification sound played successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error playing custom notification sound", e)
        }
    }
    
    /**
     * Get system notification sound URI based on sound type
     */
    private fun getSystemNotificationUri(soundType: String): android.net.Uri {
        return try {
            when (soundType) {
                "custom" -> {
                    // Try to get a custom notification sound
                    getCustomNotificationUri()
                }
                else -> {
                    // Default to system notification sound
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting system notification URI for type: $soundType", e)
            // Fallback to default notification sound
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }
    }
    
    /**
     * Get custom notification URI from system
     */
    private fun getCustomNotificationUri(): android.net.Uri {
        return try {
            Log.d(TAG, "🔍 DEBUG: Starting getCustomNotificationUri()")
            
            // First try to get a custom notification from user preferences
            val sharedPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            Log.d(TAG, "🔍 DEBUG: SharedPreferences initialized: $sharedPrefs")
            
            val customNotificationUriString = sharedPrefs.getString("custom_notification_sound_uri", null)
            Log.d(TAG, "🔍 DEBUG: Retrieved custom_notification_sound_uri from preferences: '$customNotificationUriString'")
            
            if (customNotificationUriString != null) {
                val customUri = android.net.Uri.parse(customNotificationUriString)
                Log.d(TAG, "🔔 DEBUG: Successfully parsed custom notification URI: $customUri")
                
                // Verify the URI is accessible
                try {
                    val ringtone = RingtoneManager.getRingtone(context, customUri)
                    if (ringtone != null) {
                        val title = ringtone.getTitle(context)
                        Log.d(TAG, "✅ DEBUG: Custom notification sound is accessible, title: '$title'")
                        return customUri
                    } else {
                        Log.w(TAG, "⚠️ DEBUG: Custom notification URI exists but RingtoneManager.getRingtone returned null")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ DEBUG: Error accessing custom notification URI: $customUri", e)
                }
            } else {
                Log.w(TAG, "⚠️ DEBUG: No custom notification sound URI found in preferences")
            }
            
            // Fallback to default system notification
            val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            Log.d(TAG, "🔄 DEBUG: Falling back to default system notification URI: $defaultUri")
            return defaultUri
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ DEBUG: Exception in getCustomNotificationUri()", e)
            val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            Log.d(TAG, "🔄 DEBUG: Exception fallback to default URI: $fallbackUri")
            return fallbackUri
        }
    }
    
    /**
     * Get list of available system alarm sounds
     */
    fun getAvailableSystemAlarmSounds(): List<SystemAlarmSound> {
        val sounds = mutableListOf<SystemAlarmSound>()
        
        try {
            // Get all available alarm sounds
            val alarmManager = RingtoneManager(context)
            alarmManager.setType(RingtoneManager.TYPE_ALARM)
            val cursor = alarmManager.cursor
            
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    val id = cursor.getLong(RingtoneManager.ID_COLUMN_INDEX)
                    val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                    val uri = alarmManager.getRingtoneUri(cursor.position)
                    
                    sounds.add(SystemAlarmSound(id, title, uri))
                } while (cursor.moveToNext())
                
                cursor.close()
            }
            
            // Add default system sounds
            sounds.add(SystemAlarmSound(-1, "Default Alarm", RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)))
            sounds.add(SystemAlarmSound(-2, "Default Notification", RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)))
            sounds.add(SystemAlarmSound(-3, "Default Ringtone", RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)))
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting available system alarm sounds", e)
            // Add fallback sounds
            sounds.add(SystemAlarmSound(-1, "Default Alarm", RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)))
        }
        
        return sounds
    }
    
    /**
     * Data class for system alarm sounds
     */
    data class SystemAlarmSound(
        val id: Long,
        val title: String,
        val uri: android.net.Uri
    )

    /**
     * Debug method to dump all sound-related preferences
     */
    fun debugDumpSoundPreferences() {
        try {
            Log.d(TAG, "🔍 DEBUG: === SOUND PREFERENCES DUMP ===")
            val sharedPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            
            // Get all preferences
            val allPrefs = sharedPrefs.all
            Log.d(TAG, "🔍 DEBUG: Total preferences in 'app_settings': ${allPrefs.size}")
            
            // Log all preferences
            allPrefs.forEach { (key, value) ->
                Log.d(TAG, "🔍 DEBUG: Preference '$key' = '$value' (${value?.javaClass?.simpleName})")
            }
            
            // Specifically check sound preferences
            val alarmSound = sharedPrefs.getString("alarm_sound", "NOT_FOUND")
            val notificationSound = sharedPrefs.getString("notification_sound", "NOT_FOUND")
            val customAlarmUri = sharedPrefs.getString("custom_alarm_sound_uri", "NOT_FOUND")
            val customNotificationUri = sharedPrefs.getString("custom_notification_sound_uri", "NOT_FOUND")
            val alarmVolume = sharedPrefs.getFloat("alarm_volume", -1f)
            val notificationVolume = sharedPrefs.getFloat("notification_volume", -1f)
            
            Log.d(TAG, "🔍 DEBUG: === SOUND PREFERENCES SUMMARY ===")
            Log.d(TAG, "🔍 DEBUG: alarm_sound: '$alarmSound'")
            Log.d(TAG, "🔍 DEBUG: notification_sound: '$notificationSound'")
            Log.d(TAG, "🔍 DEBUG: custom_alarm_sound_uri: '$customAlarmUri'")
            Log.d(TAG, "🔍 DEBUG: custom_notification_sound_uri: '$customNotificationUri'")
            Log.d(TAG, "🔍 DEBUG: alarm_volume: $alarmVolume")
            Log.d(TAG, "🔍 DEBUG: notification_volume: $notificationVolume")
            Log.d(TAG, "🔍 DEBUG: === END SOUND PREFERENCES DUMP ===")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ DEBUG: Error dumping sound preferences", e)
        }
    }
}