# Push Notifications — Salesforce Mobile SDK for Android

This document covers the push notification subsystem in `libs/SalesforceSDK`. All classes live in the package `com.salesforce.androidsdk.push`.

For cross-platform concepts and the shared Salesforce registration model, see the [workspace-level push doc](../../../docs/push/README.md).

---

## Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Setup](#setup)
4. [Architecture](#architecture)
5. [Key Classes](#key-classes)
6. [Registration Lifecycle](#registration-lifecycle)
7. [Re-registration Modes](#re-registration-modes)
8. [Foreground Registration Mode](#foreground-registration-mode)
9. [Encrypted Push Notifications](#encrypted-push-notifications)
10. [Actionable Notifications](#actionable-notifications)
11. [Testing](#testing)

---

## Overview

The SDK integrates Firebase Cloud Messaging (FCM) to deliver push notifications from a Salesforce org to Android devices. Registration state is stored per-user in private `SharedPreferences`, and push notifications can be encrypted end-to-end using RSA + AES.

---

## Prerequisites

| Requirement | Details |
|---|---|
| `google-services.json` | Place in your app module root; required for FCM token acquisition |
| `com.google.gms:google-services` plugin | Apply in your app-level `build.gradle.kts` |
| `PushNotificationInterface` implementation | Register via `SalesforceSDKManager.getInstance().pushNotificationReceiver` |
| `POST_NOTIFICATIONS` permission | Required for Android 13+ (API 33+); declare in manifest and request at runtime |
| External Client App | Connected app with push notification endpoint enabled in your Salesforce org |

---

## Setup

### 1. Add google-services.json

Download `google-services.json` from the [Firebase Console](https://console.firebase.google.com/) and place it in your app module directory (e.g. `app/`).

### 2. Apply the Google Services Gradle Plugin

**`build.gradle.kts`** (project level):
```kotlin
buildscript {
    dependencies {
        classpath("com.google.gms:google-services:4.4.2")
    }
}
```

**`app/build.gradle.kts`** (app level):
```kotlin
plugins {
    id("com.google.gms.google-services")
}
```

### 3. Declare POST_NOTIFICATIONS Permission (Android 13+)

**`AndroidManifest.xml`**:
```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

Request the permission at runtime in your `Activity` or `MainActivity`:
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
    checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED) {
    requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), requestCode)
}
```

### 4. Implement PushNotificationInterface

In your `Application.onCreate()` after `SalesforceSDKManager` is initialized:

```kotlin
// Native Android apps
SalesforceSDKManager.getInstance().pushNotificationReceiver =
    object : PushNotificationInterface {
        override fun onPushMessageReceived(data: Map<String?, String?>?) {
            // Handle the notification. The SDK has already decrypted any encrypted payload.
        }
        override fun supplyFirebaseMessaging(): FirebaseMessaging? = null
    }

// React Native apps — use SalesforceReactSDKManager instead
SalesforceReactSDKManager.getInstance().pushNotificationReceiver =
    object : PushNotificationInterface { /* ... */ }
```

That is all. `SFDCFcmListenerService` (declared in the SDK's own `AndroidManifest.xml`) handles FCM token acquisition, Salesforce device registration, decryption, and re-registration automatically.

---

## Architecture

```
App
 └── SalesforceSDKManager
      ├── pushNotificationReceiver: PushNotificationInterface   ← app implements this
      └── pushServiceType: Class<out PushService>              ← customizable

PushMessaging (object)          ← entry point for register/unregister
 └── PushService (open class)   ← handles SFDC endpoint communication
      └── PushNotificationsRegistrationChangeWorker  ← WorkManager worker

SFDCFcmListenerService          ← FCM callbacks (onNewToken, onMessageReceived)
 └── PushNotificationDecryptor  ← optional payload decryption
```

**Data flow — registration:**
1. App calls `PushMessaging.register(context, account)`.
2. `PushMessaging` acquires the FCM token from Firebase.
3. Token is stored in `SharedPreferences` keyed by user account.
4. A `PushNotificationsRegistrationChangeWorker` is enqueued via `WorkManager`.
5. The worker calls `PushService.performRegistrationChange(register=true, ...)`.
6. `PushService` POSTs to the Salesforce `MobilePushServiceDevice` REST endpoint.
7. On success, the Salesforce device ID is stored alongside the FCM token.

**Data flow — incoming message:**
1. FCM delivers the message to `SFDCFcmListenerService.onMessageReceived`.
2. `PushNotificationDecryptor` decrypts the payload if it is encrypted.
3. The plaintext payload is forwarded to `PushNotificationInterface.onPushMessageReceived`.

---

## Key Classes

### `PushNotificationInterface`

Interface that the app must implement to receive push notifications.

```kotlin
interface PushNotificationInterface {
    fun onPushMessageReceived(data: Map<String?, String?>?)
    fun supplyFirebaseMessaging(): FirebaseMessaging?   // optional; return null for default
}
```

Register the implementation before the SDK initializes:
```kotlin
SalesforceSDKManager.getInstance().pushNotificationReceiver = MyPushReceiver()
```

---

### `PushMessaging` (object)

Utility singleton for push operations. The main entry points for the app.

| Method | Description |
|---|---|
| `register(context, account)` | Register or re-register a user for push |
| `register(context, account, recreateKey)` | Same, with option to rotate the RSA key |
| `unregister(context, account, isLastAccount)` | Unregister a user; delete Firebase app if last account |
| `registerSFDCPush(context, account)` | Re-register at the SFDC endpoint only (token already known) |
| `isRegistered(context, account)` | Whether the FCM token is stored for this account |
| `getRegistrationId(context, account)` | Returns the stored FCM token |
| `getDeviceId(context, account)` | Returns the Salesforce device ID |
| `getNotificationsTypes(account)` | Returns stored Salesforce notification types |
| `clearRegistrationInfo(context, account)` | Wipe all registration state for this account |

---

### `PushService` (open class)

Handles communication with the Salesforce `MobilePushServiceDevice` REST endpoint. Subclass this to customize the registration or unregistration requests.

Key companion object properties:
```kotlin
var pushNotificationsRegistrationType: PushNotificationReRegistrationType
    = ReRegistrationOnAppForeground   // default

var foregroundRegistrationMode: PushNotificationForegroundRegistrationMode
    = PushNotificationForegroundRegistrationMode.ALL_USERS   // default
```

Key methods:

| Method | Description |
|---|---|
| `performRegistrationChange(register, userAccount, restClient?)` | Main entry point called by the WorkManager worker |
| `onRegistered(registrationId, account, restClient)` | Handles a successful FCM registration |
| `onUnregistered(account, restClient)` | Handles logout / unregistration |
| `registerSFDCPushNotification(...)` | POSTs to SFDC endpoint, returns Salesforce device ID |
| `unregisterSFDCPushNotification(...)` | DELETEs from SFDC endpoint |
| `onPushNotificationRegistrationStatus(status, userAccount)` | Override to react to registration status changes |
| `onSendRegisterPushNotificationRequest(...)` | Override to customize the registration HTTP request |
| `onSendUnregisterPushNotificationRequest(...)` | Override to customize the unregistration HTTP request |
| `fetchNotificationsTypes(restClient, userAccount)` | Fetches Salesforce notification types from the API |
| `registerNotificationChannels(...)` | Creates Android notification channels from Salesforce types |
| `removeNotificationsCategories()` | Deletes all Salesforce notification channels |

Registration status constants (passed to `onPushNotificationRegistrationStatus`):

| Constant | Value |
|---|---|
| `REGISTRATION_STATUS_SUCCEEDED` | 0 |
| `REGISTRATION_STATUS_FAILED` | 1 |
| `UNREGISTRATION_STATUS_SUCCEEDED` | 2 |
| `UNREGISTRATION_STATUS_FAILED` | 3 |

---

### `PushNotificationsRegistrationChangeWorker`

Internal `Worker` subclass that executes push registration changes on a background thread via `WorkManager`. Not instantiated directly — use `PushService.enqueuePushNotificationsRegistrationWork(...)`.

When `userAccount` is `null`, the worker iterates all authenticated users and calls `performRegistrationChange` for each.

---

### `SFDCFcmListenerService`

Extends `FirebaseMessagingService`. Declared in the SDK's `AndroidManifest.xml` with `android:exported="false"`.

| Callback | Behaviour |
|---|---|
| `onNewToken(token)` | Stores the new FCM token and re-registers the current user at the SFDC endpoint |
| `onMessageReceived(message)` | Forwards the message (after optional decryption) to `PushNotificationInterface` |

---

### `PushNotificationDecryptor`

Singleton that decrypts incoming push payloads. Called automatically by `SFDCFcmListenerService`.

Encrypted payload fields:
- `encrypted` — boolean; if `false`, the payload is forwarded unchanged
- `secret` — RSA-encrypted AES symmetric key (base64)
- `content` — AES-encrypted notification body (base64)

Encryption scheme: RSA-OAEP-SHA256 wrapping a 128-bit AES key + 128-bit IV.

---

### `SalesforceActionableNotificationContent`

Serializable data class modelling the Salesforce actionable notification payload under the `sfdc` key.

```kotlin
@Serializable
data class SalesforceActionableNotificationContent(val sfdc: Sfdc?) {
    companion object {
        fun fromJson(json: String): SalesforceActionableNotificationContent
    }

    @Serializable
    data class Sfdc(
        val notifType: String? = null,
        val nid: String? = null,          // notification ID (use for invokeServerNotificationAction)
        val oid: String? = null,          // org ID
        val type: Int? = null,
        val alertTitle: String? = null,
        val alertBody: String? = null,
        val alert: String? = null,
        val sid: String? = null,
        val rid: String? = null,
        val targetPageRef: String? = null,
        val badge: Int? = null,
        val uid: String? = null,
        val cid: String? = null,
        val timestamp: Int? = null,
        val act: Act? = null              // actionable action descriptor
    )
}
```

Parse from the `"content"` key of the received `data` map (the full JSON wraps the `sfdc` object):
```kotlin
override fun onPushMessageReceived(data: Map<String?, String?>?) {
    val sfdc = data?.get("content")
        ?.let { SalesforceActionableNotificationContent.fromJson(it) }
        ?.sfdc ?: return
    val notificationId = sfdc.nid
    // use notificationId with SalesforceSDKManager.invokeServerNotificationAction(...)
}
```

**Note on `Act`:** `sfdc.act?.group` is a string identifier that matches `notificationType.actionGroups[].name` — the action *buttons* (with `label` and `actionKey`) live on the notification type fetched from the API, not inside the `act` field of the incoming payload.

---

### `NotificationsApiClient`

REST client for the Salesforce Notifications API (requires API v64.0+).

```kotlin
class NotificationsApiClient(private val restClient: RestClient) {
    fun fetchNotificationsTypes(): NotificationsTypesResponseBody?
    fun submitNotificationAction(notificationId: String, actionKey: String): NotificationsActionsResponseBody?
}
```

Use via `SalesforceSDKManager`:
```kotlin
// Fetch stored notification types (populated automatically after registration)
val type = SalesforceSDKManager.getInstance().getNotificationsType("approval_request")

// Invoke a server-side notification action
SalesforceSDKManager.getInstance().invokeServerNotificationAction(
    notificationId = nid,
    actionKey = actionIdentifier,
    restClient = restClient
)
```

---

### React Native Apps

For React Native apps, the setup is identical to the native Android setup above, with one substitution: use `SalesforceReactSDKManager.getInstance()` in place of `SalesforceSDKManager.getInstance()`:

```kotlin
// In MainApplication.onCreate(), after SalesforceReactSDKManager.initReactNative(...)
SalesforceReactSDKManager.getInstance().pushNotificationReceiver =
    object : PushNotificationInterface {
        override fun onPushMessageReceived(data: Map<String?, String?>?) { /* ... */ }
        override fun supplyFirebaseMessaging(): FirebaseMessaging? = null
    }
```

There is no JavaScript push bridge — the `react-native-force` package does not export a push module. See [`SalesforceMobileSDK-ReactNative/docs/push/README.md`](https://github.com/forcedotcom/SalesforceMobileSDK-ReactNative/tree/dev/docs/push) for guidance on forwarding push payloads to the JavaScript layer via a custom event emitter.

---

## Registration Lifecycle

```
App foreground / login
        │
        ▼
PushMessaging.register(context, account)
        │
        ├─ First time for this account ──► acquire FCM token ──► store token
        │                                                          │
        │                                                          ▼
        └─ Already registered ──────────────────────► enqueue WorkManager job
                                                                   │
                                                                   ▼
                                               PushService.performRegistrationChange
                                                                   │
                                                                   ▼
                                                  POST /services/data/vXX.0/sobjects/
                                                       MobilePushServiceDevice
                                                                   │
                                                      ┌────────────┴─────────────┐
                                                      ▼                          ▼
                                               201 Created               404 Not Found
                                           (store device ID)        (push not enabled —
                                                                       stop retrying)
```

**Unregistration** (logout):
1. App calls `PushMessaging.unregister(context, account, isLastAccount)`.
2. If last account, Firebase token is deleted and the Firebase app is removed.
3. `PushService.onUnregistered` DELETEs from `MobilePushServiceDevice`.
4. Broadcasts `UNREGISTERED_ATTEMPT_COMPLETE_EVENT` and `UNREGISTERED_EVENT`.
5. Stored registration info is cleared from `SharedPreferences`.

---

## Re-registration Modes

Set via `PushService.pushNotificationsRegistrationType`:

```kotlin
PushService.pushNotificationsRegistrationType =
    PushService.PushNotificationReRegistrationType.ReRegisterPeriodically
```

| Value | Behaviour |
|---|---|
| `ReRegistrationDisabled` | No automatic re-registration |
| `ReRegistrationOnAppForeground` | Re-registers when the app foregrounds **(default)** |
| `ReRegisterPeriodically` | Re-registers all users every six days via a periodic `WorkManager` task, regardless of foreground/background |

`ReRegisterPeriodically` is useful to counteract periodic SFDC API cleanup that de-registers devices after extended inactivity.

---

## Foreground Registration Mode

When `pushNotificationsRegistrationType` is `ReRegistrationOnAppForeground`, a separate property controls **which users** are re-registered each time the app foregrounds:

```kotlin
PushService.foregroundRegistrationMode =
    PushService.PushNotificationForegroundRegistrationMode.CURRENT_USER
```

| Value | Behaviour |
|---|---|
| `ALL_USERS` | Re-registers every authenticated user **(default)** |
| `CURRENT_USER` | Re-registers only the currently active user (pre-14.0 behaviour) |

`foregroundRegistrationMode` only takes effect when `pushNotificationsRegistrationType` is `ReRegistrationOnAppForeground`. It is ignored for `ReRegistrationDisabled` and `ReRegisterPeriodically`.

### Why `CURRENT_USER` exists

Some Publisher customers are billed **per login event**. An FCM token refresh on a background user triggers a re-registration request which in turn counts as a billable login. With `ALL_USERS` (the default), background users that haven't foregrounded recently may have their tokens refreshed when the app comes to the foreground. Set `CURRENT_USER` to prevent this for those deployments.

---

## Encrypted Push Notifications

Enable encryption by ensuring the SDK's RSA key pair is registered during push setup. The SDK automatically:
1. Generates an RSA-2048 key pair in the Android Keystore (key name derived from `SalesforceKeyGenerator.getUniqueId("PushNotificationKey")`).
2. Includes the RSA public key and cipher name (`RSA_OAEP_SHA256`) in the `MobilePushServiceDevice` registration payload.
3. Salesforce encrypts the AES symmetric key with the public key before sending the notification.
4. `PushNotificationDecryptor` uses the private key from the Keystore to decrypt the symmetric key, then decrypts the payload.

No app-side configuration is required beyond normal SDK setup.

---

## Actionable Notifications

Requires Salesforce API version **v64.0 or later**.

After successful device registration, the SDK automatically fetches notification types from `GET /vXX.0/connect/notifications/types` and stores them per user. It creates one `NotificationChannel` per Salesforce notification type (channel ID = `notificationType.type`, importance `HIGH`), grouped under a `NotificationChannelGroup` named `"Salesforce Notifications"`.

Actionable notifications require three collaborating pieces:

1. **Payload parsing** — parse `"content"` in `onPushMessageReceived`, look up the notification type by `notifType`.
2. **Notification building** — construct a `NotificationCompat` with action buttons sourced from the notification type's `actionGroups`.
3. **Action handling** — a `BroadcastReceiver` receives the `PendingIntent` when the user taps a button and calls `invokeServerNotificationAction`.

### 1. Parse payload and build the notification

```kotlin
override fun onPushMessageReceived(data: Map<String?, String?>?) {
    // Payload key is "content" — the SDK has already decrypted it
    val sfdc = data?.get("content")
        ?.let { SalesforceActionableNotificationContent.fromJson(it) }
        ?.sfdc ?: return

    // Look up the stored notification type (fetched automatically after registration)
    val notifType = sfdc.notifType?.let {
        SalesforceSDKManager.getInstance().getNotificationsType(it)
    } ?: return

    // Build notification using notifType.type as the channel ID
    val notification = NotificationCompat.Builder(context, notifType.type).apply {
        setContentTitle(sfdc.alertTitle)
        setContentText(sfdc.alert)
        setStyle(NotificationCompat.BigTextStyle().bigText(sfdc.alertBody))

        // Action buttons come from the notification type's actionGroups,
        // matched by sfdc.act?.group (the payload carries a group name, not the buttons)
        notifType.actionGroups
            ?.firstOrNull { it.name == sfdc.act?.group }
            ?.actions
            ?.forEach { action ->
                addAction(
                    android.R.drawable.ic_menu_send,
                    action.label,
                    PendingIntent.getBroadcast(
                        context, 0,
                        Intent("com.example.PUSH_ACTION").apply {
                            putExtra("notificationId", sfdc.nid)
                            putExtra("actionKey", action.actionKey)
                        },
                        PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }
    }.build()
    context.getSystemService(NotificationManager::class.java)
        .notify(sfdc.nid.hashCode(), notification)
}
```

### 2. Handle the action tap with a BroadcastReceiver

```kotlin
class PushActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val nid = intent.getStringExtra("notificationId") ?: return
        val actionKey = intent.getStringExtra("actionKey") ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // restClient defaults to clientManager.peekRestClient(currentUser)
                SalesforceSDKManager.getInstance()
                    .invokeServerNotificationAction(notificationId = nid, actionKey = actionKey)
            } catch (e: Exception) {
                Log.e("Push", "Action invocation failed", e)
            }
        }
    }
}
```

Register the receiver in `Application.onCreate()` (not in the manifest — use `RECEIVER_NOT_EXPORTED` to prevent external apps from sending intents to it):

```kotlin
ContextCompat.registerReceiver(
    this,
    PushActionReceiver(),
    IntentFilter("com.example.PUSH_ACTION"),
    ContextCompat.RECEIVER_NOT_EXPORTED
)
```

---

## Testing

Push notification tests are in `libs/test/SalesforceSDKTest/src/com/salesforce/androidsdk/`:

| Test class | Coverage |
|---|---|
| `push/PushServiceTest` | SFDC registration/unregistration endpoint communication, notification channel creation/deletion, status callbacks, HTTP error handling |
| `push/PushMessagingTest` | Notification type storage/retrieval, `SalesforceSDKManager` notification API delegation, `NotificationsApiClient` content-type behaviour |
| `push/PushNotificationsRegistrationChangeWorkerTest` | WorkManager worker account-resolution logic, legacy pre-14.0 payload migration |

Tests use [MockK](https://mockk.io/) to mock `RestClient` and `RestResponse`. They run as instrumented tests on-device or on Firebase Test Lab:

```bash
./gradlew :libs:SalesforceSDK:connectedAndroidTest
```

Or build the APK for upload:
```bash
./gradlew :libs:SalesforceSDK:assembleAndroidTest
```
