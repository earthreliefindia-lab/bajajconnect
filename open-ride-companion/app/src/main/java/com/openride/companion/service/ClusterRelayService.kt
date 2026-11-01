package com.openride.companion.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.openride.companion.MainActivity
import com.openride.companion.location.ParsedDestination

/**
 * Foreground Service responsible for:
 * 1. Background navigation tracking (foregroundServiceType = location)
 * 2. Motorcycle BLE GATT cluster telemetry relay (foregroundServiceType = connectedDevice)
 * Android 14+ / 15 / 16 fully compliant.
 */
class ClusterRelayService : Service() {

    companion object {
        private const val TAG = "ClusterRelayService"
        const val CHANNEL_ID = "openride_navigation_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_NAVIGATION = "com.openride.companion.START_NAVIGATION"
        const val ACTION_STOP_NAVIGATION = "com.openride.companion.STOP_NAVIGATION"

        const val EXTRA_DESTINATION_TITLE = "extra_destination_title"
        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"

        fun startNavigation(context: Context, destination: ParsedDestination) {
            val intent = Intent(context, ClusterRelayService::class.java).apply {
                action = ACTION_START_NAVIGATION
                putExtra(EXTRA_DESTINATION_TITLE, destination.title)
                putExtra(EXTRA_LATITUDE, destination.latitude)
                putExtra(EXTRA_LONGITUDE, destination.longitude)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopNavigation(context: Context) {
            val intent = Intent(context, ClusterRelayService::class.java).apply {
                action = ACTION_STOP_NAVIGATION
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_NAVIGATION -> {
                val title = intent.getStringExtra(EXTRA_DESTINATION_TITLE) ?: "Navigating to Destination"
                val notification = buildNavigationNotification(title, "Syncing route with motorcycle cluster...")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val types = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                    } else {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    }
                    startForeground(NOTIFICATION_ID, notification, types)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }

                Log.d(TAG, "Navigation relay active for: $title")
            }
            ACTION_STOP_NAVIGATION -> {
                Log.d(TAG, "Stopping navigation relay service")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNavigationNotification(title: String, statusText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bike Navigation & Cluster Relay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows turn-by-turn guidance and maintains connection with bike cluster"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
