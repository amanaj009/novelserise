package com.writer_hub.novelserise.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.writer_hub.novelserise.databinding.ItemNovelTrendingRowBinding
import com.writer_hub.novelserise.databinding.ItemNovelTrendingTopBinding
import com.engine.core.models.HomepageNovelLite
import com.engine.core.helper.ImageHelper

class TrendingNovelAdapter(private val novels: List<HomepageNovelLite>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_TOP = 0
    private val TYPE_ROW = 1

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) TYPE_TOP else TYPE_ROW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_TOP) {
            val binding = ItemNovelTrendingTopBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            TopViewHolder(binding)
        } else {
            val binding = ItemNovelTrendingRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            RowViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val novel = novels[position]
        val rank = (position + 1).toString()

        if (holder is TopViewHolder) {
            holder.bind(novel, rank)
        } else if (holder is RowViewHolder) {
            holder.bind(novel, rank)
        }
    }

    override fun getItemCount(): Int = novels.size

    class TopViewHolder(private val binding: ItemNovelTrendingTopBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(novel: HomepageNovelLite, rank: String) {
            binding.tvRank.text = "#$rank"
            binding.tvRankBg.text = rank
            binding.tvNovelTitle.text = novel.title
            binding.tvAuthor.text = "by ${novel.authorName}"
            ImageHelper.loadImage(binding.root.context, novel.imageLinkCover ?: novel.imageLink, binding.ivNovelCover)
        }
    }

    class RowViewHolder(private val binding: ItemNovelTrendingRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(novel: HomepageNovelLite, rank: String) {
            binding.tvRank.text = rank
            binding.tvNovelTitle.text = novel.title
            binding.tvAuthor.text = novel.authorName
            ImageHelper.loadImage(binding.root.context, novel.imageLinkCover ?: novel.imageLink, binding.ivNovelCover)
        }
    }
}