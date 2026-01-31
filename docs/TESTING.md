# Testing Guide

This document describes the testing strategy for the Autorecord app and how to run tests locally and in CI.

## Table of Contents

- [Overview](#overview)
- [Unit Tests](#unit-tests)
- [Instrumentation Tests](#instrumentation-tests)
- [Running Tests Locally](#running-tests-locally)
- [CI/CD Testing](#cicd-testing)
- [Firebase Test Lab Setup](#firebase-test-lab-setup)

## Overview

The Autorecord app uses two types of tests:

### Unit Tests
- **Purpose**: Test individual components, classes, and methods in isolation
- **Scope**: Business logic, utilities, data processing
- **Location**: `app/src/test/`
- **Framework**: JUnit 4, Mockito
- **Execution**: Fast, runs on JVM without Android emulator

### Instrumentation Tests
- **Purpose**: Test complete user workflows and UI interactions on actual devices
- **Scope**: End-to-end functionality, integration with Android framework
- **Location**: `app/src/androidTest/`
- **Framework**: AndroidX Test, Espresso
- **Execution**: Runs on physical devices or emulators

## Unit Tests

Unit tests validate business logic and components without requiring an Android device or emulator.

### Running Unit Tests Locally

```bash
# Run all unit tests
./gradlew testDebugUnitTest

# Run tests with code coverage report
./gradlew testDebugUnitTest jacocoTestReport

# Run specific test class
./gradlew test --tests "com.voicenotes.motorcycle.FilenameUtilsTest"

# Run tests with detailed output
./gradlew testDebugUnitTest --info
```

### Test Reports

After running unit tests, view the HTML report:
```
app/build/reports/tests/testDebugUnitTest/index.html
```

Coverage reports (if generated with JaCoCo):
```
app/build/reports/jacoco/jacocoTestReport/html/index.html
```

## Instrumentation Tests

Instrumentation tests run on Android devices or emulators and test the complete application stack.

### Running Instrumentation Tests Locally

```bash
# Build test APKs
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest

# Install and run tests on connected device/emulator
./gradlew connectedAndroidTest

# Run specific test class
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.voicenotes.motorcycle.MainActivityTest
```

### Test APKs

After building, the APKs are located at:
- App APK: `app/build/outputs/apk/debug/app-debug.apk`
- Test APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`

## CI/CD Testing

### Unit Tests Workflow

The **Unit Tests** workflow runs automatically on all pull requests:

- **Trigger**: Any pull request
- **Actions**: 
  - Runs `./gradlew testDebugUnitTest check --no-daemon --stacktrace`
  - Executes lint checks
  - Uploads test results as artifacts
- **View workflow**: `.github/workflows/unit-tests.yml`

### Viewing CI Test Results

1. Navigate to the **Actions** tab in GitHub
2. Select the workflow run for your PR
3. Download test result artifacts for detailed reports

## Firebase Test Lab Setup

Firebase Test Lab runs instrumentation tests on real Android devices in Google's cloud infrastructure.

### Workflow Overview

The **Instrumentation tests (Firebase Test Lab)** workflow:

- **Trigger**: Manual (`workflow_dispatch`)
- **Actions**:
  - Builds debug and test APKs
  - Uploads APKs to Firebase Test Lab
  - Runs tests on specified device configuration
- **View workflow**: `.github/workflows/instrumentation-firebase.yml`

### Prerequisites

To use Firebase Test Lab, you need:

1. A Google Cloud Platform (GCP) project
2. Enabled APIs in your GCP project
3. A service account with appropriate permissions
4. GitHub repository secrets configured

### Step 1: Enable Required GCP APIs

Enable the following APIs in your GCP project:

```bash
# Set your project ID
export GCP_PROJECT_ID="your-project-id"

# Enable required APIs
gcloud services enable testing.googleapis.com
gcloud services enable toolresults.googleapis.com
gcloud services enable storage.googleapis.com
gcloud services enable firebase.googleapis.com
gcloud services enable iam.googleapis.com
```

Or enable via the [GCP Console](https://console.cloud.google.com/apis/library):
- Cloud Testing API
- Cloud Tool Results API
- Cloud Storage API
- Firebase API
- Identity and Access Management (IAM) API

### Step 2: Create Service Account

Create a service account for GitHub Actions:

```bash
# Create service account
gcloud iam service-accounts create github-actions-test-lab \
  --display-name="GitHub Actions Test Lab" \
  --project="${GCP_PROJECT_ID}"

# Get the service account email
SERVICE_ACCOUNT_EMAIL="github-actions-test-lab@${GCP_PROJECT_ID}.iam.gserviceaccount.com"
```

### Step 3: Grant IAM Roles

Grant the service account the necessary permissions:

```bash
# Grant roles (start with full access, then reduce to least privilege)
gcloud projects add-iam-policy-binding "${GCP_PROJECT_ID}" \
  --member="serviceAccount:${SERVICE_ACCOUNT_EMAIL}" \
  --role="roles/storage.admin"

gcloud projects add-iam-policy-binding "${GCP_PROJECT_ID}" \
  --member="serviceAccount:${SERVICE_ACCOUNT_EMAIL}" \
  --role="roles/cloudtestservice.testAdmin"

gcloud projects add-iam-policy-binding "${GCP_PROJECT_ID}" \
  --member="serviceAccount:${SERVICE_ACCOUNT_EMAIL}" \
  --role="roles/firebase.admin"
```

**Recommended**: After initial testing, reduce permissions to least privilege:
- `roles/storage.objectCreator` (instead of storage.admin)
- `roles/cloudtestservice.testViewer` (if read-only access is sufficient)
- Custom role with only required permissions

### Step 4: Create Service Account Key

Generate a JSON key file:

```bash
# Create and download key
gcloud iam service-accounts keys create github-actions-key.json \
  --iam-account="${SERVICE_ACCOUNT_EMAIL}"

# The key will be saved to github-actions-key.json
# Keep this file secure - it provides full access to your GCP project
```

### Step 5: Configure GitHub Secrets

Add the following secrets to your GitHub repository:

1. Go to your repository on GitHub
2. Navigate to **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret**
4. Add these secrets:

**GCP_PROJECT_ID**
- Name: `GCP_PROJECT_ID`
- Value: Your GCP project ID (e.g., `my-project-123456`)

**GOOGLE_CLOUD_SERVICE_ACCOUNT_JSON**
- Name: `GOOGLE_CLOUD_SERVICE_ACCOUNT_JSON`
- Value: The entire contents of `github-actions-key.json`

To copy the JSON contents:
```bash
cat github-actions-key.json
# Copy the entire output and paste into GitHub secret
```

**Important**: After adding the secret to GitHub, delete the local key file:
```bash
rm github-actions-key.json
```

### Step 6: Trigger the Workflow

To run instrumentation tests on Firebase Test Lab:

1. Go to your repository on GitHub
2. Navigate to **Actions** tab
3. Select **Instrumentation tests (Firebase Test Lab)** workflow
4. Click **Run workflow** button
5. Select the branch to run tests on
6. Click **Run workflow**

### Viewing Test Results

After the workflow completes:

1. View the workflow run in the **Actions** tab
2. Check the Firebase Test Lab console:
   - Go to [Firebase Console](https://console.firebase.google.com/)
   - Select your project
   - Navigate to **Test Lab** → **Test History**
3. View detailed test results, logs, screenshots, and videos

### Device Configuration

The default device configuration is:
- **Model**: Nexus 6
- **API Level**: 28 (Android 9)
- **Locale**: en (English)
- **Orientation**: portrait

To test on different devices, modify the `device` parameter in `.github/workflows/instrumentation-firebase.yml`:

```yaml
device: model=Pixel4,version=30,locale=en,orientation=portrait
```

Available devices can be listed with:
```bash
gcloud firebase test android models list
```

## Troubleshooting

### Unit Tests

**Problem**: Tests fail with "No such file or directory"
- **Solution**: Ensure you're running commands from the repository root

**Problem**: Gradle daemon issues
- **Solution**: Use `--no-daemon` flag or run `./gradlew --stop`

### Firebase Test Lab

**Problem**: "Permission denied" errors
- **Solution**: Verify IAM roles are correctly assigned to service account

**Problem**: "API not enabled"
- **Solution**: Re-run the `gcloud services enable` commands

**Problem**: Test APK build fails
- **Solution**: Ensure `gradle.properties` is configured (may need dummy values for local builds)

**Problem**: Workflow fails with authentication error
- **Solution**: Verify GitHub secrets are correctly set with exact names

## Best Practices

1. **Write unit tests first**: They run faster and catch issues early
2. **Use instrumentation tests for UI**: Test complete user workflows
3. **Keep tests independent**: Each test should run in isolation
4. **Use descriptive test names**: Clearly indicate what is being tested
5. **Add debug output**: Include extensive logging in unit tests for debugging
6. **Regular test runs**: Run tests locally before pushing code
7. **Monitor CI failures**: Address test failures promptly
8. **Maintain test coverage**: Aim for high coverage on critical code paths
9. **Secure credentials**: Never commit service account keys to the repository
10. **Review permissions**: Use least privilege for service accounts

## Additional Resources

- [Android Testing Documentation](https://developer.android.com/training/testing)
- [Firebase Test Lab Documentation](https://firebase.google.com/docs/test-lab)
- [JUnit 4 Guide](https://junit.org/junit4/)
- [Mockito Documentation](https://site.mockito.org/)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)
