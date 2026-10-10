/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.util

import com.engine.core.models.WoChapter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ChapterDate {

    private val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd HH:mm:ss.SSS",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd"
    )

    /**
     * When the chapter went public. updated_at changes on every edit or view sync, so it must not
     * be used here. Chapters published before publish_at existed fall back to created_at.
     */
    fun publishedRaw(chapter: WoChapter): String? =
        chapter.publishAt.takeUnless { it.isNullOrBlank() || it == "null" } ?: chapter.createdAt

    /** "14 Aug 2026", or "14 Aug 2026, 4:30 PM" with [withTime]. */
    fun format(rawDate: String?, withTime: Boolean = false): String {
        if (rawDate.isNullOrBlank() || rawDate == "null") return ""
        val normalized = rawDate.trim()
        val parsed = parse(normalized)
            ?: return normalized.substringBefore('T').substringBefore(' ')
        val pattern = if (withTime) "dd MMM yyyy, h:mm a" else "dd MMM yyyy"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(parsed)
    }

    private fun parse(value: String): Date? = patterns.firstNotNullOfOrNull { pattern ->
        runCatching {
            SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(value)
        }.getOrNull()
    }
}
