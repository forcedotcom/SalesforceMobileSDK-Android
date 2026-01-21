/*
 * Copyright (c) 2025-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.auth

import android.text.TextUtils

/**
 * Utility class for parsing and working with OAuth2 scopes.
 */
class ScopeParser {

    companion object {
        const val REFRESH_TOKEN = "refresh_token"
        const val ID = "id"

        /**
         * Factory method that creates a ScopeParser from a space-delimited scope string.
         *
         * @param scopeString Space-delimited scope string.
         * @return ScopeParser instance.
         */
        @JvmStatic
        fun parseScopes(scopeString: String?): ScopeParser {
            return ScopeParser(scopeString)
        }

        /**
         * String extension to convert to [ScopeParser].
         */
        fun String?.toScopeParser(): ScopeParser = ScopeParser(scopeString = this)

        /**
         * Computes the scope parameter from an array of scopes.
         *
         * Behavior:
         * - If {@code scopes} is null or empty, returns an empty string. This indicates that all
         *   scopes assigned to the connected app / external client app will be requested by default
         *   (no explicit scope parameter is sent).
         * - If {@code scopes} is non-empty, ensures {@code refresh_token} is present in the set and
         *   returns a space-delimited string of unique, sorted scopes.
         *
         * @param scopes Array of scopes.
         * @return Scope parameter string (possibly empty).
         */
        @JvmStatic
        fun computeScopeParameter(scopes: Array<String>?): String {
            // If no scopes are provided, return an empty string. This indicates that all scopes
            // assigned to the connected app / external client app will be requested by default.
            if (scopes.isNullOrEmpty()) {
                return ""
            }

            // When explicit scopes are provided, ensure REFRESH_TOKEN is included.
            val scopesSet = scopes.toSortedSet()
            scopesSet.add(REFRESH_TOKEN)
            return scopesSet.joinToString(" ")
        }

        /**
         *  Computes the scope parameter from an array of scopes.
         *
         * Behavior:
         * - If {@code scopes} is null or empty, returns an empty string. This indicates that all
         *   scopes assigned to the connected app / external client app will be requested by default
         *   (no explicit scope parameter is sent).
         * - If {@code scopes} is non-empty, ensures {@code refresh_token} is present in the set and
         *   returns a space-delimited string of unique, sorted scopes.
         */
        fun Array<String>?.toScopeParameter(): String = computeScopeParameter(this)
    }

    private val _scopes: MutableSet<String>

    /**
     * Constructor that takes an array of scopes.
     *
     * @param scopes Array of scopes.
     */
    constructor(scopes: Array<String>?) {
        this._scopes = sortedSetOf()
        scopes?.forEach { scope ->
            if (!TextUtils.isEmpty(scope)) {
                this._scopes.add(scope.trim())
            }
        }
    }

    /**
     * Constructor that takes a space-delimited scope string.
     *
     * @param scopeString Space-delimited scope string.
     */
    constructor(scopeString: String?) {
        this._scopes = scopeString
            ?.trim()
            ?.split("\\s+".toRegex())
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toMutableSet() ?: mutableSetOf()
    }


    /**
     * Checks whether the provided scope exists in this parser's scope set.
     *
     * @param scope Scope name to check.
     * @return True if present, false otherwise.
     */
    fun hasScope(scope: String?): Boolean {
        return scope?.isNotBlank() == true && _scopes.contains(scope.trim())
    }

    /**
     * Checks whether the refresh_token scope exists in this parser's scope set.
     *
     * @return True if refresh_token scope is present, false otherwise.
     */
    fun hasRefreshTokenScope(): Boolean {
        return hasScope(REFRESH_TOKEN)
    }

    /**
     * Checks whether the id scope exists in this parser's scope set.
     *
     * @return True if id scope is present, false otherwise.
     */
    fun hasIdentityScope(): Boolean {
        return hasScope(ID)
    }

    /**
     * Returns the set of scopes.
     *
     * @return Set of scopes.
     */
    val scopes: Set<String>
        get() = _scopes.toSet()

    /**
     * Returns the scopes as a space-delimited string.
     *
     * @return Space-delimited scope string.
     */
    val scopesAsString: String
        get() = if (_scopes.isEmpty()) {
            ""
        } else {
            _scopes.sorted().joinToString(" ")
        }
}
