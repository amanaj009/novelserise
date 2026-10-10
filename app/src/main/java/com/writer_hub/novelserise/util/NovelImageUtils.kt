/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object NovelImageUtils {

    /**
     * Cache the bitmap to internal storage for temporary use during cropping
     */
    fun cacheImageToDisk(context: Context, bitmap: Bitmap): Uri? {
        val uniqueName = "novel_img_${UUID.randomUUID()}.png"
        val cacheFile = File(context.cacheDir, uniqueName)
        return try {
            FileOutputStream(cacheFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }
            Uri.fromFile(cacheFile)
        } catch (e: Exception) {
            Log.e("NovelImageUtils", "Failed to cache image: ${e.message}", e)
            null
        }
    }
}
