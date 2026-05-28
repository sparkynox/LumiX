package com.sparkynox.lumix.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sparkynox.lumix.R
import com.sparkynox.lumix.ui.MainActivity

class PlayerService : Service() {

    companion object {
        const val ACTION_START = "com.sparkynox.lumix.START"
        const val ACTION_TERMINATE = "com.sparkynox.lumix.TERMINATE"
        const val CHANNEL_ID = "lumix_bg"
        const val NOTIF_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TERMINATE -> {
                stopSelf()
                // Kill the whole app
                android.os.Process.killProcess(android.os.Process.myPid())
            }
            else -> {
                startForeground(NOTIF_ID, buildNotification())
            }
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        // Open app on tap
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Terminate action
        val terminateIntent = PendingIntent.getService(
            this, 1,
            Intent(this, PlayerService::class.java).apply { action = ACTION_TERMINATE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Lumi X")
            .setContentText("Lumi X Running")
            .setSmallIcon(R.drawable.ic_play)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_close, "Terminate", terminateIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Lumi X Background",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Lumi X running in background"
                setSound(null, null)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
