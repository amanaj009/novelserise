/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.ui.deepLink

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.engine.core.helper.AppLoggerHelper
import com.engine.core.manager.GlobalAppData
import com.engine.core.utill.SocialConfig
import com.engine.core.viewmodel.SplashViewModel
import com.writer_hub.novelserise.AudioPlayerActivity
import com.writer_hub.novelserise.DashboardActivity
import com.writer_hub.novelserise.NovelDetailActivity
import com.writer_hub.novelserise.PlayChapterActivity
import com.writer_hub.novelserise.PostCommentActivity
import com.writer_hub.novelserise.ReadChapterActivity
import com.writer_hub.novelserise.UserDetailsActivity

/**
 * Builds and opens share links. Content links carry `?mode=audio|novel` so the receiving app
 * knows whether to open the reading chapter or the audio episode, and switches its app type to match.
 *
 *  - `/novel/{novelId}?mode=...`
 *  - `/chapter/{chapterId}?mode=novel` (reading chapter)
 *  - `/audio/{novelId}/{chapterId}?mode=audio` (audio episode)
 */
object DeepLinkRouter {

    private const val TAG = "DeepLinkRouter"
    const val PARAM_MODE = "mode"
    const val MODE_AUDIO = "audio"
    const val MODE_NOVEL = "novel"

    private val baseUrl get() = SocialConfig.shareFaceLink.trimEnd('/')

    private fun currentMode() = if (GlobalAppData.isAudioMode()) MODE_AUDIO else MODE_NOVEL

    fun novelLink(novelId: String, mode: String = currentMode()) = "$baseUrl/novel/$novelId?$PARAM_MODE=$mode"

    fun readingChapterLink(chapterId: String) = "$baseUrl/chapter/$chapterId?$PARAM_MODE=$MODE_NOVEL"

    fun audioEpisodeLink(novelId: String?, chapterId: String) =
        if (novelId.isNullOrBlank()) "$baseUrl/chapter/$chapterId?$PARAM_MODE=$MODE_AUDIO"
        else "$baseUrl/audio/$novelId/$chapterId?$PARAM_MODE=$MODE_AUDIO"

    /**
     * App type the link asks for, or null when it doesn't care (profile/post, or an old novel link).
     * Accepts `mode=audio|novel`, `audio_mode=1|0` and `app_type=<GlobalAppData type>`.
     */
    fun requiredAppType(uri: Uri): String? {
        val explicitAudio: Boolean? = uri.getQueryParameter(PARAM_MODE)?.lowercase()?.let {
            when (it) { MODE_AUDIO, "1" -> true; MODE_NOVEL, "0" -> false; else -> null }
        } ?: uri.getQueryParameter("audio_mode")?.let {
            when (it) { "1", "true" -> true; "0", "false" -> false; else -> null }
        } ?: uri.getQueryParameter("app_type")?.lowercase()?.let {
            when {
                GlobalAppData.isAudioMode(it) -> true
                GlobalAppData.isNovelMode(it) -> false
                else -> null
            }
        }
        val isAudio = explicitAudio ?: when (uri.pathSegments.firstOrNull()?.lowercase()) {
            "audio" -> true
            "chapter" -> false
            else -> return null
        }
        return if (isAudio) GlobalAppData.APP_TYPE_ONLY_AUDIO else GlobalAppData.APP_TYPE_ONLY_NOVEL
    }

    /** True when the link needs a different app type than the one currently active. */
    fun needsModeSwitch(uri: Uri): Boolean {
        val required = requiredAppType(uri) ?: return false
        return GlobalAppData.isAudioMode(required) != GlobalAppData.isAudioMode()
    }

    /** Switches the app type (persisted) if the link asks for one. Call before any screen is built. */
    fun applyMode(uri: Uri, splashViewModel: SplashViewModel) {
        val required = requiredAppType(uri) ?: return
        if (GlobalAppData.isAudioMode(required) != GlobalAppData.isAudioMode()) {
            AppLoggerHelper.d(TAG, "Switching app type to $required for $uri")
            splashViewModel.setAppType(required)
        }
    }

    /** Opens the screen for [uri]. Returns false when the link was not recognised. */
    fun open(activity: Activity, uri: Uri): Boolean {
        val segments = uri.pathSegments
        val type = segments.firstOrNull()?.lowercase() ?: return false
        val id = segments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return false
        val audio = GlobalAppData.isAudioMode()

        when (type) {
            "novel" -> NovelDetailActivity.start(activity, id)

            "chapter" -> if (audio) {
                PlayChapterActivity.start(activity, id)
            } else {
                val chapterId = id.toIntOrNull() ?: return false
                ReadChapterActivity.start(activity, chapterId)
            }

            "audio" -> {
                val chapterId = segments.getOrNull(2)?.takeIf { it.isNotBlank() } ?: return false
                if (audio) {
                    AudioPlayerActivity.start(activity, chapterId, id)
                } else {
                    val readId = chapterId.toIntOrNull() ?: return false
                    ReadChapterActivity.start(activity, readId)
                }
            }

            "profile" -> UserDetailsActivity.start(activity, id)

            "post" -> PostCommentActivity.start(activity, id)

            else -> return false
        }
        return true
    }

    fun openOrDashboard(activity: Activity, uri: Uri) {
        if (!open(activity, uri)) activity.startActivity(Intent(activity, DashboardActivity::class.java))
    }

    /**
     * Opens a deep-link destination with Dashboard underneath it. Splash uses this when it is
     * rebuilding the task for a cold start or an app-mode switch, so Back from shared content
     * returns to the app instead of closing it.
     */
    fun openWithDashboardBackStack(activity: Activity, uri: Uri) {
        activity.startActivity(Intent(activity, DashboardActivity::class.java))
        open(activity, uri)
    }
}
