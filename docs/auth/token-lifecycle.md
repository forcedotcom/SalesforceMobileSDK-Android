# Token Lifecycle: Code Exchange, Refresh, DPoP, and RTR

This document describes how the Android SDK acquires and renews OAuth tokens, how DPoP
proof JWTs are attached, how nonces are managed, and how Refresh Token Rotation (RTR) is
handled safely under concurrency.

The primary classes are:
- **`OAuth2.java`** — all calls to the Salesforce token and identity endpoints
- **`ClientManager.java`** (`AccMgrAuthTokenProvider`) — RTR-safe token refresh coordination
- **`RestClient.java`** (`OAuthRefreshInterceptor`) — per-request auth header attachment and
  automatic refresh on 401
- **`DPoPProofBuilder.kt`**, **`DPoPKeyManager.kt`**, **`DPoPURLHelper.kt`**,
  **`DPoPNonceCache.kt`** — DPoP proof generation and nonce caching

---

## 1. Code Exchange (initial login)

**Entry point:** `OAuth2.exchangeCode()`

After the user completes the OAuth2 authorization code flow in the login WebView,
`LoginActivity` calls `exchangeCode()` with the authorization `code` and `code_verifier`
(PKCE). This method:

1. Builds a `POST /services/oauth2/token` form body with `grant_type=authorization_code`
   (or `hybrid_auth_code` when hybrid authentication is enabled).
2. If `isUseDPoP()` is `true` and a `credentialsIdentifier` was generated at login start:
   - Looks up (or generates) the EC keypair via `DPoPKeyManager.generateOrLoadKeyPair()`.
   - Proactively reads any cached nonce via `DPoPNonceCache.get(credentialsIdentifier, tokenHost)` — null on first ever login.
   - Builds a DPoP proof JWT via `DPoPProofBuilder.buildProof()` and attaches it as the `DPoP` header.
3. Sends the request via `makeTokenEndpointRequest()` (shared with refresh).
4. On success: the response carries an `access_token`, `refresh_token`, and `token_type`
   (`"DPoP"` or `"Bearer"`). A `DPoP-Nonce` header may also be present — it is harvested
   into the cache immediately.
5. On `use_dpop_nonce` (400/401 with that error body): the nonce was just harvested (step 4);
   rebuild the proof with it and retry once. Fail-closed on a second nonce failure.
6. `TokenEndpointResponse` is returned to `LoginActivity`, which persists the tokens into
   `AccountManager` and calls `callIdentityService()` to fetch the user's identity record.

**DPoP note:** the `credentialsIdentifier` is a UUID generated at login start and stored on
`UserAccount` as the `dpopScope` AccountManager extra. It is the stable key for the DPoP
keypair and nonce cache for this user's session.

---

## 2. Token Refresh

**Entry point:** `OAuth2.refreshAuthToken()`

When an API call returns 401, `OAuthRefreshInterceptor` calls `refreshAccessToken()` which
delegates to `AccMgrAuthTokenProvider.getNewAuthToken()`. That method:

1. **Matches the account** by scanning `AccountManager` for the one whose stored refresh token
   equals this provider's `refreshToken`. Fails early (returns null) if the account has been
   removed — this ensures a removed-account path never accidentally sets `refreshing = true`
   and deadlocks waiting threads.

2. **Acquires the RTR lock** — see §4 below.

3. **Recheck-under-lock guardrail:** before making a network call, re-reads the current tokens
   from `AccountManager`. If either the access token or the refresh token in storage has
   already advanced past what this provider last used, a concurrent winner already refreshed —
   adopt their result without a redundant network POST. This is a correctness guardrail under
   RTR, not an optimisation: every needless POST rotates the refresh token and widens the
   stale-token logout window.

4. **`refreshStaleToken()`** → **`OAuth2.refreshAuthToken()`** builds a
   `POST /services/oauth2/token` form body with `grant_type=refresh_token`. DPoP proof
   and nonce handling here are identical to code exchange: proactive nonce inclusion,
   harvest from response, retry-once on `use_dpop_nonce`.

5. On success: broadcasts `ACCESS_TOKEN_REFRESH_INTENT` (or `INSTANCE_URL_UPDATE_INTENT` if
   the instance URL changed), publishes the new tokens to the per-account `RefreshState`,
   and wakes waiting losers.

6. On terminal failure (`invalid_grant`, `client_blocked`): broadcasts
   `ACCESS_TOKEN_REVOKE_INTENT` and calls `SalesforceSDKManager.logout()`.

---

## 3. DPoP Proof Attachment

Every outgoing API call goes through `OAuthRefreshInterceptor.buildAuthenticatedRequest()`,
which calls `attachDPoPProofIfNeeded()`:

```
attachDPoPProofIfNeeded(builder, method, url):
  if tokenType != "DPoP"  →  return (Bearer path, unmodified)
  if !isUseDPoP()         →  return
  if credentialsIdentifier == null  →  return
  htu    = DPoPURLHelper.canonicalize(url)   // strips query + fragment
  host   = HttpUrl.get(url).host()
  alias  = DPoPKeyManager.aliasForCredentialsIdentifier(credentialsIdentifier)
  keyPair = DPoPKeyManager.generateOrLoadKeyPair(alias)
  nonce  = DPoPNonceCache.get(credentialsIdentifier, host)  // null until token exchange completes
  proof  = DPoPProofBuilder.buildProof(method, htu, keyPair, nonce, authToken)
  builder.header("DPoP", proof)
```

`setAuthHeader()` also sets `Authorization: DPoP <accessToken>` (instead of Bearer) when
`tokenType == "DPoP"`.

**The interceptor never writes to `DPoPNonceCache`** — it only reads. All cache writes are
done by `OAuth2.java` after token-endpoint and identity-endpoint responses.

---

## 4. Refresh Token Rotation (RTR) — Concurrency Model

RTR means the server issues a new refresh token on every refresh. The old one is immediately
invalidated. Two threads simultaneously refreshing with the same refresh token will cause one
to receive `invalid_grant` → logout. `AccMgrAuthTokenProvider.getNewAuthToken()` prevents
this with a per-account coordination primitive.

### State

```java
// One entry per (userId:orgId); survives across getNewAuthToken() calls.
static final ConcurrentHashMap<String, RefreshState> REFRESH_STATES

class RefreshState {
    final Object lock             // coordination primitive
    boolean refreshing            // true while winner is in-flight
    long publishGeneration        // incremented only on successful publish
    String newAuthToken           // last successfully refreshed token
    String newInstanceUrl
    String rotatedRefreshToken    // refresh token as rotated by the last winner
    String newTokenType
    long lastRefreshTime          // wall-clock time of last successful publish
}
```

### Flow

```
Thread A                                    Thread B (same account)
──────────────────────────────────────────────────────────────────
synchronized(state.lock)
  state.refreshing == false
  → become winner
  state.refreshing = true
lock released
                                            synchronized(state.lock)
                                              state.refreshing == true
                                              snapshot startGeneration
                                              state.lock.wait(...)   ← parked
                                            lock released (by wait)

refreshStaleToken()
  → OAuth2.refreshAuthToken()
  → POST /token  (DPoP proof + nonce)
  → 400 use_dpop_nonce  → cache nonce → retry
  → 200: new access_token, rotated refresh_token
broadcast ACCESS_TOKEN_REFRESH_INTENT

synchronized(state.lock)
  state.refreshing = false
  state.newAuthToken = <new>
  state.publishGeneration++          ← edge: B detects this
  state.lock.notifyAll()
lock released
                                            Thread B wakes
                                            synchronized(state.lock)
                                              publishGeneration changed → adopt
                                            lock released
                                            return state.newAuthToken  (no /token call)
```

### Key design decisions

**Election on a generation edge, not a level.** Losers snapshot `publishGeneration` before
parking and wake when it advances — not when `refreshing` becomes `false`. This handles the
consecutive-cycle race: if a new winner has already re-set `refreshing = true` by the time a
loser wakes, the loser still detects the prior winner's publish via the generation edge and
adopts that result correctly.

**Loser timeout.** Losers wait at most 30 s. If the winner hasn't published by then, the loser
returns `null` rather than attempting a second concurrent refresh. The caller's request fails
and can retry; the in-flight winner (if merely slow) still completes normally.

**Recheck-under-lock guardrail.** Before making a network call the winner re-reads
`AccountManager`. If storage has already advanced (a prior winner refreshed), the winner
adopts without posting. This closes the window where two threads both elected themselves
winner in different `RestClient` instances sharing the same account.

**Failure publish.** On failure, `publishGeneration` is NOT incremented. A loser that woke
during a failed cycle sees an unchanged generation and returns `null`. A loser that started
waiting before an earlier successful cycle can still adopt that result. `lastRefreshTime` is
also left unchanged on failure so fresh arrivers cannot wrongly adopt a stale token via the
recency window.

**Lock is never held during network I/O.** The lock is acquired twice: once briefly for
the election (a few microseconds), then released before any network calls, then reacquired
briefly to publish the result. DPoP nonce retry inside `makeTokenEndpointRequest()` — two
HTTP calls in the worst case — happens entirely outside the lock.

---

## 5. DPoP Nonce Lifecycle

Nonces are issued exclusively by the **`/token` endpoint** (confirmed by the Salesforce DPoP
implementation team). Resource servers do not issue nonces.

```
DPoPNonceCache
  Key:   credentialsIdentifier + ":" + host
  Value: most recent nonce received from that host's /token response
  Type:  ConcurrentHashMap (thread-safe singleton)
```

### Write path (OAuth2.java only)

Both `makeTokenEndpointRequest()` and `callIdentityService()` harvest `DPoP-Nonce` from
**every** response (success or error) before inspecting the status code:

```
response = httpClient.newCall(request).execute()
nonce = response.header("DPoP-Nonce")
if nonce != null → DPoPNonceCache.store(credentialsIdentifier, host, nonce)
if isNonceChallenge(response):
    rebuild proof with DPoPNonceCache.get(credentialsIdentifier, host)
    retry once
    if second attempt also fails → throw OAuthFailedException
```

### Read path (interceptor + OAuth2.java)

Every DPoP proof — whether in `attachDPoPProofIfNeeded()` (API calls) or
`makeTokenEndpointRequest()` (token requests) — reads the cache before calling
`buildProof()`. On a warm path (cache hit) the nonce is included proactively and no
extra round-trip occurs. On a cold path (first login, or nonce rotated) the proof goes
out without a nonce; the token endpoint's challenge-retry handles it transparently.

### Interaction with the RTR lock

Because the interceptor never writes the nonce cache and never retries on nonce challenges,
nonce handling has no interaction with the RTR lock. Nonce writes happen inside
`makeTokenEndpointRequest()`, which runs only on the winner thread, after the lock has
been released.

### Logout

`SalesforceSDKManager.removeAccount()` calls:
- `DPoPKeyManager.deleteKeyPair(alias)` — destroys the EC keypair from the Android Keystore
- `DPoPNonceCache.clear(credentialsIdentifier)` — evicts cached nonces for this session

---

## 6. End-to-End: API Call Scenarios

### 6.1 Happy path (warm, DPoP with cached nonce)

```
intercept()
  buildAuthenticatedRequest()
    Authorization: DPoP <accessToken>
    DPoP: <proof with cached nonce + ath>
  chain.proceed()  →  200
  return response
```

### 6.2 Access token expired

```
intercept()
  buildAuthenticatedRequest()   (cached nonce included)
  chain.proceed()  →  401
  shouldRefresh()  →  true
  refreshAccessToken()
    getNewAuthToken()
      synchronized(state.lock): become winner, set refreshing=true; lock released
      refreshStaleToken()
        OAuth2.refreshAuthToken()
          POST /token  (DPoP proof + cached nonce)
          harvest DPoP-Nonce from response
          200: new access_token, rotated refresh_token
      synchronized(state.lock): publish, publishGeneration++, notifyAll(); lock released
  buildAuthenticatedRequest()   (new token + nonce)
  chain.proceed()  →  200
  return response
```

### 6.3 Access token expired + nonce missing or expired

```
intercept()
  chain.proceed()  →  401
  refreshAccessToken()
    getNewAuthToken()
      lock: become winner; lock released
      OAuth2.refreshAuthToken()
        POST /token  (DPoP proof, no nonce or stale nonce)
        harvest DPoP-Nonce  →  DPoPNonceCache.store(...)
        isNonceChallenge()  →  true  →  retry with fresh nonce
        POST /token  (DPoP proof + correct nonce)
        harvest DPoP-Nonce from success response (server may rotate)
        200: new access_token
      lock: publish, notifyAll(); lock released
  buildAuthenticatedRequest()   (new token + nonce now in cache)
  chain.proceed()  →  200
  return response
```

Note: cases 6.2 and 6.3 collapse into the same code path. The distinction is invisible to
`intercept()` — it always sees a single 401 from the resource server and a single successful
token after `refreshAccessToken()` returns.

### 6.4 Concurrent 401s from two threads (same account, RTR enabled)

```
Thread A                              Thread B
chain.proceed() → 401                 chain.proceed() → 401
refreshAccessToken()                  refreshAccessToken()
  getNewAuthToken()                     getNewAuthToken()
    lock: winner, refreshing=true         lock: loser, park on state.lock.wait()
    lock released
    POST /token → 200
    lock: publish, notifyAll()
    lock released                       wakes; publishGeneration advanced
                                        → adopt winner's token (no /token call)
  buildAuthenticatedRequest()         buildAuthenticatedRequest()
  chain.proceed() → 200               chain.proceed() → 200
```
