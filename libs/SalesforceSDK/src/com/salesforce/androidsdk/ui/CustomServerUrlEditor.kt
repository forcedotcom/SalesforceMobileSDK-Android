/*
 * Copyright (c) 2022-present, salesforce.com, inc.
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

import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.URLUtil
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.fragment.app.DialogFragment
import com.salesforce.androidsdk.R
import com.salesforce.androidsdk.app.SalesforceSDKManager
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Custom dialog fragment to allow the user to set a label and URL to use for the login.
 */
class CustomServerUrlEditor: DialogFragment() {
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    lateinit var rootView: View

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val isDarkTheme = SalesforceSDKManager.getInstance().isDarkTheme
        rootView = inflater.inflate(R.layout.sf__custom_server_url, container)
        rootView.context.setTheme(if (isDarkTheme) R.style.SalesforceSDK_Dialog_Dark else R.style.SalesforceSDK_Dialog)
        dialog?.setTitle(R.string.sf__server_url_add_title)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Apply Button
        rootView.findViewById<Button>(R.id.sf__apply_button).setOnClickListener {
            val label = validateInput(R.id.sf__picker_custom_label)
            if (label == null) {
                Toast.makeText(context, getString(R.string.sf__invalid_server_name), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val url = validateInput(R.id.sf__picker_custom_url)
            if (url == null) {
                Toast.makeText(context, getString(R.string.sf__invalid_server_url), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            SalesforceSDKManager.getInstance().loginServerManager.addCustomLoginServer(label.trim(), url.trim())
            rootView.findViewById<EditText>(R.id.sf__picker_custom_label).text.clear()
            rootView.findViewById<EditText>(R.id.sf__picker_custom_url).text.clear()
            dismiss()
        }

        // Cancel Button
        rootView.findViewById<Button>(R.id.sf__cancel_button).setOnClickListener { dismiss() }

        return rootView
    }

    override fun onDismiss(dialog: DialogInterface) {
        (activity as ServerPickerActivity).rebuildDisplay()
        super.onDismiss(dialog)
    }

    private fun validateInput(label: Int): String? {
        val editText = rootView.findViewById<EditText>(label)
        var text = editText.text.toString()
        val defaultValue = if (label == R.id.sf__picker_custom_label) {
            getString(R.string.sf__server_url_default_custom_label)
        } else {
            getString(R.string.sf__server_url_default_custom_url)
        }

        if (text == defaultValue || text.isBlank()) {
            editText.selectAll()
            editText.requestFocus()
            return null
        }

        if (label == R.id.sf__picker_custom_url) {
            // Ensures that the URL is a 'https://' URL, since OAuth requires 'https://'.
            if (!URLUtil.isHttpsUrl(text)) {
                text = if (URLUtil.isHttpUrl(text)) {
                    text.replace("http://", "https://")
                } else {
                    "https://$text"
                }
            }

            if (text.toHttpUrlOrNull() != null && text.contains(".")) {
                return text
            }

            return null
        }

        return text
    }
}