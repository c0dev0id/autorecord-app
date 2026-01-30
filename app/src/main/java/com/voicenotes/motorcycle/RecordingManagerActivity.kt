package com.voicenotes.motorcycle

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.voicenotes.motorcycle.database.Recording
import com.voicenotes.motorcycle.database.RecordingDatabase
import com.voicenotes.motorcycle.database.V2SStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RecordingManagerActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var adapter: RecordingAdapter
    private var mediaPlayer: MediaPlayer? = null
    private var currentlyPlayingButton: Button? = null
    private var currentlyPlayingFilepath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recording_manager)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Recording Manager"

        recyclerView = findViewById(R.id.recyclerView)
        emptyView = findViewById(R.id.emptyView)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = RecordingAdapter(
            onPlayClick = { recording, btn -> playRecording(recording, btn) },
            onTranscribeClick = { recording -> transcribeRecording(recording) },
            onOpenMapsClick = { recording -> openMaps(recording) },
            onDeleteClick = { recording -> deleteRecording(recording) },
            onDownloadClick = { recording -> downloadRecording(recording) },
            onSaveTranscriptionClick = { recording, newText -> saveTranscriptionText(recording, newText) }
        )
        recyclerView.adapter = adapter

        loadRecordings()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.recording_manager_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_download_all -> {
                showDownloadAllDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadRecordings() {
        lifecycleScope.launch {
            val db = RecordingDatabase.getDatabase(this@RecordingManagerActivity)
            db.recordingDao().getAllRecordingsLiveData().observe(this@RecordingManagerActivity) { recordings ->
                if (recordings.isEmpty()) {
                    recyclerView.visibility = View.GONE
                    emptyView.visibility = View.VISIBLE
                } else {
                    recyclerView.visibility = View.VISIBLE
                    emptyView.visibility = View.GONE
                    adapter.updateData(recordings)
                }
            }
        }
    }

    private fun playRecording(recording: Recording, playButton: Button) {
        try {
            // If already playing this file, stop it
            if (mediaPlayer != null && currentlyPlayingFilepath == recording.filepath) {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null
                playButton.text = getString(R.string.play)
                currentlyPlayingButton = null
                currentlyPlayingFilepath = null
                Toast.makeText(this, getString(R.string.playback_stopped), Toast.LENGTH_SHORT).show()
                return
            }
            
            // Stop any other playing audio
            mediaPlayer?.release()
            currentlyPlayingButton?.text = getString(R.string.play)

            // Start new playback
            mediaPlayer = MediaPlayer().apply {
                setDataSource(recording.filepath)
                prepare()
                start()
                setOnCompletionListener {
                    it.release()
                    mediaPlayer = null
                    currentlyPlayingButton?.text = getString(R.string.play)
                    currentlyPlayingButton = null
                    currentlyPlayingFilepath = null
                    Toast.makeText(this@RecordingManagerActivity, getString(R.string.playback_finished), Toast.LENGTH_SHORT).show()
                }
            }
            
            playButton.text = getString(R.string.stop)
            currentlyPlayingButton = playButton
            currentlyPlayingFilepath = recording.filepath
            Toast.makeText(this, getString(R.string.playing_recording), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("RecordingManager", "Error playing recording", e)
            Toast.makeText(this, "Error playing recording: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun transcribeRecording(recording: Recording) {
        // Check if already processing
        if (recording.v2sStatus == V2SStatus.PROCESSING) {
            Toast.makeText(this, getString(R.string.transcription_in_progress), Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, getString(R.string.starting_transcription), Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                val db = RecordingDatabase.getDatabase(this@RecordingManagerActivity)
                // Clear fallback/error state before starting new transcription
                val updated = recording.copy(
                    v2sStatus = V2SStatus.PROCESSING,
                    v2sFallback = false,
                    errorMsg = null,
                    updatedAt = System.currentTimeMillis()
                )
                db.recordingDao().updateRecording(updated)

                // Start batch processing service for transcription only
                val intent = Intent(this@RecordingManagerActivity, BatchProcessingService::class.java)
                intent.putExtra("recordingId", recording.id)
                intent.putExtra("transcribeOnly", true)
                startService(intent)

            } catch (e: Exception) {
                Log.e("RecordingManager", "Error starting transcription", e)
                Toast.makeText(this@RecordingManagerActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openMaps(recording: Recording) {
        try {
            // Create Google Maps URL with the recording's coordinates
            val mapsUrl = "https://www.google.com/maps?q=${recording.latitude},${recording.longitude}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl))
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("RecordingManager", "Error opening maps", e)
            Toast.makeText(this, "Error opening maps: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveTranscriptionText(recording: Recording, newText: String) {
        val trimmedText = newText.trim()
        if (trimmedText.isEmpty()) {
            Toast.makeText(this, "Transcription cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val db = RecordingDatabase.getDatabase(this@RecordingManagerActivity)
                val updated = recording.copy(
                    v2sResult = trimmedText,
                    v2sStatus = V2SStatus.COMPLETED,
                    updatedAt = System.currentTimeMillis()
                )
                db.recordingDao().updateRecording(updated)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RecordingManagerActivity, "Transcription saved successfully", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("RecordingManager", "Error saving transcription", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RecordingManagerActivity, "Error saving: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun changeTranscriptionText(recording: Recording) {
        // Create an EditText for the dialog
        val editText = EditText(this).apply {
            setText(recording.v2sResult ?: "")
            hint = "Enter transcription text"
            setSingleLine(false)
            maxLines = 5
            setPadding(50, 20, 50, 20)
        }

        AlertDialog.Builder(this)
            .setTitle("Edit Transcription")
            .setMessage("Modify the transcribed text:")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val newText = editText.text.toString().trim()
                if (newText.isNotEmpty()) {
                    lifecycleScope.launch {
                        try {
                            val db = RecordingDatabase.getDatabase(this@RecordingManagerActivity)
                            val updated = recording.copy(
                                v2sResult = newText,
                                v2sStatus = V2SStatus.COMPLETED,
                                updatedAt = System.currentTimeMillis()
                            )
                            db.recordingDao().updateRecording(updated)

                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@RecordingManagerActivity, "Transcription updated successfully", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e("RecordingManager", "Error updating transcription", e)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@RecordingManagerActivity, "Error updating: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                } else {
                    Toast.makeText(this, "Transcription cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteRecording(recording: Recording) {
        AlertDialog.Builder(this)
            .setTitle("Delete Recording")
            .setMessage("Are you sure you want to delete this recording?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    try {
                        // Delete the file
                        val file = File(recording.filepath)
                        if (file.exists()) {
                            file.delete()
                        }

                        // Delete from database
                        val db = RecordingDatabase.getDatabase(this@RecordingManagerActivity)
                        db.recordingDao().deleteRecording(recording)

                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@RecordingManagerActivity, "Recording deleted", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("RecordingManager", "Error deleting recording", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@RecordingManagerActivity, "Error deleting: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun downloadRecording(recording: Recording) {
        val options = arrayOf("Audio", "GPX", "CSV", "All")
        AlertDialog.Builder(this)
            .setTitle("Export Format")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> exportAudio(listOf(recording))
                    1 -> exportGPX(listOf(recording))
                    2 -> exportCSV(listOf(recording))
                    3 -> exportAll(listOf(recording))
                }
            }
            .show()
    }

    private fun showDownloadAllDialog() {
        lifecycleScope.launch {
            // Get all recordings synchronously for export
            val allRecordings = withContext(Dispatchers.IO) {
                val db = RecordingDatabase.getDatabase(this@RecordingManagerActivity)
                db.recordingDao().getAllRecordingsList()
            }

            if (allRecordings.isEmpty()) {
                Toast.makeText(this@RecordingManagerActivity, "No recordings to export", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val options = arrayOf("Audio (ZIP)", "GPX", "CSV", "All (ZIP)")
            AlertDialog.Builder(this@RecordingManagerActivity)
                .setTitle("Export All - Select Format")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> exportAudio(allRecordings)
                        1 -> exportGPX(allRecordings)
                        2 -> exportCSV(allRecordings)
                        3 -> exportAll(allRecordings)
                    }
                }
                .show()
        }
    }

    private fun exportAudio(recordings: List<Recording>) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

                if (recordings.size == 1) {
                    // Single file - just copy it
                    val recording = recordings[0]
                    val outputFile = File(downloadsDir, recording.filename)
                    File(recording.filepath).copyTo(outputFile, overwrite = true)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@RecordingManagerActivity, "Exported to Downloads/${recording.filename}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    // Multiple files - create ZIP
                    val zipFile = File(downloadsDir, "recordings_audio_$timestamp.zip")
                    ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
                        recordings.forEach { recording ->
                            val file = File(recording.filepath)
                            if (file.exists()) {
                                zip.putNextEntry(ZipEntry(recording.filename))
                                file.inputStream().use { it.copyTo(zip) }
                                zip.closeEntry()
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@RecordingManagerActivity, "Exported to Downloads/${zipFile.name}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("RecordingManager", "Error exporting audio", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RecordingManagerActivity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun exportGPX(recordings: List<Recording>) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val gpxFile = File(downloadsDir, "recordings_$timestamp.gpx")

                // Generate GPX file
                val gpxContent = generateGPX(recordings)
                gpxFile.writeText(gpxContent)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RecordingManagerActivity, "Exported to Downloads/${gpxFile.name}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("RecordingManager", "Error exporting GPX", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RecordingManagerActivity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun exportCSV(recordings: List<Recording>) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val csvFile = File(downloadsDir, "recordings_$timestamp.csv")

                // Generate CSV file
                val csvContent = generateCSV(recordings)
                csvFile.writeText(csvContent)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RecordingManagerActivity, "Exported to Downloads/${csvFile.name}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("RecordingManager", "Error exporting CSV", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RecordingManagerActivity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun exportAll(recordings: List<Recording>) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val zipFile = File(downloadsDir, "recordings_all_$timestamp.zip")

                ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
                    // Add audio files
                    recordings.forEach { recording ->
                        val file = File(recording.filepath)
                        if (file.exists()) {
                            zip.putNextEntry(ZipEntry("audio/${recording.filename}"))
                            file.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }

                    // Add GPX file
                    val gpxContent = generateGPX(recordings)
                    zip.putNextEntry(ZipEntry("recordings.gpx"))
                    zip.write(gpxContent.toByteArray())
                    zip.closeEntry()

                    // Add CSV file
                    val csvContent = generateCSV(recordings)
                    zip.putNextEntry(ZipEntry("recordings.csv"))
                    zip.write(csvContent.toByteArray())
                    zip.closeEntry()
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RecordingManagerActivity, "Exported to Downloads/${zipFile.name}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("RecordingManager", "Error exporting all", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RecordingManagerActivity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun generateGPX(recordings: List<Recording>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"VoiceNotes\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")

        recordings.forEach { recording ->
            sb.append("  <wpt lat=\"${recording.latitude}\" lon=\"${recording.longitude}\">\n")
            sb.append("    <time>${dateFormat.format(Date(recording.timestamp))}</time>\n")
            sb.append("    <name>${escapeXml(recording.filename)}</name>\n")
            sb.append("    <desc>${escapeXml(recording.v2sResult ?: "")}</desc>\n")
            sb.append("  </wpt>\n")
        }

        sb.append("</gpx>\n")
        return sb.toString()
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun escapeCsv(text: String): String {
        // Escape CSV values: double any quotes and wrap in quotes
        return "\"${text.replace("\"", "\"\"")}\""
    }

    private fun generateCSV(recordings: List<Recording>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        val sb = StringBuilder()
        sb.append("Latitude,Longitude,Timestamp,Filename,Transcription\n")

        recordings.forEach { recording ->
            sb.append("${recording.latitude},")
            sb.append("${recording.longitude},")
            sb.append("${dateFormat.format(Date(recording.timestamp))},")
            sb.append("${escapeCsv(recording.filename)},")
            sb.append("${escapeCsv(recording.v2sResult ?: "")}\n")
        }

        return sb.toString()
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }
}

class RecordingAdapter(
    private val onPlayClick: (Recording, Button) -> Unit,
    private val onTranscribeClick: (Recording) -> Unit,
    private val onOpenMapsClick: (Recording) -> Unit,
    private val onDeleteClick: (Recording) -> Unit,
    private val onDownloadClick: (Recording) -> Unit,
    private val onSaveTranscriptionClick: (Recording, String) -> Unit
) : RecyclerView.Adapter<RecordingAdapter.ViewHolder>() {

    private var recordings = listOf<Recording>()

    // Add RecordingDiffCallback
    private class RecordingDiffCallback(
        private val oldList: List<Recording>,
        private val newList: List<Recording>
    ) : DiffUtil.Callback() {
        
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size
        
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }
        
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val old = oldList[oldItemPosition]
            val new = newList[newItemPosition]
            return old.v2sResult == new.v2sResult &&
                   old.v2sStatus == new.v2sStatus &&
                   old.updatedAt == new.updatedAt
        }
        
        override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
            val old = oldList[oldItemPosition]
            val new = newList[newItemPosition]
            
            val changes = mutableMapOf<String, Any?>()
            if (old.v2sResult != new.v2sResult) changes["v2sResult"] = new.v2sResult
            if (old.v2sStatus != new.v2sStatus) changes["v2sStatus"] = new.v2sStatus
            
            return if (changes.isNotEmpty()) changes else null
        }
    }

    fun submitList(newRecordings: List<Recording>) {
        val sorted = newRecordings.sortedByDescending { it.timestamp }
        val diffCallback = RecordingDiffCallback(recordings, sorted)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        
        recordings = sorted
        diffResult.dispatchUpdatesTo(this)
    }
    
    fun updateData(newRecordings: List<Recording>) {
        submitList(newRecordings)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recording, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(recordings[position])
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            val recording = recordings[position]
            for (payload in payloads) {
                if (payload is Map<*, *>) {
                    if (payload.containsKey("v2sResult")) {
                        holder.updateTranscriptionText(recording.v2sResult ?: "")
                    }
                    if (payload.containsKey("v2sStatus")) {
                        holder.updateTranscriptionUI(recording)
                    }
                }
            }
        }
    }

    override fun getItemCount() = recordings.size
    
    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        // Cancel any ongoing animations and reset alpha
        holder.resetStatusIcon()
    }

    // Data class to hold status configuration
    private data class StatusConfig(
        val colorRes: Int,
        val drawableRes: Int,
        val textRes: Int,
        val isEnabled: Boolean
    )

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val dateTimeText: TextView = view.findViewById(R.id.dateTimeText)
        private val locationText: TextView = view.findViewById(R.id.locationText)

        private val transcriptionEditText: EditText = view.findViewById(R.id.transcriptionEditText)
        private val saveTranscriptionButton: Button = view.findViewById(R.id.saveTranscriptionButton)
        private val v2sStatusIcon: ImageView = view.findViewById(R.id.v2sStatusIcon)
        private val transcribeButton: Button = view.findViewById(R.id.transcribeButton)

        private val deleteButton: Button = view.findViewById(R.id.deleteButton)
        private val downloadButton: Button = view.findViewById(R.id.downloadButton)
        private val openMapsButton: Button = view.findViewById(R.id.openMapsButton)
        private val playButton: Button = view.findViewById(R.id.playButton)

        private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        
        // Track previous status to determine if we need transition animation
        private var previousStatus: V2SStatus? = null

        fun bind(recording: Recording) {
            // Reset previous status when binding new data to prevent incorrect animations
            previousStatus = recording.v2sStatus
            
            // Format date and time
            dateTimeText.text = dateFormat.format(Date(recording.timestamp))

            // Format location
            locationText.text = "${String.format("%.6f", recording.latitude)}, ${String.format("%.6f", recording.longitude)}"

            // Transcription EditText - populate based on status
            if (recording.v2sResult.isNullOrBlank() && 
                (recording.v2sStatus == V2SStatus.NOT_STARTED || recording.v2sStatus == V2SStatus.DISABLED)) {
                // Not started or disabled: show empty field with hint
                transcriptionEditText.setText("")
                transcriptionEditText.hint = "transcribed text goes here..."
            } else {
                // Show v2sResult (includes fallback placeholder when FALLBACK status)
                transcriptionEditText.setText(recording.v2sResult ?: "")
                transcriptionEditText.hint = "transcribed text goes here..."
            }
            
            // Save transcription button
            saveTranscriptionButton.setOnClickListener {
                val newText = transcriptionEditText.text.toString()
                onSaveTranscriptionClick(recording, newText)
            }

            // Update transcription status for button drawable
            updateTranscriptionUI(recording)

            // Action buttons
            deleteButton.setOnClickListener { onDeleteClick(recording) }
            downloadButton.setOnClickListener { onDownloadClick(recording) }
            openMapsButton.setOnClickListener { onOpenMapsClick(recording) }
            playButton.setOnClickListener { onPlayClick(recording, playButton) }
            
            // Download button visibility: show if recording has been transcoded or has data
            // For now, keeping it hidden by default as per spec (conditionally visible)
            downloadButton.visibility = if (shouldShowDownloadButton(recording)) View.VISIBLE else View.GONE
        }
        
        fun updateTranscriptionText(text: String) {
            // Only update if the EditText doesn't have focus (user isn't editing)
            if (!transcriptionEditText.hasFocus()) {
                transcriptionEditText.setText(text)
            }
        }
        
        private fun shouldShowDownloadButton(recording: Recording): Boolean {
            // Show download button if the recording file exists
            val file = File(recording.filepath)
            return file.exists()
        }
        
        // Helper function to get status configuration
        private fun getStatusConfig(status: V2SStatus): StatusConfig {
            return when (status) {
                V2SStatus.NOT_STARTED -> StatusConfig(
                    R.color.status_not_started,
                    R.drawable.ic_status_not_started,
                    R.string.transcribe,
                    true
                )
                V2SStatus.PROCESSING -> StatusConfig(
                    R.color.status_processing,
                    R.drawable.ic_status_processing,
                    R.string.processing,
                    false
                )
                V2SStatus.COMPLETED -> StatusConfig(
                    R.color.status_completed,
                    R.drawable.ic_status_completed,
                    R.string.retranscribe,
                    true
                )
                V2SStatus.FALLBACK -> StatusConfig(
                    R.color.status_fallback,
                    R.drawable.ic_status_error,
                    R.string.retry,
                    true
                )
                V2SStatus.ERROR -> StatusConfig(
                    R.color.status_error,
                    R.drawable.ic_status_error,
                    R.string.retry,
                    true
                )
                V2SStatus.DISABLED -> StatusConfig(
                    R.color.status_disabled,
                    R.drawable.ic_status_not_started,
                    R.string.disabled,
                    false
                )
            }
        }
        
        // Update status icon with smooth fade transition
        private fun updateStatusIconWithTransition(newStatus: V2SStatus) {
            val context = itemView.context
            val config = getStatusConfig(newStatus)
            
            // Fade out
            v2sStatusIcon.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction {
                    // Update icon and color
                    v2sStatusIcon.setColorFilter(ContextCompat.getColor(context, config.colorRes))
                    v2sStatusIcon.setImageResource(config.drawableRes)
                    
                    // Fade in
                    v2sStatusIcon.animate()
                        .alpha(1f)
                        .setDuration(150)
                        .start()
                }
                .start()
        }

        fun updateTranscriptionUI(recording: Recording) {
            val context = itemView.context
            val config = getStatusConfig(recording.v2sStatus)
            
            // Ensure status icon is visible
            v2sStatusIcon.visibility = View.VISIBLE
            
            // Check if status has changed to apply transition animation
            val statusChanged = previousStatus != null && previousStatus != recording.v2sStatus
            
            // Update status icon with or without animation
            if (statusChanged) {
                updateStatusIconWithTransition(recording.v2sStatus)
            } else {
                v2sStatusIcon.setColorFilter(ContextCompat.getColor(context, config.colorRes))
                v2sStatusIcon.setImageResource(config.drawableRes)
                v2sStatusIcon.alpha = 1f
            }
            
            // Update button state
            transcribeButton.text = context.getString(config.textRes)
            transcribeButton.isEnabled = config.isEnabled
            transcribeButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, config.drawableRes, 0)
            
            // Set click listener for enabled states
            if (config.isEnabled) {
                transcribeButton.setOnClickListener { onTranscribeClick(recording) }
            }
            
            // Update previous status for next check
            previousStatus = recording.v2sStatus
        }
        
        // Reset status icon animation and ensure proper state
        fun resetStatusIcon() {
            v2sStatusIcon.clearAnimation()
            v2sStatusIcon.alpha = 1f
        }
    }
}
