# Voice Notes

A lightweight Android app for motorcyclists to record GPS-tagged voice notes while riding - hands-free and automatic.

## What It Does

Launch the app and it automatically:
1. Shows a small overlay bubble
2. Announces location via text-to-speech
3. Records audio (10 seconds default)
4. Saves with GPS coordinates
5. Quits automatically

All processing (transcription, waypoints) happens later in the **Recording Manager**.

## Key Features

- **GPS Tagging** - Every recording includes precise coordinates and timestamp
- **Hands-Free** - Launch once, everything else is automatic
- **Bluetooth Support** - Works with helmet communication systems
- **Recording Manager** - Review, process, and export recordings
- **Speech-to-Text** - Optional Google Cloud transcription
- **GPX Waypoints** - Export locations for mapping apps
- **Configurable** - Set recording duration (1-99 seconds)

## Quick Start

### Installation

1. Download APK from [Releases](https://github.com/c0dev0id/autorecord-app/releases)
2. Install on Android 8.0+ device
3. Grant permissions (microphone, location, Bluetooth, overlay)
4. Configure recording duration in Voice Notes Manager

### Usage

**Two launcher icons:**
- **Voice Notes** - Records audio and GPS automatically
- **Voice Notes Manager** - Configure settings and manage recordings

Just tap "Voice Notes" whenever you want to record. The app quits automatically when done.

## Documentation

- **[User Guide](docs/USER_GUIDE.md)** - How to use the app
- **[Developer Guide](docs/DEVELOPER_GUIDE.md)** - Build and contribute
- **[Architecture](docs/ARCHITECTURE.md)** - Technical deep-dive

## Requirements

- Android 8.0 (API 26) or higher
- GPS and microphone
- Bluetooth (optional, for headsets)

### Optional Online Features

Works perfectly offline! Optional features require:
- Internet connection
- [Google Cloud credentials](docs/DEVELOPER_GUIDE.md#google-cloud-setup) for transcription

## Building from Source

See [Developer Guide](docs/DEVELOPER_GUIDE.md) for complete build instructions.

```bash
git clone https://github.com/c0dev0id/autorecord-app.git
cd autorecord-app
./gradlew assembleDebug
```

## Technology

- **Language:** Kotlin
- **Location:** Google Play Services
- **Audio:** MediaRecorder (Opus/OGG on Android 10+, AMR-WB on Android 8-9)
- **Database:** Room (SQLite)
- **Speech-to-Text:** Google Cloud Speech-to-Text API (optional)

## License

This project is provided as-is for personal use.

---

**Safety First:** Never manipulate your phone while riding. Configure the app before you start, and use voice commands or pull over safely to launch it.
