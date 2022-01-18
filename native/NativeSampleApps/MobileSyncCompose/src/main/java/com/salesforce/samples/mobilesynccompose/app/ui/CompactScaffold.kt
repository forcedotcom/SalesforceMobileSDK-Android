package com.salesforce.samples.mobilesynccompose.app.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salesforce.samples.mobilesynccompose.contacts.ui.ContactCard
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme

@Composable
fun CompactScaffoldWithFab(
    modifier: Modifier = Modifier,
    topAppBarContent: @Composable () -> Unit,
    bottomAppBarContent: @Composable () -> Unit,
    fabContent: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
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

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun CompactScaffoldPreview() {
    SalesforceMobileSDKAndroidTheme {
        CompactScaffoldWithFab(
            topAppBarContent = { TopAppBarContentPreview(label = "Contact List XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX") },
            bottomAppBarContent = {
                BottomAppBarContentPreview(
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

            LazyColumn(modifier = Modifier.padding(fixedPadding)) {
                items(count = 100) { i ->
                    ContactCard(
                        modifier = Modifier.padding(4.dp),
                        startExpanded = false,
                        isSynced = true,
                        name = "$i",
                        title = "$i Title"
                    )
                }
            }
        }
    }
}

@Composable
private fun TopAppBarContentPreview(label: String) {
    TopAppBar {
        Text(
            label,
            modifier = Modifier.padding(start = 16.dp),
            style = MaterialTheme.typography.h6,
            overflow = TextOverflow.Ellipsis,
            maxLines = 2
        )
    }
}

@Composable
private fun BottomAppBarContentPreview(cutoutShape: Shape? = null) {
    BottomAppBar(cutoutShape = cutoutShape) {
        val weightMod = Modifier.weight(1f)
        Spacer(modifier = weightMod)
        IconButton(onClick = { /*TODO*/ }) {
            Icon(Icons.Default.Search, contentDescription = "Search")
        }
//        Spacer(modifier = weightMod)
        IconButton(onClick = { /*TODO*/ }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More")
        }
//        Spacer(modifier = weightMod)
    }
}

@Composable
private fun FabContentPreview() {
    FloatingActionButton(onClick = { /* no-op */ }) {
        Icon(Icons.Default.Add, contentDescription = "Add")
    }
}