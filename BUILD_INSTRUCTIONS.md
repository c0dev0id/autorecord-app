# Build Instructions for Motorcycle Voice Notes App

## ⚠️ Important: Optional Feature Configuration

This app includes **optional features** that require credentials to function:
- **Google Cloud Speech-to-Text** - For transcription
- **OpenStreetMap OAuth** - For creating OSM notes

**The app works without these credentials** - it will still record audio and capture GPS coordinates. For detailed setup instructions, see **[CONFIGURATION.md](CONFIGURATION.md)**.

---

## Prerequisites

To build this Android app, you need the following installed on your system:

1. **Java Development Kit (JDK) 8 or higher**
   - Check: `java -version`
   - Download from: https://adoptium.net/

2. **Android SDK**
   - Option 1: Install Android Studio (recommended)
     - Download from: https://developer.android.com/studio
     - Android Studio includes the Android SDK
   - Option 2: Install Android SDK command-line tools only
     - Download from: https://developer.android.com/studio#command-tools

3. **Android SDK Platform and Build Tools**
   - SDK Platform API 34 (Android 14)
   - Build Tools 34.0.0 or higher

## Setup Steps

### 1. Install Android SDK

If you installed Android Studio:
- Open Android Studio
- Go to Tools > SDK Manager
- Install:
  - Android SDK Platform 34
  - Android SDK Build-Tools 34.0.0 or higher
  - Android SDK Platform-Tools
  - Android SDK Tools

### 2. Set ANDROID_HOME Environment Variable

**Linux/macOS:**
```bash
export ANDROID_HOME=$HOME/Android/Sdk  # or your SDK path
export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools
```

Add these lines to your `~/.bashrc` or `~/.zshrc` to make them permanent.

**Windows:**
```cmd
setx ANDROID_HOME "C:\Users\YourUsername\AppData\Local\Android\Sdk"
setx PATH "%PATH%;%ANDROID_HOME%\tools;%ANDROID_HOME%\platform-tools"
```

### 3. Update local.properties (Alternative)

Instead of setting environment variables, you can edit `local.properties`:
```
sdk.dir=/path/to/your/android/sdk
```

For example:
- Linux/macOS: `sdk.dir=/home/username/Android/Sdk`
- Windows: `sdk.dir=C\:\\Users\\username\\AppData\\Local\\Android\\Sdk`

## Building the APK

### Google Cloud Service Account Setup (Optional)

> **Note:** For complete setup instructions including troubleshooting and GitHub Actions configuration, see **[CONFIGURATION.md](CONFIGURATION.md)**.

The app can transcribe audio using Google Cloud Speech-to-Text API. This is optional - the app works without it, but transcription features will be disabled.

**Quick Setup:**

1. Create a Google Cloud project at https://console.cloud.google.com
2. Enable the "Speech-to-Text API"
3. Create a service account with "Cloud Speech-to-Text API User" role
4. Download the JSON key file
5. Copy the template file:
   ```bash
   cp gradle.properties.template gradle.properties
   ```
6. Edit `gradle.properties` and add the JSON content (on a single line):
   ```
   GOOGLE_CLOUD_SERVICE_ACCOUNT_JSON={"type":"service_account","project_id":"your-project",...}
   ```

**Important:** Keep this file private and do not commit to version control.

For detailed instructions, see [CONFIGURATION.md](CONFIGURATION.md).

### CI/CD with GitHub Actions

> **Note:** For complete GitHub Actions configuration instructions, see **[CONFIGURATION.md](CONFIGURATION.md)**.

If you're using GitHub Actions to build your app, set these repository secrets:

1. Go to repository Settings → Secrets and variables → Actions
2. Add secrets:
   - `GOOGLE_CLOUD_SERVICE_ACCOUNT_JSON` - Your service account JSON (multi-line OK)
   - `OSM_CLIENT_ID` - Your OSM OAuth Client ID

The workflows will automatically inject these during builds.

### OpenStreetMap OAuth Setup (Optional)

> **Note:** For complete setup instructions including troubleshooting, see **[CONFIGURATION.md](CONFIGURATION.md)**.

The app can create OSM notes with your voice recordings. This is optional.

**Quick Setup:**

1. Register an OAuth 2.0 application at https://www.openstreetmap.org/oauth2/applications
   - Redirect URI: `app.voicenotes.motorcycle://oauth`
   - Requested scopes: `read_prefs` and `write_notes`
   
2. Copy your "Client ID"

3. Edit `gradle.properties`:
   ```
   OSM_CLIENT_ID=your_actual_client_id_here
   ```

4. Rebuild the app

5. In app settings, use "Bind OSM Account" to authenticate

**Important:** Keep this file private and do not commit to version control.

For detailed instructions, see [CONFIGURATION.md](CONFIGURATION.md).

### Option 1: Using Gradle (Command Line)

1. Navigate to the project directory:
   ```bash
   cd /path/to/autorecord-app
   ```

2. Make gradlew executable (Linux/macOS only):
   ```bash
   chmod +x gradlew
   ```

3. Build the debug APK:
   ```bash
   ./gradlew assembleDebug
   ```

4. The APK will be generated at:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

### Option 2: Using Android Studio

1. Open Android Studio
2. Select "Open an Existing Project"
3. Navigate to the project directory and select it
4. Wait for Gradle sync to complete
5. Click Build > Build Bundle(s) / APK(s) > Build APK(s)
6. The APK will be generated at: `app/build/outputs/apk/debug/app-debug.apk`

### Building Release APK

For a release APK (smaller, optimized):
```bash
./gradlew assembleRelease
```

**Note**: 
- If you have configured signing (see below), this will create a signed APK
- Without signing configuration, this creates an unsigned APK suitable for testing
- For production use, you should use a signed APK

### Building Signed Release APK

To build a properly signed release APK for distribution:

1. **Generate a keystore** (one-time setup):
   ```bash
   keytool -genkey -v -keystore release.keystore -alias motorcycle-voice-notes \
     -keyalg RSA -keysize 2048 -validity 10000
   ```

2. **Create keystore.properties** (one-time setup):
   ```bash
   cp keystore.properties.template keystore.properties
   ```
   
   Edit `keystore.properties` with your details:
   ```properties
   storeFile=/absolute/path/to/release.keystore
   storePassword=your-keystore-password
   keyAlias=motorcycle-voice-notes
   keyPassword=your-key-password
   ```

3. **Build the signed APK**:
   ```bash
   ./gradlew assembleRelease
   ```

4. **Output location**:
   ```
   app/build/outputs/apk/release/app-release.apk
   ```

**For complete signing instructions**, including troubleshooting, Google Play publishing, and CI/CD setup, see [SIGNING.md](SIGNING.md).

**Security**: Never commit your keystore or `keystore.properties` file! They are already in `.gitignore`.

## Installing the APK

### On a Physical Device

1. Enable Developer Options on your Android device:
   - Go to Settings > About Phone
   - Tap "Build Number" 7 times

2. Enable USB Debugging:
   - Go to Settings > Developer Options
   - Enable "USB Debugging"

3. Connect your device via USB

4. Install using ADB:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

5. Or transfer the APK file to your device and install it manually

### Using an Emulator

1. Start an Android emulator from Android Studio
2. Drag and drop the APK file onto the emulator window
3. Or use ADB: `adb install app/build/outputs/apk/debug/app-debug.apk`

## First-Time App Setup

When you first launch the app, you need to:

1. **Grant Permissions**:
   - Microphone permission (for recording)
   - Fine Location permission (for GPS coordinates)
   - Overlay permission (for bubble display)
   - Storage permission (for saving recordings)
   - Bluetooth permission (Android 12+, for headset support)

2. **Configure Save Directory**:
   - Go to Settings in the app
   - Choose a directory where recordings will be saved
   - Grant "All files access" if prompted (Android 11+)

3. **Configure Recording Duration** (Optional):
   - Set recording duration (1-99 seconds, default 10)

4. **Configure Online Processing** (Optional):
   - Enable/disable "Try Online processing during ride" (default: enabled)
   - This requires Google Cloud service account credentials

5. **Configure OSM Integration** (Optional):
   - Click "Bind OSM Account" to authenticate with OpenStreetMap
   - Enable "Add OSM Note" checkbox to create notes during recording
   - This requires OSM OAuth setup (see above)

## App Usage

After setup, the app will:
1. Show a small overlay bubble when started
2. Acquire your GPS location
3. Announce "Location acquired, recording started" via TTS
4. Record audio for the configured duration (default 10 seconds)
5. Save the recording with filename format: `latitude,longitude_timestamp.ogg` (or `.m4a` on Android 8-9)
6. **If online processing is enabled and internet is available**:
   - Display "Online: Transcribing:" in the overlay
   - Transcribe the audio using Google Cloud Speech-to-Text
   - Create/update GPX waypoint with transcribed text (replaces duplicates at same coordinates)
   - If OSM note creation is enabled and authenticated:
     - Display "Online: Creating OSM Note"
     - Create an OSM note at the location with the transcribed text
     - Display "Online: OSM Note created." on success
7. **If offline or online processing disabled**:
   - Skip transcription and post-processing
   - Recording remains available for later batch processing
8. Quit automatically after completion

### Manual Batch Processing

Use the "Run Online Processing" button in settings to:
- Process all audio files (.ogg and .m4a) in your recording directory
- Transcribe each file using Google Cloud Speech-to-Text
- Create/update GPX waypoints (replaces duplicates)
- Create OSM notes if enabled and authenticated
- Display "Processing..." during operation
- Show "Processing complete" when done

This is useful for:
- Processing recordings made while offline
- Re-processing files with updated transcription settings
- Creating OSM notes for previously recorded locations

## Troubleshooting

### Build Errors

**Error: SDK location not found**
- Solution: Set ANDROID_HOME environment variable or update local.properties

**Error: Failed to find target with hash string 'android-34'**
- Solution: Install Android SDK Platform 34 via SDK Manager

**Error: Gradle version incompatible**
- Solution: Update gradle-wrapper.properties or use the version specified

### Runtime Issues

**Location not acquired**
- Ensure GPS is enabled on the device
- Grant location permission when prompted
- Try outdoors for better GPS signal

**Recording fails**
- Grant microphone permission
- Ensure save directory is configured
- Check storage space availability

**Cannot save files (Android 11+)**
- Go to Settings app > Apps > Motorcycle Voice Notes > Permissions
- Grant "All files access" permission

## Project Structure

```
autorecord-app/
├── app/
│   ├── src/main/
│   │   ├── java/com/voicenotes/motorcycle/
│   │   │   ├── MainActivity.kt          # Main recording logic
│   │   │   └── SettingsActivity.kt      # Configuration screen
│   │   ├── res/
│   │   │   ├── layout/                  # UI layouts
│   │   │   ├── values/                  # Strings and resources
│   │   │   └── mipmap/                  # App icons
│   │   └── AndroidManifest.xml          # App configuration
│   └── build.gradle                     # App-level build config
├── build.gradle                         # Project-level build config
├── settings.gradle                      # Project settings
└── gradlew                             # Gradle wrapper script
```

## Technical Details

- **Language**: Kotlin
- **Minimum SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)
- **Recording Format**: OGG with Opus codec (Android 10+) or MPEG-4 with AAC codec (Android 8-9)
- **Recording Duration**: 10 seconds (configurable in code)
- **Location**: High-accuracy GPS
- **Text-to-Speech**: Android built-in TTS engine

## License

This project is provided as-is for personal use.
