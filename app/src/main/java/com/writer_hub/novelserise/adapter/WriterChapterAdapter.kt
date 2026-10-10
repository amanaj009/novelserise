/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.engine.core.manager.GlobalAppData
import com.engine.core.models.WoChapter
import com.writer_hub.novelserise.R
import com.writer_hub.novelserise.databinding.ItemWriterChapterBinding
import com.writer_hub.novelserise.ui.custom.OptionsDialog
import com.writer_hub.novelserise.util.ChapterDate
import com.writer_hub.novelserise.util.DisplayText

class WriterChapterAdapter(
    private var chapters: MutableList<WoChapter>,
    private val onToggleStatus: (WoChapter) -> Unit,
    private val onChapterClick: (WoChapter) -> Unit,
    private val onScheduleChapter: (WoChapter) -> Unit,
    private val onViewStatistics: (WoChapter) -> Unit,
    private val onViewComments: (WoChapter) -> Unit
) : RecyclerView.Adapter<WriterChapterAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemWriterChapterBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWriterChapterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val chapter = chapters[position]
        holder.binding.apply {
            // Priority to sqc from server, otherwise use position + 1
            tvChapterSqc.text = chapter.sqc ?: (position + 1).toString()
            tvChapterTitle.text = DisplayText.of(chapter.chapterName ?: "Untitled Chapter").ifBlank { "Untitled Chapter" }

            // Word count (and its separator) is meaningless for audio novels.
            val showWords = !GlobalAppData.isAudioMode()
            tvWordCount.visibility = if (showWords) View.VISIBLE else View.GONE
            tvMetaDot.visibility = if (showWords) View.VISIBLE else View.GONE
            if (showWords) tvWordCount.text = "${chapter.wordsCount ?: 0} words"

            val isPublished = chapter.status == "published"

            switchStatus.setOnCheckedChangeListener(null)
            switchStatus.isChecked = isPublished
            showStatus(this, isPublished)

            switchStatus.setOnCheckedChangeListener { _, isChecked ->
                showStatus(this, isChecked)
                onToggleStatus(chapter)
            }

            // Once a scheduled chapter is public, it must no longer look scheduled.
            if (chapter.isSchedule == "1" && !isPublished) {
                layoutScheduleInfo.visibility = View.VISIBLE
                tvScheduledAt.text = chapter.scheduleAt
            } else {
                layoutScheduleInfo.visibility = View.GONE
            }

            tvDates.text = if (isPublished) {
                "Published ${ChapterDate.format(ChapterDate.publishedRaw(chapter), withTime = true)}"
            } else {
                "Created ${ChapterDate.format(chapter.createdAt, withTime = true)}"
            }

            // BACKGROUND PUBLISHING/UPLOADING PROGRESS
            val progressMap = GlobalAppData.uploadProgressMap.value
            val finishedMap = GlobalAppData.finishedPublishingMap.value
            
            val chapterProgress = progressMap?.get(chapter.id)
            val isFinished = finishedMap?.containsKey(chapter.id) == true

            if (isFinished) {
                // Success state
                layoutProgress.visibility = View.VISIBLE
                progressIndicator.isIndeterminate = false
                progressIndicator.progress = 100
                tvProgressStatus.text =
                    if (GlobalAppData.isAudioMode()) "Audio uploaded successfully!" else "Chapter published successfully!"

                // Re-enable interactions so user can immediately publish or open the chapter
                root.isEnabled = true
                switchStatus.isEnabled = true
                btnComments.isEnabled = true
                btnMore.isEnabled = true
            } else if (chapterProgress != null) {
                layoutProgress.visibility = View.VISIBLE
                progressIndicator.progress = chapterProgress.second
                tvProgressStatus.text = chapterProgress.first
                
                // Disable interactions while publishing/uploading
                root.isEnabled = false
                switchStatus.isEnabled = false
                btnComments.isEnabled = false
                btnMore.isEnabled = false
                
                // Set indeterminate if progress is 0 but it's running (e.g., waiting or starting)
                progressIndicator.isIndeterminate = chapterProgress.second == 0
            } else {
                layoutProgress.visibility = View.GONE
                root.isEnabled = true
                switchStatus.isEnabled = true
                btnComments.isEnabled = true
                btnMore.isEnabled = true
            }

            btnComments.contentDescription = "View comments for ${tvChapterTitle.text}"
            btnComments.setOnClickListener {
                onViewComments(chapter)
            }

            btnMore.setOnClickListener { view ->
                OptionsDialog.show(
                    context = view.context,
                    title = tvChapterTitle.text.toString(),
                    subtitle = "Chapter options",
                    options = listOf(
                        OptionsDialog.Option("Schedule publish", "Pick a date and time to go live", R.drawable.ic_timer),
                        OptionsDialog.Option("View statistics", "Views, comments and ratings", R.drawable.ic_studio_stats)
                    ),
                    selectedIndex = -1,
                    showRadio = false
                ) { index ->
                    when (index) {
                        0 -> onScheduleChapter(chapter)
                        1 -> onViewStatistics(chapter)
                    }
                }
            }

            root.setOnClickListener {
                onChapterClick(chapter)
            }
        }
    }

    private fun showStatus(binding: ItemWriterChapterBinding, published: Boolean) {
        binding.tvStatusLabel.text = if (published) "● PUBLISHED" else "● DRAFT"
        binding.tvStatusLabel.setTextColor(if (published) PUBLISHED_COLOR else DRAFT_COLOR)
        binding.tvStatusLabel.setBackgroundResource(if (published) R.drawable.bg_status_live else R.drawable.bg_status_draft)
        binding.tvSwitchHint.text = if (published) "Live" else "Publish"
    }

    override fun getItemCount(): Int = chapters.size

    /** Loaded chapters (for the header counts). */
    val items: List<WoChapter> get() = chapters

    private companion object {
        val PUBLISHED_COLOR = Color.parseColor("#10B981")
        val DRAFT_COLOR = Color.parseColor("#F59E0B")
    }

    fun updateData(newChapters: List<WoChapter>) {
        chapters.clear()
        chapters.addAll(newChapters)
        notifyDataSetChanged()
    }

    fun addChapters(newChapters: List<WoChapter>) {
        val startPosition = chapters.size
        chapters.addAll(newChapters)
        notifyItemRangeInserted(startPosition, newChapters.size)
    }

    fun addChapter(newChapter: WoChapter, atTop: Boolean) {
        if (atTop) {
            chapters.add(0, newChapter)
            // Using notifyDataSetChanged for robust update in NestedScrollView
            notifyDataSetChanged()
        } else {
            chapters.add(newChapter)
            notifyItemInserted(chapters.size - 1)
        }
    }

    fun updateChapter(updatedChapter: WoChapter) {
        val index = chapters.indexOfFirst { it.id == updatedChapter.id }
        if (index != -1) {
            chapters[index] = updatedChapter
            notifyItemChanged(index)
        }
    }
}
