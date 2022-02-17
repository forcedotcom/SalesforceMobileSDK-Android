package com.salesforce.samples.mobilesynccompose.contacts.ui.singlepane

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salesforce.samples.mobilesynccompose.R.string.*
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsListCoreEventHandler
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsListEventHandler
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsSearchEventHandler
import com.salesforce.samples.mobilesynccompose.contacts.ui.ContactCard
import com.salesforce.samples.mobilesynccompose.core.ui.components.ToggleableEditTextField
import com.salesforce.samples.mobilesynccompose.core.ui.theme.PurpleGrey40
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme
import com.salesforce.samples.mobilesynccompose.model.contacts.Contact

object SinglePaneContactsList {
    @Composable
    fun ViewingContactsList(
        modifier: Modifier = Modifier,
        contacts: List<Contact>,
        isSyncing: Boolean,
        handler: ContactsListCoreEventHandler
    ) {
        LazyColumn(modifier = modifier) {
            items(items = contacts, key = { it.id }) { contact ->
                ContactCard(
                    modifier = Modifier.padding(4.dp),
                    startExpanded = false,
                    contact = contact,
                    onCardClick = handler::listContactClick,
                    onDeleteClick = handler::listDeleteClick,
                    onEditClick = handler::listEditClick,
                )
            }
        }

        if (isSyncing) {
            LoadingOverlay()
        }
    }

    @Composable
    private fun LoadingOverlay() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = PurpleGrey40.copy(alpha = 0.25f)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val transition = rememberInfiniteTransition()
                val angle: Float by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 750,
                            easing = LinearEasing
                        ),
                    )
                )
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer { rotationZ = angle }
                )
            }
        }
    }

    object ScaffoldContent {
        @Composable
        fun RowScope.TopAppBarSearchMode(
            searchTerm: String,
            handler: ContactsSearchEventHandler
        ) {
            IconButton(onClick = { handler.exitSearch() }) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = stringResource(id = content_desc_back)
                )
            }
            ToggleableEditTextField(
                modifier = Modifier.weight(1f),
                fieldValue = searchTerm,
                isError = false, // cannot be in error state
                isEditEnabled = true,
                onValueChange = { handler.searchTermUpdated(it) }
            )
        }

        @Composable
        fun RowScope.TopAppBar() {
            Text(
                stringResource(id = label_contacts),
                modifier = Modifier.weight(1f)
            )
        }

        @Composable
        fun RowScope.BottomAppBarSearch() {
            // no content
        }

        @Composable
        fun RowScope.BottomAppBar(handler: ContactsListEventHandler) {
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = handler::searchClick) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = stringResource(id = content_desc_search)
                )
            }
        }

        @Composable
        fun Fab(handler: ContactsListCoreEventHandler) {
            FloatingActionButton(onClick = handler::listCreateClick) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(id = content_desc_add_contact)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ContactListContentPreview() {
    val TAG = "ContactListContentPreview"
    val contacts = (0..100).map {
        Contact.createNewLocal(
            firstName = "Contact",
            lastName = "$it",
            title = "Title $it",
        )
    }

    SalesforceMobileSDKAndroidTheme {
        Surface {
            SinglePaneContactsList.ViewingContactsList(
                modifier = Modifier.padding(4.dp),
                contacts = contacts,
                isSyncing = false,
                handler = object : ContactsListCoreEventHandler {
                    override fun listContactClick(contact: Contact) {
                        Log.d(TAG, "Clicked: $contact")
                    }

                    override fun listCreateClick() {
                        Log.d(TAG, "Create clicked")
                    }

                    override fun listDeleteClick(contact: Contact) {
                        Log.d(TAG, "Delete Clicked: $contact")
                    }

                    override fun listEditClick(contact: Contact) {
                        Log.d(TAG, "Edit Clicked: $contact")
                    }

                },
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ContactListSyncingPreview() {
    val TAG = "ContactListSyncingPreview"
    val contacts = (0..100).map {
        Contact.createNewLocal(
            firstName = "Contact",
            lastName = "$it",
            title = "Title $it",
        )
    }

    SalesforceMobileSDKAndroidTheme {
        Surface {
            SinglePaneContactsList.ViewingContactsList(
                modifier = Modifier.padding(4.dp),
                contacts = contacts,
                isSyncing = true,
                handler = object : ContactsListCoreEventHandler {
                    override fun listContactClick(contact: Contact) {
                        Log.d(TAG, "Clicked: $contact")
                    }

                    override fun listCreateClick() {
                        Log.d(TAG, "Create clicked")
                    }

                    override fun listDeleteClick(contact: Contact) {
                        Log.d(TAG, "Delete Clicked: $contact")
                    }

                    override fun listEditClick(contact: Contact) {
                        Log.d(TAG, "Edit Clicked: $contact")
                    }

                },
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ContactListLoadingPreview() {
    val TAG = "ContactListLoadingPreview"
    val contacts = emptyList<Contact>()

    SalesforceMobileSDKAndroidTheme {
        Surface {
            SinglePaneContactsList.ViewingContactsList(
                modifier = Modifier.padding(4.dp),
                contacts = contacts,
                isSyncing = true,
                handler = object : ContactsListCoreEventHandler {
                    override fun listContactClick(contact: Contact) {
                        Log.d(TAG, "Clicked: $contact")
                    }

                    override fun listCreateClick() {
                        Log.d(TAG, "Create clicked")
                    }

                    override fun listDeleteClick(contact: Contact) {
                        Log.d(TAG, "Delete Clicked: $contact")
                    }

                    override fun listEditClick(contact: Contact) {
                        Log.d(TAG, "Edit Clicked: $contact")
                    }
                },
            )
        }
    }
}
