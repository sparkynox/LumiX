package com.sparkynox.lumix.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.sparkynox.lumix.databinding.ItemVideoBinding
import com.sparkynox.lumix.helper.YtDlpHelper
import com.sparkynox.lumix.model.VideoItem

class VideoAdapter(
    private val onClick: (VideoItem) -> Unit
) : ListAdapter<VideoItem, VideoAdapter.VH>(Diff()) {

    inner class VH(val b: ItemVideoBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: VideoItem) {
            b.tvTitle.text = item.title
            b.tvChannel.text = item.channelName
            b.tvDuration.text = YtDlpHelper.formatDuration(item.duration)

            Glide.with(b.root)
                .load(item.thumbnailUrl)
                .centerCrop()
                .into(b.imgThumbnail)

            b.root.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(getItem(position))

    class Diff : DiffUtil.ItemCallback<VideoItem>() {
        override fun areItemsTheSame(a: VideoItem, b: VideoItem) = a.videoId == b.videoId
        override fun areContentsTheSame(a: VideoItem, b: VideoItem) = a == b
    }
}
