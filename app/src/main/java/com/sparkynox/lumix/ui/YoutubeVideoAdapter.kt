package com.sparkynox.lumix.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.sparkynox.lumix.databinding.ItemVideoCardBinding
import com.sparkynox.lumix.model.YoutubeVideo
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class YoutubeVideoAdapter(
    private val onClick: (YoutubeVideo) -> Unit
) : ListAdapter<YoutubeVideo, YoutubeVideoAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(private val b: ItemVideoCardBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(video: YoutubeVideo) {
            b.tvVideoTitle.text = video.title
            b.tvChannelName.text = video.channelName
            b.tvPublishedAt.text = formatDate(video.publishedAt)

            Glide.with(b.root)
                .load(video.thumbnailUrl)
                .centerCrop()
                .into(b.imgThumbnail)

            b.root.setOnClickListener { onClick(video) }
        }

        private fun formatDate(iso: String): String {
            return try {
                val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                parser.timeZone = TimeZone.getTimeZone("UTC")
                val date = parser.parse(iso) ?: return ""
                val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                formatter.format(date)
            } catch (e: Exception) { "" }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ItemVideoCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(b)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class DiffCallback : DiffUtil.ItemCallback<YoutubeVideo>() {
        override fun areItemsTheSame(a: YoutubeVideo, b: YoutubeVideo) = a.id == b.id
        override fun areContentsTheSame(a: YoutubeVideo, b: YoutubeVideo) = a == b
    }
}
