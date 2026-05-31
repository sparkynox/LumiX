package com.sparkynox.lumix.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.sparkynox.lumix.R
import com.sparkynox.lumix.ui.MainActivity

class PlayerService : Service() {

    var player: ExoPlayer? = null
    private var mediaSession: MediaSessionCompat? = null

    companion object {
        const val ACTION_PLAY = "lumix.PLAY"
        const val ACTION_PAUSE_RESUME = "lumix.PAUSE_RESUME"
        const val ACTION_STOP = "lumix.STOP"
        const val EXTRA_URL = "stream_url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_UPLOADER = "uploader"
        const val CHANNEL_ID = "lumix_player"
        const val NOTIF_ID = 42
        var instance: PlayerService? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannel()
        mediaSession = MediaSessionCompat(this, "LumiX")
        startForeground(NOTIF_ID, buildNotification("Lumi X", "Ready", false))

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(), true
            )
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        val title = currentMediaItem?.mediaMetadata?.title?.toString() ?: "Lumi X"
                        val artist = currentMediaItem?.mediaMetadata?.artist?.toString() ?: ""
                        updateNotification(title, artist, isPlaying)
                    }
                })
            }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_STICKY
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Lumi X"
                val uploader = intent.getStringExtra(EXTRA_UPLOADER) ?: ""

                val mediaItem = MediaItem.Builder()
                    .setUri(url)
                    .setMediaMetadata(
                        androidx.media3.common.MediaMetadata.Builder()
                            .setTitle(title).setArtist(uploader).build()
                    ).build()

                player?.apply {
                    stop()
                    setMediaItem(mediaItem)
                    prepare()
                    playWhenReady = true
                }
                startForeground(NOTIF_ID, buildNotification(title, uploader, true))
            }
            ACTION_PAUSE_RESUME -> {
                player?.let { if (it.isPlaying) it.pause() else it.play() }
            }
            ACTION_STOP -> {
                player?.stop()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION") stopForeground(true)
                }
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun updateNotification(title: String, uploader: String, isPlaying: Boolean) {
        getSystemService(NotificationManager::class.java)
            ?.notify(NOTIF_ID, buildNotification(title, uploader, isPlaying))
    }

    private fun buildNotification(title: String, uploader: String, isPlaying: Boolean): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val pauseIntent = PendingIntent.getService(
            this, 1,
            Intent(this, PlayerService::class.java).apply { action = ACTION_PAUSE_RESUME },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 2,
            Intent(this, PlayerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(uploader)
            .setSmallIcon(R.drawable.ic_play)
            .setContentIntent(openIntent)
            .addAction(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (isPlaying) "Pause" else "Play", pauseIntent
            )
            .addAction(R.drawable.ic_close, "Stop", stopIntent)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1)
                .setMediaSession(mediaSession?.sessionToken))
            .setOngoing(isPlaying)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Lumi X Player", NotificationManager.IMPORTANCE_LOW
            ).apply { setSound(null, null) }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        instance = null
        player?.release()
        mediaSession?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
