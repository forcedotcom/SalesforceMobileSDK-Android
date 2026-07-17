# Developer Info Screen

The Salesforce Mobile SDK ships a built-in developer / diagnostic screen accessible from the login UI overflow menu. It surfaces SDK configuration, the current user's OAuth state, and runtime configuration so developers can verify SDK behaviour without needing a debugger.

---

## Accessing the Screen

The screen is launched via `DevInfoActivity`. It appears in the overflow menu of `LoginActivity` and `SalesforceActivity` under the label **"Developer Support"** (`sf__dev_support_title_menu_item`).

---

## Architecture

### Data model — `DevSupportInfo`

`DevSupportInfo` (`com.salesforce.androidsdk.developer.support`) is a data class that carries all content for the screen. It is built by `SalesforceSDKManager.devSupportInfo` and consumed by `DevInfoActivity`.

```
typealias DevInfoList    = List<Pair<String, String>>
typealias DevInfoSection = Pair<String, DevInfoList>
```

| Field | Type | UI treatment |
|---|---|---|
| `basicInfo` | `DevInfoList?` | Non-collapsible rows at the top |
| `authConfigSection` | `DevInfoSection?` | Collapsible card |
| `bootConfigSection` | `DevInfoSection?` | Collapsible card |
| `currentUserSection` | `DevInfoSection?` | Collapsible card; absent when no user is logged in |
| `runtimeConfigSection` | `DevInfoSection?` | Collapsible card |
| `additionalSections` | `MutableList<DevInfoSection>` | Extra collapsible cards; for SDK extensions or app overrides |

### UI — `DevInfoActivity` / `DevInfoScreen`

`DevInfoActivity` is a Jetpack Compose `ComponentActivity`. It reads `SalesforceSDKManager.getInstance().devSupportInfo` once in `onCreate()` and renders:

- `basicInfo` rows directly (no card)
- Each section as a `CollapsibleSection` composable (card with expand/collapse chevron)
- Each row as a `DevInfoItem` — label + value, tapping the value copies it to the clipboard

### Building the data — `SalesforceSDKManager.devSupportInfo`

`SalesforceSDKManager` exposes an `open val devSupportInfo: DevSupportInfo`. The default implementation currently delegates to `createFromLegacyDevInfos(devSupportInfos)` for backward compatibility; the commented-out block (lines 1612–1640) shows the target direct implementation for SDK 14.0 when `devSupportInfos` is removed.

Subclasses can override `devSupportInfo` to add or replace sections.

---

## Sections and Rows

### Basic Info (non-collapsible)

| Row | Source |
|-----|--------|
| SDK Version | `SalesforceSDKManager.SDK_VERSION` |
| App Type | `SalesforceSDKManager.appType` |
| User Agent | `SalesforceSDKManager.userAgent` |
| Authenticated Users | `UserAccountManager.authenticatedUsers` |

### Authentication Configuration

| Row | Source |
|-----|--------|
| Use Web Server Authentication | `SalesforceSDKManager.useWebServerAuthentication` |
| Use Hybrid Authentication Token | `SalesforceSDKManager.useHybridAuthentication` |
| Force Advanced Authentication | `SalesforceSDKManager._forceAdvancedAuthentication` |
| Browser Login Enabled | `SalesforceSDKManager.isBrowserLoginEnabled` |
| IDP Enabled | `SalesforceSDKManager.isIDPLoginFlowEnabled` |
| Identity Provider | `SalesforceSDKManager.isIdentityProvider` |

### Boot Configuration

Populated by `DevSupportInfo.parseBootConfigInfo(BootConfig)`.

| Row | Source |
|-----|--------|
| Consumer Key | `BootConfig.remoteAccessConsumerKey` |
| Redirect URI | `BootConfig.oauthRedirectURI` |
| Scopes | `BootConfig.oauthScopes` joined by space |
| Local *(Hybrid only)* | `BootConfig.isLocal` |
| Start Page *(Hybrid only)* | `BootConfig.startPage` |
| Unauthenticated Start Page *(Hybrid only)* | `BootConfig.unauthenticatedStartPage` |
| Error Page *(Hybrid only)* | `BootConfig.errorPage` |
| Should Authenticate *(Hybrid only)* | `BootConfig.shouldAuthenticate()` |
| Attempt Offline Load *(Hybrid only)* | `BootConfig.attemptOfflineLoad()` |

### Current User

Populated by `DevSupportInfo.parseUserInfoSection(UserAccount?)`. Absent when no user is logged in.

| Row | Source |
|-----|--------|
| Username | `UserAccount.username` |
| Consumer Key | `UserAccount.clientId` |
| Scopes | `UserAccount.scope` |
| Instance URL | `UserAccount.instanceServer` |
| Token Format | `UserAccount.tokenFormat` (blank → "Opaque") |
| Access Token Expiration | Decoded from JWT when `tokenFormat == "jwt"`, else "Unknown" |
| Beacon Child Consumer Key | `UserAccount.beaconChildConsumerKey` (null → "None") |
| OAuth Token Type | `UserAccount.tokenType` ("Bearer" or "DPoP") |
| DPoP Nonce *(DPoP sessions only)* | `DPoPNonceCache.get(credentialsIdentifier, host)` |
| DPoP Key Thumbprint *(DPoP sessions only)* | JWK SHA-256 thumbprint of the EC P-256 public key from `DPoPKeyManager` |

### Runtime Configuration

Populated by `DevSupportInfo.parseRuntimeConfig(RuntimeConfig)`.

| Row | Source |
|-----|--------|
| Managed App | `RuntimeConfig.isManagedApp` |
| OAuth ID *(managed only)* | `RuntimeConfig.ManagedAppOAuthID` |
| Callback URL *(managed only)* | `RuntimeConfig.ManagedAppCallbackURL` |
| Require Cert Auth *(managed only)* | `RuntimeConfig.RequireCertAuth` |
| Only Show Authorized Hosts *(managed only)* | `RuntimeConfig.OnlyShowAuthorizedHosts` |

---

## Adding New Rows

### To an existing section

Modify the relevant companion-object parse function in `DevSupportInfo.kt`:

- `parseBootConfigInfo(BootConfig)` — Boot Configuration section
- `parseUserInfoSection(UserAccount?)` — Current User section
- `parseRuntimeConfig(RuntimeConfig)` — Runtime Configuration section

Example — adding a row to the Current User section:

```kotlin
fun parseUserInfoSection(currentUser: UserAccount?): DevInfoSection? {
    if (currentUser == null) return null
    // ... existing rows ...
    val rows = mutableListOf(
        "Username" to currentUser.username,
        // existing rows
        "My New Field" to someValue,
    )
    return "Current User" to rows
}
```

### As a new section

Use `additionalSections` on the returned `DevSupportInfo`, either by overriding `devSupportInfo` in a `SalesforceSDKManager` subclass or by calling `devSupportInfo.additionalSections.add(...)` before the screen is shown:

```kotlin
override val devSupportInfo: DevSupportInfo
    get() = super.devSupportInfo.also { info ->
        info.additionalSections.add(
            "My Section" to listOf(
                "Key" to "value",
            )
        )
    }
```

---

## Key Thumbprint (DPoP)

For DPoP sessions, the EC P-256 public key thumbprint (RFC 7638 JWK SHA-256) identifies which key the server has bound to the user's tokens. It is computed from the JWK `{"crv":"P-256","kty":"EC","x":"...","y":"..."}` canonical form:

```kotlin
fun jwkThumbprint(publicKey: ECPublicKey): String {
    val point = publicKey.w
    val x = base64url(toUnsigned32(point.affineX.toByteArray()))
    val y = base64url(toUnsigned32(point.affineY.toByteArray()))
    // RFC 7638: members sorted lexicographically, no whitespace
    val canonical = """{"crv":"P-256","kty":"EC","x":"$x","y":"$y"}"""
    val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
    return base64url(digest)
}
```

The thumbprint displayed in the dev info screen should match the `jkt` claim the server embedded in the issued access token.

---

## Related Files

| File | Purpose |
|------|---------|
| `libs/SalesforceSDK/.../developer/support/DevSupportInfo.kt` | Data model; parse functions for each section |
| `libs/SalesforceSDK/.../ui/DevInfoActivity.kt` | Compose UI; renders `DevSupportInfo` |
| `libs/SalesforceSDK/.../app/SalesforceSDKManager.kt` | Exposes `devSupportInfo`; builds the data |
| `libs/SalesforceSDK/.../auth/dpop/DPoPKeyManager.kt` | EC key pair generation/load; `aliasForCredentialsIdentifier()` |
| `libs/SalesforceSDK/.../auth/dpop/DPoPProofBuilder.kt` | JWK construction (`buildJwk()`); used for thumbprint computation |
| `libs/SalesforceSDK/.../auth/dpop/DPoPNonceCache.kt` | In-memory nonce cache; `get(credentialsId, host)` |
| `libs/test/.../developer/support/DevSupportInfoTest.kt` | Unit tests for `DevSupportInfo` parse functions |
| `libs/test/.../ui/DevInfoActivityTest.kt` | UI tests for `DevInfoActivity` |
