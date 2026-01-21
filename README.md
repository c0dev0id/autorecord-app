# Motorcycle Voice Notes

A lightweight Android app for motorcyclists to quickly record voice notes with GPS location tracking while riding.

## What It Does

Motorcycle Voice Notes is designed for hands-free operation while riding. When you launch the app:

1. **Shows a small overlay bubble** at the top of the screen
2. **Speaks your GPS coordinates** via text-to-speech
3. **Captures your voice message** through speech recognition
4. **Records audio** (configurable 1-99 seconds, default 10 seconds)
5. **Saves the recording** with GPS coordinates in the filename
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
   - Configure Google Cloud service account for transcription (optional)
   - Bind OSM account for note creation (optional)

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

- **Audio**: MPEG-4 files with AAC encoding (128 kbps, 44.1 kHz)
- **Naming**: `latitude,longitude_timestamp.m4a`
- **Example**: `34.052235,-118.243683_20260120_143022.m4a`
- **GPX File**: `voicenote_waypoint_collection.gpx` with waypoints containing your transcribed voice notes

## Requirements

- Android 8.0 (API 26) or higher
- GPS capability
- Microphone
- Storage access
- Bluetooth (optional, for headset support)
- Internet connection (optional, for transcription and OSM features)
- Google Cloud service account credentials (optional, for transcription)
- OpenStreetMap OAuth 2.0 account (optional, for note creation)

## OpenStreetMap Integration

To use the OSM note creation feature:

1. Register an OAuth 2.0 application at https://www.openstreetmap.org/oauth2/applications
   - Set redirect URI to: `app.voicenotes.motorcycle://oauth`
   - Request scopes: `read_prefs write_notes`
2. Update `CLIENT_ID` in `OsmOAuthManager.kt` with your application's client ID
3. Bind your OSM account in the app settings
4. Enable "Add OSM Note" checkbox

## Building from Source

See [BUILD_INSTRUCTIONS.md](BUILD_INSTRUCTIONS.md) for complete build setup and instructions.

## Technical Details

- **Language**: Kotlin
- **Build System**: Gradle
- **Location**: Google Play Services Location API
- **Audio**: MediaRecorder with AAC encoding
- **Speech Recognition**: Android SpeechRecognizer API
- **Text-to-Speech**: Android TTS Engine
- **Speech-to-Text**: Google Cloud Speech-to-Text API (post-processing)
- **OAuth**: AppAuth library for OpenStreetMap OAuth 2.0
- **HTTP**: OkHttp for OSM API calls

## License

This project is provided as-is for personal use.

---

**Safety First**: Never manipulate your phone while riding. Configure the app before you start riding, and use voice commands or pull over safely to launch it.
