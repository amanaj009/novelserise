/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.engine.core.helper.AppLogger
import com.engine.core.helper.ImageHelper
import com.engine.core.models.Novel
import com.writer_hub.novelserise.NovelDetailActivity
import com.writer_hub.novelserise.R
import com.writer_hub.novelserise.databinding.ItemNovelCardBinding

class ViewAllNovelsAdapter(
    private val novels: MutableList<Novel> = mutableListOf()
) : RecyclerView.Adapter<ViewAllNovelsAdapter.NovelViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NovelViewHolder {
        val binding = ItemNovelCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NovelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NovelViewHolder, position: Int) {
        holder.bind(novels[position])
    }

    override fun getItemCount(): Int = novels.size

    fun updateData(newNovels: List<Novel>) {
        novels.clear()
        novels.addAll(newNovels)
        notifyDataSetChanged()
    }

    fun addData(newNovels: List<Novel>) {
        val startPos = novels.size
        novels.addAll(newNovels)
        notifyItemRangeInserted(startPos, newNovels.size)
    }

    inner class NovelViewHolder(private val binding: ItemNovelCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(novel: Novel) {
            binding.tvNovelTitle.text = novel.title
            
            // Log the image link being used
            AppLogger.d("ViewAllNovelsAdapter", "Loading Image for: ${novel.title}, URL: ${novel.imageLink}")
            
            // Strictly use imageLink for consistency
            ImageHelper.loadImage(binding.root.context, novel.imageLink, binding.ivNovelCover, R.drawable.shimmer_placeholder)

            binding.root.setOnClickListener {
                novel.id?.let { id ->
                    NovelDetailActivity.start(binding.root.context, id)
                }
            }
        }
    }
}
