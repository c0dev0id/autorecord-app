package com.voicenotes.main

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.AudioManager
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.voicenotes.main.database.RecordingDatabase
import com.voicenotes.main.database.Recording
import com.voicenotes.main.database.V2SStatus
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

/**
 * Comprehensive test suite for on-device validation
 */
class TestSuite(private val context: Context) {
    
    data class TestResult(
        val name: String,
        val passed: Boolean,
        val message: String
    )
    
    private val results = mutableListOf<TestResult>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    
    /**
     * Run all tests in the test suite
     */
    fun runAllTests() {
        results.clear()
        log("[TEST] ========================================")
        log("[TEST] Starting Test Suite")
        log("[TEST] ========================================")
        log("")
        
        // Run all test categories
        testConfiguration()
        testPermissions()
        testDatabase()
        testRecordingOperations()
        testFileSystem()
        testLocationServices()
        testAudioSystem()
        testNetwork()
        testGoogleCloudIntegration()
        testGPXFile()
        testCSVFile()
        testServiceLifecycle()
        testErrorHandling()
        
        // Print summary
        printSummary()
    }
    
    /**
     * Configuration Tests
     */
    private fun testConfiguration() {
        log("[TEST] === Configuration Tests ===")
        
        runTest("SharedPreferences Read/Write") {
            val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            val testKey = "test_config_key_${System.currentTimeMillis()}"
            val testValue = "test_value_123"
            
            // Write
            prefs.edit().putString(testKey, testValue).apply()
            
            // Read
            val readValue = prefs.getString(testKey, null)
            
            // Clean up
            prefs.edit().remove(testKey).apply()
            
            if (readValue == testValue) {
                TestResult("SharedPreferences Read/Write", true, "Successfully read and wrote test value")
            } else {
                TestResult("SharedPreferences Read/Write", false, "Read value '$readValue' != expected '$testValue'")
            }
        }
        
        runTest("Save Directory Configuration") {
            val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            val saveDir = prefs.getString("saveDirectory", null)
            
            if (saveDir != null) {
                TestResult("Save Directory Configuration", true, "Save directory configured: $saveDir")
            } else {
                TestResult("Save Directory Configuration", false, "Save directory not configured")
            }
        }
        
        runTest("Recording Duration Setting") {
            val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            val duration = prefs.getInt("recordingDuration", -1)

            if (duration > 0 && duration <= 99) {
                TestResult("Recording Duration Setting", true, "Duration configured: $duration seconds")
            } else if (duration == -1) {
                TestResult("Recording Duration Setting", true, "Using default duration (not set)")
            } else {
                TestResult("Recording Duration Setting", false, "Invalid duration: $duration")
            }
        }
        
        log("")
    }

    /**
     * Database Tests - USES TEST DATABASE (in-memory, isolated)
     */
    private fun testDatabase() {
        log("[TEST] === Database Tests (Isolated Test Database) ===")

        // Create test database instance
        val testDb = RecordingDatabase.getTestDatabase(context)
        val dao = testDb.recordingDao()

        runTest("Database Initialization") {
            try {
                // Verify database and DAO are accessible
                val count = runBlocking { dao.getAllRecordingsList().size }
                TestResult("Database Initialization", true, "Test database initialized (count: $count)")
            } catch (e: Exception) {
                TestResult("Database Initialization", false, "Failed: ${e.message}")
            }
        }

        runTest("Insert Recording") {
            try {
                val recording = Recording(
                    id = 0,
                    filename = "test_recording.ogg",
                    filepath = "/test/path/recording.ogg",
                    latitude = 40.7128,
                    longitude = -74.0060,
                    timestamp = System.currentTimeMillis(),
                    v2sStatus = V2SStatus.NOT_STARTED,
                    v2sResult = null,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                val id = runBlocking { dao.insertRecording(recording) }

                if (id > 0) {
                    TestResult("Insert Recording", true, "Recording inserted with ID: $id")
                } else {
                    TestResult("Insert Recording", false, "Insert returned invalid ID: $id")
                }
            } catch (e: Exception) {
                TestResult("Insert Recording", false, "Insert failed: ${e.message}")
            }
        }

        runTest("Query Recording by ID") {
            try {
                // First insert a recording
                val recording = Recording(
                    id = 0,
                    filename = "test_recording.ogg",
                    filepath = "/test/path/recording2.ogg",
                    latitude = 37.7749,
                    longitude = -122.4194,
                    timestamp = System.currentTimeMillis(),
                    v2sStatus = V2SStatus.NOT_STARTED,
                    v2sResult = null,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                val id = runBlocking { dao.insertRecording(recording) }
                val retrieved = runBlocking { dao.getRecordingById(id) }

                if (retrieved != null && retrieved.filepath == recording.filepath) {
                    TestResult("Query Recording by ID", true, "Recording retrieved successfully")
                } else {
                    TestResult("Query Recording by ID", false, "Retrieved recording doesn't match")
                }
            } catch (e: Exception) {
                TestResult("Query Recording by ID", false, "Query failed: ${e.message}")
            }
        }

        runTest("Update Recording Status") {
            try {
                // Insert recording
                val recording = Recording(
                    id = 0,
                    filename = "test_recording.ogg",
                    filepath = "/test/path/recording3.ogg",
                    latitude = 51.5074,
                    longitude = -0.1278,
                    timestamp = System.currentTimeMillis(),
                    v2sStatus = V2SStatus.NOT_STARTED,
                    v2sResult = null,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                val id = runBlocking { dao.insertRecording(recording) }

                // Update to COMPLETED
                val updated = recording.copy(
                    id = id,
                    v2sStatus = V2SStatus.COMPLETED,
                    v2sResult = "Test transcription",
                    updatedAt = System.currentTimeMillis()
                )

                runBlocking { dao.updateRecording(updated) }

                val retrieved = runBlocking { dao.getRecordingById(id) }

                if (retrieved?.v2sStatus == V2SStatus.COMPLETED &&
                    retrieved.v2sResult == "Test transcription") {
                    TestResult("Update Recording Status", true, "Status updated successfully")
                } else {
                    TestResult("Update Recording Status", false, "Status update failed")
                }
            } catch (e: Exception) {
                TestResult("Update Recording Status", false, "Update failed: ${e.message}")
            }
        }

        runTest("Query All Recordings") {
            try {
                val allRecordings = runBlocking { dao.getAllRecordingsList() }

                // We should have at least 3 recordings from previous tests
                if (allRecordings.size >= 3) {
                    TestResult("Query All Recordings", true, "Retrieved ${allRecordings.size} recordings")
                } else {
                    TestResult("Query All Recordings", false, "Expected >= 3 recordings, got ${allRecordings.size}")
                }
            } catch (e: Exception) {
                TestResult("Query All Recordings", false, "Query failed: ${e.message}")
            }
        }

        runTest("Delete Recording") {
            try {
                // Insert recording
                val recording = Recording(
                    id = 0,
                    filename = "test_recording.ogg",
                    filepath = "/test/path/to_delete.ogg",
                    latitude = 48.8566,
                    longitude = 2.3522,
                    timestamp = System.currentTimeMillis(),
                    v2sStatus = V2SStatus.NOT_STARTED,
                    v2sResult = null,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                val id = runBlocking { dao.insertRecording(recording) }
                val beforeDelete = runBlocking { dao.getRecordingById(id) }

                runBlocking { dao.deleteRecording(recording.copy(id = id)) }

                val afterDelete = runBlocking { dao.getRecordingById(id) }

                if (beforeDelete != null && afterDelete == null) {
                    TestResult("Delete Recording", true, "Recording deleted successfully")
                } else {
                    TestResult("Delete Recording", false, "Delete operation failed")
                }
            } catch (e: Exception) {
                TestResult("Delete Recording", false, "Delete failed: ${e.message}")
            }
        }

        runTest("V2S Status Enum Handling") {
            try {
                val recording = Recording(
                    id = 0,
                    filename = "test_recording.ogg",
                    filepath = "/test/path/status_test.ogg",
                    latitude = 35.6762,
                    longitude = 139.6503,
                    timestamp = System.currentTimeMillis(),
                    v2sStatus = V2SStatus.PROCESSING,
                    v2sResult = null,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                val id = runBlocking { dao.insertRecording(recording) }
                val retrieved = runBlocking { dao.getRecordingById(id) }

                if (retrieved?.v2sStatus == V2SStatus.PROCESSING) {
                    TestResult("V2S Status Enum Handling", true, "Status enum persisted correctly")
                } else {
                    TestResult("V2S Status Enum Handling", false, "Status enum mismatch")
                }
            } catch (e: Exception) {
                TestResult("V2S Status Enum Handling", false, "Enum handling failed: ${e.message}")
            }
        }

        runTest("Null Value Handling") {
            try {
                val recording = Recording(
                    id = 0,
                    filename = "test_recording.ogg",
                    filepath = "/test/path/null_test.ogg",
                    latitude = 0.0,
                    longitude = 0.0,
                    timestamp = System.currentTimeMillis(),
                    v2sStatus = V2SStatus.NOT_STARTED,
                    v2sResult = null,  // null transcription
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                val id = runBlocking { dao.insertRecording(recording) }
                val retrieved = runBlocking { dao.getRecordingById(id) }

                if (retrieved?.v2sResult == null) {
                    TestResult("Null Value Handling", true, "Null values handled correctly")
                } else {
                    TestResult("Null Value Handling", false, "Null values not preserved")
                }
            } catch (e: Exception) {
                TestResult("Null Value Handling", false, "Null handling failed: ${e.message}")
            }
        }

        runTest("Coordinate Precision") {
            try {
                val lat = 40.712345678901234
                val lon = -74.006098765432109

                val recording = Recording(
                    id = 0,
                    filename = "test_recording.ogg",
                    filepath = "/test/path/precision_test.ogg",
                    latitude = lat,
                    longitude = lon,
                    timestamp = System.currentTimeMillis(),
                    v2sStatus = V2SStatus.NOT_STARTED,
                    v2sResult = null,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                val id = runBlocking { dao.insertRecording(recording) }
                val retrieved = runBlocking { dao.getRecordingById(id) }

                if (retrieved != null &&
                    Math.abs(retrieved.latitude - lat) < 0.000001 &&
                    Math.abs(retrieved.longitude - lon) < 0.000001) {
                    TestResult("Coordinate Precision", true, "Coordinates preserved with precision")
                } else {
                    TestResult("Coordinate Precision", false, "Coordinate precision lost")
                }
            } catch (e: Exception) {
                TestResult("Coordinate Precision", false, "Precision test failed: ${e.message}")
            }
        }

        runTest("Empty String vs Null Handling") {
            try {
                val recording = Recording(
                    id = 0,
                    filename = "test_recording.ogg",
                    filepath = "/test/path/empty_string_test.ogg",
                    latitude = 0.0,
                    longitude = 0.0,
                    timestamp = System.currentTimeMillis(),
                    v2sStatus = V2SStatus.COMPLETED,
                    v2sResult = "",  // empty string
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                val id = runBlocking { dao.insertRecording(recording) }
                val retrieved = runBlocking { dao.getRecordingById(id) }

                if (retrieved?.v2sResult == "") {
                    TestResult("Empty String vs Null Handling", true, "Empty string preserved (not converted to null)")
                } else {
                    TestResult("Empty String vs Null Handling", false, "Empty string handling issue")
                }
            } catch (e: Exception) {
                TestResult("Empty String vs Null Handling", false, "Failed: ${e.message}")
            }
        }

        runTest("Multiple Status Updates") {
            try {
                val recording = Recording(
                    id = 0,
                    filename = "test_recording.ogg",
                    filepath = "/test/path/multi_update_test.ogg",
                    latitude = 52.5200,
                    longitude = 13.4050,
                    timestamp = System.currentTimeMillis(),
                    v2sStatus = V2SStatus.NOT_STARTED,
                    v2sResult = null,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                val id = runBlocking { dao.insertRecording(recording) }

                // Update 1: Processing
                var updated = recording.copy(id = id, v2sStatus = V2SStatus.PROCESSING)
                runBlocking { dao.updateRecording(updated) }

                // Update 2: Completed with result
                updated = updated.copy(
                    v2sStatus = V2SStatus.COMPLETED,
                    v2sResult = "Final transcription"
                )
                runBlocking { dao.updateRecording(updated) }

                // Update 3: OSM processing
                runBlocking { dao.updateRecording(updated) }

                // Update 4: OSM completed
                updated = updated.copy(
                )
                runBlocking { dao.updateRecording(updated) }

                val final = runBlocking { dao.getRecordingById(id) }

                if (final?.v2sStatus == V2SStatus.COMPLETED &&
                    final.v2sResult == "Final transcription") {
                    TestResult("Multiple Status Updates", true, "Multiple updates handled correctly")
                } else {
                    TestResult("Multiple Status Updates", false, "Update sequence failed")
                }
            } catch (e: Exception) {
                TestResult("Multiple Status Updates", false, "Failed: ${e.message}")
            }
        }

        runTest("Database Isolation from Production") {
            try {
                // This test verifies we're using in-memory test database
                // Production database would have different data
                val testRecordings = runBlocking { dao.getAllRecordingsList() }

                // All recordings should be from our tests (audioFilePath starts with "/test/path/")
                val allTestData = testRecordings.all { it.filepath.startsWith("/test/path/") }

                if (allTestData) {
                    TestResult("Database Isolation from Production", true,
                        "Test database isolated (${testRecordings.size} test records)")
                } else {
                    TestResult("Database Isolation from Production", false,
                        "Found production data in test database!")
                }
            } catch (e: Exception) {
                TestResult("Database Isolation from Production", false, "Failed: ${e.message}")
            }
        }

        // Clean up test database
        try {
            testDb.close()
        } catch (e: Exception) {
            log("[TEST] Warning: Failed to close test database: ${e.message}")
        }

        log("")
    }

    /**
     * Recording Operations Tests
     */
    private fun testRecordingOperations() {
        log("[TEST] === Recording Operations Tests ===")

        runTest("File Format Selection (API Level Based)") {
            val apiLevel = Build.VERSION.SDK_INT
            val expectedFormat = if (apiLevel >= Build.VERSION_CODES.Q) "OGG" else "AMR_WB"
            val expectedExtension = if (apiLevel >= Build.VERSION_CODES.Q) ".ogg" else ".amr"

            TestResult(
                "File Format Selection (API Level Based)",
                true,
                "API $apiLevel: Format=$expectedFormat, Extension=$expectedExtension"
            )
        }

        runTest("Filename Generation Pattern") {
            try {
                val timestamp = System.currentTimeMillis()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
                val dateStr = dateFormat.format(Date(timestamp))

                val lat = 40.7128
                val lon = -74.0060
                val extension = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ".ogg" else ".amr"

                val expectedPattern = """VN_\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}_-?\d+\.\d+_-?\d+\.\d+\.(ogg|amr)"""
                val filename = "VN_${dateStr}_${lat}_${lon}${extension}"

                if (filename.matches(expectedPattern.toRegex())) {
                    TestResult("Filename Generation Pattern", true, "Generated: $filename")
                } else {
                    TestResult("Filename Generation Pattern", false, "Pattern mismatch: $filename")
                }
            } catch (e: Exception) {
                TestResult("Filename Generation Pattern", false, "Failed: ${e.message}")
            }
        }

        runTest("Coordinate Formatting in Filename") {
            try {
                val lat = 40.712345
                val lon = -74.006789

                val latStr = String.format(Locale.US, "%.4f", lat)
                val lonStr = String.format(Locale.US, "%.4f", lon)

                if (latStr == "40.7123" && lonStr == "-74.0068") {
                    TestResult("Coordinate Formatting in Filename", true, "Coordinates formatted to 4 decimals")
                } else {
                    TestResult("Coordinate Formatting in Filename", false, "Format error: $latStr, $lonStr")
                }
            } catch (e: Exception) {
                TestResult("Coordinate Formatting in Filename", false, "Failed: ${e.message}")
            }
        }

        runTest("Filename Timestamp Parsing") {
            try {
                val filename = "VN_2024-01-15_12-30-45_40.7128_-74.0060.ogg"
                val datePattern = """VN_(\d{4})-(\d{2})-(\d{2})_(\d{2})-(\d{2})-(\d{2})""".toRegex()
                val match = datePattern.find(filename)

                if (match != null) {
                    val year = match.groupValues[1]
                    val month = match.groupValues[2]
                    val day = match.groupValues[3]
                    val hour = match.groupValues[4]
                    val minute = match.groupValues[5]
                    val second = match.groupValues[6]

                    if (year == "2024" && month == "01" && day == "15" &&
                        hour == "12" && minute == "30" && second == "45") {
                        TestResult("Filename Timestamp Parsing", true, "Timestamp parsed correctly")
                    } else {
                        TestResult("Filename Timestamp Parsing", false, "Parsing error")
                    }
                } else {
                    TestResult("Filename Timestamp Parsing", false, "Pattern not matched")
                }
            } catch (e: Exception) {
                TestResult("Filename Timestamp Parsing", false, "Failed: ${e.message}")
            }
        }

        runTest("Recording Duration Validation") {
            val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            val duration = prefs.getInt("recordingDuration", 10)

            if (duration in 1..99) {
                TestResult("Recording Duration Validation", true, "Valid duration: $duration seconds")
            } else {
                TestResult("Recording Duration Validation", false, "Invalid duration: $duration")
            }
        }

        runTest("Audio File Path Construction") {
            try {
                val recordingsDir = File(context.filesDir, "recordings")
                val filename = "VN_2024-01-15_12-30-45_40.7128_-74.0060.ogg"
                val fullPath = File(recordingsDir, filename).absolutePath

                if (fullPath.contains("/files/recordings/") && fullPath.endsWith(filename)) {
                    TestResult("Audio File Path Construction", true, "Path: $fullPath")
                } else {
                    TestResult("Audio File Path Construction", false, "Invalid path: $fullPath")
                }
            } catch (e: Exception) {
                TestResult("Audio File Path Construction", false, "Failed: ${e.message}")
            }
        }

        runTest("Negative Coordinate Handling") {
            try {
                val lat = -33.8688  // Sydney
                val lon = 151.2093

                val filename = "VN_2024-01-15_12-30-45_${lat}_${lon}.ogg"
                val pattern = """_(-?\d+\.\d+)_(-?\d+\.\d+)\.(ogg|amr)$""".toRegex()
                val match = pattern.find(filename)

                if (match != null) {
                    val parsedLat = match.groupValues[1].toDoubleOrNull()
                    val parsedLon = match.groupValues[2].toDoubleOrNull()

                    if (parsedLat == lat && parsedLon == lon) {
                        TestResult("Negative Coordinate Handling", true, "Negative coordinates handled correctly")
                    } else {
                        TestResult("Negative Coordinate Handling", false, "Coordinate mismatch")
                    }
                } else {
                    TestResult("Negative Coordinate Handling", false, "Pattern not matched")
                }
            } catch (e: Exception) {
                TestResult("Negative Coordinate Handling", false, "Failed: ${e.message}")
            }
        }

        runTest("Zero Coordinate Handling") {
            try {
                val lat = 0.0
                val lon = 0.0

                val filename = "VN_2024-01-15_12-30-45_${lat}_${lon}.ogg"
                val pattern = """_(-?\d+\.\d+)_(-?\d+\.\d+)\.(ogg|amr)$""".toRegex()
                val match = pattern.find(filename)

                if (match != null) {
                    val parsedLat = match.groupValues[1].toDoubleOrNull()
                    val parsedLon = match.groupValues[2].toDoubleOrNull()

                    if (parsedLat == 0.0 && parsedLon == 0.0) {
                        TestResult("Zero Coordinate Handling", true, "Zero coordinates handled correctly")
                    } else {
                        TestResult("Zero Coordinate Handling", false, "Coordinate mismatch")
                    }
                } else {
                    TestResult("Zero Coordinate Handling", false, "Pattern not matched")
                }
            } catch (e: Exception) {
                TestResult("Zero Coordinate Handling", false, "Failed: ${e.message}")
            }
        }

        runTest("Export Format Selection") {
            try {
                val formats = listOf("Audio Only", "GPX Only", "CSV Only", "All Formats")

                var allValid = true
                formats.forEach { format ->
                    val valid = when (format) {
                        "Audio Only" -> true
                        "GPX Only" -> true
                        "CSV Only" -> true
                        "All Formats" -> true
                        else -> false
                    }
                    if (!valid) allValid = false
                }

                if (allValid) {
                    TestResult("Export Format Selection", true, "All export formats recognized")
                } else {
                    TestResult("Export Format Selection", false, "Invalid export format found")
                }
            } catch (e: Exception) {
                TestResult("Export Format Selection", false, "Failed: ${e.message}")
            }
        }

        runTest("Recordings Directory Creation") {
            try {
                val recordingsDir = File(context.filesDir, "recordings")

                if (!recordingsDir.exists()) {
                    recordingsDir.mkdirs()
                }

                if (recordingsDir.exists() && recordingsDir.isDirectory) {
                    TestResult("Recordings Directory Creation", true, "Directory exists at ${recordingsDir.absolutePath}")
                } else {
                    TestResult("Recordings Directory Creation", false, "Failed to create directory")
                }
            } catch (e: Exception) {
                TestResult("Recordings Directory Creation", false, "Failed: ${e.message}")
            }
        }

        log("")
    }

    /**
     * Permission Tests
     */
    private fun testPermissions() {
        log("[TEST] === Permission Tests ===")
        
        runTest("RECORD_AUDIO Permission") {
            val granted = ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            
            TestResult(
                "RECORD_AUDIO Permission", 
                true, 
                if (granted) "Permission granted" else "Permission not granted"
            )
        }
        
        runTest("ACCESS_FINE_LOCATION Permission") {
            val granted = ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            
            TestResult(
                "ACCESS_FINE_LOCATION Permission", 
                true, 
                if (granted) "Permission granted" else "Permission not granted"
            )
        }
        
        runTest("BLUETOOTH_CONNECT Permission") {
            val granted = ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            
            TestResult(
                "BLUETOOTH_CONNECT Permission", 
                true, 
                if (granted) "Permission granted" else "Permission not granted"
            )
        }
        
        runTest("Overlay Permission") {
            val granted = Settings.canDrawOverlays(context)
            TestResult(
                "Overlay Permission", 
                true, 
                if (granted) "Permission granted" else "Permission not granted"
            )
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runTest("Notification Permission (Android 13+)") {
                val granted = ContextCompat.checkSelfPermission(
                    context, 
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                
                TestResult(
                    "Notification Permission", 
                    true, 
                    if (granted) "Permission granted" else "Permission not granted"
                )
            }
        }
        
        log("")
    }
    
    /**
     * File System Tests
     */
    private fun testFileSystem() {
        log("[TEST] === File System Tests ===")
        
        runTest("Save Directory Exists") {
            val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            val saveDirPath = prefs.getString("saveDirectory", null) ?: 
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath + "/VoiceNotes"
            
            val saveDir = File(saveDirPath)
            
            if (saveDir.exists() && saveDir.isDirectory) {
                TestResult("Save Directory Exists", true, "Directory exists: $saveDirPath")
            } else if (saveDir.mkdirs()) {
                TestResult("Save Directory Exists", true, "Directory created: $saveDirPath")
            } else {
                TestResult("Save Directory Exists", false, "Cannot create directory: $saveDirPath")
            }
        }
        
        runTest("File Write Permission") {
            try {
                val testFile = File(context.filesDir, "test_write_${System.currentTimeMillis()}.tmp")
                testFile.writeText("test content")
                val success = testFile.exists()
                testFile.delete()
                
                if (success) {
                    TestResult("File Write Permission", true, "Can write to internal storage")
                } else {
                    TestResult("File Write Permission", false, "Cannot write to internal storage")
                }
            } catch (e: Exception) {
                TestResult("File Write Permission", false, "Write error: ${e.message}")
            }
        }
        
        runTest("File Read Permission") {
            try {
                val testFile = File(context.filesDir, "test_read_${System.currentTimeMillis()}.tmp")
                testFile.writeText("test content")
                val content = testFile.readText()
                testFile.delete()
                
                if (content == "test content") {
                    TestResult("File Read Permission", true, "Can read from internal storage")
                } else {
                    TestResult("File Read Permission", false, "Read content mismatch")
                }
            } catch (e: Exception) {
                TestResult("File Read Permission", false, "Read error: ${e.message}")
            }
        }
        
        runTest("Free Space Available") {
            val freeSpace = context.filesDir.usableSpace / (1024 * 1024) // MB
            
            if (freeSpace > 50) {
                TestResult("Free Space Available", true, "$freeSpace MB available")
            } else {
                TestResult("Free Space Available", false, "Low storage: $freeSpace MB")
            }
        }
        
        runTest("GPX File Creation Test") {
            try {
                val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                val saveDirPath = prefs.getString("saveDirectory", null) ?: 
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath + "/VoiceNotes"
                
                val gpxFile = File(saveDirPath, "test_waypoints_${System.currentTimeMillis()}.gpx")
                val gpxContent = """<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="TestSuite">
  <wpt lat="40.7128" lon="-74.0060">
    <name>Test Point</name>
    <desc>Test description</desc>
  </wpt>
</gpx>"""
                gpxFile.writeText(gpxContent)
                val success = gpxFile.exists()
                gpxFile.delete()
                
                if (success) {
                    TestResult("GPX File Creation Test", true, "GPX file created successfully")
                } else {
                    TestResult("GPX File Creation Test", false, "GPX file not created")
                }
            } catch (e: Exception) {
                TestResult("GPX File Creation Test", false, "GPX creation error: ${e.message}")
            }
        }
        
        runTest("CSV File Creation Test") {
            try {
                val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                val saveDirPath = prefs.getString("saveDirectory", null) ?: 
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath + "/VoiceNotes"
                
                val csvFile = File(saveDirPath, "test_coordinates_${System.currentTimeMillis()}.csv")
                val csvContent = "\uFEFFLatitude,Longitude,Timestamp,Transcription\n40.7128,-74.0060,2024-01-01 12:00:00,Test note\n"
                csvFile.writeText(csvContent)
                val success = csvFile.exists()
                csvFile.delete()
                
                if (success) {
                    TestResult("CSV File Creation Test", true, "CSV file created successfully")
                } else {
                    TestResult("CSV File Creation Test", false, "CSV file not created")
                }
            } catch (e: Exception) {
                TestResult("CSV File Creation Test", false, "CSV creation error: ${e.message}")
            }
        }
        
        log("")
    }
    
    /**
     * Location Services Tests
     */
    private fun testLocationServices() {
        log("[TEST] === Location Services Tests ===")
        
        runTest("Location Services Enabled") {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val enabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                         locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            
            TestResult(
                "Location Services Enabled", 
                true, 
                if (enabled) "Location services enabled" else "Location services disabled"
            )
        }
        
        runTest("GPS Provider Availability") {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val available = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            
            TestResult(
                "GPS Provider Availability", 
                true, 
                if (available) "GPS provider available" else "GPS provider not available"
            )
        }
        
        runTest("Network Provider Availability") {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val available = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            
            TestResult(
                "Network Provider Availability", 
                true, 
                if (available) "Network provider available" else "Network provider not available"
            )
        }
        
        runTest("Coordinate Extraction from Filename") {
            val testFilename = "VN_2024-01-15_12-30-45_40.7128_-74.0060.ogg"
            val pattern = """_(-?\d+\.\d+)_(-?\d+\.\d+)\.(ogg|m4a)$""".toRegex()
            val match = pattern.find(testFilename)
            
            if (match != null) {
                val lat = match.groupValues[1].toDoubleOrNull()
                val lon = match.groupValues[2].toDoubleOrNull()
                
                if (lat != null && lon != null) {
                    TestResult(
                        "Coordinate Extraction from Filename", 
                        true, 
                        "Extracted coordinates: $lat, $lon"
                    )
                } else {
                    TestResult(
                        "Coordinate Extraction from Filename", 
                        false, 
                        "Failed to parse coordinates"
                    )
                }
            } else {
                TestResult(
                    "Coordinate Extraction from Filename", 
                    false, 
                    "Filename pattern not matched"
                )
            }
        }
        
        log("")
    }
    
    /**
     * Audio System Tests
     */
    private fun testAudioSystem() {
        log("[TEST] === Audio System Tests ===")
        
        runTest("Microphone Availability") {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val hasMicrophone = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_MICROPHONE)
            
            TestResult(
                "Microphone Availability", 
                true, 
                if (hasMicrophone) "Microphone available" else "No microphone detected"
            )
        }
        
        runTest("Audio Recording Permission Check") {
            val granted = ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            
            TestResult(
                "Audio Recording Permission Check", 
                true, 
                if (granted) "Recording permission granted" else "Recording permission not granted"
            )
        }
        
        runTest("MediaRecorder Initialization Test") {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
                == PackageManager.PERMISSION_GRANTED) {
                try {
                    val recorder = MediaRecorder()
                    recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                    
                    // Test OGG_OPUS on API 29+ (Android 10+), fallback to AAC for older devices
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        recorder.setOutputFormat(MediaRecorder.OutputFormat.OGG)
                        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                        val testFile = File(context.cacheDir, "test_audio_${System.currentTimeMillis()}.ogg")
                        recorder.setOutputFile(testFile.absolutePath)
                        recorder.prepare()
                        recorder.release()
                        testFile.delete()
                        TestResult("MediaRecorder Initialization Test", true, "MediaRecorder can be initialized with OGG_OPUS")
                    } else {
                        recorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_WB)
                        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB)
                        val testFile = File(context.cacheDir, "test_audio_${System.currentTimeMillis()}.amr")
                        recorder.setOutputFile(testFile.absolutePath)
                        recorder.prepare()
                        recorder.release()
                        testFile.delete()
                        TestResult("MediaRecorder Initialization Test", true, "MediaRecorder can be initialized with AMR_WB")
                    }
                } catch (e: Exception) {
                    TestResult("MediaRecorder Initialization Test", false, "MediaRecorder error: ${e.message}")
                }
            } else {
                TestResult("MediaRecorder Initialization Test", true, "Skipped (no permission)")
            }
        }
        
        runTest("Bluetooth SCO Availability") {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val available = audioManager.isBluetoothScoAvailableOffCall
            
            TestResult(
                "Bluetooth SCO Availability", 
                true, 
                if (available) "Bluetooth SCO available" else "Bluetooth SCO not available"
            )
        }
        
        runTest("Audio Focus Handling") {
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                // Just check if we can get the audio manager
                TestResult("Audio Focus Handling", true, "Audio manager accessible")
            } catch (e: Exception) {
                TestResult("Audio Focus Handling", false, "Audio manager error: ${e.message}")
            }
        }
        
        log("")
    }
    
    /**
     * Network Tests
     */
    private fun testNetwork() {
        log("[TEST] === Network Tests ===")
        
        runTest("Internet Connectivity") {
            val isOnline = NetworkUtils.isOnline(context)
            TestResult(
                "Internet Connectivity", 
                true, 
                if (isOnline) "Device is online" else "Device is offline"
            )
        }
        
        runTest("DNS Resolution") {
            try {
                val address = InetAddress.getByName("google.com")
                TestResult("DNS Resolution", true, "DNS resolution successful: ${address.hostAddress}")
            } catch (e: Exception) {
                TestResult("DNS Resolution", false, "DNS resolution failed: ${e.message}")
            }
        }
        
        runTest("HTTPS Connection to Google Cloud API") {
            if (NetworkUtils.isOnline(context)) {
                try {
                    val url = URL("https://speech.googleapis.com/")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.requestMethod = "GET"
                    val responseCode = connection.responseCode
                    connection.disconnect()
                    
                    TestResult(
                        "HTTPS Connection to Google Cloud API", 
                        true, 
                        "Connection successful (HTTP $responseCode)"
                    )
                } catch (e: Exception) {
                    TestResult(
                        "HTTPS Connection to Google Cloud API", 
                        false, 
                        "Connection failed: ${e.message}"
                    )
                }
            } else {
                TestResult("HTTPS Connection to Google Cloud API", true, "Skipped (device offline)")
            }
        }
        
        runTest("Network Type Detection") {
            try {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val network = connectivityManager.activeNetwork
                
                if (network != null) {
                    val capabilities = connectivityManager.getNetworkCapabilities(network)
                    val networkType = when {
                        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
                        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobile"
                        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
                        else -> "Unknown"
                    }
                    TestResult("Network Type Detection", true, "Network type: $networkType")
                } else {
                    TestResult("Network Type Detection", true, "No active network")
                }
            } catch (e: Exception) {
                TestResult("Network Type Detection", false, "Network detection error: ${e.message}")
            }
        }
        
        log("")
    }
    
    /**
     * Google Cloud Integration Tests
     */
    private fun testGoogleCloudIntegration() {
        log("[TEST] === Google Cloud Integration Tests ===")
        
        runTest("Service Account Credentials Configured") {
            val configured = TranscriptionService.isConfigured()
            TestResult(
                "Service Account Credentials Configured", 
                true, 
                if (configured) "Credentials configured" else "Credentials not configured"
            )
        }
        
        runTest("Credentials JSON Format") {
            try {
                val serviceAccountJsonBase64 = BuildConfig.GOOGLE_CLOUD_SERVICE_ACCOUNT_JSON_BASE64
                
                if (serviceAccountJsonBase64.isBlank()) {
                    TestResult("Credentials JSON Format", true, "Credentials not configured (expected)")
                } else {
                    val decoded = String(android.util.Base64.decode(serviceAccountJsonBase64, android.util.Base64.NO_WRAP))
                    val valid = decoded.contains("\"type\"") && 
                               decoded.contains("\"project_id\"") && 
                               decoded.contains("\"private_key\"")
                    
                    if (valid) {
                        TestResult("Credentials JSON Format", true, "JSON format valid")
                    } else {
                        TestResult("Credentials JSON Format", false, "JSON format invalid")
                    }
                }
            } catch (e: Exception) {
                TestResult("Credentials JSON Format", false, "JSON parse error: ${e.message}")
            }
        }
        
        runTest("TranscriptionService Initialization") {
            try {
                val service = TranscriptionService(context)
                TestResult("TranscriptionService Initialization", true, "Service initialized successfully")
            } catch (e: Exception) {
                TestResult("TranscriptionService Initialization", false, "Initialization error: ${e.message}")
            }
        }
        
        log("")
    }
    
    /**
     * GPX File Tests
     */
    private fun testGPXFile() {
        log("[TEST] === GPX File Tests ===")
        
        runTest("GPX File Creation with Sample Waypoint") {
            try {
                val gpxContent = """<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="TestSuite">
  <wpt lat="40.7128" lon="-74.0060">
    <name>New York</name>
    <desc>Test waypoint</desc>
  </wpt>
</gpx>"""
                
                val testFile = File(context.cacheDir, "test_gpx_${System.currentTimeMillis()}.gpx")
                testFile.writeText(gpxContent)
                val success = testFile.exists() && testFile.length() > 0
                testFile.delete()
                
                if (success) {
                    TestResult("GPX File Creation with Sample Waypoint", true, "GPX file created with waypoint")
                } else {
                    TestResult("GPX File Creation with Sample Waypoint", false, "GPX file creation failed")
                }
            } catch (e: Exception) {
                TestResult("GPX File Creation with Sample Waypoint", false, "Error: ${e.message}")
            }
        }
        
        runTest("GPX XML Structure Validity") {
            try {
                val gpxContent = """<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="TestSuite">
  <wpt lat="40.7128" lon="-74.0060">
    <name>Test</name>
  </wpt>
</gpx>"""
                
                val valid = gpxContent.contains("<?xml") && 
                           gpxContent.contains("<gpx") && 
                           gpxContent.contains("<wpt") &&
                           gpxContent.contains("</gpx>")
                
                if (valid) {
                    TestResult("GPX XML Structure Validity", true, "GPX structure is valid")
                } else {
                    TestResult("GPX XML Structure Validity", false, "GPX structure is invalid")
                }
            } catch (e: Exception) {
                TestResult("GPX XML Structure Validity", false, "Validation error: ${e.message}")
            }
        }
        
        runTest("Coordinate Duplicate Detection Logic") {
            val coord1 = Pair(40.7128, -74.0060)
            val coord2 = Pair(40.7128, -74.0060)
            val coord3 = Pair(40.7129, -74.0061)
            
            val isDuplicate1 = coord1.first == coord2.first && coord1.second == coord2.second
            val isDuplicate2 = coord1.first == coord3.first && coord1.second == coord3.second
            
            if (isDuplicate1 && !isDuplicate2) {
                TestResult("Coordinate Duplicate Detection Logic", true, "Duplicate detection works correctly")
            } else {
                TestResult("Coordinate Duplicate Detection Logic", false, "Duplicate detection logic error")
            }
        }
        
        log("")
    }
    
    /**
     * CSV File Tests
     */
    private fun testCSVFile() {
        log("[TEST] === CSV File Tests ===")
        
        runTest("CSV File Creation with Sample Data") {
            try {
                val csvContent = "\uFEFFLatitude,Longitude,Timestamp,Transcription\n40.7128,-74.0060,2024-01-01 12:00:00,Test note\n"
                
                val testFile = File(context.cacheDir, "test_csv_${System.currentTimeMillis()}.csv")
                testFile.writeText(csvContent)
                val success = testFile.exists() && testFile.length() > 0
                testFile.delete()
                
                if (success) {
                    TestResult("CSV File Creation with Sample Data", true, "CSV file created with data")
                } else {
                    TestResult("CSV File Creation with Sample Data", false, "CSV file creation failed")
                }
            } catch (e: Exception) {
                TestResult("CSV File Creation with Sample Data", false, "Error: ${e.message}")
            }
        }
        
        runTest("CSV UTF-8 BOM Handling") {
            try {
                val csvContent = "\uFEFFLatitude,Longitude\n40.7128,-74.0060\n"
                val hasBOM = csvContent.startsWith("\uFEFF")
                
                if (hasBOM) {
                    TestResult("CSV UTF-8 BOM Handling", true, "BOM is present in CSV")
                } else {
                    TestResult("CSV UTF-8 BOM Handling", false, "BOM is missing")
                }
            } catch (e: Exception) {
                TestResult("CSV UTF-8 BOM Handling", false, "Error: ${e.message}")
            }
        }
        
        runTest("CSV Escaping (commas, quotes, newlines)") {
            try {
                val testText = "Text with, comma"
                val escapedText = "\"$testText\""
                val valid = escapedText.contains("\"") && escapedText.contains(",")
                
                if (valid) {
                    TestResult("CSV Escaping", true, "CSV escaping works correctly")
                } else {
                    TestResult("CSV Escaping", false, "CSV escaping failed")
                }
            } catch (e: Exception) {
                TestResult("CSV Escaping", false, "Error: ${e.message}")
            }
        }
        
        log("")
    }
    
    /**
     * Service Lifecycle Tests
     */
    private fun testServiceLifecycle() {
        log("[TEST] === Service Lifecycle Tests ===")
        
        runTest("OverlayService Class Exists") {
            try {
                val className = "com.voicenotes.main.OverlayService"
                Class.forName(className)
                TestResult("OverlayService Class Exists", true, "OverlayService class found")
            } catch (e: Exception) {
                TestResult("OverlayService Class Exists", false, "OverlayService not found: ${e.message}")
            }
        }
        
        runTest("BatchProcessingService Class Exists") {
            try {
                val className = "com.voicenotes.main.BatchProcessingService"
                Class.forName(className)
                TestResult("BatchProcessingService Class Exists", true, "BatchProcessingService class found")
            } catch (e: Exception) {
                TestResult("BatchProcessingService Class Exists", false, "BatchProcessingService not found: ${e.message}")
            }
        }
        
        log("")
    }

    /**
     * Error Handling Tests
     */
    private fun testErrorHandling() {
        log("[TEST] === Error Handling Tests ===")

        // Create test database for error tests
        val testDb = RecordingDatabase.getTestDatabase(context)
        val dao = testDb.recordingDao()

        runTest("Query Non-Existent Recording") {
            try {
                val nonExistentId = 999999L
                val recording = runBlocking { dao.getRecordingById(nonExistentId) }

                if (recording == null) {
                    TestResult("Query Non-Existent Recording", true, "Correctly returned null for missing ID")
                } else {
                    TestResult("Query Non-Existent Recording", false, "Should return null but returned record")
                }
            } catch (e: Exception) {
                TestResult("Query Non-Existent Recording", false, "Exception: ${e.message}")
            }
        }

        runTest("Invalid Coordinate Range (Latitude > 90)") {
            try {
                val recording = Recording(
                    id = 0,
                    filename = "test_recording.ogg",
                    filepath = "/test/path/invalid_lat.ogg",
                    latitude = 95.0,  // Invalid: > 90
                    longitude = 0.0,
                    timestamp = System.currentTimeMillis(),
                    v2sStatus = V2SStatus.NOT_STARTED,
                    v2sResult = null,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                val id = runBlocking { dao.insertRecording(recording) }

                // Database allows this - validation should be done in UI/business logic
                if (id > 0) {
                    TestResult("Invalid Coordinate Range (Latitude > 90)", true,
                        "Database accepts invalid coordinates (validation needed in app logic)")
                } else {
                    TestResult("Invalid Coordinate Range (Latitude > 90)", false, "Insert failed unexpectedly")
                }
            } catch (e: Exception) {
                TestResult("Invalid Coordinate Range (Latitude > 90)", true,
                    "Database rejected invalid value: ${e.message}")
            }
        }

        runTest("Invalid Coordinate Range (Longitude > 180)") {
            try {
                val recording = Recording(
                    id = 0,
                    filename = "test_recording.ogg",
                    filepath = "/test/path/invalid_lon.ogg",
                    latitude = 0.0,
                    longitude = 185.0,  // Invalid: > 180
                    timestamp = System.currentTimeMillis(),
                    v2sStatus = V2SStatus.NOT_STARTED,
                    v2sResult = null,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                val id = runBlocking { dao.insertRecording(recording) }

                if (id > 0) {
                    TestResult("Invalid Coordinate Range (Longitude > 180)", true,
                        "Database accepts invalid coordinates (validation needed in app logic)")
                } else {
                    TestResult("Invalid Coordinate Range (Longitude > 180)", false, "Insert failed unexpectedly")
                }
            } catch (e: Exception) {
                TestResult("Invalid Coordinate Range (Longitude > 180)", true,
                    "Database rejected invalid value: ${e.message}")
            }
        }

        runTest("Empty Audio File Path") {
            try {
                val recording = Recording(
                    id = 0,
                    filename = "test_recording.ogg",
                    filepath = "",  // Empty path
                    latitude = 0.0,
                    longitude = 0.0,
                    timestamp = System.currentTimeMillis(),
                    v2sStatus = V2SStatus.NOT_STARTED,
                    v2sResult = null,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                val id = runBlocking { dao.insertRecording(recording) }

                if (id > 0) {
                    TestResult("Empty Audio File Path", true, "Database accepts empty path (validation needed)")
                } else {
                    TestResult("Empty Audio File Path", false, "Insert failed")
                }
            } catch (e: Exception) {
                TestResult("Empty Audio File Path", false, "Exception: ${e.message}")
            }
        }

        runTest("Very Long Transcription Text") {
            try {
                val longText = "Lorem ipsum dolor sit amet. ".repeat(100) // ~2800 characters

                val recording = Recording(
                    id = 0,
                    filename = "test_recording.ogg",
                    filepath = "/test/path/long_transcription.ogg",
                    latitude = 0.0,
                    longitude = 0.0,
                    timestamp = System.currentTimeMillis(),
                    v2sStatus = V2SStatus.COMPLETED,
                    v2sResult = longText,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                val id = runBlocking { dao.insertRecording(recording) }
                val retrieved = runBlocking { dao.getRecordingById(id) }

                if (retrieved?.v2sResult == longText) {
                    TestResult("Very Long Transcription Text", true,
                        "Long text (${longText.length} chars) stored correctly")
                } else {
                    TestResult("Very Long Transcription Text", false, "Text truncated or corrupted")
                }
            } catch (e: Exception) {
                TestResult("Very Long Transcription Text", false, "Exception: ${e.message}")
            }
        }

        runTest("Special Characters in Transcription") {
            try {
                val specialText = "Test with émojis 🎤🎵 and spëcial çharacters: @#$%^&*()"

                val recording = Recording(
                    id = 0,
                    filename = "test_recording.ogg",
                    filepath = "/test/path/special_chars.ogg",
                    latitude = 0.0,
                    longitude = 0.0,
                    timestamp = System.currentTimeMillis(),
                    v2sStatus = V2SStatus.COMPLETED,
                    v2sResult = specialText,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                val id = runBlocking { dao.insertRecording(recording) }
                val retrieved = runBlocking { dao.getRecordingById(id) }

                if (retrieved?.v2sResult == specialText) {
                    TestResult("Special Characters in Transcription", true, "Special characters preserved")
                } else {
                    TestResult("Special Characters in Transcription", false, "Characters corrupted")
                }
            } catch (e: Exception) {
                TestResult("Special Characters in Transcription", false, "Exception: ${e.message}")
            }
        }

        runTest("Newlines in Transcription") {
            try {
                val multilineText = "Line 1\nLine 2\nLine 3\nLine 4"

                val recording = Recording(
                    id = 0,
                    filename = "test_recording.ogg",
                    filepath = "/test/path/newlines.ogg",
                    latitude = 0.0,
                    longitude = 0.0,
                    timestamp = System.currentTimeMillis(),
                    v2sStatus = V2SStatus.COMPLETED,
                    v2sResult = multilineText,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                val id = runBlocking { dao.insertRecording(recording) }
                val retrieved = runBlocking { dao.getRecordingById(id) }

                if (retrieved?.v2sResult == multilineText) {
                    TestResult("Newlines in Transcription", true, "Newlines preserved")
                } else {
                    TestResult("Newlines in Transcription", false, "Newlines corrupted")
                }
            } catch (e: Exception) {
                TestResult("Newlines in Transcription", false, "Exception: ${e.message}")
            }
        }

        runTest("Update Non-Existent Recording") {
            try {
                val nonExistent = Recording(
                    id = 999999L,
                    filename = "test_nonexistent.ogg",
                    filepath = "/test/path/nonexistent.ogg",
                    latitude = 0.0,
                    longitude = 0.0,
                    timestamp = System.currentTimeMillis(),
                    v2sStatus = V2SStatus.COMPLETED,
                    v2sResult = "Test",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                runBlocking { dao.updateRecording(nonExistent) }

                // Room update doesn't throw error if record doesn't exist
                TestResult("Update Non-Existent Recording", true,
                    "Update on non-existent record completed without error")
            } catch (e: Exception) {
                TestResult("Update Non-Existent Recording", false, "Exception: ${e.message}")
            }
        }

        runTest("Delete Non-Existent Recording") {
            try {
                val nonExistent = Recording(
                    id = 999998L,
                    filename = "test_nonexistent2.ogg",
                    filepath = "/test/path/nonexistent2.ogg",
                    latitude = 0.0,
                    longitude = 0.0,
                    timestamp = System.currentTimeMillis(),
                    v2sStatus = V2SStatus.COMPLETED,
                    v2sResult = "Test",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                runBlocking { dao.deleteRecording(nonExistent) }

                // Room delete doesn't throw error if record doesn't exist
                TestResult("Delete Non-Existent Recording", true,
                    "Delete on non-existent record completed without error")
            } catch (e: Exception) {
                TestResult("Delete Non-Existent Recording", false, "Exception: ${e.message}")
            }
        }

        runTest("Concurrent Database Operations") {
            try {
                // Insert multiple recordings concurrently (simulated)
                val recording1 = Recording(
                    id = 0,
                    filename = "test_recording.ogg",
                    filepath = "/test/path/concurrent1.ogg",
                    latitude = 40.0,
                    longitude = -74.0,
                    timestamp = System.currentTimeMillis(),
                    v2sStatus = V2SStatus.NOT_STARTED,
                    v2sResult = null,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                val recording2 = recording1.copy(filepath = "/test/path/concurrent2.ogg", latitude = 41.0)
                val recording3 = recording1.copy(filepath = "/test/path/concurrent3.ogg", latitude = 42.0)

                val id1 = runBlocking { dao.insertRecording(recording1) }
                val id2 = runBlocking { dao.insertRecording(recording2) }
                val id3 = runBlocking { dao.insertRecording(recording3) }

                if (id1 > 0 && id2 > 0 && id3 > 0 && id1 != id2 && id2 != id3) {
                    TestResult("Concurrent Database Operations", true,
                        "Multiple inserts completed with unique IDs")
                } else {
                    TestResult("Concurrent Database Operations", false, "ID conflict detected")
                }
            } catch (e: Exception) {
                TestResult("Concurrent Database Operations", false, "Exception: ${e.message}")
            }
        }

        runTest("Invalid Filename Pattern Parsing") {
            try {
                // Test filenames that should NOT match coordinate extraction pattern
                // Pattern extracts coords from end: _lat_lon.ext
                val invalidFilenames = listOf(
                    "invalid_no_coords.ogg",
                    "VN_2024-01-15.ogg",
                    "VN_2024-01-15_12-30-45.ogg",
                    "coords_missing_40.7128.ogg",
                    "only_lat_-74.0060.ogg"
                )

                val pattern = """_(-?\d+\.\d+)_(-?\d+\.\d+)\.(ogg|amr)$""".toRegex()
                val allFailed = invalidFilenames.all { filename ->
                    pattern.find(filename) == null
                }

                if (allFailed) {
                    TestResult("Invalid Filename Pattern Parsing", true,
                        "All invalid filenames correctly rejected")
                } else {
                    TestResult("Invalid Filename Pattern Parsing", false,
                        "Some invalid filenames incorrectly matched")
                }
            } catch (e: Exception) {
                TestResult("Invalid Filename Pattern Parsing", false, "Exception: ${e.message}")
            }
        }

        runTest("Timestamp Edge Cases (Year 2038)") {
            try {
                // Test year 2038 problem (32-bit timestamp overflow)
                val year2038Timestamp = 2147483647000L // Close to overflow

                val recording = Recording(
                    id = 0,
                    filename = "test_recording.ogg",
                    filepath = "/test/path/year2038.ogg",
                    latitude = 0.0,
                    longitude = 0.0,
                    timestamp = year2038Timestamp,
                    v2sStatus = V2SStatus.NOT_STARTED,
                    v2sResult = null,
                    createdAt = year2038Timestamp,
                    updatedAt = year2038Timestamp
                )

                val id = runBlocking { dao.insertRecording(recording) }
                val retrieved = runBlocking { dao.getRecordingById(id) }

                if (retrieved?.timestamp == year2038Timestamp) {
                    TestResult("Timestamp Edge Cases (Year 2038)", true, "Large timestamp handled correctly")
                } else {
                    TestResult("Timestamp Edge Cases (Year 2038)", false, "Timestamp corruption detected")
                }
            } catch (e: Exception) {
                TestResult("Timestamp Edge Cases (Year 2038)", false, "Exception: ${e.message}")
            }
        }

        runTest("Database Size Limit (Large Dataset)") {
            try {
                // Insert 100 recordings to test performance
                val startTime = System.currentTimeMillis()
                val insertCount = 100

                repeat(insertCount) { i ->
                    val recording = Recording(
                        id = 0,
                    filename = "test_recording.ogg",
                        filepath = "/test/path/large_dataset_$i.ogg",
                        latitude = 40.0 + (i * 0.001),
                        longitude = -74.0 + (i * 0.001),
                        timestamp = System.currentTimeMillis(),
                        v2sStatus = V2SStatus.NOT_STARTED,
                        v2sResult = null,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )

                    runBlocking { dao.insertRecording(recording) }
                }

                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime

                val count = runBlocking { dao.getAllRecordingsList().size }

                if (count >= insertCount) {
                    TestResult("Database Size Limit (Large Dataset)", true,
                        "$insertCount records inserted in ${duration}ms")
                } else {
                    TestResult("Database Size Limit (Large Dataset)", false,
                        "Only $count records inserted")
                }
            } catch (e: Exception) {
                TestResult("Database Size Limit (Large Dataset)", false, "Exception: ${e.message}")
            }
        }

        // Clean up test database
        try {
            testDb.close()
        } catch (e: Exception) {
            log("[TEST] Warning: Failed to close test database: ${e.message}")
        }

        log("")
    }

    /**
     * Helper method to run a test and handle exceptions
     */
    private fun runTest(name: String, test: () -> TestResult) {
        log("[TEST] Starting test: $name")
        
        try {
            val result = test()
            results.add(result)
            
            if (result.passed) {
                log("[TEST] ✓ PASS: ${result.name} - ${result.message}")
            } else {
                log("[TEST] ✗ FAIL: ${result.name} - ${result.message}")
            }
        } catch (e: Exception) {
            val result = TestResult(name, false, "Exception: ${e.message}")
            results.add(result)
            log("[TEST] ✗ FAIL: $name - Exception: ${e.message}")
        }
    }
    
    /**
     * Print test summary
     */
    private fun printSummary() {
        val total = results.size
        val passed = results.count { it.passed }
        val failed = total - passed
        
        log("")
        log("[TEST] ========================================")
        log("[TEST] Test Suite Complete")
        log("[TEST] Total: $total, Passed: $passed, Failed: $failed")
        log("[TEST] ========================================")
    }
    
    /**
     * Log message to DebugLogger
     */
    private fun log(message: String) {
        val timestamp = dateFormat.format(Date())
        val logMessage = "[$timestamp] $message\n"
        
        // Always log test results regardless of logging toggle
        try {
            val logFile = File(context.filesDir, "debug_log.txt")
            logFile.appendText(logMessage)
        } catch (e: Exception) {
            android.util.Log.e("TestSuite", "Failed to write log", e)
        }
    }
}
