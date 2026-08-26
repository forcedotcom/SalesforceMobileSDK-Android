# SalesforceHybrid Library (Android)

The `libs/SalesforceHybrid/` library is the Android native bridge for Cordova-based hybrid apps. It provides the Kotlin/Java implementations of all Cordova plugin classes and the WebView infrastructure that hybrid apps run inside.

## Package Breakdown

### `com.salesforce.androidsdk.phonegap.plugin` — Cordova Plugins

Each class extends Cordova's `CordovaPlugin` and exposes native SDK functionality to JavaScript:

| Class | Purpose |
|-------|---------|
| `SalesforceOAuthPlugin` | OAuth login/logout/credential retrieval |
| `SalesforceNetworkPlugin` | REST API requests through the native network stack |
| `SDKInfoPlugin` | SDK version info and app feature registration |
| `SmartStorePlugin` | Encrypted local database (SmartStore) operations |
| `MobileSyncPlugin` | Cloud data sync (sync down/up, conflict resolution) |
| `SFAccountManagerPlugin` | Multi-user account management |
| `ForcePlugin` | Base class for all Salesforce Cordova plugins |
| `JavaScriptPluginVersion` | Plugin version constants |
| `PluginConstants` | Shared string constants for plugin argument keys |
| `TestRunnerPlugin` | Test harness support for hybrid test apps |

### `com.salesforce.androidsdk.phonegap.ui` — WebView Infrastructure

| Class | Purpose |
|-------|---------|
| `SalesforceDroidGapActivity` (Kotlin) | The Cordova activity that hosts the WebView |
| `SalesforceWebView` | WebView configured for Salesforce hybrid apps |
| `SalesforceWebViewClient` | Handles OAuth redirects and URL interception |
| `SalesforceWebViewClientHelper` | Helper logic for the WebView client |
| `SalesforceWebViewEngine` | Cordova engine adapter for the Salesforce WebView |
| `SalesforceWebViewCookieManager` (Kotlin) | Cookie management for authenticated sessions |

### `com.salesforce.androidsdk.phonegap.app` — App Lifecycle

| Class | Purpose |
|-------|---------|
| `HybridApp` | `Application` subclass for hybrid apps |
| `SalesforceHybridSDKManager` | SDK initialization and configuration for hybrid |
| `SalesforceHybridUpgradeManager` | Handles SDK version upgrade migrations |

## Dependencies

From `build.gradle.kts`:

```kotlin
dependencies {
    api(project(":libs:MobileSync"))
    api(libs.cordova.framework)
    api(libs.androidx.appcompat)
    api(libs.androidx.appcompat.resources)
    api(libs.androidx.webkit)
    api(libs.androidx.core.splashscreen)
}
```

The `MobileSync` dependency transitively brings in `SmartStore -> SalesforceSDK -> SalesforceAnalytics`.

## Shared JavaScript Submodule

The [SalesforceMobileSDK-Shared](https://github.com/forcedotcom/SalesforceMobileSDK-Shared) repo is included as a git submodule at `external/shared/`:

```
[submodule "external/shared"]
    path = external/shared
    url = https://github.com/forcedotcom/SalesforceMobileSDK-Shared.git
```

The hybrid sample apps pull their JavaScript code from this submodule. After changes to Shared, update with:

```bash
git submodule update --remote external/shared
```

## Sample Apps

`hybrid/HybridSampleApps/` contains two sample apps:

- **AccountEditor** — Basic hybrid CRUD operations
- **MobileSyncExplorerHybrid** — Offline sync with SmartStore and MobileSync

Their JavaScript source comes from the Shared submodule at `external/shared/`.

## How CordovaPlugin Uses This Repo

The [SalesforceMobileSDK-CordovaPlugin](https://github.com/forcedotcom/SalesforceMobileSDK-CordovaPlugin) repo's `tools/update.sh` script clones this entire Android repo and copies it into `src/android/libs/mobile_sdk/`. The script then prunes directories that are not needed in a Cordova plugin context:

- Removes `native/` sample apps
- Removes `hybrid/` sample apps
- Removes `libs/SalesforceReact` (if present)
- Removes symbolic links
- Updates `settings.gradle.kts` to exclude pruned modules

The result is a self-contained Android composite build inside the Cordova plugin.

## Dev vs. GA Dependency Model

**Dev (pre-release):** The Android repo is cloned as a composite build:

```groovy
// In CordovaPlugin's settings.gradle
includeBuild("mobile_sdk/SalesforceMobileSDK-Android")
```

The `SalesforceHybrid` library is built from source alongside the app.

**GA (release):** The `postinstall-android.js` script in CordovaPlugin replaces the composite build with a Maven Central dependency:

```groovy
// In app/build.gradle
api 'com.salesforce.mobilesdk:SalesforceHybrid:14.0.0'
```

## Running SalesforceHybrid Unit Tests

The test source is at `libs/test/SalesforceHybridTest/`.

From Android Studio:
1. Open the project root in Android Studio
2. Select the `SalesforceHybrid` test configuration
3. Run on a connected device or emulator (min SDK 31)

From command line:

```bash
./gradlew :libs:SalesforceHybrid:connectedAndroidTest
```

Tests require a connected device or emulator (min SDK 31).

Most tests also require a `test_credentials.json` file at `shared/test/test_credentials.json`. Without it, only tests that do not make authenticated Salesforce API calls will pass. Copy the sample and fill in your org details:

```bash
cp shared/test/test_credentials.json.sample shared/test/test_credentials.json
# then edit with your Connected App credentials and org details
```

The file must contain:

```json
{
  "test_client_id":    "<Connected App consumer key>",
  "test_login_domain": "<login URL, e.g. test.salesforce.com>",
  "test_redirect_uri": "<Connected App callback URL>",
  "refresh_token":     "<valid refresh token>",
  "instance_url":      "<org instance URL>",
  "identity_url":      "<identity URL>",
  "organization_id":   "<org ID>",
  "username":          "<test user username>",
  "user_id":           "<test user ID>"
}
```

`test_credentials.json` is gitignored — never commit real credentials.

## Version Management

`setversion.sh` updates the SDK version across all Android library gradle and manifest files:

```bash
# On dev branch:
./setversion.sh -v 14.0.0 -c <versionCode> -d yes
# On master branch (after merging dev → master at release):
./setversion.sh -v 14.0.0 -c <versionCode> -d no
```

For hybrid specifically, this updates:
- `PUBLISH_VERSION` in `libs/SalesforceHybrid/build.gradle.kts` (controls Maven Central artifact version)
- `android:versionName` in `libs/SalesforceHybrid/AndroidManifest.xml` (appends `.dev` when `-d yes`)
- All other SDK library gradle and manifest files in the same pass

## Build Versions

| Component | Version |
|-----------|---------|
| SDK | 14.0.0 |
| Gradle | 9.4.1 |
| AGP (Android Gradle Plugin) | 9.1.1 |
| Kotlin | 2.3.20 |
| Min SDK | 31 (Android 12) |
| Compile/Target SDK | 36 |
