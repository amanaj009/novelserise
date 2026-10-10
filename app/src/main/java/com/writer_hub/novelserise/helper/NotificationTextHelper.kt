/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.helper

import com.engine.core.helper.decodeHtmlToTextV2
import com.engine.core.models.Notification

fun generateNotificationText(notification: Notification): String {
    val userName = notification.userData?.name?.takeIf { it.isNotEmpty() } ?: "App User"
    val notificationText = decodeHtmlToTextV2(notification.text).takeIf { it.isNotEmpty() } ?: "No description available"

    return when (notification.type) {
        "following" -> "\"$userName\" ${notification.typeText}"
        "liked_post" -> "\"$userName\" ${notification.typeText}"
        "chapter_published" -> notificationText
        "chapter_comment" -> notificationText
        "chapter_rating" -> "$userName $notificationText"
        "comment" -> "\"$userName\" ${notification.typeText}"
        "comment_reply" -> "\"$userName\" ${notification.typeText}"
        "liked_comment" -> "\"$userName\" ${notification.typeText}"
        "gift" -> "\"$userName\" ${notification.typeText}"
        "liked_page" -> "\"$userName\" ${notification.typeText}"
        "memory" -> "\"$userName\" ${notification.typeText}"
        else -> "Notification: $notificationText"
    }
}
