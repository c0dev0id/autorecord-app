# Background Overlay Recording Feature Implementation

## Overview
This implementation adds a background recording feature with a minimal overlay bubble interface that appears over other apps while recording. The app now starts in the background and shows a small chat bubble with two lines of text instead of a full-screen UI.

## Changes Made

### 1. New Files Created

#### OverlayService.kt
A new Android Service that manages the overlay bubble and handles the entire recording process in the background:
- Displays an overlay bubble with two text lines
- Acquires GPS location
- Plays TTS announcements
- Records audio with live transcription
- Creates GPX waypoints
- Automatically quits after completion

#### FinishActivityReceiver.kt
A BroadcastReceiver that allows the OverlayService to finish MainActivity when recording is complete.

#### overlay_bubble.xml
Layout file for the overlay bubble with two TextViews (Bubble Line 1 and Bubble Line 2).

#### bubble_background.xml
Drawable resource defining the bubble's appearance (rounded rectangle with semi-transparent black background).

### 2. Modified Files

#### MainActivity.kt
Significantly simplified MainActivity that:
- Checks for overlay permission (SYSTEM_ALERT_WINDOW)
- Starts OverlayService
- Moves itself to background (moveTaskToBack)
- Listens for broadcast to finish when recording is done

#### AndroidManifest.xml
- Added SYSTEM_ALERT_WINDOW permission
- Registered OverlayService
- Registered FinishActivityReceiver

#### strings.xml
Added new string resources for the overlay UI:
- acquiring_location
- location_acquired_coords
- recording_countdown
- recording_stopped_msg
- file_saved
- recording_started
- overlay_permission_required
- overlay_permission_message

## Recording Flow

The new flow matches the requirements exactly:

1. **App starts in background**: MainActivity starts OverlayService and moves to background
2. **Overlay appears**: Small bubble overlay shows over current app
3. **Acquire location**: Bubble Line 1 shows "Acquiring location..."
4. **Location acquired**: Bubble Line 1 shows "Location acquired: <coordinates>"
5. **TTS announcement**: "Location acquired" plays via TTS
6. **TTS announcement**: "Recording started" plays via TTS
7. **Recording**: Bubble Line 1 shows countdown "Recording... X seconds"
8. **Live transcription**: Bubble Line 2 shows transcribed text in real-time
9. **Recording stops**: Bubble Line 1 shows "Recording stopped"
10. **TTS announcement**: "Recording stopped" plays via TTS
11. **GPX creation**: Waypoint added to GPX file
12. **File saved**: Bubble Line 1 shows "File saved."
13. **Wait**: 2 second pause
14. **App quits**: Overlay disappears and app closes

## User Experience

From the user's perspective:
1. User presses device button configured to launch the app
2. Small overlay bubble appears (minimal space, doesn't block navigation)
3. User sees status updates in the bubble
4. User speaks their note while bubble shows "Recording..."
5. User sees their speech transcribed in real-time
6. After recording, bubble shows "File saved."
7. After 2 seconds, overlay disappears and app quits
8. User continues using their navigation app without interruption

## Technical Details

### Overlay Permission
The app now requests SYSTEM_ALERT_WINDOW permission to display over other apps. On first run with this feature, users will be prompted to grant this permission.

### Background Operation
The OverlayService runs in the background and handles all recording operations independently of MainActivity. This allows the app to function while other apps are in the foreground.

### Minimal UI Footprint
The overlay bubble is designed to be as small as possible:
- Two lines of text maximum
- Semi-transparent background
- Positioned at top center of screen
- Automatically adjusts width based on content

### Recording Logic
All recording logic (location acquisition, TTS, audio recording, speech recognition, GPX creation) has been moved from MainActivity to OverlayService to enable background operation.

## Compatibility
- Minimum Android version: API 26 (Android 8.0)
- Overlay permission required (auto-requested on first run)
- Works with all existing settings (recording duration, save directory, trigger app)
