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

import static com.salesforce.androidsdk.auth.OAuth2.CLIENT_BLOCKED_ERROR;
import static com.salesforce.androidsdk.auth.OAuth2.CLIENT_BLOCKED_RETRY_ERROR;
import static com.salesforce.androidsdk.auth.OAuth2.LogoutReason.CLIENT_BLOCKED;
import static com.salesforce.androidsdk.auth.OAuth2.LogoutReason.REFRESH_TOKEN_EXPIRED;
import static com.salesforce.androidsdk.auth.OAuth2.refreshAuthToken;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.accounts.UserAccountBuilder;
import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.analytics.EventBuilderHelper;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.AuthenticatorService;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.auth.OAuth2.LogoutReason;
import com.salesforce.androidsdk.auth.OAuth2.OAuthFailedException;
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse;
import com.salesforce.androidsdk.auth.OAuth2.TokenErrorResponse;
import com.salesforce.androidsdk.rest.RestClient.ClientInfo;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ClientManager is a factory class for RestClient which stores OAuth credentials in the AccountManager.
 * If no account is found, it kicks off the login flow which creates a new account if successful.
 *
 */
public class ClientManager {

	public static final String ACCESS_TOKEN_REVOKE_INTENT = "access_token_revoked";
    public static final String ACCESS_TOKEN_REFRESH_INTENT = "access_token_refeshed";
    public static final String INSTANCE_URL_UPDATE_INTENT = "instance_url_updated";
    /** Intent extra: the {@code error} value from the token endpoint response (e.g. "client_blocked", "invalid_grant"). */
    public static final String EXTRA_TOKEN_ERROR = "token_error";

    /** Intent extra: the {@code error_description} value from the token endpoint response. */
    public static final String EXTRA_TOKEN_ERROR_DESCRIPTION = "token_error_description";
    private static final String TAG = "ClientManager";

    private final AccountManager accountManager;
    private final String accountType;
    private final boolean revokedTokenShouldLogout;

    /**
     * Construct a ClientManager using a custom account type.
     *
     * @param ctx Context.
     * @param accountType Account type.
     * @param revokedTokenShouldLogout True - if the SDK should logout when the access token is revoked, False - otherwise.
     */
    public ClientManager(Context ctx, String accountType, boolean revokedTokenShouldLogout) {
    	this.accountManager = AccountManager.get(ctx);
        this.accountType = accountType;
        this.revokedTokenShouldLogout = revokedTokenShouldLogout;
    }

    /**
     * Method to create a RestClient asynchronously. It is intended to be used by code on the UI thread.
     *
     * If no accounts are found, it will kick off the login flow which will create a new account if successful.
     * After the account is created or if an account already existed, it creates a RestClient and returns it through restClientCallback.
     *
     * Note: The work is actually being done by the service registered to handle authentication for this application account type.
     * @see AuthenticatorService
     *
     * @param activityContext        current activity
     * @param restClientCallback     callback invoked once the RestClient is ready
     */
    public void getRestClient(Activity activityContext, RestClientCallback restClientCallback) {
        Account acc = getAccount();

        // No account found - let's add one - the AuthenticatorService add account method will start the login activity using either the default login URL or the Salesforce SDK manager's front door URL for Salesforce Identity API UI Bridge
        if (acc == null) {
            SalesforceSDKLogger.i(TAG, "No account of type " + accountType + " found");
            final Intent i = new Intent(activityContext,
                    SalesforceSDKManager.getInstance().getLoginActivityClass());
            i.setPackage(activityContext.getPackageName());
            i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

            /*
             * Special Note: `LoginActivity` does not actually return a result.
             * However, it does start broadcast intents that need to be received
             * by the starting activity.  Since login activity is started in a
             * new task, the starting activity would become available to be
             * destroyed which unregisters its broadcast intent receivers.
             *
             * Using `startActivityForResult` starts a new task with the
             * starting activity as the "base" intent with login activity as its
             * "visible" sub-activity.  This keeps the starting activity from
             * being eagerly destroyed and sets it as the activity to be started
             * if the user returns the this task after it may have been fully
             * destroyed due to memory pressure.
             *
             * TODO: This short term solution will be replaced in a future release.
             */
            activityContext.startActivityForResult(i, 0);
        }

        // Account found
        else {
            SalesforceSDKLogger.i(TAG, "Found account of type " + accountType);
            final RestClient cachedRestClient = peekRestClient();
            restClientCallback.authenticatedRestClient(cachedRestClient);
        }
    }

    /**
     * Method to created an unauthenticated RestClient asynchronously
     * @param activityContext
     * @param restClientCallback
     */
    public void getUnauthenticatedRestClient(Activity activityContext, RestClientCallback restClientCallback) {
        restClientCallback.authenticatedRestClient(peekUnauthenticatedRestClient());
    }

    /**
     * Method to create an unauthenticated RestClient.
     * @return
     */
    public RestClient peekUnauthenticatedRestClient() {
        return new RestClient(new RestClient.UnauthenticatedClientInfo(), null, HttpAccess.DEFAULT, null);
    }

    public RestClient peekRestClient() {
        return peekRestClient(getAccount());
    }

    /**
     * Method to create RestClient synchronously. It is intended to be used by code not on the UI thread (e.g. ContentProvider).
     *
     * If there is no account, it will throw an exception.
     *
     * @return
     */
    public RestClient peekRestClient(UserAccount user) {
    	return peekRestClient(getAccountByName(user.getAccountName()));
    }

    public RestClient peekRestClient(Account acc) {
        if (acc == null) {
            AccountInfoNotFoundException e = new AccountInfoNotFoundException("No user account found");
            SalesforceSDKLogger.i(TAG, "No user account found", e);
            throw e;
        }
        if (SalesforceSDKManager.getInstance().isLoggingOut()) {
        	AccountInfoNotFoundException e = new AccountInfoNotFoundException("User is logging out");
            SalesforceSDKLogger.i(TAG, "User is logging out", e);
            throw e;
        }
        UserAccount userAccount = UserAccountManager.getInstance().buildUserAccount(acc);

        if (userAccount.getAuthToken() == null) {
            throw new AccountInfoNotFoundException(AccountManager.KEY_AUTHTOKEN);
        }
        if (userAccount.getInstanceServer() == null) {
            throw new AccountInfoNotFoundException(AuthenticatorService.KEY_INSTANCE_URL);
        }
        if (userAccount.getUserId() == null) {
            throw new AccountInfoNotFoundException(AuthenticatorService.KEY_USER_ID);
        }
        if (userAccount.getOrgId() == null) {
            throw new AccountInfoNotFoundException(AuthenticatorService.KEY_ORG_ID);
        }

        try {
            final AccMgrAuthTokenProvider authTokenProvider = new AccMgrAuthTokenProvider(this,
                    userAccount.getInstanceServer(), userAccount.getAuthToken(), userAccount.getRefreshToken());
            final ClientInfo clientInfo = new ClientInfo(new URI(userAccount.getInstanceServer()),
            		new URI(userAccount.getLoginServer()), new URI(userAccount.getIdUrl()), userAccount.getAccountName(), userAccount.getUsername(),
            		userAccount.getUserId(), userAccount.getOrgId(), userAccount.getCommunityId(), userAccount.getCommunityUrl(),
                    userAccount.getFirstName(), userAccount.getLastName(), userAccount.getDisplayName(), userAccount.getEmail(), userAccount.getPhotoUrl(), userAccount.getThumbnailUrl(), userAccount.getAdditionalOauthValues(),
                    userAccount.getLightningDomain(), userAccount.getLightningSid(), userAccount.getVFDomain(), userAccount.getVFSid(), userAccount.getContentDomain(), userAccount.getContentSid(), userAccount.getCSRFToken());
            return new RestClient(clientInfo, userAccount.getAuthToken(), HttpAccess.DEFAULT, authTokenProvider);
        } catch (URISyntaxException e) {
            SalesforceSDKLogger.w(TAG, "Invalid server URL", e);
            throw new AccountInfoNotFoundException("invalid server url", e);
        }
    }

    /**
     * Invalidate current auth token. The next call to {@link #getRestClient(Activity, RestClientCallback) getRestClient} will do a refresh.
     */
    public void invalidateToken(String lastNewAuthToken) {
        accountManager.invalidateAuthToken(getAccountType(), lastNewAuthToken);
    }

    /**
     * Returns the user account that is currently active.
     *
     * @return The current user account.
     */
    public Account getAccount() {
    	return SalesforceSDKManager.getInstance().getUserAccountManager().getCurrentAccount();
    }

    /**
     * @param name The name associated with the account.
     * @return The account with the application account type and the given name.
     */
    public Account getAccountByName(String name) {
        final Account[] accounts = accountManager.getAccountsByType(getAccountType());
        for (final Account account : accounts) {
            if (account.name.equals(name)) {
                return account;
            }
        }
        return null;
    }

    /**
     * @return All of the accounts found for this application account type.
     */
    public Account[] getAccounts() {
        return accountManager.getAccountsByType(getAccountType());
    }

    /**
     * Remove all of the accounts passed in.
     *
     * @param accounts The array of accounts to remove.
     */
    public void removeAccounts(Account[] accounts) {
        if (accounts != null && accounts.length > 0) {
            for (final Account account : accounts) {
                removeAccount(account);
            }
        }
    }

    /**
     * Creates a new account and returns the parameters as a Bundle.
     */
    public Bundle createNewAccount(UserAccount userAccount) {
        return SalesforceSDKManager.getInstance().getUserAccountManager().createAccount(userAccount);
    }

    /**
     * Creates a new account and returns the parameters as a Bundle.
     *
     * @param accountName Account name
     * @param username Username.
     * @param refreshToken Refresh token.
     * @param authToken Access token.
     * @param instanceUrl Instance URL.
     * @param loginUrl Login URL.
     * @param idUrl Identity URL.
     * @param clientId Client ID.
     * @param orgId Org ID.
     * @param userId User ID.
     * @param communityId Community ID.
     * @param communityUrl Community URL.
     * @param firstName First name.
     * @param lastName Last name.
     * @param displayName Display name.
     * @param email Email.
     * @param photoUrl Photo URL.
     * @param thumbnailUrl Thumbnail URL.
     * @param additionalOauthValues Additional OAuth values.
     * @return Account info.
     *
     * @Deprecated will be removed in Mobile SDK 14.0 - please use createNewAccount(UserAccount userAccount)
     */
    @Deprecated
    public Bundle createNewAccount(String accountName, String username, String refreshToken,
    		String authToken, String instanceUrl, String loginUrl, String idUrl,
    		String clientId, String orgId, String userId, String communityId, String communityUrl,
            String firstName, String lastName, String displayName, String email, String photoUrl,
            String thumbnailUrl, Map<String, String> additionalOauthValues,
            String lightningDomain, String lightningSid, String vfDomain, String vfSid,
            String contentDomain, String contentSid, String csrfToken, Boolean nativeLogin,
            String language, String locale) {
        UserAccount userAccount = UserAccountBuilder.getInstance()
                .accountName(accountName).username(username).refreshToken(refreshToken)
                .authToken(authToken).instanceServer(instanceUrl).loginServer(loginUrl).idUrl(idUrl)
                .clientId(clientId).orgId(orgId).userId(userId).communityId(communityId).communityUrl(communityUrl)
                .firstName(firstName).lastName(lastName).displayName(displayName).email(email).photoUrl(photoUrl)
                .thumbnailUrl(thumbnailUrl).additionalOauthValues(additionalOauthValues)
                .lightningDomain(lightningDomain).lightningSid(lightningSid).vfDomain(vfDomain).vfSid(vfSid)
                .contentDomain(contentDomain).contentSid(contentSid).csrfToken(csrfToken).nativeLogin(nativeLogin)
                .language(language).locale(locale)
                .build();

        return createNewAccount(userAccount);
    }

    /**
     * Should match the value in authenticator.xml.12
     * @return The account type for this application.
     */
    public String getAccountType() {
        return accountType;
    }

    /**
     * @return The AccountManager for the application.
     */
    public AccountManager getAccountManager() {
        return accountManager;
    }

    /**
     * Removes the user account from the account manager. This is safe to call from main thread.
     *
     * @param acc Account to be removed.
     */
    public void removeAccount(Account acc) {
        if (acc != null) {
            accountManager.removeAccountExplicitly(acc);
        }
    }

    /**
     * RestClientCallback interface.
     * You must provide an implementation of this interface when calling
     * {@link ClientManager#getRestClient(Activity, RestClientCallback) getRestClient}.
     */
    public interface RestClientCallback {
        void authenticatedRestClient(RestClient client);
    }

    /**
     * AuthTokenProvider implementation that calls out to the AccountManager to get a new access token.
     * The AccountManager calls AuthenticatorService to do the actual refresh.
     * @see AuthenticatorService
     */
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
            // Incremented once per SUCCESSFUL publish (never on a failed refresh). A waiting loser
            // snapshots this value before sleeping and treats any change on wakeup as "a fresh
            // result was published while I waited." This is robust to a *subsequent* winner that
            // has already re-set refreshing=true (the consecutive-cycle race) and to spurious
            // wakeups — neither of which the refreshing flag alone can distinguish.
            long publishGeneration = 0;
            String newAuthToken;        // last winner's fresh access token (null on failure)
            String newInstanceUrl;      // last winner's instance URL (losers need it; see RestClient.refreshAccessToken)
            String rotatedRefreshToken; // refresh token after rotation, for losers to adopt
            long lastRefreshTime = -1;
        }

        private static final ConcurrentHashMap<String, RefreshState> REFRESH_STATES = new ConcurrentHashMap<>();

        /**
         * Clears the app-global per-account refresh coordination state. Test-only: {@code REFRESH_STATES}
         * is static and survives across tests, so it must be reset between them.
         */
        @androidx.annotation.VisibleForTesting
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
        private String lastNewAuthToken;
        // Mutable to support server-side Refresh Token Rotation (RTR).
        private String refreshToken;
        private String lastNewInstanceUrl;
        private long lastRefreshTime = -1 /* never refreshed */;

        /**
         * Constructor
         * @param clientManager
         * @param refreshToken
         */
        public AccMgrAuthTokenProvider(ClientManager clientManager, String instanceUrl,
                                       String authToken, String refreshToken) {
            this.clientManager = clientManager;
            this.refreshToken = refreshToken;
            lastNewAuthToken = authToken;
            lastNewInstanceUrl = instanceUrl;
        }

        /**
         * Fetch a new access token from the account manager.  If another thread
         * is already in the process of doing this, we'll just wait for it to finish and use that access token.
         * @return The auth token, or null if we can't get a new access token for any reason.
         */
        @Override
        public String getNewAuthToken() {
            SalesforceSDKLogger.i(TAG, "Need new access token");

            // The matching loop and the no-match early-out MUST run before any shared-state
            // election so that a no-match path (e.g. account removed during refresh) never
            // marks a RefreshState as refreshing — preserving the no deadlock fix and
            // logout-during-refresh semantics.
            final UserAccountManager userAccountManager = SalesforceSDKManager.getInstance().getUserAccountManager();
            final Account[] accounts = clientManager.getAccounts();
            Account matchingAccount = null;
            String stateKey = null;

            if (refreshToken != null && accounts != null) {
                for (Account account : accounts) {
                    final UserAccount user = userAccountManager.buildUserAccount(account);
                    if (user != null && refreshToken.equals(user.getRefreshToken())) {
                        matchingAccount = account;
                        final String userId = user.getUserId();
                        final String orgId = user.getOrgId();
                        if (userId == null || orgId == null) {
                            SalesforceSDKLogger.w(TAG, "Cannot serialize token refresh: " +
                                    "account is missing userId or orgId");
                            return null;
                        }
                        stateKey = userId + ":" + orgId;
                        break;
                    }
                }
            }

            // Fail early to ensure we don't logout the current user below by sending null.
            if (matchingAccount == null) {
                return null;
            }

            // Elect winner/loser on the SINGLE coordination primitive (the per-account state).
            // Losers wait (looping on the condition to absorb spurious/lost wakeups) for the
            // winner's published result and adopt it without re-attempting, logging out, or
            // broadcasting.
            final RefreshState state = REFRESH_STATES.computeIfAbsent(stateKey, k -> new RefreshState());
            synchronized (state.lock) {
                if (state.refreshing) {
                    // Snapshot the publish generation BEFORE waiting. We adopt on a *generation
                    // change* (an edge), not on observing refreshing==false (a level). This rescues
                    // the consecutive-cycle race: if a subsequent winner has already flipped
                    // refreshing back to true by the time we re-acquire the lock, we still detect
                    // that the prior winner published a result while we waited and adopt it, rather
                    // than re-parking against a deadline that began ticking during an unrelated
                    // earlier cycle.
                    final long startGeneration = state.publishGeneration;
                    final long deadline = System.currentTimeMillis() + LOSER_WAIT_TIMEOUT_MILLIS;
                    boolean published;
                    try {
                        // Loop until a new result is published (generation advanced) or the
                        // in-flight refresh ends without one. Bounded so a lost winner can't
                        // strand us forever. The generation guard also absorbs spurious/lost
                        // wakeups.
                        while (state.refreshing && state.publishGeneration == startGeneration) {
                            final long timeRemaining = deadline - System.currentTimeMillis();
                            if (timeRemaining <= 0) {
                                break;
                            }
                            state.lock.wait(timeRemaining);
                        }
                        published = state.publishGeneration != startGeneration;
                    } catch (InterruptedException e) {
                        SalesforceSDKLogger.w(TAG, "Interrupted while waiting for in-flight token refresh", e);
                        Thread.currentThread().interrupt();
                        // Adopt a result only if one was actually published while we waited.
                        if (state.publishGeneration != startGeneration && state.newAuthToken != null) {
                            adoptWinnerResult(state);
                            return state.newAuthToken;
                        }
                        return null;
                    }

                    if (published) {
                        adoptWinnerResult(state);
                        return state.newAuthToken;
                    }
                    // Timed out waiting for an in-flight refresh on this account. Becoming a
                    // second concurrent refresher would risk a parallel stale refresh-token POST
                    // and a spurious logout, so fail safe: return null rather than refresh
                    // uncoordinated. The caller's request fails and can retry; the in-flight
                    // winner (if merely slow) still completes and serves the next caller.
                    return null;
                }

                // Fresh arriver (found refreshing==false). If a winner published very recently,
                // adopt that result instead of starting a redundant refresh — closing the
                // consecutive-cycle race for threads that arrive just after a cycle completes.
                //
                // The freshness window alone is not sufficient: we must also confirm the published
                // token actually differs from the one THIS provider just failed a request with
                // (lastNewAuthToken). Without that difference check we could hand our caller back
                // the very token it just got a 401/403 on (e.g. when this provider was itself the
                // recent winner), causing an immediate repeat 401. This mirrors the recheck-under-
                // lock storage guardrail below, which likewise POSTs a real refresh when storage
                // has NOT advanced past this provider's tokens.
                if (state.newAuthToken != null
                        && !java.util.Objects.equals(state.newAuthToken, lastNewAuthToken)
                        && System.currentTimeMillis() - state.lastRefreshTime < RECENT_REFRESH_THRESHOLD_MILLIS) {
                    adoptWinnerResult(state);
                    return state.newAuthToken;
                }

                // Become the winner. Note: the previously-published newAuthToken/newInstanceUrl/
                // rotatedRefreshToken are intentionally NOT cleared here. A loser of the prior
                // cycle that is woken after we re-set refreshing=true must still be able to read
                // that last-good result via the publishGeneration edge above; clearing it would
                // re-introduce the consecutive-cycle null-return. The success branch of the finally
                // publish overwrites these fields with our own result anyway.
                state.refreshing = true;
            }

            // The winner performs the refresh. The entire body below runs inside one try/finally
            // whose finally ALWAYS publishes (or marks failed) and notifies, so no early return
            // can leave state.refreshing stuck true.
            String newAuthToken = null;
            String newInstanceUrl = null;

            try {
                /*
                 * Recheck-under-lock guardrail. We hold the per-account refresh slot, but the
                 * 401/403 that sent us here may have been provoked by a token this provider was
                 * still using from BEFORE a concurrent (or earlier) refresh already rotated it.
                 * Re-read the account's current tokens from storage: if EITHER the access token
                 * or the refresh token in storage has advanced past what this provider last used,
                 * someone already refreshed — adopt their tokens and skip a redundant network POST.
                 *
                 * Under Refresh Token Rotation every needless POST rotates the refresh token again
                 * and widens the window for a stale-token logout, so avoiding it is a correctness
                 * guardrail, not an optimization. If the adopted access token is itself stale, the
                 * caller's replayed request 401s again and the next getNewAuthToken() — now holding
                 * the latest tokens — performs a real refresh (self-correcting, never a loop).
                 */
                if (lastNewAuthToken != null) {
                    final UserAccount currentAccount =
                            UserAccountManager.getInstance().buildUserAccount(matchingAccount);
                    if (currentAccount != null) {
                        final String storedAuthToken = currentAccount.getAuthToken();
                        final String storedRefreshToken = currentAccount.getRefreshToken();
                        final boolean haveLatestTokens =
                                java.util.Objects.equals(storedAuthToken, lastNewAuthToken)
                                        && java.util.Objects.equals(storedRefreshToken, this.refreshToken);
                        if (!haveLatestTokens && storedAuthToken != null) {
                            // Storage advanced past us — adopt without refreshing or broadcasting.
                            SalesforceSDKLogger.i(TAG,
                                    "Access/refresh token already advanced in storage; adopting without refresh");
                            newAuthToken = storedAuthToken;
                            newInstanceUrl = currentAccount.getInstanceServer();
                            this.refreshToken = storedRefreshToken;
                            return newAuthToken;
                        }
                    }

                    clientManager.invalidateToken(lastNewAuthToken);
                }

                final UserAccount userAccount = refreshStaleToken(matchingAccount);
                //noinspection ConstantValue
                if (userAccount == null) {
                    throw new MalformedTokenException("refreshStaleToken returned null");
                }

                newAuthToken = userAccount.getAuthToken();
                newInstanceUrl = userAccount.getInstanceServer();

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
                 * an error (e.g. client_blocked,
                 * client_blocked_retry, invalid_grant).
                 *
                 * MalformedTokenException: token endpoint returned
                 * success but the response lacked an access token.
                 *
                 * Common action: broadcast ACCESS_TOKEN_REVOKE_INTENT
                 * and, for terminal errors, logout the user.
                 */
                final String errorType;
                final String errorDesc;
                if (e instanceof OAuthFailedException) {
                    final TokenErrorResponse tokenError = ((OAuthFailedException) e).getTokenErrorResponse();
                    errorType = tokenError.error;
                    errorDesc = tokenError.errorDescription;
                } else {
                    errorType = null;
                    errorDesc = null;
                }

                if (!CLIENT_BLOCKED_RETRY_ERROR.equals(errorType)) {
                    // Terminal error (client_blocked, invalid_grant, malformed token, etc.) — logout.
                    if (clientManager.revokedTokenShouldLogout) {
                        if (Looper.myLooper() == null) {
                            Looper.prepare();
                        }
                        final boolean showLoginPage = accounts.length == 1;
                        final LogoutReason reason = CLIENT_BLOCKED_ERROR.equals(errorType)
                                ? CLIENT_BLOCKED
                                : REFRESH_TOKEN_EXPIRED;
                        // Note: As of writing (2024) this call will never succeed because revoke API is an
                        // authenticated endpoint.  However, there is no harm in attempting and the debug logs
                        // produced may help developers better understand the state of their app.
                        SalesforceSDKManager.getInstance()
                                .logout(matchingAccount, null, showLoginPage, reason);
                    }
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
                // Update this instance's own cache so its getters stay correct.
                lastNewAuthToken = newAuthToken;
                lastNewInstanceUrl = newInstanceUrl;
                lastRefreshTime = System.currentTimeMillis();
                // Publish the result to the per-account state and wake any waiting losers.
                // This is the SINGLE publish path and ALWAYS runs on every winner exit path so
                // losers never wait forever and never wake without a definitive result.
                synchronized (state.lock) {
                    state.refreshing = false;
                    if (newAuthToken != null) {
                        state.newAuthToken = newAuthToken;
                        state.newInstanceUrl = newInstanceUrl;
                        state.rotatedRefreshToken = this.refreshToken;
                        state.lastRefreshTime = System.currentTimeMillis();
                        // Mark a fresh result as available. Bumped ONLY on success so a loser woken
                        // by a failed cycle sees an unchanged generation and correctly returns null
                        // (rather than adopting a non-result), while a loser that started waiting
                        // before an earlier success still adopts that success via the edge.
                        state.publishGeneration++;
                    }
                    // On failure we deliberately leave newAuthToken/newInstanceUrl/rotatedRefreshToken
                    // and lastRefreshTime UNCHANGED rather than nulling them. publishGeneration is
                    // the sole adopt signal: a loser of THIS failed cycle sees an unchanged
                    // generation and returns null, while a loser that began waiting before an
                    // EARLIER success must still be able to adopt that success — nulling here would
                    // wipe the last-good result out from under it and re-introduce a spurious-null
                    // (the consecutive-cycle race, success-then-failure variant). Fresh arrivers
                    // cannot wrongly adopt a stale token because the recency window keys off
                    // lastRefreshTime, which only a success advances.
                    state.lock.notifyAll();
                }
            }
            return newAuthToken;
        }

        /**
         * Copies the winner's refresh result from the shared per-account state into this loser
         * instance's cache so that this instance's getters return consistent values.
         *
         * <p>Instance URL and refresh token are only overwritten when the winner actually
         * published a non-null value; otherwise this loser keeps its own constructor values so
         * {@link #getInstanceUrl()} stays non-null even when the refresh response carried no
         * instance_url (a valid case — see {@code RestClient.refreshAccessToken}).
         */
        private void adoptWinnerResult(RefreshState state) {
            this.lastNewAuthToken = state.newAuthToken;
            this.lastRefreshTime = state.lastRefreshTime;
            if (state.newInstanceUrl != null) {
                this.lastNewInstanceUrl = state.newInstanceUrl;
            }
            if (state.rotatedRefreshToken != null) {
                this.refreshToken = state.rotatedRefreshToken;
            }
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
        public String getInstanceUrl() { return lastNewInstanceUrl; }

        @NonNull
        private UserAccount refreshStaleToken(Account account) throws NetworkErrorException, OAuthFailedException, MalformedTokenException {
            UserAccount originalUserAccount = UserAccountManager.getInstance().buildUserAccount(account);
            final Map<String,String> addlParamsMap = originalUserAccount.getAdditionalOauthValues();
            // Refresh with the LIVE persisted refresh token, not this provider's
            // construction-time snapshot. With server-side Refresh Token Rotation (RTR), a prior
            // refresh on another provider may have already rotated the token; reading the current
            // value avoids POSTing a stale token that would fail with invalid_grant.
            final String currentRefreshToken = originalUserAccount.getRefreshToken();
            try {
                final TokenEndpointResponse tr = refreshAuthToken(HttpAccess.DEFAULT,
                        new URI(originalUserAccount.getLoginServer()), originalUserAccount.getClientIdForRefresh(), currentRefreshToken, addlParamsMap);

                if (tr.authToken == null) {
                    throw new MalformedTokenException("Token endpoint returned null access token");
                }

                UserAccount updatedUserAccount = UserAccountBuilder.getInstance()
                        .populateFromUserAccount(originalUserAccount)
                        .allowUnset(false)
                        .populateFromTokenEndpointResponse(tr)
                        .build();

                UserAccountManager.getInstance().updateAccount(account, updatedUserAccount);
                updatedUserAccount.downloadProfilePhoto();
                UserAccountManager.getInstance().clearCachedCurrentUser();

                // Handle server-side Refresh Token Rotation: if the response contained a new refresh token,
                // update this provider's cached copy.
                if (tr.refreshToken != null && !tr.refreshToken.equals(refreshToken)) {
                    refreshToken = tr.refreshToken;
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
     * Exception thrown when no account could be found (during a
     * {@link ClientManager#peekRestClient() peekRestClient} call)
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
