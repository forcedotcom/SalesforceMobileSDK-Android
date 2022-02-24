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
package com.salesforce.samples.mobilesynccompose.core.ui.components

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.salesforce.samples.mobilesynccompose.R
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme

/**
 * A simple icon button used to toggle between an "expanded" state and a "collapsed" state. Use its
 * [onExpandoChangeListener] callback to drive expanded/collapsed state of parent content.
 */
@Composable
fun ExpandoButton(
    modifier: Modifier = Modifier,
    startsExpanded: Boolean,
    isEnabled: Boolean,
    onExpandoChangeListener: (isExpanded: Boolean) -> Unit
) {
    var isExpanded by rememberSaveable { mutableStateOf(startsExpanded) }
    val iconRotation: Float by animateFloatAsState(
        targetValue = when {
            isExpanded -> 0f
            LocalLayoutDirection.current == LayoutDirection.Rtl -> -90f
            else -> 90f
        }
    )
    IconButton(
        onClick = {
            if (isEnabled) {
                isExpanded = !isExpanded
                onExpandoChangeListener(isExpanded)
            }
        },
        modifier = modifier,
        enabled = isEnabled
    ) {
        Icon(
            Icons.Default.ArrowDropDown,
            contentDescription = stringResource(id = R.string.content_desc_show_more),
            modifier = Modifier.rotate(iconRotation),
        )
    }
}

@Preview(showBackground = true)
@Preview(
    showBackground = true,
    uiMode = UI_MODE_NIGHT_YES
)
@Composable
fun ExpandoButtonPreview() {
    SalesforceMobileSDKAndroidTheme {
        Surface(modifier = Modifier.padding(4.dp)) {
            ExpandoButton(
                modifier = Modifier.padding(4.dp),
                startsExpanded = false,
                isEnabled = true
            ) {
                Log.d("ExpandoButtonPreview", "New expando state = $it")
            }
        }
    }
}
