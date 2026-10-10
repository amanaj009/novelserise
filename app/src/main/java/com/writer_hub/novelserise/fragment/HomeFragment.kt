/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.fragment

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.RadioButton
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.engine.core.AppCore.tokenManager
import com.engine.core.helper.AppLoggerHelper
import com.engine.core.helper.ImageHelper
import com.engine.core.helper.decodeHtmlToTextV2
import com.engine.core.manager.GlobalAppData
import com.engine.core.manager.GlobalVariableManager
import com.engine.core.models.HomepageNovelLite
import com.engine.core.models.HomepageNovelWithTitle
import com.engine.core.models.Novel
import com.engine.core.models.WoCategory
import com.engine.core.viewmodel.AdsViewModel
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.NovelViewModel
import com.engine.core.viewmodel.SearchViewModel
import com.engine.core.viewmodel.SplashViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.writer_hub.novelserise.NovelDetailActivity
import com.writer_hub.novelserise.R
import com.writer_hub.novelserise.SearchActivity
import com.writer_hub.novelserise.SplashActivity
import com.writer_hub.novelserise.UserDetailsActivity
import com.writer_hub.novelserise.adapter.HomeFeedItem
import com.writer_hub.novelserise.adapter.HomeSectionAdapter
import com.writer_hub.novelserise.databinding.DialogFeaturedNovelBinding
import com.writer_hub.novelserise.databinding.FragmentHomeBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var novelViewModel: NovelViewModel
    private lateinit var splashViewModel: SplashViewModel
    private lateinit var adsViewModel: AdsViewModel
    private lateinit var searchViewModel: SearchViewModel
    private val TAG = "HomeFragment"

    /** Last sections from the server, so the feed can be rebuilt locally (e.g. new progress). */
    private var cachedSections: List<HomepageNovelWithTitle>? = null
    private var allCategories: List<WoCategory> = emptyList()
    private var genres: List<WoCategory> = emptyList()
    /** "Because you listened to ..." row, and the novel it was built for (so it is fetched once per novel). */
    private var becauseYou: HomepageNovelWithTitle? = null
    private var becauseYouNovelId: String? = null
    /** Loaded once and moved into the in-feed ad slot, so scrolling never reloads the ad. */
    private var homeAdView: FrameLayout? = null

    private var isDialogShownInSession = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        novelViewModel = AppProvider.getViewModel(requireActivity().application, NovelViewModel::class.java)
        splashViewModel = AppProvider.getViewModel(requireActivity().application, SplashViewModel::class.java)
        adsViewModel = AppProvider.getViewModel(requireActivity().application, AdsViewModel::class.java)
        searchViewModel = AppProvider.getViewModel(requireActivity().application, SearchViewModel::class.java)

        checkAndShowAppTypeSelection()
        setupUserTopBar()
        setupRecyclerView()
        setupSwipeRefresh()
        
        // Fetch last reading first so it's ready when fetchData is called
        splashViewModel.getLastReading()
        
        // Progress saves update the reading often; rebuild locally instead of refetching the whole home.
        splashViewModel.currentReading.observe(viewLifecycleOwner) { if (cachedSections != null) renderFeed() }

        // Genre chips reuse the search screen's category list (it carries the ids to open a genre).
        searchViewModel.categories.observe(viewLifecycleOwner) { categories ->
            allCategories = categories
            genres = categories.sortedByDescending { it.novel_count ?: 0 }.take(12)
            if (cachedSections != null) renderFeed()
        }
        searchViewModel.fetchCategories()

        checkAndShowHomeBannerAd()
        fetchData()
        
        view.post {
            if (_binding != null) {
                checkAndShowFeaturedNovel()
            }
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            fetchData()
        }
        // Optional: Customize color
        binding.swipeRefresh.setColorSchemeResources(R.color.colorPrimary)
    }

    /** The banner ad now scrolls with the feed (after the first section) instead of pinning to the bottom. */
    private fun checkAndShowHomeBannerAd() {
        homeAdView = if (GlobalVariableManager.shouldShowHomeBannerAd()) {
            FrameLayout(requireContext()).also { adsViewModel.showBannerAd(requireActivity(), it) }
        } else null
    }

    private fun checkAndShowFeaturedNovel() {
        if (isDialogShownInSession || _binding == null) {
            AppLoggerHelper.d(TAG, "Featured dialog skipped or binding null.")
            return
        }

        AppLoggerHelper.d(TAG, "Checking featured logic...")

        viewLifecycleOwner.lifecycleScope.launch {
            delay(2000)

            val currentBinding = _binding ?: run {
                AppLoggerHelper.d(TAG, "Fragment view destroyed during delay.")
                return@launch
            }

            if (!isAdded) {
                AppLoggerHelper.d(TAG, "Fragment not added after delay, skipping dialog.")
                return@launch
            }

            val isAudioMode = GlobalAppData.isAudioMode()

            // The server is authoritative. A cached item may have been disabled in admin,
            // so never show stale promotional content before refreshing it.
            val featuredItem = novelViewModel.fetchFeaturedNovel()
            if (featuredItem != null) {
                AppLoggerHelper.d(
                    TAG,
                    "Fetched featured: ${featuredItem.title} (createAudio=${featuredItem.createAudio}), saving to prefs."
                )
                novelViewModel.saveFeaturedToPrefs(featuredItem)
            }

            // Only show an item that matches the currently selected app mode.
            if (featuredItem != null && isAdded && _binding != null) {
                val isItemAudio = featuredItem.createAudio == "1"

                if (isAudioMode == isItemAudio) {
                    AppLoggerHelper.d(TAG, "Showing featured dialog for: ${featuredItem.title}")
                    showFeaturedNovelDialog(featuredItem)
                } else {
                    AppLoggerHelper.d(
                        TAG,
                        "Featured item type mismatch with app mode. Item: ${if (isItemAudio) "Audio" else "Novel"}, Mode: ${if (isAudioMode) "Audio" else "Novel"}"
                    )
                }
            } else {
                AppLoggerHelper.w(TAG, "Featured item is null or fragment/view no longer valid.")
            }
        }
    }

    private fun showFeaturedNovelDialog(novel: Novel) {
        val currentContext = context ?: return
        try {
            val isAudio = GlobalAppData.isAudioMode()
            val dialogView = DialogFeaturedNovelBinding.inflate(layoutInflater)
            val dialog = AlertDialog.Builder(currentContext)
                .setView(dialogView.root)
                .setCancelable(true)
                .create()

            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.window?.requestFeature(Window.FEATURE_NO_TITLE)

            dialogView.tvFeaturedTitle.text = decodeHtmlToTextV2(novel.title)
            val authorName = novel.userName ?: novel.writer ?: "App Writer"
            dialogView.tvFeaturedAuthor.text = "by ${decodeHtmlToTextV2(authorName)}"
            dialogView.tvFeaturedDescription.text = decodeHtmlToTextV2(novel.description)

            // Featured dialog always uses the official 2:3 novel cover.
            // Banner artwork can have a different aspect ratio and crop poorly here.
            val imageUrl = novel.imageLink
            ImageHelper.loadImage(currentContext, imageUrl, dialogView.ivFeaturedBanner)

            dialogView.tvBadge.text = if (isAudio) "FEATURED AUDIOBOOK" else "FEATURED NOVEL"
            dialogView.btnReadNow.text = if (isAudio) "Start Listening" else "Start Reading"

            dialogView.btnClose.setOnClickListener { dialog.dismiss() }
            
            dialogView.btnReadNow.setOnClickListener {
                dialog.dismiss()
                novel.id?.let { id -> NovelDetailActivity.start(currentContext, id) }
            }

            dialog.show()
            AppLoggerHelper.d(TAG, "Featured dialog displayed successfully.")
            
            isDialogShownInSession = true

        } catch (e: Exception) {
            AppLoggerHelper.e(TAG, "Error showing featured dialog: ${e.message}", e)
        }
    }

    private fun checkAndShowAppTypeSelection() {
        if (!splashViewModel.isAppTypeConfigured()) {
            showAppTypeSelectionDialog(cancelable = false)
        }
    }

    private fun showAppTypeSelectionDialog(cancelable: Boolean) {
        val currentContext = context ?: return
        val dialog = BottomSheetDialog(currentContext)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_app_type, null)
        dialog.setContentView(view)
        dialog.setCancelable(cancelable)

        val cardNovel = view.findViewById<View>(R.id.cardNovelMode)
        val cardAudio = view.findViewById<View>(R.id.cardAudioMode)
        val radioNovel = view.findViewById<RadioButton>(R.id.radioNovel)
        val radioAudio = view.findViewById<RadioButton>(R.id.radioAudio)

        if (!splashViewModel.isAppTypeConfigured()) {
            radioAudio.isChecked = true
            radioNovel.isChecked = false
        } else {
            if (GlobalAppData.isNovelMode()) {
                radioNovel.isChecked = true
                radioAudio.isChecked = false
            } else {
                radioAudio.isChecked = true
                radioNovel.isChecked = false
            }
        }

        cardNovel.setOnClickListener {
            handleAppTypeSelection(GlobalAppData.APP_TYPE_ONLY_NOVEL, dialog)
        }

        cardAudio.setOnClickListener {
            handleAppTypeSelection(GlobalAppData.APP_TYPE_ONLY_AUDIO, dialog)
        }

        dialog.show()
    }

    private fun handleAppTypeSelection(type: String, dialog: BottomSheetDialog) {
        val currentType = splashViewModel.getAppType()
        
        splashViewModel.setAppType(type)
        splashViewModel.setAppTypeConfigured(true)
        
        dialog.dismiss()

        if (currentType != type || !splashViewModel.isAppTypeConfigured()) {
            restartApp()
        }
    }

    private fun restartApp() {
        val currentContext = context ?: return
        val intent = Intent(currentContext, SplashActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        activity?.finish()
    }

    private fun setupUserTopBar() {
        _binding?.let { binding ->
            val (_, userId) = tokenManager.getTokenAndUserId()
            if (userId != null) {
                val user = tokenManager.getUser(userId)
                context?.let {
                    ImageHelper.loadImage(
                        it,
                        user?.avatar,
                        binding.ivProfile,
                        com.engine.core.R.drawable.ic_profile
                    )
                }
            }

            updateModeSelectorText()

            binding.searchCard.setOnClickListener {
                startActivity(
                    Intent(
                        context ?: return@setOnClickListener,
                        SearchActivity::class.java
                    )
                )
            }

            binding.modeSelectorCard.setOnClickListener {
                showAppModeDropdown()
            }

            binding.ivProfile.setOnClickListener {
                val (_, currentUserId) = tokenManager.getTokenAndUserId()
                if (currentUserId != null) {
                    val intent = Intent(
                        context ?: return@setOnClickListener,
                        UserDetailsActivity::class.java
                    ).apply {
                        putExtra("user_id", currentUserId)
                    }
                    startActivity(intent)
                }
            }
        }
    }

    private fun updateModeSelectorText() {
        _binding?.let { binding ->
            val isAudio = GlobalAppData.isAudioMode()
            binding.tvAppMode.text = if (isAudio) "Audio" else "Novel"
            binding.tvSearchHint.text = if (isAudio) "Search audio..." else "Search novels..."
        }
    }

    private fun showAppModeDropdown() {
        val anchor = _binding?.modeSelectorCard ?: return
        val popupView = layoutInflater.inflate(R.layout.popup_app_mode, null)
        val popup = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = 12f
        }

        val optionNovel = popupView.findViewById<MaterialCardView>(R.id.optionNovel)
        val optionAudio = popupView.findViewById<MaterialCardView>(R.id.optionAudio)
        val checkNovel = popupView.findViewById<ImageView>(R.id.checkNovel)
        val checkAudio = popupView.findViewById<ImageView>(R.id.checkAudio)
        val isAudio = GlobalAppData.isAudioMode()

        optionNovel.strokeWidth = if (isAudio) 0 else dpToPx(1)
        optionAudio.strokeWidth = if (isAudio) dpToPx(1) else 0
        checkNovel.visibility = if (isAudio) View.INVISIBLE else View.VISIBLE
        checkAudio.visibility = if (isAudio) View.VISIBLE else View.INVISIBLE

        fun selectMode(selectedType: String) {
            popup.dismiss()
            if (splashViewModel.getAppType() != selectedType) {
                splashViewModel.setAppType(selectedType)
                splashViewModel.setAppTypeConfigured(true)
                restartApp()
            }
        }

        optionNovel.setOnClickListener { selectMode(GlobalAppData.APP_TYPE_ONLY_NOVEL) }
        optionAudio.setOnClickListener { selectMode(GlobalAppData.APP_TYPE_ONLY_AUDIO) }
        popup.showAsDropDown(anchor, 0, dpToPx(6))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun setupRecyclerView() {
        _binding?.rvHome?.apply {
            layoutManager = LinearLayoutManager(context).apply { initialPrefetchItemCount = 2 }
            setHasFixedSize(true)
            // Keep a few sections alive off-screen so scrolling back does not rebind them.
            setItemViewCacheSize(6)
            // Feed updates (progress, genres) should not cross-fade whole sections.
            (itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)?.supportsChangeAnimations = false
        }
    }

    private fun fetchData() {
        viewLifecycleOwner.lifecycleScope.launch {
            _binding?.let { binding ->
                if (!binding.swipeRefresh.isRefreshing) {
                    binding.progressBar.visibility = View.VISIBLE
                }
            }
            try {
                val fetchedSections = novelViewModel.fetchNovelsInBulk(public = "1")
                if (fetchedSections != null) {
                    cachedSections = fetchedSections.filter { it.novels.isNotEmpty() }
                    renderFeed()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _binding?.let { binding ->
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                }
            }
        }
    }

    /**
     * Feed order: quick actions, continue card, genre chips, first section, ad,
     * "Because you listened to ..." picks, then the remaining sections.
     */
    private fun renderFeed() {
        val binding = _binding ?: return
        val sections = cachedSections ?: return
        loadBecauseYou()
        val items = mutableListOf<HomeFeedItem>(HomeFeedItem.PremiumOptionsItem)

        val lastReading = splashViewModel.currentReading.value
        val lastNovel = lastReading?.first
        val lastChapter = lastReading?.second
        if (lastNovel != null && lastChapter != null) {
            items.add(HomeFeedItem.ContinueReadingItem(lastNovel, lastChapter, splashViewModel.getAppType()))
        }
        if (genres.isNotEmpty()) items.add(HomeFeedItem.GenresItem(genres))

        val sectionItems = sections.map { HomeFeedItem.SectionItem(it) }
        items.addAll(sectionItems.take(1))
        if (homeAdView != null) items.add(HomeFeedItem.AdItem)
        if (lastNovel != null && becauseYouNovelId == lastNovel.id) becauseYou?.let { items.add(HomeFeedItem.SectionItem(it)) }
        items.addAll(sectionItems.drop(1))

        // Update in place so rows that did not change keep their state and loading images.
        val adapter = binding.rvHome.adapter as? HomeSectionAdapter
        if (adapter != null) adapter.submit(items) else binding.rvHome.adapter = HomeSectionAdapter(items, homeAdView)
    }

    /**
     * Loads more novels from the last novel's genre (home API novels carry no genre, so this asks the
     * category endpoint) and shows them as "Because you listened to ...". Runs once per last novel.
     */
    private fun loadBecauseYou() {
        val lastNovel = splashViewModel.currentReading.value?.first ?: return
        val novelId = lastNovel.id ?: return
        if (novelId == becauseYouNovelId) return
        val genre = lastNovel.categoryName?.takeIf { it.isNotBlank() } ?: return
        val categoryId = allCategories.firstOrNull { decodeHtmlToTextV2(it.name).equals(genre, ignoreCase = true) }?.id ?: return
        becauseYouNovelId = novelId
        becauseYou = null

        viewLifecycleOwner.lifecycleScope.launch {
            val picks = try {
                novelViewModel.fetchNovelsByCategoryAndSubcategory(
                    categoryId = categoryId, subcategoryId = "all", type = "get_novel", offset = 0, limit = 20,
                    timeSlot = "0", filter = null, resourceId = "0", userId = null, NovelId = null, competitionId = null
                )
            } catch (e: Exception) {
                AppLoggerHelper.e(TAG, "Because-you row failed", e); emptyList()
            }
                .filter { !it.id.isNullOrBlank() && it.id != novelId }
                .distinctBy { it.id }
                .take(10)
                .map { it.toHomeLite() }
            if (picks.size < 4 || becauseYouNovelId != novelId) return@launch
            val verb = if (GlobalAppData.isAudioMode()) "listened to" else "read"
            becauseYou = HomepageNovelWithTitle(
                title = "Because you $verb ${decodeHtmlToTextV2(lastNovel.title)}",
                subtitle = null, key = "because_you_listened", type = "novel",
                value = null, orderBy = null, ascending = null, notEqual = null, novels = picks
            )
            renderFeed()
        }
    }

    private fun Novel.toHomeLite() = HomepageNovelLite(
        id = id.orEmpty(), isPremium = isPremium ?: "0", title = title.orEmpty(), description = description.orEmpty(),
        imageLinkCover = imageLinkCover, imageLink = imageLink, chapterCount = chapterCount?.toString(),
        avgRating = avgRating.toString(), viewCount = view, authorName = userName, categoryName = categoryName
    )

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
