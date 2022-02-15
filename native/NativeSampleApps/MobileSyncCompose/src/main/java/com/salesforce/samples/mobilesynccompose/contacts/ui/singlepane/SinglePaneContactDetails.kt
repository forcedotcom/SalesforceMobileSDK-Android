package com.salesforce.samples.mobilesynccompose.contacts.ui.singlepane

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salesforce.samples.mobilesynccompose.R
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactDetailUiEvents.*
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsActivityUiEvents.*
import com.salesforce.samples.mobilesynccompose.contacts.state.*
import com.salesforce.samples.mobilesynccompose.contacts.ui.ContactDetailContent
import com.salesforce.samples.mobilesynccompose.contacts.ui.ContactsActivityMenuButton
import com.salesforce.samples.mobilesynccompose.contacts.ui.fixForMainContent
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactDetailEventHandler
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactsActivityEventHandler
import com.salesforce.samples.mobilesynccompose.core.ui.theme.PurpleGrey40
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme
import com.salesforce.samples.mobilesynccompose.model.contacts.Contact

@Composable
fun SinglePaneContactDetails(
    uiState: ContactDetailUiState,
    activityEventHandler: ContactsActivityEventHandler,
    detailEventHandler: ContactDetailEventHandler
) {
    Scaffold(
        topBar = {
            TopAppBarContent(
                uiState = uiState,
                activityEventHandler = activityEventHandler,
                detailEventHandler = detailEventHandler,
            )
        },
        bottomBar = {
            BottomAppBarContent(
                uiState = uiState,
                activityEventHandler = activityEventHandler
            )
        },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            FabContent(
                uiState = uiState,
                activityEventHandler = activityEventHandler,
                detailEventHandler = detailEventHandler
            )
        },
        isFloatingActionButtonDocked = true,
    ) { paddingVals ->
        val fixedPadding = paddingVals.fixForMainContent()
        Box {
            when (uiState) {
                is EditMode -> {
                    ContactDetailContent.CompactEditMode(
                        state = uiState,
                        detailEventHandler = detailEventHandler,
                        modifier = Modifier.padding(fixedPadding)
                    )
                    when (uiState) {
                        is EditMode.Saving -> LoadingContent()
                        is EditMode.DiscardChanges -> TODO()
                        is EditMode.EditingContact -> {
                            /* no-op */
                        }
                    }
                }
                NoContactSelected -> ContactDetailContent.Empty()
                is ViewingContact -> ContactDetailContent.CompactViewMode(
                    state = uiState,
                    modifier = Modifier.padding(fixedPadding)
                )
            }
        }
        BackHandler { detailEventHandler.handleEvent(DetailNavBack) }
    }
}

@Composable
private fun TopAppBarContent(
    uiState: ContactDetailUiState,
    activityEventHandler: ContactsActivityEventHandler,
    detailEventHandler: ContactDetailEventHandler
) {
    TopAppBar {
        IconButton(onClick = { detailEventHandler.handleEvent(DetailNavUp) }) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = stringResource(id = R.string.content_desc_back)
            )
        }

        val label = when (uiState) {
            is EditMode -> {
                "${uiState.firstNameVm.fieldValue} ${uiState.lastNameVm.fieldValue}"
            }

            NoContactSelected -> stringResource(id = R.string.label_contact_details)

            is ViewingContact -> {
                "${uiState.firstNameVm.fieldValue} ${uiState.lastNameVm.fieldValue}"
            }
        }

        Text(label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)

        ContactsActivityMenuButton(handler = activityEventHandler)
    }
}

@Composable
private fun BottomAppBarContent(
    uiState: ContactDetailUiState,
    activityEventHandler: ContactsActivityEventHandler
) {
    if (uiState !is NoContactSelected) {
        BottomAppBar {
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = {
                when (uiState) {
                    is EditMode -> activityEventHandler.handleEvent(ContactDelete(uiState.originalContact))
                    NoContactSelected -> {}
                    is ViewingContact -> activityEventHandler.handleEvent(ContactDelete(uiState.contact))
                }
            }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(id = R.string.cta_delete)
                )
            }
        }
    }
}

@Composable
private fun FabContent(
    uiState: ContactDetailUiState,
    activityEventHandler: ContactsActivityEventHandler,
    detailEventHandler: ContactDetailEventHandler,
) {
    if (uiState !is NoContactSelected) {
        FloatingActionButton(onClick = {
            when (uiState) {
                is EditMode -> detailEventHandler.handleEvent(SaveClick)
                NoContactSelected -> {
                    /* no-op */
                }
                is ViewingContact -> activityEventHandler.handleEvent(ContactEdit(uiState.contact))
            }
        }) {
            when (uiState) {
                is EditMode -> {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = stringResource(id = R.string.cta_save)
                    )
                }
                NoContactSelected -> {
                    /* no-op */
                }
                is ViewingContact -> {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(id = R.string.cta_edit)
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
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

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ContactDetailViewModePreview() {
    val uiState =
        ViewingContact(
            contact = Contact.createNewLocal(
                firstName = "FirstFirstFirstFirstFirstFirstFirstFirstFirstFirst",
                lastName = "LastLastLastLastLastLastLastLastLastLastLastLastLastLastLastLast",
                title = "Titletitletitletitletitletitletitletitletitletitletitletitletitletitle"
            )
        )
    SalesforceMobileSDKAndroidTheme {
        SinglePaneContactDetails(
            uiState = uiState,
            detailEventHandler = {},
            activityEventHandler = {})
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ContactDetailEditModePreview() {
    val origContact = Contact.createNewLocal(
        firstName = "FirstFirstFirstFirstFirstFirstFirstFirstFirstFirst",
        lastName = "LastLastLastLastLastLastLastLastLastLastLastLastLastLastLastLast",
        title = "Titletitletitletitletitletitletitletitletitletitletitletitletitletitle"
    )
    val editedContact = origContact.copy(
        firstName = "First EditedFirst EditedFirst EditedFirst EditedFirst EditedFirst EditedFirst EditedFirst Edited",
        title = "Title EditedTitle EditedTitle EditedTitle EditedTitle EditedTitle EditedTitle EditedTitle EditedTitle Edited"
    )

    val uiState =
        EditMode.EditingContact(
            originalContact = origContact,
            firstNameVm = editedContact.createFirstNameVm(),
            lastNameVm = editedContact.createLastNameVm(),
            titleVm = editedContact.createTitleVm(),
        )
    SalesforceMobileSDKAndroidTheme {
        SinglePaneContactDetails(
            uiState = uiState,
            detailEventHandler = {},
            activityEventHandler = {}
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ContactDetailEditModeSavingPreview() {
    val origContact = Contact.createNewLocal(
        firstName = "FirstFirstFirstFirstFirstFirstFirstFirstFirstFirst",
        lastName = "LastLastLastLastLastLastLastLastLastLastLastLastLastLastLastLast",
        title = "Titletitletitletitletitletitletitletitletitletitletitletitletitletitle"
    )
    val editedContact = origContact.copy(
        firstName = "First EditedFirst EditedFirst EditedFirst EditedFirst EditedFirst EditedFirst EditedFirst Edited",
        title = "Title EditedTitle EditedTitle EditedTitle EditedTitle EditedTitle EditedTitle EditedTitle EditedTitle Edited"
    )

    val uiState =
        EditMode.Saving(
            originalContact = origContact,
            firstNameVm = editedContact.createFirstNameVm(),
            lastNameVm = editedContact.createLastNameVm(),
            titleVm = editedContact.createTitleVm(),
        )
    SalesforceMobileSDKAndroidTheme {
        SinglePaneContactDetails(
            uiState = uiState,
            detailEventHandler = {},
            activityEventHandler = {}
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ContactDetailEmptyPreview() {
    val uiState = NoContactSelected
    SalesforceMobileSDKAndroidTheme {
        SinglePaneContactDetails(
            uiState = uiState,
            detailEventHandler = {},
            activityEventHandler = {}
        )
    }
}
