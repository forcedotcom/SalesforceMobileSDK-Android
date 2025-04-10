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
package com.salesforce.androidsdk.rest

import com.salesforce.androidsdk.rest.SfapApiClient.Companion.jsonIgnoreUnknownKeys
import kotlinx.serialization.Serializable

/**
 * Models a Notifications API Types endpoint response.
 * See https://salesforce.quip.com/KGU3ALoXRCjK#RcfABAPLVfg
 * TODO: Replace the documentation link with the final documentation. ECJ20250310
 */
@Serializable
data class NotificationsTypesResponseBody(
    val notificationTypes: Array<NotificationType>? = null
) {

    /** The original JSON used to initialize this response body */
    var sourceJson: String? = null
        private set

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NotificationsTypesResponseBody

        if (notificationTypes != null) {
            if (other.notificationTypes == null) return false
            if (!notificationTypes.contentEquals(other.notificationTypes)) return false
        } else if (other.notificationTypes != null) return false

        return true
    }

    override fun hashCode(): Int {
        return notificationTypes?.contentHashCode() ?: 0
    }

    @Serializable
    data class NotificationType(
        val actionGroups: Array<ActionGroup>? = null,
        val apiName: String? = null,
        val label: String? = null,
        val type: String? = null
    ) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as NotificationType

            if (actionGroups != null) {
                if (other.actionGroups == null) return false
                if (!actionGroups.contentEquals(other.actionGroups)) return false
            } else if (other.actionGroups != null) return false
            if (apiName != other.apiName) return false
            if (label != other.label) return false
            if (type != other.type) return false

            return true
        }

        override fun hashCode(): Int {
            var result = actionGroups?.contentHashCode() ?: 0
            result = 31 * result + (apiName?.hashCode() ?: 0)
            result = 31 * result + (label?.hashCode() ?: 0)
            result = 31 * result + (type?.hashCode() ?: 0)
            return result
        }

        @Serializable
        data class ActionGroup(
            val name: String? = null,
            val actions: Array<Action>? = null
        ) {

            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as ActionGroup

                if (name != other.name) return false
                if (actions != null) {
                    if (other.actions == null) return false
                    if (!actions.contentEquals(other.actions)) return false
                } else if (other.actions != null) return false

                return true
            }

            override fun hashCode(): Int {
                var result = name?.hashCode() ?: 0
                result = 31 * result + (actions?.contentHashCode() ?: 0)
                return result
            }

            @Serializable
            data class Action(
                val actionKey: String? = null,
                val label: String? = null,
                val name: String? = null,
                val type: String? = null
            )
        }
    }

    // region Companion

    companion object {

        /**
         * Returns a Notification Types API endpoint response from the JSON
         * text.
         * @param json The JSON text
         * @return The Notification Types API endpoint response
         */
        fun fromJson(json: String): NotificationsTypesResponseBody {

            val result = jsonIgnoreUnknownKeys.decodeFromString<NotificationsTypesResponseBody>(json)
            result.sourceJson = json
            return result
        }
    }

    // endregion
}
