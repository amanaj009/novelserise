/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.ui.custom

import android.app.Application
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.lifecycle.Observer
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.engine.core.models.GraphDataPoint
import com.engine.core.models.GraphDataSet
import com.engine.core.models.ThemeColors
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.ThemeViewModel
import kotlin.math.abs

class SimpleGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var dataSet: GraphDataSet? = null
    private var minX = 0f
    private var maxX = 0f
    private var minY = 0f
    private var maxY = 0f

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 3f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
    }
    private val pointHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = com.engine.core.manager.ThemeManager.getThemeColor(context, "colorStaticWhite")
        style = Paint.Style.FILL
    }
    private val valueTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 40f
        textAlign = Paint.Align.CENTER
    }

    private val gridLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = com.engine.core.manager.ThemeManager.getThemeColor(context, "colorOutlineVariant")
    }

    private var lineColor =
        com.engine.core.manager.ThemeManager.getThemeColor(context, "colorPrimary")
    private val path = Path()

    private var touchedPointX: Float? = null
    private var touchedPointY: Float? = null
    private var touchedPointValueX: Float? = null
    private var touchedPointValueY: Float? = null
    private var touchedPointColor: Int =
        com.engine.core.manager.ThemeManager.getThemeColor(context, "colorStaticWhite")
    private var touchedPointLabel: String? = null

    private val themeViewModel: ThemeViewModel = AppProvider.getViewModel(
        context.applicationContext as Application,
        ThemeViewModel::class.java
    )

    private val themeObserver = Observer<ThemeColors?> { theme ->
        applyTheme(theme)
    }

    companion object {
        private const val PADDING = 80f
        private const val LABEL_OFFSET = 40f
        private const val NUM_Y_LABELS = 5
        private const val TOUCH_TOLERANCE_DP = 24
    }

    private val touchTolerance: Float

    init {
        touchTolerance = TOUCH_TOLERANCE_DP * resources.displayMetrics.density
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        findViewTreeLifecycleOwner()?.let { lifecycleOwner ->
            themeViewModel.selectedTheme.observe(lifecycleOwner, themeObserver)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        themeViewModel.selectedTheme.removeObserver(themeObserver)
    }

    private fun applyTheme(theme: ThemeColors?) {
        val themeManager = com.engine.core.manager.ThemeManager
        if (theme != null) {
            axisPaint.color = Color.parseColor(theme.textColor)
            textPaint.color = Color.parseColor(theme.textColor)
            valueTextPaint.color = Color.parseColor(theme.textColor)
            gridLinePaint.color = Color.parseColor(theme.textColor)
            lineColor = Color.parseColor(theme.primaryColor)
            pointHighlightPaint.color = Color.parseColor(theme.primaryColor)
        } else {
            axisPaint.color = themeManager.getThemeColor(context, "colorOnSurface")
            textPaint.color = themeManager.getThemeColor(context, "colorOnSurface")
            valueTextPaint.color = themeManager.getThemeColor(context, "colorOnSurface")
            gridLinePaint.color = themeManager.getThemeColor(context, "colorOutlineVariant")
            lineColor = themeManager.getThemeColor(context, "colorPrimary")
            pointHighlightPaint.color = themeManager.getThemeColor(context, "colorPrimary")
        }
        invalidate()
    }

    fun setData(newDataSet: GraphDataSet?) {
        this.dataSet = newDataSet
        if (dataSet?.points?.isNotEmpty() == true) {
            calculateBounds()
        }
        touchedPointX = null
        touchedPointY = null
        touchedPointValueX = null
        touchedPointValueY = null
        touchedPointLabel = null
        invalidate()
    }

    private fun calculateBounds() {
        val points = dataSet?.points ?: emptyList()
        minX = points.minOfOrNull { it.x } ?: 0f
        maxX = points.maxOfOrNull { it.x } ?: 0f
        minY = 0f
        maxY = points.maxOfOrNull { it.y } ?: 0f

        if (maxY == minY) maxY += 10f
        if (maxX == minX) maxX += 10f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentDataSet = dataSet
        if (currentDataSet == null || currentDataSet.points.isEmpty()) return

        val graphLeft = PADDING
        val graphTop = PADDING
        val graphRight = width - PADDING
        val graphBottom = height - PADDING
        val graphWidth = graphRight - graphLeft
        val graphHeight = graphBottom - graphTop

        canvas.drawLine(graphLeft, graphBottom, graphRight, graphBottom, axisPaint)
        canvas.drawLine(graphLeft, graphTop, graphLeft, graphBottom, axisPaint)

        val xRange = maxX - minX
        val yRange = maxY - minY

        textPaint.textAlign = Paint.Align.RIGHT
        val yStep = yRange / (NUM_Y_LABELS - 1)
        val yPixelStep = graphHeight / (NUM_Y_LABELS - 1)

        for (i in 0 until NUM_Y_LABELS) {
            val labelValue = minY + (yStep * i)
            val yPos = graphBottom - (yPixelStep * i)
            canvas.drawLine(graphLeft, yPos, graphRight, yPos, gridLinePaint)
            canvas.drawText(labelValue.toInt().toString(), graphLeft - (LABEL_OFFSET / 2), yPos + (textPaint.textSize / 3), textPaint)
        }

        linePaint.color = lineColor
        path.reset()
        currentDataSet.points.forEachIndexed { pointIndex, point ->
            val xPos = graphLeft + ((point.x - minX) / xRange) * graphWidth
            val yPos = graphBottom - ((point.y - minY) / yRange) * graphHeight

            if (pointIndex == 0) {
                path.moveTo(xPos, yPos)
            } else {
                path.lineTo(xPos, yPos)
            }
        }
        canvas.drawPath(path, linePaint)

        textPaint.textAlign = Paint.Align.CENTER
        val currentPoints = currentDataSet.points
        if (currentPoints.isNotEmpty()) {
            if (currentPoints.size > 7) {
                canvas.drawText(minX.toInt().toString(), graphLeft, graphBottom + LABEL_OFFSET, textPaint)
                canvas.drawText(maxX.toInt().toString(), graphRight, graphBottom + LABEL_OFFSET, textPaint)
            } else {
                currentPoints.forEach { point ->
                    val xPos = graphLeft + ((point.x - minX) / xRange) * graphWidth
                    val xLabel = if (dataSet?.period?.lowercase() == "chapter-wise") {
                        "Ch ${point.x.toInt()}"
                    } else {
                        point.x.toInt().toString()
                    }
                    canvas.drawText(xLabel, xPos, graphBottom + LABEL_OFFSET, textPaint)
                }
            }
        } else {
            canvas.drawText(minX.toInt().toString(), graphLeft, graphBottom + LABEL_OFFSET, textPaint)
            canvas.drawText(maxX.toInt().toString(), graphRight, graphBottom + LABEL_OFFSET, textPaint)
        }

        touchedPointX?.let { tx ->
            touchedPointY?.let { ty ->
                touchedPointValueX?.let { valX ->
                    touchedPointValueY?.let { valY ->
                        pointHighlightPaint.color = touchedPointColor
                        canvas.drawCircle(tx, ty, 15f, pointHighlightPaint)

                        val dateText = "Date: ${valX.toInt()}"
                        val valueLabel = touchedPointLabel ?: currentDataSet.label
                        val valueText = "$valueLabel: ${valY.toInt()}"

                        val lineHeight = valueTextPaint.descent() - valueTextPaint.ascent()
                        val textBaseY = if (ty - (2 * lineHeight) < graphTop) ty + (lineHeight * 2) else ty - (LABEL_OFFSET / 2) - 10f

                        canvas.drawText(dateText, tx, textBaseY - lineHeight / 2, valueTextPaint)
                        canvas.drawText(valueText, tx, textBaseY + lineHeight / 2, valueTextPaint)
                    }
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_DOWN) {
            val touchX = event.x
            val touchY = event.y

            val currentDataSet = dataSet
            if (currentDataSet == null || currentDataSet.points.isEmpty()) return false

            val graphLeft = PADDING
            val graphTop = PADDING
            val graphRight = width - PADDING
            val graphBottom = height - PADDING
            val graphWidth = graphRight - graphLeft
            val graphHeight = graphBottom - graphTop

            val xRange = maxX - minX
            val yRange = maxY - minY

            var closestPoint: GraphDataPoint? = null
            var closestDistance = Float.MAX_VALUE
            var closestPointScreenX: Float? = null
            var closestPointScreenY: Float? = null

            currentDataSet.points.forEach { point ->
                val pointScreenX = graphLeft + ((point.x - minX) / xRange) * graphWidth
                val pointScreenY = graphBottom - ((point.y - minY) / yRange) * graphHeight

                val distance = (abs(pointScreenX - touchX) + abs(pointScreenY - touchY))
                if (distance < closestDistance && distance <= touchTolerance) {
                    closestDistance = distance
                    closestPoint = point
                    closestPointScreenX = pointScreenX
                    closestPointScreenY = pointScreenY
                }
            }

            if (closestPoint != null) {
                touchedPointX = closestPointScreenX
                touchedPointY = closestPointScreenY
                touchedPointValueX = closestPoint?.x
                touchedPointValueY = closestPoint?.y
                touchedPointColor = lineColor
                touchedPointLabel = currentDataSet.label
            } else {
                touchedPointX = null
                touchedPointY = null
                touchedPointValueX = null
                touchedPointValueY = null
                touchedPointLabel = null
            }
            invalidate()
            return true
        }
        return super.onTouchEvent(event)
    }
}
