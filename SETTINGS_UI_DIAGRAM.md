# Settings Screen UI Diagram

This document provides a visual representation of the Settings screen with the new Recording Duration configuration.

## Settings Screen Layout

```
┌─────────────────────────────────────────────────┐
│  ← Configuration                                │
├─────────────────────────────────────────────────┤
│                                                 │
│  Configuration                                  │
│  Configure recording storage location and       │
│  duration                                       │
│                                                 │
│  ┌───────────────────────────────────────────┐ │
│  │ Storage Location                          │ │
│  │                                           │ │
│  │ /storage/emulated/0/Music/VoiceNotes      │ │
│  │                                           │ │
│  │  [ Choose Folder ]                        │ │
│  └───────────────────────────────────────────┘ │
│                                                 │
│  ┌───────────────────────────────────────────┐ │
│  │ Recording Duration                        │ │
│  │                                           │ │
│  │ 10 seconds                                │ │
│  │                                           │ │
│  │ ┌─────────────────────────────────────┐  │ │
│  │ │ Enter 1-99 seconds                  │  │ │
│  │ └─────────────────────────────────────┘  │ │
│  │                                           │ │
│  │  [ Set Duration ]                         │ │
│  └───────────────────────────────────────────┘ │
│                                                 │
│  ┌───────────────────────────────────────────┐ │
│  │ Permissions                               │ │
│  │                                           │ │
│  │ Grant microphone, location, and overlay   │ │
│  │ permissions                               │ │
│  │                                           │ │
│  │  [ Grant Required Permissions ]           │ │
│  └───────────────────────────────────────────┘ │
│                                                 │
│                                                 │
│  ┌───────────────────────────────────────────┐ │
│  │               [ Quit ]                    │ │
│  └───────────────────────────────────────────┘ │
│                                                 │
└─────────────────────────────────────────────────┘
```

## Recording Duration Card - Detailed View

```
┌─────────────────────────────────────────────────────┐
│ Recording Duration                                  │
│ ─────────────────────────────────────────────────── │
│                                                     │
│ Current Setting:                                    │
│ 10 seconds                                          │
│                                                     │
│ Enter New Duration:                                 │
│ ┌─────────────────────────────────────────────┐   │
│ │ [  10  ]                    🔢 numeric pad   │   │
│ └─────────────────────────────────────────────┘   │
│ Enter 1-99 seconds                                  │
│                                                     │
│ ┌───────────────────┐                              │
│ │  Set Duration     │  ← Tap to save               │
│ └───────────────────┘                              │
│                                                     │
└─────────────────────────────────────────────────────┘
```

## User Interaction Flow

```
User opens Settings
       │
       ├─→ Scrolls to "Storage Location" card
       │
       ├─→ Sees current path: "/storage/emulated/0/Music/VoiceNotes"
       │
       ├─→ Taps "Choose Folder" button
       │
       ├─→ Android folder picker opens directly
       │
       ├─→ Selects desired folder
       │
       ├─→ Path is saved and display updates
       │
       ├─→ Scrolls to "Recording Duration" card
       │
       ├─→ Sees current value: "10 seconds"
       │
       ├─→ Taps in the EditText field
       │
       ├─→ Numeric keyboard appears (0-9 only)
       │
       ├─→ Enters desired duration (e.g., 15)
       │
       ├─→ Taps "Set Duration" button
       │
       ├─→ Validation occurs:
       │     - If 1-99: Success! ✓
       │     - If <1 or >99: Error toast shown
       │     - If empty: Error toast shown
       │     - If non-numeric: Error toast shown
       │
       ├─→ On success:
       │     - Duration saved to SharedPreferences
       │     - Display updates: "15 seconds"
       │     - Toast: "Recording duration set to 15 seconds"
       │
       └─→ Duration used in next recording
```

## Validation Rules

- **Minimum**: 1 second
- **Maximum**: 99 seconds  
- **Default**: 10 seconds
- **Input Type**: Numeric only (enforced by keyboard)
- **Max Length**: 2 digits

## Toast Messages

### Success
```
┌─────────────────────────────────────────┐
│ Recording duration set to 15 seconds    │
└─────────────────────────────────────────┘
```

### Error (Invalid Range)
```
┌─────────────────────────────────────────┐
│ Duration must be between 1 and 99       │
│ seconds                                 │
└─────────────────────────────────────────┘
```

## Color Scheme

- **Card Background**: White (#FFFFFF)
- **Card Elevation**: 2dp shadow
- **Title Text**: Bold, 16sp, Dark Gray (#212121)
- **Value Text**: Regular, 14sp, Medium Gray (#757575)
- **Hint Text**: Regular, 14sp, Light Gray
- **Button**: Material Design default (Blue/Purple)
- **Page Background**: Light Gray (#F5F5F5)

## Material Design Elements

- **CardView**: Rounded corners, elevation shadow
- **EditText**: Material underline, hint text
- **Button**: Material button with ripple effect
- **Typography**: Roboto font family
- **Spacing**: 16dp padding inside cards, 12dp between elements

## Main Activity Changes

When a recording starts, the info text now displays:

```
┌─────────────────────────────────────────┐
│                                         │
│        Recording for 15 seconds...      │
│                                         │
└─────────────────────────────────────────┘
```

Instead of the hardcoded:

```
┌─────────────────────────────────────────┐
│                                         │
│        Recording for 10 seconds...      │
│                                         │
└─────────────────────────────────────────┘
```

## Tutorial Dialog Update

The tutorial now dynamically shows the configured duration:

```
┌─────────────────────────────────────────────────┐
│  How This App Works                             │
├─────────────────────────────────────────────────┤
│                                                 │
│  Welcome to Motorcycle Voice Notes!             │
│                                                 │
│  Here's what happens when you launch the app:   │
│                                                 │
│  1. 📍 GPS location is acquired                 │
│  2. 🎤 Records 15 seconds of audio in MP3       │  ← Dynamic
│  3. 🗣️ Audio is transcribed to text in real-time│
│  4. 💾 Saved with GPS coordinates in filename   │
│  5. 📌 Waypoint created in GPX file using       │
│     transcribed text                            │
│  6. 🚀 Your chosen app launches automatically   │
│                                                 │
│  The app prefers Bluetooth microphones if       │
│  connected.                                     │
│                                                 │
│  Perfect for quick voice notes while riding!    │
│                                                 │
│           [ Start Recording ]                   │
│                                                 │
└─────────────────────────────────────────────────┘
```

## Implementation Summary

### New UI Elements
1. **durationValueText** (TextView): Displays current duration value
2. **durationEditText** (EditText): Input field for new duration
3. **setDurationButton** (Button): Saves the new duration

### Data Flow
1. Load from SharedPreferences: `getInt("recordingDuration", 10)`
2. User modifies value in EditText
3. Validation: 1 ≤ duration ≤ 99
4. Save to SharedPreferences: `putInt("recordingDuration", duration)`
5. Update UI display
6. Used by MainActivity for recording timer

### Key Code Locations
- **SettingsActivity.kt**: Lines 24-32, 66-67, 70, 88-90, 94-100, 225-250
- **MainActivity.kt**: Lines 125-127, 136, 284-286, 339, 344-347
- **activity_settings.xml**: Lines 110-158
- **strings.xml**: Lines 22-25
