package com.salesforce.samples.mobilesynccompose.contacts.ui.singlepane

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.salesforce.samples.mobilesynccompose.R
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsListUiEvents.ListNavUp
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsListUiEvents.SearchTermUpdated
import com.salesforce.samples.mobilesynccompose.contacts.state.ContactsListUiState
import com.salesforce.samples.mobilesynccompose.contacts.ui.ContactsActivityMenuButton
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactsActivityEventHandler
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactsListEventHandler
import com.salesforce.samples.mobilesynccompose.core.ui.components.ToggleableEditTextField

@Composable
fun SinglePaneContactsList(
    uiState: ContactsListUiState,
    listEventHandler: ContactsListEventHandler,
    activityEventHandler: ContactsActivityEventHandler
) {
    Scaffold(
        topBar = {
            TopAppBar {
                topAppBar(
                    uiState = uiState,
                    listEventHandler = listEventHandler,
                    activityEventHandler = activityEventHandler
                )
            }
        },
        bottomBar = {
            BottomAppBar(
                cutoutShape = MaterialTheme.shapes.small.copy(CornerSize(percent = 50)),
                content = bottomAppBarContent
            )
        },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = fabContent,
        isFloatingActionButtonDocked = true,
        content = mainContent
    )
}

@Composable
private fun RowScope.topAppBar(
    uiState: ContactsListUiState,
    listEventHandler: ContactsListEventHandler,
    activityEventHandler: ContactsActivityEventHandler
) {
    var handleBack = false
    when (uiState) {
        is ContactsListUiState.Searching -> {
            IconButton(onClick = { listEventHandler.handleEvent(ListNavUp) }) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = stringResource(id = R.string.content_desc_back)
                )
            }
            ToggleableEditTextField(
                modifier = Modifier.weight(1f),
                fieldValue = uiState.searchTerm,
                isError = false, // cannot be in error state
                isEditEnabled = true,
                onValueChange = { listEventHandler.handleEvent(SearchTermUpdated(newSearchTerm = it)) }
            )
            handleBack = true
        }

        ContactsListUiState.Loading,
        is ContactsListUiState.ViewingList -> {
            Text(
                stringResource(id = R.string.label_contacts),
                modifier = Modifier.weight(1f)
            )
        }
    }

    ContactsActivityMenuButton(handler = activityEventHandler)

    BackHandler(enabled = handleBack) { listEventHandler.handleEvent(ListNavUp) }
}
