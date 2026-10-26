/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.adapter

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.engine.core.helper.ImageHelper
import com.engine.core.manager.GlobalAppData
import com.engine.core.manager.ThemePrefManager
import com.engine.core.models.HomepageNovelWithTitle
import com.engine.core.models.Novel
import com.engine.core.models.WoCategory
import com.engine.core.models.WoChapter
import com.engine.core.utill.ThemeUtils
import com.writer_hub.novelserise.AudioPlayerActivity
import com.writer_hub.novelserise.CategoryNovelActivity
import com.writer_hub.novelserise.DailyBonusActivity
import com.writer_hub.novelserise.HomeSectionNovelByIdActivity
import com.writer_hub.novelserise.MessageActivity
import com.writer_hub.novelserise.ReadChapterActivity
import com.writer_hub.novelserise.ReferEarnActivity
import com.writer_hub.novelserise.RewardsActivity
import com.writer_hub.novelserise.R
import com.writer_hub.novelserise.databinding.ItemContinueReadingBinding
import com.writer_hub.novelserise.databinding.ItemHomeAdBinding
import com.writer_hub.novelserise.databinding.ItemHomeGenresBinding
import com.writer_hub.novelserise.databinding.ItemHomeSectionBinding
import com.writer_hub.novelserise.databinding.ItemHomeSectionTrendingBinding
import com.writer_hub.novelserise.databinding.WidgetPremiumOptionsBinding
import com.writer_hub.novelserise.util.DisplayText
import com.writer_hub.novelserise.util.RateAppManager

sealed interface HomeFeedItem {
    data class SectionItem(val section: HomepageNovelWithTitle) : HomeFeedItem
    object PremiumOptionsItem : HomeFeedItem
    data class ContinueReadingItem(val novel: Novel, val chapter: WoChapter, val appType: String) : HomeFeedItem
    data class GenresItem(val categories: List<WoCategory>) : HomeFeedItem
    /** In-feed slot that hosts the (single, long-lived) home banner ad view. */
    object AdItem : HomeFeedItem
}

/**
 * @param adView the loaded home banner ad; moved into the [HomeFeedItem.AdItem] slot when bound.
 */
class HomeSectionAdapter(items: List<HomeFeedItem>, private val adView: View? = null) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<HomeFeedItem> = items.toList()

    /**
     * Updates the feed in place: unchanged rows are not rebound, so their covers keep loading
     * instead of being cancelled every time a late piece (genres, progress, picks) arrives.
     */
    fun submit(newItems: List<HomeFeedItem>) {
        val old = items
        items = newItems.toList()
        androidx.recyclerview.widget.DiffUtil.calculateDiff(object : androidx.recyclerview.widget.DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = items.size
            override fun areItemsTheSame(o: Int, n: Int) = identity(old[o]) == identity(items[n])
            override fun areContentsTheSame(o: Int, n: Int) = old[o] == items[n]
        }).dispatchUpdatesTo(this)
    }

    private fun identity(item: HomeFeedItem): String = when (item) {
        is HomeFeedItem.SectionItem -> "section:${item.section.key}:${item.section.sectionId}:${item.section.title}"
        is HomeFeedItem.ContinueReadingItem -> "continue"
        is HomeFeedItem.GenresItem -> "genres"
        HomeFeedItem.PremiumOptionsItem -> "premium"
        HomeFeedItem.AdItem -> "ad"
    }

    private val TYPE_GENERAL = 0
    private val TYPE_TRENDING = 1
    private val TYPE_PREMIUM_OPTIONS = 2
    private val TYPE_BANNER = 3
    private val TYPE_CONTINUE_READING = 4
    private val TYPE_GENRES = 5
    private val TYPE_AD = 6

    // Color palettes (Resources managed)
    private val lightColorRes = listOf(
        R.color.home_bg_light_1,
        R.color.home_bg_light_2,
        R.color.home_bg_light_3,
        R.color.home_bg_light_4,
        R.color.home_bg_light_5,
        R.color.home_bg_light_6,
        R.color.home_bg_light_7
    )
    // Dark mode: every section card uses the same deep green.
    private val darkSectionColorRes = R.color.home_bg_dark_3

    // Card views are shared across all sections, so a section scrolling into view reuses cards
    // instead of inflating new ones. One pool per card layout (both adapters use view type 0).
    private val featuredPool = RecyclerView.RecycledViewPool().apply { setMaxRecycledViews(0, 30) }
    private val trendingPool = RecyclerView.RecycledViewPool().apply { setMaxRecycledViews(0, 24) }

    // Theme inputs for the section colour, read once instead of on every bind.
    private var isDarkMode: Boolean? = null
    private var themeStyle: String? = null

    override fun getItemViewType(position: Int): Int {
        return when (val item = items[position]) {
            is HomeFeedItem.PremiumOptionsItem -> TYPE_PREMIUM_OPTIONS
            is HomeFeedItem.ContinueReadingItem -> TYPE_CONTINUE_READING
            is HomeFeedItem.GenresItem -> TYPE_GENRES
            is HomeFeedItem.AdItem -> TYPE_AD
            is HomeFeedItem.SectionItem -> {
                val section = item.section
                when {
                    section.key == "trending" -> TYPE_TRENDING
                    isBannerSection(section) -> TYPE_BANNER
                    else -> TYPE_GENERAL
                }
            }
        }
    }

    private fun isBannerSection(section: HomepageNovelWithTitle): Boolean {
        val key = section.key.trim()
        val title = section.title.trim()
        return key.contains("middle", ignoreCase = true) ||
            title.contains("middle", ignoreCase = true) ||
            key.equals("featured_showcase", ignoreCase = true) ||
            key.equals("featured-showcase", ignoreCase = true) ||
            title.equals("Featured Showcase", ignoreCase = true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_TRENDING -> TrendingSectionViewHolder(ItemHomeSectionTrendingBinding.inflate(inflater, parent, false))
            TYPE_GENERAL -> GeneralSectionViewHolder(ItemHomeSectionBinding.inflate(inflater, parent, false))
            TYPE_PREMIUM_OPTIONS -> PremiumOptionsViewHolder(WidgetPremiumOptionsBinding.inflate(inflater, parent, false))
            TYPE_BANNER -> BannerSectionViewHolder(ItemHomeSectionBinding.inflate(inflater, parent, false))
            TYPE_CONTINUE_READING -> ContinueReadingViewHolder(ItemContinueReadingBinding.inflate(inflater, parent, false))
            TYPE_GENRES -> GenresViewHolder(ItemHomeGenresBinding.inflate(inflater, parent, false))
            TYPE_AD -> AdViewHolder(ItemHomeAdBinding.inflate(inflater, parent, false))
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        val color = getBackgroundColor(context, position)

        when (holder) {
            is TrendingSectionViewHolder -> if (item is HomeFeedItem.SectionItem) holder.bind(item.section, color)
            is GeneralSectionViewHolder -> if (item is HomeFeedItem.SectionItem) holder.bind(item.section, color)
            is BannerSectionViewHolder -> if (item is HomeFeedItem.SectionItem) holder.bind(item.section)
            is PremiumOptionsViewHolder -> holder.bind()
            is ContinueReadingViewHolder -> if (item is HomeFeedItem.ContinueReadingItem) holder.bind(item)
            is GenresViewHolder -> if (item is HomeFeedItem.GenresItem) holder.bind(item.categories)
            is AdViewHolder -> holder.bind()
        }
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        if (holder is BannerSectionViewHolder) {
            holder.stopAutoSlide()
        }
    }

    private fun getBackgroundColor(context: Context, position: Int): Int {
        val dark = isDarkMode ?: ((context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES).also { isDarkMode = it }

        val resId = if (dark) {
            darkSectionColorRes
        } else {
            val style = themeStyle ?: ThemePrefManager(context).themeStyle.also { themeStyle = it }
            when (style) {
                ThemePrefManager.STYLE_ROMANTIC -> lightColorRes[(position + 1) % lightColorRes.size]
                ThemePrefManager.STYLE_SKYBLUE -> lightColorRes[(position + 2) % lightColorRes.size]
                ThemePrefManager.STYLE_COMIC -> lightColorRes[(position + 3) % lightColorRes.size]
                else -> lightColorRes[position % lightColorRes.size]
            }
        }
        return ContextCompat.getColor(context, resId)
    }

    override fun getItemCount(): Int = items.size

    /**
     * Rounded section background: a GradientDrawable round-rect, the cheapest rounded shape to draw.
     * No outline clipping: the covers sit inside the section's 16dp vertical padding, so they never
     * reach the rounded corners, and clipping every scrolling frame would only cost GPU time.
     */
    private fun View.paintSection(color: Int) {
        val shape = background as? android.graphics.drawable.GradientDrawable
            ?: android.graphics.drawable.GradientDrawable().also {
                it.cornerRadius = 16f * resources.displayMetrics.density
                background = it
            }
        shape.setColor(color)
    }

    // --- VIEW HOLDERS ---

    inner class ContinueReadingViewHolder(private val binding: ItemContinueReadingBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: HomeFeedItem.ContinueReadingItem) {
            val novel = item.novel
            val chapter = item.chapter
            
            val isAudio = GlobalAppData.isAudioMode(item.appType)
            binding.tvLabel.text = if (isAudio) "Continue Listening" else "Continue Reading"
            // Reading mode resumes the reader, so the button shows a book, not play.
            binding.btnContinue.setImageResource(if (isAudio) R.drawable.ic_player_play else R.drawable.ic_continue_read)
            binding.btnContinue.contentDescription = if (isAudio) "Resume listening" else "Continue reading"

            // How far into the chapter, as saved by the player/reader (0-100).
            val percent = chapter.readingStatus?.toIntOrNull()?.coerceIn(0, 100) ?: 0
            val chapterNumber = chapter.sqc?.toIntOrNull()?.takeIf { it > 0 }
            binding.progressChapter.setProgressCompat(percent, false)
            binding.tvProgress.text = listOfNotNull(
                chapterNumber?.let { "Chapter $it" },
                "$percent% ${if (isAudio) "listened" else "read"}"
            ).joinToString(" \u2022 ")
            
            binding.tvNovelTitle.text = DisplayText.of(novel.title)
            binding.tvChapterTitle.text = DisplayText.of(chapter.chapterName)

            // Fixed: Consistently use imageLink as in NovelDetailActivity
            val density = binding.root.resources.displayMetrics.density
            ImageHelper.loadImage(
                binding.root.context, novel.imageLink, binding.ivCover, allowHardware = true,
                targetWidthPx = (64 * density).toInt(), targetHeightPx = (96 * density).toInt()
            )
                
            val onClick = View.OnClickListener {
                if (GlobalAppData.isAudioMode(item.appType)) {
                    AudioPlayerActivity.start(it.context, chapter.id ?: "", novel.id ?: "")
                } else {
                    chapter.id?.toIntOrNull()?.let { id ->
                        ReadChapterActivity.start(it.context, id)
                    }
                }
            }
            
            binding.container.setOnClickListener(onClick)
            binding.btnContinue.setOnClickListener(onClick)
        }
    }

    inner class TrendingSectionViewHolder(private val binding: ItemHomeSectionTrendingBinding) :
        RecyclerView.ViewHolder(binding.root) {
        // Built once per view; bind only swaps data.
        private val grid = GridLayoutManager(binding.root.context, 2, GridLayoutManager.HORIZONTAL, false)
            .apply { initialPrefetchItemCount = 8 }

        init {
            binding.rvTrendingOthers.apply {
                layoutManager = grid
                setRecycledViewPool(trendingPool)
                setHasFixedSize(true)
                itemAnimator = null
            }
        }

        fun bind(section: HomepageNovelWithTitle, color: Int) {
            binding.tvSectionTitle.text = DisplayText.of(section.title)
            binding.sectionCard.paintSection(color)
            
            binding.tvSeeAll.visibility = if (section.sectionId != 0) View.VISIBLE else View.GONE
            binding.tvSeeAll.setOnClickListener {
                HomeSectionNovelByIdActivity.start(it.context, section.sectionId, section.title, section.key, section.novels)
            }

            // No separate #1 hero card (hidden in the layout): every ranked novel sits in the same
            // two-row grid as the other sections.
            val novels = section.novels
            val rows = if (novels.size >= 4) 2 else 1
            if (grid.spanCount != rows) grid.spanCount = rows
            val adapter = binding.rvTrendingOthers.adapter as? TrendingRowAdapter
            if (adapter == null) binding.rvTrendingOthers.adapter = TrendingRowAdapter(novels, rows) else adapter.submit(novels, rows)
        }
    }

    inner class GeneralSectionViewHolder(private val binding: ItemHomeSectionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        // Built once per view; bind only swaps data.
        private val grid = GridLayoutManager(binding.root.context, 2, GridLayoutManager.HORIZONTAL, false)
            .apply { initialPrefetchItemCount = 8 }

        init {
            binding.rvSectionNovels.apply {
                layoutManager = grid
                setRecycledViewPool(featuredPool)
                setHasFixedSize(true)
                itemAnimator = null
            }
        }

        fun bind(section: HomepageNovelWithTitle, color: Int) {
            binding.tvSectionTitle.text = DisplayText.of(section.title)
            binding.sectionCard.paintSection(color)
            
            binding.btnViewAll.visibility = if (section.sectionId != 0) View.VISIBLE else View.GONE
            binding.btnViewAll.setOnClickListener {
                HomeSectionNovelByIdActivity.start(it.context, section.sectionId, section.title, section.key, section.novels)
            }

            // Two rows like "Highest Rated"; a single row only when there are too few novels to fill two.
            val novelCount = section.novels.size
            val spanCount = if (novelCount >= 4) 2 else 1

            val maxDisplayItems = spanCount * 5
            val displayNovels = if (novelCount > maxDisplayItems) section.novels.take(maxDisplayItems) else section.novels

            if (grid.spanCount != spanCount) grid.spanCount = spanCount
            val adapter = binding.rvSectionNovels.adapter as? FeaturedNovelAdapter
            if (adapter == null) binding.rvSectionNovels.adapter = FeaturedNovelAdapter(displayNovels) else adapter.submit(displayNovels)
        }
    }

    inner class BannerSectionViewHolder(private val binding: ItemHomeSectionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private val handler = Handler(Looper.getMainLooper())
        private var runnable: Runnable? = null

        fun bind(section: HomepageNovelWithTitle) {
            binding.sectionHeader.visibility = View.GONE
            binding.tvSectionTitle.visibility = View.GONE
            binding.btnViewAll.visibility = View.GONE
            // Banners round their own corners; no section background needed.
            binding.sectionCard.background = null
            binding.sectionContainer.setPadding(0, 0, 0, 0)

            val adapter = HomeBannerAdapter(section.novels)
            binding.rvSectionNovels.adapter = adapter
            binding.rvSectionNovels.layoutManager = LinearLayoutManager(binding.root.context, LinearLayoutManager.HORIZONTAL, false)
            
            if (binding.rvSectionNovels.onFlingListener == null) {
                PagerSnapHelper().attachToRecyclerView(binding.rvSectionNovels)
            }

            setupDots(section.novels.size)

            stopAutoSlide()
            if (section.novels.size > 1) {
                startAutoSlide(section.novels.size)
            }
        }

        /** One dot per banner; the current one is wider and fully opaque. */
        private fun setupDots(count: Int) {
            val dots = binding.bannerDots
            dots.removeAllViews()
            dots.visibility = if (count > 1) View.VISIBLE else View.GONE
            if (count <= 1) return
            val density = binding.root.resources.displayMetrics.density
            repeat(count) {
                dots.addView(View(binding.root.context).apply {
                    setBackgroundResource(R.drawable.bg_banner_dot)
                    layoutParams = android.widget.LinearLayout.LayoutParams((6 * density).toInt(), (6 * density).toInt())
                        .apply { marginStart = (3 * density).toInt(); marginEnd = (3 * density).toInt() }
                })
            }
            highlightDot(0)
            binding.rvSectionNovels.clearOnScrollListeners()
            binding.rvSectionNovels.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState != RecyclerView.SCROLL_STATE_IDLE) return
                    val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                    val position = lm.findFirstCompletelyVisibleItemPosition().takeIf { it != RecyclerView.NO_POSITION }
                        ?: lm.findFirstVisibleItemPosition()
                    if (position != RecyclerView.NO_POSITION) highlightDot(position)
                }
            })
        }

        private fun highlightDot(active: Int) {
            val density = binding.root.resources.displayMetrics.density
            for (i in 0 until binding.bannerDots.childCount) {
                val dot = binding.bannerDots.getChildAt(i)
                val selected = i == active
                dot.alpha = if (selected) 1f else 0.35f
                dot.layoutParams = dot.layoutParams.apply { width = ((if (selected) 16 else 6) * density).toInt() }
            }
        }

        private fun startAutoSlide(size: Int) {
            runnable = object : Runnable {
                override fun run() {
                    val lm = binding.rvSectionNovels.layoutManager as LinearLayoutManager
                    val currentPos = lm.findFirstVisibleItemPosition()
                    if (currentPos != RecyclerView.NO_POSITION) {
                        val nextPos = (currentPos + 1) % size
                        binding.rvSectionNovels.smoothScrollToPosition(nextPos)
                    }
                    handler.postDelayed(this, 3000)
                }
            }
            handler.postDelayed(runnable!!, 3000)
        }

        fun stopAutoSlide() {
            runnable?.let { handler.removeCallbacks(it) }
            runnable = null
        }
    }

    inner class GenresViewHolder(private val binding: ItemHomeGenresBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(categories: List<WoCategory>) {
            val context = binding.root.context
            val group = binding.chipGroupGenres
            group.removeAllViews()
            val chipBackground = android.content.res.ColorStateList.valueOf(
                ThemeUtils.getThemeColor(context, com.google.android.material.R.attr.colorSurfaceVariant)
            )
            val chipText = ThemeUtils.getThemeColor(context, com.google.android.material.R.attr.colorOnSurface)
            categories.forEach { category ->
                group.addView(com.google.android.material.chip.Chip(context).apply {
                    text = DisplayText.of(category.name)
                    isCheckable = false
                    chipBackgroundColor = chipBackground
                    chipStrokeWidth = 0f
                    setTextColor(chipText)
                    setOnClickListener { CategoryNovelActivity.start(context, category.id) }
                })
            }
        }
    }

    inner class AdViewHolder(private val binding: ItemHomeAdBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            val ad = adView ?: return
            if (ad.parent === binding.adSlot) return
            (ad.parent as? ViewGroup)?.removeView(ad)
            binding.adSlot.removeAllViews()
            binding.adSlot.addView(ad)
        }
    }

    inner class PremiumOptionsViewHolder(private val binding: WidgetPremiumOptionsBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            val context = binding.root.context
            binding.llRateUs.setOnClickListener { RateAppManager.showRateDialog(context) }
            binding.llChat.setOnClickListener { context.startActivity(Intent(context, MessageActivity::class.java)) }
            binding.llGift.setOnClickListener { context.startActivity(Intent(context, RewardsActivity::class.java)) }
            binding.llDailyBonus.setOnClickListener { context.startActivity(Intent(context, DailyBonusActivity::class.java)) }
            binding.llShareEarn.setOnClickListener { context.startActivity(Intent(context, ReferEarnActivity::class.java)) }
        }
    }
}
