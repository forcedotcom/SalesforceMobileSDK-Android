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
Connected App login tests with explicit scope selection across web server and user agent flows, both hybrid and non-hybrid. Includes in-app WebView variants (`forceAdvancedAuthentication = false`) that exercise the legacy WKWebView path.

| Test | Scopes | Flow | Auth Surface |
|------|--------|------|--------------|
| `testCAOpaque_SubsetScopes_WebServerFlow` | Subset | Web Server | Browser (default) |
| `testCAOpaque_AllScopes_WebServerFlow` | All | Web Server | Browser (default) |
| `testCAOpaque_DefaultScopes_WebServerFlow_InAppWebView` | Default | Web Server | In-App WebView |
| `testCAOpaque_SubsetScopes_WebServerFlow_InAppWebView` | Subset | Web Server | In-App WebView |
| `testCAOpaque_AllScopes_WebServerFlow_InAppWebView` | All | Web Server | In-App WebView |
| `testCAOpaque_SubsetScopes_WebServerFlow_NotHybrid` | Subset | Web Server | Browser (default) |
| `testCAOpaque_AllScopes_WebServerFlow_NotHybrid` | All | Web Server | Browser (default) |
| `testCAOpaque_SubsetScopes_UserAgentFlow` | Subset | User Agent | In-App WebView |
| `testCAOpaque_AllScopes_UserAgentFlow` | All | User Agent | In-App WebView |
| `testCAOpaque_SubsetScopes_UserAgentFlow_NotHybrid` | Subset | User Agent | In-App WebView |
| `testCAOpaque_AllScopes_UserAgentFlow_NotHybrid` | All | User Agent | In-App WebView |

#### ECALoginTests
External Client App (ECA) login tests for both opaque and JWT token formats with scope variations. Also covers pool server login (non-DPoP).

| Test | App Config | Scopes | Notes |
|------|-----------|--------|-------|
| `testECAOpaque_DefaultScopes` | ECA Opaque | Default | |
| `testECAOpaque_SubsetScopes` | ECA Opaque | Subset | |
| `testECAOpaque_AllScopes` | ECA Opaque | All | |
| `testECAJwt_DefaultScopes` | ECA JWT | Default | |
| `testECAJwt_SubsetScopes_NotHybrid` | ECA JWT | Subset | |
| `testECAJwt_AllScopes` | ECA JWT | All | |
| `testECAJwt_ViaLoginPoolServer` | ECA JWT | — | Pool server login without DPoP |

#### DPoPLoginTests
All DPoP tests live here — basic login, RTR, multi-user, migration, server enforcement, upgrade, restart, pool server, and admin login. Verifies that DPoP-bound access tokens are issued (`token_type: "DPoP"`), API calls succeed with `ath`-bound proofs, the access token refreshes correctly, and the DPoP nonce rotates on every `/token` response. As of Mobile SDK 14, DPoP defaults **on** for new logins (`SalesforceSDKManager.useDPoP` defaults to `true`), so `cleanup()` resets it to `true` after each test; turning it off is the explicit Bearer compatibility path (see `LegacyLoginTests`). Tests that need a specific posture set it via `LoginOptions` before each login. All DPoP tests use the `regular_auth` login host (sdb38) — DPoP is an ECA property, not an org property.

| Test | App Config | Hybrid | Notes |
|------|-----------|--------|-------|
| `testECAJwtDPoP_Hybrid` | ECA JWT DPoP | Yes | |
| `testECAJwtDPoP_NoHybrid` | ECA JWT DPoP | No | |
| `testECAJwtDPoPRtr_Hybrid` | ECA JWT DPoP RTR | Yes | `@Ignore` (W-22512846 — server does not yet support Named JWTs for Hybrid Flows) |
| `testECAJwtDPoPRtr_NoHybrid` | ECA JWT DPoP RTR | No | DPoP + refresh token rotation |
| `testECAJwtDPoP_MultiUser_UniqueTokens` | ECA JWT DPoP | — | Two users; unique tokens; independent revoke+refresh per user |
| `testECAJwtDPoP_And_NonDPoP_MultiUser_FlagOff_IndependentProofs` | ECA JWT DPoP + ECA JWT | — | DPoP and non-DPoP users coexist; toggling DPoP off for second user does not affect first |
| `testMigrate_ECAJwtDPoP_AddMoreScopes` | ECA JWT DPoP | — | Scope upgrade; DPoP binding preserved |
| `testMigrate_ECAJwtDPoP_To_ECAJwtDPoPRtr` | ECA JWT DPoP → ECA JWT DPoP RTR | — | Migrate from DPoP to DPoP+RTR |
| `testLogin_DPoP_ECA_Without_DPoP_Fails` | ECA JWT DPoP | — | Server enforcement: DPoP-enforced ECA rejects login without DPoP (`useDPoP=false`); no account created |
| `testUpgrade_NonDPoP_InPlace_ToDPoP` | ECA JWT → ECA JWT DPoP | — | Bearer → DPoP in-place upgrade; global `useDPoP` flag remains off; per-call `dpopOverride` triggers upgrade |
| `testECAJwtDPoP_WithRestart` | ECA JWT DPoP | — | DPoP EC key pair survives process restart (AndroidKeyStore) |
| `testECAJwtDPoP_ViaLoginPoolServer` | ECA JWT DPoP | — | `@Ignore` (W-23864247 — pool login server rejects valid `dpop_jkt` token exchange) |
| `testLoginForAdmin_DPoP` | ECA JWT DPoP | — | Login for Admins hand-off to Custom Tab works with DPoP |

#### RTRLoginTests
Tests for ECA configurations with Refresh Token Rotation (RTR) enabled. Verifies that the refresh token rotates on each token refresh cycle. The `assertRevokeAndRefreshWorks` check asserts the refresh token **changes** after a revoke/refresh cycle for RTR apps. DPoP+RTR tests live in `DPoPLoginTests`.

| Test | App Config | Hybrid | Notes |
|------|-----------|--------|-------|
| `testECAJwtRtr_Hybrid` | ECA JWT RTR | Yes | `@Ignore` (W-22512846 — server does not yet support Named JWTs for Hybrid Flows) |
| `testECAJwtRtr_NoHybrid` | ECA JWT RTR | No | |
| `testECAOpaqueRtr_Hybrid` | ECA Opaque RTR | Yes | |
| `testECAOpaqueRtr_NoHybrid` | ECA Opaque RTR | No | |

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

#### AdvancedAuthBeaconLoginTests
Tests for Beacon app login flows using advanced authentication with Chrome Custom Tabs. This class runs the same tests as BeaconLoginTests but uses the advanced_auth login host. Requires intent filters in AndroidManifest.xml matching the beacon redirect URIs (`beaconadvancedopaque://success/done` and `beaconadvancedjwt://success/done`). Each test validates that the B4 marker (`forceAdvancedAuthentication`) is present in the `ftr_` user-agent segment.

| Test | App Config | Scopes | Login Host |
|------|-----------|--------|------------|
| `testBeaconOpaque_DefaultScopes` | Beacon Opaque | Default | Advanced Auth |
| `testBeaconOpaque_SubsetScopes` | Beacon Opaque | Subset | Advanced Auth |
| `testBeaconOpaque_AllScopes` | Beacon Opaque | All | Advanced Auth |
| `testBeaconJwt_DefaultScopes` | Beacon JWT | Default | Advanced Auth |
| `testBeaconJwt_SubsetScopes` | Beacon JWT | Subset | Advanced Auth |
| `testBeaconJwt_AllScopes` | Beacon JWT | All | Advanced Auth |

#### LoginForAdminTests
Tests for the "Login for Admins" menu flow, which launches OAuth in a Chrome Custom Tab while the in-app WebView remains loaded. Intended for orgs requiring browser-based admin sign-in (client certificates, SSO) even when the app uses the in-app WebView. Always uses Web Server Flow + PKCE. The DPoP variant lives in `DPoPLoginTests`.

| Test | WebView Flow | Description |
|------|-------------|-------------|
| `testLoginForAdmin_WebServerFlowEnabled` | Web Server | Custom tab URL matches WebView URL |
| `testLoginForAdmin_WebServerFlowDisabled` | User Agent | Custom tab forces Web Server Flow despite WebView config |

#### NegativeLoginTests
Negative-path tests for runtime consumer-key/dynamic-config selection. Covers invalid consumer keys, invalid scopes, and dynamic-config changes that the user does not commit by logging in.

| Test | Description |
|------|-------------|
| `testInvalidConsumerKey_loginFails` | Dynamic config with invalid consumer key; login must fail and no account created |
| `testInvalidScope_loginFails` | Dynamic config with invalid scope; login must fail and no account created |
| `testChangeDynamicConfigWithoutLogin_existingUserUnaffected` | Change dynamic config without login; existing user's tokens and config remain intact |

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
| `testMigrateCAUserAgent_To_ECAExtendedWebServer` | Migrate CA (user agent flow) → ECA with extended scopes; migration always uses web server flow internally |
| `testMigrateCAUserAgent_To_BeaconExtendedWebServer` | Migrate CA (user agent flow) → Beacon with extended scopes; migration always uses web server flow internally |

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
| `testMultiUser_revokeOtherUserRefreshToken` | Revoke secondary user's refresh token server-side; SDK logs that user out on next refresh, primary user unaffected |
| `testAdvancedAuthUser_HasBWFlag_RegularAuthUser_DoesNot` | One user on advanced auth (BW flag set), one on regular auth; validates per-user BW flag isolation after switching |
| `testFlagDiversity_NonHybridOpaqueVsHybridJwt` | User A: non-hybrid+OT; User B: hybrid+JT. Validates A-marker and token-format isolation across user switches and after User B logout. |
| `testFlagDiversity_BeaconNonHybridJwtVsHybridOpaque` | User A: beacon+non-hybrid+JT; User B: hybrid+OT, no beacon. Three flags differ; any single per-user flag leak is detectable on at least two axes. |
| `testFlagDiversity_WebServerNonHybridOpaque_vs_WebServerHybridJwt` | User A: A1+OT; User B: A2+JT. Detects A-marker and token-format leakage across user switches. |
| `testFlagDiversity_WebServerHybridBeaconJwt_vs_WebServerNonHybridOpaque` | User A: A2+JT+BN (beacon); User B: A1+OT. Maximum orthogonality — all three axes (A-marker, token format, beacon) differ. |
| `testFlagDiversity_WebServerHybridBeaconOpaque_vs_WebServerNonHybridOpaque` | User A: A2+OT+BN (beacon); User B: A1+OT. Tests A-marker and BN leakage; both users use web server flow. |

### Multi-user flag leakage detection tests

These tests exist to catch bugs where a feature flag registered for one user bleeds into another user's `ftr_` segment. They are designed so that any single leaked flag produces a detectable mismatch on at least one assertion axis (A-marker, token format, or beacon child key).

#### How per-user flag storage works

The SDK stores feature flags keyed by `orgId/userId` in `SalesforceSDKManager.perUserFeatures`. On each user switch the user-agent string is recomputed as `global features + perUserFeatures[orgId/userId]` for the now-current user. A leak can occur if:

- A flag is registered without a user argument (goes to global `features` instead of per-user).
- The `perUserFeatures` key for two test accounts resolves to the same string (same Salesforce user reached via different auth domains).
- A Login Options toggle (web server flow, hybrid auth token) is not explicitly reset between sequential `loginAndValidate` calls.

#### Test details

##### `testFlagDiversity_NonHybridOpaqueVsHybridJwt`

| | User A | User B |
|---|---|---|
| **App config** | CA Opaque | ECA JWT |
| **Flow** | Web server, non-hybrid | Web server, hybrid (default) |
| **Expected A-marker** | A1 (`WEB_SERVER_NON_HYBRID`) | A2 (`WEB_SERVER_HYBRID`) |
| **Token format** | OT (opaque) | JT (JWT) |
| **Beacon child** | No | No |

After User B logs out, User A is re-validated alone (no MU flag) to confirm multi-user removal does not corrupt the remaining user's flags.

##### `testFlagDiversity_BeaconNonHybridJwtVsHybridOpaque`

| | User A | User B |
|---|---|---|
| **App config** | Beacon JWT | ECA Opaque |
| **Flow** | Web server, non-hybrid | Web server, hybrid (default) |
| **Expected A-marker** | A1 (`WEB_SERVER_NON_HYBRID`) | A2 (`WEB_SERVER_HYBRID`) |
| **Token format** | JT (JWT) | OT (opaque) |
| **Beacon child** | Yes (BN) | No |

> **Why ECA Opaque and not CA Opaque for User B?** `CA_OPAQUE` is the boot-config app; `needsLoginOptions` is `false` for it, so Login Options are skipped and the HybridAuthToken toggle (left off by User A's non-hybrid login) is never re-enabled. Using `ECA_OPAQUE` forces `needsLoginOptions = true`, which explicitly re-enables the toggle before User B logs in.

After User B logs out, User A is re-validated alone to confirm A1+JT+BN and no MU flag.

##### `testFlagDiversity_WebServerNonHybridOpaque_vs_WebServerHybridJwt`

| | User A | User B |
|---|---|---|
| **App config** | ECA Opaque | ECA JWT |
| **Flow** | Web server, non-hybrid | Web server, hybrid (default) |
| **Expected A-marker** | A1 (`WEB_SERVER_NON_HYBRID`) | A2 (`WEB_SERVER_HYBRID`) |
| **Token format** | OT (opaque) | JT (JWT) |
| **Beacon child** | No | No |

##### `testFlagDiversity_WebServerHybridBeaconJwt_vs_WebServerNonHybridOpaque`

| | User A | User B |
|---|---|---|
| **App config** | Beacon JWT | ECA Opaque |
| **Flow** | Web server, hybrid (default) | Web server, non-hybrid |
| **Expected A-marker** | A2 (`WEB_SERVER_HYBRID`) | A1 (`WEB_SERVER_NON_HYBRID`) |
| **Token format** | JT (JWT) | OT (opaque) |
| **Beacon child** | Yes (BN) | No |

Maximum orthogonality — A-marker, token format, and beacon all differ simultaneously.

##### `testFlagDiversity_WebServerHybridBeaconOpaque_vs_WebServerNonHybridOpaque`

| | User A | User B |
|---|---|---|
| **App config** | Beacon Opaque | ECA Opaque |
| **Flow** | Web server, hybrid (default) | Web server, non-hybrid |
| **Expected A-marker** | A2 (`WEB_SERVER_HYBRID`) | A1 (`WEB_SERVER_NON_HYBRID`) |
| **Token format** | OT (opaque) | OT (opaque) |
| **Beacon child** | Yes (BN) | No |

Both users use web server flow; the beacon vs non-beacon dimension is what generates the detectable BN/A-marker difference.

#### WelcomeLoginTests
Tests for the Welcome Discovery login flow. Uses the SDK's Login Options "Discovery Result Editor" to inject a simulated discovery result (login hint + My Domain), then drives the same code path the real callback URL would have produced.

| Test | Login Host | App Config |
|------|-----------|-----------|
| `testWelcomeDiscovery_RegularAuthLoginHost` | Regular Auth | ECA Opaque |
| `testWelcomeDiscovery_AdvancedAuthLoginHost` | Advanced Auth | Beacon Opaque |

#### LoginWithRestartTests
Tests that user sessions and per-user feature flags persist across a cold app restart. Each test logs in, kills the app process (leaving the instrumentation runner alive), relaunches the app, and verifies that both session credentials and user-agent feature flags are reloaded correctly from disk. Feature flags tested: BW (browser-based / advanced auth) and WD (welcome discovery). The DPoP restart test lives in `DPoPLoginTests`.

| Test | App Config | Scopes | Config Type | Feature Flag |
|------|-----------|--------|-------------|--------------|
| `testCAOpaque_DefaultScopes_WithRestart` | CA Opaque | Default | Static | — |
| `testECAOpaque_DefaultScopes_WithRestart` | ECA Opaque | Default | Static | — |
| `testBeaconOpaque_DefaultScopes_WithRestart` | Beacon Opaque | Default | Static | — |
| `testECAJwt_DefaultScopes_DynamicConfiguration_WithRestart` | ECA JWT | Default | Dynamic | — |
| `testECAJwt_SubsetScopes_DynamicConfiguration_WithRestart` | ECA JWT | Subset | Dynamic | — |
| `testBeaconJwt_DefaultScopes_DynamicConfiguration_WithRestart` | Beacon JWT | Default | Dynamic | — |
| `testBeaconJwt_SubsetScopes_DynamicConfiguration_WithRestart` | Beacon JWT | Subset | Dynamic | — |
| `testAdvancedAuth_WithRestart` | Beacon Opaque | Default | Static | BW |
| `testWelcomeDiscovery_WithRestart` | ECA Opaque | Default | Static | WD |
| `testMultiUserRestart` | ECA Opaque + ECA JWT | Default | Mixed | — |

### Validation Per Test

Each `loginAndValidate` call performs the following checks:
1. **User identity** — username matches the expected test user
2. **OAuth values** — consumer key, scopes granted, and token format (opaque vs JWT) match the app configuration
3. **Token format** — opaque tokens are exactly 112 characters; JWT tokens exceed that length; refresh tokens are 87 characters
4. **API request** — a REST API call succeeds with the issued tokens
5. **DPoP (DPoP apps only)** — `OAuth Token Type` is `"DPoP"` and the DPoP nonce is non-empty after login
6. **Browser-login B-marker** — the `ftr_` segment of the user-agent string contains exactly one B-marker (B1–B4) when browser-based login was used, and none when it was not. See [B-marker semantics](#b-and-l-markers-in-ftr_) below.
7. **Login-server L-marker** — the `ftr_` segment contains exactly one L-marker (L1–L5) on every non-refresh login. See [L-marker semantics](#b-and-l-markers-in-ftr_) below.

`assertRevokeAndRefreshWorks` additionally verifies for DPoP apps:
- **Token type preserved** — `OAuth Token Type` remains `"DPoP"` after refresh
- **Nonce rotated** — the DPoP nonce changes after each token refresh cycle (server issues a new nonce with each `/token` response), proving the server processed the DPoP proof and did not silently ignore the header

Migration tests additionally verify:
- Access and refresh tokens are **replaced** (not reused)
- A **token refresh** succeeds after revoking the new access token

Multi-user tests additionally verify:
- Tokens are **unique** across users
- **User switching** preserves each user's tokens and OAuth configuration
- **Token refresh** targets the correct user's app after switching

Restart tests additionally verify:
- Session credentials are **reloaded from disk** after a cold process restart
- Per-user feature flags (BW, WD, B-markers, L-markers) encoded in the user agent string **persist** across restarts via `hydratePerUserFeatures()`
- DPoP EC key pairs stored in **AndroidKeyStore** survive a process kill and restart

### B- and L-markers in `ftr_`

The `ftr_` user-agent segment encodes per-user telemetry codes. Two code families are validated by `validateUserAgent`:

#### B-markers — why browser login was used

Registered once per user alongside the BW (browser-windows) flag. Exactly one is set when browser-based login occurred; none are set for in-app WebView login.

| Code | Constant | Meaning | Priority |
|------|----------|---------|----------|
| B1 | `FEATURE_BROWSER_LOGIN_SERVER_AUTH_CONFIG` | Server auth-config required browser login | Lowest |
| B3 | `FEATURE_BROWSER_LOGIN_FOR_ADMIN` | "Login for Admin" flow used | Highest |
| B4 | `FEATURE_BROWSER_LOGIN_FORCE_FLAG` | `SalesforceSDKManager.forceAdvancedAuthentication` was set | — |

> **Note:** B2 (MDM-required browser auth) is iOS-only and is never registered on Android.

Priority order when multiple reasons apply: **B3 > B4 > B1**.

#### L-markers — which login server type was used

Registered on every non-refresh login. Exactly one is set per login.

| Code | Constant | Meaning |
|------|----------|---------|
| L1 | `FEATURE_LOGIN_SERVER_PRODUCTION` | Production pool server (`login.salesforce.com` and internal pool equivalents) |
| L2 | `FEATURE_LOGIN_SERVER_SANDBOX` | Sandbox (`test.salesforce.com`) |
| L3 | `FEATURE_LOGIN_SERVER_WELCOME_DISCOVERY` | Welcome Discovery flow was used |
| L4 | `FEATURE_LOGIN_SERVER_MY_DOMAIN` | My Domain org (`.my.` in the host) |
| L5 | `FEATURE_LOGIN_SERVER_OTHER` | Any other login server |

L3 takes priority over the resolved domain: even if Welcome Discovery resolves to a My Domain org, the final L-marker is L3 (captured before the WD global flag is cleared).

## Architecture

### Test Infrastructure

| Component | Description |
|-----------|-------------|
| `AuthFlowTest` | Abstract base class providing `loginAndValidate`, `migrateAndValidate`, and `restartAndValidateUser` orchestration. Also exposes `restartApp()` (kills only the app process via `pidof` filtered by `myPid`, then relaunches), `addOtherUserAndValidate()`, and `switchToUserAndValidateUser()` helpers for restart and multi-user flows. Uses `ActivityScenarioRule` + `ComposeTestRule`. Assigns users based on API level to spread credential usage across Firebase Test Lab devices. |
| `UITestConfig` | Deserializes `ui_test_config.json` (from `shared/test/`) into typed enums: `KnownAppConfig`, `KnownLoginHostConfig`, `KnownUserConfig`, `ScopeSelection`. |

### Page Objects

| Page Object | Scope | Technology |
|------------|-------|------------|
| `BasePageObject` | Shared context and string resolution | Compose Test |
| `LoginPageObject` | Salesforce login WebView (username, password, login button, server picker, login options) | Espresso Web + Compose Test |
| `ChromeCustomTabPageObject` | Advanced auth login in Chrome Custom Tab (extends `LoginPageObject`) | UIAutomator |
| `LoginOptionsPageObject` | SDK Login Options screen (toggle web server flow, hybrid token, DPoP, override boot config) | Compose Test |
| `AuthorizationPageObject` | OAuth "Allow" button handling after login or migration | UIAutomator |
| `AuthFlowTesterPageObject` | Main app screen (credentials, tokens, user switching, migration, API requests, revocation) | Compose Test + UIAutomator |

### Configuration

- **App configs** (`KnownAppConfig`): `ECA_OPAQUE`, `ECA_JWT`, `ECA_OPAQUE_RTR`, `ECA_JWT_RTR`, `ECA_JWT_DPOP`, `ECA_JWT_DPOP_RTR`, `BEACON_OPAQUE`, `BEACON_JWT`, `CA_OPAQUE`, `CA_JWT`
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
- **Use DPoP toggle** — enable or disable DPoP (Demonstrating Proof of Possession) for the current login attempt. As of Mobile SDK 14 this defaults **on** (the toggle initializes from `SalesforceSDKManager.useDPoP`, which now defaults to `true`); turn it off for the Bearer compatibility path. When on, the SDK generates an EC P-256 key pair in AndroidKeyStore and attaches DPoP proof JWTs at token exchange and on every API call. Only meaningful when logging in with a DPoP-enabled ECA.
- **Discovery Result Editor toggle** — when enabled, exposes fields to simulate a Welcome Discovery result by entering a **Login Host** and **Username**. Tap **Save** to arm the simulated discovery result for the next login attempt. This simulates receiving a discovery callback without requiring email verification.

### Change Server

From the login screen:

1. Tap the **three-dot menu** → **"Change Server"**
2. Select a login host from the server picker bottom sheet

This switches between regular authentication (in-app WebView) and advanced authentication (Custom Tab) depending on the `.well-known` auth config of the host.

### Main Screen

The main screen shows expandable cards for the current user's data:

- **User Credentials** — expand to inspect identity (username, user ID, org ID), OAuth client configuration (client ID, login domain), tokens (access token, refresh token, format, OAuth token type, scopes), URLs, community info, domains/SIDs, cookies/security, beacon fields, and a **DPoP** section (shown only for DPoP sessions) with the current server-issued DPoP nonce. Sensitive values are masked by default; tap a row to reveal the full value. Long-press any row to copy its value to the clipboard. Tap the share icon on a card header to export the full section as JSON.
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

