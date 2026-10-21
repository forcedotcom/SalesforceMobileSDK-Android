# Login Feature-Flag Persistence Boundary

The screen-lock feature flag may be temporarily in memory before it is written to `AccountManager` while a login is in progress. The first section specifies the screen-lock marker; native DP-marker timing is described separately below. Other feature markers retain their own flow-specific timing.

Before the SDK publishes the newly authenticated account as the current user, the screen-lock feature write must complete successfully. In particular, the SDK must not hand the app a REST client, send the user-switch notification, invoke the authentication-success callback, or launch the completed login flow while that write is pending.

Feature writes must be awaited and must not be fire-and-forget. Account persistence and screen-lock policy cleanup must not run on the main thread when they can block on `AccountManager` binder IPC. The screen-lock UI is launched on the main thread only after background persistence completes.

## Native Login DP-marker persistence

Native Login bypasses `LoginActivity.onAuthFlowSuccess()`. After account persistence,
`AuthenticationUtilities.onAuthFlowComplete()` handles `nativeLogin && !tokenMigration` by
registering `FEATURE_DPOP` for an actual DPoP token or removing it for a non-DPoP token.
This occurs after `sendUserSwitchIntent()` and before the eventual authentication-success
callback; it is not a guarantee that every user-switch observer sees DP synchronized already.
It does not move the screen-lock persistence boundary described above.

Same-user reauthentication can replace the account snapshot while its per-user feature cache
is already populated. Public `registerUsedAppFeature` / `unregisterUsedAppFeature` intentionally
skip persistence for unchanged membership. The native path therefore calls the internal
`synchronizeUserFeatureFlags(account)` afterward: compare the snapshot with the final cached
set and persist the complete set when different. Repeated DPoP → DPoP login must retain the
persisted DP marker; DPoP → Bearer reauthentication must remove it without losing other cached
markers. Public no-op registration behavior remains unchanged.

The cache key is org ID plus user ID. `persistUserFeatureFlags` copies the set onto the
snapshot and updates AccountManager; startup hydration reads persisted sets. The native
completion mutex serializes this work with native supersession/fallback invalidation.
Regression coverage lives in `NativeLoginManagerTest` (real same-user account replacement)
and `SalesforceSDKManagerTests` (snapshot synchronization and unchanged-write suppression).
