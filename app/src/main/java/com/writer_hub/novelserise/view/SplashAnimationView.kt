/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.writer_hub.novelserise.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Code-drawn version of the launch video: particles gather, the logo pulses out rings, a light
 * streak sweeps past, then "NOVEL", an underline and a typed "SERIES" appear.
 *
 * The first frame shows the logo exactly where the system splash icon is (same size, screen
 * centre), so the hand-off from the system splash has no jump and no black screen.
 */
class SplashAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Called once when the animation has played to the end. */
    var onFinished: (() -> Unit)? = null

    /** Called once the first frames are on screen, so heavier startup work can begin. */
    var onFramesShowing: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val logoSize = resources.getDimension(R.dimen.splash_logo_size)
    private val logo: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.splash_logo_mark)

    private val pink = Color.rgb(0xF0, 0x2E, 0x65)
    private val glowColor = Color.rgb(0x5A, 0x10, 0x28)

    private val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = pink
    }
    private val streakPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pink }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0xF4, 0xF2, 0xF4)
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textSize = 46f * density
        textAlign = Paint.Align.LEFT
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pink
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textSize = 19f * density
        textAlign = Paint.Align.LEFT
    }

    private val title = "NOVEL"
    private val subtitle = "SERIES"
    private val titleSpacing = 6f * density
    private val subtitleSpacing = 13f * density

    private class Particle(val angle: Float, val startRadius: Float, val size: Float, val delay: Float)

    private val particles = List(42) {
        Particle(
            angle = Random.nextFloat() * 2f * PI.toFloat(),
            startRadius = 0.45f + Random.nextFloat() * 0.6f,
            size = (2f + Random.nextFloat() * 4f),
            delay = Random.nextFloat() * 0.25f
        )
    }

    private val logoRect = RectF()
    private var elapsed = 0f
    private var finished = false
    private var framesShowingNotified = false

    private val animator = ValueAnimator.ofFloat(0f, TOTAL_MS).apply {
        duration = TOTAL_MS.toLong()
        interpolator = LinearInterpolator()
        addUpdateListener {
            elapsed = it.animatedValue as Float
            invalidate()
            if (elapsed >= FRAMES_SHOWING_MS && !framesShowingNotified) {
                framesShowingNotified = true
                onFramesShowing?.invoke()
            }
            if (elapsed >= TOTAL_MS && !finished) {
                finished = true
                onFinished?.invoke()
            }
        }
    }

    fun start() {
        if (!animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return
        val t = elapsed
        val cx = w / 2f

        // The logo starts at screen centre (where the system splash icon is) and rises to make
        // room for the wordmark.
        val rise = ease(progress(t, 1200f, 1650f)) * h * 0.065f
        val cy = h / 2f - rise

        drawBackgroundGlow(canvas, cx, cy, w, h, t)
        drawParticles(canvas, cx, cy, max(w, h), t)
        drawRings(canvas, cx, cy, w, t)
        drawLogo(canvas, cx, cy, t)
        drawStreak(canvas, cx, cy, w, t)
        drawWordmark(canvas, cx, cy, t)
    }

    private fun drawBackgroundGlow(canvas: Canvas, cx: Float, cy: Float, w: Float, h: Float, t: Float) {
        val fadeIn = ease(progress(t, 0f, 500f))
        val pulse = bump(t, 750f, 1300f)
        val radius = max(w, h) * 0.55f
        val alpha = (fadeIn * (0.85f + 0.15f * pulse) * 255).toInt()
        glowPaint.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(withAlpha(glowColor, alpha), withAlpha(glowColor, alpha / 3), Color.TRANSPARENT),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, glowPaint)
    }

    private fun drawParticles(canvas: Canvas, cx: Float, cy: Float, extent: Float, t: Float) {
        if (t > 1000f) return
        for (p in particles) {
            val local = progress(t / 1000f, p.delay, 1f)
            if (local <= 0f) continue
            // Accelerate into the logo, like the dust gathering in the video.
            val travel = local.pow(2)
            val r = extent * p.startRadius * (1f - travel) + logoSize * 0.15f * travel
            val x = cx + cos(p.angle) * r
            val y = cy + sin(p.angle) * r
            val fade = min(local / 0.2f, 1f) * (1f - progress(local, 0.75f, 1f))
            val size = p.size * density
            particlePaint.color = withAlpha(pink, (fade * 90).toInt())
            canvas.drawCircle(x, y, size * 2.2f, particlePaint)
            particlePaint.color = withAlpha(Color.rgb(0xFF, 0xD6, 0xE2), (fade * 255).toInt())
            canvas.drawCircle(x, y, size * 0.7f, particlePaint)
        }
    }

    private fun drawRings(canvas: Canvas, cx: Float, cy: Float, w: Float, t: Float) {
        val inner = progress(t, 750f, 1250f)
        if (inner in 0.001f..0.999f) {
            ringPaint.alpha = ((1f - inner) * 230).toInt()
            canvas.drawCircle(cx, cy, logoSize * (0.55f + 0.45f * ease(inner)), ringPaint)
        }
        val outer = progress(t, 800f, 1400f)
        if (outer in 0.001f..0.999f) {
            ringPaint.alpha = ((1f - outer) * 200).toInt()
            canvas.drawCircle(cx, cy, logoSize * 0.7f + (w * 0.5f) * ease(outer), ringPaint)
        }
    }

    private fun drawLogo(canvas: Canvas, cx: Float, cy: Float, t: Float) {
        // Small "pop" as the particles arrive.
        val scale = 1f + 0.08f * bump(t, 750f, 1150f)
        val half = logoSize * scale / 2f
        logoRect.set(cx - half, cy - half, cx + half, cy + half)
        canvas.drawBitmap(logo, null, logoRect, logoPaint)
    }

    private fun drawStreak(canvas: Canvas, cx: Float, cy: Float, w: Float, t: Float) {
        val p = progress(t, 1050f, 1500f)
        if (p <= 0f || p >= 1f) return
        val start = cx + logoSize * 0.45f
        val head = start + (w - start + logoSize) * ease(p)
        val tail = head - logoSize * 1.1f
        val alpha = ((1f - p) * 255).toInt()
        streakPaint.shader = LinearGradient(
            tail, cy, head, cy,
            intArrayOf(Color.TRANSPARENT, withAlpha(pink, alpha), withAlpha(Color.WHITE, alpha)),
            floatArrayOf(0f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(max(tail, start), cy - 1.5f * density, head, cy + 1.5f * density, streakPaint)
    }

    private fun drawWordmark(canvas: Canvas, cx: Float, logoCy: Float, t: Float) {
        // Measured from the launch video: wordmark sits below the risen logo.
        val titleBaseline = logoCy + logoSize * 1.05f + titlePaint.textSize * 0.36f
        val lineY = titleBaseline + 18f * density
        val subtitleBaseline = lineY + 16f * density + subtitlePaint.textSize * 0.72f

        // NOVEL: letters rise and fade in one after another.
        drawLetters(canvas, title, titlePaint, titleSpacing, cx, titleBaseline) { i ->
            val p = ease(progress(t, 1450f + i * 80f, 1450f + i * 80f + 300f))
            p to (1f - p) * 14f * density
        }

        // Underline grows out from the centre.
        val line = ease(progress(t, 1850f, 2150f))
        if (line > 0f) {
            val halfWidth = 118f * density * line
            canvas.drawRect(cx - halfWidth, lineY - 1.2f * density, cx + halfWidth, lineY + 1.2f * density, linePaint)
        }

        // SERIES: typed letter by letter with a blinking cursor.
        val typingStart = 2150f
        val perLetter = 85f
        val typed = ((t - typingStart) / perLetter).toInt().coerceIn(0, subtitle.length)
        if (t >= typingStart) {
            drawLetters(canvas, subtitle, subtitlePaint, subtitleSpacing, cx, subtitleBaseline) { i ->
                (if (i < typed) 1f else 0f) to 0f
            }
            val typingDone = typingStart + perLetter * subtitle.length
            val cursorOn = ((t - typingStart) / 180f).toInt() % 2 == 0
            if (t < typingDone + 350f && cursorOn) {
                val cursorX = cursorXAfter(typed, cx)
                subtitlePaint.alpha = 200
                canvas.drawRect(
                    cursorX + 3f * density, subtitleBaseline - subtitlePaint.textSize * 0.8f,
                    cursorX + 5f * density, subtitleBaseline + 2f * density, subtitlePaint
                )
                subtitlePaint.alpha = 255
            }
        }
    }

    /** X just after the [count]th subtitle letter, for placing the typing cursor. */
    private fun cursorXAfter(count: Int, cx: Float): Float {
        var x = cx - lettersWidth(subtitle, subtitlePaint, subtitleSpacing) / 2f
        for (i in 0 until count) x += subtitlePaint.measureText(subtitle, i, i + 1) + subtitleSpacing
        return x - subtitleSpacing
    }

    private fun lettersWidth(text: String, paint: Paint, spacing: Float): Float {
        var width = 0f
        for (i in text.indices) width += paint.measureText(text, i, i + 1)
        return width + spacing * (text.length - 1)
    }

    /**
     * Draws [text] centred on [cx], one letter at a time; [state] gives each letter's
     * (alpha 0..1, vertical offset).
     */
    private inline fun drawLetters(
        canvas: Canvas,
        text: String,
        paint: Paint,
        spacing: Float,
        cx: Float,
        baseline: Float,
        state: (Int) -> Pair<Float, Float>
    ) {
        var x = cx - lettersWidth(text, paint, spacing) / 2f
        for (i in text.indices) {
            val (alpha, offset) = state(i)
            val letterWidth = paint.measureText(text, i, i + 1)
            if (alpha > 0f) {
                paint.alpha = (alpha * 255).toInt()
                canvas.drawText(text, i, i + 1, x, baseline + offset, paint)
            }
            x += letterWidth + spacing
        }
        paint.alpha = 255
    }

    private fun progress(t: Float, start: Float, end: Float): Float =
        ((t - start) / (end - start)).coerceIn(0f, 1f)

    /** Ease-out cubic. */
    private fun ease(p: Float): Float = 1f - (1f - p).pow(3)

    /** 0 → 1 → 0 over [start, end]. */
    private fun bump(t: Float, start: Float, end: Float): Float {
        val p = progress(t, start, end)
        return sin(p * PI).toFloat()
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    companion object {
        private const val TOTAL_MS = 2900f
        private const val FRAMES_SHOWING_MS = 120f
    }
}
