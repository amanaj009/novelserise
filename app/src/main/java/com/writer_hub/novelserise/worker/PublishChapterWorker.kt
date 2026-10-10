/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise.worker

import com.engine.core.manager.GlobalAppData
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.writer_hub.novelserise.R
import com.engine.core.models.WoChapter
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.WriterChapterViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PublishChapterWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val channelId = "publish_chapter_channel"
    private val notificationId = workerParams.id.hashCode()

    override suspend fun doWork(): Result {
        val novelId = inputData.getString("novel_id") ?: return Result.failure()
        val chapterId = inputData.getString("chapter_id") ?: return Result.failure()
        val chapterName = inputData.getString("chapter_name") ?: ""
        val contentFile = inputData.getString("content_file") ?: ""
        val wordCount = inputData.getString("word_count") ?: "0"
        val status = inputData.getString("status") ?: "published"

        createNotificationChannel()
        setForeground(createForegroundInfo(0, chapterName))

        return withContext(Dispatchers.IO) {
            try {
                // Update Global Progress
                updateProgress(chapterId, "Starting...", 10)

                val fileManager = AppProvider.getFileManager()
                val content = fileManager.getFromFile(contentFile) ?: ""
                
                // Clean up the temporary file
                val file = File(applicationContext.filesDir, contentFile)
                if (file.exists()) file.delete()

                val viewModel = AppProvider.getViewModelForWorker(applicationContext, WriterChapterViewModel::class.java)
                
                // Set Novel ID in view model
                viewModel.setSelectedNovelId(novelId)

                updateProgress(chapterId, "Uploading Content...", 40)

                val resultChapter = viewModel.saveOrUpdateChapter(
                    chapterId = chapterId,
                    chapterName = chapterName,
                    content = content,
                    wordCount = wordCount,
                    status = status
                )

                if (resultChapter != null) {
                    updateProgress(chapterId, "Completed", 100)
                    
                    // Update finished map so activity can refresh adapter
                    val finishedMap = GlobalAppData.finishedPublishingMap.value.orEmpty().toMutableMap()
                    finishedMap[chapterId] = resultChapter
                    GlobalAppData.finishedPublishingMap.postValue(finishedMap)
                    
                    // TRIGGER STREAM UPDATE
                    GlobalAppData.chapterSync.postValue(true)
                    
                    showFinalNotification(chapterName, true)
                    Result.success(workDataOf("chapter_id" to resultChapter.id))
                } else {
                    updateProgress(chapterId, "Failed", 0)
                    showFinalNotification(chapterName, false)
                    Result.failure()
                }
            } catch (e: Exception) {
                updateProgress(chapterId, "Error: ${e.message}", 0)
                showFinalNotification(chapterName, false)
                Result.failure()
            }
        }
    }

    private fun updateProgress(chapterId: String, status: String, progress: Int) {
        val currentMap = GlobalAppData.uploadProgressMap.value.orEmpty().toMutableMap()
        currentMap[chapterId] = Pair(status, progress)
        GlobalAppData.uploadProgressMap.postValue(currentMap)
        
        // Update notification
        notificationManager.notify(notificationId, createNotification(progress, "Publishing: $status"))
    }

    private fun createForegroundInfo(progress: Int, chapterName: String): ForegroundInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                createNotification(progress, "Publishing $chapterName"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, createNotification(progress, "Publishing $chapterName"))
        }
    }

    private fun createNotification(progress: Int, contentText: String) =
        NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Publishing Chapter")
            .setTicker("Publishing Chapter")
            .setContentText(contentText)
            .setSmallIcon(com.writer_hub.novelserise.R.drawable.ic_timer)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, progress, progress == 0)
            .build()

    private fun showFinalNotification(chapterName: String, success: Boolean) {
        val title = if (success) "Chapter Published!" else "Publishing Failed"
        val message = if (success) "$chapterName is now live." else "There was an error publishing $chapterName."
        
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(if (success) com.engine.core.R.drawable.ic_check_circle_filled else com.engine.core.R.drawable.ic_report)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId + 1, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Chapter Publishing",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }
}
