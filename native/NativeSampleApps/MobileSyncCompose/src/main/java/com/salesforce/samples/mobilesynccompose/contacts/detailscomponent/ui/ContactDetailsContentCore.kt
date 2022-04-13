package com.salesforce.samples.mobilesynccompose.contacts.detailscomponent.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.salesforce.samples.mobilesynccompose.R
import com.salesforce.samples.mobilesynccompose.contacts.detailscomponent.ContactDetailsUiState
import com.salesforce.samples.mobilesynccompose.core.ui.components.LoadingOverlay
import com.salesforce.samples.mobilesynccompose.core.ui.state.SObjectUiSyncState
import com.salesforce.samples.mobilesynccompose.core.vm.EditableTextFieldUiState

@Composable
fun ContactDetailsContent(
    details: ContactDetailsUiState,
    modifier: Modifier = Modifier,
) {
    when (details) {
        is ContactDetailsUiState.ViewingContactDetails -> ContactDetailsViewingContact(
            modifier = modifier,
            details = details
        )
        is ContactDetailsUiState.NoContactSelected -> ContactDetailsNoContactSelected()
    }
}

@Composable
private fun ContactDetailsViewingContact(
    modifier: Modifier = Modifier,
    details: ContactDetailsUiState.ViewingContactDetails
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .verticalScroll(state = scrollState)
    ) {
        if (details.uiSyncState == SObjectUiSyncState.Deleted) {
            LocallyDeletedRow()
        }

        details.firstNameField.OutlinedTextFieldWithHelp(isEditingEnabled = details.isEditingEnabled)
        details.lastNameField.OutlinedTextFieldWithHelp(isEditingEnabled = details.isEditingEnabled)
        details.titleField.OutlinedTextFieldWithHelp(isEditingEnabled = details.isEditingEnabled)
        details.departmentField.OutlinedTextFieldWithHelp(isEditingEnabled = details.isEditingEnabled)
    }

    if (details.dataOperationIsActive) {
        LoadingOverlay()
    }

    details.curDialogUiState?.RenderDialog(modifier = Modifier)
}

@Composable
private fun ContactDetailsNoContactSelected() {
    // TODO use string res for "no contact selected"
    Box(modifier = Modifier.fillMaxSize()) {
        Text(text = "No contact selected.", modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun EditableTextFieldUiState.OutlinedTextFieldWithHelp(isEditingEnabled: Boolean) {
    com.salesforce.samples.mobilesynccompose.core.ui.components.OutlinedTextFieldWithHelp(
        fieldValue = fieldValue,
        isEditEnabled = isEditingEnabled && fieldIsEnabled,
        isError = isInErrorState,
        onValueChange = onValueChange,
        label = { labelRes?.let { Text(stringResource(id = it)) } },
        help = { helperRes?.let { Text(stringResource(id = it)) } },
        placeholder = { placeholderRes?.let { Text(stringResource(id = it)) } }
    )
}

@Composable
private fun LocallyDeletedRow() {
    var infoIsExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { infoIsExpanded = true }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colors.error) {
            Text(stringResource(id = R.string.label_locally_deleted))
            Icon(
                painterResource(id = R.drawable.ic_help),
                contentDescription = stringResource(id = R.string.content_desc_help),
                modifier = Modifier
                    .size(32.dp)
                    .padding(8.dp)
            )
        }
    }
    if (infoIsExpanded) {
        LocallyDeletedInfoDialog(onDismiss = { infoIsExpanded = false })
    }
}

@Composable
private fun LocallyDeletedInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(id = android.R.string.ok)) }
        },
        text = { Text(stringResource(id = R.string.body_locally_deleted_info)) }
    )
}
