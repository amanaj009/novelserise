/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 */

package com.writer_hub.novelserise.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.engine.core.helper.AppLoggerHelper
import com.engine.core.helper.ImageHelper
import com.engine.core.manager.GlobalAppData
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.WoChpterViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AudioUploadWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val viewModel: WoChpterViewModel =
        AppProvider.getViewModelForWorker(appContext, WoChpterViewModel::class.java)
    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var notificationBitmap: Bitmap? = null
    private val notificationId = id.hashCode()

    override suspend fun doWork(): Result {
        val fileUriString = inputData.getString(KEY_FILE_URI) ?: return Result.failure()
        val chapterId = inputData.getString(KEY_CHAPTER_ID) ?: return Result.failure()
        val novelId = inputData.getString(KEY_NOVEL_ID) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: return Result.failure()
        val status = inputData.getString(KEY_STATUS) ?: "draft"
        val isLocked = inputData.getString(KEY_IS_LOCKED) ?: "0"
        val imageUrl = inputData.getString(KEY_IMAGE_URL)

        if (!imageUrl.isNullOrEmpty()) {
            notificationBitmap = ImageHelper.loadBitmap(appContext, imageUrl, 512, 512)
        }

        setForeground(createForegroundInfo("Starting upload..."))

        return try {
            val fileUri = Uri.parse(fileUriString)
            val finalFile = if (fileUri.scheme == "file") fileUri.path?.let(::File) else null
            if (finalFile == null || !finalFile.exists()) {
                showFinalNotification(title, false)
                return Result.failure()
            }

            val finalUploadUri = Uri.fromFile(finalFile)
            val finalFileType = "audio/mp4"
            updateProgress(chapterId, "Preparing upload...", 0)

            val uploadLinkResponse =
                viewModel.getAudioUploadLink(finalFile.name, finalFileType).await()
                    ?: return Result.failure().also { showFinalNotification(title, false) }

            updateProgress(chapterId, "Uploading...", 0)
            val uploadSuccess = viewModel.uploadFileToR2(
                applicationContext,
                uploadLinkResponse.uploadUrl,
                finalUploadUri,
                finalFileType
            ) { progress -> updateProgress(chapterId, "Uploading: $progress%", progress) }.await()

            if (!uploadSuccess) {
                showFinalNotification(title, false)
                return Result.failure()
            }

            updateProgress(chapterId, "Finalizing...", 95)
            val updatedChapter = viewModel.saveAudioChapter(
                chapterId = chapterId,
                novelId = novelId,
                title = title,
                status = status,
                isLocked = isLocked,
                fileKey = uploadLinkResponse.fileKey
            ).await()

            if (updatedChapter != null) {
                withContext(Dispatchers.Main) {
                    val finishedMap = GlobalAppData.finishedPublishingMap.value.orEmpty().toMutableMap()
                    finishedMap[chapterId] = updatedChapter
                    GlobalAppData.finishedPublishingMap.value = finishedMap
                    if (GlobalAppData.selectedChapter.value?.id == chapterId) {
                        GlobalAppData.selectedChapter.value = updatedChapter
                    }
                }
                showFinalNotification(title, true)
                Result.success()
            } else {
                showFinalNotification(title, false)
                Result.failure()
            }
        } catch (error: Exception) {
            AppLoggerHelper.e(TAG, "Audio upload failed", error)
            showFinalNotification(title, false)
            Result.failure()
        } finally {
            withContext(Dispatchers.Main) {
                val progressMap = GlobalAppData.uploadProgressMap.value.orEmpty().toMutableMap()
                progressMap.remove(chapterId)
                GlobalAppData.uploadProgressMap.value = progressMap
            }
        }
    }

    private fun updateProgress(chapterId: String, text: String, progress: Int) {
        notificationManager.notify(notificationId, createNotification(text, progress, true))
        val progressMap = GlobalAppData.uploadProgressMap.value.orEmpty().toMutableMap()
        progressMap[chapterId] = text to progress
        GlobalAppData.uploadProgressMap.postValue(progressMap)
    }

    private fun createForegroundInfo(progress: String): ForegroundInfo {
        val notification = createNotification(progress, 0, true)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun createNotification(
        progress: String,
        progressPercent: Int,
        isOngoing: Boolean
    ): Notification {
        ensureNotificationChannel()
        val builder = NotificationCompat.Builder(appContext, UPLOAD_CHANNEL_ID)
            .setContentTitle("Uploading Audio")
            .setContentText(progress)
            .setSmallIcon(com.engine.core.R.drawable.ic_upload)
            .setOngoing(isOngoing)
            .setOnlyAlertOnce(true)
            .setProgress(100, progressPercent, false)

        notificationBitmap?.let { bitmap ->
            builder.setLargeIcon(bitmap)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(bitmap)
                    .setSummaryText(progress)
            )
        }
        return builder.build()
    }

    private fun showFinalNotification(title: String, success: Boolean) {
        ensureNotificationChannel()
        notificationManager.cancel(notificationId)
        val notification = NotificationCompat.Builder(appContext, UPLOAD_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(if (success) "Upload complete" else "Upload failed")
            .setSmallIcon(
                if (success) com.engine.core.R.drawable.ic_check_circle_filled
                else com.engine.core.R.drawable.ic_report
            )
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(notificationId + 1, notification)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    UPLOAD_CHANNEL_ID,
                    "Audio Upload",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    companion object {
        const val KEY_FILE_URI = "KEY_FILE_URI"
        const val KEY_CHAPTER_ID = "KEY_CHAPTER_ID"
        const val KEY_NOVEL_ID = "KEY_NOVEL_ID"
        const val KEY_TITLE = "KEY_TITLE"
        const val KEY_STATUS = "KEY_STATUS"
        const val KEY_IS_LOCKED = "KEY_IS_LOCKED"
        const val KEY_SKIP_COMPRESSION = "KEY_SKIP_COMPRESSION"
        const val KEY_IMAGE_URL = "KEY_IMAGE_URL"

        private const val UPLOAD_CHANNEL_ID = "upload_channel"
        private const val TAG = "AudioUploadWorker"
    }
}
