/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.writer_hub.novelserise.NovelDetailActivity
import com.writer_hub.novelserise.R
import com.writer_hub.novelserise.databinding.ItemNovelCardBinding
import com.engine.core.models.Novel
import com.engine.core.helper.ImageHelper

class NovelHorizontalAdapter(
    private val novels: List<Novel>
) : RecyclerView.Adapter<NovelHorizontalAdapter.NovelViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NovelViewHolder {
        val binding = ItemNovelCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        // Fix width for horizontal display
        val params = binding.root.layoutParams
        params.width = (parent.context.resources.displayMetrics.widthPixels * 0.4).toInt()
        binding.root.layoutParams = params
        return NovelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NovelViewHolder, position: Int) {
        holder.bind(novels[position])
    }

    override fun getItemCount(): Int = novels.size

    inner class NovelViewHolder(private val binding: ItemNovelCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(novel: Novel) {
            binding.tvNovelTitle.text = novel.title
            
            // Fixed: Prioritize imageLink to match NovelDetailActivity consistency
            val imageUrl = novel.imageLink.takeIf { !it.isNullOrEmpty() } ?: novel.imageLinkCover
            ImageHelper.loadImage(binding.root.context, imageUrl, binding.ivNovelCover, R.drawable.shimmer_placeholder)

            binding.root.setOnClickListener {
                novel.id?.let { id ->
                    NovelDetailActivity.start(binding.root.context, id)
                }
            }
        }
    }
}
