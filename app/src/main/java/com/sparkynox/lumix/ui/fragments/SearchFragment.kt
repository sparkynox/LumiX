package com.sparkynox.lumix.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.sparkynox.lumix.api.YouTubeApiClient
import com.sparkynox.lumix.databinding.FragmentSearchBinding
import com.sparkynox.lumix.helper.YtDlpHelper
import com.sparkynox.lumix.ui.PlayerActivity
import com.sparkynox.lumix.ui.YoutubeVideoAdapter
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {

    private var _b: FragmentSearchBinding? = null
    private val b get() = _b!!
    private val adapter = YoutubeVideoAdapter { video -> extractAndPlay(video.url) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentSearchBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.rvResults.layoutManager = LinearLayoutManager(requireContext())
        b.rvResults.adapter = adapter

        b.btnSearch.setOnClickListener { doSearch() }

        b.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch(); true
            } else false
        }
    }

    private fun doSearch() {
        val query = b.etSearch.text.toString().trim()
        if (query.isEmpty()) return

        // Hide keyboard
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(b.etSearch.windowToken, 0)

        b.progressSearch.visibility = View.VISIBLE
        b.tvEmpty.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val (videos, _) = YouTubeApiClient.search(query)
                adapter.submitList(videos)
                b.tvEmpty.visibility = if (videos.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                b.tvEmpty.text = "Search failed: ${e.message}"
                b.tvEmpty.visibility = View.VISIBLE
            } finally {
                b.progressSearch.visibility = View.GONE
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
