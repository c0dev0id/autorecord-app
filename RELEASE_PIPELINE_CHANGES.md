# Release Pipeline Fixes

## Summary

This document describes the changes made to fix APK signing and release creation in the release pipeline.

## Problems Identified

1. **Secret Name Mismatch**: The workflow was using `SIGNING_KEY_PASSWORD` but the configured secret is `SIGNING_KEYSTORE_PASSWORD`
2. **Missing Signing Configuration**: The `vnmanager-opener` module had no signing configuration, so release APKs couldn't be signed
3. **Static Version Numbers**: The `vnmanager-opener` module used hardcoded version numbers instead of git-based versions
4. **Deprecated Actions**: The workflow used deprecated GitHub Actions (`actions/create-release@v1` and `actions/upload-release-asset@v1`)

## Changes Made

### 1. Fixed Secret Name in Workflow (`.github/workflows/release-pipeline.yml`)

**Changed:**
- `SIGNING_KEY_PASSWORD` → `SIGNING_KEYSTORE_PASSWORD` (lines 123, 129-132)

**Reason:** 
The problem statement specified that the configured secret is `SIGNING_KEYSTORE_PASSWORD`, not `SIGNING_KEY_PASSWORD`.

### 2. Added Signing Configuration to vnmanager-opener (`vnmanager-opener/build.gradle`)

**Added:**
- `getVersionName()` function to extract version from git tags (with fallback to "1.0.0")
- `getVersionCode()` function to count commits
- `signingConfigs.release` block to load keystore properties
- Conditional signing in `buildTypes.release`

**Reason:** 
The vnmanager-opener APK needs to be signed for release just like the main app. This change mirrors the signing configuration from the main app module.

### 3. Replaced Deprecated GitHub Actions

**Changed:**
- Replaced `actions/create-release@v1` with `softprops/action-gh-release@v1`
- Replaced multiple `actions/upload-release-asset@v1` calls with a single upload in the release action
- Simplified APK file handling by copying to standardized names in the workspace

**Reason:** 
The old actions are deprecated and no longer maintained. The new `softprops/action-gh-release@v1` action is the recommended modern alternative and simplifies the workflow by handling all file uploads in one step.

## How It Works Now

### Signing Process

1. **Setup Phase**: The workflow reads three secrets:
   - `SIGNING_KEYSTORE_BASE64`: Base64-encoded keystore file
   - `SIGNING_KEYSTORE_PASSWORD`: Password for the keystore
   - `SIGNING_KEY_ALIAS`: Alias for the signing key

2. **Keystore Configuration**: If the signing secrets are present:
   - The base64 keystore is decoded to `release.keystore`
   - A `keystore.properties` file is created with the credentials
   - Both `app` and `vnmanager-opener` modules read this file

3. **Build**: Both modules build release APKs:
   - If `keystore.properties` exists → signed release APKs
   - If not → unsigned release APKs (or build fails)

### Release Creation

1. **Tag Determination**: The workflow determines the release tag:
   - If manually triggered with a version → use that version
   - If triggered by a tag push → use that tag
   - Otherwise → auto-increment from the latest tag

2. **APK Preparation**: The workflow finds and copies APKs to standardized names:
   - `voice-notes-debug.apk`
   - `vnmanager-opener-debug.apk`
   - `voice-notes-release.apk` (if signed)
   - `vnmanager-opener-release.apk` (if signed)

3. **Release Creation**: Using `softprops/action-gh-release@v1`:
   - Creates a GitHub release with the determined tag
   - Uploads all found APK files
   - Includes a descriptive release body
   - Marks as draft/prerelease if not a semantic version

## Verification

To verify the changes work correctly:

1. Ensure the following secrets are configured in GitHub:
   - `SIGNING_KEYSTORE_BASE64`
   - `SIGNING_KEYSTORE_PASSWORD`
   - `SIGNING_KEY_ALIAS`

2. Trigger the workflow either by:
   - Pushing a tag: `git tag v1.0.0 && git push origin v1.0.0`
   - Manual dispatch from GitHub Actions UI

3. Check the workflow run:
   - Both APKs should be built and signed
   - A release should be created with all APKs attached

## Testing Checklist

- [ ] Verify workflow syntax is valid (YAML parsing)
- [ ] Ensure secrets are properly configured in GitHub repository settings
- [ ] Test workflow by pushing a semver tag (e.g., `v1.0.0`)
- [ ] Verify both debug APKs are always created
- [ ] Verify both release APKs are signed when secrets are present
- [ ] Confirm release page is created with proper tag
- [ ] Check that all APKs are uploaded to the release
- [ ] Validate APK signatures using `apksigner verify`
