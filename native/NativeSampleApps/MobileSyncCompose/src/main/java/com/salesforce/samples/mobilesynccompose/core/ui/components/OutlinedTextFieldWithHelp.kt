package com.salesforce.samples.mobilesynccompose.core.ui.components

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme

/**
 * A text field which extends the built-in [OutlinedTextField] to also have a "helper" text below
 * the input field.
 */
@Composable
fun OutlinedTextFieldWithHelp(
    fieldValue: String,
    isEditEnabled: Boolean,
    isError: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    help: (@Composable () -> Unit)? = null,
    maxLines: UInt = UInt.MAX_VALUE,
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            modifier = fieldModifier.then(Modifier.fillMaxWidth()),
            value = fieldValue,
            onValueChange = onValueChange,
            label = label,
            placeholder = placeholder,
            readOnly = !isEditEnabled,
            isError = isError,
            enabled = isEditEnabled,
            maxLines = maxLines.toInt().coerceIn(1..Int.MAX_VALUE),
        )
        if (help != null) {
            val localContentColor = when {
                isError -> MaterialTheme.colors.error
                isEditEnabled -> LocalContentColor.current
                else -> LocalContentColor.current.copy(ContentAlpha.disabled)
            }

            val textStyle = MaterialTheme.typography.caption.copy(color = localContentColor)

            CompositionLocalProvider(
                LocalContentColor provides localContentColor,
                LocalTextStyle provides textStyle
            ) {
                help()
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LabeledTextFieldPreview() {
    SalesforceMobileSDKAndroidTheme {
        Surface {
            Column {
                var isEditEnabled = true
                var isError = true
                OutlinedTextFieldWithHelp(
                    modifier = Modifier.padding(8.dp),
                    fieldModifier = Modifier.fillMaxWidth(),
                    fieldValue = "isEditEnabled = $isEditEnabled, isError = $isError",
                    isEditEnabled = isEditEnabled,
                    isError = isError,
                    label = { Text("Label") },
                    placeholder = { Text("Hint") },
                    help = { Text("Help Text Goes Here") },
                    onValueChange = {}
                )

                isError = false

                OutlinedTextFieldWithHelp(
                    modifier = Modifier.padding(8.dp),
                    fieldModifier = Modifier.fillMaxWidth(),
                    fieldValue = "isEditEnabled = $isEditEnabled, isError = $isError",
                    isEditEnabled = isEditEnabled,
                    isError = isError,
                    label = { Text("Label") },
                    placeholder = { Text("Hint") },
                    help = { Text("Help Text Goes Here") },
                    onValueChange = {}
                )

                isError = true
                isEditEnabled = false

                OutlinedTextFieldWithHelp(
                    modifier = Modifier.padding(8.dp),
                    fieldModifier = Modifier.fillMaxWidth(),
                    fieldValue = "isEditEnabled = $isEditEnabled, isError = $isError",
                    isEditEnabled = isEditEnabled,
                    isError = isError,
                    label = { Text("Label") },
                    placeholder = { Text("Hint") },
                    help = { Text("Help Text Goes Here") },
                    onValueChange = {}
                )

                isError = false

                OutlinedTextFieldWithHelp(
                    modifier = Modifier.padding(8.dp),
                    fieldModifier = Modifier.fillMaxWidth(),
                    fieldValue = "isEditEnabled = $isEditEnabled, isError = $isError",
                    isEditEnabled = isEditEnabled,
                    isError = isError,
                    label = { Text("Label") },
                    placeholder = { Text("Hint") },
                    help = { Text("Help Text Goes Here") },
                    onValueChange = {}
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OverflowPreview() {
    OutlinedTextFieldWithHelp(
        fieldValue = "OverflowOverflowOverflowOverflowOverflowOverflowOverflowOverflowOverflowOverflowOverflow",
        isEditEnabled = false,
        isError = false,
        onValueChange = {},
        maxLines = 1u
    )
}
