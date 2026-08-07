/*
 * Copyright (c) 2014-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.androidsdk.rest;

import static com.salesforce.androidsdk.auth.OAuth2.LogoutReason.CLIENT_BLOCKED;
import static com.salesforce.androidsdk.auth.OAuth2.LogoutReason.REFRESH_TOKEN_EXPIRED;
import static com.salesforce.androidsdk.auth.OAuth2.refreshAuthToken;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.accounts.UserAccountBuilder;
import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.analytics.EventBuilderHelper;
import com.salesforce.androidsdk.app.Features;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.auth.OAuthErrorCode;
import com.salesforce.androidsdk.auth.OAuth2;
import com.salesforce.androidsdk.auth.OAuth2.LogoutReason;
import com.salesforce.androidsdk.auth.OAuth2.OAuthFailedException;
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse;
import com.salesforce.androidsdk.auth.OAuth2.TokenErrorResponse;
import com.salesforce.androidsdk.rest.RestClient.ClientInfo;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for authenticated {@link RestClient} instances belonging to one persisted user.
 * A manager never changes identity when the application switches its current user.
 */
public class ClientManager {

	public static final String ACCESS_TOKEN_REVOKE_INTENT = "access_token_revoked";
    public static final String ACCESS_TOKEN_REFRESH_INTENT = "access_token_refeshed";
    public static final String INSTANCE_URL_UPDATE_INTENT = "instance_url_updated";
    /** Intent extra: the {@code error} value from the token endpoint response (e.g. "app_attest_failed", "invalid_grant"). */
    public static final String EXTRA_TOKEN_ERROR = "token_error";

    /** Intent extra: the {@code error_description} value from the token endpoint response. */
    public static final String EXTRA_TOKEN_ERROR_DESCRIPTION = "token_error_description";
    private static final String TAG = "ClientManager";

    private final AccountManager accountManager;
    @Nullable
    private final Account account;

    /**
     * Constructs a manager permanently bound to an existing persisted user.
     *
     * @param ctx Context.
     * @param user Persisted user this manager represents.
     */
    public ClientManager(@NonNull Context ctx, @NonNull UserAccount user) {
        accountManager = AccountManager.get(ctx);
        account = UserAccountManager.getInstance().buildAccount(user);
        if (account == null) {
            SalesforceSDKLogger.w(TAG,
                    "No persisted account matches the supplied user; manager will remain inactive");
        }
    }

    @VisibleForTesting
    ClientManager(@NonNull AccountManager accountManager,
                  @NonNull Account account) {
        this.accountManager = accountManager;
        this.account = account;
    }

    /** Creates a client for this manager's bound user, or null if it is unavailable. */
    @Nullable
    public RestClient peekRestClient() {
        final UserAccount user = getValidatedUser(/* requireRefreshFields = */ false);
        if (user == null) {
            SalesforceSDKLogger.w(TAG, "Bound user account is no longer available");
            return null;
        }
        return createRestClient(user);
    }

    @Nullable
    private RestClient createRestClient(UserAccount userAccount) {
        if (account == null) {
            return null;
        }
        if (SalesforceSDKManager.getInstance().isLoggingOut(account)) {
            SalesforceSDKLogger.i(TAG, "User is logging out");
            return null;
        }

        if (userAccount.getAuthToken() == null) {
            SalesforceSDKLogger.w(TAG, "Cannot create a client without an auth token");
            return null;
        }
        if (userAccount.getInstanceServer() == null) {
            SalesforceSDKLogger.w(TAG, "Cannot create a client without an instance URL");
            return null;
        }
        if (userAccount.getLoginServer() == null) {
            SalesforceSDKLogger.w(TAG, "Cannot create a client without a login URL");
            return null;
        }
        if (userAccount.getIdUrl() == null) {
            SalesforceSDKLogger.w(TAG, "Cannot create a client without an identity URL");
            return null;
        }
        if (userAccount.getUserId() == null) {
            SalesforceSDKLogger.w(TAG, "Cannot create a client without a user ID");
            return null;
        }
        if (userAccount.getOrgId() == null) {
            SalesforceSDKLogger.w(TAG, "Cannot create a client without an org ID");
            return null;
        }

        try {
            final AccMgrAuthTokenProvider authTokenProvider = new AccMgrAuthTokenProvider(this);
            final ClientInfo clientInfo = new ClientInfo(new URI(userAccount.getInstanceServer()),
                    new URI(userAccount.getLoginServer()), new URI(userAccount.getIdUrl()), userAccount.getAccountName(), userAccount.getUsername(),
                    userAccount.getUserId(), userAccount.getOrgId(), userAccount.getCommunityId(), userAccount.getCommunityUrl(),
                    userAccount.getFirstName(), userAccount.getLastName(), userAccount.getDisplayName(), userAccount.getEmail(), userAccount.getPhotoUrl(), userAccount.getThumbnailUrl(), userAccount.getAdditionalOauthValues(),
                    userAccount.getLightningDomain(), userAccount.getLightningSid(), userAccount.getVFDomain(), userAccount.getVFSid(), userAccount.getContentDomain(), userAccount.getContentSid(), userAccount.getCSRFToken());
            return new RestClient(clientInfo, userAccount.getAuthToken(), userAccount.getTokenType(), userAccount.getCredentialsIdentifier(), HttpAccess.DEFAULT, authTokenProvider);
        } catch (URISyntaxException e) {
            SalesforceSDKLogger.w(TAG, "Invalid server URL", e);
            return null;
        }
    }

    /**
     * Returns the exact account this manager was constructed for.
     *
     * @return Bound Android account, or null if the supplied user does not resolve to a valid
     * persisted record.
     */
    @Nullable
    public Account getAccount() {
        return account;
    }

    @VisibleForTesting
    int getBoundAccountCount() {
        if (account == null) {
            return 0;
        }
        return accountManager.getAccountsByType(account.type).length;
    }

    @Nullable
    @VisibleForTesting
    UserAccount getValidatedUser(boolean requireRefreshFields) {
        if (account == null || !accountExists()) {
            return null;
        }
        final UserAccount user = UserAccountManager.getInstance().buildUserAccount(account);
        if (user == null
                || isMissing(user.getUserId())
                || isMissing(user.getOrgId())) {
            return null;
        }
        if (requireRefreshFields && (isMissing(user.getRefreshTokenForPersistence())
                || isMissing(user.getLoginServer())
                || isMissing(user.getClientIdForRefresh()))) {
            return null;
        }
        return user;
    }

    private boolean accountExists() {
        if (account == null) {
            return false;
        }
        for (Account candidate : accountManager.getAccountsByType(account.type)) {
            if (account.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMissing(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }

    /** Account-bound auth-token provider. */
    public static class AccMgrAuthTokenProvider implements RestClient.AuthTokenProvider {

        /**
         * App-global, per-account refresh coordination state.
         *
         * <p>Many subsystems each hold their own {@link RestClient} and therefore their own
         * {@code AccMgrAuthTokenProvider} instance, each carrying a construction-time refresh-token
         * snapshot. Without app-global serialization, a token-refresh storm (e.g. on resume) could
         * have multiple providers POST in true parallel. With server-side Refresh Token Rotation
         * (RTR) the loser then POSTs an already-rotated refresh token, gets {@code invalid_grant},
         * and logs the user out. This per-account state serializes refreshes so exactly one provider
         * (the "winner") performs the network refresh and the others ("losers") adopt its result.
         */
        private static final class RefreshState {
            // Dedicated monitor for this state's winner/loser coordination. A private final lock
            // object (rather than synchronizing on the RefreshState reference itself) makes the
            // intent explicit and avoids the "synchronization on local variable" inspection.
            final Object lock = new Object();
            boolean refreshing;
            // Incremented once per successful publish. Waiting losers adopt only when this edge
            // advances, so a failed refresh cannot be mistaken for a usable result.
            long publishGeneration;
            String newAuthToken;        // last successful winner's fresh access token
            String newInstanceUrl;      // last winner's instance URL (losers need it; see RestClient.refreshAccessToken)
            String rotatedRefreshToken; // refresh token after rotation, for losers to adopt
            String newTokenType;        // last winner's token type (e.g. "Bearer" or "DPoP")
            long lastRefreshTime = -1;
        }

        private static final ConcurrentHashMap<String, RefreshState> REFRESH_STATES = new ConcurrentHashMap<>();

        /**
         * Clears the app-global per-account refresh coordination state. Test-only: {@code REFRESH_STATES}
         * is static and survives across tests, so it must be reset between them.
         */
        @VisibleForTesting
        static void resetRefreshStateForTest() {
            REFRESH_STATES.clear();
        }

        /** Bounded safety-net so a loser never parks forever if a winner is somehow lost. */
        private static final long LOSER_WAIT_TIMEOUT_MILLIS = 30_000L;

        /**
         * A fresh provider that arrives right after a refresh cycle completed (so it found
         * {@code refreshing == false}) adopts that just-published token instead of starting a new
         * refresh, as long as the publish is this recent. This closes the consecutive-cycle race
         * for fresh arrivers: it stops a freshly-arriving provider from electing itself a new
         * winner microseconds after another winner published — which under Refresh Token Rotation
         * would mean a redundant POST that rotates the token again and widens the stale-token
         * logout window. Kept small: it only needs to exceed the notify-to-reacquire window (sub-
         * millisecond in practice), and a shorter window minimizes the time a server-revoked token
         * could be re-handed before the next request's 401 forces a real refresh.
         */
        private static final long RECENT_REFRESH_THRESHOLD_MILLIS = 3_000L;

        private final ClientManager clientManager;
        @Nullable
        private final String clientInstanceUrlOverride;
        private final long loserWaitTimeoutMillis;
        private String lastNewAuthToken;
        // Mutable to support server-side Refresh Token Rotation (RTR).
        private String refreshToken;
        private String lastNewInstanceUrl;
        private long lastRefreshTime = -1 /* never refreshed */;
        private String lastTokenType;

        /**
         * Constructs a provider whose account identity comes exclusively from its bound manager.
         */
        public AccMgrAuthTokenProvider(@NonNull ClientManager clientManager) {
            this(clientManager, null, LOSER_WAIT_TIMEOUT_MILLIS);
        }

        /**
         * Constructs a provider with a routing-only instance URL override. The override never
         * participates in account selection, persistence, refresh coordination, or logout.
         */
        public AccMgrAuthTokenProvider(@NonNull ClientManager clientManager,
                                       @Nullable String clientInstanceUrlOverride) {
            this(clientManager, clientInstanceUrlOverride, LOSER_WAIT_TIMEOUT_MILLIS);
        }

        /** Test-only constructor that shortens the bounded loser wait. */
        @VisibleForTesting
        AccMgrAuthTokenProvider(@NonNull ClientManager clientManager,
                                long loserWaitTimeoutMillis) {
            this(clientManager, null, loserWaitTimeoutMillis);
        }

        private AccMgrAuthTokenProvider(@NonNull ClientManager clientManager,
                                        @Nullable String clientInstanceUrlOverride,
                                        long loserWaitTimeoutMillis) {
            this.clientManager = clientManager;
            this.clientInstanceUrlOverride = clientInstanceUrlOverride;
            this.loserWaitTimeoutMillis = loserWaitTimeoutMillis;
            final UserAccount user =
                    clientManager.getValidatedUser(/* requireRefreshFields = */ false);
            if (user != null) {
                refreshToken = user.getRefreshTokenForPersistence();
                lastNewAuthToken = user.getAuthToken();
                lastNewInstanceUrl = user.getInstanceServer();
            }
        }

        /**
         * Constructs a provider for the user bound to {@code clientManager}.
         *
         * @param clientManager Manager that supplies the provider's persisted account identity and
         *                      live credentials.
         * @param instanceUrl Ignored. Routing comes from the manager's bound user.
         * @param authToken Ignored. Access-token state comes from the manager's bound user.
         * @param refreshToken Ignored. Refresh-token state comes from the manager's bound user.
         * @deprecated Use {@link #AccMgrAuthTokenProvider(ClientManager)}. If {@code instanceUrl}
         * represented a custom client route, use
         * {@link #AccMgrAuthTokenProvider(ClientManager, String)}. This compatibility constructor
         * will be removed in Mobile SDK 15.0.
         */
        @Deprecated
        public AccMgrAuthTokenProvider(@NonNull ClientManager clientManager,
                                       @Nullable String instanceUrl,
                                       @Nullable String authToken,
                                       @Nullable String refreshToken) {
            this(clientManager);
        }

        /**
         * Fetch a new access token from the account manager.  If another thread
         * is already in the process of doing this, we'll just wait for it to finish and use that access token.
         * @return The auth token, or null if we can't get a new access token for any reason.
         */
        @Override
        public String getNewAuthToken() {
            SalesforceSDKLogger.i(TAG, "Need new access token");

            // Validation and the no-match early-out MUST run before any shared-state election.
            // Otherwise an account removed or malformed during refresh could leave a RefreshState
            // marked active, reintroducing both the lost-winner deadlock and logout-during-refresh
            // races this coordination protects against.
            final UserAccount initialUser =
                    clientManager.getValidatedUser(/* requireRefreshFields = */ true);
            if (initialUser == null) {
                return null;
            }
            final Account matchingAccount = clientManager.getAccount();
            if (matchingAccount == null) {
                return null;
            }
            final String refreshStateKey = refreshStateKeyFor(initialUser);

            // Elect winner/loser on the SINGLE coordination primitive (the per-account state).
            // Losers wait (looping on the condition to absorb spurious/lost wakeups) for the
            // winner's published result and adopt it without re-attempting, logging out, or
            // broadcasting.
            final RefreshState state = REFRESH_STATES.computeIfAbsent(
                    refreshStateKey, k -> new RefreshState());
            synchronized (state.lock) {
                if (state.refreshing) {
                    // Snapshot the publish generation BEFORE waiting. We adopt on a generation
                    // change (an edge), not on refreshing becoming false (a level). If a later
                    // winner has already set refreshing back to true when this thread reacquires
                    // the lock, the edge still proves that the prior winner published a result.
                    final long startGeneration = state.publishGeneration;
                    final long deadline = System.currentTimeMillis() + loserWaitTimeoutMillis;
                    boolean published;
                    try {
                        // Loop until a result is published or the in-flight refresh ends without
                        // one. The generation guard absorbs spurious and lost wakeups; the deadline
                        // prevents a lost winner from parking this caller forever.
                        while (state.refreshing && state.publishGeneration == startGeneration) {
                            final long timeRemaining = deadline - System.currentTimeMillis();
                            if (timeRemaining <= 0) {
                                break;
                            }
                            state.lock.wait(timeRemaining);
                        }
                        published = state.publishGeneration != startGeneration;
                    } catch (InterruptedException e) {
                        SalesforceSDKLogger.w(TAG,
                                "Interrupted while waiting for in-flight token refresh", e);
                        Thread.currentThread().interrupt();
                        if (state.publishGeneration != startGeneration
                                && tryAdoptWinnerResult(state)) {
                            return state.newAuthToken;
                        }
                        return null;
                    }

                    if (published) {
                        return tryAdoptWinnerResult(state) ? state.newAuthToken : null;
                    }

                    // Timed out waiting for this account's in-flight refresh. Starting a second,
                    // uncoordinated refresh could POST a stale rotated token and spuriously log the
                    // user out. Fail safely; the request can retry after the winner completes.
                    return null;
                }

                // A fresh provider arriving just after a completed cycle may adopt that result
                // instead of starting a redundant refresh. The freshness window alone is not
                // sufficient: the published access token must also differ from the token this
                // provider just failed a request with. Otherwise we could immediately replay the
                // same rejected token. tryAdoptWinnerResult additionally verifies that the shared
                // result is still the one persisted for this manager's bound identity.
                if (state.newAuthToken != null
                        && !Objects.equals(state.newAuthToken, lastNewAuthToken)
                        && System.currentTimeMillis() - state.lastRefreshTime
                        < RECENT_REFRESH_THRESHOLD_MILLIS
                        && tryAdoptWinnerResult(state)) {
                    return state.newAuthToken;
                }

                // Become the winner. Intentionally retain the previous published values here. A
                // loser from the prior cycle may observe its publish-generation edge only after
                // this winner sets refreshing=true; clearing the values would make that loser
                // return null even though its winner succeeded.
                state.refreshing = true;
            }

            // The winner performs the refresh. The entire body below runs inside one try/finally
            // whose finally ALWAYS publishes (or marks failed) and notifies, so no early return
            // can leave state.refreshing stuck true.
            String newAuthToken = null;
            String newInstanceUrl = null;
            String newTokenType = null;

            try {
                /*
                 * Recheck-under-lock guardrail. We hold the per-account refresh slot, but the
                 * 401/403 that sent us here may have been provoked by a token this provider was
                 * still using from BEFORE a concurrent (or earlier) refresh already rotated it.
                 * Re-read the account's current tokens from storage. If either token has advanced,
                 * another refresh already completed and this provider adopts the persisted result
                 * instead of issuing a redundant request.
                 *
                 * Under Refresh Token Rotation every needless POST rotates the refresh token again
                 * and widens the window for a stale-token logout, so avoiding it is a correctness
                 * guardrail, not an optimization. If the adopted access token is itself stale, the
                 * caller's replayed request 401s again and the next getNewAuthToken() — now holding
                 * the latest tokens — performs a real refresh (self-correcting, never a loop).
                 */
                if (lastNewAuthToken != null) {
                    final UserAccount currentAccount =
                            clientManager.getValidatedUser(/* requireRefreshFields = */ true);
                    if (currentAccount == null) {
                        return null;
                    }
                    final String storedAuthToken = currentAccount.getAuthToken();
                    final String storedRefreshToken = currentAccount.getRefreshTokenForPersistence();
                    final boolean haveLatestTokens = Objects.equals(
                            storedAuthToken, lastNewAuthToken)
                            && Objects.equals(storedRefreshToken, refreshToken);
                    if (!haveLatestTokens && storedAuthToken != null) {
                        SalesforceSDKLogger.i(TAG,
                                "Access or refresh token already advanced in storage; "
                                        + "adopting without refresh");
                        newAuthToken = storedAuthToken;
                        newInstanceUrl = currentAccount.getInstanceServer();
                        newTokenType = currentAccount.getTokenType();
                        refreshToken = storedRefreshToken;
                        return newAuthToken;
                    }
                }

                final UserAccount requestUser =
                        clientManager.getValidatedUser(/* requireRefreshFields = */ true);
                if (requestUser == null) {
                    return null;
                }
                // Refresh with the live persisted token, not this provider's construction-time
                // snapshot. Another provider may already have rotated it; posting that stale
                // snapshot would produce invalid_grant and could spuriously log the user out.
                final UserAccount userAccount = refreshStaleToken(
                        matchingAccount,
                        requestUser,
                        requestUser.getRefreshTokenForPersistence()
                );
                if (userAccount == null) {
                    return null;
                }

                newAuthToken = userAccount.getAuthToken();
                newInstanceUrl = userAccount.getInstanceServer();
                newTokenType = userAccount.getTokenType();

                if (clientManager.getValidatedUser(
                        /* requireRefreshFields = */ false) == null) {
                    newAuthToken = null;
                    newInstanceUrl = null;
                    newTokenType = null;
                    return null;
                }

                Intent broadcastIntent;
                if (newInstanceUrl != null && !newInstanceUrl.equalsIgnoreCase(lastNewInstanceUrl)) {

                    // Broadcasts an intent that the instance server has changed (implicitly token refreshed too).
                    broadcastIntent = new Intent(INSTANCE_URL_UPDATE_INTENT);
                } else {

                    // Broadcasts an intent that the access token has been refreshed.
                    broadcastIntent = new Intent(ACCESS_TOKEN_REFRESH_INTENT);
                    EventBuilderHelper.createAndStoreEvent("tokenRefresh", null, TAG, null);
                }
                broadcastIntent.setPackage(SalesforceSDKManager.getInstance().getAppContext().getPackageName());
                SalesforceSDKManager.getInstance().getAppContext().sendBroadcast(broadcastIntent);
            } catch (OAuthFailedException | MalformedTokenException e) {
                /*
                 * OAuthFailedException: token endpoint returned
                 * an error (e.g. app_attest_failed,
                 * app_attest_failed_retry, invalid_grant).
                 *
                 * MalformedTokenException: token endpoint returned
                 * success but the response lacked an access token.
                 *
                 * Common action: broadcast ACCESS_TOKEN_REVOKE_INTENT
                 * and, for terminal errors, logout the user.
                 */
                final String errorType;
                final String errorDesc;
                final OAuthErrorCode errorCode;
                if (e instanceof OAuthFailedException) {
                    final TokenErrorResponse tokenError = ((OAuthFailedException) e).getTokenErrorResponse();
                    errorType = tokenError.error;
                    errorDesc = tokenError.errorDescription;
                    errorCode = tokenError.errorCode;
                } else {
                    errorType = null;
                    errorDesc = null;
                    errorCode = OAuthErrorCode.UNKNOWN;
                }

                // Account removal or malformed persisted data suppresses every local side effect.
                if (clientManager.getValidatedUser(
                        /* requireRefreshFields = */ false) == null) {
                    return null;
                }

                final boolean terminal = !(e instanceof OAuthFailedException)
                        || errorCode != OAuthErrorCode.APP_ATTESTATION_FAILED_RETRY;

                if (terminal) {
                    // Terminal error (app_attest_failed, invalid_grant, malformed token, etc.) — logout.
                    if (Looper.myLooper() == null) {
                        Looper.prepare();
                    }
                    final boolean showLoginPage = clientManager.getBoundAccountCount() == 1;
                    final LogoutReason reason = errorCode == OAuthErrorCode.APP_ATTESTATION_FAILED
                            ? CLIENT_BLOCKED
                            : REFRESH_TOKEN_EXPIRED;
                    // The refresh token may already be unusable, but logout still performs
                    // best-effort remote cleanup before removing the exact local account.
                    SalesforceSDKManager.getInstance()
                            .logout(matchingAccount, null, showLoginPage, reason);
                }

                // Broadcast revoke intent with error details when available.
                final Intent broadcastIntent = new Intent(ACCESS_TOKEN_REVOKE_INTENT);
                if (errorType != null) {
                    broadcastIntent.putExtra(EXTRA_TOKEN_ERROR, errorType);
                }
                if (errorDesc != null) {
                    broadcastIntent.putExtra(EXTRA_TOKEN_ERROR_DESCRIPTION, errorDesc);
                }
                broadcastIntent.setPackage(SalesforceSDKManager.getInstance().getAppContext().getPackageName());
                SalesforceSDKManager.getInstance().getAppContext().sendBroadcast(broadcastIntent);
            } catch (Exception e) {
                SalesforceSDKLogger.w(TAG, "Exception thrown while getting auth token", e);
            } finally {
                // Keep the attempted result in this provider, but only successful refresh or
                // storage-adoption paths count as a completed refresh.
                lastNewAuthToken = newAuthToken;
                lastNewInstanceUrl = newInstanceUrl;
                lastTokenType = newTokenType;
                if (newAuthToken != null) {
                    lastRefreshTime = System.currentTimeMillis();
                }
                // Publish the result to the per-account state and wake any waiting losers.
                // This is the SINGLE publish path and ALWAYS runs on every winner exit path so
                // losers never wait forever and never wake without a definitive result.
                synchronized (state.lock) {
                    state.refreshing = false;
                    if (newAuthToken != null) {
                        state.newAuthToken = newAuthToken;
                        state.newInstanceUrl = newInstanceUrl;
                        state.rotatedRefreshToken = this.refreshToken;
                        state.newTokenType = newTokenType;
                        state.lastRefreshTime = System.currentTimeMillis();
                        // Bump the generation ONLY on success. A loser woken by a failed cycle sees
                        // no edge and cannot mistake the retained prior result for a new result
                        // from its winner.
                        state.publishGeneration++;
                    }
                    // On failure, deliberately retain the prior published token, instance URL,
                    // rotated refresh token, and timestamp. A loser that began waiting before an
                    // earlier success must still be able to adopt it even if a later cycle fails.
                    // publishGeneration is the sole signal that this loser's cycle produced a new
                    // result, while lastRefreshTime prevents fresh arrivals from adopting an
                    // indefinitely stale retained value.
                    state.lock.notifyAll();
                }
            }
            return newAuthToken;
        }

        /**
         * Attempts to copy the winner's refresh result from shared per-account state into this
         * loser instance's cache so that this instance's getters return consistent values.
         *
         * <p>Instance URL and refresh token are only overwritten when the winner actually
         * published a non-null value; otherwise this loser keeps its own constructor values so
         * {@link #getInstanceUrl()} stays non-null even when the refresh response carried no
         * instance_url (a valid case — see {@code RestClient.refreshAccessToken}).
         *
         * @return {@code true} only when the account remains available and the published result
         * still matches its persisted credential generation.
         */
        private boolean tryAdoptWinnerResult(RefreshState state) {
            final UserAccount liveUser =
                    clientManager.getValidatedUser(/* requireRefreshFields = */ false);
            if (liveUser == null
                    || state.newAuthToken == null
                    || !Objects.equals(liveUser.getAuthToken(), state.newAuthToken)
                    || !Objects.equals(liveUser.getRefreshTokenForPersistence(),
                    state.rotatedRefreshToken)
                    || !Objects.equals(liveUser.getTokenType(), state.newTokenType)
                    || (state.newInstanceUrl != null
                    && !Objects.equals(liveUser.getInstanceServer(), state.newInstanceUrl))) {
                return false;
            }
            this.lastNewAuthToken = state.newAuthToken;
            this.lastRefreshTime = state.lastRefreshTime;
            this.lastTokenType = state.newTokenType;
            if (state.newInstanceUrl != null) {
                this.lastNewInstanceUrl = state.newInstanceUrl;
            }
            if (state.rotatedRefreshToken != null) {
                this.refreshToken = state.rotatedRefreshToken;
            }
            return true;
        }

        /**
         * Keys refresh coordination by Salesforce user identity so different users refresh
         * independently while all clients for one org/user pair share a single winner.
         */
        private static String refreshStateKeyFor(UserAccount user) {
            return user.getUserId() + ":" + user.getOrgId();
        }

        @Override
        public String getRefreshToken() {
            return refreshToken;
        }

        @Override
        public long getLastRefreshTime() {
            return lastRefreshTime;
        }

        @Override
        public String getInstanceUrl() {
            return clientInstanceUrlOverride != null
                    ? clientInstanceUrlOverride
                    : lastNewInstanceUrl;
        }

        @Override
        public String getTokenType() {
            return lastTokenType;
        }

        @Nullable
        private UserAccount refreshStaleToken(
                Account account,
                UserAccount originalUserAccount,
                String currentRefreshToken
        ) throws NetworkErrorException, OAuthFailedException, MalformedTokenException {
            final Map<String, String> addlParamsMap = originalUserAccount.getAdditionalOauthValues();
            try {
                final URI tokenServer = OAuth2.overrideLoginServerIfNeeded(originalUserAccount);
                SalesforceSDKLogger.i(TAG, "Initiating token refresh to host: " + tokenServer.getHost());
                final TokenEndpointResponse tr = refreshAuthToken(HttpAccess.DEFAULT,
                        tokenServer, originalUserAccount.getClientIdForRefresh(), currentRefreshToken, addlParamsMap,
                        originalUserAccount.getCredentialsIdentifier());

                if (tr.authToken == null) {
                    throw new MalformedTokenException("Token endpoint returned null access token");
                }

                UserAccount updatedUserAccount = UserAccountBuilder.getInstance()
                        .populateFromUserAccount(originalUserAccount)
                        .refreshToken(originalUserAccount.getRefreshTokenForPersistence())
                        .allowUnset(false)
                        .populateFromTokenEndpointResponse(tr)
                        .build();

                // Confirm that the account still exists and can be rebuilt immediately before and
                // after persistence. Token-generation comparisons are handled separately.
                if (clientManager.getValidatedUser(
                        /* requireRefreshFields = */ false) == null) {
                    return null;
                }

                /*
                 * Detect server-side Refresh Token Rotation: the response
                 * carried a refresh token that differs from this provider's
                 * cached copy. Stamp the ISO-8601 rotation time on the account
                 * BEFORE the primary persist below so the timestamp is written
                 * by the authoritative updateAccount call, not as a side
                 * effect of feature-flag registration.
                 */
                boolean refreshTokenRotated = tr.refreshToken != null && !tr.refreshToken.equals(refreshToken);
                if (refreshTokenRotated) {
                    updatedUserAccount.setLastTokenRotationTime(Instant.now().toString());
                }

                UserAccountManager.getInstance().updateAccount(account, updatedUserAccount);
                if (clientManager.getValidatedUser(
                        /* requireRefreshFields = */ false) == null) {
                    return null;
                }
                updatedUserAccount.downloadProfilePhoto();
                UserAccountManager.getInstance().clearCachedCurrentUser();

                if (refreshTokenRotated) {
                    /*
                     * Update this provider's cached copy and surface RTR as a
                     * per-user feature flag. The rotation timestamp is already
                     * persisted (above), so RTR-Active state here is
                     * independent of the timestamp's durability.
                     */
                    refreshToken = tr.refreshToken;
                    SalesforceSDKManager.getInstance().registerUsedAppFeature(Features.FEATURE_RTR, updatedUserAccount);
                }

                return updatedUserAccount;
            } catch (OAuthFailedException ofe) {
                SalesforceSDKLogger.i(TAG, "Token endpoint error: (Error: " + ofe.getTokenErrorResponse().error + ", Status Code: " + ofe.getHttpStatusCode() + ")", ofe);
                throw ofe;
            } catch (MalformedTokenException mte) {
                throw mte;
            } catch (Exception e) {
                SalesforceSDKLogger.e(TAG, "Exception thrown while getting new auth token", e);
                throw new NetworkErrorException(e);
            }
        }
    }

    /**
     * Exception thrown when a token refresh response is malformed (e.g. missing access_token).
     */
    static class MalformedTokenException extends Exception {
        MalformedTokenException(String msg) {
            super(msg);
        }
    }

    /**
     * Legacy exception type retained for binary compatibility. ClientManager now reports an
     * unavailable account or client with a null result instead of throwing this exception.
     */
    public static class AccountInfoNotFoundException extends RuntimeException {

    	private static final long serialVersionUID = 1L;

        AccountInfoNotFoundException(String msg) {
            super(msg);
        }

        public AccountInfoNotFoundException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }
}
