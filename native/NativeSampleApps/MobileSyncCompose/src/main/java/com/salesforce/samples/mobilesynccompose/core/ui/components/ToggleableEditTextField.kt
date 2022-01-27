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

@Composable
fun ToggleableEditTextField(
    fieldValue: String,
    isEditEnabled: Boolean,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    isError: Boolean = false,
    onValueChange: (String) -> Unit = {},
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    help: (@Composable () -> Unit)? = null,
) {
    /* TODO I'm not sure what the best way to differentiate editable from not...  It would be great
        if the border was gone for non-editable, but then placeholder + label behavior is weird b/c
        the label takes the place of the placeholder until the field has focus. */
//    val colors =
//        if (!isEditEnabled)
//            TextFieldDefaults.outlinedTextFieldColors(
//                disabledTextColor = LocalContentColor.current.copy(
//                    LocalContentAlpha.current
//                )
//            )
//        else
//            TextFieldDefaults.outlinedTextFieldColors()

    Column(modifier = modifier) {
        OutlinedTextField(
            modifier = fieldModifier,
            value = fieldValue,
            onValueChange = onValueChange,
            label = label,
            placeholder = placeholder,
            readOnly = !isEditEnabled,
            isError = isError,
            enabled = isEditEnabled,
//            colors = colors,
        )
        if (help != null) {
            val localContentColor = when {
                isError -> MaterialTheme.colors.error
                isEditEnabled -> LocalContentColor.current
                else -> LocalContentColor.current.copy(ContentAlpha.disabled)
            }
//                MaterialTheme.colors.onSurface.copy(alpha = 0.75f)

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
                    fieldModifier = Modifier.fillMaxWidth(),
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
                    fieldModifier = Modifier.fillMaxWidth(),
                    fieldValue = "isEditEnabled = $isEditEnabled, isError = $isError",
                    isEditEnabled = isEditEnabled,
                    isError = isError,
                    label = { Text("Label") },
                    placeholder = { Text("Hint") },
                    help = { Text("Help Text Goes Here") }
                )

                isError = true
                isEditEnabled = false

                ToggleableEditTextField(
                    modifier = Modifier.padding(8.dp),
                    fieldModifier = Modifier.fillMaxWidth(),
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
                    fieldModifier = Modifier.fillMaxWidth(),
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
