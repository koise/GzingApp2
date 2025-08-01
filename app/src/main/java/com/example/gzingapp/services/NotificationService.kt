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

class NotificationService(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "GzingAppChannel"
        const val ALARM_CHANNEL_ID = "GzingAppAlarmChannel"
        const val CHANNEL_NAME = "GzingApp Notifications"
        const val ALARM_CHANNEL_NAME = "GzingApp Alarms"
        const val CHANNEL_DESCRIPTION = "Notifications from GzingApp"
        const val ALARM_CHANNEL_DESCRIPTION = "Critical alarm notifications from GzingApp"
        private const val TAG = "NotificationService"
        const val STOP_ALARM_ACTION = "STOP_ALARM_ACTION"

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

    private fun initializeVibrator() {
        localVibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // Also set the static reference
        currentVibrator = localVibrator
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

            notificationManager.createNotificationChannel(channel)
            notificationManager.createNotificationChannel(alarmChannel)

            Log.d(TAG, "Notification channels created")
        }
    }

    fun showNotification(title: String, message: String, notificationId: Int) {
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

    fun showAlarmNotification(title: String, message: String, notificationId: Int) {
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
        }

        val stopAlarmPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1000,
            stopAlarmIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

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
            .addAction(
                R.drawable.ic_stop,
                "Stop Alarm",
                stopAlarmPendingIntent
            )

        // Play system alarm sound using static reference
        playAlarmSound()

        // Trigger strong vibration for alarm using static reference
        triggerAlarmVibration()

        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            ) {
                notify(notificationId, builder.build())
                Log.d(TAG, "Alarm notification shown with ID: $notificationId")
            } else {
                Log.e(TAG, "POST_NOTIFICATIONS permission not granted")
            }
        }
    }

    fun playAlarmSound() {
        try {
            // Stop any existing sound using static method
            stopAlarmSound()

            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            currentMediaPlayer = MediaPlayer().apply {
                setDataSource(context, alarmUri)
                isLooping = true // Loop the alarm sound

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .build()
                    )
                }

                prepareAsync()
                setOnPreparedListener {
                    start()
                    isAlarmPlaying = true
                    Log.d(TAG, "Alarm sound started (static reference)")
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    false
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

    fun triggerAlarmVibration() {
        val alarmPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000, 500, 1000)

        // Use static reference so it can be stopped from receiver
        currentVibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(
                    android.os.VibrationEffect.createWaveform(alarmPattern, 0), // Repeat pattern
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
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
}

