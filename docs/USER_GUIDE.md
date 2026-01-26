# Voice Notes - User Guide

Welcome to Voice Notes! This guide will help you get the most out of your GPS-tagged voice recording app.

## Table of Contents

- [Getting Started](#getting-started)
- [Using Voice Notes](#using-voice-notes)
- [Recording Manager](#recording-manager)
- [Settings](#settings)
- [Optional Online Features](#optional-online-features)
- [File Formats](#file-formats)
- [Troubleshooting](#troubleshooting)
- [Privacy & Data](#privacy--data)

---

## Getting Started

### Installation

1. **Download the APK** from the [Releases page](https://github.com/c0dev0id/autorecord-app/releases)
2. **Install** on your Android 8.0+ device
3. **Launch** Voice Notes Manager (the settings icon)
4. **Grant permissions** when prompted:
   - Microphone (to record audio)
   - Location (to capture GPS coordinates)
   - Bluetooth (to use helmet communication system)
   - Overlay (to show recording status)

### First-Time Setup

After installation, you'll see **two icons** on your home screen:

- **Voice Notes** 🎤 - The main recording app
- **Voice Notes Manager** ⚙️ - Settings and recording management

**Important:** Configure your settings before your first ride:

1. Open **Voice Notes Manager**
2. Tap "Grant All Permissions"
3. Set your preferred recording duration (default: 10 seconds)
4. That's it! You're ready to ride.

### Two Launcher Icons Explained

**Voice Notes (Main Icon)**
- Tap to record a voice note
- Runs completely in the background
- Shows a small overlay bubble
- Quits automatically when done

**Voice Notes Manager (Settings Icon)**
- Configure app settings
- View and manage your recordings
- Process recordings (transcription)
- Export data in various formats
- View debug logs

💡 **Tip:** Long-press the main Voice Notes icon to quickly access the Manager.

---

## Using Voice Notes

### Recording a Voice Note

1. **Before riding:** Make sure permissions are granted and GPS is enabled
2. **While riding:** Tap the Voice Notes icon (or use voice command to launch it)
3. **What happens next:**
   - Small overlay bubble appears at the top of the screen
   - "Fetching location..." message appears
   - Text-to-speech announces when location is acquired
   - Recording starts automatically
   - Countdown shows remaining seconds
   - Recording stops automatically
   - File saved with GPS coordinates
   - App quits, returning you to your previous app

**Total time:** ~15-45 seconds depending on GPS acquisition

### The Overlay Bubble

The overlay bubble shows your recording status:

- **"Fetching location..."** - GPS is being acquired
- **"Recording: X seconds"** - Currently recording
- **"File saved: filename.ogg"** - Recording complete

The bubble automatically disappears when the app quits.

### Recording Duration

You can record for 1-99 seconds:

1. Open Voice Notes Manager
2. Scroll to "Recording Duration" card
3. Enter your preferred duration (e.g., 5, 10, 20)
4. Tap "Set Duration"

**Default:** 10 seconds

### Using Bluetooth Microphones

Voice Notes automatically detects and uses Bluetooth audio devices:

- **Helmet communication systems** (Sena, Cardo, etc.)
- **Bluetooth headsets**
- **Wireless earbuds with microphone**

**How it works:**
- App automatically connects to your paired Bluetooth device
- Audio routes through Bluetooth microphone
- Recording quality is optimized for voice
- Connection closes after recording

**No configuration needed!** Just pair your Bluetooth device with your phone as normal.

---

## Recording Manager

All your recordings are stored in a database and accessible through the Recording Manager.

### Opening Recording Manager

1. Tap the **Voice Notes Manager** icon, or
2. Long-press the Voice Notes icon and select "Voice Notes Manager"

### Viewing Recordings

Recordings are displayed as cards showing:

- **Date and time** of recording
- **GPS coordinates** (latitude, longitude)
- **Transcription** (if processed)
- **Status indicators** for Speech-to-Text
- **Action buttons** (play, transcribe, export, delete)

Recordings are sorted by **newest first**.

### Playing Recordings

Tap the **play icon** (▶️) in the header of any recording card.

- Audio plays through your phone speaker or connected Bluetooth device
- Playback stops automatically when complete
- A toast message confirms when playback finishes

### Processing Recordings

Voice Notes separates recording from processing. All processing happens in the Recording Manager.

#### Speech-to-Text Transcription

Convert your voice recording to text:

1. Find the recording you want to transcribe
2. Tap the **"Transcribe"** button
3. Watch the status:
   - Progress spinner appears
   - Button shows "Processing..."
   - When complete: green checkmark ✓ and transcribed text appears
4. If it fails: red X appears with error message, tap "Retry"

**Status Indicators:**
- **Gray ?** - Not yet transcribed
- **Spinner** - Transcribing now...
- **Green ✓** - Completed successfully
- **Orange ⚠** - Partial result (fallback)
- **Red ✗** - Failed (error message shown)

**Requirements:**
- Internet connection
- Google Cloud credentials configured (developer task)

### Exporting Recordings

Export single or all recordings in various formats.

#### Export Single Recording

1. Tap **"Export"** button on a recording card
2. Choose format:
   - **Audio** - Just the .ogg audio file
   - **GPX** - GPS waypoint file for mapping apps
   - **CSV** - Spreadsheet with coordinates and transcription
   - **All** - ZIP file containing everything

3. File saves to your Downloads folder
4. Toast message shows filename and location

#### Export All Recordings

1. Tap the **three-dot menu** (⋮) in the top-right
2. Select "Download All"
3. Choose format:
   - **Audio (ZIP)** - All recordings in one ZIP file
   - **GPX** - Single GPX file with all waypoints
   - **CSV** - Single CSV file with all data
   - **All (ZIP)** - Complete export with audio, GPX, and CSV

**File Locations:** All exports save to `/storage/emulated/0/Download/`

### Deleting Recordings

1. Tap **"Delete"** button on a recording card
2. Confirm deletion
3. Recording is removed from database and file is deleted

**Warning:** This action cannot be undone!

### Understanding Status Colors

- **Green** - Success, completed
- **Orange** - Warning or partial result
- **Red** - Error or failure
- **Gray** - Not started or disabled

---

## Settings

Access settings through **Voice Notes Manager**.

### Permissions

**Grant All Permissions** button requests all required permissions at once:

- Microphone
- Fine Location
- Bluetooth
- Overlay

**Permission Status List** shows which permissions are granted.

### Recording Duration

Set how long each recording should last (1-99 seconds).

Default: 10 seconds

### Save Directory

Recordings are automatically saved to internal app storage (not directly accessible via file manager for security).

Use the **Recording Manager** to:
- View all your recordings
- Play audio
- Export recordings to Downloads folder

Tap **"Open Recording Manager"** to view and manage your recordings.

### Online Processing

Shows whether Google Cloud Speech-to-Text is configured.

- **✓ Configured** - Transcription available
- **⚠ Not configured** - Transcription disabled

See [Optional Online Features](#optional-online-features) for setup.

### Debug Log

View detailed logs of API calls and errors:

1. Tap **"View Debug Log"**
2. See timestamped log entries
3. Useful for troubleshooting issues

---

## Optional Online Features

Voice Notes works perfectly **offline** - it will always record audio and capture GPS coordinates. Online features are optional enhancements.

### What Works Offline

✅ Recording voice notes
✅ Capturing GPS coordinates
✅ Playing recordings
✅ Deleting recordings
✅ Exporting audio files

### What Requires Internet

❌ Speech-to-Text transcription

### Speech-to-Text Transcription

**What it does:** Converts your voice recording into text

**Why it's optional:**
- Requires Google Cloud account (developer task)
- Uses Google Cloud API (may incur costs)
- Pre-built APKs may not have credentials

**If not configured:**
- Recordings still save with GPS coordinates
- Transcription section shows "Not configured"
- You can still listen to your recordings
- Export audio and GPX files normally

**To enable:** Developer must configure Google Cloud credentials (see Developer Guide)

---

## File Formats

### Audio Files

**Android 10+:**
- Format: OGG with Opus encoding
- Extension: `.ogg`
- Bitrate: 32 kbps
- Sample rate: 48 kHz
- Optimized for voice

**Android 8-9:**
- Format: AMR-WB (Adaptive Multi-Rate Wideband)
- Extension: `.amr`
- Bitrate: Variable (6.6-23.85 kbps)
- Sample rate: 16 kHz
- Optimized for voice

### Filename Format

Recordings are saved with this format:
```
latitude,longitude_timestamp.ogg
```

**Example:**
```
40.712776,-74.005974_20260125_143022.ogg
```

This means:
- Latitude: 40.712776
- Longitude: -74.005974
- Date: January 25, 2026
- Time: 14:30:22

### GPX Format

GPX (GPS Exchange Format) is supported by most mapping applications:

- Garmin devices
- Maps.me
- Komoot
- OsmAnd
- And many more

**GPX content includes:**
- Waypoint at recording location
- Timestamp
- Recording filename
- Transcribed text (if available)

### CSV Format

Spreadsheet format with these columns:

- Latitude
- Longitude
- Timestamp
- Filename
- Transcription
- V2S Status

Compatible with Excel, Google Sheets, LibreOffice Calc, etc.

---

## Troubleshooting

### "Location not acquired" or Timeout

**Problem:** GPS can't get a fix within 30 seconds

**Solutions:**
1. Make sure Location/GPS is enabled in phone settings
2. Move to an area with clear sky view
3. Wait a few moments for GPS to initialize
4. Try again

**Why it happens:**
- GPS needs clear view of sky
- Tunnels, parking garages, dense urban areas can block signal
- Cold start (first GPS fix after phone reboot) takes longer

**Note:** If GPS times out, the app uses your last known location automatically

### Recording Doesn't Start

**Problem:** App launches but nothing happens

**Solutions:**
1. Check permissions in Voice Notes Manager
2. Ensure microphone permission is granted
3. Check if another app is using the microphone
4. Restart your phone

### No Bluetooth Audio

**Problem:** Recording doesn't use Bluetooth microphone

**Solutions:**
1. Check Bluetooth is enabled
2. Ensure device is paired and connected
3. Grant Bluetooth Connect permission
4. Try disconnecting and reconnecting Bluetooth device
5. Some devices need to be in "call mode" - try making a test call first

### Transcription Not Working

**Problem:** "Transcribe" button doesn't work or shows error

**Solutions:**
1. Check internet connection
2. Verify Voice Notes Manager shows "✓ Configured" for Google Cloud
3. Check Debug Log for error details
4. Contact developer if using pre-built APK (may need credentials)

**If configuration shows "⚠ Not configured":**
- Pre-built APK doesn't have Google Cloud credentials
- Feature not available unless you build from source with credentials

### Storage Space Issues

**Problem:** "Not enough storage space"

**Solutions:**
1. Export and backup your recordings
2. Delete old recordings you don't need
3. Free up space on your device
4. Recordings typically use 100-500 KB each

### App Crashes or Freezes

**Solutions:**
1. Check Debug Log for errors
2. Ensure you have latest app version
3. Clear app data (Settings > Apps > Voice Notes > Clear Data)
4. Reinstall the app
5. Report issue to developer with debug log

---

## Privacy & Data

### What Data is Collected

Voice Notes only stores data **locally on your device**:

- Audio recordings (in Music/VoiceNotes folder)
- GPS coordinates
- Timestamps
- Transcriptions (if you use the feature)

### What Data is NOT Collected

❌ No tracking or analytics
❌ No personal information sent to developer
❌ No usage statistics
❌ No crash reports (unless you manually share logs)
❌ No location history beyond what you record

### Third-Party Services

**Only if you enable optional features:**

**Google Cloud Speech-to-Text:**
- Audio file sent to Google for transcription
- Subject to Google Cloud Privacy Policy
- Only when you tap "Transcribe"

### Deleting Your Data

**To delete specific recordings:**
1. Open Recording Manager
2. Tap "Delete" on any recording

**To delete ALL data:**
1. Go to phone Settings > Apps > Voice Notes
2. Tap "Clear Data"
3. Or uninstall the app

**To delete exported files:**
1. Open file manager
2. Go to Downloads folder
3. Delete files manually

### Storage Location

Recordings are stored in internal app storage for security:
```
/data/data/com.voicenotes.motorcycle/files/recordings/
```

**Note:** This location is not directly accessible via file manager (Android security restriction).

**To access your recordings:**
- Use the Recording Manager within the app
- Export recordings to Downloads folder (accessible via file manager)

---

## Tips for Best Results

### For Better GPS Accuracy

- Enable "High Accuracy" location mode in phone settings
- Let GPS acquire first fix before starting ride (can take 30-60 seconds)
- Clear sky view helps significantly

### For Better Audio Quality

- Use a good quality Bluetooth microphone or helmet system
- Speak clearly and at normal volume
- Minimize wind noise (good helmet helps)
- Shorter recordings (5-10 seconds) work best

### For Better Transcriptions

- Speak clearly and not too fast
- Use simple language
- Minimize background noise
- Good microphone quality is essential

### For Efficient Workflow

1. **During ride:** Just tap and record (don't process)
2. **After ride:** Process all recordings in Recording Manager
3. **End of day/week:** Export and backup your data
4. **Monthly:** Delete processed recordings you don't need

---

## Getting Help

- **Issues or bugs:** Check Debug Log first
- **Questions:** See [Developer Guide](DEVELOPER_GUIDE.md) for technical details
- **Feature requests:** Contact the developer

---

**Safe riding! Never manipulate your phone while riding. Set up the app before you ride, and only launch it when safely stopped or using voice commands.**
