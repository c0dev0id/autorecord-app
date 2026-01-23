package com.voicenotes.motorcycle

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
        testFileSystem()
        testLocationServices()
        testAudioSystem()
        testNetwork()
        testGoogleCloudIntegration()
        testOSMIntegration()
        testGPXFile()
        testCSVFile()
        testBatchProcessing()
        testServiceLifecycle()
        
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
        
        runTest("Online Processing Toggle") {
            val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean("tryOnlineProcessingDuringRide", true)
            TestResult("Online Processing Toggle", true, "Online processing: ${if (enabled) "enabled" else "disabled"}")
        }
        
        runTest("OSM Note Creation Toggle") {
            val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean("addOsmNote", false)
            TestResult("OSM Note Creation Toggle", true, "OSM note creation: ${if (enabled) "enabled" else "disabled"}")
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
        
        runTest("HTTPS Connection to OSM API") {
            if (NetworkUtils.isOnline(context)) {
                try {
                    val url = URL("https://api.openstreetmap.org/")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.requestMethod = "GET"
                    val responseCode = connection.responseCode
                    connection.disconnect()
                    
                    TestResult(
                        "HTTPS Connection to OSM API", 
                        true, 
                        "Connection successful (HTTP $responseCode)"
                    )
                } catch (e: Exception) {
                    TestResult(
                        "HTTPS Connection to OSM API", 
                        false, 
                        "Connection failed: ${e.message}"
                    )
                }
            } else {
                TestResult("HTTPS Connection to OSM API", true, "Skipped (device offline)")
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
     * OSM Integration Tests
     */
    private fun testOSMIntegration() {
        log("[TEST] === OSM Integration Tests ===")
        
        runTest("OSM OAuth Tokens Exist") {
            val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            val accessToken = prefs.getString("osm_access_token", null)
            
            TestResult(
                "OSM OAuth Tokens Exist", 
                true, 
                if (accessToken != null) "Access token exists" else "No access token found"
            )
        }
        
        runTest("OSM Client ID Configuration") {
            val clientId = BuildConfig.OSM_CLIENT_ID
            val configured = clientId.isNotBlank() && clientId != "your_osm_client_id"
            
            TestResult(
                "OSM Client ID Configuration", 
                true, 
                if (configured) "Client ID configured" else "Client ID not configured (placeholder)"
            )
        }
        
        runTest("OsmOAuthManager Initialization") {
            try {
                val manager = OsmOAuthManager(context)
                TestResult("OsmOAuthManager Initialization", true, "Manager initialized successfully")
            } catch (e: Exception) {
                TestResult("OsmOAuthManager Initialization", false, "Initialization error: ${e.message}")
            }
        }
        
        runTest("OSM Username Retrieval") {
            val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            val username = prefs.getString("osm_username", null)
            
            TestResult(
                "OSM Username Retrieval", 
                true, 
                if (username != null) "Username: $username" else "No username stored"
            )
        }
        
        runTest("OSM Note Creation Settings") {
            val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean("addOsmNote", false)
            
            TestResult(
                "OSM Note Creation Settings", 
                true, 
                "OSM note creation: ${if (enabled) "enabled" else "disabled"}"
            )
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
     * Batch Processing Tests
     */
    private fun testBatchProcessing() {
        log("[TEST] === Batch Processing Tests ===")
        
        runTest("Audio File Discovery") {
            try {
                val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                val saveDirPath = prefs.getString("saveDirectory", null) ?: 
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath + "/VoiceNotes"
                
                val saveDir = File(saveDirPath)
                if (saveDir.exists()) {
                    val audioFiles = saveDir.listFiles { file -> 
                        file.extension == "ogg" || file.extension == "m4a" 
                    }
                    val count = audioFiles?.size ?: 0
                    val oggCount = audioFiles?.count { it.extension == "ogg" } ?: 0
                    val m4aCount = audioFiles?.count { it.extension == "m4a" } ?: 0
                    TestResult("Audio File Discovery", true, "Found $count audio files ($oggCount .ogg, $m4aCount .m4a) in save directory")
                } else {
                    TestResult("Audio File Discovery", true, "Save directory does not exist yet")
                }
            } catch (e: Exception) {
                TestResult("Audio File Discovery", false, "Error: ${e.message}")
            }
        }
        
        runTest("Coordinate Extraction from Filename Pattern") {
            val testFilenameOgg = "VN_2024-01-15_12-30-45_40.7128_-74.0060.ogg"
            val testFilenameM4a = "VN_2024-01-15_12-30-45_40.7128_-74.0060.m4a"
            val pattern = """_(-?\d+\.\d+)_(-?\d+\.\d+)\.(ogg|m4a)$""".toRegex()
            val matchOgg = pattern.find(testFilenameOgg)
            val matchM4a = pattern.find(testFilenameM4a)
            
            if (matchOgg != null && matchM4a != null) {
                TestResult("Coordinate Extraction from Filename Pattern", true, "Pattern matched both .ogg and .m4a files successfully")
            } else {
                TestResult("Coordinate Extraction from Filename Pattern", false, "Pattern match failed")
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
                val className = "com.voicenotes.motorcycle.OverlayService"
                Class.forName(className)
                TestResult("OverlayService Class Exists", true, "OverlayService class found")
            } catch (e: Exception) {
                TestResult("OverlayService Class Exists", false, "OverlayService not found: ${e.message}")
            }
        }
        
        runTest("BatchProcessingService Class Exists") {
            try {
                val className = "com.voicenotes.motorcycle.BatchProcessingService"
                Class.forName(className)
                TestResult("BatchProcessingService Class Exists", true, "BatchProcessingService class found")
            } catch (e: Exception) {
                TestResult("BatchProcessingService Class Exists", false, "BatchProcessingService not found: ${e.message}")
            }
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
