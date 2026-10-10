/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.notificationmanager

import com.engine.core.notification.SMInternalNotificationManager
import com.engine.core.notification.SMFirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : SMFirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Data messages are displayed by the shared payload handler. Firebase only
        // auto-displays notification messages while the app is in the background,
        // so explicitly display notification-only messages in the foreground.
        if (remoteMessage.data.isEmpty()) {
            remoteMessage.notification?.let { notification ->
                SMInternalNotificationManager.showDefaultNotification(
                    notificationId = remoteMessage.messageId?.hashCode()
                        ?: System.currentTimeMillis().toInt(),
                    title = notification.title ?: "NovelSeries",
                    message = notification.body.orEmpty(),
                    image = notification.imageUrl?.toString(),
                    payload = null,
                    notificationType = "update"
                )
            }
        }
    }
}
