package com.sparkynox.lumix.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.sparkynox.lumix.databinding.ActivityMainBinding
import com.sparkynox.lumix.helper.YtDlpHelper
import com.sparkynox.lumix.model.VideoItem
import com.sparkynox.lumix.service.PlayerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val adapter = VideoAdapter { video -> playVideo(video) }
    private var isExtracting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        startPlayerService()
        setupRecycler()
        setupSearch()
        setupBottomNav()
        loadTrending()
    }

    private fun startPlayerService() {
        val intent = Intent(this, PlayerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun setupRecycler() {
        binding.rvVideos.layoutManager = LinearLayoutManager(this)
        binding.rvVideos.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { loadTrending() }
        binding.swipeRefresh.setColorSchemeColors(getColor(com.sparkynox.lumix.R.color.accent))
    }

    private fun setupSearch() {
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch()
                true
            } else false
        }

        binding.btnSearch.setOnClickListener { doSearch() }
    }

    private fun setupBottomNav() {
        binding.btnHome.setOnClickListener {
            setActiveTab(0)
            loadTrending()
        }

        binding.btnSearchTab.setOnClickListener {
            setActiveTab(1)
            binding.searchBar.visibility = View.VISIBLE
            binding.etSearch.requestFocus()
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun setActiveTab(tab: Int) {
        binding.btnHome.alpha = if (tab == 0) 1f else 0.5f
        binding.btnSearchTab.alpha = if (tab == 1) 1f else 0.5f
        if (tab == 0) binding.searchBar.visibility = View.GONE
    }

    private fun doSearch() {
        val query = binding.etSearch.text.toString().trim()
        if (query.isEmpty()) return

        // Hide keyboard
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)

        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    YtDlpHelper.search(query)
                }
                adapter.submitList(results)
                binding.tvEmpty.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Search failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun loadTrending() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = false

        lifecycleScope.launch {
            try {
                val videos = withContext(Dispatchers.IO) {
                    YtDlpHelper.getTrending()
                }
                adapter.submitList(videos)
                binding.tvEmpty.visibility = if (videos.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                binding.tvEmpty.text = "Failed to load: ${e.message}"
                binding.tvEmpty.visibility = View.VISIBLE
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun playVideo(video: VideoItem) {
        if (isExtracting) return
        isExtracting = true

        // Show mini player loading
        binding.layoutMiniPlayer.visibility = View.VISIBLE
        binding.tvMiniTitle.text = video.title
        binding.tvMiniChannel.text = video.channelName

        com.bumptech.glide.Glide.with(this)
            .load(video.thumbnailUrl)
            .centerCrop()
            .into(binding.imgMiniThumb)

        lifecycleScope.launch {
            try {
                val info = withContext(Dispatchers.IO) {
                    YtDlpHelper.extract(applicationContext, video.url)
                }

                // Play in service
                val intent = Intent(this@MainActivity, PlayerService::class.java).apply {
                    action = PlayerService.ACTION_PLAY
                    putExtra(PlayerService.EXTRA_URL, info.streamUrl)
                    putExtra(PlayerService.EXTRA_TITLE, info.title)
                    putExtra(PlayerService.EXTRA_UPLOADER, info.uploader)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }

            } catch (e: Exception) {
                binding.layoutMiniPlayer.visibility = View.GONE
                Toast.makeText(
                    this@MainActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                isExtracting = false
            }
        }

        // Mini player controls
        binding.btnMiniClose.setOnClickListener {
            binding.layoutMiniPlayer.visibility = View.GONE
            startService(Intent(this, PlayerService::class.java).apply {
                action = PlayerService.ACTION_STOP
            })
        }

        binding.btnMiniPause.setOnClickListener {
            startService(Intent(this, PlayerService::class.java).apply {
                action = PlayerService.ACTION_PAUSE_RESUME
            })
        }
    }
}
