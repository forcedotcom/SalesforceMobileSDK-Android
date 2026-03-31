# Salesforce.com Mobile SDK for Android
[![Tests](https://github.com/forcedotcom/SalesforceMobileSDK-Android/actions/workflows/nightly.yaml/badge.svg?branch=dev)](https://github.com/forcedotcom/SalesforceMobileSDK-Android/actions/workflows/nightly.yaml)
[![Known Vulnerabilities](https://snyk.io/test/github/forcedotcom/SalesforceMobileSDK-Android/badge.svg)](https://snyk.io/test/github/forcedotcom/SalesforceMobileSDK-Android)
![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/forcedotcom/SalesforceMobileSDK-Android?sort=semver)


You have arrived at the source repository for the Salesforce Mobile SDK for Android. Welcome! Starting with our 2.0 release, there are now two ways you can choose to work with the Mobile SDK:

- If you'd like to work with the source code of the SDK itself, you've come to the right place! You can browse sample app source code and debug down through the layers to get a feel for how everything works under the covers. Read on for instructions on how to get started with the SDK in your development environment.
- If you're just eager to start developing your own application, the quickest way is to use our npm binary distribution package, called [forcedroid](https://npmjs.org/package/forcedroid), which is hosted on [npmjs.org](https://npmjs.org/). Getting started is as simple as installing the npm package and launching your template app. You'll find more details on the forcedroid package page.

Installation (do this first - really)
==

After cloning the SalesforceMobileSDK-Android project from github, run the install script from the command line:

`./install.sh`

This pulls submodule dependencies from github.

(Windows users: run `cscript install.vbs` from the command line instead.)

Introduction
==

### What's New in 13.2.0
See [release notes](https://github.com/forcedotcom/SalesforceMobileSDK-Android/releases).

### Native Applications
The Salesforce Mobile SDK provides essential libraries for quickly building native mobile apps that seamlessly integrate with the Salesforce cloud architecture.  Out of the box, we provide an implementation of OAuth2, abstracting away the complexity of securely storing refresh tokens or fetching a new session ID when a session expires. The SDK also provides Java wrappers for the Salesforce REST API, making it easy to retrieve, store, and manipulate data.

### Hybrid Applications
HTML5 is quickly emerging as dominant technology for developing cross-platform mobile applications. While developers can create sophisticated apps with HTML5 and JavaScript, some limitations remain, specifically: session management, access to the camera and address book, and the inability to distribute apps inside public App Stores. The Salesforce Mobile Container makes possible to combine the ease of web app development with power of the Android platform by wrapping a web app inside a thin native container, producing a hybrid application.

## Libraries

The SDK consists of six libraries under `libs/`:

| Library | Purpose | Key Features |
|---------|---------|--------------|
| **SalesforceAnalytics** | Telemetry and analytics | Event tracking, instrumentation |
| **SalesforceSDK** | Authentication and REST API | OAuth2, REST client, account management, identity, push notifications |
| **SmartStore** | Encrypted local storage | SQLCipher-backed storage, indexing, Smart SQL queries |
| **MobileSync** | Data synchronization | Sync up/down, conflict resolution, offline-first patterns |
| **SalesforceHybrid** | Hybrid app support | Cordova integration, JavaScript bridge |
| **SalesforceReact** | React Native support | React Native bridge modules |

### Library Dependencies

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

## Usage

### Authentication (OAuth2)

```kotlin
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.OAuth2

// Configure SDK in your Application class
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        SalesforceSDKManager.initNative(
            applicationContext,
            MainActivity::class.java
        ) { config ->
            config.oauthClientId = "YOUR_CONSUMER_KEY"
            config.oauthRedirectURI = "YOUR_CALLBACK_URL"
            config.oauthScopes = arrayOf("web", "api", "refresh_token")

            // Optional: customize login host
            config.loginServerUrl = "https://login.salesforce.com"
        }
    }
}

// Get current user
val userAccount = SalesforceSDKManager.getInstance().userAccountManager.currentUser
println("Logged in as: ${userAccount.username}")
println("Organization: ${userAccount.orgId}")

// Logout
SalesforceSDKManager.getInstance().logout(null)
```

### REST API

```kotlin
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.rest.RestRequest

// Query records
val request = RestRequest.getRequestForQuery(
    SalesforceSDKManager.getInstance().apiVersion,
    "SELECT Id, Name FROM Account LIMIT 10"
)

restClient.sendAsync(request) { response ->
    val records = response.asJSONObject().getJSONArray("records")
    for (i in 0 until records.length()) {
        val record = records.getJSONObject(i)
        println("Account: ${record.getString("Name")}")
    }
}

// Create a record
val fields = JSONObject().apply {
    put("Name", "Acme Corp")
    put("Industry", "Technology")
}

val request = RestRequest.getRequestForCreate(
    SalesforceSDKManager.getInstance().apiVersion,
    "Account",
    fields
)

restClient.sendAsync(request) { response ->
    val id = response.asJSONObject().getString("id")
    println("Created account with ID: $id")
}

// Update a record
val fields = JSONObject().put("Name", "Updated Name")

val request = RestRequest.getRequestForUpdate(
    SalesforceSDKManager.getInstance().apiVersion,
    "Account",
    recordId,
    fields
)

// Delete a record
val request = RestRequest.getRequestForDelete(
    SalesforceSDKManager.getInstance().apiVersion,
    "Account",
    recordId
)
```

### SmartStore (Encrypted Storage)

```kotlin
import com.salesforce.androidsdk.smartstore.store.SmartStore
import com.salesforce.androidsdk.smartstore.store.IndexSpec
import com.salesforce.androidsdk.smartstore.store.QuerySpec

// Get store instance
val store = SalesforceSDKManager.getInstance().getSmartStore()

// Register a soup (table)
val indexSpecs = arrayOf(
    IndexSpec("Name", SmartStore.Type.string),
    IndexSpec("LastModifiedDate", SmartStore.Type.string)
)

store.registerSoup("accounts", indexSpecs)

// Insert/update entries
val entry = JSONObject().apply {
    put("Name", "Acme Corp")
    put("Industry", "Technology")
}

store.upsert("accounts", entry)

// Query entries
val querySpec = QuerySpec.buildSmartQuerySpec(
    "SELECT {accounts:Name}, {accounts:Industry} FROM {accounts} ORDER BY {accounts:Name}",
    10
)

val results = store.query(querySpec, 0)
val cursor = results.getJSONArray("rows")
for (i in 0 until cursor.length()) {
    val entry = cursor.getJSONObject(i)
    println("Account: ${entry.getString("Name")}")
}

// Delete entries
store.delete("accounts", entryId)
```

### MobileSync (Data Synchronization)

```kotlin
import com.salesforce.androidsdk.mobilesync.manager.SyncManager
import com.salesforce.androidsdk.mobilesync.target.SoqlSyncDownTarget
import com.salesforce.androidsdk.mobilesync.target.SyncUpTarget
import com.salesforce.androidsdk.mobilesync.util.SyncOptions

// Get sync manager
val syncManager = SyncManager.getInstance()

// Sync down from Salesforce
val target = SoqlSyncDownTarget(
    "SELECT Id, Name, Industry FROM Account WHERE LastModifiedDate > {LastModifiedDate}"
)

val options = SyncOptions.optionsForSyncDown(SyncOptions.MergeMode.OVERWRITE)

syncManager.syncDown(target, "accounts", null, options) { syncState ->
    when {
        syncState.isDone -> {
            println("Sync down complete: ${syncState.totalSize} records")
        }
        syncState.hasFailed() -> {
            println("Sync failed: ${syncState.error}")
        }
    }
}

// Sync up to Salesforce
val target = SyncUpTarget()
val options = SyncOptions.optionsForSyncUp(
    listOf("Name", "Industry"),
    SyncOptions.MergeMode.OVERWRITE
)

syncManager.syncUp(target, "accounts", null, options) { syncState ->
    if (syncState.isDone) {
        println("Sync up complete: ${syncState.totalSize} records")
    }
}
```

## Building from Source

### Prerequisites

- **Android Studio**: Electric Eel or higher
- **JDK**: 17 or higher
- **Gradle**: 8.14.3 (managed by wrapper)
- **Android SDK**: API 35 (compileSdk), API 28 minimum (minSdk)
- **Git**: 2.13 or higher

### Setup

```bash
# Clone the repository
git clone https://github.com/forcedotcom/SalesforceMobileSDK-Android.git
cd SalesforceMobileSDK-Android

# Install dependencies (submodules)
./install.sh  # macOS/Linux
# or
cscript install.vbs  # Windows

# Open in Android Studio
# File → Open → Select SalesforceMobileSDK-Android directory
```

### Building

```bash
# Build all libraries
./gradlew build

# Build specific library
./gradlew :libs:SalesforceSDK:build
./gradlew :libs:SmartStore:build
./gradlew :libs:MobileSync:build

# Run lint checks
./gradlew :libs:SalesforceSDK:lint
```

### Running Tests

```bash
# Run tests for SalesforceSDK (requires connected device or emulator)
./gradlew :libs:SalesforceSDK:connectedAndroidTest

# Run SmartStore tests
./gradlew :libs:SmartStore:connectedAndroidTest

# Run MobileSync tests
./gradlew :libs:MobileSync:connectedAndroidTest

# Run all instrumented tests
./gradlew connectedAndroidTest
```

## Distribution

The SDK is distributed via:

- **Maven Central**: Published artifacts for all libraries
- **npm**: CLI tool [forcedroid](https://www.npmjs.com/package/forcedroid) for generating apps from templates

### Using Gradle Dependencies

```kotlin
// build.gradle.kts (app module)
dependencies {
    implementation("com.salesforce.mobilesdk:SalesforceSDK:13.2.0")
    implementation("com.salesforce.mobilesdk:SmartStore:13.2.0")
    implementation("com.salesforce.mobilesdk:MobileSync:13.2.0")

    // For hybrid apps
    implementation("com.salesforce.mobilesdk:SalesforceHybrid:13.2.0")

    // For React Native apps
    implementation("com.salesforce.mobilesdk:SalesforceReact:13.2.0")
}
```

### Creating Apps with forcedroid

```bash
# Install forcedroid CLI
npm install -g forcedroid

# Create a new app
forcedroid create \
  --appname MyApp \
  --packagename com.mycompany.myapp \
  --organization "My Company"

# List available templates
forcedroid listtemplates

# Create from specific template
forcedroid createwithtemplate \
  --templaterepouri AndroidNativeKotlinTemplate \
  --appname MyApp \
  --packagename com.mycompany.myapp
```

Setting up your Development Environment
==

The following steps will help you get started with your development environment, whether you choose to develop native apps or hybrid apps. See the `README` files in the `native/` and `hybrid/` folders for additional notes pertaining to development in those environments.

1. Install the Android SDK and Android Studio: http://developer.android.com/sdk/index.html
2. Get setup on github: http://help.github.com/

Downloading the Salesforce SDK
==

To pull down the SDK from github, create a new directory and git clone the salesforce SDK repo.
<pre>
git clone https://github.com/forcedotcom/SalesforceMobileSDK-Android.git
</pre>

Documentation
==

* [SalesforceSDK](https://forcedotcom.github.io/SalesforceMobileSDK-Android/index.html)
* Salesforce Mobile SDK Development Guide -- [HTML](https://developer.salesforce.com/docs/atlas.en-us.mobile_sdk.meta/mobile_sdk/preface_intro.htm)
* [Mobile SDK Trail](https://trailhead.salesforce.com/trails/mobile_sdk_intro)

Discussion
==

If you would like to make suggestions, have questions, or encounter any issues, we'd love to hear from you. Post any feedback you have on the [Mobile SDK Trailblazer Community](https://trailhead.salesforce.com/en/trailblazer-community/groups/0F94S000000kH0HSAU?tab=discussion&sort=LAST_MODIFIED_DATE_DESC).
