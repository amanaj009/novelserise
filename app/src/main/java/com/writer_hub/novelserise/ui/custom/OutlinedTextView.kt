/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.ui.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.graphics.ColorUtils

/**
 * Text drawn as an outline (stroke) over a faint fill, like the rank numbers on "Top 10" rows.
 * The outline uses the text color; [fillAlpha] sets how much of it shows inside the glyphs.
 * Meant for short, single-line text with wrap_content size.
 */
class OutlinedTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    var strokeWidthPx: Float = 2.5f * resources.displayMetrics.density
    var fillAlpha: Int = 50

    init {
        // Stroked glyphs are costly to raster; cache them in a GPU layer so scrolling only
        // composites the texture. The layer is redrawn only when the text changes.
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        val textLayout = layout ?: return super.onDraw(canvas)
        val outlineColor = currentTextColor
        val textPaint = paint

        canvas.save()
        canvas.translate(compoundPaddingLeft.toFloat(), extendedPaddingTop.toFloat())

        textPaint.style = Paint.Style.FILL
        textPaint.color = ColorUtils.setAlphaComponent(outlineColor, fillAlpha)
        textLayout.draw(canvas)

        textPaint.style = Paint.Style.STROKE
        textPaint.strokeWidth = strokeWidthPx
        textPaint.strokeJoin = Paint.Join.ROUND
        textPaint.color = outlineColor
        textLayout.draw(canvas)

        textPaint.style = Paint.Style.FILL
        canvas.restore()
    }
}
