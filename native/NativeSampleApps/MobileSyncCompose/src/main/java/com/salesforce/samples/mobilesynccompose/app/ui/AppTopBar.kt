package com.salesforce.samples.mobilesynccompose.app.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme

@Composable
fun AppTopBar(label: String) {
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

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CompactTopAppBarPreview() {
    SalesforceMobileSDKAndroidTheme {
        AppTopBar(label = "Hello, World!")
    }
}