# Migration Guide: Enable android.newDsl=true

**Status:** Ready for implementation
**Priority:** High (required before AGP 10.0)
**Estimated Effort:** 2-4 hours
**Risk Level:** Low-Medium
**Created:** 2026-04-23

---

## Executive Summary

This migration enables the new Android Gradle Plugin DSL by changing `android.newDsl=false` to `true` (or removing the property). This single change will eliminate ~17 deprecation warnings per build and is **mandatory** before upgrading to AGP 10.0.

**Key Benefits:**
- ✅ Eliminates 12 `android {}` block deprecation warnings
- ✅ Eliminates 4 variant API deprecation warnings
- ✅ Eliminates newDsl property deprecation warning
- ✅ AGP 10.0 compatible
- ✅ Better build performance
- ✅ Full Gradle configuration caching support

**Expected Code Changes:** None (syntax remains identical)

---

## Background

### What is android.newDsl?

The `android.newDsl` flag controls which AGP DSL API is used:

- **Old DSL (false):** Uses legacy `LibraryExtension`, `BaseAppModuleExtension`, variant APIs
- **New DSL (true):** Uses modern `com.android.build.api.dsl.LibraryExtension`, `ApplicationExtension`, `AndroidComponentsExtension`

The syntax in build.gradle.kts files **remains identical** - only the underlying implementation changes.

### Current State

**gradle.properties line 17:**
```properties
android.newDsl=false
```

**Warnings per build:**
```
w: 'fun Project.android(configure: Action<LibraryExtension>): Unit' is deprecated. (×12)
WARNING: API 'libraryVariants' is obsolete... (×3)
WARNING: API 'applicationVariants' is obsolete... (×1)
WARNING: The option setting 'android.newDsl=false' is deprecated. (×1)
---
Total: ~17 deprecation warnings
```

### Why This Was Deferred

Analysis on 2026-04-23 showed this is likely a **legacy configuration** that may no longer be necessary:
- Publishing plugin uses new DSL patterns
- No direct variant API usage found in codebase
- JaCoCo configured with direct paths
- Modern build patterns throughout

However, thorough testing is required to validate this hypothesis.

---

## Migration Steps

### Prerequisites

1. ✅ Ensure you're on the feature branch or create a new one:
   ```bash
   git checkout -b migration/enable-android-newdsl
   ```

2. ✅ Ensure build is clean before starting:
   ```bash
   export JAVA_HOME=/Users/johnson.eric/Salesforce/Apps/openjdk_17.0.16.0.101_17.61.12_aarch64
   ./gradlew clean
   ```

3. ✅ Commit any outstanding changes:
   ```bash
   git status
   git add .
   git commit -m "Checkpoint before android.newDsl migration"
   ```

### Phase 1: Make the Change (5 minutes)

**File:** `gradle.properties`

**Option A - Remove the property (RECOMMENDED):**
```diff
  android.builtInKotlin=false
- android.newDsl=false
```

**Option B - Change to true:**
```diff
- android.newDsl=false
+ android.newDsl=true
```

**Recommendation:** Remove the line entirely, as `true` is the default in AGP 9.0+.

### Phase 2: Initial Build Test (15-30 minutes)

**Goal:** Quick validation that the change doesn't break basic builds

```bash
# Set Java home
export JAVA_HOME=/Users/johnson.eric/Salesforce/Apps/openjdk_17.0.16.0.101_17.61.12_aarch64

# Clean build
./gradlew clean

# Test a single library first
./gradlew :libs:SalesforceSDK:build --info 2>&1 | tee build-salesforcesdk.log

# Check for errors
echo "Build exit code: $?"

# Verify no deprecation warnings
grep -i "android.newDsl\|Project.android.*deprecated\|libraryVariants.*obsolete" build-salesforcesdk.log
# Expected: No matches or significantly fewer warnings
```

**Decision Point:**
- ✅ **Build succeeds, no warnings:** Proceed to Phase 3
- ⚠️ **Build succeeds, new warnings:** Investigate warnings, may still be OK
- ❌ **Build fails:** Proceed to "Troubleshooting" section

### Phase 3: Comprehensive Build Test (30-60 minutes)

**Goal:** Verify all modules build successfully

```bash
# Build all libraries in sequence
./gradlew :libs:SalesforceAnalytics:build
./gradlew :libs:SalesforceSDK:build
./gradlew :libs:SmartStore:build
./gradlew :libs:MobileSync:build
./gradlew :libs:SalesforceHybrid:build
./gradlew :libs:SalesforceReact:build

# Build sample apps
./gradlew :native:NativeSampleApps:RestExplorer:assembleDebug
./gradlew :native:NativeSampleApps:AuthFlowTester:assembleDebug
./gradlew :native:NativeSampleApps:ConfiguredApp:assembleDebug
./gradlew :native:NativeSampleApps:AppConfigurator:assembleDebug
./gradlew :hybrid:HybridSampleApps:AccountEditor:assembleDebug
./gradlew :hybrid:HybridSampleApps:MobileSyncExplorerHybrid:assembleDebug

# Or build everything at once (faster but less granular)
./gradlew build --parallel
```

**Success Criteria:**
- ✅ All modules build without errors
- ✅ No `android {}` deprecation warnings
- ✅ No variant API warnings
- ✅ No new errors introduced

**Log and Document:**
```bash
# Capture final build output
./gradlew clean build 2>&1 | tee build-full-newdsl.log

# Count warnings (should be dramatically reduced)
grep -c "warning\|deprecated" build-full-newdsl.log
```

### Phase 4: Publishing Verification (30 minutes)

**Goal:** Ensure Maven publishing still works correctly

```bash
# Publish libraries to local Maven repository
./gradlew :libs:SalesforceAnalytics:publishToMavenLocal
./gradlew :libs:SalesforceSDK:publishToMavenLocal
./gradlew :libs:SmartStore:publishToMavenLocal
./gradlew :libs:MobileSync:publishToMavenLocal
./gradlew :libs:SalesforceHybrid:publishToMavenLocal
./gradlew :libs:SalesforceReact:publishToMavenLocal

# Verify artifacts were created
ls -la ~/.m2/repository/com/salesforce/mobilesdk/SalesforceSDK/14.0.0/
ls -la ~/.m2/repository/com/salesforce/mobilesdk/SmartStore/14.0.0/
ls -la ~/.m2/repository/com/salesforce/mobilesdk/MobileSync/14.0.0/

# Check for expected files:
# - <artifact>-14.0.0.aar
# - <artifact>-14.0.0.pom
# - <artifact>-14.0.0-sources.jar
```

**Success Criteria:**
- ✅ All libraries publish without errors
- ✅ AAR, POM, and sources JAR files are created
- ✅ File sizes are reasonable (not empty or corrupted)

### Phase 5: Test Execution (1-2 hours, if applicable)

**Goal:** Run test suite to catch runtime issues

```bash
# Unit tests (if configured)
./gradlew test

# Android instrumented tests (requires device/emulator)
# Note: May require test credentials in shared/test/test_credentials.json
./gradlew :libs:SalesforceSDK:connectedAndroidTest
./gradlew :libs:SmartStore:connectedAndroidTest
./gradlew :libs:MobileSync:connectedAndroidTest

# Check test results
find . -name "TEST-*.xml" -type f
```

**Success Criteria:**
- ✅ All tests pass (or same failures as before migration)
- ✅ No new test failures introduced

**Note:** If tests are not routinely run locally, this phase can be deferred to CI/CD validation.

### Phase 6: Final Validation (15 minutes)

**Checklist:**
- [ ] gradle.properties updated (newDsl removed or set to true)
- [ ] All library modules build successfully
- [ ] All sample app modules build successfully
- [ ] Maven publishing works
- [ ] Tests pass (or match baseline)
- [ ] No new deprecation warnings
- [ ] Build logs captured for reference

**Warnings Check:**
```bash
# Compare warning counts before and after
grep -c "deprecated\|obsolete" build-baseline.log  # Before migration
grep -c "deprecated\|obsolete" build-full-newdsl.log  # After migration

# Expected: Significant reduction (~17 fewer warnings)
```

### Phase 7: Commit and Document (15 minutes)

```bash
# Review changes
git diff gradle.properties

# Stage change
git add gradle.properties

# Commit with detailed message
git commit -m "$(cat <<'EOF'
Enable android.newDsl=true to resolve AGP deprecations

- Remove android.newDsl=false from gradle.properties
- This enables the new Android Gradle Plugin DSL API
- Resolves ~17 deprecation warnings per build

Warnings eliminated:
- 12× android {} block deprecation warnings
- 4× variant API warnings (libraryVariants, testVariants, etc.)
- 1× newDsl property deprecation warning

The new DSL uses:
- com.android.build.api.dsl.LibraryExtension (instead of LibraryExtension)
- com.android.build.api.dsl.ApplicationExtension (instead of BaseAppModuleExtension)
- AndroidComponentsExtension (instead of variant APIs)

No build script changes were required - syntax remains identical.

AGP 10.0 will remove support for the old DSL, making this change
mandatory before that upgrade.

Verified:
- All library modules build successfully
- All sample apps build successfully
- Maven publishing works correctly
- Test suite passes (or matches baseline)

Closes: [ticket number if applicable]
EOF
)"

# Create summary
cat > MIGRATION_android_newDsl_RESULTS.md <<'EOF'
# android.newDsl Migration Results

**Date:** $(date +%Y-%m-%d)
**Status:** SUCCESS

## Changes Made
- Removed `android.newDsl=false` from gradle.properties

## Build Results
- ✅ All 6 library modules build successfully
- ✅ All 6 sample app modules build successfully
- ✅ Maven publishing verified
- ✅ No build script changes required

## Warnings Eliminated
- Before: ~17 deprecation warnings per build
- After: [insert actual count]
- Improvement: [calculate reduction]

## Testing Results
- [Document test execution results]

## Issues Encountered
- [List any issues and resolutions, or "None"]

## Rollback Plan
If issues arise post-merge, rollback by reverting this commit:
```
git revert [commit-hash]
```
This will restore `android.newDsl=false`.
EOF

git add MIGRATION_android_newDsl_RESULTS.md
git commit -m "Add android.newDsl migration results documentation"
```

---

## Troubleshooting

### Issue 1: Build Fails with Variant API Errors

**Symptoms:**
```
Cannot access libraryVariants
Cannot access testVariants
```

**Diagnosis:**
Some code is directly using the old variant APIs.

**Solution:**
Find and update the code using new APIs:

```bash
# Find usage
./gradlew build -Pandroid.debug.obsoleteApi=true 2>&1 | grep -A10 "libraryVariants"

# Common locations to check:
# - buildSrc/src/main/kotlin/*.gradle.kts
# - build.gradle.kts files
# - Custom plugin code
```

**Migrate to new API:**
```kotlin
// Old API (deprecated)
android {
    libraryVariants.all {
        // Configure variant
    }
}

// New API
import com.android.build.api.variant.LibraryAndroidComponentsExtension

androidComponents {
    onVariants { variant ->
        // Configure variant
    }
}
```

**Resources:**
- https://developer.android.com/build/extend-agp
- https://developer.android.com/reference/tools/gradle-api/8.0/com/android/build/api/variant/AndroidComponentsExtension

### Issue 2: Publishing Fails

**Symptoms:**
```
Could not resolve all dependencies
Publication configuration error
```

**Diagnosis:**
Publishing plugin may need adjustment for new DSL.

**Solution:**
Check `buildSrc/src/main/kotlin/publish-module.gradle.kts`:

```kotlin
// Should already use new API
pluginManager.withPlugin("com.android.library") {
    extensions.configure<LibraryExtension>("android") {  // This line
        publishing {
            singleVariant("release") {
                withSourcesJar()
            }
        }
    }
}
```

Update import if needed:
```kotlin
import com.android.build.api.dsl.LibraryExtension  // New DSL
```

### Issue 3: Tests Fail or Don't Run

**Symptoms:**
```
No tests found
Test configuration error
```

**Diagnosis:**
Test variant configuration may need update.

**Solution:**
Review test configuration in build files. Usually no changes needed, but verify:

```kotlin
android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Should work with both DSLs
    }
}
```

### Issue 4: Custom Gradle Tasks Break

**Symptoms:**
```
Task configuration error
Cannot resolve variant
```

**Diagnosis:**
Custom tasks accessing variants need migration.

**Solution:**
Update custom task code to use `AndroidComponentsExtension`:

```kotlin
// Old
tasks.register("customTask") {
    android.libraryVariants.all { variant ->
        // ...
    }
}

// New
androidComponents {
    onVariants { variant ->
        tasks.register("customTask${variant.name.capitalize()}") {
            // ...
        }
    }
}
```

---

## Rollback Procedure

If critical issues arise that cannot be quickly resolved:

```bash
# Option 1: Revert the commit
git revert [commit-hash]
git push

# Option 2: Manually restore property
echo "android.newDsl=false" >> gradle.properties
git add gradle.properties
git commit -m "Rollback: Restore android.newDsl=false due to [issue description]"

# Option 3: Revert branch changes
git checkout main -- gradle.properties
git add gradle.properties
git commit -m "Rollback: Restore android.newDsl=false"
```

**After Rollback:**
1. Document the specific failure that caused rollback
2. Investigate root cause
3. Plan proper fix
4. Retry migration when ready

---

## Success Metrics

**Before Migration:**
- [ ] Baseline build captured
- [ ] ~17 deprecation warnings per build documented
- [ ] Baseline test results captured

**After Migration:**
- [ ] Zero android {} deprecation warnings
- [ ] Zero variant API warnings
- [ ] Zero newDsl property warnings
- [ ] All builds succeed
- [ ] All tests pass (or match baseline)
- [ ] Publishing works
- [ ] Build performance same or better

---

## Related Issues

This migration resolves the following deprecations:

1. **android {} block warnings** (12 files)
   - `'fun Project.android(configure: Action<LibraryExtension>): Unit' is deprecated`
   - Affects all library modules and sample apps

2. **Variant API warnings** (4 warnings)
   - `API 'libraryVariants' is obsolete`
   - `API 'testVariants' is obsolete`
   - `API 'unitTestVariants' is obsolete`
   - `API 'applicationVariants' is obsolete`

3. **newDsl property warning** (1 warning)
   - `The option setting 'android.newDsl=false' is deprecated`

**Total resolved:** ~17 warnings per build

---

## References

- [Android Gradle Plugin API Reference](https://developer.android.com/reference/tools/gradle-api)
- [Extending AGP](https://developer.android.com/build/extend-agp)
- [AGP Release Notes](https://developer.android.com/build/releases/gradle-plugin)
- [AndroidComponentsExtension](https://developer.android.com/reference/tools/gradle-api/8.0/com/android/build/api/variant/AndroidComponentsExtension)
- [Variant API Migration Guide](https://developer.android.com/studio/build/extend-agp)

---

## Post-Migration Tasks

After successful migration:

1. [ ] Update team documentation
2. [ ] Notify team of build changes
3. [ ] Update CI/CD if necessary
4. [ ] Archive this migration document for reference
5. [ ] Close related tickets
6. [ ] Plan for any remaining AGP 9.x deprecations

---

## Notes

- **Date Created:** 2026-04-23
- **AGP Version:** 9.2.0
- **Gradle Version:** 9.4.1
- **Kotlin Version:** 2.2.10
- **Confidence Level:** 70% that no code changes will be needed
- **Recommended Timeline:** Next maintenance window or sprint

---

## Approval

Before executing this migration:

- [ ] Technical lead review
- [ ] Schedule allocated
- [ ] Backup/rollback plan understood
- [ ] Team notified

**Approved by:** _______________
**Date:** _______________
