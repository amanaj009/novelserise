/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.engine.core.helper.ImageHelper
import com.engine.core.models.HomepageNovelLite
import com.writer_hub.novelserise.NovelDetailActivity
import com.writer_hub.novelserise.R
import com.writer_hub.novelserise.databinding.ItemHomeNewestUpdateBinding
import com.writer_hub.novelserise.databinding.ItemHomeRankingCardBinding
import com.writer_hub.novelserise.databinding.ItemHomeRankingRowBinding
import com.writer_hub.novelserise.util.DisplayText

/** "Newest Updates" rows: cover, title, author, time since the last chapter and a short description. */
class NewestUpdateAdapter(novels: List<HomepageNovelLite>) :
    RecyclerView.Adapter<NewestUpdateAdapter.ViewHolder>() {

    private var novels: List<HomepageNovelLite> = novels

    fun submit(newNovels: List<HomepageNovelLite>) {
        if (newNovels == novels) return
        novels = newNovels
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemHomeNewestUpdateBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(novels[position])

    override fun getItemCount(): Int = novels.size

    inner class ViewHolder(private val binding: ItemHomeNewestUpdateBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener { view ->
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) NovelDetailActivity.start(view.context, novels[position].id)
            }
        }

        fun bind(novel: HomepageNovelLite) {
            binding.tvTitle.text = DisplayText.of(novel.title)
            binding.tvAuthor.text = DisplayText.of(novel.authorName ?: novel.writerData?.name)
            binding.tvTime.text = novel.lastPublishChapterTimeReadable.orEmpty()
            binding.tvDescription.text = DisplayText.of(novel.description)
            val density = binding.root.resources.displayMetrics.density
            ImageHelper.loadImage(
                binding.root.context, novel.imageLink, binding.ivCover,
                R.drawable.shimmer_placeholder, allowHardware = true,
                targetWidthPx = (56 * density).toInt(), targetHeightPx = (84 * density).toInt()
            )
        }
    }
}

/**
 * Ranking cards: the novels are split into cards of [PER_CARD] ranked rows each, so the section
 * swipes sideways through "Top 1-3", "Top 4-6", ... and rank badges keep counting across cards.
 */
class RankingCardAdapter(novels: List<HomepageNovelLite>) :
    RecyclerView.Adapter<RankingCardAdapter.ViewHolder>() {

    private var novels: List<HomepageNovelLite> = novels

    fun submit(newNovels: List<HomepageNovelLite>) {
        if (newNovels == novels) return
        novels = newNovels
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemHomeRankingCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)

    override fun getItemCount(): Int = (novels.size + PER_CARD - 1) / PER_CARD

    inner class ViewHolder(private val binding: ItemHomeRankingCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(cardIndex: Int) {
            val from = cardIndex * PER_CARD
            val chunk = novels.subList(from, minOf(from + PER_CARD, novels.size))
            binding.tvCardTitle.text = "Top ${from + 1}-${from + chunk.size}"

            val inflater = LayoutInflater.from(binding.root.context)
            val density = binding.root.resources.displayMetrics.density
            binding.rowsContainer.removeAllViews()
            chunk.forEachIndexed { i, novel ->
                val row = ItemHomeRankingRowBinding.inflate(inflater, binding.rowsContainer, false)
                row.tvRank.text = (from + i + 1).toString()
                row.tvTitle.text = DisplayText.of(novel.title)
                val views = novel.viewText?.takeIf { it.isNotBlank() } ?: novel.viewCount
                row.tvViews.text = views?.takeIf { it.isNotBlank() }?.let { "🔥 $it" }.orEmpty()
                row.root.setOnClickListener { NovelDetailActivity.start(it.context, novel.id) }
                ImageHelper.loadImage(
                    binding.root.context, novel.imageLink, row.ivCover,
                    R.drawable.shimmer_placeholder, allowHardware = true,
                    targetWidthPx = (52 * density).toInt(), targetHeightPx = (78 * density).toInt()
                )
                binding.rowsContainer.addView(row.root)
            }
        }
    }

    companion object {
        const val PER_CARD = 3
    }
}
