/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.ui.handlePayload

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import com.engine.core.helper.AppLoggerHelper
import com.engine.core.models.Notification
import com.engine.core.models.Novel
import com.engine.core.utill.EdgeToEdgeHelper
import com.writer_hub.novelserise.BaseActivity
import com.writer_hub.novelserise.R
import com.writer_hub.novelserise.helper.NotificationHandler

class HandlePayloadActivity : BaseActivity() {

    private val TAG = "HandlePayloadActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        // Fix for ClassNotFoundException when unmarshalling Parcelables like Novel.
        // We set the correct ClassLoader for Intent extras early to avoid crashes in system services.
        intent?.setExtrasClassLoader(Novel::class.java.classLoader)

        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // This enables edge-to-edge UI if needed
        setContentView(R.layout.activity_handle_payload)

        // Use a generic ID if R.id.root doesn't exist yet, but in layout we'll name it root.
        val rootView = findViewById<View>(R.id.root)
        if (rootView != null) {
            EdgeToEdgeHelper.applyEdgeToEdge(this, rootView)
        }

        // Use IntentCompat to safely retrieve the notification data
        val notification = intent?.let {
            IntentCompat.getParcelableExtra(it, "notification_data", Notification::class.java)
        }

        // If the notification is found, process it
        if (notification != null) {
            AppLoggerHelper.d(TAG, "Notification data found: ${notification.type}")
            // Use the NotificationHandler to process the notification
            NotificationHandler.handleNotificationClick(this, notification)
        } else {
            // If no notification data is found, log a warning
            AppLoggerHelper.w(TAG, "No notification data found.")
        }

        // Close the activity permanently after handling the notification
        finish()
    }
}
