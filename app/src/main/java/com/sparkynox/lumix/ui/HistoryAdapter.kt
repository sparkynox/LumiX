package com.sparkynox.lumix.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.sparkynox.lumix.databinding.ItemHistoryBinding
import com.sparkynox.lumix.model.HistoryItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private val onClick: (HistoryItem) -> Unit
) : ListAdapter<HistoryItem, HistoryAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(private val binding: ItemHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HistoryItem) {
            binding.tvTitle.text = item.title
            binding.tvUploader.text = item.uploader
            binding.tvWatchedAt.text = formatDate(item.watchedAt)

            Glide.with(binding.root)
                .load(item.thumbnailUrl)
                .centerCrop()
                .into(binding.imgThumbnail)

            binding.root.setOnClickListener { onClick(item) }
        }

        private fun formatDate(timestamp: Long): String {
            val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<HistoryItem>() {
        override fun areItemsTheSame(old: HistoryItem, new: HistoryItem) = old.videoId == new.videoId
        override fun areContentsTheSame(old: HistoryItem, new: HistoryItem) = old == new
    }
}
