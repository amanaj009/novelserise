/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.writer_hub.novelserise.R
import com.writer_hub.novelserise.databinding.ItemNovelSearchBinding
import com.engine.core.models.SearchSuggestion
import com.engine.core.helper.ImageHelper

class SearchAdapter(
    private var items: List<SearchSuggestion>,
    private val onItemClick: (SearchSuggestion) -> Unit
) : RecyclerView.Adapter<SearchAdapter.SearchViewHolder>() {

    class SearchViewHolder(val binding: ItemNovelSearchBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        val binding = ItemNovelSearchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SearchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        val item = items[position]
        holder.binding.apply {
            tvNovelTitle.text = item.name

            if (item.tag == "novel") {
                tvWriterName.text = "By ${item.authorName ?: "Unknown"}"
                tvWriterName.visibility = View.VISIBLE
            } else {
                tvWriterName.text = "Writer"
                tvWriterName.visibility = View.VISIBLE
            }

            tvDescription.visibility = View.GONE // Suggestions don't have description
            
            // Use ImageHelper (Coil)
            ImageHelper.loadImage(
                ivNovelCover.context,
                item.image,
                ivNovelCover,
                R.drawable.shimmer_placeholder
            )

            root.setOnClickListener { onItemClick(item) }
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<SearchSuggestion>) {
        items = newItems
        notifyDataSetChanged()
    }
}
