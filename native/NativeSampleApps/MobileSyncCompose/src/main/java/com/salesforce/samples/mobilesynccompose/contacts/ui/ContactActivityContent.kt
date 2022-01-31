package com.salesforce.samples.mobilesynccompose.contacts.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactActivityState.*
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactActivityViewModel
import com.salesforce.samples.mobilesynccompose.core.ui.LayoutRestrictions

@Composable
fun ContactActivityContent(
    layoutRestrictions: LayoutRestrictions,
    vm: ContactActivityViewModel
) {
    val activityState = vm.activityState.collectAsState()

    when (val state = activityState.value) {
        is CreateNewContact -> CreateContactLayout(
            vm = vm,
            activityState = state
        )
        is EditContactDetails -> EditContactLayout(
            vm = vm,
            activityState = state
        )
        is ViewContactDetails -> ViewContactLayout(
            activityState = state,
            onAddClick = vm::createNewContact,
            onContactClick = vm::viewContact,
            onContactDelete = vm::deleteContact,
            onContactEdit = vm::editContact,
            onMenuClick = { TODO("onMenuClick") },
            onSearchClick = vm::showSearch,
        )
    }
}

@Composable
private fun CreateContactLayout(
    vm: ContactActivityViewModel,
    activityState: CreateNewContact
) {

}

@Composable
private fun EditContactLayout(
    vm: ContactActivityViewModel,
    activityState: EditContactDetails
) {

}

@Composable
private fun ViewContactLayout(
    activityState: ViewContactDetails,
    onAddClick: () -> Unit,
    onContactClick: (TempContactObject) -> Unit,
    onContactDelete: (TempContactObject) -> Unit,
    onContactEdit: (TempContactObject) -> Unit,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
) {
    when {
        activityState.selectedContact != null -> TODO("Contact Details: Selected Contact = ${activityState.selectedContact}")
        activityState.isSearchMode -> TODO("Search Mode")
        else -> ContactListScreen(
            activityState = activityState,
            onAddClick = onAddClick,
            onContactClick = onContactClick,
            onContactDeleteClick = onContactDelete,
            onContactEditClick = onContactEdit,
            onMenuClick = onMenuClick,
            onSearchClick = onSearchClick
        )
    }
}
