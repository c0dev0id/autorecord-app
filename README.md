# Motorcycle Voice Notes

An Android app designed for recording voice notes while riding a motorcycle. The app automatically acquires your GPS location, records a 10-second voice note, saves it with location and timestamp information, and then launches your preferred navigation or music app.

## Features

- **Automatic GPS Location**: Acquires precise GPS coordinates on app launch
- **Voice Feedback**: Announces recording start and stop via text-to-speech
- **Quick Recording**: Records exactly 10 seconds of audio
- **Smart Naming**: Saves files as `latitude_longitude_timestamp.3gp`
- **App Launcher**: Automatically opens your configured app after recording
- **Hands-Free Design**: Minimal interaction required while riding

## Perfect for Motorcyclists

This app is designed for quick voice notes while riding:
- Record observations, directions, or reminders
- Automatically tagged with GPS location
- Minimal distraction from riding
- Quick return to navigation or music app

## Quick Start

### Download Pre-built APK

Download the latest APK from:
- **[Releases Page](../../releases)** - Stable releases with version tags
- **[Actions Tab](../../actions)** - Latest builds from CI pipeline

### Installation

1. Download the APK file to your Android device
2. Enable "Install from Unknown Sources" if prompted
3. Install the APK
4. Launch the app and complete first-time setup:
   - Grant microphone, location, and storage permissions
   - Choose a save directory for recordings
   - Select a trigger app (e.g., Google Maps, Spotify)
5. On subsequent launches, the app automatically:
   - Acquires GPS location
   - Records 10 seconds of audio
   - Saves the file
   - Launches your trigger app

## Building from Source

The project includes automated CI/CD pipelines via GitHub Actions:

- **Automatic builds** on every push
- **Release builds** on version tags
- **Pull request checks** to ensure code quality

See [BUILD_INSTRUCTIONS.md](BUILD_INSTRUCTIONS.md) for detailed manual build instructions, or check the [GitHub Actions workflows](.github/workflows/README.md) for automated builds.

## Requirements

- Android 8.0 (API 26) or higher
- GPS capability
- Microphone
- Storage access

## Permissions

The app requires:
- **Microphone**: To record audio
- **Fine Location**: To tag recordings with GPS coordinates
- **Storage**: To save recording files
- **Query All Packages**: To list installed apps for trigger app selection

## Technical Stack

- **Language**: Kotlin
- **Build System**: Gradle
- **Location Services**: Google Play Services Location API
- **Audio Recording**: MediaRecorder
- **Text-to-Speech**: Android TTS Engine

## File Format

Recordings are saved as `.3gp` files with AMR-NB encoding, which provides good quality for voice while keeping file sizes small.

Filename format: `<latitude>_<longitude>_<timestamp>.3gp`

Example: `34.052235_-118.243683_20260120_143022.3gp`
