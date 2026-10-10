/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.writer_hub.novelserise.R
import com.writer_hub.novelserise.databinding.ItemWriterNovelBinding
import com.writer_hub.novelserise.util.DisplayText
import com.engine.core.models.Novel
import com.engine.core.helper.ImageHelper
import java.util.Locale

class WriterNovelAdapter(
    private var novels: MutableList<Novel>,
    private val onEditClick: (Novel) -> Unit,
    private val onStatsClick: (Novel) -> Unit,
    private val onNovelClick: (Novel) -> Unit
) : RecyclerView.Adapter<WriterNovelAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemWriterNovelBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            // Listeners set once; they read the bound item by position.
            binding.btnEdit.setOnClickListener { current()?.let(onEditClick) }
            binding.btnStats.setOnClickListener { current()?.let(onStatsClick) }
            binding.root.setOnClickListener { current()?.let(onNovelClick) }
        }

        private fun current(): Novel? = novels.getOrNull(bindingAdapterPosition)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWriterNovelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val novel = novels[position]
        holder.binding.apply {
            tvTitle.text = DisplayText.of(novel.title)

            val chapters = novel.chapterCount ?: 0
            tvChapters.text = if (chapters == 1) "1 chapter" else "$chapters chapters"
            tvStats.text = "${compactCount(novel.view?.toLongOrNull() ?: 0)} views"

            val isLive = novel.isPublic == "1"
            tvStatus.text = if (isLive) "● LIVE" else "● DRAFT"
            tvStatus.setTextColor(if (isLive) LIVE_COLOR else DRAFT_COLOR)
            tvStatus.setBackgroundResource(if (isLive) R.drawable.bg_status_live else R.drawable.bg_status_draft)

            ImageHelper.loadImage(ivCover.context, novel.imageLink, ivCover, R.drawable.shimmer_placeholder)
        }
    }

    override fun getItemCount(): Int = novels.size

    /** Everything currently loaded (for the studio totals). */
    val items: List<Novel> get() = novels

    fun updateData(newNovels: List<Novel>) {
        novels.clear()
        novels.addAll(newNovels)
        notifyDataSetChanged()
    }

    /**
     * Appends a list of new novels to the end of the current list.
     * @param newNovels The list of novels to add.
     */
    fun addNovels(newNovels: List<Novel>) {
        val startPosition = novels.size
        novels.addAll(newNovels)
        notifyItemRangeInserted(startPosition, newNovels.size)
    }

    companion object {
        private val LIVE_COLOR = Color.parseColor("#10B981")
        private val DRAFT_COLOR = Color.parseColor("#F59E0B")

        fun compactCount(value: Long): String = when {
            value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0).replace(".0M", "M")
            value >= 1_000 -> String.format(Locale.US, "%.1fk", value / 1_000.0).replace(".0k", "k")
            else -> value.toString()
        }
    }
}
