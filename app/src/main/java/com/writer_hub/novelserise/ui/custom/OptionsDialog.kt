/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.ui.custom

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.writer_hub.novelserise.R
import com.writer_hub.novelserise.databinding.DialogOptionsBinding

/**
 * App-styled choice sheets used instead of the system AlertDialog:
 * [show] lists option cards (icon, title, subtitle, radio); [showGrid] picks a number from a grid.
 */
object OptionsDialog {

    data class Option(val label: String, val detail: String? = null, @DrawableRes val icon: Int? = null)

    fun show(
        context: Context,
        title: String,
        options: List<Option>,
        selectedIndex: Int,
        subtitle: String? = null,
        /** false for action menus: a chevron replaces the radio. */
        showRadio: Boolean = true,
        onSelected: (Int) -> Unit
    ): BottomSheetDialog {
        val (sheet, binding) = createSheet(context, title, subtitle)
        val onSurface = themeColor(context, com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVariant = themeColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant)
        val primary = themeColor(context, androidx.appcompat.R.attr.colorPrimary)

        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(if (selected) R.drawable.bg_option_card_selected else R.drawable.bg_option_card)
                foreground = rippleDrawable(context)
                setPadding(dp(context, 14f), dp(context, 14f), dp(context, 16f), dp(context, 14f))
                setOnClickListener { sheet.dismiss(); if (!selected) onSelected(index) }
            }

            option.icon?.let { icon ->
                card.addView(FrameLayout(context).apply {
                    setBackgroundResource(if (selected) R.drawable.bg_option_icon_selected else R.drawable.bg_option_icon)
                    addView(ImageView(context).apply {
                        setImageResource(icon)
                        setColorFilter(if (selected) Color.WHITE else onSurface)
                    }, FrameLayout.LayoutParams(dp(context, 22f), dp(context, 22f), Gravity.CENTER))
                }, LinearLayout.LayoutParams(dp(context, 44f), dp(context, 44f)).apply { marginEnd = dp(context, 14f) })
            }

            card.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = option.label
                    textSize = 16f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(if (selected) primary else onSurface)
                })
                option.detail?.let {
                    addView(TextView(context).apply {
                        text = it
                        textSize = 13f
                        setTextColor(onSurfaceVariant)
                    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        .apply { topMargin = dp(context, 2f) })
                }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            if (showRadio) {
                card.addView(View(context).apply {
                    setBackgroundResource(if (selected) R.drawable.bg_radio_on else R.drawable.bg_radio_off)
                }, LinearLayout.LayoutParams(dp(context, 22f), dp(context, 22f)))
            } else {
                card.addView(ImageView(context).apply {
                    setImageResource(R.drawable.ic_chevron_right)
                    setColorFilter(onSurfaceVariant)
                }, LinearLayout.LayoutParams(dp(context, 22f), dp(context, 22f)))
            }

            binding.optionsContainer.addView(card, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(context, 10f) })
        }

        sheet.show()
        return sheet
    }

    /** Numbered grid (e.g. pages 1..[count]); [selected] is 1-based. */
    fun showGrid(
        context: Context,
        title: String,
        count: Int,
        selected: Int,
        subtitle: String? = null,
        columns: Int = 5,
        onSelected: (Int) -> Unit
    ): BottomSheetDialog {
        val (sheet, binding) = createSheet(context, title, subtitle)
        val onSurface = themeColor(context, com.google.android.material.R.attr.colorOnSurface)
        val gap = dp(context, 10f)

        (1..count).chunked(columns).forEach { rowNumbers ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (col in 0 until columns) {
                val number = rowNumbers.getOrNull(col)
                val cellParams = LinearLayout.LayoutParams(0, dp(context, 52f), 1f).apply {
                    if (col > 0) marginStart = gap
                }
                if (number == null) {
                    row.addView(View(context), cellParams)
                    continue
                }
                val isSelected = number == selected
                row.addView(TextView(context).apply {
                    text = number.toString()
                    gravity = Gravity.CENTER
                    textSize = 16f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(if (isSelected) Color.WHITE else onSurface)
                    setBackgroundResource(if (isSelected) R.drawable.bg_page_cell_selected else R.drawable.bg_page_cell)
                    foreground = rippleDrawable(context)
                    setOnClickListener { sheet.dismiss(); if (!isSelected) onSelected(number) }
                }, cellParams)
            }
            binding.optionsContainer.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = gap })
        }

        // Many pages scroll inside the sheet; start with the current page in view.
        val maxHeight = (context.resources.displayMetrics.heightPixels * 0.5f).toInt()
        binding.scrollOptions.post {
            if (binding.scrollOptions.height > maxHeight) {
                binding.scrollOptions.layoutParams = binding.scrollOptions.layoutParams.apply { height = maxHeight }
                val rowIndex = (selected - 1) / columns
                binding.optionsContainer.getChildAt(rowIndex)?.let {
                    binding.scrollOptions.scrollTo(0, (it.top - maxHeight / 3).coerceAtLeast(0))
                }
            }
        }

        sheet.show()
        return sheet
    }

    private fun createSheet(context: Context, title: String, subtitle: String?): Pair<BottomSheetDialog, DialogOptionsBinding> {
        val sheet = BottomSheetDialog(context, R.style.ThemeOverlay_NovelSerise_TransparentBottomSheet)
        val binding = DialogOptionsBinding.inflate(LayoutInflater.from(context))
        sheet.setContentView(binding.root)
        // Our own rounded background instead of the default white sheet; open fully expanded.
        (binding.root.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.behavior.skipCollapsed = true

        binding.tvDialogTitle.text = title
        binding.tvDialogSubtitle.text = subtitle.orEmpty()
        binding.tvDialogSubtitle.visibility = if (subtitle.isNullOrBlank()) View.GONE else View.VISIBLE
        return sheet to binding
    }

    private fun rippleDrawable(context: Context) = TypedValue().let {
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        androidx.core.content.ContextCompat.getDrawable(context, it.resourceId)
    }

    private fun dp(context: Context, value: Float) = (value * context.resources.displayMetrics.density).toInt()

    private fun themeColor(context: Context, attr: Int): Int {
        val tv = TypedValue()
        context.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }
}
