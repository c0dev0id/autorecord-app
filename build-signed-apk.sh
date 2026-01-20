#!/bin/bash

# Build script for signed release APK
# This script builds a signed release APK for the Motorcycle Voice Notes Android App

echo "=============================================="
echo "Motorcycle Voice Notes - Signed Release Build"
echo "=============================================="
echo ""

# Check if Android SDK is set
if [ -z "$ANDROID_HOME" ] && [ ! -f "local.properties" ]; then
    echo "ERROR: Android SDK not found!"
    echo ""
    echo "Please set ANDROID_HOME environment variable or create local.properties file"
    echo "See BUILD_INSTRUCTIONS.md for details"
    echo ""
    exit 1
fi

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "ERROR: Java is not installed!"
    echo "Please install Java JDK 8 or higher"
    exit 1
fi

echo "Java version:"
java -version
echo ""

# Check if keystore.properties exists
if [ ! -f "keystore.properties" ]; then
    echo "ERROR: keystore.properties not found!"
    echo ""
    echo "To build a signed APK, you need to set up signing configuration."
    echo ""
    echo "Quick setup:"
    echo "  1. Generate a keystore:"
    echo "     keytool -genkey -v -keystore release.keystore -alias motorcycle-voice-notes \\"
    echo "       -keyalg RSA -keysize 2048 -validity 10000"
    echo ""
    echo "  2. Create keystore.properties:"
    echo "     cp keystore.properties.template keystore.properties"
    echo ""
    echo "  3. Edit keystore.properties with your keystore details"
    echo ""
    echo "For detailed instructions, see SIGNING.md"
    echo ""
    echo "Alternatively, build an unsigned release APK with:"
    echo "  ./gradlew assembleRelease"
    echo ""
    exit 1
fi

# Verify keystore file exists
KEYSTORE_FILE=$(grep "storeFile=" keystore.properties | cut -d'=' -f2-)
if [ ! -f "$KEYSTORE_FILE" ]; then
    echo "ERROR: Keystore file not found at: $KEYSTORE_FILE"
    echo ""
    echo "Please verify the 'storeFile' path in keystore.properties"
    echo "Use an absolute path to your keystore file"
    echo ""
    exit 1
fi

echo "Keystore found: $KEYSTORE_FILE"
echo ""

# Make gradlew executable
chmod +x gradlew

echo "Starting signed release build..."
echo ""

# Clean and build signed release APK
./gradlew clean assembleRelease

# Check if build was successful
if [ $? -eq 0 ]; then
    echo ""
    echo "=============================================="
    echo "BUILD SUCCESSFUL!"
    echo "=============================================="
    echo ""
    
    # Check if APK is signed
    if [ -f "app/build/outputs/apk/release/app-release.apk" ]; then
        echo "Signed APK location: app/build/outputs/apk/release/app-release.apk"
        echo ""
        
        # Show APK size
        APK_SIZE=$(du -h app/build/outputs/apk/release/app-release.apk | cut -f1)
        echo "APK size: $APK_SIZE"
        echo ""
        
        # Verify signature
        echo "Verifying APK signature..."
        jarsigner -verify -verbose app/build/outputs/apk/release/app-release.apk 2>&1 | grep -i "jar verified" && echo "✓ APK signature verified successfully" || echo "⚠ APK signature verification failed"
        echo ""
        
        echo "To view certificate details:"
        echo "  keytool -printcert -jarfile app/build/outputs/apk/release/app-release.apk"
        echo ""
        echo "To install on a connected device:"
        echo "  adb install app/build/outputs/apk/release/app-release.apk"
        echo ""
        echo "To install on a device (replacing existing version):"
        echo "  adb install -r app/build/outputs/apk/release/app-release.apk"
        echo ""
    else
        echo "Warning: Expected APK not found at app/build/outputs/apk/release/app-release.apk"
        echo "Listing all APKs in release directory:"
        ls -lh app/build/outputs/apk/release/
        echo ""
    fi
else
    echo ""
    echo "=============================================="
    echo "BUILD FAILED!"
    echo "=============================================="
    echo ""
    echo "Please check the error messages above."
    echo ""
    echo "Common issues:"
    echo "  - Incorrect keystore password in keystore.properties"
    echo "  - Wrong keystore path in keystore.properties"
    echo "  - Invalid key alias in keystore.properties"
    echo ""
    echo "For troubleshooting, see SIGNING.md"
    echo ""
    exit 1
fi
