package com.sparkynox.lumix.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.sparkynox.lumix.api.YouTubeApiClient
import com.sparkynox.lumix.databinding.FragmentFeedBinding
import com.sparkynox.lumix.helper.YtDlpHelper
import com.sparkynox.lumix.ui.PlayerActivity
import com.sparkynox.lumix.ui.YoutubeVideoAdapter
import kotlinx.coroutines.launch

class LikedVideosFragment : Fragment() {

    private var _b: FragmentFeedBinding? = null
    private val b get() = _b!!
    private val adapter = YoutubeVideoAdapter { video -> extractAndPlay(video.url) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentFeedBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.tvFeedTitle.text = "Liked Videos"
        b.rvFeed.layoutManager = LinearLayoutManager(requireContext())
        b.rvFeed.adapter = adapter
        b.swipeRefresh.setOnRefreshListener { loadFeed() }
        loadFeed()
    }

    private fun loadFeed() {
        b.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                val (videos, _) = YouTubeApiClient.getLikedVideos(requireContext())
                adapter.submitList(videos)
                b.tvEmpty.visibility = if (videos.isEmpty()) View.VISIBLE else View.GONE
                if (videos.isEmpty()) b.tvEmpty.text = "No liked videos found"
            } catch (e: Exception) {
                b.tvEmpty.text = "Failed: ${e.message}"
                b.tvEmpty.visibility = View.VISIBLE
            } finally {
                b.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun extractAndPlay(url: String) {
        lifecycleScope.launch {
            try {
                val info = YtDlpHelper.extract(requireContext(), url)
                startActivity(Intent(requireContext(), PlayerActivity::class.java).apply {
                    putExtra(PlayerActivity.EXTRA_TITLE, info.title)
                    putExtra(PlayerActivity.EXTRA_UPLOADER, info.uploader)
                    putExtra(PlayerActivity.EXTRA_STREAM_URL, info.streamUrl)
                    putExtra(PlayerActivity.EXTRA_AUDIO_URL, info.audioOnlyUrl)
                    putExtra(PlayerActivity.EXTRA_THUMBNAIL, info.thumbnailUrl)
                    putExtra(PlayerActivity.EXTRA_DURATION, info.duration)
                })
            } catch (e: Exception) {
                com.google.android.material.snackbar.Snackbar
                    .make(b.root, "Error: ${e.message}", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                    .show()
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
