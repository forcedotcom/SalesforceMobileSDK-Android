package com.salesforce.samples.mobilesynccompose.contacts.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Card
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salesforce.samples.mobilesynccompose.R
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactDetailFieldViewModel
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactDetailsChangedHandler
import com.salesforce.samples.mobilesynccompose.core.ui.components.ToggleableEditTextField
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme

object ContactDetailContent {
    @Composable
    fun Empty() {
        Box(contentAlignment = Alignment.Center) {
            Text("No contact selected")
        }
    }

    @Composable
    fun Compact(
        modifier: Modifier = Modifier,
        nameVm: ContactDetailFieldViewModel,
        titleVm: ContactDetailFieldViewModel,
        contactDetailsChangedHandler: ContactDetailsChangedHandler,
        isEditMode: Boolean,
    ) {
        val scrollState = rememberScrollState()

        Column(
            modifier = modifier
                .scrollable(
                    state = scrollState,
                    orientation = Orientation.Vertical
                )
                .padding(horizontal = 8.dp)
        ) {
            ToggleableEditTextField(
                fieldValue = nameVm.fieldValue,
                isError = nameVm.isInErrorState,
                isEditEnabled = isEditMode,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                fieldModifier = Modifier.fillMaxWidth(),
                onValueChange = contactDetailsChangedHandler::onNameChanged,
                label = { Text(stringResource(id = R.string.label_contact_name)) },
                placeholder = { Text(stringResource(id = R.string.label_contact_name)) },
                help = { Text(nameVm.helperRes?.let { stringResource(id = it) } ?: "") }
            )
            ToggleableEditTextField(
                fieldValue = titleVm.fieldValue,
                isError = titleVm.isInErrorState,
                isEditEnabled = isEditMode,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                fieldModifier = Modifier.fillMaxWidth(),
                onValueChange = contactDetailsChangedHandler::onTitleChanged,
                label = { Text(stringResource(id = R.string.label_contact_title)) },
                placeholder = { Text(stringResource(id = R.string.label_contact_title)) },
                help = { Text(titleVm.helperRes?.let { stringResource(id = it) } ?: "") }
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 500)
@Preview(showBackground = true, widthDp = 500, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ContactDetailContentPreview() {
    SalesforceMobileSDKAndroidTheme {
        Surface(modifier = Modifier.fillMaxWidth()) {
            Column {
                Card(modifier = Modifier.padding(4.dp)) {
                    ContactDetailContentPreview(isEditMode = true, hasHelp = true)
                }
                Card(modifier = Modifier.padding(4.dp)) {
                    ContactDetailContentPreview(isEditMode = false, hasHelp = true)
                }
                Card(modifier = Modifier.padding(4.dp)) {
                    ContactDetailContentPreview(isEditMode = true, hasHelp = false)
                }
                Card(modifier = Modifier.padding(4.dp)) {
                    ContactDetailContentPreview(isEditMode = false, hasHelp = false)
                }
            }
        }
    }
}

@Composable
private fun ContactDetailContentPreview(isEditMode: Boolean, hasHelp: Boolean) {
    var nameVm: ContactDetailFieldViewModel by remember {
        mutableStateOf(
            ContactDetailFieldViewModel(
                fieldValue = "Name",
                isInErrorState = true,
                helperRes = if (hasHelp) R.string.help_cannot_be_blank else null
            )
        )
    }
    var titleVm: ContactDetailFieldViewModel by remember {
        mutableStateOf(
            ContactDetailFieldViewModel(
                fieldValue = "Title",
                isInErrorState = false,
                helperRes = if (hasHelp) R.string.help_cannot_be_blank else null
            )
        )
    }

    val onValueChangedHandler = object : ContactDetailsChangedHandler {
        override fun onNameChanged(newValue: String) {
            val isBlank = newValue.isBlank()
            nameVm = nameVm.copy(
                fieldValue = newValue,
                isInErrorState = isBlank,
                helperRes = if (hasHelp) R.string.help_cannot_be_blank else null
            )
        }

        override fun onTitleChanged(newValue: String) {
            titleVm = titleVm.copy(fieldValue = newValue)
        }
    }

    ContactDetailContent.Compact(
        nameVm = nameVm,
        titleVm = titleVm,
        contactDetailsChangedHandler = onValueChangedHandler,
        isEditMode = isEditMode,
    )
}
