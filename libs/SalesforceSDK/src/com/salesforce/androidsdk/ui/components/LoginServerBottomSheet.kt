package com.salesforce.androidsdk.ui.components

import android.widget.ScrollView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.LoginViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginServerBottomSheet(
    viewModel: LoginViewModel,
//    servers: List<@Composable ()-> Unit>,
    ) {
    val sheetState = rememberModalBottomSheetState()
    val loginServerManager = SalesforceSDKManager.getInstance().loginServerManager

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.showBottomSheet.value = false
        },
        sheetState = sheetState,
        containerColor = Color.White,
    ) {
        var editing by remember { mutableStateOf(false) }

        // Sheet content
        if (editing) {
            var name by remember { mutableStateOf("") }
            var url by remember { mutableStateOf("") }

            IconButton(onClick = { editing = false }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.Black)
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier =  Modifier
                    .padding(start = 15.dp, end = 15.dp, top = 15.dp)
                    .fillMaxWidth(),
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Url") },
                modifier =  Modifier
                    .padding(start = 15.dp, end = 15.dp, bottom = 15.dp)
                    .fillMaxWidth(),
            )
            Button(
                onClick = {
                    // TODO: validate input
                    loginServerManager.addCustomLoginServer(name, url)
                    editing = false
                },
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                colors = ButtonColors(
                    containerColor = Color.Black,
                    contentColor = Color.Black,
                    disabledContainerColor = Color.Black,
                    disabledContentColor = Color.Black
                )
            ) {
                Text(text = "Save", color = Color.White)
            }
        } else {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Change Server",
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.padding(10.dp))
                loginServerManager.loginServers.forEach { server ->
                    HorizontalDivider(thickness = 1.dp)
                    LoginServerCard(viewModel, server)
                }

                TextButton(
                    modifier = Modifier.padding(bottom = 20.dp),
                    onClick = {
                        editing = true
                    }
                ) {
                    Text(
                        text = "Add New Connection",
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}