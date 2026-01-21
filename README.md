# Motorcycle Voice Notes

An Android app designed for recording voice notes while riding a motorcycle. The app automatically acquires your GPS location, records a 10-second voice note with speech-to-text transcription, saves it with location and timestamp information, and then launches your preferred navigation or music app.

## Screenshots

> **Note**: Screenshots will be added after building and testing the app on an Android device. See [SCREENSHOTS.md](SCREENSHOTS.md) for detailed screenshot requirements and guidelines.

### Main Screen
<!-- Screenshot of main activity showing recording interface -->

### First Run Tutorial
<!-- Screenshot of tutorial dialog explaining how the app works -->

### Settings Screen
<!-- Screenshot of configuration screen -->

### App Selection
<!-- Screenshot of app chooser dialog -->

For detailed screenshot specifications, see [SCREENSHOTS.md](SCREENSHOTS.md).

## Features

- **Automatic GPS Location**: Acquires precise GPS coordinates on app launch
- **Voice Feedback**: Announces recording start and stop via text-to-speech
- **Configurable Recording Duration**: Records 1-99 seconds of audio in MP3 format (AAC encoding), default 10 seconds
- **Live Speech-to-Text**: Transcribes your voice in real-time during recording
- **Smart Naming**: Saves files as `latitude,longitude_timestamp.mp3`
- **Coordinate-Based Waypoints**: GPX waypoint names use "VoiceNote: " + coordinates format
- **Transcription in Descriptions**: GPX waypoint descriptions contain your transcribed speech
- **GPX Waypoint Tracking**: Creates/updates `acquired_locations.gpx` with waypoints for each recording
- **Bluetooth Microphone Support**: Automatically prefers Bluetooth microphones when connected
- **Always Recording**: Records again every time you launch the app
- **Background Operation**: Continues recording while your trigger app runs
- **Landscape Support**: Works in both portrait and landscape orientations
- **Hands-Free Design**: Minimal interaction required while riding
- **First-Run Tutorial**: Explains how the app works on first launch

## User Guide

For detailed setup and usage instructions, see the **[User Guide](USER_GUIDE.md)**.

Quick start:
1. Install the app and launch it
2. Grant microphone, location, Bluetooth, and notification permissions
3. Choose where to save recordings
4. Select which app to launch after recording
5. View the tutorial explaining how the app works
6. Start recording! The app will handle the rest automatically

## Perfect for Motorcyclists

This app is designed for quick voice notes while riding:
- Record observations, directions, or reminders
- Automatically tagged with GPS location
- Speech transcribed and used as waypoint names
- Bluetooth headset support for clearer audio
- Minimal distraction from riding
- Quick return to navigation or music app
- Records every time you launch it

## Quick Start

### Download Pre-built APK

Download the latest APK from:
- **[Releases Page](https://github.com/c0dev0id/autorecord-app/releases)** - Stable releases with version tags
- **[Actions Tab](https://github.com/c0dev0id/autorecord-app/actions)** - Latest builds from CI pipeline

### Installation

1. Download the APK file to your Android device
2. Enable "Install from Unknown Sources" if prompted
3. Install the APK
4. Launch the app and complete first-time setup:
   - Grant microphone, location, and storage permissions
   - Choose a save directory for recordings
   - Select a trigger app (e.g., Google Maps, Spotify)
   - Configure recording duration (optional, defaults to 10 seconds)
5. On subsequent launches, the app automatically:
   - Acquires GPS location
   - Records audio for your configured duration
   - Saves the file
   - Launches your trigger app

## Building from Source

The project includes automated CI/CD pipelines via GitHub Actions:

- **Automatic builds** on every push
- **Release builds** on version tags
- **Pull request checks** to ensure code quality

You can build both debug and signed release APKs. See [BUILD_INSTRUCTIONS.md](BUILD_INSTRUCTIONS.md) for detailed manual build instructions, or check the [GitHub Actions workflows](.github/workflows/README.md) for automated builds.

For creating signed APKs suitable for distribution or Google Play Store publishing, see the comprehensive [SIGNING.md](SIGNING.md) guide.

## Requirements

- Android 8.0 (API 26) or higher
- GPS capability
- Microphone
- Storage access

## Permissions

The app requires:
- **Microphone**: To record audio
- **Fine Location**: To tag recordings with GPS coordinates
- **Bluetooth**: To detect and use Bluetooth microphones
- **Bluetooth Connect**: To connect to Bluetooth audio devices (Android 12+)
- **Modify Audio Settings**: To route audio to Bluetooth devices
- **Storage**: To save recording files
- **Notifications**: To provide status updates (Android 13+)
- **Query All Packages**: To list installed apps for trigger app selection

## Technical Stack

- **Language**: Kotlin
- **Build System**: Gradle
- **Location Services**: Google Play Services Location API
- **Audio Recording**: MediaRecorder
- **Text-to-Speech**: Android TTS Engine

## File Format

Recordings are saved as `.mp3` files with AAC encoding at 128 kbps and 44.1 kHz sample rate, providing excellent quality for voice while keeping file sizes reasonable.

Filename format: `<latitude>,<longitude>_<timestamp>.mp3`

Example: `34.052235,-118.243683_20260120_143022.mp3`

A GPX file (`acquired_locations.gpx`) is automatically created/updated with waypoints for each recording. The waypoint names use the format "VoiceNote: " followed by coordinates, while the waypoint descriptions contain the transcribed text from your voice note when available, making it easy to identify locations by what you said.
