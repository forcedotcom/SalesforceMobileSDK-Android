package com.salesforce.samples.mobilesynccompose.contacts.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salesforce.samples.mobilesynccompose.contacts.vm.*
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactDetailUiEvents.FieldValuesChanged
import com.salesforce.samples.mobilesynccompose.contacts.state.*
import com.salesforce.samples.mobilesynccompose.core.ui.components.ToggleableEditTextField
import com.salesforce.samples.mobilesynccompose.core.ui.safeStringResource
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme
import com.salesforce.samples.mobilesynccompose.model.contacts.Contact
import kotlinx.coroutines.launch

object ContactDetailContent {
    @Composable
    fun Empty() {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No contact selected")
        }
    }

    @Composable
    fun CompactViewMode(state: ViewingContact, modifier: Modifier = Modifier) {
        val scrollState = rememberScrollState()
        Column(
            modifier = modifier
                .scrollable(
                    state = scrollState,
                    orientation = Orientation.Vertical
                )
                .padding(horizontal = 8.dp),
        ) {
            state.vmList.forEach { fieldVm ->
                ToggleableEditTextField(
                    fieldValue = fieldVm.fieldValue,
                    isEditEnabled = false,
                    isError = fieldVm.isInErrorState,
                    onValueChange = { }, // Not editable in this mode
                    label = { Text(safeStringResource(id = fieldVm.labelRes)) },
                    help = { Text(safeStringResource(id = fieldVm.helperRes)) },
                    placeholder = { Text(safeStringResource(id = fieldVm.placeholderRes)) }
                )
            }
        }
    }

    @Composable
    fun CompactEditMode(
        state: EditingContact,
        delegate: DetailComponentEventHandler,
        modifier: Modifier = Modifier
    ) {
        val scrollState = rememberScrollState()
        val scope = rememberCoroutineScope()

        Column(
            modifier = modifier
                .padding(horizontal = 8.dp)
                .verticalScroll(state = scrollState)
        ) {
            state.vmList.forEach { fieldVm ->
                ToggleableEditTextField(
                    fieldValue = fieldVm.fieldValue,
                    isEditEnabled = fieldVm.canBeEdited,
                    isError = fieldVm.isInErrorState,
                    onValueChange = { newVal ->
                        val newObj = fieldVm.onFieldValueChange(newVal)
                        delegate.handleEvent(FieldValuesChanged(newObject = newObj))
                    },
                    modifier = Modifier.onGloballyPositioned { layoutCoords ->
                        if (fieldVm == state.vmToScrollTo) {
                            scope.launch {
                                scrollState.animateScrollTo(layoutCoords.positionInParent().y.toInt())
                            }
                        }
                    },
                    label = { Text(safeStringResource(id = fieldVm.labelRes)) },
                    help = { Text(safeStringResource(id = fieldVm.helperRes)) },
                    placeholder = { Text(safeStringResource(id = fieldVm.placeholderRes)) }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CompactEditModePreview() {
    var contactObject: Contact by remember {
        mutableStateOf(
            Contact.createNewLocal(
                firstName = "First",
                lastName = "Last",
                title = "Title"
            )
        )
    }

    val state = EditingContact(
        originalContact = contactObject,
        firstNameVm = contactObject.createFirstNameVm(),
        lastNameVm = contactObject.createLastNameVm(),
        titleVm = contactObject.createTitleVm(),
        savingContact = false
    )

    SalesforceMobileSDKAndroidTheme {
        Surface {
            ContactDetailContent.CompactEditMode(state = state, delegate = {})
        }
    }
}
