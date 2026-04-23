# android.newDsl Migration - BLOCKER FOUND

**Date:** 2026-04-23
**Status:** ❌ BLOCKED - Cannot proceed with current configuration
**Migration Guide:** See MIGRATION_android_newDsl.md

---

## Executive Summary

The android.newDsl=true migration was attempted but **cannot be completed** due to a fundamental incompatibility between:
- Android Gradle Plugin 9.2.0's new DSL
- Kotlin Gradle Plugin 2.2.10
- Project's current build configuration

**The migration is BLOCKED** until the Kotlin plugin compatibility issue is resolved.

---

## Blocker Details

### Error 1: Kotlin Plugin Cast Exception

**Configuration:** `newDsl=true` + `builtInKotlin=false` + explicit `kotlin-android` plugin

**Error:**
```
An exception occurred applying plugin request [id: 'kotlin-android']
> Failed to apply plugin 'kotlin-android'.
   > class com.android.build.gradle.internal.dsl.LibraryExtensionImpl$AgpDecorated_Decorated
     cannot be cast to class com.android.build.gradle.BaseExtension
```

**Root Cause:**
When `android.newDsl=true`, AGP uses the new DSL type `com.android.build.api.dsl.LibraryExtension`.
However, Kotlin Gradle Plugin 2.2.10 with `builtInKotlin=false` still expects the old `BaseExtension` type and attempts an invalid cast.

**Impact:** Build fails at plugin application phase for all modules.

### Error 2: Kotlin Compilation Failure

**Configuration:** `newDsl=true` + `builtInKotlin=true` (default) + NO explicit `kotlin-android` plugin

**Error:**
```
> Task :libs:SalesforceAnalytics:compileDebugKotlin NO-SOURCE

> Task :libs:SalesforceAnalytics:compileDebugJavaWithJavac FAILED
error: cannot find symbol
    private static SalesforceLogReceiverFactory logReceiverFactory;
                   ^
  symbol:   class SalesforceLogReceiverFactory
  location: class SalesforceLogger
```

**Root Cause:**
AGP's built-in Kotlin support (`builtInKotlin=true`) is not properly configured or compatible with this project.
Kotlin compilation doesn't run (shows "NO-SOURCE"), causing Java compilation to fail when it tries to reference Kotlin classes.

**Impact:** Build succeeds through configuration phase but fails during compilation.

---

## Attempted Solutions

### Attempt 1: Remove `android.newDsl=false` only
- **Result:** Error 1 (Cast Exception)
- **Files changed:** `gradle.properties`, all `build.gradle.kts` (removed TODO comments)

### Attempt 2: Remove `android.newDsl=false` + Remove `android.builtInKotlin=false` + Remove explicit `kotlin-android` plugin
- **Result:** Error 2 (Kotlin Compilation Failure)
- **Files changed:** `gradle.properties`, all `build.gradle.kts`

### Attempt 3: Remove `android.newDsl=false` + Keep `android.builtInKotlin=false` + Keep explicit `kotlin-android` plugin
- **Result:** Error 1 (Cast Exception) - same as Attempt 1

---

## Analysis

The two configuration options both fail:

| Configuration | newDsl | builtInKotlin | kotlin-android plugin | Result |
|---------------|--------|---------------|----------------------|--------|
| **Original** | false | false | Yes (explicit) | ✅ Works |
| **Option A** | true (default) | false | Yes (explicit) | ❌ Cast Exception |
| **Option B** | true (default) | true (default) | No (removed) | ❌ Kotlin compilation fails |

**Conclusion:** The migration cannot proceed with:
- AGP 9.2.0
- Kotlin Gradle Plugin 2.2.10
- Current project build configuration

---

## Root Cause Investigation

### Why Option A Fails

The Kotlin Gradle Plugin 2.2.10, when applied explicitly with `builtInKotlin=false`, has internal code that:
1. Accesses the Android extension via `project.extensions.getByType(BaseExtension::class.java)`
2. Attempts to cast it to `BaseExtension` type

However, when `newDsl=true`:
- AGP creates `LibraryExtensionImpl$AgpDecorated_Decorated` instead
- This type does NOT inherit from `BaseExtension`
- The cast fails with `ClassCastException`

This is a known compatibility issue between:
- Kotlin Gradle Plugin < 2.3.x (speculation)
- AGP 9.x with `newDsl=true`

### Why Option B Fails

AGP's built-in Kotlin support is designed to work seamlessly with the new DSL, but:
1. The project has a complex Kotlin configuration (serialization, parcelize, compose plugins)
2. AGP's built-in support may not fully handle all Kotlin compiler plugins
3. The compilation phase shows "NO-SOURCE" suggesting Kotlin sources aren't being detected

This could be due to:
- Source set configuration issues
- Kotlin plugin detection issues
- Incomplete AGP built-in Kotlin implementation

---

## Required Actions Before Migration Can Proceed

One of the following must be resolved:

### Option 1: Update Kotlin Gradle Plugin (RECOMMENDED)

**Action:** Investigate if a newer Kotlin Gradle Plugin version supports AGP 9.2.0's new DSL with `builtInKotlin=false`

**Steps:**
1. Check Kotlin Gradle Plugin release notes for AGP 9.x compatibility
2. Test with Kotlin Gradle Plugin 2.3.x or later (if available)
3. Verify all Kotlin compiler plugins remain compatible

**Risk:** May introduce Kotlin language version incompatibilities or break existing code

### Option 2: Fully Configure AGP Built-in Kotlin Support

**Action:** Properly configure AGP's built-in Kotlin support to work with this project

**Steps:**
1. Research AGP built-in Kotlin requirements and limitations
2. Adjust source set configurations
3. Migrate Kotlin compiler plugin configurations
4. Test Kotlin compilation with all modules

**Risk:** May require significant build script refactoring

### Option 3: Wait for AGP 10.0

**Action:** Defer migration until upgrading to AGP 10.0, which removes old DSL entirely

**Steps:**
1. Continue using `android.newDsl=false` until AGP 10.0 upgrade
2. Bundle both android.newDsl and AGP 10.0 migrations together
3. At that point, the old DSL won't be available, forcing resolution

**Risk:** Forced migration under time pressure when AGP 10.0 is released

---

## Recommendations

1. **Immediate:** Keep `android.newDsl=false` in place (revert attempted migration)
2. **Short-term:** Investigate Kotlin Gradle Plugin updates or AGP built-in Kotlin configuration
3. **Long-term:** Plan for combined AGP 10.0 + newDsl migration with proper Kotlin plugin compatibility

---

## Impact Assessment

**Warnings Remaining:**
- 12× `android {}` block deprecation warnings
- 4× variant API warnings
- 1× `android.newDsl=false` deprecation warning
- **Total: ~17 deprecation warnings per build** ⚠️

**Timeline Risk:**
- AGP 10.0 will **remove** old DSL support entirely
- Migration will become **mandatory**, not optional
- Current blocker must be resolved before AGP 10.0 upgrade

**Recommended Deadline:**
Resolve this blocker **before** attempting AGP 10.0 upgrade (likely 6-12 months from now).

---

## Related Files

- `gradle.properties` - Contains `android.newDsl=false` setting
- `build.gradle.kts` - Kotlin Gradle Plugin version (2.2.10)
- `buildSrc/build.gradle.kts` - BuildSrc Kotlin plugin version
- All `libs/*/build.gradle.kts` - Library module builds
- All `*SampleApps/*/build.gradle.kts` - Sample app builds

---

## Migration Attempt Log

**2026-04-23:**
- Removed `android.newDsl=false` from `gradle.properties`
- Removed TODO comments from all `build.gradle.kts` files
- Tested with `builtInKotlin=false` + explicit `kotlin-android`: ❌ Cast Exception
- Tested without `builtInKotlin` setting + no explicit `kotlin-android`: ❌ Compilation Failure
- Reverted all changes
- Documented blocker findings in this file

---

## Next Steps

1. ✅ Document blocker (this file)
2. ⏳ Research Kotlin Gradle Plugin compatibility with AGP 9.x new DSL
3. ⏳ Explore AGP built-in Kotlin configuration options
4. ⏳ Test potential solutions in isolated branch
5. ⏳ Update MIGRATION_android_newDsl.md with resolution once found

---

## References

- [AGP 9.0 New DSL Migration](https://developer.android.com/build/r/new-dsl)
- [Kotlin Gradle Plugin Documentation](https://kotlinlang.org/docs/gradle.html)
- [AGP Built-in Kotlin Support](https://developer.android.com/build/releases/gradle-plugin#kotlin-support)
- Original Migration Guide: `MIGRATION_android_newDsl.md`
