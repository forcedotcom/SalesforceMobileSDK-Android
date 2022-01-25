package com.salesforce.samples.mobilesynccompose.core.ui.components

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme

@Composable
fun ToggleableEditTextField(
    modifier: Modifier = Modifier,
    fieldValue: String,
    isEditEnabled: Boolean,
    isError: Boolean = false,
    onValueChange: (String) -> Unit = {},
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    help: (@Composable () -> Unit)? = null,
) {
    val borderColor =
        if (!isEditEnabled)
            TextFieldDefaults.outlinedTextFieldColors(
                disabledBorderColor = Color.Transparent,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                errorBorderColor = Color.Transparent
            )
        else
            TextFieldDefaults.outlinedTextFieldColors()

    Column(modifier = modifier) {
        OutlinedTextField(
            value = fieldValue,
            onValueChange = onValueChange,
            label = label,
            placeholder = placeholder,
            readOnly = !isEditEnabled,
            isError = isError,
            colors = borderColor,
        )
        if (help != null) {
            val localContentColor = if (isError)
                MaterialTheme.colors.error
            else
                MaterialTheme.colors.onSurface.copy(alpha = 0.75f)

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
                ToggleableEditTextField(
                    modifier = Modifier.padding(8.dp),
                    fieldValue = "isEditEnabled = $isEditEnabled, isError = $isError",
                    isEditEnabled = isEditEnabled,
                    isError = isError,
                    label = { Text("Label") },
                    placeholder = { Text("Hint") },
                    help = { Text("Help Text Goes Here") }
                )

                isError = false

                ToggleableEditTextField(
                    modifier = Modifier.padding(8.dp),
                    fieldValue = "isEditEnabled = $isEditEnabled, isError = $isError",
                    isEditEnabled = isEditEnabled,
                    isError = isError,
                    label = { Text("Label") },
                    placeholder = { Text("Hint") }
                )

                isError = true
                isEditEnabled = false

                ToggleableEditTextField(
                    modifier = Modifier.padding(8.dp),
                    fieldValue = "isEditEnabled = $isEditEnabled, isError = $isError",
                    isEditEnabled = isEditEnabled,
                    isError = isError,
                    label = { Text("Label") },
                    placeholder = { Text("Hint") },
                    help = { Text("Help Text Goes Here") }
                )

                isError = false

                ToggleableEditTextField(
                    modifier = Modifier.padding(8.dp),
                    fieldValue = "isEditEnabled = $isEditEnabled, isError = $isError",
                    isEditEnabled = isEditEnabled,
                    isError = isError,
                    label = { Text("Label") },
                    placeholder = { Text("Hint") },
                    help = { Text("Help Text Goes Here") }
                )
            }
        }
    }
}
