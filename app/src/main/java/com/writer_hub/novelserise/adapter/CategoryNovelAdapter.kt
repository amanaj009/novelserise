/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.writer_hub.novelserise.R
import com.writer_hub.novelserise.databinding.ItemNovelSearchBinding
import com.engine.core.models.Novel
import com.engine.core.helper.ImageHelper

class CategoryNovelAdapter(
    private var novels: List<Novel>,
    private val onItemClick: (Novel) -> Unit
) : RecyclerView.Adapter<CategoryNovelAdapter.NovelViewHolder>() {

    fun updateData(newNovels: List<Novel>) {
        novels = newNovels
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NovelViewHolder {
        val binding = ItemNovelSearchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NovelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NovelViewHolder, position: Int) {
        val novel = novels[position]
        holder.bind(novel)
        holder.itemView.setOnClickListener { onItemClick(novel) }
    }

    override fun getItemCount(): Int = novels.size

    inner class NovelViewHolder(private val binding: ItemNovelSearchBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(novel: Novel) {
            binding.tvNovelTitle.text = novel.title
            binding.tvWriterName.text = "By ${novel.userName ?: novel.writer ?: "Unknown"}"
            binding.tvDescription.text = novel.description
            
            ImageHelper.loadImage(binding.ivNovelCover.context, novel.imageLink, binding.ivNovelCover, R.drawable.shimmer_placeholder)
        }
    }
}