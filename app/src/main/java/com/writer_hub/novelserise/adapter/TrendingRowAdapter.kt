package com.writer_hub.novelserise.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.engine.core.helper.ImageHelper
import com.engine.core.models.HomepageNovelLite
import com.writer_hub.novelserise.NovelDetailActivity
import com.writer_hub.novelserise.R
import com.writer_hub.novelserise.databinding.ItemNovelTrendingRowBinding
import com.writer_hub.novelserise.util.DisplayText

/**
 * Ranked novels for a horizontal grid with [rows] rows. A horizontal GridLayoutManager fills
 * column by column, so items are reordered to read row by row: the top row shows ranks
 * 1, 2, 3, ... and the next row continues after it.
 */
class TrendingRowAdapter(novels: List<HomepageNovelLite>, rows: Int = 1) :
    RecyclerView.Adapter<TrendingRowAdapter.ViewHolder>() {

    /** Source list and row count, to skip rebinding when a section is shown again unchanged. */
    var source: Pair<List<HomepageNovelLite>, Int> = novels to rows
        private set

    /** (rank, novel) in grid fill order. */
    private var items: List<Pair<Int, HomepageNovelLite>> = arrange(novels, rows)

    fun submit(newNovels: List<HomepageNovelLite>, rows: Int) {
        if (source == (newNovels to rows)) return
        source = newNovels to rows
        items = arrange(newNovels, rows)
        notifyDataSetChanged()
    }

    private fun arrange(novels: List<HomepageNovelLite>, rows: Int): List<Pair<Int, HomepageNovelLite>> {
        val ranked = novels.mapIndexed { index, novel -> (index + 1) to novel }
        if (rows <= 1 || ranked.isEmpty()) return ranked
        val perRow = (ranked.size + rows - 1) / rows
        val rowLists = ranked.chunked(perRow)
        return (0 until perRow).flatMap { column -> rowLists.mapNotNull { it.getOrNull(column) } }
    }

    inner class ViewHolder(val binding: ItemNovelTrendingRowBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener { view ->
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) NovelDetailActivity.start(view.context, items[position].second.id)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemNovelTrendingRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (rank, novel) = items[position]
        val binding = holder.binding
        binding.tvRank.text = rank.toString()
        // Two-digit ranks shrink so they stay within the cover's corner.
        binding.tvRank.textSize = if (rank < 10) 44f else 34f
        binding.tvNovelTitle.text = DisplayText.of(novel.title)

        // Author line only when there is one (an empty line left a gap under the title).
        val author = (novel.authorName ?: novel.writerData?.name)?.takeIf { it.isNotBlank() }
        binding.tvAuthor.text = DisplayText.of(author)
        binding.tvAuthor.visibility = if (author != null) View.VISIBLE else View.GONE

        // Decode exactly at the 80x120dp cover: smaller textures mean less GPU work per frame.
        val density = binding.root.resources.displayMetrics.density
        ImageHelper.loadImage(
            binding.root.context, novel.imageLink, binding.ivNovelCover,
            R.drawable.shimmer_placeholder, allowHardware = true,
            targetWidthPx = (80 * density).toInt(), targetHeightPx = (120 * density).toInt()
        )
    }

    override fun getItemCount(): Int = items.size
}
