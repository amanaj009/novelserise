package com.writer_hub.novelserise.helper

import android.content.Context
import android.content.Intent
import com.writer_hub.novelserise.ChapterCommentsActivity
import com.writer_hub.novelserise.ChatActivity
import com.writer_hub.novelserise.NovelDetailActivity
import com.writer_hub.novelserise.PostCommentActivity
import com.writer_hub.novelserise.RewardsActivity
import com.writer_hub.novelserise.UserDetailsActivity
import com.engine.core.helper.AppLoggerHelper
import com.engine.core.models.Notification

object NotificationHandler {

    private const val TAG = "NotificationHandler"

    fun handleNotificationClick(context: Context, notification: Notification) {
        when (notification.type) {
            "liked_post" -> {
                val postId = notification.postId
                if (!postId.isNullOrEmpty()) {
                    PostCommentActivity.start(context, postId)
                }
            }
            "following" -> {
                val notifierId = notification.notifierId
                if (!notifierId.isNullOrEmpty()) {
                    UserDetailsActivity.start(context, notifierId)
                }
            }
            "comment", "comment_reply", "liked_comment" -> {
                val postId = notification.postId
                if (!postId.isNullOrEmpty()) {
                    PostCommentActivity.start(context, postId)
                }
            }
            "chapter_comment" -> {
                val novelId = notification.novelId ?: "0"
                val chapterId = notification.chapterId
                if (!chapterId.isNullOrEmpty()) {
                    ChapterCommentsActivity.start(context, novelId, chapterId)
                }
            }
            "chapter_published" -> {
                val novelId = notification.novelId
                if (!novelId.isNullOrEmpty()) {
                    NovelDetailActivity.start(context, novelId)
                }
            }
            "chat" -> {
                val userId = notification.notifierId ?: ""
                val name = notification.userData?.name ?: "Unknown"
                val avatar = notification.userData?.avatar ?: ""
                if (userId.isNotEmpty()) {
                    ChatActivity.start(context, userId, name, avatar)
                }
            }
            "gift", "memory" -> {
                context.startActivity(Intent(context, RewardsActivity::class.java))
            }
            "resource_gift_received" -> {
                val novelId = notification.novelId
                if (!novelId.isNullOrEmpty()) {
                    NovelDetailActivity.start(context, novelId)
                } else {
                    AppLoggerHelper.d(TAG, "resource_gift_received: No novel ID available")
                }
            }
            else -> {
                AppLoggerHelper.w(TAG, "Unknown notification type: ${notification.type}")
            }
        }
    }
}
