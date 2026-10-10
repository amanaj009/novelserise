/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.util

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import com.writer_hub.novelserise.R
import com.writer_hub.novelserise.databinding.DialogRateAppBinding

object RateAppManager {

    private const val PREFS_NAME = "RateAppPrefs"
    private const val KEY_LAUNCH_COUNT = "launch_count"
    private const val KEY_NEVER_SHOW_AGAIN = "never_show_again"
    private const val LAUNCHES_UNTIL_PROMPT = 5 // Show after 5 launches

    fun incrementLaunchCount(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val launchCount = prefs.getInt(KEY_LAUNCH_COUNT, 0) + 1
        prefs.edit().putInt(KEY_LAUNCH_COUNT, launchCount).apply()
    }

    private fun shouldShowPrompt(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_NEVER_SHOW_AGAIN, false)) {
            return false
        }
        val launchCount = prefs.getInt(KEY_LAUNCH_COUNT, 0)
        return launchCount >= LAUNCHES_UNTIL_PROMPT
    }

    private fun setNeverShowAgain(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_NEVER_SHOW_AGAIN, true).apply()
    }
    
    private fun resetLaunchCount(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_LAUNCH_COUNT, 0).apply()
    }

    private val STAR_ON = Color.parseColor("#FFC53D")
    private val STAR_OFF = Color.parseColor("#3A3D45")
    private val LABELS = listOf("Tap a star to rate", "Hated it 😞", "Didn't like it 😕", "It's okay 🙂", "Liked it! 😊", "Loved it! 🤩")

    fun showRateDialog(context: Context) {
        val binding = DialogRateAppBinding.inflate(LayoutInflater.from(context))
        val dialog = Dialog(context).apply {
            setContentView(binding.root)
            setCancelable(true)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window?.setLayout(
                (context.resources.displayMetrics.widthPixels * 0.88f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val appName = context.applicationInfo.loadLabel(context.packageManager)
        binding.tvRateTitle.text = "Enjoying $appName?"

        // Custom star row: tap sets the rating, each lit star pops in turn.
        val density = context.resources.displayMetrics.density
        var rating = 0
        val stars = (1..5).map { value ->
            ImageView(context).apply {
                setImageResource(R.drawable.ic_rate_star)
                setColorFilter(STAR_OFF)
                contentDescription = "$value star"
                val pad = (4 * density).toInt()
                setPadding(pad, pad, pad, pad)
            }.also { star ->
                binding.starRow.addView(star, LinearLayout.LayoutParams((48 * density).toInt(), (48 * density).toInt()))
            }
        }

        fun render(animate: Boolean) {
            stars.forEachIndexed { i, star ->
                val lit = i < rating
                star.setColorFilter(if (lit) STAR_ON else STAR_OFF)
                if (animate && lit) {
                    star.animate().cancel()
                    star.scaleX = 0.6f
                    star.scaleY = 0.6f
                    star.animate().scaleX(1f).scaleY(1f).setStartDelay(i * 40L).setDuration(260)
                        .setInterpolator(OvershootInterpolator(3f)).start()
                }
            }
            binding.tvRatingLabel.text = LABELS[rating]
            binding.tvRatingLabel.setTextColor(if (rating > 0) STAR_ON else binding.btnNoThanks.currentTextColor)
            binding.btnRateNow.alpha = if (rating > 0) 1f else 0.45f
            binding.btnRateNow.text = if (rating > 0) "Rate on Play Store" else "Rate Now"
        }

        stars.forEachIndexed { i, star ->
            star.setOnClickListener {
                rating = i + 1
                render(animate = true)
            }
        }
        render(animate = false)

        binding.btnRateNow.setOnClickListener {
            if (rating == 0) {
                // Nudge the stars instead of disabling the button.
                binding.starRow.animate().cancel()
                binding.starRow.translationX = 0f
                binding.starRow.animate().translationX(12 * density).setDuration(60).withEndAction {
                    binding.starRow.animate().translationX(0f).setInterpolator(OvershootInterpolator(4f)).setDuration(220).start()
                }.start()
                return@setOnClickListener
            }
            setNeverShowAgain(context)
            dialog.dismiss()
            Toast.makeText(context, "Thank you for your feedback!", Toast.LENGTH_SHORT).show()

            // Open Play Store
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
                context.startActivity(intent)
            } catch (e: Exception) {
                // Fallback to browser if Play Store is not installed
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}"))
                context.startActivity(intent)
            }
        }

        binding.btnLater.setOnClickListener {
            resetLaunchCount(context) // Ask again after another 5 launches
            dialog.dismiss()
        }

        binding.btnNoThanks.setOnClickListener {
            setNeverShowAgain(context)
            dialog.dismiss()
        }

        dialog.show()
        // Gentle entrance
        binding.root.alpha = 0f
        binding.root.scaleX = 0.92f
        binding.root.scaleY = 0.92f
        binding.root.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220)
            .setInterpolator(OvershootInterpolator(1.2f)).start()
    }

    fun showRateDialogIfQualified(context: Context) {
        if (shouldShowPrompt(context)) {
            showRateDialog(context)
        }
    }
}
