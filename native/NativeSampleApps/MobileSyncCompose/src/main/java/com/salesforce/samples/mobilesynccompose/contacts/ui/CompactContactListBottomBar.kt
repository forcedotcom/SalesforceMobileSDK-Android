package com.salesforce.samples.mobilesynccompose.app.ui.compact

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.BottomAppBar
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.salesforce.samples.mobilesynccompose.R
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme

@Composable
fun CompactContactListBottomBar(
    modifier: Modifier = Modifier,
    onSearchClick: () -> Unit,
    onMenuClick: () -> Unit,
    cutoutShape: Shape? = null
) {
    BottomAppBar(modifier = modifier, cutoutShape = cutoutShape) {
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onSearchClick) {
            Icon(
                Icons.Default.Search,
                contentDescription = stringResource(id = R.string.content_desc_search)
            )
        }
        IconButton(onClick = onMenuClick) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(id = R.string.content_desc_menu)
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CompactBottomAppBarPreview() {
    SalesforceMobileSDKAndroidTheme {
        CompactContactListBottomBar(
            onSearchClick = { /*TODO*/ },
            onMenuClick = { /*TODO*/ },
            cutoutShape = MaterialTheme.shapes.small.copy(CornerSize(percent = 50))
        )
    }
}