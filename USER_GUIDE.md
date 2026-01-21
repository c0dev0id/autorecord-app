# Motorcycle Voice Notes - User Guide

## Overview

Motorcycle Voice Notes is designed for riders who need to quickly record voice memos while on the road. The app automatically captures your GPS location, records audio with speech-to-text transcription, and then seamlessly returns you to your navigation or music app. It preferentially uses Bluetooth microphones for clearer audio quality.

## First Time Setup

When you launch the app for the first time, you'll need to configure two essential settings:

### 1. Recording Storage Location

Choose where your voice recordings will be saved:

- Tap **"Set Recording Location"**
- The app will use the default location: `/storage/emulated/0/Music/VoiceNotes`
- This folder will be created automatically if it doesn't exist
- Recordings are saved as MP3 files with GPS coordinates and timestamps in the filename

**File naming format:** `latitude_longitude_timestamp.mp3`

**Example:** `34.052235_-118.243683_20260120_143022.mp3`

### 2. App to Launch After Recording

Select which app opens automatically after recording:

- Tap **"Select App to Launch"**
- Choose from your installed apps (e.g., Google Maps, Waze, Spotify, etc.)
- This app will open automatically after each recording completes

### 3. Grant Permissions

The app requires the following permissions to function:

- **Microphone** - To record your voice notes
- **Location** - To tag recordings with GPS coordinates
- **Bluetooth** - To detect Bluetooth microphones
- **Bluetooth Connect** - To connect to Bluetooth audio devices (Android 12+)
- **Modify Audio Settings** - To route audio to Bluetooth devices
- **Notifications** (Android 13+) - To provide status updates

Tap **"Grant Required Permissions"** and allow all requested permissions.

### 4. Configure Recording Duration (Optional)

You can customize how long each recording lasts:

- The default recording duration is **10 seconds**
- You can change this to any value between **1 and 99 seconds**
- Open the Settings screen and find the "Recording Duration (seconds)" card
- Enter your desired duration (e.g., 5 for quick markers, 30 for detailed notes)
- Tap "Set Duration" to save
- This setting persists across all future recordings

## How It Works

### First Run After Setup

On your first actual recording session, you'll see a tutorial explaining:

1. Launch the app
2. **Tutorial screen appears** explaining the process
3. Tap "Start Recording" to begin
4. The app acquires your GPS location
5. Voice announcement: "Location acquired, recording"
6. Records for your configured duration (using Bluetooth mic if available)
7. Audio is transcribed to text in real-time
8. Voice announcement: "Recording complete"
9. Saves the file with GPS coordinates in filename
10. Creates or updates `acquired_locations.gpx` with waypoint using transcribed text
11. Launches your selected app

### Subsequent Runs

Every time you launch the app:

1. Launch the app
2. Recording process starts automatically
3. GPS acquisition
4. Recording with live transcription (duration you configured)
5. File saved with waypoint created using your spoken text
6. Your trigger app launches
7. You can continue using your trigger app without interruption

**Note**: The app now records **every time** you launch it, not just the first time.

## Recording Files

### Audio Format

- **Format:** MP3 (MPEG-4 AAC encoding)
- **Bitrate:** 128 kbps
- **Sample Rate:** 44.1 kHz
- **Duration:** Configurable (1-99 seconds, default 10 seconds)
- **Microphone:** Prefers Bluetooth microphones when available

### Speech-to-Text Transcription

The app transcribes your recorded audio in real-time:
- Transcription happens while you speak during recording
- Transcribed text is used as the waypoint name in the GPX file
- Makes it easier to identify locations by what you said
- If transcription fails, falls back to using the filename
- Works best with clear audio and Bluetooth microphones

**Best Practices**:
- Speak clearly and at normal pace
- Use Bluetooth microphone for better quality
- Minimize background noise
- Keep messages simple and direct
- Wait a moment after speaking for recognition to complete

### GPX Location File

The app creates and maintains a file called `acquired_locations.gpx` in your recording folder. This file contains:

- GPS waypoints for each recording
- Waypoint name: `VoiceNote: <latitude>_<longitude>` format
- Waypoint description: Transcribed text from your voice note (or filename as fallback)
- Timestamp of when the recording was made
- Can be imported into mapping applications like Google Earth, Garmin, etc.

## Bluetooth Microphone Support

The app automatically detects and prefers Bluetooth audio devices:

### Automatic Detection

- The app checks for Bluetooth headsets/microphones on launch
- If available, routes audio recording through Bluetooth
- Provides clearer audio quality, especially while riding
- Reduces wind noise compared to phone microphone

### Supported Devices

- Bluetooth headsets with microphone
- Motorcycle helmet communication systems
- Wireless earbuds with microphone
- Any Bluetooth device with SCO (Synchronous Connection Oriented) audio

### Best Practices

- Pair your Bluetooth device before launching the app
- Ensure the device is connected and active
- Test audio quality with a sample recording
- Position microphone appropriately for clear speech

## Screen Orientation

The app supports both portrait and landscape orientations, making it easy to use in different mounting configurations on your motorcycle.

## Configuration Changes

You can return to the configuration screen at any time by:

1. Tapping the **"Configuration"** button on the main screen
2. Make changes to:
   - Recording storage location
   - Trigger app selection
   - Permissions

## Tips for Motorcyclists

### Mounting Your Phone

- Use a quality motorcycle phone mount
- Position for easy reach but minimal distraction
- Ensure good GPS signal reception
- Keep away from excessive vibration

### Bluetooth Headset Recommendations

- Use a motorcycle-specific Bluetooth system
- Helmet-integrated communication systems work best
- Ensure good microphone placement for voice clarity
- Test before riding to verify audio quality

### Recording Best Practices

- Speak clearly toward the microphone
- Bluetooth headsets significantly reduce wind noise
- Keep messages concise - you have 10 seconds
- The app transcribes your speech for waypoint names
- Wait for location acquisition before speaking (listen for TTS announcement)

### Using Voice Commands

If your phone supports voice activation:

- Set up "OK Google" or "Hey Siri" to launch the app
- This allows truly hands-free operation while riding

### Safety First

- **Never manipulate your phone while riding**
- Pull over safely before adjusting settings
- Configure everything before you start riding
- The app is designed for minimal interaction

## Troubleshooting

### Location Not Acquired

- Ensure GPS/Location services are enabled on your device
- Check that location permissions are granted
- Try moving to an area with better sky visibility
- Wait a moment for GPS to acquire satellites

### Recording Not Saved

- Verify the storage location exists and is writable
- Check available storage space
- If the folder doesn't exist, the app will prompt you to reconfigure

### App Doesn't Launch Trigger App

- Ensure the trigger app is still installed
- Check that the trigger app package hasn't changed
- Reconfigure the trigger app in settings

### No Sound During Recording

- Check microphone permissions
- Ensure no other app is using the microphone
- Test your device microphone in another app
- For Bluetooth: Verify device is connected and paired

### Bluetooth Microphone Not Working

- Ensure Bluetooth is enabled on your device
- Verify the device is paired and connected
- Check that the device supports voice/call audio (SCO)
- Try disconnecting and reconnecting the device
- Grant Bluetooth permissions when prompted (Android 12+)

### Transcription Issues

If speech-to-text transcription isn't working well:

- Speak clearly and at normal volume
- Reduce background noise (use Bluetooth headset)
- Ensure microphone permissions are granted
- Check that your device supports speech recognition
- If transcription consistently fails, waypoints will use filenames instead
- Try speaking immediately after the recording starts

## Privacy & Data

- All recordings are stored locally on your device
- No data is sent to external servers
- GPS coordinates are only embedded in filenames and GPX file
- You have complete control over your recordings

## File Management

### Accessing Your Recordings

Recordings are stored in your configured directory, typically:
`/storage/emulated/0/Music/VoiceNotes/`

You can access them via:

- File manager apps
- Computer when connected via USB
- Cloud backup apps (Google Drive, Dropbox, etc.)

### Backing Up

Consider setting up automatic cloud backup:

1. Install your preferred cloud storage app
2. Configure it to backup the VoiceNotes folder
3. Your recordings will be automatically backed up

### GPX File Usage

The `acquired_locations.gpx` file can be:

- Opened in Google Earth to see all recording locations
- Waypoint names show transcribed speech for easy identification
- Imported into GPS devices
- Used in route planning software
- Shared with others for route documentation

## Advanced Usage

### Using Transcribed Waypoints

The speech-to-text feature creates meaningful waypoint names:

- Speak clearly: "Turn left at the red barn"
- Waypoint will be named with that phrase
- Makes it easy to find specific locations later
- Much more useful than just GPS coordinates
- Review waypoints in mapping software to plan routes

### Creating a Quick Launch Shortcut

For fastest access:

1. Long-press the app icon on your home screen
2. Drag it to your home screen or dock
3. Consider placing it in an easily accessible location

### Integration with Other Apps

The app works well with:

- **Navigation apps:** Google Maps, Waze, OsmAnd
- **Music apps:** Spotify, YouTube Music, Podcast apps
- **Communication apps:** Return to a call or messaging app

## Support

If you encounter issues or have suggestions:

- Check the [README](README.md) for general information
- Visit the [GitHub repository](https://github.com/c0dev0id/autorecord-app) for updates
- Report issues in the GitHub Issues section

## Version Information

This guide is for Motorcycle Voice Notes v1.0.0 and later.

---

**Remember:** The primary goal is safe riding. This app is designed to minimize distraction and maximize efficiency. Always prioritize road safety over technology.
