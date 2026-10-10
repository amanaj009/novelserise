/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.ui.custom

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.DrawableRes
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.writer_hub.novelserise.R
import com.writer_hub.novelserise.databinding.DialogAppSheetBinding

/**
 * App-styled replacements for plain AlertDialogs: [info] shows a titled (optionally long, scrolling)
 * message with one button; [confirm] asks a yes/no question with an optional destructive action.
 */
object AppSheet {

    fun info(
        context: Context,
        title: String,
        message: CharSequence,
        @DrawableRes icon: Int? = null,
        buttonText: String = "Got it"
    ): BottomSheetDialog {
        val (sheet, binding) = create(context, title, message, icon)
        binding.btnSheetPrimary.text = buttonText
        binding.btnSheetPrimary.setOnClickListener { sheet.dismiss() }
        sheet.show()
        return sheet
    }

    fun confirm(
        context: Context,
        title: String,
        message: CharSequence,
        confirmText: String,
        cancelText: String = "Cancel",
        @DrawableRes icon: Int? = null,
        destructive: Boolean = false,
        onConfirm: () -> Unit
    ): BottomSheetDialog {
        val (sheet, binding) = create(context, title, message, icon)
        binding.btnSheetSecondary.visibility = View.VISIBLE
        binding.btnSheetSecondary.text = cancelText
        binding.btnSheetSecondary.setOnClickListener { sheet.dismiss() }
        binding.btnSheetPrimary.text = confirmText
        if (destructive) binding.btnSheetPrimary.setBackgroundResource(R.drawable.bg_destructive_button)
        binding.btnSheetPrimary.setOnClickListener {
            sheet.dismiss()
            onConfirm()
        }
        sheet.show()
        return sheet
    }

    private fun create(
        context: Context,
        title: String,
        message: CharSequence,
        @DrawableRes icon: Int?
    ): Pair<BottomSheetDialog, DialogAppSheetBinding> {
        val sheet = BottomSheetDialog(context, R.style.ThemeOverlay_NovelSerise_TransparentBottomSheet)
        val binding = DialogAppSheetBinding.inflate(LayoutInflater.from(context))
        sheet.setContentView(binding.root)
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.behavior.skipCollapsed = true

        binding.tvSheetTitle.text = title
        binding.tvSheetMessage.text = message
        if (icon != null) {
            binding.ivSheetIcon.setImageResource(icon)
            binding.ivSheetIcon.visibility = View.VISIBLE
        } else {
            binding.tvSheetTitle.setPadding(0, 0, 0, 0)
            (binding.tvSheetTitle.layoutParams as? android.widget.LinearLayout.LayoutParams)?.marginStart = 0
        }

        // Long text (policies) scrolls inside the sheet instead of pushing the buttons off screen.
        val maxHeight = (context.resources.displayMetrics.heightPixels * 0.55f).toInt()
        binding.scrollMessage.post {
            if (binding.scrollMessage.height > maxHeight) {
                binding.scrollMessage.layoutParams = binding.scrollMessage.layoutParams.apply { height = maxHeight }
            }
        }
        return sheet to binding
    }
}
