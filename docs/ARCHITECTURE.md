# Architecture Documentation

## Table of Contents

1. [System Overview](#system-overview)
2. [Architecture Patterns](#architecture-patterns)
3. [Component Architecture](#component-architecture)
4. [Data Flow](#data-flow)
5. [Database Architecture](#database-architecture)
6. [Service Architecture](#service-architecture)
7. [UI Architecture](#ui-architecture)
8. [External Integrations](#external-integrations)
9. [File System Architecture](#file-system-architecture)
10. [Security Architecture](#security-architecture)
11. [Testing Architecture](#testing-architecture)
12. [Performance Considerations](#performance-considerations)

---

## System Overview

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Voice Notes App                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────┐              ┌──────────────┐            │
│  │   Main Icon  │              │ Manager Icon │            │
│  │  (Recording) │              │ (Processing) │            │
│  └──────┬───────┘              └──────┬───────┘            │
│         │                              │                     │
│         ▼                              ▼                     │
│  ┌─────────────┐              ┌──────────────┐            │
│  │  Overlay    │              │  Recording   │            │
│  │  Service    │              │  Manager     │            │
│  │             │              │  Activity    │            │
│  └─────┬───────┘              └──────┬───────┘            │
│        │                              │                     │
│        │                              ▼                     │
│        │                      ┌──────────────┐            │
│        │                      │    Batch     │            │
│        │                      │  Processing  │            │
│        │                      │   Service    │            │
│        │                      └──────┬───────┘            │
│        │                              │                     │
│        ▼                              ▼                     │
│  ┌──────────────────────────────────────────┐             │
│  │         Room Database (SQLite)            │             │
│  │  ┌────────────────────────────────────┐  │             │
│  │  │         recordings table           │  │             │
│  │  └────────────────────────────────────┘  │             │
│  └──────────────────────────────────────────┘             │
│                       │                                     │
│                       ▼                                     │
│  ┌──────────────────────────────────────────┐             │
│  │       Internal Storage (Audio Files)      │             │
│  │   /data/data/.../files/recordings/*.ogg   │             │
│  └──────────────────────────────────────────┘             │
│                                                              │
└─────────────────────────────────────────────────────────────┘
                       │
        ┌──────────────┴───────────────┐
        │                              │
        ▼                              ▼
┌────────────────┐
│  Google Cloud  │
│  Speech-to-Text│
└────────────────┘
```

### Design Philosophy

The architecture follows these core principles:

1. **Separation of Concerns**: Recording and processing are completely separate flows
2. **Database-Driven**: Single source of truth for all recording metadata
3. **Service-Oriented**: Long-running operations handled by Android Services
4. **Offline-First**: Core functionality works without network
5. **User Safety**: Production database never touched by tests

---

## Architecture Patterns

### 1. Service-Oriented Architecture (SOA)

Two independent services handle different concerns:

**OverlayService** (Recording):
- Foreground service with overlay permission
- No user interaction during recording
- Direct database insertion
- Auto-termination

**BatchProcessingService** (Processing):
- Background service for API calls
- User-initiated from UI
- Status updates via database
- Graceful error handling

### 2. Repository Pattern (Implicit)

While not explicit, the `RecordingDao` acts as a repository:

```kotlin
interface RecordingDao {
    // Repository methods
    @Insert suspend fun insertRecording(recording: Recording): Long
    @Query suspend fun getAllRecordings(): List<Recording>
    @Update suspend fun updateRecording(recording: Recording)
    @Delete suspend fun deleteRecording(recording: Recording)
}
```

### 3. Observer Pattern (LiveData)

UI reactively updates based on database changes:

```kotlin
// In RecordingManagerActivity
recordingDao.getAllRecordingsLiveData().observe(this) { recordings ->
    adapter.updateData(recordings)
}
```

### 4. Singleton Pattern

Database instance is a thread-safe singleton:

```kotlin
companion object {
    @Volatile
    private var INSTANCE: RecordingDatabase? = null

    fun getDatabase(context: Context): RecordingDatabase {
        return INSTANCE ?: synchronized(this) {
            val instance = Room.databaseBuilder(...)
            INSTANCE = instance
            instance
        }
    }
}
```

### 5. Strategy Pattern

File format selection based on Android version:

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    // Strategy: OGG/Opus
    mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.OGG)
    mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
} else {
    // Strategy: AMR-WB
    mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_WB)
    mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB)
}
```

---

## Component Architecture

### Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                      UI Layer                                │
├─────────────────────────────────────────────────────────────┤
│  MainActivity                                                │
│  RecordingManagerActivity                                    │
│  SettingsActivity                                            │
│  DebugLogActivity                                            │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        │ observes LiveData
                        │
┌───────────────────────▼─────────────────────────────────────┐
│                   Business Logic Layer                       │
├─────────────────────────────────────────────────────────────┤
│  OverlayService (recording logic)                           │
│  BatchProcessingService (processing logic)                   │
│  TranscriptionService (STT logic)                            │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        │ reads/writes
                        │
┌───────────────────────▼─────────────────────────────────────┐
│                    Data Layer                                │
├─────────────────────────────────────────────────────────────┤
│  RecordingDatabase (Room)                                    │
│  ├─ RecordingDao (queries)                                   │
│  ├─ Recording (entity)                                       │
│  ├─ Converters (type converters)                             │
│  └─ RecordingMigration (migrations)                          │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        │ persists to
                        │
┌───────────────────────▼─────────────────────────────────────┐
│                  Storage Layer                               │
├─────────────────────────────────────────────────────────────┤
│  SQLite Database: recording_database                         │
│  Internal Storage: /files/recordings/*.ogg                   │
│  SharedPreferences: AppPrefs                                 │
└─────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Dependencies |
|-----------|---------------|--------------|
| **MainActivity** | App entry point, start OverlayService | OverlayService |
| **OverlayService** | Record audio, GPS, save to DB | RecordingDatabase, MediaRecorder, FusedLocationProviderClient, TextToSpeech |
| **RecordingManagerActivity** | Display recordings, trigger processing | RecordingDatabase, BatchProcessingService, MediaPlayer |
| **BatchProcessingService** | Process recordings in background | RecordingDatabase, TranscriptionService |
| **TranscriptionService** | Google Cloud Speech-to-Text API | HttpURLConnection, JSON parsing |
| **RecordingDatabase** | Data persistence | Room library |
| **SettingsActivity** | Configuration management | SharedPreferences |
| **DebugLogActivity** | Logging and testing | TestSuite, DebugLogger |

---

## Data Flow

### Recording Flow

```
User taps main icon
        │
        ▼
MainActivity.onCreate()
        │
        ▼
Start OverlayService
        │
        ▼
┌────────────────────────────────────────┐
│      OverlayService Sequence           │
├────────────────────────────────────────┤
│ 1. Show overlay bubble                 │
│ 2. Initialize TTS (with timeout)       │
│ 3. Request GPS location (30s timeout)  │
│ 4. Announce location via TTS           │
│ 5. Start MediaRecorder                 │
│ 6. Record for configured duration      │
│ 7. Stop MediaRecorder                  │
│ 8. Save file to internal storage       │
│ 9. Create database entry               │
│ 10. Update overlay: "Recording saved"  │
│ 11. Stop service (auto-quit)           │
└────────────────────────────────────────┘
        │
        ▼
Recording stored in:
- Database: recordings table
- Storage: /files/recordings/VN_*.ogg
```

### Processing Flow

```
User taps manager icon
        │
        ▼
RecordingManagerActivity
        │
        ▼
Query all recordings (LiveData)
        │
        ▼
Display in RecyclerView
        │
        ▼
User taps "Transcribe" button
        │
        ▼
RecordingManagerActivity.transcribeRecording()
        │
        ├─ Update status: V2SStatus.PROCESSING
        ├─ Update database
        └─ Start BatchProcessingService
                │
                ▼
        BatchProcessingService.processSingleRecording()
                │
                ▼
        TranscriptionService.transcribe()
                │
                ├─ Read audio file from storage
                ├─ Base64 encode
                ├─ POST to Google Cloud API
                └─ Parse JSON response
                │
                ▼
        Update database:
        ├─ v2sStatus = COMPLETED
        ├─ v2sResult = "transcription text"
        └─ updatedAt = current timestamp
                │
                ▼
        LiveData notifies RecyclerView
                │
                ▼
        UI updates with transcription
```

---

## Database Architecture

### Schema Design

```sql
CREATE TABLE recordings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    audioFilePath TEXT NOT NULL,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    timestamp INTEGER NOT NULL,
    v2sStatus TEXT NOT NULL,           -- Enum: NOT_STARTED, PROCESSING, etc.
    v2sResult TEXT,                     -- Nullable: transcription text
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL
);
```

### Entity Definition

```kotlin
@Entity(tableName = "recordings")
data class Recording(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "audioFilePath")
    val audioFilePath: String,

    @ColumnInfo(name = "latitude")
    val latitude: Double,

    @ColumnInfo(name = "longitude")
    val longitude: Double,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "v2sStatus")
    val v2sStatus: V2SStatus,

    @ColumnInfo(name = "v2sResult")
    val v2sResult: String? = null,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long,

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long
)
```

### Status State Machine

**V2SStatus transitions**:
```
NOT_STARTED → PROCESSING → COMPLETED
                    ↓
                  ERROR
                    ↓
                 FALLBACK
```

### Type Converters

```kotlin
class Converters {
    @TypeConverter
    fun fromV2SStatus(value: V2SStatus): String {
        return value.name
    }

    @TypeConverter
    fun toV2SStatus(value: String): V2SStatus {
        return V2SStatus.valueOf(value)
    }
}
```

### DAO Interface

```kotlin
@Dao
interface RecordingDao {
    // Insert operations
    @Insert
    suspend fun insertRecording(recording: Recording): Long

    // Query operations
    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getRecordingById(id: Long): Recording?

    @Query("SELECT * FROM recordings ORDER BY timestamp DESC")
    suspend fun getAllRecordings(): List<Recording>

    @Query("SELECT * FROM recordings ORDER BY timestamp DESC")
    fun getAllRecordingsLiveData(): LiveData<List<Recording>>

    // Update operations
    @Update
    suspend fun updateRecording(recording: Recording)

    // Delete operations
    @Delete
    suspend fun deleteRecording(recording: Recording)
}
```

### Database Migration

```kotlin
object RecordingMigration {
    // Future migrations go here
    // Example:
    // val MIGRATION_1_2 = object : Migration(1, 2) {
    //     override fun migrate(database: SupportSQLiteDatabase) {
    //         database.execSQL("ALTER TABLE recordings ADD COLUMN newField TEXT")
    //     }
    // }
}
```

### Database Instances

**Production database**:
```kotlin
fun getDatabase(context: Context): RecordingDatabase {
    return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
            context.applicationContext,
            RecordingDatabase::class.java,
            "recording_database"  // Persisted to disk
        )
            .fallbackToDestructiveMigration()
            .build()
        INSTANCE = instance
        instance
    }
}
```

**Test database**:
```kotlin
fun getTestDatabase(context: Context): RecordingDatabase {
    return Room.inMemoryDatabaseBuilder(
        context.applicationContext,
        RecordingDatabase::class.java  // No database name = in-memory
    )
        .allowMainThreadQueries()
        .build()
}
```

**Key differences**:
- Production: Persistent SQLite file
- Test: In-memory, cleared on process exit
- Test: Allows main thread queries (for testing convenience)

---

## Service Architecture

### Android Service Types Used

1. **Foreground Service** (OverlayService):
   - User-visible notification
   - Higher priority (less likely to be killed)
   - Used for recording

2. **Background Service** (BatchProcessingService):
   - No notification (unless processing)
   - Lower priority
   - Used for API calls

### OverlayService Architecture

**Service Lifecycle**:
```
onCreate()
    │
    ▼
onStartCommand()
    │
    ├─ startForeground()  // Show notification
    ├─ showOverlay()       // Display bubble
    ├─ initializeTTS()     // Initialize Text-to-Speech
    └─ requestLocation()   // Start GPS acquisition
            │
            ▼
    onLocationReceived()
            │
            ├─ announceTTS()      // Speak location
            └─ startRecording()   // Begin audio capture
                    │
                    ▼
            onRecordingComplete()
                    │
                    ├─ saveToDatabase()
                    └─ stopSelfAndFinish()
                            │
                            ▼
                    onDestroy()
```

**Threading model**:
- Main thread: UI operations (overlay, TTS)
- Background thread: Database operations (coroutines)
- MediaRecorder thread: Audio recording

**State management**:
```kotlin
private var mediaRecorder: MediaRecorder? = null
private var tts: TextToSpeech? = null
private var recordingFilePath: String? = null
private var isRecording = false
```

### BatchProcessingService Architecture

**Service Lifecycle**:
```
onCreate()
    │
    ▼
onStartCommand(intent)
    │
    ├─ Extract recordingId
    ├─ Extract flags: transcribeOnly
    └─ Launch coroutine
            │
            ▼
    processSingleRecording(id, transcribeOnly)
            │
            ├─ if transcribeOnly:
            │   └─ transcribe() → update DB
            │
            └─ if not:
                └─ transcribe() → update DB
                        │
                        ▼
                stopSelf()
```

**Error handling**:
```kotlin
try {
    // API call
    val result = transcriptionService.transcribe(file)
    // Update success status
} catch (e: Exception) {
    // Update error status
    recording.copy(
        v2sStatus = V2SStatus.ERROR,
        v2sResult = "Error: ${e.message}"
    )
}
```

---

## UI Architecture

### Activity Hierarchy

```
BaseActivity (if exists)
    │
    ├─ MainActivity
    │   └─ Purpose: Launch OverlayService
    │
    ├─ RecordingManagerActivity
    │   ├─ RecyclerView (recordings list)
    │   ├─ ViewHolder (item layout)
    │   └─ Adapter (data binding)
    │
    ├─ SettingsActivity
    │   └─ SharedPreferences management
    │
    └─ DebugLogActivity
        ├─ ScrollView (log display)
        └─ TestSuite integration
```

### RecordingManagerActivity Architecture

**MVVM-lite pattern**:
```
┌─────────────────────────────────────┐
│   RecordingManagerActivity (View)   │
├─────────────────────────────────────┤
│ - RecyclerView                      │
│ - RecordingAdapter                  │
│ - ViewHolder                        │
└───────────────┬─────────────────────┘
                │
                │ observes LiveData
                │
┌───────────────▼─────────────────────┐
│   RecordingDao (ViewModel-like)     │
├─────────────────────────────────────┤
│ - getAllRecordingsLiveData()        │
│ - updateRecording()                 │
└───────────────┬─────────────────────┘
                │
                │ queries
                │
┌───────────────▼─────────────────────┐
│   RecordingDatabase (Model)         │
├─────────────────────────────────────┤
│ - SQLite database                   │
└─────────────────────────────────────┘
```

**LiveData flow**:
```kotlin
// In RecordingManagerActivity
private lateinit var recordingDao: RecordingDao

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val db = RecordingDatabase.getDatabase(this)
    recordingDao = db.recordingDao()

    // Observe LiveData
    recordingDao.getAllRecordingsLiveData().observe(this) { recordings ->
        // UI update on main thread
        adapter.updateData(recordings)
        updateEmptyState(recordings.isEmpty())
    }
}
```

### RecyclerView Architecture

**Adapter pattern**:
```kotlin
class RecordingAdapter : RecyclerView.Adapter<RecordingAdapter.ViewHolder>() {

    private var recordings = listOf<Recording>()

    fun updateData(newRecordings: List<Recording>) {
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

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(recording: Recording) {
            // Bind data to views
            updateTranscriptionUI(recording)
        }
    }
}
```

### Material Design Implementation

**item_recording.xml structure**:
```xml
<com.google.android.material.card.MaterialCardView
    android:elevation="4dp"
    android:layout_margin="8dp">

    <LinearLayout android:orientation="vertical">

        <!-- Header: Date/Time + Play button -->
        <LinearLayout android:orientation="horizontal">
            <TextView android:id="@+id/dateTimeText" />
            <ImageView android:id="@+id/playIcon" />
        </LinearLayout>

        <!-- GPS Coordinates -->
        <TextView android:id="@+id/locationText" />

        <!-- Speech-to-Text Section -->
        <LinearLayout>
            <ImageView android:id="@+id/v2sStatusIcon" />
            <ProgressBar android:id="@+id/v2sProgressBar" />
            <Button android:id="@+id/transcribeButton" />
        </LinearLayout>
        <TextView android:id="@+id/transcriptionText" />

    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

**Status color coding**:
```kotlin
when (recording.v2sStatus) {
    V2SStatus.NOT_STARTED -> {
        statusIcon.setColorFilter(Color.GRAY)
        statusIcon.setImageResource(android.R.drawable.ic_menu_help)
    }
    V2SStatus.PROCESSING -> {
        progressBar.visibility = View.VISIBLE
        button.isEnabled = false
    }
    V2SStatus.COMPLETED -> {
        statusIcon.setColorFilter(getColor(android.R.color.holo_green_dark))
        statusIcon.setImageResource(android.R.drawable.checkbox_on_background)
    }
    V2SStatus.ERROR -> {
        statusIcon.setColorFilter(Color.RED)
        statusIcon.setImageResource(android.R.drawable.ic_dialog_alert)
    }
}
```

---

## External Integrations

### Google Cloud Speech-to-Text Integration

**Architecture**:
```
TranscriptionService
        │
        ├─ Read audio file from storage
        ├─ Base64 encode file content
        ├─ Build JSON request
        │   {
        │     "config": {
        │       "encoding": "OGG_OPUS",
        │       "sampleRateHertz": 48000,
        │       "languageCode": "en-US"
        │     },
        │     "audio": {
        │       "content": "<base64_audio>"
        │     }
        │   }
        │
        ├─ POST to https://speech.googleapis.com/v1/speech:recognize
        ├─ Add Authorization header (Bearer token)
        └─ Parse JSON response
            {
              "results": [{
                "alternatives": [{
                  "transcript": "transcribed text"
                }]
              }]
            }
```

**Authentication**:
```kotlin
// Service account JSON (Base64 encoded in BuildConfig)
val serviceAccountJson = Base64.decode(
    BuildConfig.GOOGLE_CLOUD_SERVICE_ACCOUNT_JSON_BASE64,
    Base64.NO_WRAP
)

// Extract credentials
val jsonObject = JSONObject(String(serviceAccountJson))
val privateKey = jsonObject.getString("private_key")
val clientEmail = jsonObject.getString("client_email")

// Generate JWT token
val token = generateJWT(privateKey, clientEmail)

// Add to request
connection.setRequestProperty("Authorization", "Bearer $token")
```

---

## File System Architecture

### Storage Locations

```
/data/data/com.voicenotes.motorcycle/
├── files/
│   ├── recordings/                    # Audio files
│   │   ├── VN_2024-01-15_12-30-45_40.7128_-74.0060.ogg
│   │   ├── VN_2024-01-15_12-35-22_40.7138_-74.0070.ogg
│   │   └── ...
│   └── debug_log.txt                  # Debug logging
├── databases/
│   └── recording_database             # SQLite database
└── shared_prefs/
    └── AppPrefs.xml                   # Settings
```

### Filename Convention

**Pattern**: `VN_YYYY-MM-DD_HH-mm-ss_LAT_LON.ext`

**Example**: `VN_2024-01-15_12-30-45_40.7128_-74.0060.ogg`

**Components**:
- `VN`: Voice Note prefix
- `2024-01-15`: Date (ISO 8601)
- `12-30-45`: Time (24-hour format)
- `40.7128`: Latitude (4 decimal places)
- `-74.0060`: Longitude (4 decimal places)
- `.ogg`: File extension (or `.amr` for Android 8-9)

**Parsing regex**:
```kotlin
val pattern = """VN_(\d{4})-(\d{2})-(\d{2})_(\d{2})-(\d{2})-(\d{2})_(-?\d+\.\d+)_(-?\d+\.\d+)\.(ogg|amr)""".toRegex()
val match = pattern.find(filename)
val (year, month, day, hour, minute, second, lat, lon, ext) = match.destructured
```

### Export Formats

**1. Audio Only**:
- Copies audio files to Downloads folder
- Preserves original filenames

**2. GPX (GPS Exchange Format)**:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="Voice Notes">
  <metadata>
    <name>Voice Notes Waypoints</name>
    <time>2024-01-15T12:30:45Z</time>
  </metadata>
  <wpt lat="40.7128" lon="-74.0060">
    <time>2024-01-15T12:30:45Z</time>
    <name>Voice Note 1</name>
    <desc>Transcription text here</desc>
  </wpt>
  <!-- More waypoints -->
</gpx>
```

**3. CSV (Comma-Separated Values)**:
```csv
Latitude,Longitude,Timestamp,Transcription
40.7128,-74.0060,2024-01-15 12:30:45,"Transcription text"
40.7138,-74.0070,2024-01-15 12:35:22,"Another transcription"
```

**Note**: CSV includes UTF-8 BOM (`\uFEFF`) for Excel compatibility

---

## Security Architecture

### Permission Model

**Runtime permissions** (dangerous):
```xml
<!-- Audio recording -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />

<!-- GPS location -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

<!-- Bluetooth audio (Android 12+) -->
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

**Install-time permissions** (normal):
```xml
<!-- Network access -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- Foreground service -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

<!-- Overlay window -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
```

**Note:** Storage and notification permissions are NOT required. The app uses `getExternalFilesDir()` which provides app-specific storage without requiring storage permissions on Android 10+.

### Data Protection

**1. Internal Storage** (app-private):
- Audio files stored in `/data/data/com.voicenotes.motorcycle/files/`
- Only accessible by the app (unless device is rooted)
- Automatically deleted when app is uninstalled

**2. Database Encryption** (optional):
- Could implement SQLCipher for encrypted database
- Currently not implemented (internal storage already protected)

**3. API Credentials**:
```kotlin
// Stored in BuildConfig (not in source code)
val googleCloudCreds = BuildConfig.GOOGLE_CLOUD_SERVICE_ACCOUNT_JSON_BASE64

// SharedPreferences (app-private)
val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
```

**4. Network Security**:
- All API calls use HTTPS
- Certificate pinning not implemented (relies on system trust store)

### Privacy Considerations

**Data retention**:
- Recordings stored indefinitely (user must delete)
- Database records persist until deleted
- Debug logs append-only (can be cleared)

**Data sharing**:
- Audio files: Never sent anywhere (except user export)
- Transcriptions: Sent to Google Cloud (Base64 encoded)
- GPS coordinates: Stored locally only
- No analytics or crash reporting

**User control**:
- User can delete any recording
- User controls when to transcribe (opt-in)
- User can disable debug logging

---

## Testing Architecture

### Test Database Isolation

**Critical design decision**: Tests must never touch production database.

**Implementation**:
```kotlin
// RecordingDatabase.kt
companion object {
    // Production database (singleton)
    fun getDatabase(context: Context): RecordingDatabase {
        return INSTANCE ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                RecordingDatabase::class.java,
                "recording_database"  // Persistent file
            )
                .fallbackToDestructiveMigration()
                .build()
            INSTANCE = instance
            instance
        }
    }

    // Test database (in-memory, isolated)
    fun getTestDatabase(context: Context): RecordingDatabase {
        return Room.inMemoryDatabaseBuilder(
            context.applicationContext,
            RecordingDatabase::class.java
        )
            .allowMainThreadQueries()  // Convenience for testing
            .build()
    }
}
```

**Test usage**:
```kotlin
// In TestSuite.kt
private fun testDatabase() {
    // Create isolated test database
    val testDb = RecordingDatabase.getTestDatabase(context)
    val dao = testDb.recordingDao()

    runTest("Insert Recording") {
        val recording = Recording(...)
        val id = runBlocking { dao.insertRecording(recording) }
        // Test assertions
    }

    // Clean up
    testDb.close()
}
```

### Test Suite Architecture

**Test categories** (82 tests total):

```
TestSuite
├── testConfiguration() - 4 tests
│   ├── SharedPreferences Read/Write
│   ├── Save Directory Configuration
│   └── Recording Duration Setting
│
├── testPermissions() - 4 tests
│   ├── RECORD_AUDIO Permission
│   ├── ACCESS_FINE_LOCATION Permission
│   ├── BLUETOOTH_CONNECT Permission
│   └── Overlay Permission
│
├── testDatabase() - 13 tests ★NEW★
│   ├── Database Initialization
│   ├── Insert Recording
│   ├── Query Recording by ID
│   ├── Update Recording Status
│   ├── Query All Recordings
│   ├── Delete Recording
│   ├── V2S Status Enum Handling
│   ├── Null Value Handling
│   ├── Coordinate Precision
│   ├── Empty String vs Null Handling
│   ├── Multiple Status Updates
│   └── Database Isolation from Production ★
│
├── testRecordingOperations() - 10 tests ★NEW★
│   ├── File Format Selection (API Level Based)
│   ├── Filename Generation Pattern
│   ├── Coordinate Formatting in Filename
│   ├── Filename Timestamp Parsing
│   ├── Recording Duration Validation
│   ├── Audio File Path Construction
│   ├── Negative Coordinate Handling
│   ├── Zero Coordinate Handling
│   ├── Export Format Selection
│   └── Recordings Directory Creation
│
├── testFileSystem() - 6 tests
├── testLocationServices() - 4 tests
├── testAudioSystem() - 5 tests
├── testNetwork() - 5 tests
├── testGoogleCloudIntegration() - 3 tests
├── testGPXFile() - 3 tests
├── testCSVFile() - 3 tests
├── testServiceLifecycle() - 2 tests
│
└── testErrorHandling() - 14 tests ★NEW★
    ├── Query Non-Existent Recording
    ├── Invalid Coordinate Range (Latitude > 90)
    ├── Invalid Coordinate Range (Longitude > 180)
    ├── Empty Audio File Path
    ├── Very Long Transcription Text
    ├── Special Characters in Transcription
    ├── Newlines in Transcription
    ├── Update Non-Existent Recording
    ├── Delete Non-Existent Recording
    ├── Concurrent Database Operations
    ├── Invalid Filename Pattern Parsing
    ├── Timestamp Edge Cases (Year 2038)
    └── Database Size Limit (Large Dataset)
```

### Test Execution Flow

```
User taps "Run Tests"
        │
        ▼
TestSuite.runAllTests()
        │
        ├─ Create test database
        ├─ Run test categories sequentially
        │   ├─ testConfiguration()
        │   ├─ testPermissions()
        │   ├─ testDatabase()
        │   ├─ ...
        │   └─ testErrorHandling()
        │
        ├─ Close test database
        └─ printSummary()
                │
                ▼
        Results written to debug_log.txt
                │
                ▼
        DebugLogActivity displays results
```

---

## Performance Considerations

### Database Performance

**Indexing strategy**:
- Primary key (id) automatically indexed
- Consider adding index on timestamp for faster sorted queries:
  ```sql
  CREATE INDEX idx_timestamp ON recordings(timestamp DESC);
  ```

**Query optimization**:
- Use LiveData for reactive updates (no manual polling)
- Suspend functions for non-blocking database access
- Batch operations when possible

**Memory management**:
- RecyclerView recycles views (efficient for large lists)
- Audio files not loaded into memory (streamed via MediaPlayer)
- Test database in-memory (fast, but memory-limited)

### Audio Recording Performance

**MediaRecorder optimization**:
- OGG/Opus: Better compression (smaller files)
- AMR-WB: Lower CPU usage (better for older devices)
- Recording runs on separate thread (no UI blocking)

**File I/O**:
- Write directly to internal storage (no copying)
- Asynchronous database insertion

### Network Performance

**API call optimization**:
- Background service (doesn't block UI)
- Retry logic with exponential backoff
- Batch processing capability (though not currently used)

**Bandwidth considerations**:
- Audio files Base64 encoded (~33% size increase)
- Could implement audio compression before upload
- Could implement chunked upload for large files

### UI Performance

**RecyclerView optimization**:
- ViewHolder pattern (view recycling)
- DiffUtil for efficient updates (not currently implemented)
- Image loading minimal (status icons only)

**LiveData benefits**:
- Automatic lifecycle management (no memory leaks)
- Updates only when active (no wasted CPU)
- Main thread delivery (thread-safe)

### Battery Optimization

**Service management**:
- OverlayService: Auto-terminates after recording
- BatchProcessingService: Stops after processing
- No background polling or wake locks

**GPS optimization**:
- Single location request (not continuous tracking)
- 30-second timeout (prevents battery drain)
- Fallback to last known location

**Network optimization**:
- Only when user triggers (no automatic background sync)
- WiFi preferred for large uploads (could be implemented)

---

## Scalability Considerations

### Database Scalability

**Current limitations**:
- SQLite: Single-writer (sequential writes)
- No pagination (loads all recordings)

**Potential improvements**:
- Add pagination: `LIMIT` and `OFFSET` queries
- Add filtering: Date range, status filters
- Add search: Full-text search on transcriptions

**Example pagination**:
```kotlin
@Query("SELECT * FROM recordings ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
suspend fun getRecordingsPaged(limit: Int, offset: Int): List<Recording>
```

### Storage Scalability

**Current approach**:
- Internal storage (limited by device)
- No automatic cleanup

**Potential improvements**:
- Implement storage quota monitoring
- Add auto-delete old recordings
- Support external storage (SD card)
- Implement cloud backup (Google Drive, Dropbox)

### Performance Monitoring

**Potential additions**:
- Database query timing
- API response time tracking
- Memory usage monitoring
- Battery drain analysis

---

## Future Architecture Improvements

### 1. ViewModel Integration

Move from LiveData in DAO to proper ViewModel:

```kotlin
class RecordingViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: RecordingRepository
    val allRecordings: LiveData<List<Recording>>

    init {
        val recordingDao = RecordingDatabase.getDatabase(application).recordingDao()
        repository = RecordingRepository(recordingDao)
        allRecordings = repository.allRecordings
    }

    fun insert(recording: Recording) = viewModelScope.launch {
        repository.insert(recording)
    }
}
```

### 2. Dependency Injection

Implement Hilt or Koin for better testability:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RecordingDatabase {
        return RecordingDatabase.getDatabase(context)
    }

    @Provides
    fun provideRecordingDao(database: RecordingDatabase): RecordingDao {
        return database.recordingDao()
    }
}
```

### 3. Repository Pattern

Formalize repository layer:

```kotlin
class RecordingRepository(private val recordingDao: RecordingDao) {
    val allRecordings: LiveData<List<Recording>> = recordingDao.getAllRecordingsLiveData()

    suspend fun insert(recording: Recording): Long {
        return recordingDao.insertRecording(recording)
    }

    suspend fun update(recording: Recording) {
        recordingDao.updateRecording(recording)
    }
}
```

### 4. WorkManager Integration

Replace BatchProcessingService with WorkManager:

```kotlin
class TranscriptionWorker(context: Context, params: WorkerParameters)
    : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val recordingId = inputData.getLong("recordingId", -1)
        // Process recording
        return Result.success()
    }
}

// Enqueue work
val workRequest = OneTimeWorkRequestBuilder<TranscriptionWorker>()
    .setInputData(workDataOf("recordingId" to id))
    .build()
WorkManager.getInstance(context).enqueue(workRequest)
```

### 5. Navigation Component

Implement Jetpack Navigation for better navigation flow.

### 6. Compose UI

Migrate to Jetpack Compose for modern declarative UI.

---

## Conclusion

This architecture provides:
- ✅ Clear separation of concerns
- ✅ Scalable database design
- ✅ Testable components
- ✅ Secure data handling
- ✅ Efficient resource usage

The modular design allows for easy enhancement and maintenance while keeping complexity manageable for a single-developer project.

For implementation details, see [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md).

---

**Document Version**: 1.0.0
**Last Updated**: 2026-01-25
**Maintained By**: Development Team
