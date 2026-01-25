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
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.voicenotes.motorcycle.database.Recording
import com.voicenotes.motorcycle.database.RecordingDatabase
import com.voicenotes.motorcycle.database.V2SStatus
import com.voicenotes.motorcycle.database.OsmStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RecordingManagerActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var adapter: RecordingAdapter
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recording_manager)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Recording Manager"

        recyclerView = findViewById(R.id.recyclerView)
        emptyView = findViewById(R.id.emptyView)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = RecordingAdapter(
            onPlayClick = { recording -> playRecording(recording) },
            onTranscribeClick = { recording -> transcribeRecording(recording) },
            onCreateOsmClick = { recording -> createOsmNote(recording) },
            onDeleteClick = { recording -> deleteRecording(recording) },
            onDownloadClick = { recording -> downloadRecording(recording) }
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
                    adapter.submitList(recordings)
                }
            }
        }
    }

    private fun playRecording(recording: Recording) {
        try {
            // Stop any currently playing audio
            mediaPlayer?.release()

            mediaPlayer = MediaPlayer().apply {
                setDataSource(recording.filepath)
                prepare()
                start()
                setOnCompletionListener {
                    it.release()
                    mediaPlayer = null
                    Toast.makeText(this@RecordingManagerActivity, "Playback finished", Toast.LENGTH_SHORT).show()
                }
            }
            Toast.makeText(this, "Playing recording...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("RecordingManager", "Error playing recording", e)
            Toast.makeText(this, "Error playing recording: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun transcribeRecording(recording: Recording) {
        // Check if transcription is already complete
        if (recording.v2sStatus == V2SStatus.COMPLETED) {
            Toast.makeText(this, "Already transcribed", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if already processing
        if (recording.v2sStatus == V2SStatus.PROCESSING) {
            Toast.makeText(this, "Transcription in progress...", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Starting transcription...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                val db = RecordingDatabase.getDatabase(this@RecordingManagerActivity)
                val updated = recording.copy(
                    v2sStatus = V2SStatus.PROCESSING,
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

    private fun createOsmNote(recording: Recording) {
        // Check if OSM note is already created
        if (recording.osmStatus == OsmStatus.COMPLETED) {
            if (recording.osmResult != null) {
                // Open the OSM note URL
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(recording.osmResult))
                    startActivity(intent)
                    return
                } catch (e: Exception) {
                    Toast.makeText(this, "OSM note already created", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            Toast.makeText(this, "OSM note already created", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if already processing
        if (recording.osmStatus == OsmStatus.PROCESSING) {
            Toast.makeText(this, "OSM note creation in progress...", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if transcription is available
        if (recording.v2sResult.isNullOrBlank()) {
            Toast.makeText(this, "Please transcribe first", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Creating OSM note...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                val db = RecordingDatabase.getDatabase(this@RecordingManagerActivity)
                val updated = recording.copy(
                    osmStatus = OsmStatus.PROCESSING,
                    updatedAt = System.currentTimeMillis()
                )
                db.recordingDao().updateRecording(updated)

                // Start batch processing service for OSM note creation only
                val intent = Intent(this@RecordingManagerActivity, BatchProcessingService::class.java)
                intent.putExtra("recordingId", recording.id)
                intent.putExtra("osmOnly", true)
                startService(intent)

            } catch (e: Exception) {
                Log.e("RecordingManager", "Error creating OSM note", e)
                Toast.makeText(this@RecordingManagerActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
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
            sb.append("    <name>${recording.filename}</name>\n")
            if (recording.v2sResult != null) {
                sb.append("    <desc>${recording.v2sResult}</desc>\n")
            }
            sb.append("  </wpt>\n")
        }

        sb.append("</gpx>\n")
        return sb.toString()
    }

    private fun generateCSV(recordings: List<Recording>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        val sb = StringBuilder()
        sb.append("Latitude,Longitude,Timestamp,Filename,Transcription,V2S Status,OSM Status\n")

        recordings.forEach { recording ->
            sb.append("${recording.latitude},")
            sb.append("${recording.longitude},")
            sb.append("${dateFormat.format(Date(recording.timestamp))},")
            sb.append("\"${recording.filename}\",")
            sb.append("\"${recording.v2sResult ?: ""}\",")
            sb.append("${recording.v2sStatus},")
            sb.append("${recording.osmStatus}\n")
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
    private val onPlayClick: (Recording) -> Unit,
    private val onTranscribeClick: (Recording) -> Unit,
    private val onCreateOsmClick: (Recording) -> Unit,
    private val onDeleteClick: (Recording) -> Unit,
    private val onDownloadClick: (Recording) -> Unit
) : RecyclerView.Adapter<RecordingAdapter.ViewHolder>() {

    private var recordings = listOf<Recording>()

    fun submitList(newRecordings: List<Recording>) {
        recordings = newRecordings.sortedByDescending { it.timestamp }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recording, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(recordings[position])
    }

    override fun getItemCount() = recordings.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val dateTimeText: TextView = view.findViewById(R.id.dateTimeText)
        private val locationText: TextView = view.findViewById(R.id.locationText)
        private val playIcon: ImageView = view.findViewById(R.id.playIcon)

        private val transcriptionText: TextView = view.findViewById(R.id.transcriptionText)
        private val v2sStatusIcon: ImageView = view.findViewById(R.id.v2sStatusIcon)
        private val v2sProgressBar: ProgressBar = view.findViewById(R.id.v2sProgressBar)
        private val transcribeButton: Button = view.findViewById(R.id.transcribeButton)

        private val osmSection: View = view.findViewById(R.id.osmSection)
        private val osmStatusIcon: ImageView = view.findViewById(R.id.osmStatusIcon)
        private val osmProgressBar: ProgressBar = view.findViewById(R.id.osmProgressBar)
        private val createOsmButton: Button = view.findViewById(R.id.createOsmButton)

        private val deleteButton: Button = view.findViewById(R.id.deleteButton)
        private val downloadButton: Button = view.findViewById(R.id.downloadButton)

        private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

        fun bind(recording: Recording) {
            // Format date and time
            dateTimeText.text = dateFormat.format(Date(recording.timestamp))

            // Format location
            locationText.text = "${String.format("%.6f", recording.latitude)}, ${String.format("%.6f", recording.longitude)}"

            // Play button
            playIcon.setOnClickListener { onPlayClick(recording) }

            // Transcription section
            updateTranscriptionUI(recording)

            // OSM section
            updateOsmUI(recording)

            // Action buttons
            deleteButton.setOnClickListener { onDeleteClick(recording) }
            downloadButton.setOnClickListener { onDownloadClick(recording) }
        }

        private fun updateTranscriptionUI(recording: Recording) {
            // Update transcription text
            if (recording.v2sResult != null && recording.v2sResult.isNotBlank()) {
                transcriptionText.text = recording.v2sResult
                transcriptionText.setTextColor(itemView.context.getColor(android.R.color.black))
                transcriptionText.setTypeface(null, android.graphics.Typeface.NORMAL)
            } else {
                transcriptionText.text = "No transcription yet"
                transcriptionText.setTextColor(itemView.context.getColor(android.R.color.darker_gray))
                transcriptionText.setTypeface(null, android.graphics.Typeface.ITALIC)
            }

            // Update status and button based on V2S status
            when (recording.v2sStatus) {
                V2SStatus.NOT_STARTED -> {
                    v2sStatusIcon.setImageResource(R.drawable.ic_status_not_started)
                    v2sStatusIcon.clearColorFilter()
                    v2sStatusIcon.visibility = View.VISIBLE
                    v2sProgressBar.visibility = View.GONE
                    transcribeButton.text = "Transcribe"
                    transcribeButton.isEnabled = true
                    transcribeButton.setOnClickListener { onTranscribeClick(recording) }
                }
                V2SStatus.PROCESSING -> {
                    v2sStatusIcon.visibility = View.GONE
                    v2sProgressBar.visibility = View.VISIBLE
                    transcribeButton.text = "Processing..."
                    transcribeButton.isEnabled = false
                }
                V2SStatus.COMPLETED -> {
                    v2sStatusIcon.setImageResource(R.drawable.ic_status_completed)
                    v2sStatusIcon.clearColorFilter()
                    v2sStatusIcon.visibility = View.VISIBLE
                    v2sProgressBar.visibility = View.GONE
                    transcribeButton.text = "Completed"
                    transcribeButton.isEnabled = false
                }
                V2SStatus.FALLBACK -> {
                    v2sStatusIcon.setImageResource(R.drawable.ic_status_error)
                    v2sStatusIcon.clearColorFilter()
                    v2sStatusIcon.visibility = View.VISIBLE
                    v2sProgressBar.visibility = View.GONE
                    transcribeButton.text = "Retry"
                    transcribeButton.isEnabled = true
                    transcribeButton.setOnClickListener { onTranscribeClick(recording) }
                }
                V2SStatus.ERROR -> {
                    v2sStatusIcon.setImageResource(R.drawable.ic_status_error)
                    v2sStatusIcon.clearColorFilter()
                    v2sStatusIcon.visibility = View.VISIBLE
                    v2sProgressBar.visibility = View.GONE
                    transcribeButton.text = "Retry"
                    transcribeButton.isEnabled = true
                    transcribeButton.setOnClickListener { onTranscribeClick(recording) }

                    // Show error message if available
                    if (recording.errorMsg != null) {
                        transcriptionText.text = "Error: ${recording.errorMsg}"
                        transcriptionText.setTextColor(itemView.context.getColor(android.R.color.holo_red_dark))
                    }
                }
                V2SStatus.DISABLED -> {
                    v2sStatusIcon.setImageResource(R.drawable.ic_status_not_started)
                    v2sStatusIcon.clearColorFilter()
                    v2sStatusIcon.visibility = View.VISIBLE
                    v2sProgressBar.visibility = View.GONE
                    transcribeButton.text = "Disabled"
                    transcribeButton.isEnabled = false
                }
            }
        }

        private fun updateOsmUI(recording: Recording) {
            // Check if OSM integration is enabled
            val prefs = itemView.context.getSharedPreferences("AppPrefs", android.content.Context.MODE_PRIVATE)
            val osmEnabled = prefs.getBoolean("addOsmNote", false)

            if (!osmEnabled) {
                osmSection.visibility = View.GONE
                return
            }

            osmSection.visibility = View.VISIBLE

            // Update status and button based on OSM status
            when (recording.osmStatus) {
                OsmStatus.NOT_STARTED -> {
                    osmStatusIcon.setImageResource(R.drawable.ic_status_not_started)
                    osmStatusIcon.clearColorFilter()
                    osmStatusIcon.visibility = View.VISIBLE
                    osmProgressBar.visibility = View.GONE
                    createOsmButton.text = "Create Note"
                    createOsmButton.isEnabled = true
                    createOsmButton.setOnClickListener { onCreateOsmClick(recording) }
                }
                OsmStatus.PROCESSING -> {
                    osmStatusIcon.visibility = View.GONE
                    osmProgressBar.visibility = View.VISIBLE
                    createOsmButton.text = "Creating..."
                    createOsmButton.isEnabled = false
                }
                OsmStatus.COMPLETED -> {
                    osmStatusIcon.setImageResource(R.drawable.ic_status_completed)
                    osmStatusIcon.clearColorFilter()
                    osmStatusIcon.visibility = View.VISIBLE
                    osmProgressBar.visibility = View.GONE
                    createOsmButton.text = "View Note"
                    createOsmButton.isEnabled = true
                    createOsmButton.setOnClickListener { onCreateOsmClick(recording) }
                }
                OsmStatus.ERROR -> {
                    osmStatusIcon.setImageResource(R.drawable.ic_status_error)
                    osmStatusIcon.clearColorFilter()
                    osmStatusIcon.visibility = View.VISIBLE
                    osmProgressBar.visibility = View.GONE
                    createOsmButton.text = "Retry"
                    createOsmButton.isEnabled = true
                    createOsmButton.setOnClickListener { onCreateOsmClick(recording) }
                }
                OsmStatus.DISABLED -> {
                    osmStatusIcon.setImageResource(R.drawable.ic_status_not_started)
                    osmStatusIcon.clearColorFilter()
                    osmStatusIcon.visibility = View.VISIBLE
                    osmProgressBar.visibility = View.GONE
                    createOsmButton.text = "Disabled"
                    createOsmButton.isEnabled = false
                }
            }
        }
    }
}
