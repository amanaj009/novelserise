package com.writer_hub.novelserise.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.engine.core.helper.ImageHelper
import com.engine.core.models.HomepageNovelLite
import com.writer_hub.novelserise.NovelDetailActivity
import com.writer_hub.novelserise.R
import com.writer_hub.novelserise.databinding.ItemNovelFeaturedBinding
import com.writer_hub.novelserise.util.DisplayText

/** Cover + title cards inside a home section. Binding is kept minimal because it runs while scrolling. */
class FeaturedNovelAdapter(novels: List<HomepageNovelLite>) :
    RecyclerView.Adapter<FeaturedNovelAdapter.FeaturedViewHolder>() {

    var novels: List<HomepageNovelLite> = novels
        private set

    fun submit(newNovels: List<HomepageNovelLite>) {
        if (newNovels == novels) return
        novels = newNovels
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeaturedViewHolder {
        val binding = ItemNovelFeaturedBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FeaturedViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FeaturedViewHolder, position: Int) = holder.bind(novels[position])

    override fun getItemCount(): Int = novels.size

    inner class FeaturedViewHolder(private val binding: ItemNovelFeaturedBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            // One listener per view, not a new lambda on every bind.
            binding.root.setOnClickListener { view ->
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) NovelDetailActivity.start(view.context, novels[position].id)
            }
        }

        fun bind(novel: HomepageNovelLite) {
            binding.tvNovelTitle.text = DisplayText.of(novel.title)
            // Decode exactly at the card's 80x120dp: smaller textures mean less GPU work per frame.
            val density = binding.root.resources.displayMetrics.density
            ImageHelper.loadImage(
                binding.root.context, novel.imageLink, binding.ivNovelCover,
                R.drawable.shimmer_placeholder, allowHardware = true,
                targetWidthPx = (80 * density).toInt(), targetHeightPx = (120 * density).toInt()
            )
        }
    }
}
