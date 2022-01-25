package com.salesforce.samples.mobilesynccompose.app.ui.compact

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salesforce.samples.mobilesynccompose.R
import com.salesforce.samples.mobilesynccompose.contacts.ui.ContactListContent
import com.salesforce.samples.mobilesynccompose.contacts.ui.TempContactObject
import com.salesforce.samples.mobilesynccompose.core.ui.LocalWindowLayoutRestrictions
import com.salesforce.samples.mobilesynccompose.core.ui.WindowSizeClass.Compact
import com.salesforce.samples.mobilesynccompose.core.ui.WindowSizeClass.Medium
import com.salesforce.samples.mobilesynccompose.core.ui.WindowSizeClassInfo
import com.salesforce.samples.mobilesynccompose.core.ui.restrictions.WindowLayoutRestriction.FoldingRestriction
import com.salesforce.samples.mobilesynccompose.core.ui.restrictions.WindowLayoutRestriction.SimpleRestriction
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme

@Composable
fun CompactAppScaffold(
    modifier: Modifier = Modifier,
    topAppBarContent: @Composable () -> Unit = {},
    bottomAppBarContent: @Composable () -> Unit = {},
    fabContent: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    when (val restrictions = LocalWindowLayoutRestrictions.current) {
        is FoldingRestriction -> TODO("CompactAppScaffold() - FoldingRestriction")
        is SimpleRestriction -> {
            Scaffold(
                modifier = modifier,
                topBar = topAppBarContent,
                bottomBar = bottomAppBarContent,
                floatingActionButton = fabContent,
                isFloatingActionButtonDocked = true,
                floatingActionButtonPosition = FabPosition.Center,
                content = content
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CompactAppScaffoldPreview() {
    SalesforceMobileSDKAndroidTheme {
        CompositionLocalProvider(
//            LocalWindowLayoutRestrictions provides FoldingRestriction(
//                windowSizeClassInfo = WindowSizeClassInfo(horiz = Compact, vert = Medium),
//                foldOrientation = Horizontal,
//                foldState = Flat,
//                isHingeOccluding = false,
//                hingeBounds = android.graphics.Rect(0, 0, 500, 1000)
//            )
            LocalWindowLayoutRestrictions provides SimpleRestriction(
                WindowSizeClassInfo(horiz = Compact, vert = Medium)
            )
        ) {
            CompactAppScaffold(
                topAppBarContent = { CompactAppTopBar(label = "Contact List XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX") },
                bottomAppBarContent = {
                    CompactContactListBottomBar(
                        onSearchClick = {},
                        onMenuClick = {},
                        cutoutShape = MaterialTheme.shapes.small.copy(all = CornerSize(percent = 50))
                    )
                },
                fabContent = { FabContentPreview() }
            ) { paddingVals ->
                val fixedPadding = PaddingValues(
                    start = paddingVals.calculateStartPadding(LocalLayoutDirection.current) + 4.dp,
                    top = paddingVals.calculateTopPadding() + 4.dp,
                    end = paddingVals.calculateEndPadding(LocalLayoutDirection.current) + 4.dp,
                    bottom = paddingVals.calculateBottomPadding() + 4.dp
                )

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
}

@Composable
private fun FabContentPreview() {
    FloatingActionButton(onClick = { /* no-op */ }) {
        Icon(
            Icons.Default.Add,
            contentDescription = stringResource(id = R.string.content_desc_add_contact)
        )
    }
}