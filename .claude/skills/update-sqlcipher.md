# Update SQLCipher Skill

This skill automates the process of updating the SQLCipher library version in the SalesforceMobileSDK-Android project.

## When to Use
Use this skill when you need to:
- Update SQLCipher to a newer version for security patches or new features
- Track changes in SQLCipher's OpenSSL provider version
- Handle API changes in new SQLCipher versions

## Background
SQLCipher is an open-source extension to SQLite that provides transparent 256-bit AES encryption of database files. The SDK uses it in the SmartStore library for secure local data storage.

## Parameters
- `NEW_VERSION`: The new SQLCipher version (e.g., "4.10.0", "4.11.0")
- `OLD_VERSION`: The current SQLCipher version (default: check build.gradle.kts)
- `NEW_OPENSSL_VERSION`: The OpenSSL version bundled with the new SQLCipher (check SQLCipher release notes)

## Process

### 1. Research the New Version

Before starting, check the SQLCipher release notes:
- Visit: https://github.com/sqlcipher/sqlcipher/releases
- Review changes, breaking changes, and new features
- Note the OpenSSL version included (important for tests)
- Check for API changes that might affect the SDK

**Key things to look for:**
- OpenSSL version changes
- API signature changes (e.g., DatabaseErrorHandler methods)
- Deprecated features or behavior changes
- Security fixes or enhancements

### 2. Update Dependency Version

Update `libs/SmartStore/build.gradle.kts`:

**Pattern to update:**
```kotlin
dependencies {
    api(project(":libs:SalesforceSDK"))
    //noinspection GradleDependency -  Needs to line up with supported SQLCipher version.
    api("androidx.sqlite:sqlite:2.2.0")
    api("net.zetetic:sqlcipher-android:OLD_VERSION")  // Change this
    // ... other dependencies
}
```

Change to:
```kotlin
    api("net.zetetic:sqlcipher-android:NEW_VERSION")
```

### 3. Update Version Tests

Update `libs/test/SmartStoreTest/src/com/salesforce/androidsdk/smartstore/store/SmartStoreTest.java`:

**Update the SQLCipher version test:**
```java
@Test
public void testSQLCipherVersion() {
    Assert.assertEquals("Wrong sqlcipher version", "NEW_VERSION community", store.getSQLCipherVersion());
}
```

**Update the cipher provider version test:**
```java
@Test
public void testCipherProviderVersion() {
    Assert.assertEquals("Wrong sqlcipher provider version", "OpenSSL NEW_OPENSSL_VERSION", store.getCipherProviderVersion());
}
```

**Note:** The OpenSSL version format is typically like "OpenSSL 3.0.17 1 Jul 2025" - check the actual runtime value or SQLCipher release notes.

### 4. Check for API Changes

Review if SQLCipher has any API changes that affect these files:

**Key files to check:**
- `libs/SmartStore/src/com/salesforce/androidsdk/smartstore/store/DBOpenHelper.java`
- `libs/SmartStore/src/com/salesforce/androidsdk/smartstore/store/SmartStore.java`
- `libs/SmartStore/src/com/salesforce/androidsdk/smartstore/store/DBHelper.java`

**Historical API changes:**

**SQLCipher 4.7.0:**
- `DatabaseErrorHandler.onCorruption()` signature changed
  - Old: `onCorruption(SQLiteDatabase dbObj)`
  - New: `onCorruption(SQLiteDatabase dbObj, SQLiteException exception)`
- Update in `DBOpenHelper.java`:
  ```java
  static class DBErrorHandler implements DatabaseErrorHandler {
      @Override
      public void onCorruption(SQLiteDatabase dbObj, SQLiteException exception) {
          throw new SmartStore.SmartStoreException("Database is corrupted", exception);
      }
  }
  ```

### 5. Handle Test Behavior Changes

Some SQLCipher versions may change database behavior affecting tests:

**Example from SQLCipher 4.7.0:**
- JSON1 extension behavior changed
- Tests that checked `type != Type.json1` needed to change to just `true`
- Affected query tests with explain plans

**Files to review:**
- `libs/test/SmartStoreTest/src/com/salesforce/androidsdk/smartstore/store/SmartStoreTest.java`
- Look for tests that validate query behavior, indexing, or JSON handling

### 6. Run Tests

Build and run the SmartStore tests:

```bash
# Build SmartStore
./gradlew libs:SmartStore:assembleDebug

# Run SmartStore tests
./gradlew libs:SmartStore:connectedDebugAndroidTest
```

**Key tests to verify:**
- `testSQLCipherVersion()` - Confirms correct version
- `testCipherProviderVersion()` - Confirms correct OpenSSL version
- `testCipherFIPSStatus()` - Should remain false for community edition
- All query and index tests
- Encryption/decryption tests

### 7. Test License Key Support (if using commercial edition)

If testing with commercial or enterprise SQLCipher editions:

```java
// Before using SmartStore
SmartStore.setLicenseKey("your-license-key");
```

This was added in commit `cce8f2b09` to support commercial SQLCipher editions.

### 8. Verify Actual Versions at Runtime

To confirm the actual version numbers for tests, you can:

1. Run a temporary test to log the versions:
```java
Log.d("SQLCipher", "Version: " + store.getSQLCipherVersion());
Log.d("SQLCipher", "Provider: " + store.getCipherProviderVersion());
```

2. Or check SQLCipher release notes for the exact OpenSSL version bundled

### 9. Create Pull Request

When creating the PR:
- **Title:** "Moving to SQLCipher {NEW_VERSION}" or "Update SQLCipher to {NEW_VERSION}"
- **Description:** Include:
  - SQLCipher version being updated to
  - OpenSSL version included
  - Link to SQLCipher release notes
  - Any API changes handled
  - Test results summary
  - Any breaking changes or migration notes

## File Checklist

- [ ] `libs/SmartStore/build.gradle.kts` - Update dependency version
- [ ] `libs/test/SmartStoreTest/src/com/salesforce/androidsdk/smartstore/store/SmartStoreTest.java` - Update version tests
- [ ] `libs/SmartStore/src/com/salesforce/androidsdk/smartstore/store/DBOpenHelper.java` - Check for API changes
- [ ] Run full SmartStore test suite
- [ ] Verify on multiple Android API levels (min SDK to latest)

## Version History

Recent SQLCipher updates in the project:

- **4.10.0** - Current version (PR #2744)
- **4.9.0** - Previous version (PR #2717)
- **4.7.2** - Introduced `onCorruption()` API change (PR #2698)
- **4.6.1** - Stable version (PR #2605)
- **License Key Support** - Added in commit `cce8f2b09` (2025-01-16)

## Key Files Reference

**Build Configuration:**
- `libs/SmartStore/build.gradle.kts` - Gradle dependency

**Source Files:**
- `libs/SmartStore/src/com/salesforce/androidsdk/smartstore/store/SmartStore.java` - Main SmartStore class, license key support
- `libs/SmartStore/src/com/salesforce/androidsdk/smartstore/store/DBOpenHelper.java` - Database helper, API hooks
- `libs/SmartStore/src/com/salesforce/androidsdk/smartstore/store/DBHelper.java` - Database utilities

**Test Files:**
- `libs/test/SmartStoreTest/src/com/salesforce/androidsdk/smartstore/store/SmartStoreTest.java` - Version tests and main test suite

## Notes

- SQLCipher updates are usually straightforward but can have subtle issues
- Always test thoroughly on real devices, not just emulators
- Check SQLCipher's GitHub issues before and after updating
- The community edition (what we use) includes "community" in the version string
- OpenSSL version changes are common and must be updated in tests
- Test with encrypted databases from previous versions to ensure migration works

## Resources

- SQLCipher for Android: https://github.com/sqlcipher/sqlcipher-android
- SQLCipher Releases: https://github.com/sqlcipher/sqlcipher/releases
- SQLCipher Documentation: https://www.zetetic.net/sqlcipher/documentation/
- AndroidX SQLite: https://developer.android.com/jetpack/androidx/releases/sqlite
