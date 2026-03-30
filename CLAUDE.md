# CLAUDE.md — Salesforce Mobile SDK for Android

---

## About This Project

The Salesforce Mobile SDK is a public, open-source SDK that enables developers to build mobile apps that integrate with the Salesforce platform. It is consumed by ISVs, SI partners, and internal Salesforce teams.

**Key constraint**: This is a **public SDK**. Every change is visible to external developers. Backward compatibility, deprecation cycles, and semver discipline are non-negotiable.

## Android Library Architecture

All libraries are defined in `settings.gradle.kts` and live under `libs/` with the following dependency hierarchy (arrows = "depends on"):

```
MobileSync
  └── SmartStore
       └── SalesforceSDK
            └── SalesforceAnalytics

SalesforceHybrid
  └── SalesforceSDK

SalesforceReact
  └── SalesforceSDK
```

### Library Descriptions

| Library | Path | Purpose |
|---------|------|---------|
| **SalesforceAnalytics** | `libs/SalesforceAnalytics/` | Telemetry, instrumentation, and analytics event tracking |
| **SalesforceSDK** | `libs/SalesforceSDK/` | OAuth2 authentication, identity, REST client (`RestClient`), account management, `SalesforceSDKManager`, push notifications, login UI, app lifecycle, pin/passcode screen |
| **SmartStore** | `libs/SmartStore/` | Encrypted on-device SQLite storage (backed by SQLCipher). Soup-based data model with indexing and Smart SQL query support |
| **MobileSync** | `libs/MobileSync/` | Bidirectional data sync between device (SmartStore) and Salesforce cloud. Sync targets, sync managers, layout/metadata sync. `MobileSyncSDKManager` |
| **SalesforceHybrid** | `libs/SalesforceHybrid/` | Support for Cordova-based hybrid applications |
| **SalesforceReact** | `libs/SalesforceReact/` | Support for React Native applications |

### Android Reference Docs
- [Android Javadoc](https://forcedotcom.github.io/SalesforceMobileSDK-Android/index.html)

### Android Build Details
- **Minimum SDK**: 28 (Android 9.0)
- **Compile SDK**: 35 (Android 15.0)
- **Target SDK**: Follows compileSdk
- **Build system**: Gradle 8.14.3 with Kotlin DSL (`.gradle.kts` files)
- **AGP (Android Gradle Plugin)**: 8.12.0
- **Setup**: Run `./install.sh` after cloning to pull submodule dependencies
- **Dependency management**: Maven Central for dependencies, libraries published to Maven Central
- **Encryption**: SmartStore depends on SQLCipher for Android (`net.zetetic:sqlcipher-android:4.10.0`)
- **Language**: Kotlin for all new code. Legacy Java exists in older classes. No new Java files.
- **Kotlin version**: 1.9.24 with API/language version 1.6 for backward compatibility

### Android Build & Test Commands
```bash
# Initial setup (after clone)
./install.sh

# Build all libraries
./gradlew build

# Build a specific library
./gradlew :libs:SalesforceSDK:build
./gradlew :libs:SmartStore:build
./gradlew :libs:MobileSync:build

# Run tests for a specific library (runs on Firebase Test Lab in CI)
./gradlew :libs:SalesforceSDK:connectedAndroidTest
./gradlew :libs:SmartStore:connectedAndroidTest
./gradlew :libs:MobileSync:connectedAndroidTest

# Run lint checks
./gradlew :libs:SalesforceSDK:lint
./gradlew :libs:SmartStore:lint
./gradlew :libs:MobileSync:lint

# Build test APKs
./gradlew :libs:SalesforceSDK:assembleAndroidTest

# Run all checks (lint + test)
./gradlew check
```

---

## Code Standards

### General Rules (Both Platforms)
- **Public API changes require a deprecation cycle**. Deprecate in release N, remove no earlier than release N+2 (next major). Always use `@Deprecated` (Kotlin/Java) with a message explaining the replacement and `replaceWith` where possible.
- **No hardcoded secrets, tokens, or PII in source**. Not even in test fixtures — use test-scoped configuration.
- **Never log PII, refresh tokens, or full request/response bodies**. Use the SDK's own telemetry APIs.
- **Compiler warnings are bugs**. Fix all warnings before submitting a PR. Pay special attention to deprecation warnings from upstream SDK versions.
- **Localization**: New user-facing strings must be added to `sf__strings.xml` (with `sf__` prefix). Flag new strings in the PR description.

### Android-Specific
- **Kotlin for all new code**. No new Java files.
- **Null safety**: Use Kotlin's null safety features. Avoid `!!` (force unwrap) unless absolutely necessary with clear justification.
- **Coroutines preferred** over callbacks. Callback-based methods are being deprecated in favor of suspending functions (ongoing since 13.0).
- **`RestClient`** is the single entry point for REST API calls. Don't create custom `OkHttpClient` instances.
- **Follow AndroidX conventions**: Use `androidx.*` packages, not legacy support libraries.
- **Jetpack Compose**: New UI code should use Compose where appropriate. Legacy XML layouts exist but are being migrated.

### Naming Conventions
- **Classes/Interfaces/Objects**: `PascalCase`
- **Functions/Properties**: `camelCase`
- **Constants**: `UPPER_SNAKE_CASE`
- **Packages**: `lowercase.with.dots`

---

## Testing Standards

### Unit Tests
- **Android**: AndroidX Test framework with JUnit4 and Espresso. Test targets in `libs/test/` directories.
- **Coverage target**: 80% line coverage for new code. Never decrease existing coverage.
- **Naming**: `test_given[Precondition]_when[Action]_then[Expected]` (snake_case for test methods is acceptable)
- **Focus on behavior**: Test public API contracts. Test how consumers will use the SDK, not internal state.
- **Mock boundaries, not internals**: Mock network responses, keystore access, SQLCipher, and system services. Do not mock the class under test.
- **Edge cases are mandatory**: Null/empty inputs, expired tokens, network failures, SQLCipher encryption errors, concurrent access to SmartStore soups.
- **No flaky tests**: Tests must be deterministic. No `Thread.sleep()` / hardcoded delays. Use Espresso idling resources and proper synchronization.
- **Test data cleanup**: Every test must clean up created soups, user accounts, and cached data. Use `@Before`/`@After` rigorously.
- **Test credentials**: Tests requiring authentication need `test_credentials.json` in `shared/test/`.

### What to Test for Each Library

**SalesforceSDK (Core)**:
- OAuth2 flow: token refresh, token revocation, session expiry, multi-user account switching
- REST client: request construction, response parsing, error handling (401→refresh, 403, network errors)
- Identity service: user info retrieval, community user handling
- Push notification registration/deregistration
- App lifecycle: foreground/background transitions, biometric unlock, passcode lock
- Login UI: host picker, custom tabs, QR code login, welcome domain login

**SmartStore**:
- Soup CRUD: create, read, update, upsert, delete
- Indexing: index creation, index migration, query performance
- Smart SQL: query construction, aggregation, joins across soups
- Encryption: database open/close with correct key, key rotation, SQLCipher compatibility
- Concurrency: simultaneous reads/writes from multiple threads
- Migration: upgrade path from older SmartStore versions

**MobileSync**:
- Sync down: SOQL targets, MRU targets, layout targets, metadata targets
- Sync up: create/update/delete records, batch sync, advanced sync, conflict detection
- Parent-child relationships: sync up with related records
- Clean ghosts: remove server-deleted records from local store

### When Generating Tests
1. Read this file first for project conventions.
2. Identify which library the code belongs to and match existing test patterns in that library's test directory.
3. Follow BDD naming: `test_given[Precondition]_when[Action]_then[Expected]`
4. Use existing test utilities and mocks already in the test targets. Don't create parallel mock infrastructure.
5. Run the full test suite for the affected library after generation. Fix failures before committing.
6. If the code under test requires an External Client App or sandbox org, note this clearly in the test documentation.
7. If test generation reveals tightly coupled or untestable code, flag this in the PR description as a refactoring opportunity — do not restructure production code just to make tests pass.

---

## Code Review Checklist

When reviewing PRs (or acting as an AI reviewer), verify:

- [ ] **Backward compatibility**: Does this change break any existing public API? If an API is being changed, is the old version properly deprecated with a migration path?
- [ ] **Both platforms considered**: If this is a feature/fix that applies to both iOS and Android, is there a corresponding PR for the other platform?
- [ ] **Tests included**: New/changed behavior has corresponding test coverage in the correct test target.
- [ ] **No regressions**: Full test suite for the affected library passes.
- [ ] **Multi-user**: Does this work correctly with multiple logged-in accounts?
- [ ] **Localization**: Are new user-facing strings added to `sf__strings.xml` (with `sf__` prefix)? Are they called out in the PR description?
- [ ] **Security**: No hardcoded credentials, no PII logging, tokens handled securely, no new cleartext network traffic exceptions.
- [ ] **Performance**: No unnecessary main-thread work, no unbounded memory growth, no blocking network calls.
- [ ] **Deprecation warnings**: Does this PR introduce new deprecation warnings? Are existing warnings addressed?
- [ ] **Documentation**: Public APIs have KDoc comments. Breaking changes are called out for release notes.
- [ ] **Sample apps**: If changing public API, are sample apps (RestExplorer, MobileSyncExplorerHybrid, etc.) updated?

---

## Agent Behavior Guidelines

These rules apply when Claude Code operates as an agent in these repos:

### Do
- Always run the test suite for the affected library before creating a commit. Before running the suite, check that `test_credentials.json` exists in `shared/test`.
- When fixing a bug, write a failing test first, then fix the code.
- Check both iOS and Android repos when investigating a feature — the behavior should be consistent across platforms.
- Reference the public developer documentation at https://developer.salesforce.com/docs/platform/mobile-sdk/guide when understanding expected behavior.
- Flag any public API surface changes for human review — these have downstream impact on thousands of apps.
- Use `--allowedTools` restrictions in CI — limit to Read, Write, Grep, Glob, and the test runner.

### Don't
- Don't merge without human approval. This is a public SDK — every merge is a commitment to external developers.
- Don't modify `build.gradle.kts`, `settings.gradle.kts`, or CI configuration files (`.github/workflows/`) without explicit human request.
- Don't add new third-party dependencies without flagging for human review and legal/license check.
- Don't suppress lint warnings, deprecation warnings, or test failures.
- Don't modify `install.sh` or `install.vbs` setup scripts.
- Don't remove deprecated APIs — they follow a release-cycle deprecation process. Only remove in major versions after the deprecation notice period.
- Don't bump minimum SDK version without team discussion.

### Escalation — Stop and Flag for Human Review
- Any change to the OAuth2 flow, token storage, or credential handling.
- Any change to SQLCipher integration, encryption keys, or keystore access.
- Any new public API or modification to an existing public API signature.
- Any change to the login UI flow or account switching behavior.
- Build system changes (Gradle, AGP, Kotlin versions).
- Dependency version bumps (especially SQLCipher, Cordova, OkHttp).
- Changes affecting Android permissions or security configurations.
- Any change that touches `sf__strings.xml` (localization).
- Removal of any previously deprecated API.

---

## Key Domain Concepts

Understanding these concepts is essential for working in this codebase:

- **External Client App or Connected App (legacy)**: A Salesforce configuration that defines OAuth2 client credentials (consumer key, callback URI, scopes). Every Mobile SDK app requires one. External Client Apps are the preferred model.
- **Soup**: SmartStore's unit of storage — analogous to a database table. Has a name, index specs, and entries (JSON blobs).
- **Smart SQL**: SmartStore's SQL dialect for querying across soups. Uses `{soupName:fieldPath}` syntax.
- **Sync Target**: MobileSync's abstraction for defining what data to sync and how. Includes SOQL down targets, SOSL down targets, MRU targets, layout targets, metadata targets, and various up targets (standard, batch, advanced).
- **User Agent**: The SDK constructs a specific user agent string that identifies the SDK version, app type, and platform. Don't override this.
- **SalesforceSDKManager**: The singleton entry point for SDK configuration and lifecycle. Manages OAuth configuration settings, auth scopes, login behavior, and user account events.
- **Hybrid Authentication**: Uses session IDs from login/refresh endpoints (instead of frontdoor URLs) for loading app content in WebViews.
- **UI Bridge API**: Used to construct frontdoor URLs for opening Salesforce UIs in WebViews without re-authentication.
- **Advanced Authentication**: Custom Tabs login (Chrome Custom Tabs), bypassing standard WebView for orgs requiring it.
- **QR Code Login**: Allows login by scanning a QR code generated from a Salesforce setup page. Validates consumer key match.
- **WebSockets**: Client-side bidirectional TCP connections added in 13.1, working with SDK auth.

---

## Release Process Awareness

When working on code, be aware of these release patterns:

- **Major releases** (e.g., 13.0): May remove previously deprecated APIs. Include breaking changes documented in migration guides and "APIs Removed" pages.
- **Minor releases** (e.g., 13.1): New features and deprecations. Should not remove APIs. Document new deprecations clearly.
- **Patch releases** (e.g., 13.1.1): Bug fixes only. No API changes, no new features.
- **Deprecation notices**: Always check compiler warnings page ([Android](https://developer.salesforce.com/docs/platform/mobile-sdk/guide/android-current-deprecations.html)).

---

## Related Documentation

- **Mobile SDK Development Guide**: https://developer.salesforce.com/docs/platform/mobile-sdk/guide
- **Android Javadoc**: https://forcedotcom.github.io/SalesforceMobileSDK-Android/index.html
- **iOS Library References**: See iOS repo CLAUDE.md for iOS-specific links
- **GitHub Repos**: https://github.com/forcedotcom (search `SalesforceMobileSDK`)
- **Migration Guides**: Published per major release in the developer guide
