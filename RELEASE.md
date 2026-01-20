# Release Guide

Quick reference for creating releases of the Motorcycle Voice Notes app.

## Automated Release Process

### 1. Update Version

Edit `app/build.gradle`:

```gradle
android {
    defaultConfig {
        versionCode 2      // Increment by 1
        versionName "1.0.1" // Update version string
    }
}
```

### 2. Commit Version Change

```bash
git add app/build.gradle
git commit -m "Bump version to 1.0.1"
git push
```

### 3. Create and Push Tag

```bash
git tag v1.0.1
git push origin v1.0.1
```

### 4. Wait for Build

- GitHub Actions will automatically build the APKs
- Navigate to [Actions](../../actions) to monitor progress
- Build typically takes 3-5 minutes

### 5. Release Created

- Check the [Releases](../../releases) page
- Release will include:
  - Debug APK (`motorcycle-voice-notes-1.0.1-debug.apk`)
  - Release APK (`motorcycle-voice-notes-1.0.1-release-unsigned.apk`)
  - Installation instructions
  - Usage guide

## Manual Release Process

If you need to trigger a release manually:

1. Go to [Actions](../../actions) tab
2. Select "Android Release Build"
3. Click "Run workflow"
4. Enter version number (e.g., `1.0.1`)
5. Click "Run workflow" button

Download APKs from the workflow run's artifacts.

## Version Numbering

Follow [Semantic Versioning](https://semver.org/):

- **Major** (X.0.0): Breaking changes or major features
- **Minor** (1.X.0): New features, backwards compatible
- **Patch** (1.0.X): Bug fixes, backwards compatible

### Examples

- `v1.0.0` - Initial release
- `v1.0.1` - Bug fix
- `v1.1.0` - New feature (e.g., configurable recording duration)
- `v2.0.0` - Major change (e.g., new UI, breaking changes)

## Release Checklist

Before creating a release:

- [ ] Test the app on physical device
- [ ] Verify all features work correctly
- [ ] Update `versionCode` and `versionName` in `app/build.gradle`
- [ ] Update `README.md` if there are new features
- [ ] Update `BUILD_INSTRUCTIONS.md` if build process changed
- [ ] Commit all changes
- [ ] Push commits to repository
- [ ] Create and push version tag
- [ ] Verify CI build succeeds
- [ ] Test downloaded APK from release

## Signing Release APKs

Currently, release APKs are unsigned. To sign them:

### Create Keystore

```bash
keytool -genkey -v -keystore release.keystore -alias motorcycle-voice-notes \
  -keyalg RSA -keysize 2048 -validity 10000
```

### Add Secrets to GitHub

Go to Repository Settings > Secrets and Variables > Actions

Add these secrets:
- `KEYSTORE_FILE`: Base64-encoded keystore
  ```bash
  cat release.keystore | base64 | pbcopy
  ```
- `KEYSTORE_PASSWORD`: Your keystore password
- `KEY_ALIAS`: `motorcycle-voice-notes`
- `KEY_PASSWORD`: Your key password

### Update Workflow

The signing step is already prepared in the workflow documentation. See [`.github/workflows/README.md`](.github/workflows/README.md) for instructions.

## Troubleshooting

### Tag Already Exists

```bash
# Delete local tag
git tag -d v1.0.1

# Delete remote tag
git push origin :refs/tags/v1.0.1

# Create new tag
git tag v1.0.1
git push origin v1.0.1
```

### Build Failed

1. Check [Actions](../../actions) tab for error logs
2. Verify `app/build.gradle` syntax is correct
3. Ensure all Kotlin files compile locally: `./gradlew build`
4. Check workflow file syntax at `.github/workflows/android-release.yml`

### Release Not Created

1. Verify tag format: Must be `v*.*.*` (e.g., `v1.0.0`)
2. Check workflow permissions in repository settings
3. Review workflow logs for specific errors

## Downloading APKs

### From Releases Page

1. Go to [Releases](../../releases)
2. Click on the latest release
3. Download APK from "Assets" section

### From Actions

1. Go to [Actions](../../actions)
2. Click on a successful workflow run
3. Scroll to "Artifacts" section
4. Download the APK artifact

## Distribution

### Direct Distribution

Share the APK file directly:
- Email
- Cloud storage (Google Drive, Dropbox)
- File sharing services

### GitHub Releases

Share the release URL:
```
https://github.com/YOUR_USERNAME/autorecord-app/releases/latest
```

### App Stores (Future)

To publish on Google Play Store:
1. Create a signed release APK
2. Create a Google Play Developer account
3. Set up store listing
4. Upload APK through Play Console
5. Submit for review

## Post-Release

After creating a release:

1. **Announce** the release (if applicable)
2. **Monitor** for bug reports
3. **Update** documentation if needed
4. **Plan** next version features

## Quick Commands Reference

```bash
# Check current version
grep -A 4 "defaultConfig" app/build.gradle

# Create release (all steps)
vim app/build.gradle  # Update version
git add app/build.gradle
git commit -m "Bump version to 1.0.1"
git push
git tag v1.0.1
git push origin v1.0.1

# View tags
git tag -l

# View latest tag
git describe --tags --abbrev=0

# Delete tag (if mistake)
git tag -d v1.0.1
git push origin :refs/tags/v1.0.1
```
