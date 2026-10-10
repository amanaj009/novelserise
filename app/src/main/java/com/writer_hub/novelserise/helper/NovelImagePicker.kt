/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.helper

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.writer_hub.novelserise.NovelImageEditorActivity

class NovelImagePicker(
    private val caller: ActivityResultCaller,
    private val onResult: (Uri, Int) -> Unit
) {
    private var activeId: Int = 0
    private var provider: ((Uri, Context) -> Intent)? = null

    private val context: Context?
        get() = when (caller) {
            is Context -> caller
            is Fragment -> caller.context
            else -> null
        }

    private val editorLauncher = caller.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                res.data?.getParcelableExtra(NovelImageEditorActivity.KEY_OUTPUT_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                res.data?.getParcelableExtra(NovelImageEditorActivity.KEY_OUTPUT_URI)
            }
            uri?.let { onResult(it, activeId) }
        }
    }

    private val galleryLauncher = caller.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { pickedUri ->
            context?.let { ctx ->
                val intent = provider?.invoke(pickedUri, ctx)
                if (intent != null) {
                    editorLauncher.launch(intent)
                }
            }
        }
    }

    fun start(id: Int, intentProvider: (Uri, Context) -> Intent) {
        activeId = id
        provider = intentProvider
        galleryLauncher.launch("image/*")
    }
}
