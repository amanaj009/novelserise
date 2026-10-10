/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.ui.custom

import android.content.Context
import android.graphics.Outline
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import androidx.appcompat.widget.AppCompatImageView
import com.writer_hub.novelserise.R

/**
 * Rounded-corner image for covers and banners in scrolling lists. Rounds with the view outline,
 * which the GPU clips for free, instead of the offscreen layer + mask that shape-drawing image
 * views use. With [heightRatio] set it sizes itself (height = width x ratio), so a banner row can
 * be this single view instead of a card + layout + image.
 */
class CoverImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    var cornerRadiusPx: Float = 8f * resources.displayMetrics.density
        set(value) {
            field = value
            invalidateOutline()
        }

    var heightRatio: Float = 0f
        set(value) {
            field = value
            requestLayout()
        }

    init {
        context.obtainStyledAttributes(attrs, R.styleable.CoverImageView).apply {
            cornerRadiusPx = getDimension(R.styleable.CoverImageView_coverCornerRadius, cornerRadiusPx)
            heightRatio = getFloat(R.styleable.CoverImageView_coverHeightRatio, 0f)
            recycle()
        }
        scaleType = ScaleType.CENTER_CROP
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
            }
        }
        clipToOutline = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (heightRatio <= 0f || MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, (width * heightRatio).toInt())
    }
}
