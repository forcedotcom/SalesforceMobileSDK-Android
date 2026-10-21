# Login Feature-Flag Persistence Boundary

The screen-lock feature flag may be temporarily in memory before it is written to `AccountManager` while a login is in progress. The first section specifies the screen-lock marker; native DP-marker timing is described separately below. Other feature markers retain their own flow-specific timing.

Before the SDK publishes the newly authenticated account as the current user, the screen-lock feature write must complete successfully. In particular, the SDK must not hand the app a REST client, send the user-switch notification, invoke the authentication-success callback, or launch the completed login flow while that write is pending.

Feature writes must be awaited and must not be fire-and-forget. Account persistence and screen-lock policy cleanup must not run on the main thread when they can block on `AccountManager` binder IPC. The screen-lock UI is launched on the main thread only after background persistence completes.

## Native Login DP-marker persistence

Native Login persists DP from the resulting account's token type after account persistence
and the user-switch intent, but before the authentication-success callback. This is separate
from the screen-lock marker's pre-notification boundary above.

Same-user reauthentication must preserve DP for a DPoP session and remove it for a Bearer
session, including when a replacement account snapshot needs its feature flags synchronized.
See the [Native Login lifecycle](token-lifecycle.md#11-native-login-code-exchange) for the
completion path and synchronization mechanism.
