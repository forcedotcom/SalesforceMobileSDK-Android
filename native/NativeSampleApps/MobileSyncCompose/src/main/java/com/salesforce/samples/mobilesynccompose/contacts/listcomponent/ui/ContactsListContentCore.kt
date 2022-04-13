package com.salesforce.samples.mobilesynccompose.contacts.listcomponent.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.salesforce.samples.mobilesynccompose.contacts.detailscomponent.toUiSyncState
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.SObjectRecord
import com.salesforce.samples.mobilesynccompose.core.ui.components.LoadingOverlay
import com.salesforce.samples.mobilesynccompose.model.contacts.ContactObject

@Composable
fun ContactsListContent(
    modifier: Modifier = Modifier,
    contactRecords: List<SObjectRecord<ContactObject>>,
    showLoadingOverlay: Boolean,
    listContactClick: (id: String) -> Unit,
    listDeleteClick: (id: String) -> Unit,
    listEditClick: (id: String) -> Unit,
    listUndeleteClick: (id: String) -> Unit,
) {
    LazyColumn(modifier = modifier) {
        items(items = contactRecords, key = { it.id }) { record ->
            ContactCard(
                modifier = Modifier.padding(4.dp),
                startExpanded = false,
                model = record.sObject,
                syncState = record.localStatus.toUiSyncState(),
                onCardClick = { listContactClick(record.id) },
                onDeleteClick = { listDeleteClick(record.id) },
                onUndeleteClick = { listUndeleteClick(record.id) },
                onEditClick = { listEditClick(record.id) },
            )
        }
    }

    if (showLoadingOverlay) {
        LoadingOverlay()
    }
}
