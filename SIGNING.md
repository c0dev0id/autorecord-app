# Signed APK Guide

This guide explains how to create and use signed APKs for the Motorcycle Voice Notes app.

## Why Sign Your APK?

A signed APK is required for:
- Publishing to Google Play Store
- Distributing to users who want verified apps
- Installing on devices with strict security settings
- Ensuring your app updates properly (must be signed with same key)

## Quick Start

### 1. Generate a Keystore

Use Java's `keytool` to create a keystore file:

```bash
keytool -genkey -v -keystore release.keystore -alias motorcycle-voice-notes \
  -keyalg RSA -keysize 2048 -validity 10000
```

You'll be prompted for:
- **Keystore password**: Choose a strong password (you'll need this later)
- **Key password**: Can be same as keystore password
- **Name, Organization, etc.**: Fill in your details

**Important**: Keep your keystore file and passwords safe! You'll need them for all future releases.

### 2. Create keystore.properties

Copy the template file:

```bash
cp keystore.properties.template keystore.properties
```

Edit `keystore.properties` with your keystore details:

```properties
storeFile=/absolute/path/to/release.keystore
storePassword=your-keystore-password
keyAlias=motorcycle-voice-notes
keyPassword=your-key-password
```

**Security Note**: Never commit `keystore.properties` to version control! It's already in `.gitignore`.

### 3. Build Signed APK

Build the signed release APK:

```bash
./gradlew assembleRelease
```

The signed APK will be at:
```
app/build/outputs/apk/release/app-release.apk
```

## Detailed Instructions

### Keystore Generation Options

#### Option 1: Using keytool (Recommended)

```bash
keytool -genkey -v \
  -keystore /path/to/release.keystore \
  -alias motorcycle-voice-notes \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

#### Option 2: Using Android Studio

1. Open Android Studio
2. Go to: Build > Generate Signed Bundle / APK
3. Select "APK" and click "Next"
4. Click "Create new..." to create a keystore
5. Fill in the keystore details
6. Save the keystore file securely

### Keystore Best Practices

1. **Backup Your Keystore**
   - Store multiple copies in secure locations
   - Use encrypted cloud storage or a password manager
   - If you lose your keystore, you can't update your app!

2. **Use Strong Passwords**
   - Minimum 8 characters
   - Mix uppercase, lowercase, numbers, symbols
   - Don't use the same password elsewhere

3. **Document Your Details**
   - Keep a secure record of:
     - Keystore location
     - Keystore password
     - Key alias
     - Key password
     - Organization details used

4. **Security**
   - Never commit keystore or passwords to Git
   - Don't share your keystore file
   - Don't include passwords in scripts

### Keystore Properties File Format

The `keystore.properties` file should look like:

```properties
storeFile=/Users/yourname/keystores/release.keystore
storePassword=MyStrongPassword123!
keyAlias=motorcycle-voice-notes
keyPassword=MyStrongPassword123!
```

**Notes**:
- Use absolute paths for `storeFile`
- Use forward slashes (/) even on Windows
- No quotes around values
- The file must be in the project root directory

### Building Different Types

#### Signed Release APK
```bash
./gradlew assembleRelease
```
Output: `app/build/outputs/apk/release/app-release.apk`

#### Unsigned Release APK (no keystore.properties)
```bash
./gradlew assembleRelease
```
Output: `app/build/outputs/apk/release/app-release-unsigned.apk`

#### Debug APK (always signed with debug key)
```bash
./gradlew assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

### Verifying APK Signature

To verify your APK is properly signed:

```bash
# Check signature
jarsigner -verify -verbose -certs app/build/outputs/apk/release/app-release.apk

# View certificate details
keytool -printcert -jarfile app/build/outputs/apk/release/app-release.apk
```

### Installing Signed APK

```bash
# Install on connected device
adb install app/build/outputs/apk/release/app-release.apk

# Reinstall (preserve data)
adb install -r app/build/outputs/apk/release/app-release.apk
```

## GitHub Actions (CI/CD)

To enable signed APKs in GitHub Actions:

### 1. Encode Keystore

Convert your keystore to base64:

```bash
# macOS/Linux
cat release.keystore | base64 | pbcopy

# Windows (PowerShell)
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) | Set-Clipboard
```

### 2. Add GitHub Secrets

Go to: Repository Settings > Secrets and variables > Actions

Add these secrets:
- `KEYSTORE_FILE` - The base64-encoded keystore
- `KEYSTORE_PASSWORD` - Your keystore password
- `KEY_ALIAS` - Your key alias (e.g., `motorcycle-voice-notes`)
- `KEY_PASSWORD` - Your key password

### 3. Update Workflow

The workflow file `.github/workflows/android-release.yml` needs to be updated with:

```yaml
- name: Decode Keystore
  run: |
    echo "${{ secrets.KEYSTORE_FILE }}" | base64 -d > release.keystore

- name: Create keystore.properties
  run: |
    echo "storeFile=../release.keystore" >> keystore.properties
    echo "storePassword=${{ secrets.KEYSTORE_PASSWORD }}" >> keystore.properties
    echo "keyAlias=${{ secrets.KEY_ALIAS }}" >> keystore.properties
    echo "keyPassword=${{ secrets.KEY_PASSWORD }}" >> keystore.properties

- name: Build Signed Release APK
  run: ./gradlew assembleRelease --stacktrace
```

## Troubleshooting

### "keystore.properties not found"

The app will build an unsigned APK if `keystore.properties` doesn't exist. Create the file following the instructions above.

### "keystore not found"

Check that:
- The path in `keystore.properties` is absolute
- The keystore file exists at that path
- You're using forward slashes (/) in the path

### "Keystore was tampered with, or password was incorrect"

- Verify your keystore password is correct
- Check for typos in `keystore.properties`
- Try regenerating the keystore if it's corrupted

### "certificate chain not found for: <alias>"

- Check that your `keyAlias` matches the alias used when creating the keystore
- Use `keytool -list -keystore release.keystore` to see available aliases

### Build fails with signing errors

Try:
```bash
./gradlew clean
./gradlew assembleRelease --stacktrace
```

## Migration from Unsigned to Signed

If you previously released unsigned APKs:

1. **First signed release**: Users may need to uninstall and reinstall
2. **Future updates**: Must use the same keystore for all updates
3. **Version code**: Increment version code in `app/build.gradle`

## Google Play Store Publishing

To publish on Google Play Store:

1. Generate a signed release APK using these instructions
2. Create a Google Play Developer account ($25 one-time fee)
3. Create a new app in Play Console
4. Fill in store listing details
5. Upload your signed APK
6. Complete the content rating questionnaire
7. Submit for review

Note: Google Play now prefers Android App Bundle (AAB) format. To build AAB:

```bash
./gradlew bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`

## Security Checklist

Before distributing your signed APK:

- [ ] Keystore is backed up in secure location
- [ ] Passwords are documented securely
- [ ] `keystore.properties` is not in version control
- [ ] APK signature is verified with `jarsigner`
- [ ] Version code and version name are correct
- [ ] APK is tested on physical device

## Resources

- [Android App Signing Documentation](https://developer.android.com/studio/publish/app-signing)
- [Keytool Documentation](https://docs.oracle.com/javase/8/docs/technotes/tools/unix/keytool.html)
- [Google Play Console](https://play.google.com/console)

## Quick Reference

```bash
# Generate keystore
keytool -genkey -v -keystore release.keystore -alias motorcycle-voice-notes -keyalg RSA -keysize 2048 -validity 10000

# Build signed APK
./gradlew assembleRelease

# Verify signature
jarsigner -verify -verbose app/build/outputs/apk/release/app-release.apk

# Install on device
adb install app/build/outputs/apk/release/app-release.apk
```
