# Developer Guide

## Overview

Voice Notes is an Android app designed for motorcyclists to record GPS-tagged voice notes. This guide provides developers with the information needed to understand, modify, and extend the application.

## Table of Contents

1. [Getting Started](#getting-started)
2. [Project Structure](#project-structure)
3. [Architecture Overview](#architecture-overview)
4. [Key Components](#key-components)
5. [Configuration](#configuration)
6. [Building and Running](#building-and-running)
7. [Testing](#testing)
8. [Development Workflow](#development-workflow)
9. [Release Process](#release-process)

---

## Getting Started

### Prerequisites

- **Android Studio**: Arctic Fox (2020.3.1) or later
- **JDK**: 17 or later
- **Android SDK**: API 26 (Android 8.0) minimum, API 35 target
- **Git**: For version control
- **Google Cloud Platform account**: For Speech-to-Text API

### Initial Setup

1. **Clone the repository**:
   ```bash
   git clone https://github.com/yourusername/autorecord-app.git
   cd autorecord-app
   ```

2. **Open in Android Studio**:
   - Open Android Studio
   - Select "Open an Existing Project"
   - Navigate to the cloned repository

3. **Sync Gradle**:
   - Android Studio will automatically sync Gradle
   - Wait for dependencies to download

4. **Configure API credentials** (see [Configuration](#configuration) section)

---

## Project Structure

```
app/
├── src/
│   ├── main/
│   │   ├── java/com/voicenotes/motorcycle/
│   │   │   ├── database/           # Room database entities, DAOs, converters
│   │   │   ├── MainActivity.kt     # App entry point with launcher icon
│   │   │   ├── OverlayService.kt   # Recording service with overlay bubble
│   │   │   ├── RecordingManagerActivity.kt  # Post-recording management
│   │   │   ├── BatchProcessingService.kt    # Background processing service
│   │   │   ├── SettingsActivity.kt          # Configuration activity
│   │   │   ├── TranscriptionService.kt      # Google Cloud Speech-to-Text
│   │   │   ├── DebugLogActivity.kt          # Debug log viewer
│   │   │   ├── DebugLogger.kt               # Logging utility
│   │   │   ├── TestSuite.kt                 # On-device test suite
│   │   │   └── NetworkUtils.kt              # Network connectivity helpers
│   │   ├── res/
│   │   │   ├── layout/              # XML layouts (6 files)
│   │   │   ├── drawable/            # Icons and graphics
│   │   │   ├── values/              # Strings, colors, styles
│   │   │   └── xml/                 # Preferences, file paths
│   │   └── AndroidManifest.xml      # App manifest with permissions
│   └── test/                        # Unit tests (if any)
├── build.gradle                     # App-level build configuration
└── gradle.properties                # Project properties
```

### Key Directories

- **`database/`**: All Room database-related code
  - `Recording.kt`: Entity definition
  - `RecordingDao.kt`: Data access object with queries
  - `RecordingDatabase.kt`: Database singleton
  - `Converters.kt`: Type converters for enums
  - `RecordingMigration.kt`: Database migration logic

- **`res/layout/`**: UI layouts
  - `activity_main.xml`: Main launcher
  - `activity_settings.xml`: Settings screen
  - `activity_recording_manager.xml`: Recording list
  - `item_recording.xml`: Recording list item (Material Design)
  - `activity_debug_log.xml`: Debug log viewer

---

## Architecture Overview

The app follows a **service-oriented architecture** with clear separation between:

1. **Recording Flow**: Completely background, no user interaction
   - Triggered by main launcher icon
   - Handled by `OverlayService`
   - Saves to internal storage with database entry

2. **Processing Flow**: Interactive, user-driven
   - Triggered by manager launcher icon
   - Handled by `RecordingManagerActivity`
   - Uses `BatchProcessingService` for background operations

3. **Database Layer**: Room-based persistence
   - Single source of truth for all recordings
   - Tracks processing status (V2S)
   - LiveData for reactive UI updates

4. **External Services**: API integrations
   - Google Cloud Speech-to-Text

For detailed architecture information, see [ARCHITECTURE.md](ARCHITECTURE.md).

---

## Key Components

### 1. OverlayService (Recording Service)

**Location**: `OverlayService.kt`

**Purpose**: Handles the complete recording flow without user interaction.

**Key responsibilities**:
- Display overlay bubble
- Announce location via TTS
- Record audio (OGG/Opus or AMR-WB)
- Tag with GPS coordinates
- Save to database
- Auto-quit

**Important methods**:
- `onStartCommand()`: Service entry point
- `initializeTTS()`: Text-to-Speech setup
- `requestLocation()`: GPS acquisition
- `startRecording()`: MediaRecorder setup
- `finishRecordingProcess()`: Save and cleanup

**File format selection**:
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    // Android 10+: OGG with Opus encoder
    mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.OGG)
    mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
} else {
    // Android 8-9: AMR-WB
    mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_WB)
    mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB)
}
```

### 2. RecordingManagerActivity (Processing UI)

**Location**: `RecordingManagerActivity.kt`

**Purpose**: Interactive management of recordings after they're captured.

**Key responsibilities**:
- Display list of recordings (LiveData-driven)
- Play audio files
- Trigger transcription
- Export in multiple formats (Audio, GPX, CSV)
- Delete recordings

**Important methods**:
- `setupRecyclerView()`: Initialize RecyclerView with adapter
- `playRecording()`: MediaPlayer-based playback
- `transcribeRecording()`: Start Speech-to-Text processing
- `exportRecordings()`: Multi-format export

**UI features**:
- Material Design cards with elevation
- Color-coded status indicators (green/red/orange/gray)
- Progress spinners during operations
- Real-time Toast feedback
- Separate action buttons (Transcribe)

### 3. BatchProcessingService (Background Processor)

**Location**: `BatchProcessingService.kt`

**Purpose**: Background service for processing recordings (transcription).

**Operation modes**:
- `transcribeOnly`: Only perform Speech-to-Text
- Full processing: Perform transcription

**Key methods**:
- `processSingleRecording()`: Process one recording
- `handleTranscriptionWithRetry()`: Retry logic for transcription

**Status tracking**:
- Updates database in real-time
- Uses `V2SStatus` enum
- Handles errors gracefully

### 4. Database (Room)

**Location**: `database/` directory

**Entity**: `Recording.kt`
```kotlin
@Entity(tableName = "recordings")
data class Recording(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val audioFilePath: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val v2sStatus: V2SStatus,
    val v2sResult: String?,
    val createdAt: Long,
    val updatedAt: Long
)
```

**Status enums**:
- `V2SStatus`: NOT_STARTED, PROCESSING, COMPLETED, ERROR, FALLBACK

**DAO operations**: `RecordingDao.kt`
- `insertRecording()`: Returns ID
- `getRecordingById()`: Nullable return
- `getAllRecordings()`: Returns List<Recording>
- `updateRecording()`: Update existing record
- `deleteRecording()`: Remove from database

**Database methods**:
- `getDatabase(context)`: Production database (singleton)
- `getTestDatabase(context)`: In-memory test database (isolated)

### 5. TranscriptionService (Google Cloud STT)

**Location**: `TranscriptionService.kt`

**Purpose**: Converts audio files to text using Google Cloud Speech-to-Text API.

**Key features**:
- Base64-encoded service account credentials
- Automatic audio file reading
- Configurable language (default: en-US)
- Error handling with detailed messages

**Configuration**:
```kotlin
// BuildConfig from gradle.properties
val serviceAccountJsonBase64 = BuildConfig.GOOGLE_CLOUD_SERVICE_ACCOUNT_JSON_BASE64
```

### 6. SettingsActivity (Configuration)

**Location**: `SettingsActivity.kt`

**Purpose**: Central configuration for all app features.

**Settings available**:
- Recording duration (1-99 seconds)
- Google Cloud credentials (Base64)
- Debug logging toggle

**SharedPreferences keys**:
- `recordingDuration`: Int (default 10)
- `enableDebugLogging`: Boolean

---

## Configuration

### Google Cloud Speech-to-Text

1. **Create a Google Cloud project**:
   - Go to https://console.cloud.google.com
   - Create a new project
   - Enable Speech-to-Text API

2. **Create service account**:
   - Navigate to IAM & Admin → Service Accounts
   - Create service account with "Speech-to-Text User" role
   - Generate JSON key

3. **Encode credentials**:
   ```bash
   base64 -w 0 service-account-key.json > credentials.base64
   ```

4. **Add to `gradle.properties`**:
   ```properties
   GOOGLE_CLOUD_SERVICE_ACCOUNT_JSON_BASE64=<paste base64 here>
   ```

### Build Configuration

**`build.gradle` (app level)**:
```gradle
android {
    buildTypes {
        debug {
            buildConfigField "String", "GOOGLE_CLOUD_SERVICE_ACCOUNT_JSON_BASE64",
                "\"${project.findProperty('GOOGLE_CLOUD_SERVICE_ACCOUNT_JSON_BASE64') ?: ''}\""
        }
        release {
            // Same as debug
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}
```

---

## Building and Running

### Debug Build

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Build and install
./gradlew installDebug
```

**Output**: `app/build/outputs/apk/debug/app-debug.apk`

### Release Build

```bash
# Build release APK (requires signing configuration)
./gradlew assembleRelease

# Or use the release build script (recommended)
./build-release.sh
```

**Important:** Release builds use ProGuard/R8 for code optimization and obfuscation. See [ProGuard Configuration](#proguard-configuration) section below.

**Signing configuration** (required for distribution):

Create `keystore.properties` file (not in version control):
```properties
storeFile=path/to/keystore.jks
storePassword=your_keystore_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

See [docs/SECURITY.md](SECURITY.md) for detailed security and signing instructions.

### Running Tests

#### Unit Tests

The project includes JUnit unit tests for core functionality:

```bash
# Run all unit tests
./gradlew test

# Run tests for specific module
./gradlew app:testDebugUnitTest

# Run tests with coverage
./gradlew testDebugUnitTest jacocoTestReport
```

**Unit test files** (in `app/src/test/java/com/voicenotes/motorcycle/`):
- `CoordinateUtilsTest.kt` - Coordinate parsing and validation
- `FilenameUtilsTest.kt` - Filename generation and parsing
- `RecordingValidationTest.kt` - Recording data validation
- `V2SStatusTest.kt` - Enum conversions and transitions
- `DateTimeUtilsTest.kt` - Date/time formatting and timezone handling

#### On-Device Integration Tests

```bash
# Run on-device tests (via app UI)
# 1. Install app
# 2. Open Settings → Debug Log
# 3. Tap "Run Tests" button
```

The on-device test suite includes 82 tests covering database operations, file system, permissions, and API integrations.

### Running Lint

```bash
# Run lint on debug build
./gradlew lintDebug

# Run lint on release build
./gradlew lintRelease

# View lint report
open app/build/reports/lint-results-debug.html
```

**Lint configuration** is in `app/lint.xml` with custom rules for:
- Security issues (errors)
- Performance warnings
- Correctness checks
- Accessibility guidelines

---

## Testing

### Test Suite Structure

**Location**: `TestSuite.kt`

**Test categories** (82 total tests):
1. Configuration Tests (4 tests)
2. Permission Tests (5 tests)
3. **Database Tests (13 tests)** - NEW
4. **Recording Operations Tests (10 tests)** - NEW
5. File System Tests (6 tests)
6. Location Services Tests (4 tests)
7. Audio System Tests (5 tests)
8. Network Tests (5 tests)
9. Google Cloud Integration Tests (3 tests)
10. GPX File Tests (3 tests)
11. CSV File Tests (3 tests)
13. Service Lifecycle Tests (2 tests)
14. **Error Handling Tests (14 tests)** - NEW

### Database Test Isolation

**CRITICAL**: Tests use isolated in-memory database.

```kotlin
// Production database
val db = RecordingDatabase.getDatabase(context)

// Test database (isolated, in-memory)
val testDb = RecordingDatabase.getTestDatabase(context)
```

**Test database characteristics**:
- In-memory (no persistence)
- Allows main thread queries
- Cleared when process ends
- Zero impact on production data

### Writing New Tests

Add tests to `TestSuite.kt`:

```kotlin
private fun testMyFeature() {
    log("[TEST] === My Feature Tests ===")

    runTest("Test Name") {
        try {
            // Test logic here
            val result = myFunction()

            if (result == expected) {
                TestResult("Test Name", true, "Test passed")
            } else {
                TestResult("Test Name", false, "Expected $expected, got $result")
            }
        } catch (e: Exception) {
            TestResult("Test Name", false, "Exception: ${e.message}")
        }
    }

    log("")
}
```

**Add to test suite**:
```kotlin
fun runAllTests() {
    // ...
    testMyFeature()
    // ...
}
```

### Running Tests

1. Install app on device/emulator
2. Open Settings (manager icon)
3. Scroll to Debug Log section
4. Tap "View Debug Log"
5. Tap "Run Tests" button
6. Wait for completion (1-2 minutes)
7. Review results in log

**Expected result**: `Total: 82, Passed: 82, Failed: 0`

### Testing & QA

This section consolidates testing procedures and implementation notes for key features.

#### Fallback Transcription Testing

**Feature**: When Speech-to-Text returns empty/blank text, the system stores a human-readable fallback placeholder directly in `v2sResult`.

**Implementation Details**:
- Fallback format: `"latitude,longitude (no text)"` with coordinates formatted to 6 decimal places
- Example: `"37.774929,-122.419416 (no text)"`
- Status set to `V2SStatus.FALLBACK` with `v2sFallback = true`
- Stored in database, displayed in UI, and exported to files consistently

**Test Cases**:
1. **Quiet/empty recording tests**:
   - After processing: `v2sStatus == FALLBACK`, `v2sFallback == true`, `v2sResult == "lat,lng (no text)"`
   - Recording Manager EditText displays the same fallback placeholder text
   - GPX/CSV exports contain the same placeholder text from v2sResult
   - All three (DB, UI, exports) show identical text

2. **Blank transcription** (spaces/tabs/newlines):
   - Results in FALLBACK status with placeholder text
   - Same behavior as empty transcription

3. **Retranscribe from FALLBACK**:
   - "Retry" button appears and is enabled for FALLBACK status
   - Clicking clears `v2sFallback` and `errorMsg` before starting new transcription
   - If new transcription succeeds with content, status becomes COMPLETED
   - New transcription replaces fallback text in v2sResult

**Code Locations**:
- `BatchProcessingService.kt` (lines 100-117): Stores fallback placeholder when transcription is blank
- `RecordingManagerActivity.kt` (lines 628-638): Displays v2sResult (includes fallback)
- `TranscriptionService.kt` (lines 174-178): Joins all Speech-to-Text result chunks

#### Processing Animation Testing

**Feature**: Visual feedback during transcription processing using alpha-pulse animation on status icon.

**Implementation Details**:
- Alpha-pulse animation fades icon between 0.3f (30%) and 1.0f (100%) opacity
- Duration: 800ms per cycle (fade in + fade out)
- Repeat mode: REVERSE, infinite until processing completes

**Animation Lifecycle**:
1. **PROCESSING status**: `startProcessingAnimation()` creates and starts ObjectAnimator
2. **Status change**: `stopProcessingAnimation()` cancels animator, nulls reference, resets alpha to 1.0f
3. **View recycling**: `onViewRecycled()` calls `stopProcessingAnimation()` to prevent memory leaks

**Test Cases**:
1. **Animation behavior**:
   - Start transcription → status icon pulses smoothly
   - Alpha fades between 30% and 100% opacity over 800ms
   - Animation continues until transcription completes
   - Progress bar remains hidden during PROCESSING

2. **Status transitions**:
   - PROCESSING → COMPLETED: Animation stops, icon becomes static
   - PROCESSING → FALLBACK: Animation stops, shows error icon
   - PROCESSING → ERROR: Animation stops, shows error icon

3. **View recycling**:
   - Scroll list while processing → no animation artifacts
   - No duplicate animations on same item
   - Memory usage stable during scrolling

**Code Locations**:
- `RecordingManagerActivity.kt` (lines 627-628): `processingAnimator` property
- `RecordingManagerActivity.kt` (lines 738-764): Animation helper methods
- `RecordingManagerActivity.kt` (lines 605-608): `onViewRecycled()` cleanup

#### Long Transcription Testing

**Feature**: Complete transcription of audio files longer than 10 seconds.

**Implementation Details**:
- Google Cloud Speech-to-Text API returns multiple result chunks for longer audio
- All chunks are joined with spaces using `joinToString(" ")`
- Ensures no truncation of transcribed content

**Test Cases**:
1. Record audio file longer than 10 seconds with continuous speech
2. Transcribe the recording
3. Compare transcribed text length with actual spoken content
4. Verify no content is missing from middle or end
5. Check that all result chunks are joined properly

**Expected Behavior**:
- Complete transcription of all spoken words
- No truncation after first few words
- Multiple result chunks properly joined with spaces

#### UI/UX Testing Checklist

**Transcription Button States**:
- NOT_STARTED: "Transcribe" button (enabled)
- PROCESSING: "Processing" button (disabled)
- COMPLETED: "Retranscribe" button (enabled)
- FALLBACK: "Retry" button (enabled)
- ERROR: "Retry" button (enabled)
- DISABLED: "Disabled" button (disabled)

**Playback Controls**:
- Play button toggles to "Stop" during playback
- Only one play control per recording (action row button)
- Starting new playback stops previous playback
- Playback completion resets button to "Play"

**Regression Testing**:
- [ ] Recording list loads correctly
- [ ] Date/time displays properly
- [ ] GPS coordinates display correctly
- [ ] Delete button works
- [ ] Export functionality works
- [ ] Manual transcription editing works
- [ ] Status icons display correctly

---

## Development Workflow

### Code Style

- **Language**: Kotlin
- **Formatting**: Follow Android Studio default formatting
- **Naming conventions**:
  - Classes: PascalCase (e.g., `RecordingManagerActivity`)
  - Functions: camelCase (e.g., `startRecording()`)
  - Variables: camelCase (e.g., `mediaRecorder`)
  - Constants: UPPER_SNAKE_CASE (e.g., `MAX_DURATION`)

### Git Workflow

1. **Create feature branch**:
   ```bash
   git checkout -b feature/my-new-feature
   ```

2. **Make changes and commit**:
   ```bash
   git add .
   git commit -m "Add new feature: description"
   ```

3. **Push to remote**:
   ```bash
   git push origin feature/my-new-feature
   ```

4. **Create pull request** on GitHub

### Adding New Features

#### Example: Add new export format

1. **Update UI** (`activity_recording_manager.xml`):
   - Add new button or menu item

2. **Implement export logic** (`RecordingManagerActivity.kt`):
   ```kotlin
   private fun exportAsJson(recordings: List<Recording>) {
       // Implementation here
   }
   ```

3. **Add to export dialog**:
   ```kotlin
   val formats = arrayOf("Audio Only", "GPX Only", "CSV Only", "JSON", "All Formats")
   ```

4. **Add tests** (`TestSuite.kt`):
   ```kotlin
   runTest("JSON Export Format") {
       // Test implementation
   }
   ```

5. **Update documentation** (`USER_GUIDE.md`, this file)

### Debugging Tips

1. **Enable debug logging**:
   - Settings → Debug Logging → Enable
   - View logs in Debug Log activity

2. **Check database**:
   - Use Android Studio Database Inspector
   - Or query via DAO in debug code

3. **Test with mock data**:
   - Use test database: `getTestDatabase(context)`
   - Insert test recordings

4. **Check permissions**:
   - Verify all permissions granted
   - Test on different Android versions

5. **Network issues**:
   - Check `NetworkUtils.isOnline()`
   - Test with offline mode

---

## ProGuard Configuration

### Overview

Release builds use ProGuard/R8 for:
- **Code obfuscation**: Makes reverse engineering harder
- **Minification**: Reduces APK size by removing unused code
- **Resource shrinking**: Removes unused resources
- **Optimization**: Improves runtime performance

### Configuration File

ProGuard rules are defined in `app/proguard-rules.pro` with comprehensive rules for:

**Android & AndroidX:**
- Preserve native methods
- Keep custom view constructors
- Maintain Parcelables and Serializables

**Room Database:**
- Keep `@Entity`, `@Dao`, `@Database` annotations
- Preserve type converters
- Maintain migration classes

**Kotlin:**
- Keep Kotlin metadata for reflection
- Preserve coroutines
- Maintain data class copy() methods

**Google Cloud & APIs:**
- Keep Google Cloud Speech-to-Text classes
- Preserve gRPC and Protobuf
- Maintain Google Auth libraries

**OkHttp:**
- Keep OkHttp platform classes
- Preserve Okio

### Testing ProGuard Builds

**Always test release builds** before distribution:

```bash
# Build release APK
./build-release.sh

# Install on device
adb install app/build/outputs/apk/release/app-release.apk

# Test all features:
# - Recording with GPS
# - Audio playback
# - Transcription
# - Export functionality
# - Settings changes
```

### ProGuard Mapping File

After each release build, save the mapping file:

```bash
# Location
app/build/outputs/mapping/release/mapping.txt

# This file is needed to:
# - Deobfuscate crash reports
# - Debug production issues
# - Understand stack traces
```

**Important:** Store mapping files for each release version. Without it, crash reports will be unreadable.

### Troubleshooting ProGuard Issues

**Symptom:** App crashes in release but works in debug

**Common causes:**
1. Missing ProGuard rules for a library
2. Reflection-based code not preserved
3. Serialization issues

**Solutions:**
```bash
# Check ProGuard output
cat app/build/outputs/mapping/release/usage.txt

# Add keep rules for missing classes
-keep class com.example.MissingClass { *; }

# Enable debugging (temporarily)
-dontobfuscate
```

---

## Release Process

### Pre-release Checklist

1. **Run all tests**:
   - [ ] Unit tests pass: `./gradlew test`
   - [ ] On-device test suite passes (82/82)
   - [ ] Manual testing of all features
   - [ ] Test on multiple Android versions (8, 10, 12, 14)

2. **Code quality**:
   - [ ] No compiler warnings
   - [ ] Lint checks pass: `./gradlew lintRelease`
   - [ ] Code reviewed
   - [ ] ProGuard mapping file saved

3. **Security**:
   - [ ] No credentials in source code
   - [ ] gradle.properties and keystore.properties in .gitignore
   - [ ] Release APK signed with production keystore
   - [ ] Keystore backed up securely
   - [ ] API keys restricted in Google Cloud Console

4. **ProGuard**:
   - [ ] ProGuard enabled (minifyEnabled true)
   - [ ] Resource shrinking enabled (shrinkResources true)
   - [ ] Release APK tested with all features
   - [ ] ProGuard mapping file saved for this version
   - [ ] No crashes with ProGuard enabled

5. **Documentation**:
   - [ ] README.md updated
   - [ ] USER_GUIDE.md updated
   - [ ] DEVELOPER_GUIDE.md updated (this file)
   - [ ] ARCHITECTURE.md updated
   - [ ] CHANGELOG.md updated
   - [ ] SECURITY.md reviewed

6. **Verification**:
   - [ ] Run verification script: `./verify-release.sh`
   - [ ] Build release APK: `./build-release.sh`
   - [ ] Install on clean device (not development device)
   - [ ] Test all features on release build
   - [ ] Check app size is reasonable
   - [ ] Verify version number is correct

### Version Numbering

The app uses **automated git-based versioning** - versions are automatically derived from git tags and commit history.

#### Semantic Versioning

Follow semantic versioning for git tags: `vMAJOR.MINOR.PATCH`

- **MAJOR**: Breaking changes
- **MINOR**: New features (backward compatible)
- **PATCH**: Bug fixes

#### How It Works

**versionName** (from `git describe --tags`):
- On exact tag: `1.0.0` (strips 'v' prefix from tag `v1.0.0`)
- Between tags: `0.0.3-18-gd10b8b8` (format: `tag-commits-hash`)
  - `0.0.3`: Last tag
  - `18`: Number of commits since tag
  - `gd10b8b8`: Git commit hash
- With uncommitted changes: adds `-dirty` suffix
- Fallback: `1.0.0` (when git unavailable)

**versionCode** (from `git rev-list --count HEAD`):
- Automatically increments with each commit
- Ensures Google Play accepts updates (requires increasing versionCode)
- Current approach: Total commit count in branch history
- Fallback: `1` (when git unavailable)

**No manual version editing required** - both values are computed automatically in `app/build.gradle`:

```gradle
def getVersionName() {
    // Uses git describe --tags --always --dirty
}

def getVersionCode() {
    // Counts commits: git rev-list --count HEAD
}

android {
    defaultConfig {
        versionCode getVersionCode()    // Auto: 116, 117, 118...
        versionName getVersionName()    // Auto: "0.0.3-18-gd10b8b8"
    }
}
```

### Creating Release

1. **Update CHANGELOG.md**:
   ```markdown
   ## [1.1.0] - 2026-01-26

   ### Added
   - New export format: JSON
   - Database test isolation

   ### Fixed
   - GPS timeout issue
   ```

2. **Commit all changes**:
   ```bash
   git add .
   git commit -m "Prepare release v1.1.0"
   git push
   ```

3. **Create and push git tag** (this automatically sets the version):
   ```bash
   git tag -a v1.1.0 -m "Release version 1.1.0"
   git push origin v1.1.0
   ```

   The tag will automatically set:
   - `versionName` = `1.1.0` (tag stripped of 'v' prefix)
   - `versionCode` = commit count at that tag

4. **Build release APK** (checkout the tag first):
   ```bash
   git checkout v1.1.0
   ./gradlew assembleRelease
   ```

   The built APK will have version `1.1.0` from the tag.

5. **Test release build**:
   - Install on clean device: `adb install app/build/outputs/apk/release/app-release.apk`
   - Run through all workflows
   - Check for crashes
   - Verify version in Settings → About

6. **Return to development**:
   ```bash
   git checkout main  # or your development branch
   ```

   Development builds will show version like `1.1.0-5-gabc1234` (5 commits after v1.1.0).

6. **Create GitHub release**:
   - Upload APK
   - Copy changelog
   - Mark as release/pre-release

---

## Common Tasks

### Adding a New Database Field

1. **Update entity** (`Recording.kt`):
   ```kotlin
   data class Recording(
       // ...
       val newField: String? = null
   )
   ```

2. **Create migration** (`RecordingMigration.kt`):
   ```kotlin
   val MIGRATION_1_2 = object : Migration(1, 2) {
       override fun migrate(database: SupportSQLiteDatabase) {
           database.execSQL("ALTER TABLE recordings ADD COLUMN newField TEXT")
       }
   }
   ```

3. **Update database version** (`RecordingDatabase.kt`):
   ```kotlin
   @Database(entities = [Recording::class], version = 2)
   ```

4. **Add migration** to database builder:
   ```kotlin
   Room.databaseBuilder(...)
       .addMigrations(MIGRATION_1_2)
       .build()
   ```

### Changing Audio Format

Modify `OverlayService.kt`:

```kotlin
private fun startRecording(location: Location?) {
    mediaRecorder = MediaRecorder().apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)

        // Change format here
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setAudioEncodingBitRate(128000)
        setAudioSamplingRate(44100)

        // Update file extension
        val extension = ".m4a"
        // ...
    }
}
```

### Adding New Permission

1. **Add to `AndroidManifest.xml`**:
   ```xml
   <uses-permission android:name="android.permission.NEW_PERMISSION" />
   ```

2. **Request at runtime** (for dangerous permissions):
   ```kotlin
   if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEW_PERMISSION)
       != PackageManager.PERMISSION_GRANTED) {
       ActivityCompat.requestPermissions(this,
           arrayOf(Manifest.permission.NEW_PERMISSION),
           REQUEST_CODE)
   }
   ```

3. **Add to test suite** (`TestSuite.kt`):
   ```kotlin
   runTest("NEW_PERMISSION Permission") {
       val granted = ContextCompat.checkSelfPermission(context,
           Manifest.permission.NEW_PERMISSION) == PackageManager.PERMISSION_GRANTED
       TestResult("NEW_PERMISSION Permission", true,
           if (granted) "Permission granted" else "Permission not granted")
   }
   ```

---

## Troubleshooting

### Build Issues

**Problem**: Gradle sync fails
- **Solution**: Check internet connection, clear Gradle cache (`~/.gradle/caches`)

**Problem**: Compilation errors after pull
- **Solution**: Clean and rebuild: `./gradlew clean build`

**Problem**: Missing BuildConfig fields
- **Solution**: Check `gradle.properties` for required keys

### Runtime Issues

**Problem**: App crashes on start
- **Solution**: Check logcat, verify permissions in manifest

**Problem**: No GPS location
- **Solution**: Enable location services, grant location permission

**Problem**: Recording fails
- **Solution**: Check microphone permission, ensure SD card not full

**Problem**: Transcription not working
- **Solution**: Verify Google Cloud credentials, check network connection

### Testing Issues

**Problem**: Tests fail on device
- **Solution**: Clear app data, reinstall app, check network connection

**Problem**: Database tests contaminate production data
- **Solution**: Verify tests use `getTestDatabase()`, not `getDatabase()`

---

## Resources

### Android Documentation
- **Services**: https://developer.android.com/guide/components/services
- **Room Database**: https://developer.android.com/training/data-storage/room
- **MediaRecorder**: https://developer.android.com/guide/topics/media/mediarecorder
- **Location**: https://developer.android.com/training/location

### API Documentation
- **Google Cloud Speech-to-Text**: https://cloud.google.com/speech-to-text/docs

### Libraries Used
- **Room**: Android persistence library
- **Lifecycle**: Android Architecture Components
- **Material Design**: UI components

---

## Contributing

### Reporting Issues

- Use GitHub Issues
- Provide clear description
- Include steps to reproduce
- Attach logcat if applicable

### Submitting Changes

1. Fork the repository
2. Create feature branch
3. Make changes with tests
4. Submit pull request
5. Wait for review

### Code Review Process

- All changes require review
- Tests must pass
- Documentation must be updated
- Code style must be consistent

---

## Contact

For questions or support:
- **GitHub Issues**: https://github.com/yourusername/autorecord-app/issues
- **Documentation**: See [README.md](../README.md), [USER_GUIDE.md](USER_GUIDE.md), [ARCHITECTURE.md](ARCHITECTURE.md)

---

**Last Updated**: 2026-01-25
**Version**: 1.0.0
