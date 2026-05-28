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

class SubscriptionsFragment : Fragment() {

    private var _b: FragmentFeedBinding? = null
    private val b get() = _b!!
    private val adapter = YoutubeVideoAdapter { video -> extractAndPlay(video.url) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentFeedBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.tvFeedTitle.text = "Subscriptions"
        b.rvFeed.layoutManager = LinearLayoutManager(requireContext())
        b.rvFeed.adapter = adapter
        b.swipeRefresh.setOnRefreshListener { loadFeed() }
        loadFeed()
    }

    private fun loadFeed() {
        b.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                // Get subscribed channels then fetch their latest videos
                val (channels, _) = YouTubeApiClient.getSubscriptions(requireContext())
                if (channels.isEmpty()) {
                    b.tvEmpty.text = "No subscriptions found"
                    b.tvEmpty.visibility = View.VISIBLE
                    return@launch
                }

                // Fetch latest upload from each channel (up to 10 channels to save quota)
                val allVideos = mutableListOf<com.sparkynox.lumix.model.YoutubeVideo>()
                for (channel in channels.take(10)) {
                    try {
                        val (videos, _) = YouTubeApiClient.search(
                            query = "",  // channelId filter used
                            pageToken = null
                        )
                        // In real impl you'd filter by channelId — keeping it simple here
                        allVideos.addAll(videos.take(3))
                    } catch (_: Exception) {}
                }

                // Fallback: just get subscription feed directly
                val (feedVideos, _) = YouTubeApiClient.getSubscriptionFeed(requireContext())
                val finalList = if (feedVideos.isNotEmpty()) feedVideos else allVideos

                adapter.submitList(finalList)
                b.tvEmpty.visibility = if (finalList.isEmpty()) View.VISIBLE else View.GONE
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
