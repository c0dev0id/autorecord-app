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
            onProcessClick = { recording -> processRecording(recording) },
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
                }
            }
            Toast.makeText(this, "Playing: ${recording.filename}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("RecordingManager", "Error playing recording", e)
            Toast.makeText(this, "Error playing recording: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun processRecording(recording: Recording) {
        // TODO: Implement processing - trigger TranscriptionService and OsmNotesService
        Toast.makeText(this, "Processing: ${recording.filename}", Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch {
            try {
                val db = RecordingDatabase.getDatabase(this@RecordingManagerActivity)
                val updated = recording.copy(
                    v2sStatus = V2SStatus.PROCESSING,
                    updatedAt = System.currentTimeMillis()
                )
                db.recordingDao().updateRecording(updated)
                
                // Start batch processing service for this single file
                val intent = Intent(this@RecordingManagerActivity, BatchProcessingService::class.java)
                intent.putExtra("recordingId", recording.id)
                startService(intent)
                
                Toast.makeText(this@RecordingManagerActivity, "Processing started", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("RecordingManager", "Error starting processing", e)
                Toast.makeText(this@RecordingManagerActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun deleteRecording(recording: Recording) {
        AlertDialog.Builder(this)
            .setTitle("Delete Recording")
            .setMessage("Are you sure you want to delete ${recording.filename}?")
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
            .setTitle("Download Format")
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
                .setTitle("Download All - Select Format")
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
                        Toast.makeText(this@RecordingManagerActivity, "Saved to Downloads/${recording.filename}", Toast.LENGTH_LONG).show()
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
                        Toast.makeText(this@RecordingManagerActivity, "Saved to Downloads/${zipFile.name}", Toast.LENGTH_LONG).show()
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
                    Toast.makeText(this@RecordingManagerActivity, "Saved to Downloads/${gpxFile.name}", Toast.LENGTH_LONG).show()
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
                    Toast.makeText(this@RecordingManagerActivity, "Saved to Downloads/${csvFile.name}", Toast.LENGTH_LONG).show()
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
                    Toast.makeText(this@RecordingManagerActivity, "Saved to Downloads/${zipFile.name}", Toast.LENGTH_LONG).show()
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
    private val onProcessClick: (Recording) -> Unit,
    private val onDeleteClick: (Recording) -> Unit,
    private val onDownloadClick: (Recording) -> Unit
) : RecyclerView.Adapter<RecordingAdapter.ViewHolder>() {
    
    private var recordings = listOf<Recording>()
    
    fun submitList(newRecordings: List<Recording>) {
        recordings = newRecordings
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
        private val filenameText: TextView = view.findViewById(R.id.filenameText)
        private val locationText: TextView = view.findViewById(R.id.locationText)
        private val v2sStatusIcon: ImageView = view.findViewById(R.id.v2sStatusIcon)
        private val osmStatusIcon: ImageView = view.findViewById(R.id.osmStatusIcon)
        private val playButton: Button = view.findViewById(R.id.playButton)
        private val processButton: Button = view.findViewById(R.id.processButton)
        private val deleteButton: Button = view.findViewById(R.id.deleteButton)
        private val downloadButton: Button = view.findViewById(R.id.downloadButton)
        
        fun bind(recording: Recording) {
            filenameText.text = recording.filename
            locationText.text = "${String.format("%.6f", recording.latitude)}, ${String.format("%.6f", recording.longitude)}"
            
            // Set V2S status icon
            when (recording.v2sStatus) {
                V2SStatus.NOT_STARTED -> {
                    v2sStatusIcon.setImageResource(android.R.drawable.ic_menu_help)
                    v2sStatusIcon.contentDescription = "Not transcribed"
                }
                V2SStatus.PROCESSING -> {
                    v2sStatusIcon.setImageResource(android.R.drawable.ic_popup_sync)
                    v2sStatusIcon.contentDescription = "Transcribing"
                }
                V2SStatus.COMPLETED -> {
                    v2sStatusIcon.setImageResource(android.R.drawable.checkbox_on_background)
                    v2sStatusIcon.contentDescription = "Transcribed"
                }
                V2SStatus.FALLBACK -> {
                    v2sStatusIcon.setImageResource(android.R.drawable.ic_dialog_alert)
                    v2sStatusIcon.contentDescription = "Partial transcription"
                }
                V2SStatus.ERROR -> {
                    v2sStatusIcon.setImageResource(android.R.drawable.ic_delete)
                    v2sStatusIcon.contentDescription = "Transcription error"
                }
                V2SStatus.DISABLED -> {
                    v2sStatusIcon.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                    v2sStatusIcon.contentDescription = "Transcription disabled"
                }
            }
            
            // Set OSM status icon
            when (recording.osmStatus) {
                OsmStatus.NOT_STARTED -> {
                    osmStatusIcon.setImageResource(android.R.drawable.ic_menu_help)
                    osmStatusIcon.contentDescription = "OSM note not created"
                }
                OsmStatus.PROCESSING -> {
                    osmStatusIcon.setImageResource(android.R.drawable.ic_popup_sync)
                    osmStatusIcon.contentDescription = "Creating OSM note"
                }
                OsmStatus.COMPLETED -> {
                    osmStatusIcon.setImageResource(android.R.drawable.checkbox_on_background)
                    osmStatusIcon.contentDescription = "OSM note created"
                }
                OsmStatus.ERROR -> {
                    osmStatusIcon.setImageResource(android.R.drawable.ic_delete)
                    osmStatusIcon.contentDescription = "OSM note error"
                }
                OsmStatus.DISABLED -> {
                    osmStatusIcon.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                    osmStatusIcon.contentDescription = "OSM disabled"
                }
            }
            
            playButton.setOnClickListener { onPlayClick(recording) }
            processButton.setOnClickListener { onProcessClick(recording) }
            deleteButton.setOnClickListener { onDeleteClick(recording) }
            downloadButton.setOnClickListener { onDownloadClick(recording) }
        }
    }
}
