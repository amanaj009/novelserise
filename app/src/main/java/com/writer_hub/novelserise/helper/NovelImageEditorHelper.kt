/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.helper

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.writer_hub.novelserise.NovelImageEditorActivity

object NovelImageEditorHelper {

    fun getEditorIntent(
        context: Context,
        uri: Uri,
        title: String,
        x: Int = 1,
        y: Int = 1,
        isFixed: Boolean = false
    ): Intent {
        return Intent(context, NovelImageEditorActivity::class.java).apply {
            putExtra(NovelImageEditorActivity.KEY_SOURCE_URI, uri)
            putExtra(NovelImageEditorActivity.KEY_SCREEN_TITLE, title)
            putExtra(NovelImageEditorActivity.KEY_RATIO_X, x)
            putExtra(NovelImageEditorActivity.KEY_RATIO_Y, y)
            putExtra(NovelImageEditorActivity.KEY_IS_FIXED, isFixed)
        }
    }

    fun getNovelCoverIntent(context: Context, uri: Uri): Intent {
        return getEditorIntent(context, uri, "Novel Cover", 2, 3, true)
    }

    fun getWideBannerIntent(context: Context, uri: Uri): Intent {
        return getEditorIntent(context, uri, "Wide Banner", 16, 9, true)
    }
}
