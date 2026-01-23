# Motorcycle Voice Notes

A lightweight Android app for motorcyclists to quickly record voice notes with GPS location tracking while riding.

## What It Does

Motorcycle Voice Notes is designed for hands-free operation while riding. When you launch the app:

1. **Shows a small overlay bubble** at the top of the screen
2. **Announces location acquired** via text-to-speech
3. **Records audio** (configurable 1-99 seconds, default 10 seconds)
4. **Saves the recording** with GPS coordinates in the filename
5. **Transcribes your voice message** using Google Cloud Speech-to-Text (optional, during or after ride)
6. **Creates a GPX waypoint** with your transcribed message
7. **Optional OSM Note creation** if enabled and authenticated
8. **Quits automatically** so you stay in your current app

## Key Features

- **GPS Tagging**: Every recording is named with precise coordinates and timestamp
- **Speech-to-Text**: Your spoken words become waypoint descriptions in the GPX file
- **GPX Waypoint Management**: Duplicate coordinates are replaced, not added twice
- **OpenStreetMap Integration**: Optionally create OSM notes with your voice recordings
- **Online Processing**: During ride transcription with Google Cloud Speech-to-Text API
- **Batch Processing**: Process all recorded files later with manual button
- **Bluetooth Support**: Automatically uses your Bluetooth headset/helmet system
- **Customizable**: Set recording duration and storage location
- **GPX Export**: All locations saved to `voicenote_waypoint_collection.gpx` for easy import into mapping apps
- **Minimal Interaction**: Launch once, everything happens automatically
- **Overlay Bubble**: Visual feedback during recording with transcribed text display
- **Background Operation**: Works while other apps are in the foreground

## Perfect For

- Recording riding observations and directions
- Making quick voice memos without stopping
- Tagging interesting locations with voice notes
- Building route waypoints for future reference
- Hands-free operation with Bluetooth helmet systems
- Contributing notes to OpenStreetMap while riding

## Quick Start

### Installation

1. Download the APK from [Releases](https://github.com/c0dev0id/autorecord-app/releases) or [Actions](https://github.com/c0dev0id/autorecord-app/actions)
2. Install on your Android device (Android 8.0+)
3. Launch the app and complete initial setup:
   - Grant permissions (microphone, location, Bluetooth, overlay)
   - Choose recording storage location
   - Optionally set recording duration (default: 10 seconds)

> **Note:** Pre-built APKs may not have Google Cloud or OSM credentials configured. See **[CONFIGURATION.md](CONFIGURATION.md)** to understand which features require setup.

### Usage

Just launch the app whenever you want to record a note. A small overlay bubble will appear showing recording status, and the app will quit automatically when done, leaving you in whatever app you were using.

### Online Processing Options

- **During Ride**: Enable "Try Online processing during ride" in settings (default: on)
  - Transcribes audio immediately after recording
  - Creates GPX waypoint with transcribed text
  - Optionally creates OSM note if authenticated
  - Skips processing if offline

- **Manual Batch Processing**: Use "Run Online Processing" button in settings
  - Processes all m4a files in your recording directory
  - Transcribes each file and creates/updates GPX waypoints
  - Creates OSM notes if enabled and authenticated
  - Perfect for processing recordings made while offline

## File Format

- **Audio**: OGG files with Opus encoding (32 kbps, 48 kHz) on Android 10+, or MPEG-4 files with AAC encoding (128 kbps, 44.1 kHz) on older devices
- **Naming**: `latitude,longitude_timestamp.ogg` (or `.m4a` on Android 8-9)
- **Example**: `34.052235,-118.243683_20260120_143022.ogg`
- **GPX File**: `voicenote_waypoint_collection.gpx` with waypoints containing your transcribed voice notes

## Requirements

- Android 8.0 (API 26) or higher
- GPS capability
- Microphone
- Storage access
- Bluetooth (optional, for headset support)

### Optional Online Features

The app includes optional features that require configuration:

- **Internet connection** - Required for transcription and OSM features
- **Google Cloud credentials** - For Speech-to-Text transcription ([setup guide](CONFIGURATION.md))
- **OSM OAuth account** - For creating OpenStreetMap notes ([setup guide](CONFIGURATION.md))

> **Note:** The app works perfectly without these features - it will still record audio and capture GPS coordinates. See **[CONFIGURATION.md](CONFIGURATION.md)** for setup instructions.

## OpenStreetMap Integration

The app can create OSM notes with your voice recordings. This requires configuration:

1. **For developers**: See **[CONFIGURATION.md](CONFIGURATION.md)** for complete setup instructions
2. **For users**: Pre-built APKs may not have OSM credentials - build from source if you need this feature

Quick overview:
- Register an OAuth 2.0 application at https://www.openstreetmap.org/oauth2/applications
- Set redirect URI to: `app.voicenotes.motorcycle://oauth`
- Request scopes: `read_prefs write_notes`
- Configure your Client ID (see [CONFIGURATION.md](CONFIGURATION.md))
- Bind your OSM account in the app settings
- Enable "Add OSM Note" checkbox

## Building from Source

See [BUILD_INSTRUCTIONS.md](BUILD_INSTRUCTIONS.md) for complete build setup and instructions.

## Technical Details

- **Language**: Kotlin
- **Build System**: Gradle
- **Location**: Google Play Services Location API
- **Audio**: MediaRecorder with Opus encoding (OGG container, .ogg files) on Android 10+, or AAC encoding (MPEG-4 container, .m4a files) on older devices
- **Text-to-Speech**: Android TTS Engine (for status announcements)
- **Speech-to-Text**: Google Cloud Speech-to-Text API (for transcription)
- **OAuth**: AppAuth library for OpenStreetMap OAuth 2.0
- **HTTP**: OkHttp for OSM API calls

## License

This project is provided as-is for personal use.

---

**Safety First**: Never manipulate your phone while riding. Configure the app before you start riding, and use voice commands or pull over safely to launch it.
