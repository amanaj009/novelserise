/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.ui.custom

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Turns a downward drag anywhere on screen into a collapse progress (0 = fully open, 1 = collapsed),
 * like the Pocket FM player. It only dims the area behind; [onProgressChanged] decides how the
 * content moves (slide away, or morph into a mini player). The host window should be translucent.
 */
class SwipeDismissLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    /** Finger travel (px) that maps to a fully collapsed sheet. */
    var collapseDistance: () -> Float = { height.toFloat() }

    var onProgressChanged: ((progress: Float) -> Unit)? = null

    /** Called once the collapse animation has finished. */
    var onDismissed: (() -> Unit)? = null

    var progress = 0f
        private set

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    /** Release speed (px/s) that counts as a deliberate move up or down. */
    private val directionVelocity = 150 * resources.displayMetrics.density
    // Material "emphasized decelerate": quick start, long soft landing.
    private val settleInterpolator = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    private val maxDimAlpha = 0.6f

    private var downX = 0f
    private var downY = 0f
    private var dragStartY = 0f
    private var isDragging = false
    private var isDismissing = false
    private var velocityTracker: VelocityTracker? = null
    private var animator: ValueAnimator? = null

    /** Shows the collapsed state immediately; call [animateIn] once laid out. */
    fun showCollapsed() = setProgress(1f)

    fun animateIn() = animateTo(0f, fullDuration = 450L)

    fun dismiss() {
        if (isDismissing) return
        isDismissing = true
        animateTo(1f, fullDuration = 400L) { onDismissed?.invoke() }
    }

    /** Re-applies the current progress, e.g. after the content was re-laid out. */
    fun refresh() = setProgress(progress)

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (isDismissing) return true
        trackVelocity(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y; isDragging = false
                animator?.cancel()
            }
            MotionEvent.ACTION_MOVE -> if (shouldStartDrag(ev)) startDrag(ev)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> resetTracking()
        }
        return isDragging
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (isDismissing) return true
        trackVelocity(ev)
        when (ev.actionMasked) {
            // Claim touches that land on non-clickable parts of the sheet so they can start a drag.
            MotionEvent.ACTION_DOWN -> { downX = ev.x; downY = ev.y; return true }
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging && shouldStartDrag(ev)) startDrag(ev)
                if (isDragging) setProgress(((ev.y - dragStartY) / distance()).coerceIn(0f, 1f))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) settle(ev.actionMasked == MotionEvent.ACTION_UP)
                resetTracking()
            }
        }
        return true
    }

    private fun distance() = collapseDistance().coerceAtLeast(1f)

    private fun shouldStartDrag(ev: MotionEvent): Boolean {
        val dy = ev.y - downY
        val dx = ev.x - downX
        return dy > touchSlop && dy > abs(dx) * 1.2f
    }

    private fun startDrag(ev: MotionEvent) {
        isDragging = true
        dragStartY = ev.y - progress * distance()
        parent?.requestDisallowInterceptTouchEvent(true)
    }

    /**
     * Never leaves the sheet parked half-way: on release it keeps going the way the finger was
     * moving (down = collapse, up = reopen); a still finger decides by how far it was dragged.
     */
    private fun settle(released: Boolean) {
        velocityTracker?.computeCurrentVelocity(1000)
        val velocityY = velocityTracker?.yVelocity ?: 0f
        val collapse = when {
            !released -> false
            velocityY > directionVelocity -> true
            velocityY < -directionVelocity -> false
            else -> progress > 0.1f
        }
        if (collapse) dismiss() else animateTo(0f, fullDuration = 350L)
    }

    /** Animates to [target]; the duration scales with the distance left so every settle feels equally fast. */
    private fun animateTo(target: Float, fullDuration: Long, onEnd: (() -> Unit)? = null) {
        animator?.cancel()
        val remaining = abs(target - progress)
        animator = ValueAnimator.ofFloat(progress, target).apply {
            duration = (fullDuration * remaining).toLong().coerceIn(160L, fullDuration)
            interpolator = settleInterpolator
            addUpdateListener { setProgress(it.animatedValue as Float) }
            if (onEnd != null) addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = onEnd()
            })
            start()
        }
    }

    private fun setProgress(value: Float) {
        progress = value
        setBackgroundColor(Color.argb((maxDimAlpha * (1f - value) * 255).toInt(), 0, 0, 0))
        onProgressChanged?.invoke(value)
    }

    private fun trackVelocity(ev: MotionEvent) {
        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain()
        velocityTracker?.addMovement(ev)
    }

    private fun resetTracking() {
        isDragging = false
        velocityTracker?.recycle()
        velocityTracker = null
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
