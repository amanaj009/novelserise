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

/**
 * Time ruler for the audio studio timeline: a tick every second, a taller one every 5 s and a
 * "m:ss" label every [labelEverySeconds]. Only the visible part of the ruler is drawn.
 */
class TimelineRulerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density

    /** Timeline scale in dp per second (same value the activity uses to lay out tracks). */
    var dpPerSecond: Float = 50f
        set(value) { field = value; invalidate() }

    private val labelEverySeconds = 5

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4DFFFFFF")
        strokeWidth = density
    }
    private val majorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#99FFFFFF")
        strokeWidth = density
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#99FFFFFF")
        textSize = 10 * density
    }

    fun setColors(minor: Int, major: Int, label: Int) {
        tickPaint.color = minor
        majorTickPaint.color = major
        labelPaint.color = label
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val pxPerSecond = dpPerSecond * density
        if (pxPerSecond <= 0f) return
        val clip = canvas.clipBounds
        val first = (clip.left / pxPerSecond).toInt().coerceAtLeast(0)
        val last = (clip.right / pxPerSecond).toInt() + 1
        val bottom = height.toFloat()
        for (second in first..last) {
            val x = second * pxPerSecond
            if (second % labelEverySeconds == 0) {
                canvas.drawLine(x, bottom - 10 * density, x, bottom, majorTickPaint)
                canvas.drawText(label(second), x + 3 * density, bottom - 12 * density, labelPaint)
            } else {
                canvas.drawLine(x, bottom - 5 * density, x, bottom, tickPaint)
            }
        }
    }

    private fun label(totalSeconds: Int) = "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
