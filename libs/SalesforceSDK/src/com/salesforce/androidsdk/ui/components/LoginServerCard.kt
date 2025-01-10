package com.salesforce.androidsdk.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.config.LoginServerManager
import com.salesforce.androidsdk.ui.LoginViewModel

//    @Preview
@Composable
fun LoginServerCard(viewModel: LoginViewModel, server: LoginServerManager.LoginServer) {
    Card(
        modifier = Modifier
            .padding(top = 10.dp, bottom = 10.dp)
            .fillMaxWidth()
            .clickable {
                viewModel.showServerPicker.value = false
                viewModel.loading.value = true
                viewModel.dynamicBackgroundColor.value = Color.White
                SalesforceSDKManager.getInstance().loginServerManager.selectedLoginServer = server
            },
        colors = CardColors(
            containerColor = Color.White,
            contentColor = Color.Black,
            disabledContentColor = Color.Gray,
            disabledContainerColor = Color.Black,
        ),
        shape = RectangleShape,
    ) {
        Text(server.name, modifier = Modifier.padding(start = 15.dp, end = 15.dp, top = 15.dp), fontSize = 22.sp)
        Text(server.url, modifier = Modifier.padding(start = 15.dp, end = 15.dp, bottom = 15.dp), fontStyle = FontStyle.Italic)
    }
}