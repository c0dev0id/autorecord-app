# Build Instructions for Motorcycle Voice Notes App

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
   - Storage permission (for saving recordings)

2. **Configure Save Directory**:
   - Go to Settings in the app
   - Choose a directory where recordings will be saved
   - Grant "All files access" if prompted (Android 11+)

3. **Select Trigger App**:
   - Choose an app to launch after recording completes
   - This could be a navigation app, music player, etc.

## App Usage

After setup, the app will:
1. Acquire your GPS location when started
2. Play audio: "Location acquired, recording for 10 seconds"
3. Record audio for 10 seconds
4. Play audio: "Recording stopped"
5. Save the recording with filename format: `latitude_longitude_timestamp.3gp`
6. Launch the configured trigger app

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
- **Recording Format**: 3GP (AMR-NB codec)
- **Recording Duration**: 10 seconds (configurable in code)
- **Location**: High-accuracy GPS
- **Text-to-Speech**: Android built-in TTS engine

## License

This project is provided as-is for personal use.
