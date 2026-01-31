# CI/CD Workflows Documentation

This document describes the CI/CD workflows configured for the AutoRecord Android application.

## Overview

The CI/CD pipeline is structured into three main workflows:

1. **Fast PR Check** (`pr-fast-check.yml`) - Runs on every PR and commit for quick feedback
2. **Production Readiness** (`production-readiness.yml`) - Comprehensive checks for production deployment
3. **Release Pipeline** (`release-pipeline.yml`) - Automated release creation and publishing

## Workflows

### 1. Fast PR Check (`pr-fast-check.yml`)

**Triggers:**
- Push to branches: `claude/android-voice-notes-app-PHSNL`, `main`, `develop`
- Pull requests to: `claude/android-voice-notes-app-PHSNL`, `main`, `develop`
- Ignores changes to: `*.md`, `docs/**`, `.gitignore`, `LICENSE`

**Jobs:**

#### 1.1 warm-caches
- **Purpose:** Populate Gradle caches for faster subsequent builds
- **Runs:** First, no dependencies
- **Timeout:** 30 minutes
- **Actions:**
  - Checks out code with full history (`fetch-depth: 0`)
  - Sets up JDK 17 (Temurin distribution)
  - Caches Gradle dependencies and wrapper
  - Runs lightweight `./gradlew tasks` to warm caches
- **Cache Key:** `${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties', 'gradle/**') }}`

#### 1.2 unit-tests
- **Purpose:** Run unit tests
- **Depends on:** `warm-caches`
- **Timeout:** 30 minutes
- **Actions:**
  - Restores Gradle caches
  - Runs `./gradlew testDebugUnitTest`
  - Uploads test results as artifacts (retention: 7 days)

#### 1.3 lint
- **Purpose:** Run Android lint checks
- **Depends on:** `warm-caches`
- **Timeout:** 30 minutes
- **Actions:**
  - Restores Gradle caches
  - Runs `./gradlew lintDebug`
  - Uploads lint reports as artifacts (retention: 7 days)

#### 1.4 ktlint
- **Purpose:** Kotlin code style checking
- **Depends on:** `warm-caches`
- **Timeout:** 20 minutes
- **Actions:**
  - Downloads ktlint binary (v1.0.1)
  - Runs ktlint on `app/src/**/*.kt`
  - Uploads ktlint report as artifact (retention: 7 days)
- **Note:** Detekt is not configured in this project, so we use ktlint instead

#### 1.5 dependency-review
- **Purpose:** Review dependencies for security vulnerabilities
- **Depends on:** `warm-caches`
- **Timeout:** 15 minutes
- **Runs only:** On pull requests
- **Actions:**
  - Uses `actions/dependency-review-action@v4`
  - Fails on moderate or higher severity issues
  - Posts summary comment on PR failures

#### 1.6 build
- **Purpose:** Build all APK variants
- **Depends on:** `unit-tests`, `lint`
- **Timeout:** 45 minutes
- **Actions:**
  - Restores Gradle caches
  - Runs `./gradlew assembleDebug assembleRelease assembleDebugAndroidTest`
  - Uploads three APK artifacts:
    - `app-debug-apk` (app-debug.apk)
    - `app-release-apk` (app-release-unsigned.apk)
    - `app-debug-androidTest-apk` (app-debug-androidTest.apk)
  - Retention: 7 days

#### 1.7 update-caches
- **Purpose:** Update Gradle caches after all jobs complete
- **Depends on:** `build`, `ktlint`
- **Runs:** Always (even if previous jobs fail)
- **Timeout:** 15 minutes
- **Actions:**
  - Restores and updates Gradle caches
  - Runs `./gradlew tasks` to ensure cache is current

**Parallelization:**
- `warm-caches` runs first (sequential)
- `unit-tests`, `lint`, `ktlint`, `dependency-review` run in parallel after `warm-caches`
- `build` runs after `unit-tests` and `lint` complete
- `update-caches` runs last

---

### 2. Production Readiness (`production-readiness.yml`)

**Triggers:**
- Manual trigger via `workflow_dispatch`
- Push to `main` or `claude/android-voice-notes-app-PHSNL` branches
- Nightly schedule at 2 AM UTC

**Jobs:**

#### 2.1 warm-caches
- **Purpose:** Populate Gradle caches
- **Timeout:** 30 minutes
- Same as Fast PR Check warm-caches job

#### 2.2 build
- **Purpose:** Build all APK variants for production
- **Depends on:** `warm-caches`
- **Timeout:** 60 minutes
- **Actions:**
  - Builds all variants: Debug, Release, AndroidTest
  - Uploads APK artifacts (retention: 30 days)

#### 2.3 lint
- **Purpose:** Run comprehensive lint checks
- **Depends on:** `warm-caches`
- **Timeout:** 30 minutes
- **Actions:**
  - Runs `./gradlew lintDebug lintRelease`
  - Uploads lint reports (retention: 30 days)

#### 2.4 ktlint
- **Purpose:** Kotlin style checking
- **Depends on:** `warm-caches`
- **Timeout:** 20 minutes
- Same as Fast PR Check ktlint job

#### 2.5 codeql
- **Purpose:** Security vulnerability scanning with CodeQL
- **Depends on:** `warm-caches`
- **Timeout:** 60 minutes
- **Permissions:** `actions: read`, `contents: read`, `security-events: write`
- **Actions:**
  - Initializes CodeQL for `java-kotlin` languages
  - Uses `security-and-quality` query suite
  - Builds app with `./gradlew assembleDebug`
  - Performs CodeQL analysis
  - Uploads results to GitHub Security tab

#### 2.6 firebase-test-lab
- **Purpose:** Run instrumentation tests on Firebase Test Lab
- **Depends on:** `build`
- **Timeout:** 120 minutes
- **Runs only:** When not triggered by schedule (to save Firebase quota)
- **Required Secrets:**
  - `GOOGLE_CLOUD_SERVICE_ACCOUNT_JSON`: GCP service account credentials
  - `GCP_PROJECT_ID`: GCP project ID
- **Actions:**
  - Downloads debug and androidTest APKs
  - Authenticates to Google Cloud
  - Runs tests on device: Nexus6, API 28
  - Stores results in GCS bucket
  - Uploads test results as artifacts (retention: 30 days)

#### 2.7 coverage
- **Purpose:** Generate test coverage reports
- **Depends on:** `warm-caches`
- **Timeout:** 30 minutes
- **Actions:**
  - Runs `./gradlew testDebugUnitTest jacocoTestReport`
  - Uploads JaCoCo coverage reports (retention: 30 days)

#### 2.8 upload-artifacts
- **Purpose:** Create draft GitHub release with APKs
- **Depends on:** `build`, `lint`, `codeql`
- **Runs only:** On `workflow_dispatch` or `push` events
- **Timeout:** 15 minutes
- **Permissions:** `contents: write`
- **Actions:**
  - Downloads debug and release APKs
  - Generates release tag: `draft-YYYYMMDD-HHMMSS`
  - Creates draft GitHub release
  - Uploads APKs as raw release assets (not zipped):
    - `app-debug.apk` (content-type: `application/vnd.android.package-archive`)
    - `app-release-unsigned.apk` (content-type: `application/vnd.android.package-archive`)

#### 2.9 update-caches
- **Purpose:** Update Gradle caches
- **Depends on:** `build`, `lint`, `ktlint`, `codeql`, `coverage`
- **Runs:** Always
- **Timeout:** 15 minutes

#### 2.10 aggregation
- **Purpose:** Report overall status
- **Depends on:** `build`, `lint`, `ktlint`, `codeql`, `coverage`, `update-caches`
- **Runs:** Always
- **Timeout:** 5 minutes
- **Actions:**
  - Generates summary of all job results
  - Fails if critical jobs (`build` or `codeql`) failed
  - Adds summary to GitHub Step Summary

**Parallelization:**
- `warm-caches` runs first
- `build`, `lint`, `ktlint`, `codeql`, `coverage` run in parallel after `warm-caches`
- `firebase-test-lab` runs after `build`
- `upload-artifacts` runs after `build`, `lint`, `codeql`
- `update-caches` runs after all main jobs
- `aggregation` runs last

---

### 3. Release Pipeline (`release-pipeline.yml`)

**Triggers:**
- Push of tags matching `v*.*.*` (e.g., `v1.0.0`, `v2.1.3`)
- Manual trigger via `workflow_dispatch` with version input

**Concurrency:** Only one release workflow at a time, no cancellation

**Jobs:**

#### 3.1 warm-caches
- **Purpose:** Populate Gradle caches
- **Timeout:** 30 minutes
- Same as other workflows

#### 3.2 build
- **Purpose:** Build production-ready release APK
- **Depends on:** `warm-caches`
- **Timeout:** 60 minutes
- **Actions:**
  - Optionally sets up APK signing (if secrets are configured)
  - Runs `./gradlew assembleRelease`
  - Uploads release APK (retention: 90 days)
  - Generates build summary showing APK size and signing status

**Optional Signing Configuration:**
Required secrets for signing:
- `SIGNING_KEY_PASSWORD`: Keystore and key password
- `SIGNING_KEY_ALIAS`: Key alias in keystore
- `SIGNING_KEYSTORE_BASE64`: Base64-encoded keystore file

#### 3.3 smoke-tests
- **Purpose:** Run critical smoke tests
- **Depends on:** `build`
- **Timeout:** 60 minutes
- **Runs only:** On manual `workflow_dispatch`
- **Actions:**
  - Downloads release APK
  - Runs local smoke tests (placeholder)
  - Can be extended to run Firebase Test Lab tests

#### 3.4 upload-release-artifacts
- **Purpose:** Create GitHub release and upload APK
- **Depends on:** `build`
- **Timeout:** 15 minutes
- **Permissions:** `contents: write`
- **Actions:**
  - Determines release tag (from git tag or manual input)
  - Creates GitHub release (draft for manual dispatch, published for tag push)
  - Uploads release APK as raw asset (content-type: `application/vnd.android.package-archive`)
  - Includes release notes with link to CHANGELOG.md

#### 3.5 publish-play-store (DISABLED BY DEFAULT)
- **Purpose:** Publish to Google Play Store
- **Currently:** Commented out, not enabled
- **To enable:** Uncomment job and configure secrets

**Required secrets for Play Store publishing:**
- `PLAY_STORE_SERVICE_ACCOUNT_JSON`: Google Play Console service account JSON

#### 3.6 release-summary
- **Purpose:** Report release pipeline status
- **Depends on:** `build`, `upload-release-artifacts`
- **Runs:** Always
- **Timeout:** 5 minutes
- **Actions:**
  - Generates summary of release pipeline
  - Fails if critical jobs failed

**Parallelization:**
- `warm-caches` runs first
- `build` runs after `warm-caches`
- `smoke-tests` runs after `build` (optional)
- `upload-release-artifacts` runs after `build`
- `release-summary` runs last

---

## Cache Strategy

### Cache Keys

All workflows use consistent cache keys for Gradle dependencies:

**Primary Key:**
```yaml
key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties', 'gradle/**') }}
```

This key includes:
- Operating system (`ubuntu-latest`)
- Hash of all Gradle build files
- Hash of gradle-wrapper.properties
- Hash of files in gradle/ directory

**Restore Keys (fallback):**
```yaml
restore-keys: |
  ${{ runner.os }}-gradle-
```

### Cached Paths

```yaml
~/.gradle/caches    # Gradle dependency cache
~/.gradle/wrapper   # Gradle wrapper distributions
```

### Cache Invalidation

Caches are automatically invalidated when:
- Any `*.gradle` or `*.gradle.kts` file changes
- `gradle-wrapper.properties` changes
- Any file in `gradle/` directory changes

### Cache Warming

Each workflow includes a `warm-caches` job that:
1. Runs first before other jobs
2. Restores existing cache or creates new one
3. Runs `./gradlew tasks --no-daemon --build-cache`
4. Saves cache for subsequent jobs

### Cache Updates

Each workflow includes an `update-caches` job that:
1. Runs after all other jobs complete
2. Updates cache with any new dependencies
3. Ensures cache is current for next workflow run

---

## Required GitHub Secrets

### Required for All Workflows

| Secret Name | Description | Required For |
|------------|-------------|--------------|
| `GOOGLE_CLOUD_SERVICE_ACCOUNT_JSON` | GCP service account JSON for Speech-to-Text API | All builds |
| `OSM_CLIENT_ID` | OpenStreetMap OAuth client ID | All builds |

### Required for Firebase Test Lab

| Secret Name | Description | Required For |
|------------|-------------|--------------|
| `GOOGLE_CLOUD_SERVICE_ACCOUNT_JSON` | GCP service account JSON (same as above) | Firebase Test Lab |
| `GCP_PROJECT_ID` | GCP project ID (e.g., `my-project-12345`) | Firebase Test Lab |

**Note:** The GCP service account must have permissions:
- Cloud Testing API
- Cloud Storage (for storing test results)

### Optional - For Signed Releases

| Secret Name | Description | Required For |
|------------|-------------|--------------|
| `SIGNING_KEY_PASSWORD` | Password for keystore and key | Signed release builds |
| `SIGNING_KEY_ALIAS` | Alias of the signing key in keystore | Signed release builds |
| `SIGNING_KEYSTORE_BASE64` | Base64-encoded keystore file | Signed release builds |

To encode your keystore:
```bash
base64 -i your-keystore.jks | tr -d '\n'
```

### Optional - For Play Store Publishing

| Secret Name | Description | Required For |
|------------|-------------|--------------|
| `PLAY_STORE_SERVICE_ACCOUNT_JSON` | Google Play Console service account JSON | Play Store publishing |

---

## Artifact Strategy

### Workflow Artifacts

Artifacts are temporary files stored by GitHub Actions:

**Fast PR Check:**
- `unit-test-results`: Test reports (7 days)
- `lint-results`: Lint reports (7 days)
- `ktlint-report`: KtLint report (7 days)
- `app-debug-apk`: Debug APK (7 days)
- `app-release-apk`: Unsigned release APK (7 days)
- `app-debug-androidTest-apk`: Test APK (7 days)

**Production Readiness:**
- Same as Fast PR Check, plus:
- `firebase-test-results`: Firebase test results (30 days)
- `coverage-report`: JaCoCo coverage reports (30 days)
- All artifacts retained for 30 days

**Release Pipeline:**
- `app-release-apk`: Release APK (90 days)

### GitHub Release Assets

Release artifacts are uploaded as **raw APK files** (not zipped):

**Content Type:** `application/vnd.android.package-archive`

**Production Readiness (draft releases):**
- `app-debug.apk`: Debug APK for testing
- `app-release-unsigned.apk`: Unsigned release APK

**Release Pipeline:**
- `app-release.apk` or `app-release-unsigned.apk`: Final release APK
- Signed if signing secrets are configured
- Unsigned otherwise (requires manual signing)

**Why raw APKs?**
- Direct download and installation
- No extraction needed
- Proper content-type for Android downloads
- Works with `adb install` without extraction

---

## Running Workflows Manually

### Fast PR Check
Cannot be triggered manually - runs automatically on push and PR.

### Production Readiness
1. Go to "Actions" tab in GitHub
2. Select "Production Readiness" workflow
3. Click "Run workflow"
4. Select branch
5. Click "Run workflow" button

### Release Pipeline
1. Go to "Actions" tab in GitHub
2. Select "Release Pipeline" workflow
3. Click "Run workflow"
4. Enter release version (e.g., `v1.0.0`)
5. Click "Run workflow" button

**Or create a git tag:**
```bash
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin v1.0.0
```

---

## Workflow Configuration

### JDK Version
All workflows use **JDK 17** (Temurin distribution) to match local development.

### Gradle Configuration
All workflows create `gradle.properties` with:
```properties
GOOGLE_CLOUD_SERVICE_ACCOUNT_JSON=<from secrets>
OSM_CLIENT_ID=<from secrets>
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.enableJetifier=true
kotlin.code.style=official
org.gradle.caching=true
org.gradle.parallel=true
org.gradle.daemon=false
org.gradle.configureondemand=true
```

### Timeout Settings
- Short jobs (cache, summary): 5-20 minutes
- Medium jobs (unit tests, lint): 30 minutes
- Long jobs (build): 45-60 minutes
- Firebase Test Lab: 120 minutes

### Concurrency
- **Fast PR Check**: Cancel in-progress runs for same branch
- **Production Readiness**: Cancel in-progress runs for same branch
- **Release Pipeline**: No cancellation (complete all releases)

---

## Troubleshooting

### Build Failures

**"No git tags found":**
- Ensure repository has at least one git tag
- Use `fetch-depth: 0` to fetch full history
- Create a tag: `git tag -a v1.0.0 -m "Initial release"`

**Cache restore failures:**
- GitHub may evict old caches
- Workflow will regenerate cache automatically
- First run after cache eviction will be slower

**Out of memory errors:**
- Increase `org.gradle.jvmargs` in gradle.properties
- Current setting: `-Xmx2048m` (2 GB)

### Firebase Test Lab Failures

**Authentication errors:**
- Verify `GOOGLE_CLOUD_SERVICE_ACCOUNT_JSON` secret
- Ensure service account has required permissions
- Check `GCP_PROJECT_ID` is correct

**Test timeout:**
- Increase timeout in workflow (currently 120 minutes)
- Optimize test suite for faster execution

**Device unavailable:**
- Check Firebase Test Lab device availability
- Try different device/API level combination

### Release Pipeline Failures

**Signing errors:**
- Verify all signing secrets are configured
- Ensure keystore is valid
- Check key alias matches keystore

**Tag push not triggering workflow:**
- Verify tag matches pattern `v*.*.*`
- Check workflow file syntax
- Ensure workflows are enabled in repository settings

---

## Best Practices

### For Contributors

1. **Always run tests locally** before pushing
2. **Check PR workflow status** before requesting review
3. **Address lint warnings** - they'll fail CI
4. **Keep commits small** - faster CI feedback
5. **Use draft PRs** for work in progress

### For Maintainers

1. **Monitor nightly builds** - catch issues early
2. **Review CodeQL alerts** - security first
3. **Keep dependencies updated** - use Dependabot
4. **Test Firebase Test Lab** regularly - verify quota
5. **Review coverage reports** - maintain test quality

### For Releases

1. **Always use semantic versioning** - `vMAJOR.MINOR.PATCH`
2. **Test manually before release** - run production-readiness workflow
3. **Update CHANGELOG.md** before tagging
4. **Create annotated tags** - `git tag -a v1.0.0 -m "Release notes"`
5. **Verify release assets** - download and test APKs

---

## Performance Optimization

### Cache Hits
- Expected cache hit rate: >90%
- Cache size: ~500 MB (Gradle dependencies)
- Cache restore time: ~30 seconds
- Cold build (no cache): ~10-15 minutes
- Warm build (with cache): ~3-5 minutes

### Parallel Execution
- Fast PR Check: 6 jobs in parallel
- Production Readiness: 5 jobs in parallel
- Total speedup: ~3-4x vs sequential

### Build Times
- Fast PR Check: ~10-15 minutes (parallel)
- Production Readiness: ~30-45 minutes (full suite)
- Release Pipeline: ~15-20 minutes (release build)

---

## Future Enhancements

Potential improvements to consider:

1. **Matrix builds**: Test multiple Android API levels
2. **UI tests**: Add Espresso UI tests
3. **Screenshot tests**: Visual regression testing
4. **Performance tests**: Monitor APK size and build time
5. **Automated changelog**: Generate from commit messages
6. **Branch protection**: Require PR checks before merge
7. **Auto-merge**: Dependabot auto-merge for minor updates
8. **Slack notifications**: Alert team on failures
9. **Deploy previews**: Test APKs for each PR
10. **A/B testing**: Deploy to beta track for testing

---

## Support

For questions or issues with CI/CD workflows:

1. Check workflow logs in Actions tab
2. Review this documentation
3. Check existing issues for similar problems
4. Create new issue with workflow logs attached
5. Tag relevant maintainers for urgent issues

---

## References

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Gradle Build Cache](https://docs.gradle.org/current/userguide/build_cache.html)
- [Firebase Test Lab](https://firebase.google.com/docs/test-lab)
- [CodeQL](https://codeql.github.com/)
- [Android Gradle Plugin](https://developer.android.com/build)
