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
import com.writer_hub.novelserise.databinding.ItemViewAllNovelBinding
import com.engine.core.helper.decodeHtmlToTextV2
import com.engine.core.models.HomepageNovelLite
import com.engine.core.helper.ImageHelper
import com.writer_hub.novelserise.util.DisplayText
import java.util.Locale

class HomeSectionNovelByIdAdapter : ListAdapter<HomepageNovelLite, HomeSectionNovelByIdAdapter.NovelViewHolder>(NovelDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NovelViewHolder {
        val binding = ItemViewAllNovelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NovelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NovelViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class NovelViewHolder(private val binding: ItemViewAllNovelBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(novel: HomepageNovelLite) {
            binding.tvNovelTitle.text = DisplayText.of(novel.title)
            val author = DisplayText.of(novel.authorName ?: novel.writerData?.name ?: "")
            binding.tvAuthorName.text = author
            binding.tvAuthorName.visibility = if (author.isBlank()) View.GONE else View.VISIBLE
            binding.tvDescription.text = DisplayText.of(novel.description)
            // "3.7426" -> "3.7", "163993" -> "164K"
            binding.tvRating.text = novel.avgRating?.toDoubleOrNull()?.let { String.format(Locale.US, "%.1f", it) } ?: "0.0"
            binding.tvViews.text = WriterNovelAdapter.compactCount(novel.viewCount?.toLongOrNull() ?: 0L).uppercase()

            // Strictly use imageLink for consistency
            ImageHelper.loadImage(binding.root.context, novel.imageLink, binding.ivNovelCover)

            binding.root.setOnClickListener {
                NovelDetailActivity.start(it.context, novel.id)
            }
        }
    }

    class NovelDiffCallback : DiffUtil.ItemCallback<HomepageNovelLite>() {
        override fun areItemsTheSame(oldItem: HomepageNovelLite, newItem: HomepageNovelLite): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: HomepageNovelLite, newItem: HomepageNovelLite): Boolean {
            return oldItem == newItem
        }
    }
}
