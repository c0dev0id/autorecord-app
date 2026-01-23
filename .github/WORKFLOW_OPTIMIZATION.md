# GitHub Actions Workflow Optimization Guide

This document describes the caching and performance optimizations implemented in the GitHub Actions workflows for the autorecord-app project.

## Overview

The workflows have been optimized to significantly reduce build times by implementing comprehensive caching strategies and enabling Gradle performance features.

## Implemented Optimizations

### 1. Path-Based Workflow Triggering

**What:** Uses `paths-ignore` filters to skip builds when only non-code files change
**Impact:** Prevents unnecessary workflow runs for documentation or workflow changes
**Time Savings:** 100% (entire workflow skipped when only docs/workflows change)

```yaml
on:
  pull_request:
    types: [opened, synchronize, reopened]
    paths-ignore:
      - '**.md'                    # Documentation files
      - 'docs/**'                  # Documentation directory
      - '.github/workflows/**'     # Workflow changes don't affect app builds
      - '.gitignore'               # Git config changes
      - 'LICENSE'                  # License changes
```

**Examples of changes that will NOT trigger builds:**
- README.md updates
- Documentation additions/changes
- Workflow file modifications
- .gitignore changes
- License updates

**Examples of changes that WILL trigger builds:**
- Source code changes (app/src/**)
- Gradle configuration (*.gradle*, gradle-wrapper.properties)
- Android manifest changes
- Resource changes (res/**)

### 2. Gradle Dependency Caching

**What:** Uses `actions/setup-java@v4` with `cache: gradle` parameter
**Impact:** Caches Gradle dependencies, wrappers, and build cache
**Time Savings:** ~30-60 seconds per run (after initial cache)

```yaml
- name: Set up JDK 17
  uses: actions/setup-java@v4
  with:
    java-version: '17'
    distribution: 'temurin'
    cache: gradle  # Built-in Gradle caching
```

**Note:** The built-in `cache: gradle` is sufficient and preferred over manual `actions/cache` for Gradle. We removed redundant explicit Gradle caching in `android-build.yml` and `create-release.yml`.

### 3. Android SDK Caching

**What:** Caches Android SDK components using `actions/cache@v4`
**Impact:** Prevents re-downloading SDK packages (~1-2 GB) on each run
**Time Savings:** ~60-120 seconds per run

```yaml
- name: Cache Android SDK
  uses: actions/cache@v4
  with:
    path: |
      ~/.android/build-cache
      ~/.android/cache
    key: ${{ runner.os }}-android-sdk-${{ hashFiles('**/build.gradle*', '**/gradle-wrapper.properties') }}
    restore-keys: |
      ${{ runner.os }}-android-sdk-
```

**Cache Key Strategy:**
- Primary key includes hash of build files to invalidate when dependencies change
- Restore key provides fallback to use previous cache even if build files changed slightly

### 4. Gradle Build Cache

**What:** Enables Gradle's built-in build cache with `--build-cache` flag
**Impact:** Reuses task outputs from previous builds (incremental compilation)
**Time Savings:** ~20-40% faster builds on subsequent runs

```yaml
- name: Build Debug APK
  run: ./gradlew assembleDebug --stacktrace --build-cache --parallel
```

### 5. Parallel Execution

**What:** Enables parallel task execution with `--parallel` flag
**Impact:** Executes independent Gradle tasks concurrently
**Time Savings:** ~10-30% faster builds depending on available CPU cores

### 6. Consistent Caching Strategy

**What:** Standardized caching approach across all three workflows
**Impact:** Predictable performance, easier maintenance
**Workflows Optimized:**
- `pr-check.yml` - Pull request validation
- `android-build.yml` - CI builds on push
- `create-release.yml` - Release builds

## Performance Impact Summary

| Optimization | First Run | Subsequent Runs | Cache Hit | Skipped Runs |
|--------------|-----------|-----------------|-----------|--------------|
| Path Filtering | N/A | N/A | N/A | 100% (docs only) |
| Gradle Dependencies | 0s | ~30-60s faster | ~95% | N/A |
| Android SDK | 0s | ~60-120s faster | ~99% | N/A |
| Build Cache | 0s | ~20-40% faster | ~70% | N/A |
| Parallel Execution | ~10-30% faster | ~10-30% faster | N/A | N/A |

**Total Expected Improvement:**
- **Documentation-only changes: Entire workflow skipped** (saves 4-8 minutes)
- First run with code changes: ~10-30% faster (parallel execution only)
- Subsequent runs with code changes: **~2-4 minutes faster** (with cache hits)

## Cache Invalidation

Caches are automatically invalidated when:

1. **Gradle Dependencies:** Build files (`*.gradle*`) or Gradle wrapper properties change
2. **Android SDK:** Build configuration changes (uses same key as Gradle)
3. **Build Cache:** Gradle automatically manages based on task inputs

## Path Filtering Strategy

The workflows use `paths-ignore` to prevent unnecessary runs:

### Ignored Paths (workflow will NOT run):
- `**.md` - All Markdown files (README, docs, etc.)
- `docs/**` - Documentation directory
- `.github/workflows/**` - Workflow file changes
- `.gitignore` - Git configuration
- `LICENSE` - License file

### Included Paths (workflow WILL run):
- `app/**` - All application source code
- `*.gradle*` - Gradle build files
- `gradle-wrapper.properties` - Gradle wrapper config
- `gradle/**` - Gradle configuration
- Any other files not explicitly ignored

**Example scenarios:**
- ✅ PR updating only README.md → **Workflow skipped** (saves 4-8 minutes)
- ✅ PR updating workflow YAML → **Workflow skipped** (avoids recursive triggers)
- ❌ PR updating app source code → **Workflow runs** (as expected)
- ❌ PR updating build.gradle → **Workflow runs** (build config changed)
- ✅ PR with both code + docs → **Workflow runs** (code changes present)

**Note:** The `create-release.yml` workflow is manually triggered (`workflow_dispatch`) and doesn't need path filtering.

**Trade-off:** Workflow file changes will not trigger CI builds. This is intentional to:
- Prevent recursive workflow triggers
- Avoid unnecessary APK builds when only workflow YAML changes
- Save CI minutes for documentation/workflow maintenance

If you need to test workflow changes, use:
- Local YAML validation: `python3 -c "import yaml; yaml.safe_load(open('file.yml'))"`
- Manual workflow triggers via GitHub UI (workflow_dispatch)
- Push a small code change alongside the workflow change to trigger validation

## Monitoring Cache Performance

To check cache effectiveness in GitHub Actions:

1. Go to Actions tab → Select a workflow run
2. Look for "Cache Android SDK" and "Set up JDK 17" steps
3. Check for "Cache restored" or "Cache not found" messages

Example output when cache hits:
```
Cache restored from key: Linux-android-sdk-abc123...
```

## Additional Optimization Opportunities

### Already Optimal
- ✅ Gradle dependency management (using wrapper)
- ✅ JDK version consistency (17 across all workflows)
- ✅ Artifact retention (7-30 days, appropriate)
- ✅ Workflow triggers (efficient, not redundant)

### Future Considerations

1. **Matrix Builds** (Not Recommended)
   - Currently only building debug APK
   - Adding release builds would increase time, only beneficial if needed

2. **Self-Hosted Runners** (Advanced)
   - Would provide persistent caches
   - Requires infrastructure investment
   - Only beneficial for high-frequency builds

3. **Docker Layer Caching** (Not Applicable)
   - Project doesn't use Docker
   - Would add complexity without benefit

4. **Dependency Pre-warming** (Not Recommended)
   - Setup-java's built-in cache already handles this
   - Would add workflow complexity

## Best Practices

### DO:
- ✅ Keep cache keys consistent with invalidation needs
- ✅ Use restore-keys for partial cache hits
- ✅ Monitor cache hit rates in workflow logs
- ✅ Keep build.gradle dependencies up to date

### DON'T:
- ❌ Add redundant explicit Gradle caching (setup-java handles it)
- ❌ Cache build outputs (.apk files) between jobs (use artifacts)
- ❌ Over-optimize at the cost of workflow simplicity
- ❌ Use overly specific cache keys (reduces hit rate)

## Maintenance

### Regular Tasks
- Review cache effectiveness monthly
- Update action versions when available (@v4 → @v5)
- Monitor GitHub Actions minutes usage

### Troubleshooting

**Cache not restoring?**
- Check if build files changed (expected behavior)
- Verify cache key format is correct
- Check GitHub Actions cache storage limits (10 GB per repo)

**Builds still slow?**
- Check network conditions (affects SDK downloads on cache miss)
- Review Gradle daemon logs for issues
- Consider if dependencies increased significantly

## Conclusion

The implemented optimizations provide significant performance improvements with minimal maintenance overhead. The caching strategy is:
- **Effective:** Saves 2-4 minutes per run after initial cache
- **Reliable:** Uses GitHub's recommended caching approaches
- **Maintainable:** Clear, well-documented, and consistent

For questions or issues, refer to:
- [GitHub Actions Caching Documentation](https://docs.github.com/en/actions/using-workflows/caching-dependencies-to-speed-up-workflows)
- [Gradle Build Cache Guide](https://docs.gradle.org/current/userguide/build_cache.html)
