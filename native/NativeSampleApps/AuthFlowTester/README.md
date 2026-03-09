# AuthFlowTester

A native Android sample app for the Salesforce Mobile SDK that serves as the primary vehicle for **UI automation testing** of authentication flows. The app displays OAuth credentials, token details, and user information after login, enabling end-to-end validation of the SDK's authentication infrastructure.

## UI Test Coverage

Tests are executed by GitHub Actions via `.github/workflows/reusable-ui-workflow.yaml` and run in [Firebase Test Lab](https://firebase.google.com/docs/test-lab) across all supported API levels using the AndroidX Test Orchestrator.

- **PR runs** — a subset of representative tests on a single API level
- **Nightly runs** — all tests batched across API level. Multi-user tests run in separate batches with even/odd API level splitting to avoid credential collisions between adjacent levels.

### Test Suites

#### BootConfigLoginTests
Legacy login tests using the default Connected App (CA) opaque configuration from the app's `bootconfig.xml`.

| Test | App Config | Scopes | Flow | Hybrid |
|------|-----------|--------|------|--------|
| `testCAOpaque_DefaultScopes_WebServerFlow` | CA Opaque | Default | Web Server | Yes |
| `testCAOpaque_DefaultScopes_WebServerFlow_NotHybrid` | CA Opaque | Default | Web Server | No |
| `testCAOpaque_DefaultScopes_UserAgentFlow` | CA Opaque | Default | User Agent | Yes |
| `testCAOpaque_DefaultScopes_UserAgentFlow_NotHybrid` | CA Opaque | Default | User Agent | No |

#### CAScopeSelectionLoginTests
Connected App login tests with explicit scope selection across web server and user agent flows, both hybrid and non-hybrid.

| Test | Scopes | Flow | Hybrid |
|------|--------|------|--------|
| `testCAOpaque_SubsetScopes_WebServerFlow` | Subset | Web Server | No |
| `testCAOpaque_AllScopes_WebServerFlow` | All | Web Server | Yes |
| `testCAOpaque_SubsetScopes_WebServerFlow_NotHybrid` | Subset | Web Server | No |
| `testCAOpaque_AllScopes_WebServerFlow_NotHybrid` | All | Web Server | No |
| `testCAOpaque_SubsetScopes_UserAgentFlow` | Subset | User Agent | Yes |
| `testCAOpaque_AllScopes_UserAgentFlow` | All | User Agent | Yes |
| `testCAOpaque_SubsetScopes_UserAgentFlow_NotHybrid` | Subset | User Agent | No |
| `testCAOpaque_AllScopes_UserAgentFlow_NotHybrid` | All | User Agent | No |

#### ECALoginTests
External Client App (ECA) login tests for both opaque and JWT token formats with scope variations.

| Test | App Config | Scopes |
|------|-----------|--------|
| `testECAOpaque_DefaultScopes` | ECA Opaque | Default |
| `testECAOpaque_SubsetScopes` | ECA Opaque | Subset |
| `testECAOpaque_AllScopes` | ECA Opaque | All |
| `testECAJwt_DefaultScopes` | ECA JWT | Default |
| `testECAJwt_SubsetScopes_NotHybrid` | ECA JWT | Subset |
| `testECAJwt_AllScopes` | ECA JWT | All |

#### BeaconLoginTests
Beacon app login tests for lightweight authentication use cases, covering both opaque and JWT token formats.

| Test | App Config | Scopes |
|------|-----------|--------|
| `testBeaconOpaque_DefaultScopes` | Beacon Opaque | Default |
| `testBeaconOpaque_SubsetScopes` | Beacon Opaque | Subset |
| `testBeaconOpaque_AllScopes` | Beacon Opaque | All |
| `testBeaconJwt_DefaultScopes` | Beacon JWT | Default |
| `testBeaconJwt_SubsetScopes` | Beacon JWT | Subset |
| `testBeaconJwt_AllScopes` | Beacon JWT | All |

#### AdvancedAuthLoginTests (WIP)
Tests login via advanced authentication hosts that use Chrome Custom Tabs instead of the in-app WebView. Skipped on API ≤ 31 in Firebase Test Lab due to outdated Chrome.

| Test | App Config | Scopes | Login Host |
|------|-----------|--------|------------|
| `testECAOpaque_DefaultScopes` | ECA Opaque | Default | Advanced Auth |

#### RefreshTokenMigrationTests
Tests the SDK's refresh token migration flow, which exchanges tokens when an app's OAuth configuration changes (e.g., scope upgrades or connected app changes). Validates that tokens are replaced and the new tokens are functional.

| Test | Description |
|------|-------------|
| `testMigrate_CA_AddMoreScopes` | Scope upgrade within the same CA JWT app |
| `testMigrate_ECA_AddMoreScopes` | Scope upgrade within the same ECA JWT app |
| `testMigrate_Beacon_AddMoreScopes` | Scope upgrade within the same Beacon JWT app |
| `testMigrate_CA_To_Beacon` | Migrate from CA Opaque to Beacon Opaque |
| `testMigrateBeacon_To_CA` | Migrate from Beacon Opaque to CA Opaque |
| `testMigrateCA_To_ECA` | Migrate CA → ECA → CA (with rollback) |
| `testMigrateCA_To_BeaconAndBack` | Migrate CA → Beacon → CA (with rollback) |
| `testMigrateBeaconOpaque_To_JWTAndBack` | Migrate Beacon Opaque → JWT → Opaque (with rollback) |

#### MultiUserLoginTests
End-to-end tests for multi-user scenarios: logging in two users, switching between them, and validating that each user's tokens and OAuth configuration are preserved independently.

| Test | Description |
|------|-------------|
| `testSameApp_SameScopes_uniqueTokens` | Two users on CA Opaque; validates unique tokens, user switching, and token refresh per user |
| `testSameApp_ECA_DifferentScopes` | Two users on ECA JWT with different scopes; validates scope isolation after switching |
| `testSameApp_Beacon_DifferentScopes` | Two users on Beacon Opaque with different scopes |
| `testFirstStatic_SecondDynamic_DifferentApps` | First user on boot config (CA), second on dynamic config (Beacon JWT) |
| `testFirstDynamic_SecondStatic_DifferentApps` | First user on dynamic config (ECA JWT), second on boot config (CA) |
| `testDifferentApps_differentScopes` | Two users on different apps with different scopes |
| `testMultiUser_tokenMigration` | Migrate one user's tokens while the other remains unaffected |
| `testMultiUser_tokenMigration_backgroundUser` | Migrate a background user's tokens; validate foreground user is unaffected and refresh works correctly post-switch |

#### WelcomeLoginTests (WIP)
Planned tests for welcome discovery login flows with both regular and advanced auth hosts, using static and dynamic configurations.

### Validation Per Test

Each `loginAndValidate` call performs the following checks:
1. **User identity** — username matches the expected test user
2. **OAuth values** — consumer key, scopes granted, and token format (opaque vs JWT) match the app configuration
3. **Token format** — opaque tokens are exactly 112 characters; JWT tokens exceed that length; refresh tokens are 87 characters
4. **API request** — a REST API call succeeds with the issued tokens

Migration tests additionally verify:
- Access and refresh tokens are **replaced** (not reused)
- A **token refresh** succeeds after revoking the new access token

Multi-user tests additionally verify:
- Tokens are **unique** across users
- **User switching** preserves each user's tokens and OAuth configuration
- **Token refresh** targets the correct user's app after switching

## Architecture

### Test Infrastructure

| Component | Description |
|-----------|-------------|
| `AuthFlowTest` | Abstract base class providing `loginAndValidate` and `migrateAndValidate` orchestration. Uses `ActivityScenarioRule` + `ComposeTestRule`. Assigns users based on API level to spread credential usage across Firebase Test Lab devices. |
| `UITestConfig` | Deserializes `ui_test_config.json` (from `shared/test/`) into typed enums: `KnownAppConfig`, `KnownLoginHostConfig`, `KnownUserConfig`, `ScopeSelection`. |

### Page Objects

| Page Object | Scope | Technology |
|------------|-------|------------|
| `BasePageObject` | Shared context and string resolution | Compose Test |
| `LoginPageObject` | Salesforce login WebView (username, password, login button, server picker, login options) | Espresso Web + Compose Test |
| `ChromeCustomTabPageObject` | Advanced auth login in Chrome Custom Tab (extends `LoginPageObject`) | UIAutomator |
| `LoginOptionsPageObject` | SDK Login Options screen (toggle web server flow, hybrid token, override boot config) | Compose Test |
| `AuthorizationPageObject` | OAuth "Allow" button handling after login or migration | UIAutomator |
| `AuthFlowTesterPageObject` | Main app screen (credentials, tokens, user switching, migration, API requests, revocation) | Compose Test + UIAutomator |

### Configuration

- **App configs** (`KnownAppConfig`): `ECA_OPAQUE`, `ECA_JWT`, `BEACON_OPAQUE`, `BEACON_JWT`, `CA_OPAQUE`, `CA_JWT`
- **Login hosts** (`KnownLoginHostConfig`): `REGULAR_AUTH` (in-app WebView), `ADVANCED_AUTH` (Chrome Custom Tab)
- **Scope options** (`ScopeSelection`): `EMPTY` (default/boot config scopes), `SUBSET` (all minus `sfap_api`), `ALL`
- **Users** (`KnownUserConfig`): `FIRST` through `FIFTH`, assigned per API level

> **Note:** A valid `shared/test/ui_test_config.json` file with login host URLs, user credentials, and app configurations is required. See `shared/test/ui_test_config.json.sample` for the expected format.

## Manual Testing

The app is also useful for hands-on exploration and debugging of the SDK's authentication flows. After logging in, the main screen exposes several interactive features.

### Login Options

Accessible from the login screen before authenticating:

1. Tap the **three-dot menu** (More Options) in the top bar
2. Tap **"Developer Support"**
3. Tap **"Login Options"**

The Login Options screen allows you to override the default boot config for the current login attempt:

- **Web Server Flow toggle** — enable or disable the web server OAuth flow (default: on). When off, the user agent flow is used.
- **Hybrid Auth Token toggle** — enable or disable hybrid authentication tokens (default: on).
- **Override Boot Config toggle** — when enabled, exposes fields to enter a custom **Consumer Key**, **Redirect URI**, and **Scopes** (space-separated). Tap **Save** to apply. This lets you test different app configurations (CA, ECA, Beacon) without rebuilding the app.

### Change Server

From the login screen:

1. Tap the **three-dot menu** → **"Change Server"**
2. Select a login host from the server picker bottom sheet

This switches between regular authentication (in-app WebView) and advanced authentication (Custom Tab) depending on the `.well-known` auth config of the host.

### Main Screen

The main screen shows expandable cards for the current user's data:

- **User Credentials** — expand to inspect identity (username, user ID, org ID), OAuth client configuration (client ID, login domain), tokens (access token, refresh token, format, scopes), URLs, community info, domains/SIDs, cookies/security, and beacon fields. Sensitive values are masked by default; tap a row to reveal the full value. Long-press any row to copy its value to the clipboard. Tap the share icon on a card header to export the full section as JSON.
- **JWT Details** — appears only when the current user has a JWT access token. Shows decoded header (algorithm, key ID, token type, version) and payload (audience, expiration, issuer, subject, scopes, client ID).
- **OAuth Configuration** — displays the currently configured boot config values: consumer key, callback URL, and scopes.

### Bottom Bar Actions

The bottom bar provides three actions:

- **Migrate Access Token** (key icon) — opens the token migration bottom sheet (see below).
- **Switch User** (person-add icon) — opens the SDK's account picker. Select an existing user to switch, or tap "Add New Account" to log in as a second user.
- **Logout** (logout icon) — presents a confirmation dialog, then logs out the current user via `SalesforceSDKManager.logout()`.

### Revoke Access Token

Tap **"Revoke Access Token"** to POST to `/services/oauth2/revoke` with the current access token. A dialog confirms success or failure. After revoking, make a REST API request to trigger the SDK's automatic token refresh.

### Make REST API Request

Tap **"Make REST API Request"** to send a lightweight REST request using the current tokens. A dialog shows success or failure, and an expandable "Response Details" section displays the full JSON response.

### Token Migration

The token migration sheet allows you to exchange a user's refresh token for a new one under a different connected app configuration.

1. Tap the **key icon** in the bottom bar
2. If multiple users are logged in, select the target user via the radio buttons
3. Enter the new app's **Consumer Key**, **Callback URL**, and optionally **Scopes**
   - Alternatively, tap the **JSON import icon** (top-right of the sheet) to paste a JSON object with keys `remoteConsumerKey`, `oauthRedirectURI`, and `oauthScopes`. The dialog auto-populates from the clipboard.
4. Tap **"Migrate Refresh Token"**
5. If the server requires authorization, tap **Allow** on the OAuth approval page
6. On success, the sheet dismisses and the main screen refreshes with the new tokens

After migration, verify the new configuration by expanding the User Credentials card to check the updated client ID, scopes, and token format.

