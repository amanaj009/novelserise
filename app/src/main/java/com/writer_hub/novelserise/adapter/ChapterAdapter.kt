package com.writer_hub.novelserise.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.writer_hub.novelserise.util.ChapterDate
import com.writer_hub.novelserise.util.DisplayText
import com.engine.core.manager.GlobalAppData
import com.engine.core.manager.GlobalVariableManager
import com.engine.core.models.ReadingSettings
import com.engine.core.models.WoChapter
import com.engine.core.utill.ThemeUtils
import com.writer_hub.novelserise.databinding.ItemChapterBinding
import com.writer_hub.novelserise.databinding.ItemLoadMoreBinding

class ChapterAdapter(
    private var chapters: List<WoChapter>,
    private var progressMap: Map<String, Int> = emptyMap(),
    private val settings: ReadingSettings? = null,
    private val onLoadMoreClick: () -> Unit = {},
    /** True where the list already sits inside the screen padding, so rows need no side margin. */
    private val flushRows: Boolean = false,
    private val onChapterClick: (WoChapter) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var showLoadMore: Boolean = false
    private var isLoading: Boolean = false

    companion object {
        private const val VIEW_TYPE_CHAPTER = 0
        private const val VIEW_TYPE_LOAD_MORE = 1
    }

    fun updateChapters(newChapters: List<WoChapter>) {
        chapters = newChapters
        notifyDataSetChanged()
    }

    fun setLoadMore(show: Boolean, loading: Boolean = false) {
        val changed = this.showLoadMore != show || this.isLoading != loading
        this.showLoadMore = show
        this.isLoading = loading
        if (changed) notifyDataSetChanged()
    }

    fun getChapters(): List<WoChapter> = chapters

    fun updateProgressMap(newProgressMap: Map<String, Int>) {
        progressMap = newProgressMap
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (showLoadMore && position == chapters.size) VIEW_TYPE_LOAD_MORE else VIEW_TYPE_CHAPTER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_LOAD_MORE) {
            val binding = ItemLoadMoreBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            LoadMoreViewHolder(binding)
        } else {
            val binding = ItemChapterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            if (flushRows) {
                (binding.root.layoutParams as? ViewGroup.MarginLayoutParams)?.apply { marginStart = 0; marginEnd = 0 }
            }
            ChapterViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ChapterViewHolder) {
            holder.bind(chapters[position])
        } else if (holder is LoadMoreViewHolder) {
            holder.bind()
        }
    }

    override fun getItemCount(): Int = chapters.size + (if (showLoadMore) 1 else 0)

    inner class LoadMoreViewHolder(private val binding: ItemLoadMoreBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            binding.btnLoadMore.visibility = if (isLoading) View.GONE else View.VISIBLE
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnLoadMore.setOnClickListener { onLoadMoreClick() }
            
            settings?.let { s ->
                binding.btnLoadMore.setTextColor(s.themeTextColor)
                binding.progressBar.indeterminateTintList = ColorStateList.valueOf(s.themeTextColor)
                binding.progressBar.progressTintList = ColorStateList.valueOf(s.themeTextColor)
            }
        }
    }

    /** Theme colours resolved once, not four times per row bind. */
    private class ThemeColors(context: android.content.Context) {
        val onSurface = ThemeUtils.getThemeColor(context, com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVariant = ThemeUtils.getThemeColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant)
        val primary = ThemeUtils.getThemeColor(context, androidx.appcompat.R.attr.colorPrimary)
        val onSurfaceVariantList: ColorStateList = ColorStateList.valueOf(onSurfaceVariant)
    }

    private var themeColors: ThemeColors? = null

    inner class ChapterViewHolder(private val binding: ItemChapterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(chapter: WoChapter) {
            // Priority to non-zero sqc from server, otherwise use position + 1
            val chapterSqc = chapter.sqc?.toIntOrNull()?.takeIf { it > 0 } ?: (adapterPosition + 1)
            binding.tvChapterNumber.text = chapterSqc.toString()
            binding.tvChapterTitle.text = DisplayText.of(chapter.chapterName)

            val isAudioMode = GlobalAppData.isAudioMode()
            val context = binding.root.context
            val colors = themeColors ?: ThemeColors(context).also { themeColors = it }

            val freeChapterLimit = GlobalVariableManager.getFreeChapterCount()
            val hasReadingSubscription = GlobalVariableManager.hasReadingSubscriptionPack()
            
            val currentUserId = GlobalAppData.userDetails?.userData?.userId
            val isOwner = !chapter.userId.isNullOrEmpty() && chapter.userId == currentUserId

            val isLocked = (chapter.isBuyed == "0") &&
                    !chapter.temporaryUnlock &&
                    (chapterSqc > freeChapterLimit) &&
                    !hasReadingSubscription &&
                    !isOwner
            
            val cleanDate = ChapterDate.format(ChapterDate.publishedRaw(chapter))

            // Apply theme colors from settings if available
            settings?.let { s ->
                binding.tvChapterTitle.setTextColor(s.themeTextColor)
                binding.tvChapterNumber.setTextColor(s.themeTextColor)
                binding.tvChapterInfo.setTextColor(s.themeTextColor)
                binding.tvChapterNumber.alpha = 0.7f
                binding.tvChapterInfo.alpha = 0.6f
                binding.ivLock.imageTintList = ColorStateList.valueOf(s.themeTextColor)
                binding.ivLock.alpha = 0.6f
            } ?: run {
                // Fallback to default theme attributes
                binding.tvChapterTitle.setTextColor(colors.onSurface)
                binding.tvChapterNumber.setTextColor(colors.onSurfaceVariant)
                binding.tvChapterInfo.setTextColor(colors.onSurfaceVariant)
                binding.ivLock.imageTintList = colors.onSurfaceVariantList
            }

            if (isAudioMode) {
                // Filter out invalid duration values like "0" or "00:00"
                val duration = chapter.audioDuration?.takeIf { it != "0" && it != "00:00" }
                binding.tvChapterInfo.text = if (duration != null) "$cleanDate • $duration" else cleanDate
                
                val currentlyPlayingId = GlobalAppData.currentlyPlayingChapter.value?.id
                if (currentlyPlayingId == chapter.id) {
                    binding.tvChapterTitle.setTextColor(colors.primary)
                }
            } else {
                val info = "$cleanDate • ${chapter.wordsCount} words"
                binding.tvChapterInfo.text = info
            }

            // Locked chapters show only the lock icon; no coin/diamond price in the list.
            binding.tvChapterPrice.visibility = View.GONE
            binding.ivLock.visibility = if (isLocked) View.VISIBLE else View.GONE

            val progress = progressMap[chapter.id] ?: chapter.readingStatus?.toIntOrNull() ?: 0
            if (progress > 0 && !isLocked) {
                binding.chapterProgress.visibility = View.VISIBLE
                binding.chapterProgress.progress = progress
                
                settings?.let { s ->
                    binding.chapterProgress.progressTintList = ColorStateList.valueOf(s.themeTextColor)
                }

                if (progress >= 100) {
                     binding.tvChapterInfo.text = "Completed • " + binding.tvChapterInfo.text
                }
            } else {
                binding.chapterProgress.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                onChapterClick(chapter)
            }
        }
    }
}
