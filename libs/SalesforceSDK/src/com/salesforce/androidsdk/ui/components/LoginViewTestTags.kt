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
package com.salesforce.androidsdk.ui.components

/**
 * Stable, locale-invariant test tags for the Mobile SDK login screen and the
 * server / account picker bottom sheet.
 *
 * Each value is applied to its Compose node via [androidx.compose.ui.platform.testTag]. Because
 * the composition roots that host these nodes opt in with
 * `Modifier.semantics { testTagsAsResourceId = true }`, the tags are surfaced to UI automation
 * (UIAutomator2 / UTAM) as Android `resource-id`s. Unlike the localized `contentDescription`
 * strings the UI also exposes, these values never change across device languages, so test and
 * page-object code can anchor on them in any locale — including right-to-left layouts.
 *
 * This object is the single source of truth for those values. It is intentionally public so that
 * SDK instrumented tests, sample-app page objects (e.g. AuthFlowTester), and external UTAM page
 * objects can all reference the same constants instead of duplicating the literal strings.
 *
 * IMPORTANT: These are test anchors only. Do NOT localize them and do NOT reuse them as
 * user-facing strings.
 */
object LoginViewTestTags {

    // region Login screen top bar
    /** Three-dot "More Options" overflow button in the login top app bar. */
    const val MORE_OPTIONS_BUTTON = "sf__more_options_button"

    /** Navigation "back" button in the login top app bar (shown conditionally). */
    const val BACK_BUTTON = "sf__back_button"
    // endregion

    // region Login screen overflow menu items
    /** "Change Server" overflow menu item. */
    const val MENU_ITEM_PICK_SERVER = "sf__menu_item_pick_server"

    /** "Clear Cookies" overflow menu item. */
    const val MENU_ITEM_CLEAR_COOKIES = "sf__menu_item_clear_cookies"

    /** "Clear Cache" overflow menu item. */
    const val MENU_ITEM_CLEAR_CACHE = "sf__menu_item_clear_cache"

    /** "Reload" overflow menu item. */
    const val MENU_ITEM_RELOAD = "sf__menu_item_reload"

    /** "Login for Admins" overflow menu item (shown conditionally). */
    const val MENU_ITEM_LOGIN_FOR_ADMINS = "sf__menu_item_login_for_admins"

    /** "Developer Support" overflow menu item (debug builds only). */
    const val MENU_ITEM_DEV_SUPPORT = "sf__menu_item_dev_support"
    // endregion

    // region Login screen body
    /** Bottom-bar login action button (biometric / IDP / custom). */
    const val LOGIN_BUTTON = "sf__login_button"

    /** Indeterminate loading spinner shown over the login WebView. */
    const val LOADING_INDICATOR = "sf__loading_indicator"
    // endregion

    // region Picker bottom sheet
    /** Root container of the login-server picker bottom sheet. */
    const val SERVER_PICKER = "sf__server_picker"

    /** Root container of the user-account picker bottom sheet. */
    const val ACCOUNT_PICKER = "sf__account_picker"

    /** Back arrow shown in the picker header while adding a new connection. */
    const val PICKER_BACK_BUTTON = "sf__picker_back_button"

    /** Close (X) button in the picker header. */
    const val PICKER_CLOSE_BUTTON = "sf__picker_close_button"

    /** "Add New Connection" button in the login-server picker. */
    const val CUSTOM_URL_BUTTON = "sf__custom_url_button"

    /** "Add New Account" button in the user-account picker. */
    const val ADD_NEW_ACCOUNT_BUTTON = "sf__add_new_account_button"

    /** Name field in the picker's "Add Connection" form. */
    const val PICKER_CUSTOM_LABEL = "sf__picker_custom_label"

    /** URL field in the picker's "Add Connection" form. */
    const val PICKER_CUSTOM_URL = "sf__picker_custom_url"

    /** Save / apply button in the picker's "Add Connection" form. */
    const val APPLY_BUTTON = "sf__apply_button"
    // endregion

    // region Server list item
    /**
     * Trash icon on a custom server row that arms the slide-to-delete affordance.
     *
     * Every custom server row carries this same tag by design, so a bare match resolves to
     * multiple nodes. To target a specific row, combine it with a content matcher that
     * identifies the row:
     * ```
     * onNode(hasTestTag(SERVER_DELETE_BUTTON) and hasAncestor(hasText("MyOrg")))
     * ```
     */
    const val SERVER_DELETE_BUTTON = "sf__server_delete_button"

    /**
     * Revealed confirm-delete target on a custom server row.
     *
     * Shared across all custom rows just like [SERVER_DELETE_BUTTON]; combine it with a content
     * matcher (e.g. `and hasAncestor(hasText("MyOrg"))`) to target a specific row.
     */
    const val SERVER_CONFIRM_DELETE_BUTTON = "sf__server_confirm_delete_button"
    // endregion
}
