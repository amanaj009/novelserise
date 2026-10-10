package com.writer_hub.novelserise.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.writer_hub.novelserise.NovelDetailActivity
import com.writer_hub.novelserise.R
import com.writer_hub.novelserise.databinding.ItemNovelCardBinding
import com.engine.core.models.HomepageNovelLite
import com.engine.core.helper.ImageHelper

class NovelGridAdapter(private val novels: List<HomepageNovelLite>) :
    RecyclerView.Adapter<NovelGridAdapter.NovelViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NovelViewHolder {
        val binding = ItemNovelCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NovelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NovelViewHolder, position: Int) {
        val novel = novels[position]
        holder.bind(novel)
    }

    override fun getItemCount(): Int = novels.size

    class NovelViewHolder(private val binding: ItemNovelCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(novel: HomepageNovelLite) {
            binding.tvNovelTitle.text = novel.title
            
            // Fixed: Prioritize imageLink to match NovelDetailActivity consistency
            val imageUrl = novel.imageLink.takeIf { !it.isNullOrEmpty() } ?: novel.imageLinkCover
            ImageHelper.loadImage(binding.root.context, imageUrl, binding.ivNovelCover, R.drawable.shimmer_placeholder)

            binding.root.setOnClickListener {
                novel.id?.let { id ->
                    NovelDetailActivity.start(it.context, id)
                }
            }
        }
    }
}
