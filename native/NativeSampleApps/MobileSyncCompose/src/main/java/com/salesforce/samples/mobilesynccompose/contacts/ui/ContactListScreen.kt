package com.salesforce.samples.mobilesynccompose.contacts.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.salesforce.samples.mobilesynccompose.R
import com.salesforce.samples.mobilesynccompose.app.ui.AppScaffold
import com.salesforce.samples.mobilesynccompose.app.ui.AppTopBar
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactActivityState

@Composable
fun ContactListScreen(
    activityState: ContactActivityState,
    onAddClick: () -> Unit,
    onContactClick: (TempContactObject) -> Unit,
    onContactDeleteClick: (TempContactObject) -> Unit,
    onContactEditClick: (TempContactObject) -> Unit,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
) {
    AppScaffold(
//            layoutRestrictions = LayoutRestrictions(
//                WindowSizeRestrictions(
//                    horiz = WindowSizeClass.Compact,
//                    vert = WindowSizeClass.Medium
//                )
//            ),
        topAppBarContent = {
            AppTopBar(label = stringResource(id = R.string.label_contacts))
        },

        bottomAppBarContent = {
            ContactListBottomBar(
                onMenuClick = onMenuClick,
                onSearchClick = onSearchClick,
                cutoutShape = MaterialTheme.shapes.small.copy(CornerSize(percent = 50))
            )
        },

        fabContent = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(id = R.string.content_desc_add_contact)
                )
            }
        }
    ) { paddingVals ->
        val fixedPadding = PaddingValues(
            start = paddingVals.calculateStartPadding(LocalLayoutDirection.current) + 4.dp,
            top = paddingVals.calculateTopPadding() + 4.dp,
            end = paddingVals.calculateEndPadding(LocalLayoutDirection.current) + 4.dp,
            bottom = paddingVals.calculateBottomPadding() + 4.dp
        )

        // TODO get real contacts in here
        ContactListContent(
            modifier = Modifier.padding(fixedPadding),
            contacts = activityState.contacts,
            onContactClick = onContactClick,
            onContactDeleteClick = onContactDeleteClick,
            onContactEditClick = onContactEditClick
        )
    }
}