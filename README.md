# Motorcycle Voice Notes

A lightweight Android app for motorcyclists to quickly record voice notes with GPS location tracking while riding.

## What It Does

Motorcycle Voice Notes is designed for hands-free operation while riding. When you launch the app:

1. **Speaks your GPS coordinates** via text-to-speech
2. **Captures your voice message** through speech recognition
3. **Records audio** (configurable 1-99 seconds, default 10 seconds)
4. **Saves the recording** with GPS coordinates in the filename
5. **Creates a GPX waypoint** with your transcribed message
6. **Launches your preferred app** (navigation, music, etc.)

## Key Features

- **GPS Tagging**: Every recording is named with precise coordinates and timestamp
- **Speech-to-Text**: Your spoken words become waypoint descriptions in the GPX file
- **Bluetooth Support**: Automatically uses your Bluetooth headset/helmet system
- **Customizable**: Set recording duration, storage location, and trigger app
- **GPX Export**: All locations saved to `acquired_locations.gpx` for easy import into mapping apps
- **OpenStreetMap Integration**: Optionally create notes at recording locations in your OSM account
- **Minimal Interaction**: Launch once, everything happens automatically
- **Overlay Bubble**: Visual feedback during recording with transcribed text display

## Perfect For

- Recording riding observations and directions
- Making quick voice memos without stopping
- Tagging interesting locations with voice notes
- Building route waypoints for future reference
- Hands-free operation with Bluetooth helmet systems

## Quick Start

### Installation

1. Download the APK from [Releases](https://github.com/c0dev0id/autorecord-app/releases) or [Actions](https://github.com/c0dev0id/autorecord-app/actions)
2. Install on your Android device (Android 8.0+)
3. Launch the app and complete initial setup:
   - Grant permissions (microphone, location, Bluetooth, overlay)
   - Choose recording storage location
   - Select trigger app (auto-detects DMD2 if installed)
   - Optionally set recording duration (default: 10 seconds)

### Usage

Just launch the app whenever you want to record a note. The app handles everything automatically and returns you to your selected app.

### OpenStreetMap Integration (Optional)

The app can automatically create notes at recording locations in your OpenStreetMap account:

1. Open the Configuration screen
2. Check "Add Location Note to OSM Account"
3. Click "Connect to OpenStreetMap" and sign in with your OSM account
4. Once connected, notes will be created automatically after each recording (when online)

**Note**: This requires an OpenStreetMap account and internet connectivity. If offline, note creation is skipped automatically without affecting recordings.

## File Format

- **Audio**: MP3 files with AAC encoding (128 kbps, 44.1 kHz)
- **Naming**: `latitude,longitude_timestamp.mp3`
- **Example**: `34.052235,-118.243683_20260120_143022.mp3`
- **GPX File**: `acquired_locations.gpx` with waypoints containing your transcribed voice notes

## Requirements

- Android 8.0 (API 26) or higher
- GPS capability
- Microphone
- Storage access
- Bluetooth (optional, for headset support)
- Internet connection (optional, for OpenStreetMap integration)

## Building from Source

See [BUILD_INSTRUCTIONS.md](BUILD_INSTRUCTIONS.md) for complete build setup and instructions.

## Technical Details

- **Language**: Kotlin
- **Build System**: Gradle
- **Location**: Google Play Services Location API
- **Audio**: MediaRecorder with AAC encoding
- **Speech Recognition**: Android SpeechRecognizer API
- **Text-to-Speech**: Android TTS Engine
- **HTTP Client**: Retrofit + OkHttp (for OSM API)
- **OAuth**: AppAuth library (for OSM authentication)
- **Secure Storage**: EncryptedSharedPreferences (for tokens)

## License

This project is provided as-is for personal use.

---

**Safety First**: Never manipulate your phone while riding. Configure the app before you start riding, and use voice commands or pull over safely to launch it.
