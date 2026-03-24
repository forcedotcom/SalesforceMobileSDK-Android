# Update Min SDK Skill

This skill automates the process of updating the minimum Android SDK version across the entire SalesforceMobileSDK-Android project.

## When to Use
Use this skill when you need to:
- Increase the minimum SDK version for the project
- Clean up code that was conditional on older API levels
- Update CI/CD workflows to test new API ranges

## Parameters
- `NEW_MIN_SDK`: The new minimum SDK version (e.g., 30, 31, 32)
- `OLD_MIN_SDK`: The current minimum SDK version (default: 28)

## Process

### 1. Update Build Configuration Files

Update `minSdk` in all `build.gradle.kts` files:

**Libraries:**
- `libs/SalesforceSDK/build.gradle.kts`
- `libs/SalesforceAnalytics/build.gradle.kts`
- `libs/SmartStore/build.gradle.kts`
- `libs/MobileSync/build.gradle.kts`
- `libs/SalesforceHybrid/build.gradle.kts`
- `libs/SalesforceReact/build.gradle.kts`

**Native Sample Apps:**
- `native/NativeSampleApps/RestExplorer/build.gradle.kts`
- `native/NativeSampleApps/AppConfigurator/build.gradle.kts`
- `native/NativeSampleApps/ConfiguredApp/build.gradle.kts`
- `native/NativeSampleApps/AuthFlowTester/build.gradle.kts`

**Hybrid Sample Apps:**
- `hybrid/HybridSampleApps/AccountEditor/build.gradle.kts`
- `hybrid/HybridSampleApps/MobileSyncExplorerHybrid/build.gradle.kts`

**Pattern to update:** In `defaultConfig` block, change:
```kotlin
minSdk = OLD_MIN_SDK
```
to:
```kotlin
minSdk = NEW_MIN_SDK
```

### 2. Update Gradle Properties

Update `gradle.properties`:
- Change `cdvMinSdkVersion=OLD_MIN_SDK` to `cdvMinSdkVersion=NEW_MIN_SDK`

### 3. Update GitHub Actions Workflows

Update API level ranges in `.github/workflows/reusable-lib-workflow.yaml`:
- Update `FULL_API_RANGE` environment variable (appears in two places):
  - In the "Run Tests" step (around line 104)
  - In the "Copy Test Results" step (around line 155)
- Change from: `FULL_API_RANGE: "28 29 30 31 32 33 34 35 36"`
- To: `FULL_API_RANGE: "NEW_MIN_SDK ... 36"` (include all versions from NEW_MIN_SDK to latest)

Update API level ranges in `.github/workflows/reusable-ui-workflow.yaml`:
- Update `FULL_API_RANGE` environment variable (appears in three places):
  - Single User Tests step (around line 93)
  - Multi User Tests step (around line 120)
- Change the same range as above

### 4. Clean Up Code - Remove Obsolete API Level Checks

Search for and remove/simplify code that checks for API levels below NEW_MIN_SDK:

**Common patterns to look for:**
```kotlin
if (SDK_INT >= VERSION_CODES.X) {
    // Code for newer API
} else {
    // Code for older API - can be removed if X < NEW_MIN_SDK
}
```

**Key files to check:**
- `libs/SalesforceSDK/src/com/salesforce/androidsdk/app/SalesforceSDKManager.kt`
- `libs/SalesforceSDK/src/com/salesforce/androidsdk/security/KeyStoreWrapper.java`
- `libs/SalesforceAnalytics/src/com/salesforce/androidsdk/analytics/security/Encryptor.java`
- `libs/SalesforceSDK/src/com/salesforce/androidsdk/ui/*.kt` files

**Examples from past updates:**

When moving from 26 to 28:
- Removed `if (SDK_INT >= O)` checks (API 26) from SalesforceSDKManager
- Simplified StrongBox checks in KeyStoreWrapper.java
- Removed legacy encryption provider calls in Encryptor.java

When moving from 24 to 26:
- Simplified system UI visibility settings
- Removed SalesforceSDK_AccessibleNav style fallback

### 5. Update AndroidManifest.xml (if needed)

Check `libs/SalesforceSDK/AndroidManifest.xml` for permissions that are no longer needed:

**Example:** When moving to API 28+, removed USE_FINGERPRINT permission:
```xml
<!-- Remove if NEW_MIN_SDK > 27 -->
<uses-permission android:name="android.permission.USE_FINGERPRINT" />
```

### 6. Search and Update TODO Comments

Search for TODO comments related to minimum API and update or remove them:

**Pattern:** `TODO.*min.*(?:api|API|sdk|SDK|version)`

Common TODOs to address:
- `TODO: Remove when min API > X` - If X <= NEW_MIN_SDK, implement the TODO
- `TODO: Remove check when min API >= X` - If X <= NEW_MIN_SDK, simplify the code
- Update remaining TODOs to reflect the new minimum

**Key locations:**
- `libs/SalesforceSDK/src/com/salesforce/androidsdk/ui/LoginActivity.kt`
- `libs/SalesforceSDK/src/com/salesforce/androidsdk/ui/ScreenLockActivity.kt`
- `libs/SalesforceSDK/src/com/salesforce/androidsdk/app/SalesforceSDKManager.kt`
- `libs/SalesforceSDK/src/com/salesforce/androidsdk/push/PushMessaging.kt`

### 7. Verify Changes

After making all changes:

1. **Build check:** Run `./gradlew assembleDebug` to ensure all modules compile
2. **Grep verification:** Run `grep -r "minSdk = $OLD_MIN_SDK" --include="*.kts"` to ensure no files were missed
3. **API check:** Run `grep -r "SDK_INT.*VERSION_CODES\." --include="*.java" --include="*.kt"` to find remaining API level checks
4. **TODO check:** Run `grep -r "TODO.*min.*API" --include="*.java" --include="*.kt"` to find remaining TODOs

### 8. Create Pull Request

When creating the PR:
- **Title:** "Changing min SDK from {OLD_MIN_SDK} to {NEW_MIN_SDK}"
- **Description:** Include:
  - Reason for the update (Android version market share, deprecated APIs, etc.)
  - List of files changed
  - Code cleanup performed (removed API level checks, updated TODOs)
  - CI/CD updates made

## API Version Reference

Common Android API levels to know:
- API 28 (Android 9.0 Pie)
- API 29 (Android 10)
- API 30 (Android 11)
- API 31 (Android 12)
- API 32 (Android 12L)
- API 33 (Android 13)
- API 34 (Android 14)
- API 35 (Android 15)
- API 36 (Android 16)

## Notes

- Always check the latest Android version market share before deciding on a new minimum
- Consider the impact on existing users and customers
- Test thoroughly on devices at the new minimum API level
- Update documentation if the minimum SDK change affects developer guidance
- Be conservative with minimum SDK increases - only increase when there's clear benefit

## Historical Changes

- **PR #2649 (Jan 2025):** 26 → 28
  - Removed USE_FINGERPRINT permission (deprecated API 28)
  - Simplified encryption provider logic
  - Removed StrongBox API level checks

- **PR #2529 (Mar 2024):** 24 → 26
  - Removed SalesforceSDK_AccessibleNav style fallback
  - Simplified system UI visibility code
  - Updated CircleCI config (now migrated to GitHub Actions)
