/*
 * Copyright (c) 2026-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.auth.dpop

import android.os.Handler
import android.os.Looper
import androidx.annotation.VisibleForTesting
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.accounts.upgradeToDPoP
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Headless helper that upgrades a user's Bearer refresh token to a DPoP-bound one when needed.
 *
 * This class has no UI. It decides whether a migration is needed, runs
 * [upgradeToDPoP] and reports an [Outcome]. Note that the SDK itself presents
 * `TokenMigrationActivity` while the upgrade runs. Apps that want their own "securing your
 * session" screen can use [isMigrationNeeded] to decide whether to show it.
 *
 * The account's token type ([DPoPKeyManager.isDPoPTokenType]) is the single source of truth:
 * nothing is persisted by this class. On failure the user is left on their existing session and
 * the next call retries.
 *
 * Contract:
 * - Every call completes exactly once, on the main thread (the SDK's own callbacks may arrive on
 *   a background thread).
 * - Concurrent calls for the same user share a single upgrade. This matters because an activity's
 *   `onResume` fires again when `TokenMigrationActivity` finishes.
 * - If the SDK never reports back (for example the user backs out of `TokenMigrationActivity`),
 *   all waiting callers get [Outcome.Failed] once the timeout elapses and the next call may start
 *   a new upgrade. A late success from the timed-out upgrade is still honored: it completes any
 *   upgrade that was started since for the same user.
 *
 * Logcat: `adb logcat -s DPoPMigration`
 */
object DpopMigrationManager {

    /** Default time to wait for the SDK to report a result, in milliseconds. */
    const val DEFAULT_TIMEOUT_MS = 90_000L

    /** Reason carried by [Outcome.Failed] when the timeout elapses. */
    const val REASON_TIMED_OUT = "timed out"

    private const val TAG = "DPoPMigration"
    private const val REASON_NO_USER = "no user"
    private const val REASON_ALREADY_DPOP = "already DPoP"

    /** The result of a migration request. */
    sealed class Outcome {
        /** Nothing to do: there is no user, or the user is already DPoP-bound. */
        data class NotNeeded(val reason: String) : Outcome()

        /** The refresh token was upgraded to a DPoP-bound one. */
        data object Migrated : Outcome()

        /** The upgrade failed or timed out. The existing session is untouched. */
        data class Failed(val reason: String) : Outcome()
    }

    /** Java-friendly completion callback, invoked once on the main thread. */
    fun interface Callback {
        fun onComplete(outcome: Outcome)
    }

    /**
     * Seam over [upgradeToDPoP] so tests can drive the SDK callbacks directly.
     * Not part of the public API.
     */
    @VisibleForTesting
    internal var upgrader: (
        UserAccount,
        (UserAccount) -> Unit,
        (String, String?, Throwable?) -> Unit,
    ) -> Unit = { user, onSuccess, onFailure ->
        UserAccountManager.getInstance().upgradeToDPoP(user, onSuccess, onFailure)
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    /** One run of the upgrade for one user: its waiting callers and its timeout task. */
    private class Attempt(val callers: MutableList<(Outcome) -> Unit>) {
        var timeoutTask: Runnable? = null
        var finished = false
    }

    // Main thread only. Key: "<orgId>:<userId>".
    private val inFlight = mutableMapOf<String, Attempt>()

    /**
     * Cheap, side-effect free check: would [migrateIfNeeded] start (or join) an upgrade now?
     * Returns true while an upgrade is already in flight for [user], so UI shown for it stays up.
     * Safe to call from any thread; the result is only a snapshot.
     */
    @JvmStatic
    @JvmOverloads
    fun isMigrationNeeded(
        user: UserAccount? = UserAccountManager.getInstance().currentUser,
    ): Boolean = user != null && (
        synchronized(inFlight) { inFlight.containsKey(keyOf(user)) } ||
            !DPoPKeyManager.isDPoPTokenType(user.tokenType)
        )

    /**
     * Upgrades [user] (default: the current user) to a DPoP-bound refresh token if needed.
     *
     * Can be called from any thread. The result is delivered on the main thread.
     *
     * @param user The user to upgrade, or null (yields [Outcome.NotNeeded]).
     * @param timeoutMs How long to wait for the SDK to report back. When calls are coalesced the
     * timeout of the call that started the upgrade applies. A value <= 0 disables the timeout.
     */
    suspend fun migrateIfNeeded(
        user: UserAccount? = UserAccountManager.getInstance().currentUser,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): Outcome = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            val completion: (Outcome) -> Unit = { outcome ->
                if (continuation.isActive) continuation.resumeWith(Result.success(outcome))
            }
            val attempt = startOrJoin(user, timeoutMs, completion)
            continuation.invokeOnCancellation {
                mainHandler.post { attempt?.callers?.remove(completion) }
            }
        }
    }

    /**
     * Callback flavor of [migrateIfNeeded] for Java callers. [callback] is invoked once on the
     * main thread.
     */
    @JvmStatic
    @JvmOverloads
    fun migrateIfNeeded(
        user: UserAccount? = UserAccountManager.getInstance().currentUser,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        callback: Callback,
    ) {
        runOnMain { startOrJoin(user, timeoutMs) { callback.onComplete(it) } }
    }

    // region Private

    /** Main thread only. Returns the attempt [completion] is waiting on, if any. */
    private fun startOrJoin(
        user: UserAccount?,
        timeoutMs: Long,
        completion: (Outcome) -> Unit,
    ): Attempt? {
        if (user == null) {
            SalesforceSDKLogger.i(TAG, "skipped: $REASON_NO_USER")
            completion(Outcome.NotNeeded(REASON_NO_USER))
            return null
        }
        val key = keyOf(user)

        synchronized(inFlight) { inFlight[key] }?.let {
            SalesforceSDKLogger.i(TAG, "upgrade already in flight, waiting for it")
            it.callers.add(completion)
            return it
        }

        if (DPoPKeyManager.isDPoPTokenType(user.tokenType)) {
            SalesforceSDKLogger.i(TAG, "skipped: $REASON_ALREADY_DPOP")
            completion(Outcome.NotNeeded(REASON_ALREADY_DPOP))
            return null
        }

        SalesforceSDKLogger.i(TAG, "upgrade needed, starting")
        // Callbacks and the timer capture THEIR attempt, so a stale callback from an old,
        // timed-out run can never complete the callers of a newer run.
        val attempt = Attempt(mutableListOf(completion))
        synchronized(inFlight) { inFlight[key] = attempt }
        if (timeoutMs > 0) {
            val task = Runnable {
                SalesforceSDKLogger.w(TAG, "timed out after ${timeoutMs}ms, existing session kept")
                finish(key, attempt, Outcome.Failed(REASON_TIMED_OUT))
            }
            attempt.timeoutTask = task
            mainHandler.postDelayed(task, timeoutMs)
        }

        // The SDK calls back off the main thread: hop to main before touching state.
        upgrader(
            user,
            {
                runOnMain {
                    if (attempt.finished) {
                        // Late success: callers already got Failed. A newer attempt for the same
                        // user is now moot since the user is DPoP-bound, so complete it too.
                        SalesforceSDKLogger.i(TAG, "late success after timeout")
                        synchronized(inFlight) { inFlight[key] }
                            ?.let { finish(key, it, Outcome.Migrated) }
                    } else {
                        SalesforceSDKLogger.i(TAG, "success")
                        finish(key, attempt, Outcome.Migrated)
                    }
                }
            },
            { error, errorDesc, throwable ->
                runOnMain {
                    if (attempt.finished) {
                        SalesforceSDKLogger.w(TAG, "late failure after timeout (ignored)", throwable)
                    } else {
                        SalesforceSDKLogger.w(TAG, "failed, existing session kept", throwable)
                        finish(key, attempt, Outcome.Failed(listOfNotNull(error, errorDesc).joinToString(": ")))
                    }
                }
            },
        )
        return attempt
    }

    /** Main thread only. Delivers [outcome] to everybody waiting on [attempt], at most once. */
    private fun finish(key: String, attempt: Attempt, outcome: Outcome) {
        if (attempt.finished) return
        attempt.finished = true
        attempt.timeoutTask?.let { mainHandler.removeCallbacks(it) }
        synchronized(inFlight) { if (inFlight[key] === attempt) inFlight.remove(key) }
        attempt.callers.toList().forEach { it(outcome) }
        attempt.callers.clear()
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    private fun keyOf(user: UserAccount) = "${user.orgId}:${user.userId}"

    /** Clears all state. Tests only. */
    @VisibleForTesting
    internal fun resetForTesting() {
        synchronized(inFlight) {
            inFlight.values.forEach { a -> a.timeoutTask?.let { mainHandler.removeCallbacks(it) } }
            inFlight.clear()
        }
    }

    // endregion
}
