/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.util

import android.util.LruCache
import com.engine.core.helper.decodeHtmlToTextV2

/**
 * Cheap text for list bindings. decodeHtmlToTextV2 logs three lines, rebuilds the string and runs
 * Html.fromHtml on every call, which is too slow to do per card while scrolling. Plain text (the
 * usual case) skips all of it; anything with markup or entities is decoded once and cached.
 */
object DisplayText {
    private val cache = LruCache<String, String>(512)

    fun of(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        if (raw.none { it == '<' || it == '&' || it == '\n' || it == '\r' || it == '\t' }) {
            return raw.trimEnd() // same result decodeHtmlToTextV2 gives for plain text
        }
        return cache.get(raw) ?: decodeHtmlToTextV2(raw).also { cache.put(raw, it) }
    }
}
