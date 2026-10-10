/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.ui.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.sin

/**
 * Decorative waveform for a timeline clip: rounded bars whose heights come from a seed, so a clip
 * always shows the same shape. It is not decoded from the audio.
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val barWidth = 3 * density
    private val gap = 2 * density

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80FFFFFF")
        strokeCap = Paint.Cap.ROUND
        strokeWidth = barWidth
    }

    var seed: Int = 0
        set(value) { field = value; invalidate() }

    fun setBarColor(color: Int) {
        paint.color = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val mid = height / 2f
        val maxHalf = height / 2f - barWidth
        if (maxHalf <= 0f) return
        val clip = canvas.clipBounds
        val step = barWidth + gap
        var i = (clip.left / step).toInt()
        var x = i * step + barWidth / 2
        while (x < width && x <= clip.right + step) {
            // Two mixed sines give a speech-like envelope; stable for a given seed.
            val t = i + seed * 7
            val amp = 0.25f + 0.75f * abs(sin(t * 0.37f) * 0.6f + sin(t * 0.13f + seed) * 0.4f)
            val half = (maxHalf * amp).coerceAtLeast(density)
            canvas.drawLine(x, mid - half, x, mid + half, paint)
            i++
            x += step
        }
    }
}
