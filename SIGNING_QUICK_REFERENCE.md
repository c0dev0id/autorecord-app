# APK Signing Quick Reference

This document provides a quick overview of APK signing for the Motorcycle Voice Notes app.

## For Users

### I just want to use the app
- Download the **Debug APK** from [Releases](../../releases) or [Actions](../../actions)
- Debug APKs are automatically signed with a debug certificate
- No special configuration needed

## For Developers

### Building Locally

#### Debug APK (Signed automatically)
```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

#### Release APK (Unsigned by default)
```bash
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release-unsigned.apk
```

#### Release APK (Signed)
1. Set up signing once (see [SIGNING.md](SIGNING.md))
2. Build:
   ```bash
   ./build-signed-apk.sh
   # or
   ./gradlew assembleRelease
   # Output: app/build/outputs/apk/release/app-release.apk
   ```

### First-Time Signing Setup

1. **Generate keystore** (one time only):
   ```bash
   keytool -genkey -v -keystore release.keystore -alias motorcycle-voice-notes \
     -keyalg RSA -keysize 2048 -validity 10000
   ```

2. **Configure signing** (one time only):
   ```bash
   cp keystore.properties.template keystore.properties
   # Edit keystore.properties with your details
   ```

3. **Build signed APK**:
   ```bash
   ./build-signed-apk.sh
   ```

**Important**: 
- Backup your keystore securely!
- Never commit keystore.properties to Git (it's already in .gitignore)

### GitHub Actions / CI

By default, GitHub Actions builds:
- **Debug APK**: Always signed with debug key
- **Release APK**: Unsigned (unless you configure signing secrets)

To enable signing in GitHub Actions:
1. Add secrets to your repository (Settings > Secrets and variables > Actions):
   - `KEYSTORE_FILE` - Base64-encoded keystore
   - `KEYSTORE_PASSWORD` - Your keystore password
   - `KEY_ALIAS` - Your key alias
   - `KEY_PASSWORD` - Your key password

2. Uncomment the signing steps in `.github/workflows/android-release.yml`

See [SIGNING.md](SIGNING.md) for detailed instructions.

## APK Types Comparison

| APK Type | Signing | Use Case | How to Build |
|----------|---------|----------|--------------|
| **Debug** | Auto-signed with debug key | Development, testing, personal use | `./gradlew assembleDebug` |
| **Release (unsigned)** | Not signed | For manual signing later | `./gradlew assembleRelease` (no keystore.properties) |
| **Release (signed)** | Signed with your key | Distribution, Google Play | `./build-signed-apk.sh` (requires keystore setup) |

## When Do I Need Signing?

### ✅ You NEED signing if:
- Publishing to Google Play Store
- Distributing to many users
- Updating an already-installed app
- Users have strict security settings

### ❌ You DON'T need signing if:
- Testing on your own device (use debug APK)
- Sharing with a few friends (use debug APK)
- Just trying out the app (use debug APK)

## Quick Commands

```bash
# Build debug APK (always signed automatically)
./gradlew assembleDebug

# Build signed release APK (requires keystore setup)
./build-signed-apk.sh

# Build unsigned release APK
./gradlew assembleRelease  # without keystore.properties

# Verify APK is signed
jarsigner -verify -verbose app/build/outputs/apk/release/app-release.apk

# Install APK on connected device
adb install app/build/outputs/apk/release/app-release.apk
```

## Documentation

- **[SIGNING.md](SIGNING.md)** - Complete signing guide (keystore generation, configuration, troubleshooting)
- **[BUILD_INSTRUCTIONS.md](BUILD_INSTRUCTIONS.md)** - General build instructions
- **[RELEASE.md](RELEASE.md)** - Release process and versioning

## Troubleshooting

### "I want a signed APK but don't want to set up keystore"
Use the debug APK - it's automatically signed and works for most use cases.

### "Build fails with signing errors"
Make sure `keystore.properties` exists and has correct paths/passwords. Run `./build-signed-apk.sh` for helpful error messages.

### "Can I use the same keystore for multiple apps?"
Yes, but use a different alias for each app.

### "I lost my keystore!"
If you haven't published to Play Store yet, generate a new one. If you have published, you cannot update your app anymore with a different key.

## Need Help?

See the comprehensive [SIGNING.md](SIGNING.md) guide for:
- Step-by-step instructions
- Keystore best practices
- Security recommendations
- GitHub Actions setup
- Google Play Store publishing
- Detailed troubleshooting
