/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.sheet

import com.engine.core.manager.GlobalAppData
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.writer_hub.novelserise.EarnCoinsActivity
import com.writer_hub.novelserise.ShopActivity
import com.writer_hub.novelserise.databinding.BottomSheetUnlockChapterBinding
import com.engine.core.helper.ImageHelper
import com.engine.core.helper.ToastHelper
import com.engine.core.manager.GlobalVariableManager
import com.engine.core.models.Novel
import com.engine.core.models.ReadingSettings
import com.engine.core.models.UserDetailsModel
import com.engine.core.utill.ThemeUtils
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.UserViewModel
import com.engine.core.viewmodel.WoChpterViewModel
import com.writer_hub.novelserise.R
import com.writer_hub.novelserise.util.DisplayText

/**
 * Locked-chapter sheet: shows which chapter is locked, lets the user pick Diamonds or Points
 * (with price, balance and shortfall on each card), offers a free ad option, and has one primary
 * button that either unlocks with the chosen method or sends the user to top up.
 */
class UnlockChapterBottomSheetNew : BottomSheetDialogFragment() {

    private var _binding: BottomSheetUnlockChapterBinding? = null
    private val binding get() = _binding!!

    private lateinit var woChpterViewModel: WoChpterViewModel
    private lateinit var userViewModel: UserViewModel
    private var selectedNovel: Novel? = null
    private var isPremium: Boolean = false
    private var currentSettings: ReadingSettings? = null

    private enum class Method { DIAMONDS, POINTS }

    /** Null until the user taps a card; then their choice sticks even when balances refresh. */
    private var chosenMethod: Method? = null
    private var points = 0
    private var diamonds = 0

    private val accent = Color.parseColor("#E91E63")
    private val accentLight = Color.parseColor("#FF4F8B")
    private val warning = Color.parseColor("#FF6B6B")

    // Theme-derived colours, set in applyTheme().
    private var textColor = Color.WHITE
    private var mutedText = Color.GRAY
    private var cardFill = Color.TRANSPARENT
    private var cardStroke = Color.GRAY
    private var isDark = true

    companion object {
        fun newInstance(settings: ReadingSettings? = null): UnlockChapterBottomSheetNew {
            val fragment = UnlockChapterBottomSheetNew()
            fragment.currentSettings = settings
            return fragment
        }
    }

    /** Transparent sheet container, so only our rounded background is drawn (no grey corners). */
    override fun getTheme(): Int = R.style.ThemeOverlay_NovelSerise_TransparentBottomSheet

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetUnlockChapterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isCancelable = false

        userViewModel = AppProvider.getViewModel(requireActivity().application, UserViewModel::class.java)

        if (currentSettings != null) applyTheme(currentSettings!!) else applyDefaultTheme()
        observeGlobalData()
        setupClickListeners()
    }

    private fun applyDefaultTheme() {
        val context = context ?: return
        try {
            val bgColor = ThemeUtils.getThemeColor(context, com.google.android.material.R.attr.colorSurface)
            val text = ThemeUtils.getThemeColor(context, com.google.android.material.R.attr.colorOnSurface)
            val dark = ColorUtils.calculateLuminance(bgColor) < 0.5
            applyTheme(ReadingSettings(
                themeBackgroundColor = if (dark) Color.parseColor("#0F1828") else bgColor,
                themeTextColor = text,
                themeType = if (dark) "DARK" else "LIGHT"
            ))
        } catch (e: Exception) {
            applyTheme(ReadingSettings())
        }
    }

    private fun observeGlobalData() {
        GlobalAppData.selectedNovel.observe(viewLifecycleOwner) { novel ->
            novel ?: return@observe
            selectedNovel = novel
            val novelId = novel.id ?: return@observe

            woChpterViewModel = AppProvider.getUniqueViewModel(requireActivity().application, WoChpterViewModel::class.java, novelId)
            isPremium = GlobalVariableManager.getEffectivePremiumStatus(novel)

            binding.textForUnlock.text = DisplayText.of(novel.title)
            val density = resources.displayMetrics.density
            ImageHelper.loadImage(
                requireContext(), novel.imageLink, binding.ivCover, allowHardware = true,
                targetWidthPx = (60 * density).toInt(), targetHeightPx = (90 * density).toInt()
            )
            if (isPremium) binding.adsCard.visibility = View.GONE

            observeChapterViewModel()
            render()
        }

        GlobalAppData.selectedChapter.observe(viewLifecycleOwner) { chapter ->
            val name = DisplayText.of(chapter?.chapterName)
            val number = chapter?.sqc?.toIntOrNull()?.takeIf { it > 0 }
            binding.titleUnlockChapter.text = name.ifBlank { if (number != null) "Chapter $number" else "Premium chapter" }
        }

        GlobalAppData.userDetailsLiveData.observe(viewLifecycleOwner) { userDetails ->
            userDetails ?: return@observe
            points = userDetails.userData?.points?.toIntOrNull() ?: 0
            diamonds = userDetails.userData?.diamond?.toIntOrNull() ?: 0
            render()
        }
    }

    private fun pointsPrice() = GlobalVariableManager.getChapterCoinPrice(selectedNovel)
    private fun diamondPrice() = GlobalVariableManager.getChapterDiamondPrice(selectedNovel)

    /** The method in use: the user's pick, else whichever they can afford (points first), else diamonds. */
    private fun activeMethod(): Method {
        chosenMethod?.let { return it }
        val pp = pointsPrice(); val dp = diamondPrice()
        return when {
            pp > 0 && points >= pp -> Method.POINTS
            dp > 0 -> Method.DIAMONDS
            else -> Method.POINTS
        }
    }

    /** Refreshes both payment cards, their selection state and the main button. */
    private fun render() {
        if (_binding == null) return
        val pp = pointsPrice(); val dp = diamondPrice()
        val method = activeMethod()

        binding.diamondsCard.visibility = if (dp > 0) View.VISIBLE else View.GONE
        binding.pointsCard.visibility = if (pp > 0) View.VISIBLE else View.GONE

        bindMethodCard(
            selected = method == Method.DIAMONDS, price = dp, balance = diamonds,
            card = binding.diamondsCard, balanceText = binding.diamondsCardValue,
            priceText = binding.tvDiamondPrice, radio = binding.radioDiamonds, iconBadge = binding.diamondsIconBadge
        )
        bindMethodCard(
            selected = method == Method.POINTS, price = pp, balance = points,
            card = binding.pointsCard, balanceText = binding.pointsCardValue,
            priceText = binding.tvPointsPrice, radio = binding.radioPoints, iconBadge = binding.pointsIconBadge
        )
        binding.tvDiamondPrice.text = dp.toString()
        binding.tvPointsPrice.text = pp.toString()

        val affordable = when (method) {
            Method.DIAMONDS -> dp > 0 && diamonds >= dp
            Method.POINTS -> pp > 0 && points >= pp
        }
        binding.buttonUnlockChapter.text = when {
            affordable && method == Method.DIAMONDS -> "Unlock for $dp diamonds"
            affordable -> "Unlock for $pp points"
            method == Method.DIAMONDS -> "Top up & Unlock"
            else -> "Earn Points to Unlock"
        }
    }

    private fun bindMethodCard(
        selected: Boolean, price: Int, balance: Int,
        card: View, balanceText: android.widget.TextView, priceText: android.widget.TextView,
        radio: View, iconBadge: View
    ) {
        val density = resources.displayMetrics.density
        card.background = GradientDrawable().apply {
            cornerRadius = 16f * density
            setColor(if (selected) ColorUtils.setAlphaComponent(accent, if (isDark) 34 else 22) else cardFill)
            setStroke(((if (selected) 1.5f else 1f) * density).toInt().coerceAtLeast(1), if (selected) accent else cardStroke)
        }
        iconBadge.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ColorUtils.setAlphaComponent(if (selected) accent else textColor, if (selected) 60 else 22))
        }
        radio.background = radioDrawable(selected)
        priceText.setTextColor(textColor)

        val shortfall = price - balance
        if (shortfall > 0) {
            val text = "Balance $balance  ·  Need $shortfall more"
            val span = android.text.SpannableString(text)
            val start = text.indexOf("Need")
            span.setSpan(android.text.style.ForegroundColorSpan(warning), start, text.length, 0)
            balanceText.text = span
        } else {
            balanceText.text = "Balance $balance"
        }
        balanceText.setTextColor(mutedText)
    }

    private fun radioDrawable(selected: Boolean): Drawable {
        val density = resources.displayMetrics.density
        val ring = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setStroke((2 * density).toInt(), if (selected) accent else ColorUtils.setAlphaComponent(textColor, 90))
        }
        if (!selected) return ring
        val dot = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(accent) }
        return LayerDrawable(arrayOf(ring, dot)).apply {
            val inset = (6 * density).toInt()
            setLayerInset(1, inset, inset, inset, inset)
        }
    }

    /** Colours everything from the theme (app or reader), with the pink accent for key parts. */
    fun applyTheme(settings: ReadingSettings) {
        currentSettings = settings
        if (_binding == null) return

        val bgColor = settings.themeBackgroundColor
        textColor = settings.themeTextColor
        isDark = settings.themeType == "DARK"
        mutedText = ColorUtils.setAlphaComponent(textColor, 170)
        cardFill = ColorUtils.setAlphaComponent(textColor, if (isDark) 14 else 10)
        cardStroke = ColorUtils.setAlphaComponent(textColor, if (isDark) 34 else 28)
        val density = resources.displayMetrics.density

        // Sheet: rounded top with a soft pink glow fading into the base colour.
        val r = 24f * density
        val radii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        val base = GradientDrawable().apply { setColor(bgColor); cornerRadii = radii }
        val glow = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(ColorUtils.setAlphaComponent(accent, if (isDark) 46 else 26), Color.TRANSPARENT)
        ).apply { cornerRadii = radii }
        binding.root.background = LayerDrawable(arrayOf(base, glow)).apply {
            setLayerHeight(1, (150 * density).toInt())
            setLayerGravity(1, android.view.Gravity.TOP)
        }

        binding.dragHandle.background = GradientDrawable().apply {
            cornerRadius = 100f; setColor(ColorUtils.setAlphaComponent(textColor, 70))
        }

        // Header
        binding.lockBadge.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(accent)
            setStroke((2 * density).toInt(), bgColor)
        }
        binding.ivLockStatus.imageTintList = ColorStateList.valueOf(Color.WHITE)
        binding.tvLockedLabel.setTextColor(accentLight)
        binding.titleUnlockChapter.setTextColor(textColor)
        binding.textForUnlock.setTextColor(mutedText)
        binding.tvChooseLabel.setTextColor(mutedText)

        // Method cards
        binding.diamondsCardTitle.setTextColor(textColor)
        binding.pointsCardTitle.setTextColor(textColor)
        binding.diamondsCardIcon.imageTintList = null
        binding.pointsCardIcon.imageTintList = ColorStateList.valueOf(textColor)
        binding.tvDiamondPrice.setCompoundDrawablesRelative(null, null, smallIcon(R.drawable.ic_diamonds_income, null), null)
        binding.tvPointsPrice.setCompoundDrawablesRelative(null, null, smallIcon(R.drawable.ic_point_daily, textColor), null)

        // Ad card
        binding.adsCard.background = GradientDrawable().apply {
            cornerRadius = 16f * density; setColor(cardFill); setStroke(density.toInt().coerceAtLeast(1), cardStroke)
        }
        binding.adsIconBadge.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL; setColor(Color.parseColor("#2BB673"))
        }
        binding.adsCardIcon.imageTintList = ColorStateList.valueOf(Color.WHITE)
        binding.adsCardTitle.setTextColor(textColor)
        binding.adsCardValue.setTextColor(mutedText)
        binding.tvFreeTag.background = GradientDrawable().apply {
            cornerRadius = 6f * density; setColor(ColorUtils.setAlphaComponent(Color.parseColor("#2BB673"), 50))
        }
        binding.tvFreeTag.setTextColor(Color.parseColor("#4ADE80"))
        binding.adsChevron.imageTintList = ColorStateList.valueOf(mutedText)

        // Primary button: pink gradient, slightly rounded rectangle.
        binding.buttonUnlockChapter.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(accentLight, accent)
        ).apply { cornerRadius = 14f * density }
        binding.buttonUnlockChapter.setTextColor(Color.WHITE)

        // Secondary links
        binding.buttonEarnCoins.setTextColor(accentLight)
        binding.buttonBuyDiamonds.setTextColor(accentLight)
        binding.linkDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL; setColor(ColorUtils.setAlphaComponent(textColor, 90))
        }

        for (v in listOf(binding.buttonUnlockChapter, binding.diamondsCard, binding.pointsCard, binding.adsCard,
            binding.buttonEarnCoins, binding.buttonBuyDiamonds)) v.foreground = ripple()

        render()
    }

    private fun smallIcon(res: Int, tint: Int?): Drawable? {
        val size = (16 * resources.displayMetrics.density).toInt()
        val icon = ContextCompat.getDrawable(requireContext(), res)?.mutate() ?: return null
        tint?.let { icon.setTint(it) }
        icon.setBounds(0, 0, size, size)
        return icon
    }

    private fun ripple() = android.util.TypedValue().let {
        requireContext().theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        ContextCompat.getDrawable(requireContext(), it.resourceId)
    }

    private fun setupClickListeners() {
        binding.diamondsCard.setOnClickListener { chosenMethod = Method.DIAMONDS; render() }
        binding.pointsCard.setOnClickListener { chosenMethod = Method.POINTS; render() }
        binding.buttonBuyDiamonds.setOnClickListener {
            startActivity(Intent(requireContext(), ShopActivity::class.java))
        }
        binding.buttonEarnCoins.setOnClickListener {
            startActivity(Intent(requireContext(), EarnCoinsActivity::class.java))
        }
        binding.adsCard.setOnClickListener {
            if (!isPremium) {
                startActivity(Intent(requireContext(), EarnCoinsActivity::class.java))
            } else {
                ToastHelper.makeText(requireContext(), getString(R.string.premium_text), 0).show()
            }
        }
        binding.buttonUnlockChapter.setOnClickListener { handleUnlock() }
    }

    private fun observeChapterViewModel() {
        if (!::woChpterViewModel.isInitialized) return
        woChpterViewModel.purchaseStatus.observe(viewLifecycleOwner) { status ->
            status?.let {
                handlePurchaseStatus(it)
                woChpterViewModel.clearPurchaseStatus()
            }
        }
    }

    private fun handlePurchaseStatus(status: WoChpterViewModel.PurchaseStatus) {
        when (status) {
            WoChpterViewModel.PurchaseStatus.LOADING -> showLoadingState(true)
            WoChpterViewModel.PurchaseStatus.SUCCESS -> {
                ToastHelper.makeText(requireContext(), "Chapter Unlocked!", 0).show()
                dismiss()
            }
            WoChpterViewModel.PurchaseStatus.ERROR -> {
                ToastHelper.makeText(requireContext(), getString(R.string.not_enough_balance), 0).show()
                showLoadingState(false)
            }
        }
    }

    private fun showLoadingState(isLoading: Boolean) {
        binding.buttonUnlockChapter.isEnabled = !isLoading
        binding.buttonUnlockChapter.setTextColor(if (isLoading) Color.TRANSPARENT else Color.WHITE)
        binding.progressUnlockChapter.visibility = if (isLoading) View.VISIBLE else View.GONE
        for (v in listOf(binding.adsCard, binding.pointsCard, binding.diamondsCard, binding.buttonEarnCoins, binding.buttonBuyDiamonds)) {
            v.isEnabled = !isLoading
        }
        binding.adsCard.alpha = if (isLoading) 0.5f else 1f
    }

    /** Unlocks with the chosen method, or sends the user to top up that currency. */
    private fun handleUnlock() {
        val pp = pointsPrice(); val dp = diamondPrice()
        when (activeMethod()) {
            Method.DIAMONDS ->
                if (dp > 0 && diamonds >= dp) { if (::woChpterViewModel.isInitialized) woChpterViewModel.buyChapterWithDiamondsv2(dp) }
                else startActivity(Intent(requireContext(), ShopActivity::class.java))
            Method.POINTS ->
                if (pp > 0 && points >= pp) { if (::woChpterViewModel.isInitialized) woChpterViewModel.buyChapterWithCoins(pp) }
                else startActivity(Intent(requireContext(), EarnCoinsActivity::class.java))
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
        }
        (view?.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)

        dialog?.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                activity?.finish()
                true
            } else false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
