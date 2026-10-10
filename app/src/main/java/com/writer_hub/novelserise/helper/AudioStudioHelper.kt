package com.writer_hub.novelserise.helper

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

object AudioStudioHelper {

    /**
     * Processes audio with optimized settings for Voice: AAC, 96k, Mono.
     */
    suspend fun processAudio(
        context: Context,
        inputUri: Uri,
        trimStartMs: Long,
        trimEndMs: Long,
        voiceEffect: String? = null,
        bgMusicUri: Uri? = null,
        bgVolume: Float = 0.3f,
        bgStartOffsetMs: Long = 0,
        bgEndOffsetMs: Long = 0,
        onProgress: (String) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val mainPath = getRealPathFromUri(context, inputUri) ?: return@withContext null
        val outputDir = File(context.cacheDir, "studio_output").apply { mkdirs() }
        val outputFile = File(outputDir, "studio_final_${System.currentTimeMillis()}.m4a")

        onProgress("Preparing Studio...")

        val startSec = trimStartMs / 1000.0
        val durationSec = (trimEndMs - trimStartMs) / 1000.0
        
        // 1. Main Audio Processing (Trim + Voice Effects)
        var mainFilter = "volume=1.0"
        when (voiceEffect) {
            "deep" -> mainFilter += ",asetrate=44100*0.8,atempo=1.25"
            "chipmunk" -> mainFilter += ",asetrate=44100*1.5,atempo=0.67"
            "echo" -> mainFilter += ",aecho=0.8:0.9:1000:0.3"
        }

        val command: String
        // Optimization: Added -ac 1 (Mono) and -b:a 96k for best size/quality balance
        if (bgMusicUri != null) {
            val bgPath = getRealPathFromUri(context, bgMusicUri) ?: mainPath
            onProgress("Mixing sound layers...")

            val bgStartSec = bgStartOffsetMs / 1000.0
            val bgDurationSec = (bgEndOffsetMs - bgStartOffsetMs) / 1000.0

            val filterComplex = "[0:a]atrim=0:$durationSec,$mainFilter[main]; " +
                    "[1:a]atrim=0:$bgDurationSec,volume=$bgVolume,adelay=${bgStartOffsetMs}|${bgStartOffsetMs}[bg]; " +
                    "[main][bg]amix=inputs=2:duration=first:dropout_transition=2"

            command = "-y -ss $startSec -t $durationSec -i \"$mainPath\" -i \"$bgPath\" " +
                    "-filter_complex \"$filterComplex\" -ac 1 -c:a aac -b:a 96k \"${outputFile.absolutePath}\""
        } else {
            onProgress("Applying studio effects...")
            command = "-y -ss $startSec -t $durationSec -i \"$mainPath\" " +
                    "-af \"$mainFilter\" -ac 1 -c:a aac -b:a 96k \"${outputFile.absolutePath}\""
        }

        val session = FFmpegKit.execute(command)
        if (ReturnCode.isSuccess(session.returnCode)) {
            outputFile
        } else {
            null
        }
    }

    private fun getRealPathFromUri(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.path
        
        val tempFile = File(context.cacheDir, "studio_in_${System.currentTimeMillis()}")
        return try {
            if (uri.scheme == "http" || uri.scheme == "https") {
                URL(uri.toString()).openStream().use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            tempFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}
