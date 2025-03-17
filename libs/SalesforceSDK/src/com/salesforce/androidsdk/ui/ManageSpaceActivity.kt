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
package com.salesforce.androidsdk.ui

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons.Default
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults.centerAlignedTopAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow.Companion.Ellipsis
import androidx.compose.ui.tooling.preview.Preview
import com.salesforce.androidsdk.R.string.app_name
import com.salesforce.androidsdk.R.string.sf__manage_space_confirmation
import com.salesforce.androidsdk.R.string.sf__manage_space_logout_no
import com.salesforce.androidsdk.R.string.sf__manage_space_logout_yes
import com.salesforce.androidsdk.R.string.sf__manage_space_title
import com.salesforce.androidsdk.app.SalesforceSDKManager.Companion.getInstance
import com.salesforce.androidsdk.auth.OAuth2.LogoutReason.USER_LOGOUT

/**
 * An activity which prompts the user to clear storage and informs the user that
 * this will log out all users.
 *
 * @author Johnson.Eric@Salesforce.com
 */
open class ManageSpaceActivity : ComponentActivity() {

    // region Activity Implementation

    public override fun onCreate(savedInstanceState: Bundle?) {

        enableEdgeToEdge()

        super.onCreate(savedInstanceState)

        // Set content
        setContent {
            MaterialTheme(colorScheme = getInstance().colorScheme()) {
                ManageSpaceView()
            }
        }
    }

    // endregion
    // region Manage Space Activity Composable Functions

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @Composable
    @Preview
    fun ManageSpaceView() {

        Scaffold(
            topBar = { ManageSpaceTopBar() }
        ) {}

        ClearStoragePromptAlertDialog(
            onDismiss = {
                finish()
            },
            onConfirm = {
                getInstance().logout(
                    account = null,
                    frontActivity = this@ManageSpaceActivity,
                    showLoginPage = false,
                    reason = USER_LOGOUT
                )
            },
            titleText = stringResource(sf__manage_space_title),
            textText = stringResource(sf__manage_space_confirmation),
            confirmButtonText = stringResource(sf__manage_space_logout_yes),
            dismissButtonText = stringResource(sf__manage_space_logout_no),
            icon = Default.Info
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    open fun ManageSpaceTopBar() {
        CenterAlignedTopAppBar(
            colors = centerAlignedTopAppBarColors(
                containerColor = colorScheme.primaryContainer,
                titleContentColor = colorScheme.primary,
            ), title = {
                Text(
                    stringResource(app_name),
                    maxLines = 1,
                    overflow = Ellipsis
                )
            }
        )
    }

    /**
     * The clear storage prompt alert dialog.
     *
     * @param onDismiss An action when the dialog is dismissed
     * @param onConfirm An action when the dialog is confirmed
     * @param titleText The dialog title text
     * @param textText The dialog body text
     * @param confirmButtonText The confirmation button text
     * @param dismissButtonText The dismiss button text
     * @param icon The dialog's hero icon
     */
    @Composable
    open fun ClearStoragePromptAlertDialog(
        onDismiss: () -> Unit,
        onConfirm: () -> Unit,
        titleText: String,
        textText: String,
        confirmButtonText: String,
        dismissButtonText: String,
        icon: ImageVector
    ) {
        AlertDialog(
            icon = {
                Icon(icon, contentDescription = "Icon")
            }, title = {
                Text(text = titleText)
            }, text = {
                Text(text = textText)
            }, onDismissRequest = {
                onDismiss()
            }, confirmButton = {
                TextButton(onClick = {
                    onConfirm()
                }) {
                    Text(confirmButtonText)
                }
            }, dismissButton = {
                TextButton(onClick = {
                    onDismiss()
                }) {
                    Text(dismissButtonText)
                }
            })
    }
}
