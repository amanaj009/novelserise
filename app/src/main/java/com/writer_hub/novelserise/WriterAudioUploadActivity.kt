/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

package com.writer_hub.novelserise

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.graphics.Color
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.engine.core.helper.AlertUtils
import com.engine.core.helper.decodeHtmlToTextV2
import com.engine.core.models.WoChapter
import com.engine.core.utill.EdgeToEdgeHelper
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.WoChpterViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.writer_hub.novelserise.databinding.ActivityWriterAudioUploadBinding
import com.writer_hub.novelserise.databinding.DialogAudioSetupBinding
import com.writer_hub.novelserise.databinding.DialogEditTitleBinding
import com.writer_hub.novelserise.databinding.ItemStudioTrackBinding
import com.writer_hub.novelserise.helper.AudioStudioHelper
import com.writer_hub.novelserise.worker.AudioUploadWorker
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.max

class WriterAudioUploadActivity : BaseActivity() {

    private lateinit var binding: ActivityWriterAudioUploadBinding
    private lateinit var viewModel: WoChpterViewModel
    private var chapter: WoChapter? = null
    
    private val tracks = mutableListOf<TrackModel>()
    private val players = mutableMapOf<String, MediaPlayer>()
    private var audioSetupDialog: BottomSheetDialog? = null
    private var audioSetupPlayer: MediaPlayer? = null
    
    private val workManager by lazy { WorkManager.getInstance(applicationContext) }

    private val BASE_PIXELS_PER_SECOND = 50 
    private var scaleFactor = 1.0f

    private val pixelsPerSecond: Int
        get() = (BASE_PIXELS_PER_SECOND * scaleFactor).toInt()

    private var isPlaying = false
    private val playbackHandler = Handler(Looper.getMainLooper())
    private var playbackTimeMs = 0L
    private var totalDurationMs = 60000L

    private val playbackRunnable = object : Runnable {
        override fun run() {
            if (!isPlaying) return
            playbackTimeMs += 50 
            if (playbackTimeMs > totalDurationMs) {
                stopPlayback()
                return
            }
            updatePlayheadUI()
            checkPlayers()
            playbackHandler.postDelayed(this, 50)
        }
    }

    data class TrackModel(
        val id: String = UUID.randomUUID().toString(),
        val uri: Uri,
        var name: String,
        var durationMs: Long,
        var trimStartMs: Long = 0,
        var trimEndMs: Long = 0,
        var volume: Float = 1.0f,
        var effect: String? = null,
        var offsetMs: Long = 0,
        var isLocal: Boolean = true,
        var isLocked: Boolean = false
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWriterAudioUploadBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdgeHelper.applyEdgeToEdge(this, binding.root)
        
        viewModel = AppProvider.getViewModel(application, WoChpterViewModel::class.java)
        chapter = intent.getParcelableExtra("chapter")
        
        initUI()
        loadInitialData()
        observeActiveUpload()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initUI() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnEditTitle.setOnClickListener { showEditTitleDialog() }
        
        binding.btnChooseAudio.setOnClickListener { pickAudioLauncher.launch(arrayOf("audio/*")) }

        val onSurface = getThemeColor(this, com.google.android.material.R.attr.colorOnSurface)
        binding.timelineRuler.setColors(
            minor = ColorUtils.setAlphaComponent(onSurface, 0x40),
            major = ColorUtils.setAlphaComponent(onSurface, 0x99),
            label = ColorUtils.setAlphaComponent(onSurface, 0x99)
        )
        binding.timelineRuler.dpPerSecond = pixelsPerSecond.toFloat()
        // Centre the playhead knob on 0:00 once it has a width.
        binding.playhead.post { updatePlayheadUI() }

        binding.btnAddMusic.setOnClickListener {
            if (tracks.isNotEmpty()) {
                Toast.makeText(this, "Only one audio track is allowed.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pickAudioLauncher.launch(arrayOf("audio/*"))
        }
        
        binding.btnUpload.setOnClickListener { saveAndUpload() }
        
        binding.btnPlayPauseGlobal.setOnClickListener {
            togglePlayback()
        }
        
        binding.timelineContent.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                val x = event.x
                val density = resources.displayMetrics.density
                val seconds = x / (pixelsPerSecond * density)
                playbackTimeMs = (seconds * 1000).toLong().coerceAtLeast(0)
                updatePlayheadUI()
                return@setOnTouchListener true
            }
            false
        }
        
        binding.timelineContent.minimumWidth = (300 * pixelsPerSecond * resources.displayMetrics.density).toInt()
    }

    private fun loadInitialData() {
        binding.tvToolbarTitle.text = decodeHtmlToTextV2(chapter?.chapterName) ?: "Audio Studio"

        if (!chapter?.audioFilePath.isNullOrEmpty()) {
            val remoteUrl = chapter?.audioFilePath!!
            val name = remoteUrl.substringAfterLast('/')
            val track = TrackModel(
                uri = Uri.parse(remoteUrl),
                name = name,
                durationMs = 0, 
                isLocal = false
            )
            tracks.add(track)
            
            lifecycleScope.launch {
                try {
                    val mp = MediaPlayer()
                    mp.setDataSource(remoteUrl)
                    mp.prepareAsync()
                    mp.setOnPreparedListener { 
                        track.durationMs = it.duration.toLong()
                        track.trimEndMs = track.durationMs
                        renderTracks()
                        updateEmptyState()
                        mp.release()
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
        renderTracks()
        updateEmptyState()
    }
    
    private fun updateEmptyState() {
        val empty = tracks.isEmpty()
        binding.layoutEmptyState.visibility = if (empty) View.VISIBLE else View.GONE
        // Nothing to scrub through yet: keep the empty state clean.
        binding.playhead.visibility = if (empty) View.INVISIBLE else View.VISIBLE
        binding.tvTimelineHint.visibility = if (empty) View.GONE else View.VISIBLE
        // One track per chapter: the add button steps back once a track exists.
        binding.btnAddMusic.alpha = if (empty) 1f else 0.45f
        binding.tvProjectInfo.text = tracks.firstOrNull()?.let { track ->
            val effect = when (track.effect) { "deep" -> " · Deep voice"; "chipmunk" -> " · Chipmunk"; "echo" -> " · Echo"; else -> "" }
            "1 track · ${formatDuration(track.trimEndMs - track.trimStartMs)} · ${(track.volume * 100).toInt()}% volume$effect"
        } ?: "No audio yet · add a track to begin"
    }

    private val pickAudioLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            showAudioSetupDialog(uri, isNew = true)
        }
    }

    private fun showAudioSetupDialog(uri: Uri, isNew: Boolean, existingTrack: TrackModel? = null) {
        releaseAudioSetupResources(dismissDialog = true)
        val dialog = BottomSheetDialog(this, R.style.ThemeOverlay_NovelSerise_TransparentBottomSheet)
        audioSetupDialog = dialog
        val sheet = DialogAudioSetupBinding.inflate(layoutInflater)
        dialog.setContentView(sheet.root)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true

        val fileName = existingTrack?.name ?: getFileName(uri) ?: "Audio file"
        sheet.tvSetupTitle.text = if (isNew) "Set up audio" else "Edit track"
        sheet.tvFileName.text = fileName

        var tempPlayer: MediaPlayer? = MediaPlayer()
        audioSetupPlayer = tempPlayer

        tempPlayer?.setOnPreparedListener { mp ->
            if (
                audioSetupPlayer !== mp ||
                audioSetupDialog !== dialog ||
                isFinishing ||
                isDestroyed
            ) {
                runCatching { mp.release() }
                return@setOnPreparedListener
            }

            val duration = mp.duration.toLong()
            sheet.progressLoading.visibility = View.GONE
            sheet.contentGroup.visibility = View.VISIBLE
            sheet.tvFileName.text = "$fileName · ${formatDuration(duration)}"

            // Volume
            sheet.sliderVolume.value = (existingTrack?.volume ?: 1.0f).coerceIn(0f, 1f)
            sheet.tvVolumeValue.text = "${(sheet.sliderVolume.value * 100).toInt()}%"
            sheet.sliderVolume.addOnChangeListener { _, v, _ ->
                tempPlayer?.setVolume(v, v)
                sheet.tvVolumeValue.text = "${(v * 100).toInt()}%"
            }

            // Trim
            val trim = sheet.sliderTrim
            trim.valueFrom = 0.0f
            trim.valueTo = max(duration.toFloat(), 1.0f)
            val start = (existingTrack?.trimStartMs?.toFloat() ?: 0.0f).coerceIn(trim.valueFrom, trim.valueTo)
            val end = if (existingTrack != null && existingTrack.trimEndMs > 0) existingTrack.trimEndMs.toFloat().coerceIn(start, trim.valueTo) else trim.valueTo
            trim.setValues(start, end)
            val showTrim = {
                val a = trim.values[0].toLong()
                val b = trim.values[1].toLong()
                sheet.tvTrimValue.text = "${formatDuration(a)} – ${formatDuration(b)} · ${formatDuration(b - a)}"
            }
            showTrim()
            trim.addOnChangeListener { _, _, _ -> showTrim() }

            // Voice effect chips
            val effectKeys = listOf<String?>(null, "deep", "chipmunk", "echo")
            var selectedEffect = existingTrack?.effect?.takeIf { it in effectKeys }
            val chips = (0 until sheet.effectChips.childCount).map { sheet.effectChips.getChildAt(it) as TextView }
            val onSurface = getThemeColor(this, com.google.android.material.R.attr.colorOnSurface)
            val renderChips = {
                chips.forEachIndexed { i, chip ->
                    val selected = effectKeys[i] == selectedEffect
                    chip.setBackgroundResource(if (selected) R.drawable.bg_filter_chip_selected else R.drawable.bg_filter_chip)
                    chip.setTextColor(if (selected) Color.WHITE else onSurface)
                }
            }
            chips.forEachIndexed { i, chip -> chip.setOnClickListener { selectedEffect = effectKeys[i]; renderChips() } }
            renderChips()

            // Preview plays the trimmed range at the chosen volume (effects are applied on upload).
            sheet.btnPreview.setOnClickListener {
                runCatching {
                    if (tempPlayer?.isPlaying == true) {
                        tempPlayer?.pause()
                        setPreviewButton(sheet.btnPreview, playing = false)
                    } else {
                        tempPlayer?.seekTo(trim.values[0].toInt())
                        tempPlayer?.start()
                        setPreviewButton(sheet.btnPreview, playing = true)
                    }
                }.onFailure {
                    Toast.makeText(this@WriterAudioUploadActivity, "Unable to preview audio", Toast.LENGTH_SHORT).show()
                }
            }

            sheet.btnDone.setOnClickListener {
                val trimStart = trim.values[0].toLong()
                val trimEnd = trim.values[1].toLong()
                val vol = sheet.sliderVolume.value

                if (isNew) {
                    tracks.add(TrackModel(
                        uri = uri, name = fileName, durationMs = duration,
                        trimStartMs = trimStart, trimEndMs = trimEnd,
                        volume = vol, effect = selectedEffect
                    ))
                } else if (existingTrack != null) {
                    existingTrack.trimStartMs = trimStart
                    existingTrack.trimEndMs = trimEnd
                    existingTrack.volume = vol
                    existingTrack.effect = selectedEffect
                }

                renderTracks()
                updateEmptyState()
                dialog.dismiss()
            }

            if (!isNew && existingTrack != null) {
                sheet.btnRemove.visibility = View.VISIBLE
                sheet.btnRemove.setOnClickListener {
                    tracks.remove(existingTrack)
                    renderTracks()
                    updateEmptyState()
                    dialog.dismiss()
                }
            }
        }

        tempPlayer?.setOnCompletionListener { setPreviewButton(sheet.btnPreview, playing = false) }

        tempPlayer?.setOnErrorListener { mp, _, _ ->
            if (audioSetupPlayer === mp && !isFinishing && !isDestroyed) {
                Toast.makeText(
                    this@WriterAudioUploadActivity,
                    "Unable to load this audio file",
                    Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
            }
            true
        }

        dialog.setOnDismissListener {
            if (audioSetupDialog === dialog) {
                audioSetupDialog = null
            }
            if (audioSetupPlayer === tempPlayer) {
                audioSetupPlayer = null
            }
            tempPlayer?.setOnPreparedListener(null)
            tempPlayer?.setOnErrorListener(null)
            tempPlayer?.setOnCompletionListener(null)
            runCatching { tempPlayer?.release() }
            tempPlayer = null
        }
        dialog.show()

        try {
            tempPlayer?.setDataSource(this, uri)
            tempPlayer?.prepareAsync()
        } catch (_: Exception) {
            Toast.makeText(this, "Error loading audio", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
    }

    private fun setPreviewButton(button: TextView, playing: Boolean) {
        button.text = if (playing) "Pause" else "Preview"
        val icon = ContextCompat.getDrawable(this, if (playing) R.drawable.ic_player_pause else R.drawable.ic_player_play)
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
    }

    private fun releaseAudioSetupResources(dismissDialog: Boolean) {
        val dialog = audioSetupDialog
        val player = audioSetupPlayer
        audioSetupDialog = null
        audioSetupPlayer = null

        player?.setOnPreparedListener(null)
        player?.setOnErrorListener(null)
        runCatching { player?.release() }

        if (dismissDialog && dialog?.isShowing == true) {
            dialog.setOnDismissListener(null)
            runCatching { dialog.dismiss() }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun renderTracks() {
        binding.trackContainer.removeAllViews()
        
        tracks.forEach { track ->
            val trackView = layoutInflater.inflate(com.writer_hub.novelserise.R.layout.item_studio_track, binding.trackContainer, false)
            val bindingItem = ItemStudioTrackBinding.bind(trackView)
            
            bindingItem.tvTrackName.text = track.name
            bindingItem.waveform.seed = track.id.hashCode()
            bindingItem.cardTrack.setBackgroundResource(if (track.isLocked) R.drawable.bg_studio_clip_locked else R.drawable.bg_studio_clip)
            bindingItem.tvTrackDuration.text = formatDuration(track.trimEndMs - track.trimStartMs)
            
            val durationSec = (track.trimEndMs - track.trimStartMs) / 1000.0
            val widthPx = (durationSec * pixelsPerSecond * resources.displayMetrics.density).toInt()
            val params = bindingItem.cardTrack.layoutParams as ViewGroup.MarginLayoutParams
            params.width = max(widthPx, (72 * resources.displayMetrics.density).toInt())
            
            val offsetPx = (track.offsetMs / 1000.0 * pixelsPerSecond * resources.displayMetrics.density).toInt()
            params.leftMargin = offsetPx
            bindingItem.cardTrack.layoutParams = params

            bindingItem.ivLock.setImageResource(if (track.isLocked) com.engine.core.R.drawable.ic_lock else R.drawable.ic_lock_open)
            bindingItem.ivLock.setOnClickListener {
                track.isLocked = !track.isLocked
                renderTracks()
            }
            
            bindingItem.cardTrack.setOnClickListener {
                if (!track.isLocked) {
                    showAudioSetupDialog(track.uri, isNew = false, existingTrack = track)
                } else {
                    Toast.makeText(this, "Track is locked", Toast.LENGTH_SHORT).show()
                }
            }
            
            if (!track.isLocked) {
                bindingItem.cardTrack.setOnTouchListener(object : View.OnTouchListener {
                    var dX = 0f
                    override fun onTouch(v: View, event: MotionEvent): Boolean {
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                dX = v.x - event.rawX
                                return true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val newX = event.rawX + dX
                                val newLeft = max(0f, newX).toInt()
                                val lp = v.layoutParams as ViewGroup.MarginLayoutParams
                                lp.leftMargin = newLeft
                                v.layoutParams = lp
                                val density = resources.displayMetrics.density
                                val seconds = newLeft / (pixelsPerSecond * density)
                                track.offsetMs = (seconds * 1000).toLong()
                                return true
                            }
                            MotionEvent.ACTION_UP -> {
                                v.performClick()
                                return true
                            }
                        }
                        return false
                    }
                })
            }
            binding.trackContainer.addView(trackView)
        }
        
        totalDurationMs = tracks.maxOfOrNull { it.offsetMs + (it.trimEndMs - it.trimStartMs) } ?: 60000L
        totalDurationMs = max(totalDurationMs, 10000L)
        binding.tvTotalTime.text = " / " + formatDuration(totalDurationMs)
        
        val requiredWidth = (totalDurationMs / 1000.0 * pixelsPerSecond * resources.displayMetrics.density).toInt() + 1000
        binding.timelineContent.minimumWidth = max(requiredWidth, resources.displayMetrics.widthPixels)
    }

    private fun togglePlayback() {
        if (isPlaying) stopPlayback() else startGlobalPlayback()
    }

    private fun startGlobalPlayback() {
        if (tracks.isEmpty()) return
        AlertUtils.showLoadingDialog(this, "Preparing playback...")
        players.clear()
        var preparedCount = 0
        tracks.forEach { track ->
            val mp = MediaPlayer().apply {
                try {
                    setDataSource(this@WriterAudioUploadActivity, track.uri)
                    setVolume(track.volume, track.volume)
                    prepareAsync()
                } catch (e: Exception) { preparedCount++ }
            }
            mp.setOnPreparedListener {
                preparedCount++
                if (preparedCount == tracks.size) {
                    AlertUtils.dismissLoadingDialog()
                    isPlaying = true
                    binding.btnPlayPauseGlobal.setImageResource(R.drawable.ic_player_pause)
                    playbackHandler.post(playbackRunnable)
                }
            }
            players[track.id] = mp
        }
    }
    
    private fun stopPlayback() {
        isPlaying = false
        binding.btnPlayPauseGlobal.setImageResource(R.drawable.ic_player_play)
        playbackHandler.removeCallbacks(playbackRunnable)
        players.values.forEach { player ->
            runCatching {
                if (player.isPlaying) player.pause()
            }
            runCatching { player.release() }
        }
        players.clear()
    }
    
    private fun updatePlayheadUI() {
        val density = resources.displayMetrics.density
        val x = (playbackTimeMs / 1000.0 * pixelsPerSecond * density).toFloat()
        val scrollX = binding.timelineScrollView.scrollX
        val screenWidth = binding.timelineScrollView.width
        if (isPlaying && x > scrollX + screenWidth * 0.8) {
             binding.timelineScrollView.smoothScrollTo(x.toInt() - (screenWidth/2), 0)
        }
        binding.playhead.translationX = x - binding.playhead.width / 2f
        binding.tvCurrentTime.text = formatDuration(playbackTimeMs)
    }
    
    private fun checkPlayers() {
        tracks.forEach { track ->
            val mp = players[track.id] ?: return@forEach
            val trackStart = track.offsetMs
            val trackDuration = track.trimEndMs - track.trimStartMs
            val trackEnd = trackStart + trackDuration
            if (playbackTimeMs in trackStart..trackEnd) {
                if (!mp.isPlaying) {
                    val seekTo = (playbackTimeMs - trackStart + track.trimStartMs).toInt()
                    mp.seekTo(seekTo)
                    mp.start()
                }
            } else if (mp.isPlaying) mp.pause()
        }
    }

    private fun saveAndUpload() {
        val title = binding.tvToolbarTitle.text.toString().trim()
        if (tracks.isEmpty()) { updateChapterDetails(title) ; return }

        lifecycleScope.launch {
            try {
                AlertUtils.showLoadingDialog(this@WriterAudioUploadActivity, "Rendering Studio Project...")
                val mainTrack = tracks.firstOrNull() ?: return@launch
                val processedFile = AudioStudioHelper.processAudio(
                    context = this@WriterAudioUploadActivity,
                    inputUri = mainTrack.uri,
                    trimStartMs = mainTrack.trimStartMs,
                    trimEndMs = mainTrack.trimEndMs,
                    voiceEffect = mainTrack.effect
                ) { _ -> }

                if (processedFile != null) {
                    startUploadWorker(Uri.fromFile(processedFile), title)
                }
            } catch (e: Exception) { e.printStackTrace() } finally { AlertUtils.dismissLoadingDialog() }
        }
    }
    
    private fun showEditTitleDialog() {
        val dialog = BottomSheetDialog(this, R.style.ThemeOverlay_NovelSerise_TransparentBottomSheet)
        val sheet = DialogEditTitleBinding.inflate(layoutInflater)
        dialog.setContentView(sheet.root)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        sheet.etTitle.setText(binding.tvToolbarTitle.text)
        sheet.etTitle.setSelection(sheet.etTitle.text.length)
        sheet.btnCancel.setOnClickListener { dialog.dismiss() }
        sheet.btnSave.setOnClickListener {
            val newTitle = sheet.etTitle.text.toString().trim()
            if (newTitle.isNotEmpty()) {
                binding.tvToolbarTitle.text = newTitle
                chapter?.chapterName = newTitle
            }
            dialog.dismiss()
        }
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.show()
        sheet.etTitle.requestFocus()
    }

    private fun startUploadWorker(uri: Uri, title: String) {
        val chapterId = chapter?.id ?: return
        val novelId = chapter?.novelId ?: return

        val data = workDataOf(
            AudioUploadWorker.KEY_FILE_URI to uri.toString(),
            AudioUploadWorker.KEY_CHAPTER_ID to chapterId,
            AudioUploadWorker.KEY_NOVEL_ID to novelId,
            AudioUploadWorker.KEY_TITLE to title,
            AudioUploadWorker.KEY_STATUS to (chapter?.status ?: "0"),
            AudioUploadWorker.KEY_IS_LOCKED to if ((chapter?.price?.toIntOrNull() ?: 0) > 0) "1" else "0",
            AudioUploadWorker.KEY_SKIP_COMPRESSION to true,
            AudioUploadWorker.KEY_IMAGE_URL to (chapter?.imageLink ?: "")
        )

        val uploadWorkRequest = OneTimeWorkRequestBuilder<AudioUploadWorker>()
            .setInputData(data)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag("audio_upload")
            .build()

        // Stable per-chapter work survives rotation/process recreation and cannot be cancelled by a
        // duplicate Activity instance enqueueing the same upload again.
        val uniqueWorkName = "audio_upload_${novelId}_$chapterId"
        workManager.enqueueUniqueWork(uniqueWorkName, ExistingWorkPolicy.KEEP, uploadWorkRequest)
        Toast.makeText(this, "Project uploading...", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun updateChapterDetails(title: String) {
        lifecycleScope.launch {
            try {
                AlertUtils.showLoadingDialog(this@WriterAudioUploadActivity, "Updating Title...")
                val result = viewModel.updateChapterDetails(null, chapter?.id!!, "update_name", title, null, null).await()
                if (result == true) finish()
            } catch (e: Exception) { e.printStackTrace() } finally { AlertUtils.dismissLoadingDialog() }
        }
    }

    private fun observeActiveUpload() {
        workManager.getWorkInfosByTagLiveData("audio_upload").observe(this) { workInfos ->
            val workInfo = workInfos.firstOrNull() ?: return@observe
            binding.btnUpload.isEnabled = workInfo.state != WorkInfo.State.RUNNING
            binding.btnUpload.alpha = if (workInfo.state == WorkInfo.State.RUNNING) 0.6f else 1f
            binding.tvUploadLabel.text = if (workInfo.state == WorkInfo.State.RUNNING) "Uploading..." else "Upload Project"
        }
    }

    private fun getFileName(uri: Uri): String? {
        if (uri.scheme == "file" || uri.scheme == "http" || uri.scheme == "https") return File(uri.path ?: "").name.ifBlank { uri.lastPathSegment }
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { if (it.moveToFirst()) { val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME) ; if (idx != -1) name = it.getString(idx) } }
        return name
    }
    
    private fun formatDuration(duration: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(duration)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(duration) - TimeUnit.MINUTES.toSeconds(minutes)
        return String.format("%02d:%02d", minutes, seconds)
    }

    fun getThemeColor(context: Context, attr: Int): Int {
        val typedValue = TypedValue() ; context.theme.resolveAttribute(attr, typedValue, true) ; return typedValue.data
    }

    override fun onPause() {
        if (players.isNotEmpty() || isPlaying) stopPlayback()
        super.onPause()
    }

    override fun onDestroy() {
        releaseAudioSetupResources(dismissDialog = true)
        AlertUtils.dismissLoadingDialog()
        stopPlayback()
        super.onDestroy()
    }
}
