package com.salesforce.samples.mobilesynccompose.contacts

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salesforce.samples.mobilesynccompose.R.string.content_desc_add_contact
import com.salesforce.samples.mobilesynccompose.R.string.label_contacts
import com.salesforce.samples.mobilesynccompose.app.ui.AppScaffold
import com.salesforce.samples.mobilesynccompose.app.ui.AppTopBar
import com.salesforce.samples.mobilesynccompose.contacts.ui.ContactListBottomBar
import com.salesforce.samples.mobilesynccompose.contacts.ui.ContactListContent
import com.salesforce.samples.mobilesynccompose.contacts.ui.TempContactObject
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme

class ContactsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainActivityContent()
        }
    }
}

@Composable
private fun MainActivityContent() {
    SalesforceMobileSDKAndroidTheme {
        AppScaffold(
//            layoutRestrictions = LayoutRestrictions(
//                WindowSizeRestrictions(
//                    horiz = WindowSizeClass.Compact,
//                    vert = WindowSizeClass.Medium
//                )
//            ),
            topAppBarContent = {
                AppTopBar(label = stringResource(id = label_contacts))
            },

            bottomAppBarContent = {
                ContactListBottomBar(
                    onSearchClick = { /*TODO: enter search mode*/ },
                    onMenuClick = { /*TODO: Bottom sheet*/ },
                    cutoutShape = MaterialTheme.shapes.small.copy(CornerSize(percent = 50))
                )
            },

            fabContent = {
                FloatingActionButton(onClick = { /*TODO: enter contact create from here*/ }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(id = content_desc_add_contact)
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
                contacts = (0..100).map {
                    TempContactObject(
                        it,
                        name = "Name $it",
                        title = "Title $it"
                    )
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun DefaultPreview() {
    MainActivityContent()
}