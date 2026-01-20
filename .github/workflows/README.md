# GitHub Actions Workflows

This directory contains automated workflows for building and releasing the Motorcycle Voice Notes Android app.

## Workflows

### 1. Android CI Build (`android-build.yml`)

**Triggers:**
- Push to `main`, `develop`, or `claude/**` branches
- Pull requests to `main` or `develop`
- Manual trigger via workflow_dispatch

**Actions:**
- Sets up Java 17 and Android SDK
- Builds debug APK
- Uploads APK as artifact (30-day retention)

**Usage:**
Runs automatically on every push. Download the APK from the workflow run's artifacts.

---

### 2. Android Release Build (`android-release.yml`)

**Triggers:**
- Push of version tags (format: `v*.*.*`, e.g., `v1.0.0`)
- Manual trigger with version input

**Actions:**
- Builds both debug and release APKs
- Renames APKs with version number
- Creates GitHub Release with APKs attached
- Includes installation and usage instructions

**Usage:**

**Create a release automatically:**
```bash
git tag v1.0.0
git push origin v1.0.0
```

**Or trigger manually:**
1. Go to Actions tab in GitHub
2. Select "Android Release Build"
3. Click "Run workflow"
4. Enter version number (e.g., 1.0.0)

The workflow will create a draft release with both APKs attached.

---

### 3. Pull Request Check (`pr-check.yml`)

**Triggers:**
- Pull request opened, synchronized, or reopened

**Actions:**
- Runs Android lint checks
- Builds debug APK to verify compilation
- Posts build status comment on PR
- Uploads lint report as artifact

**Usage:**
Runs automatically on all pull requests. Ensures code quality before merging.

---

## Artifacts

All workflows upload build artifacts that you can download:

1. **Navigate to Actions tab** in GitHub
2. **Click on a workflow run**
3. **Scroll to Artifacts section**
4. **Download** the APK file

Artifacts are retained for:
- CI builds: 30 days
- PR checks: 7 days
- Releases: Permanently (attached to release)

---

## Release Process

To create a new release:

### Automated (Recommended)

1. **Update version** in `app/build.gradle`:
   ```gradle
   versionCode 2
   versionName "1.0.1"
   ```

2. **Commit changes**:
   ```bash
   git add app/build.gradle
   git commit -m "Bump version to 1.0.1"
   git push
   ```

3. **Create and push tag**:
   ```bash
   git tag v1.0.1
   git push origin v1.0.1
   ```

4. **Wait for workflow** to complete

5. **Check Releases page** for the new release with APKs

### Manual

1. Go to Actions > Android Release Build
2. Click "Run workflow"
3. Enter version number
4. Click "Run workflow"
5. Download APKs from artifacts or create release manually

---

## Environment Setup

The workflows use:

- **Runner**: `ubuntu-latest`
- **Java**: JDK 17 (Temurin distribution)
- **Android SDK**: Installed via `android-actions/setup-android@v3`
- **Gradle**: Version specified in `gradle-wrapper.properties`

### Caching

Gradle dependencies are cached to speed up builds:
- Gradle caches: `~/.gradle/caches`
- Gradle wrapper: `~/.gradle/wrapper`

Cache key is based on Gradle files hash, ensuring fresh builds when dependencies change.

---

## Troubleshooting

### Build Fails

1. **Check Java version**: Workflow uses JDK 17
2. **Check Gradle version**: Update `gradle-wrapper.properties` if needed
3. **Review error logs**: Click on failed workflow for details

### Release Not Created

1. **Verify tag format**: Must be `v*.*.*` (e.g., `v1.0.0`)
2. **Check permissions**: Repository needs `GITHUB_TOKEN` with release permissions
3. **Review workflow logs**: Look for specific error messages

### APK Not Uploaded

1. **Check build logs**: Ensure `assembleDebug` completed successfully
2. **Verify path**: APK should be at `app/build/outputs/apk/debug/app-debug.apk`
3. **Review artifact upload step**: Look for upload errors

---

## Customization

### Change Trigger Branches

Edit `android-build.yml`:
```yaml
on:
  push:
    branches: [ main, your-branch ]
```

### Modify Retention Days

Edit artifact upload step:
```yaml
- name: Upload Debug APK
  uses: actions/upload-artifact@v4
  with:
    retention-days: 90  # Change from 30
```

### Add Signing for Release APK

To sign release APKs, add signing configuration:

1. **Create keystore** locally
2. **Add secrets** to GitHub repository:
   - `KEYSTORE_FILE` (base64 encoded)
   - `KEYSTORE_PASSWORD`
   - `KEY_ALIAS`
   - `KEY_PASSWORD`

3. **Update `android-release.yml`**:
   ```yaml
   - name: Decode Keystore
     run: |
       echo "${{ secrets.KEYSTORE_FILE }}" | base64 -d > keystore.jks

   - name: Build Signed Release APK
     run: |
       ./gradlew assembleRelease \
         -Pandroid.injected.signing.store.file=$PWD/keystore.jks \
         -Pandroid.injected.signing.store.password=${{ secrets.KEYSTORE_PASSWORD }} \
         -Pandroid.injected.signing.key.alias=${{ secrets.KEY_ALIAS }} \
         -Pandroid.injected.signing.key.password=${{ secrets.KEY_PASSWORD }}
   ```

---

## Build Status Badges

Add to README.md:

```markdown
![Android CI](https://github.com/YOUR_USERNAME/autorecord-app/workflows/Android%20CI%20Build/badge.svg)
![Release](https://github.com/YOUR_USERNAME/autorecord-app/workflows/Android%20Release%20Build/badge.svg)
```

Replace `YOUR_USERNAME` with your GitHub username.
