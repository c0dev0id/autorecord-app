# Application Flow Instructions - Motorcycle Voice Notes

## Overview

This document describes the complete application flow for the Motorcycle Voice Notes app, including all post-processing phases implemented in this release.

## Core Recording Flow

### 1. App Launch
- User launches app from launcher or shortcut
- `MainActivity` starts
- Configuration check occurs (permissions, save directory)
- If configured: Start `OverlayService`
- If not configured: Show setup screen

### 2. Overlay Service Initialization
- Create overlay bubble at top of screen
- Initialize GPS location client
- Initialize Text-to-Speech engine
- Display: "Acquiring location..."

### 3. Location Acquisition
- Request high-accuracy GPS location
- Wait for location fix
- On success: Display coordinates, proceed to recording
- On failure: Display error, quit after 1 second

### 4. Voice Announcements
- TTS speaks: "Location acquired"
- TTS speaks: "Recording started"
- Both announcements complete before recording begins

### 5. Audio Recording
- Start MediaRecorder with AAC encoding
- Use Bluetooth audio source if available
- Display countdown timer in overlay
- Duration: Configurable (default 10 seconds)
- Save to: `{lat},{lng}_{timestamp}.m4a`

### 6. Recording Completion
- Stop MediaRecorder
- TTS speaks: "Recording stopped"
- Display: "File saved: {filename}"
- Proceed to post-processing decision

## Post-Processing Flow (Phases 3-8)

### 7. Post-Processing Decision

**Check: Online Processing Enabled?**
- Setting: `tryOnlineProcessingDuringRide` (default: true)
- If disabled: Skip to quit (step 12)
- If enabled: Continue to connectivity check

**Check: Internet Connectivity**
- Use `NetworkUtils.isOnline()`
- If offline: Skip to quit (step 12)
- If online: Continue to transcription (step 8)

### 8. Audio Transcription

**Display**: "Online: Transcribing:"

**API Call**: Google Cloud Speech-to-Text
- Read m4a file
- Send to Cloud Speech API
- Configuration:
  - Encoding: Auto-detect
  - Sample rate: 44100 Hz
  - Language: en-US
  - Enable automatic punctuation
  - Model: default

**On Success**:
- Extract transcribed text
- If empty: Use "{lat},{lng} (no text)"
- Display: "Online: Transcribing: {text}"
- Wait 1 second
- Proceed to GPX creation (step 9)

**On Failure**:
- Display: "Online: Transcribing: failed :-("
- Log error
- Wait 1 second
- Skip GPX and OSM, proceed to quit (step 12)

### 9. GPX Waypoint Creation (Phase 3)

**File**: `voicenote_waypoint_collection.gpx`

**Duplicate Handling**:
1. Check if GPX file exists
2. If exists: Parse content
3. Search for waypoint with matching coordinates (6 decimal precision)
4. If found: Replace existing waypoint with new data
5. If not found: Add new waypoint before `</gpx>` tag
6. If file doesn't exist: Create new GPX file with header and waypoint

**Waypoint Format**:
```xml
<wpt lat="{lat}" lon="{lng}">
  <time>{ISO8601_timestamp}</time>
  <name>VoiceNote: {lat},{lng}</name>
  <desc>{transcribed_text}</desc>
</wpt>
```

**Log**: "GPX waypoint created/updated: {name}"

### 10. OSM Note Creation Decision (Phase 4)

**Check: OSM Note Creation Enabled?**
- Setting: `addOsmNote` (default: false)
- If disabled: Skip to quit (step 12)
- If enabled: Continue to authentication check

**Check: OSM Authentication**
- Use `OsmOAuthManager.getAccessToken()`
- If not authenticated: Log error, skip to quit (step 12)
- If authenticated: Continue to OSM note creation (step 11)

### 11. OSM Note Creation

**Display**: "Online: Creating OSM Note"

**API Call**: OpenStreetMap API
- Endpoint: `POST /api/0.6/notes.json`
- Parameters:
  - lat: Location latitude
  - lon: Location longitude
  - text: Transcribed text (URL encoded)
- Headers:
  - Authorization: Bearer {access_token}

**On Success**:
- Display: "Online: OSM Note created."
- Wait 1 second
- Proceed to quit (step 12)

**On Failure**:
- Display: "Online: OSM Note creation failed :("
- Log error
- Wait 1 second
- Proceed to quit (step 12)

### 12. Service Termination

- Remove overlay bubble
- Stop Bluetooth SCO if active
- Send broadcast to finish MainActivity
- Stop service

## Manual Batch Processing Flow (Phase 6)

### Triggered By
- User clicks "Run Online Processing" button in settings

### Process
1. **Disable UI**: Change button text to "Processing..."
2. **Start Service**: Launch `BatchProcessingService`
3. **Find Files**: Scan save directory for all m4a files
4. **Process Each File**:
   - Broadcast progress with filename
   - Extract coordinates from filename
   - Transcribe audio (same API as during ride)
   - Create/update GPX waypoint (with duplicate handling)
   - If OSM enabled and authenticated: Create OSM note
   - Log success or failure
5. **Complete**: Broadcast completion, stop service
6. **Update UI**: Re-enable button, show "Processing complete" toast

## Settings and Configuration (Phase 5)

### Settings Screen Elements

**Recording Configuration**:
- Save directory selection
- Recording duration (1-99 seconds)

**Permissions**:
- Grant all permissions button
- Permission status display

**Online Processing**:
- "Try Online processing during ride" checkbox (default: checked)

**OSM Integration**:
- Account status display
- "Bind OSM Account" / "Remove OSM Account" button
- "Add OSM Note" checkbox (enabled only when authenticated)

**Manual Processing**:
- "Run Online Processing" button

### OAuth Flow (Phase 4)

**Bind Account**:
1. User clicks "Bind OSM Account"
2. Launch OAuth authorization flow
3. Open browser to OSM OAuth endpoint
4. User logs in and authorizes
5. Redirect to `app.voicenotes.motorcycle://oauth`
6. Exchange authorization code for access token
7. Save tokens securely in SharedPreferences
8. Fetch and save username
9. Update UI: Show username, enable "Add OSM Note"

**Remove Account**:
1. User clicks "Remove OSM Account"
2. Clear all tokens from SharedPreferences
3. Update UI: Hide username, disable "Add OSM Note"

## Error Handling

### No Retry Policy
- All failures are reported immediately
- No automatic retry attempts
- User can manually retry via batch processing

### Offline Behavior
- If offline: Skip post-processing entirely
- Recordings remain available for later batch processing
- No error messages for expected offline behavior

### API Failures
- Google Cloud API: Display "failed :-(" message
- OSM API: Display "creation failed :(" message
- All failures logged for debugging
- App continues to next step (graceful degradation)

## Data Storage

### SharedPreferences (AppPrefs)
- `saveDirectory`: Recording storage path
- `recordingDuration`: Recording length in seconds
- `tryOnlineProcessingDuringRide`: Enable online processing (boolean)
- `addOsmNote`: Enable OSM note creation (boolean)

### SharedPreferences (OsmAuth)
- `osm_access_token`: OAuth access token
- `osm_refresh_token`: OAuth refresh token
- `osm_username`: OSM username

### Files
- `{lat},{lng}_{timestamp}.m4a`: Audio recordings
- `voicenote_waypoint_collection.gpx`: GPX waypoint collection

## Security Considerations

### API Keys
- Google Cloud API key stored in BuildConfig
- Not hardcoded in source (uses gradle.properties)
- Required for transcription features

### OSM OAuth
- OAuth 2.0 with Authorization Code flow
- Access tokens stored in SharedPreferences (should use Android Keystore in production)
- Tokens can be revoked by user
- Client ID must be configured per deployment

### Permissions
- Sensitive permissions requested at setup
- Runtime permission checks before use
- Overlay permission for bubble display

## Testing Checklist

### Phase 3 - GPX Duplicate Handling
- ✓ Waypoint at same coordinates replaces existing
- ✓ Waypoint at different coordinates adds new entry
- ✓ GPX file maintains valid XML structure

### Phase 4 - OSM OAuth
- ✓ OAuth flow launches and completes
- ✓ Tokens saved and retrieved correctly
- ✓ Username displayed after binding
- ✓ "Add OSM Note" enables after binding
- ✓ Remove account clears tokens and disables checkbox
- ✓ OSM notes created successfully when enabled

### Phase 5 - Settings UI
- ✓ All new settings controls displayed
- ✓ Checkboxes save state to SharedPreferences
- ✓ OAuth button text toggles correctly
- ✓ Manual processing button disables during processing

### Phase 6 - Batch Processing
- ✓ All m4a files discovered and processed
- ✓ Progress broadcast sent for each file
- ✓ Button re-enables after completion
- ✓ GPX and OSM notes created for all files

### Phase 7-8 - Integration
- ✓ Full flow: record → transcribe → GPX → OSM
- ✓ Offline mode skips post-processing
- ✓ Manual processing works for offline recordings
- ✓ All documentation updated

## Implementation Status

All phases (3-8) have been implemented in this release:
- ✅ Phase 3: GPX Waypoint Duplicate Handling
- ✅ Phase 4: OpenStreetMap OAuth Integration
- ✅ Phase 5: Settings UI Updates
- ✅ Phase 6: Manual Batch Processing
- ✅ Phase 7: Final Integration
- ✅ Phase 8: Testing & Documentation
