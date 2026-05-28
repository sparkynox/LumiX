package com.sparkynox.lumix.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.bumptech.glide.Glide
import com.sparkynox.lumix.R
import com.sparkynox.lumix.databinding.ActivityPlayerBinding
import com.sparkynox.lumix.service.PlayerService

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null

    // True = audio only (background mode), False = video mode
    private var audioOnlyMode = false

    private lateinit var streamUrl: String
    private var audioUrl: String? = null
    private lateinit var title: String
    private lateinit var uploader: String
    private lateinit var thumbnailUrl: String

    companion object {
        const val EXTRA_STREAM_URL = "stream_url"
        const val EXTRA_AUDIO_URL = "audio_url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_UPLOADER = "uploader"
        const val EXTRA_THUMBNAIL = "thumbnail"
        const val EXTRA_DURATION = "duration"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Pull extras
        streamUrl = intent.getStringExtra(EXTRA_STREAM_URL) ?: run { finish(); return }
        audioUrl = intent.getStringExtra(EXTRA_AUDIO_URL)
        title = intent.getStringExtra(EXTRA_TITLE) ?: "Unknown"
        uploader = intent.getStringExtra(EXTRA_UPLOADER) ?: ""
        thumbnailUrl = intent.getStringExtra(EXTRA_THUMBNAIL) ?: ""

        binding.tvTitle.text = title
        binding.tvUploader.text = uploader

        Glide.with(this).load(thumbnailUrl).centerCrop().into(binding.imgThumbnail)

        setupPlayer()
        setupControls()
    }

    private fun setupPlayer() {
        player = ExoPlayer.Builder(this).build().also { exo ->
            binding.playerView.player = exo
            val url = if (audioOnlyMode && audioUrl != null) audioUrl!! else streamUrl
            exo.setMediaItem(MediaItem.fromUri(url))
            exo.prepare()
            exo.playWhenReady = true

            exo.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    binding.btnPlayPause.setImageResource(
                        if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                    )
                }
            })
        }

        // Hide thumbnail once playback starts
        binding.imgThumbnail.visibility = if (audioOnlyMode) View.VISIBLE else View.GONE
        binding.playerView.visibility = if (audioOnlyMode) View.GONE else View.VISIBLE
    }

    private fun setupControls() {
        binding.btnPlayPause.setOnClickListener {
            player?.let { if (it.isPlaying) it.pause() else it.play() }
        }

        binding.btnBack.setOnClickListener { finish() }

        // Toggle audio-only (background) mode
        binding.btnAudioMode.setOnClickListener {
            audioOnlyMode = !audioOnlyMode
            binding.btnAudioMode.setImageResource(
                if (audioOnlyMode) R.drawable.ic_audio_on else R.drawable.ic_audio_off
            )

            val currentPos = player?.currentPosition ?: 0L
            player?.release()

            // Show/hide player view
            binding.playerView.visibility = if (audioOnlyMode) View.GONE else View.VISIBLE
            binding.imgThumbnail.visibility = if (audioOnlyMode) View.VISIBLE else View.GONE

            setupPlayer()
            player?.seekTo(currentPos)
        }

        // Hand off to background service
        binding.btnBackground.setOnClickListener {
            val url = if (audioOnlyMode && audioUrl != null) audioUrl!! else streamUrl
            val intent = Intent(this, PlayerService::class.java).apply {
                action = PlayerService.ACTION_PLAY
                putExtra(PlayerService.EXTRA_STREAM_URL, url)
                putExtra(PlayerService.EXTRA_TITLE, title)
                putExtra(PlayerService.EXTRA_UPLOADER, uploader)
            }
            startService(intent)
            player?.stop() // player in foreground not needed anymore
            finish()
        }
    }

    override fun onStop() {
        super.onStop()
        // Don't release here — let the foreground service handle it if bg mode
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}
