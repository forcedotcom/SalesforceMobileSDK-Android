package com.salesforce.samples.mobilesynccompose.core.ui.components

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.salesforce.samples.mobilesynccompose.core.ui.state.WINDOW_SIZE_COMPACT_CUTOFF_DP
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme

@Composable
fun FloatingTextEntryBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    elevation: Dp = 4.dp,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions(),
) {
    val shape = RoundedCornerShape(CornerSize(percent = 50))
    Surface(modifier = modifier, shape = shape, elevation = elevation) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = fieldModifier,
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle,
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            isError = isError,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            maxLines = 1,
            singleLine = true,
            colors = TextFieldDefaults.textFieldColors(backgroundColor = Color.Transparent)
        )
    }
}

@Preview(showBackground = true, widthDp = WINDOW_SIZE_COMPACT_CUTOFF_DP)
@Preview(showBackground = true, widthDp = WINDOW_SIZE_COMPACT_CUTOFF_DP, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun FloatingSearchBarPreview() {
    SalesforceMobileSDKAndroidTheme {
        Surface {
            Column {
                FloatingTextEntryBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    value = "Hello, World!",
                    onValueChange = {},
                    placeholder = { Text("Placeholder") },
                    leadingIcon = { Icon(Icons.Default.Check, contentDescription = "") },
                )

                FloatingTextEntryBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    value = "",
                    onValueChange = {},
                    placeholder = { Text("Placeholder") },
                    trailingIcon = {
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Clear, contentDescription = "")
                        }
                    },
                )

                FloatingTextEntryBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    value = "Error!",
                    onValueChange = {},
                    placeholder = { Text("Placeholder") },
                    leadingIcon = { Icon(Icons.Default.Warning, contentDescription = "") },
                    trailingIcon = {
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Clear, contentDescription = "")
                        }
                    },
                    isError = true
                )
            }
        }
    }
}
