/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import android.graphics.RectF

/**
 * Where the mini player (layout_now_playing.xml) and its parts sit on screen, so the full audio
 * player can shrink its cover, titles, play button and seek bar into exactly those spots while it
 * collapses and hand off without a visible jump.
 *
 * The screen hosting the mini player publishes this while it is started; the player falls back to a
 * plain slide-down when [bottomY] is null. The measured rects/text sizes are exact once the mini player
 * has been laid out; until then the dp defaults below (kept in sync with layout_now_playing.xml) are used.
 */
object MiniPlayerAnchor {
    /** Screen Y (px) of the mini player's bottom edge, or null when no mini player is on screen. */
    var bottomY: Int? = null

    /** Measured part bounds in px, relative to the mini player's top-left; null until laid out. */
    var coverRect: RectF? = null
    var titleRect: RectF? = null
    var subtitleRect: RectF? = null
    var playRect: RectF? = null
    var closeRect: RectF? = null
    var titleTextPx: Float? = null
    var subtitleTextPx: Float? = null

    const val HEIGHT_DP = 64f
    const val COVER_START_DP = 12f
    const val COVER_WIDTH_DP = 36f
    const val COVER_HEIGHT_DP = 54f
    const val COVER_CORNER_DP = 4f
    const val TEXT_START_DP = 60f
    const val TITLE_TOP_DP = 13f
    const val SUBTITLE_TOP_DP = 34f
    const val TITLE_TEXT_SP = 14f
    const val SUBTITLE_TEXT_SP = 12f
    const val PLAY_SIZE_DP = 40f
    /** Distance from the right screen edge to the play button's right edge. */
    const val PLAY_END_DP = 52f
    const val CLOSE_SIZE_DP = 40f
    const val CLOSE_END_DP = 8f
    const val PROGRESS_HEIGHT_DP = 2f

    fun clearParts() {
        coverRect = null; titleRect = null; subtitleRect = null; playRect = null; closeRect = null
        titleTextPx = null; subtitleTextPx = null
    }
}
