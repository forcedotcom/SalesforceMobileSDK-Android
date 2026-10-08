# Login Feature-Flag Persistence Boundary

The screen-lock feature flag may be temporarily in memory before it is written to `AccountManager` while a login is in progress. This document specifies the screen-lock marker only; other feature markers retain their own flow-specific timing.

Before the SDK publishes the newly authenticated account as the current user, the screen-lock feature write must complete successfully. In particular, the SDK must not hand the app a REST client, send the user-switch notification, invoke the authentication-success callback, or launch the completed login flow while that write is pending.

Feature writes must be awaited and must not be fire-and-forget. Work that calls `AccountManager` must not run on the main thread when it can block on binder IPC.
