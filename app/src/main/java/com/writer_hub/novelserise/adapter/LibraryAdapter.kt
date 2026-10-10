/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.writer_hub.novelserise.NovelDetailActivity
import com.writer_hub.novelserise.R
import com.writer_hub.novelserise.databinding.ItemLibraryNovelBinding
import com.writer_hub.novelserise.util.DisplayText
import com.engine.core.models.Novel
import com.engine.core.helper.ImageHelper

class LibraryAdapter(
    private val onRemoveClick: (Novel) -> Unit
) : ListAdapter<Novel, LibraryAdapter.LibraryViewHolder>(NovelDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LibraryViewHolder {
        val binding = ItemLibraryNovelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LibraryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LibraryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class LibraryViewHolder(private val binding: ItemLibraryNovelBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(novel: Novel) {
            binding.tvTitle.text = DisplayText.of(novel.title)
            binding.tvAuthor.text = "by ${DisplayText.of(novel.userName ?: novel.writer ?: "Unknown Author")}"
            binding.tvDescription.text = DisplayText.of(novel.description)

            binding.tvTotalChapters.text = novel.chapterCount?.toString() ?: "0"
            binding.tvNewChapters.text = novel.newChaptersCount?.toString() ?: "0"
            binding.tvUnreadChapters.text = novel.unreadChaptersCount?.toString() ?: "0"
            // Only show the pink "new" / "unread" pills when they carry a count.
            (binding.tvNewChapters.parent as View).visibility =
                if ((novel.newChaptersCount ?: 0) > 0) View.VISIBLE else View.GONE
            (binding.tvUnreadChapters.parent as View).visibility =
                if ((novel.unreadChaptersCount ?: 0) > 0) View.VISIBLE else View.GONE

            // Fixed: Prioritize imageLink to match NovelDetailActivity consistency
            val imageUrl = novel.imageLink.takeIf { !it.isNullOrEmpty() } ?: novel.imageLinkCover
            ImageHelper.loadImage(binding.ivCover.context, imageUrl, binding.ivCover, R.drawable.shimmer_placeholder)

            binding.btnRemove.setOnClickListener {
                onRemoveClick(novel)
            }

            binding.root.setOnClickListener {
                novel.id?.let { id ->
                    NovelDetailActivity.start(it.context, id)
                }
            }
        }
    }

    class NovelDiffCallback : DiffUtil.ItemCallback<Novel>() {
        override fun areItemsTheSame(oldItem: Novel, newItem: Novel): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Novel, newItem: Novel): Boolean {
            return oldItem == newItem
        }
    }
}
