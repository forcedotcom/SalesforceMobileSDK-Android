/*
 * Copyright (c) 2026-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 * - Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written permission
 * of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.samples.authflowtester.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.salesforce.androidsdk.rest.ApiVersionStrings
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.samples.authflowtester.ConcurrentRequestResult
import com.salesforce.samples.authflowtester.ConcurrentRequestState
import com.salesforce.samples.authflowtester.INNER_CARD_PADDING
import com.salesforce.samples.authflowtester.PADDING
import com.salesforce.samples.authflowtester.R
import com.salesforce.samples.authflowtester.RequestResult
import com.salesforce.samples.authflowtester.concurrentRestRequest
import com.salesforce.samples.authflowtester.makeConcurrentRestRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.util.concurrent.atomic.AtomicInteger

const val MANY_REQUEST_CARD_CONTENT_DESC = "many_request_card"
const val MANY_REQUEST_OPTIONS_CONTENT_DESC = "many_request_options"
const val MANY_REQUEST_OPTIONS_TEST_TAG = "many_request_options_toggle"
const val MANY_REQUEST_OPTIONS_EXPANDED_STATE = "Expanded"
const val MANY_REQUEST_OPTIONS_COLLAPSED_STATE = "Collapsed"
const val MANY_REQUEST_BUTTON_CONTENT_DESC = "many_request_button"
const val MANY_REQUEST_SUMMARY_CONTENT_DESC = "many_request_summary"
const val MANY_REQUEST_COMPLETED_COUNT_CONTENT_DESC = "many_request_completed_count"
const val MANY_REQUEST_SUCCESS_COUNT_CONTENT_DESC = "many_request_success_count"
const val MANY_REQUEST_FAILURE_COUNT_CONTENT_DESC = "many_request_failure_count"
const val MANY_REQUEST_IN_FLIGHT_COUNT_CONTENT_DESC = "many_request_in_flight_count"
const val MANY_REQUEST_QUEUED_COUNT_CONTENT_DESC = "many_request_queued_count"
const val MANY_REQUEST_INTERRUPTION_STATUS_CONTENT_DESC = "many_request_interruption_status"
const val MANY_REQUEST_ERROR_DETAILS_CONTENT_DESC = "many_request_error_details"
const val MANY_REQUEST_ERROR_COPY_CONTENT_DESC = "many_request_error_copy"

/** UI-test-only failure injection. The activity gates use of this hook behind DEBUG + UI testing. */
object ConcurrentRequestTestHooks {
    @Volatile
    var failedRequestIndex: Int? = null

    @Volatile
    var submittedCount: Int = 0

    @Volatile
    var lastInterruptionStatus: String? = null
}

/**
 * Process-stable owner for batches that deliberately race logout. A composition-owned scope would
 * cancel the very requests this screen is intended to exercise when logout removes the activity.
 */
private object ConcurrentRequestBatchScope {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}

private const val DEFAULT_REQUEST_COUNT = 20
private const val IN_FLIGHT_TRIGGER_COUNT = 5
private val REQUEST_COUNT_OPTIONS = listOf(5, 10, 20, 50)

fun manyRequestCountContentDescription(count: Int) = "many_request_count_$count"

fun manyRequestInterruptionContentDescription(interruption: ManyRequestInterruption) =
    "many_request_interruption_${interruption.name.lowercase()}"

fun manyRequestSquareTestTag(index: Int) = "many_request_square_${index + 1}"

enum class ManyRequestInterruption(val displayName: String) {
    MANUAL("Manual"),
    REVOKE("Revoke when requests are in flight"),
    LOGOUT("Logout when requests are in flight"),
}

data class ConcurrentRequestBatchContext(
    val client: RestClient?,
    val accessToken: String?,
    val logoutAction: () -> Unit,
)

@Composable
fun ConcurrentRestRequestsCard(
    batchContextProvider: () -> ConcurrentRequestBatchContext,
    revokeAction: suspend (RestClient?, String?) -> RequestResult,
    failedRequestIndexProvider: () -> Int? = { null },
) {
    val context = LocalContext.current
    var optionsExpanded by remember { mutableStateOf(false) }
    var requestCount by remember { mutableIntStateOf(DEFAULT_REQUEST_COUNT) }
    var interruption by remember { mutableStateOf(ManyRequestInterruption.MANUAL) }
    var batchInProgress by remember { mutableStateOf(false) }
    var activeRunId by remember { mutableIntStateOf(0) }
    var selectedError by remember { mutableStateOf<ConcurrentRequestResult?>(null) }
    var interruptionStatus by remember { mutableStateOf<String?>(null) }
    val requests = remember { mutableStateListOf<ConcurrentRequestResult>() }

    val completedCount = requests.count {
        it.state == ConcurrentRequestState.SUCCEEDED || it.state == ConcurrentRequestState.FAILED
    }
    val successCount = requests.count { it.state == ConcurrentRequestState.SUCCEEDED }
    val failureCount = requests.count { it.state == ConcurrentRequestState.FAILED }
    val inFlightCount = requests.count { it.state == ConcurrentRequestState.IN_FLIGHT }
    val queuedCount = requests.count { it.state == ConcurrentRequestState.QUEUED }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding((INNER_CARD_PADDING / 2).dp)
            .semantics { contentDescription = MANY_REQUEST_CARD_CONTENT_DESC },
        shape = RoundedCornerShape(com.salesforce.samples.authflowtester.CORNER_SHAPE.dp),
    ) {
        Column(modifier = Modifier.padding(PADDING.dp)) {
            Text(
                text = context.getString(R.string.concurrent_rest_requests),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = PADDING.dp, vertical = (PADDING / 2).dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !batchInProgress) { optionsExpanded = !optionsExpanded }
                    .padding(PADDING.dp)
                    .testTag(MANY_REQUEST_OPTIONS_TEST_TAG)
                    .semantics {
                        contentDescription = MANY_REQUEST_OPTIONS_CONTENT_DESC
                        stateDescription = if (optionsExpanded) {
                            MANY_REQUEST_OPTIONS_EXPANDED_STATE
                        } else {
                            MANY_REQUEST_OPTIONS_COLLAPSED_STATE
                        }
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = context.getString(
                        R.string.many_request_options_summary,
                        requestCount,
                        interruption.displayName,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.rotate(if (optionsExpanded) 180f else 0f),
                )
            }

            AnimatedVisibility(visible = optionsExpanded) {
                Column(modifier = Modifier.padding(horizontal = PADDING.dp)) {
                    Text(
                        text = context.getString(R.string.request_count),
                        fontWeight = FontWeight.Medium,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy((PADDING / 2).dp),
                    ) {
                        REQUEST_COUNT_OPTIONS.forEach { count ->
                            FilterChip(
                                selected = requestCount == count,
                                onClick = { requestCount = count },
                                enabled = !batchInProgress,
                                label = { Text(count.toString()) },
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics {
                                        contentDescription = manyRequestCountContentDescription(count)
                                    },
                            )
                        }
                    }

                    Text(
                        text = context.getString(R.string.request_mix_description),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = (PADDING / 2).dp),
                    )

                    Text(
                        text = context.getString(R.string.interruption),
                        fontWeight = FontWeight.Medium,
                    )
                    ManyRequestInterruption.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !batchInProgress) { interruption = option }
                                .semantics {
                                    contentDescription = manyRequestInterruptionContentDescription(option)
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = interruption == option,
                                onClick = { interruption = option },
                                enabled = !batchInProgress,
                            )
                            Text(option.displayName)
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val runId = activeRunId + 1
                    activeRunId = runId
                    batchInProgress = true
                    interruptionStatus = null
                    selectedError = null
                    val batchContext = batchContextProvider()
                    val injectedFailure = failedRequestIndexProvider()
                    ConcurrentRequestTestHooks.submittedCount = 0
                    ConcurrentRequestTestHooks.lastInterruptionStatus = null
                    requests.clear()
                    repeat(requestCount) { index ->
                        val (type, request) = concurrentRestRequest(
                            index,
                            ApiVersionStrings.VERSION_NUMBER,
                            injectedFailure,
                        )
                        requests += ConcurrentRequestResult(
                            index = index,
                            type = type,
                            endpoint = request.path,
                            state = ConcurrentRequestState.QUEUED,
                        )
                    }

                    ConcurrentRequestBatchScope.scope.launch {
                        runBatch(
                            runId = runId,
                            activeRunId = { activeRunId },
                            capturedClient = batchContext.client,
                            capturedAccessToken = batchContext.accessToken,
                            interruption = interruption,
                            requests = requests,
                            failedRequestIndex = injectedFailure,
                            updateInterruptionStatus = {
                                interruptionStatus = it
                                ConcurrentRequestTestHooks.lastInterruptionStatus = it
                            },
                            revokeAction = revokeAction,
                            logoutAction = batchContext.logoutAction,
                        )
                        if (activeRunId == runId) batchInProgress = false
                    }
                },
                enabled = !batchInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(PADDING.dp)
                    .semantics { contentDescription = MANY_REQUEST_BUTTON_CONTENT_DESC },
                shape = RoundedCornerShape(com.salesforce.samples.authflowtester.CORNER_SHAPE.dp),
            ) {
                if (batchInProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(INNER_CARD_PADDING.dp))
                    Spacer(Modifier.width(INNER_CARD_PADDING.dp))
                    Text(context.getString(R.string.making_many_requests))
                } else {
                    Text(context.getString(R.string.make_many_rest_api_requests))
                }
            }

            if (requests.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(PADDING.dp)
                        .semantics { contentDescription = MANY_REQUEST_SUMMARY_CONTENT_DESC },
                    verticalArrangement = Arrangement.spacedBy((PADDING / 2).dp),
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy((PADDING / 2).dp),
                        verticalArrangement = Arrangement.spacedBy((PADDING / 2).dp),
                    ) {
                        requests.forEach { request ->
                            RequestStatusSquare(request) {
                                if (request.state == ConcurrentRequestState.FAILED) {
                                    selectedError = request
                                }
                            }
                        }
                    }

                    Text(
                        text = context.getString(
                            R.string.many_request_completed_count,
                            completedCount,
                            requests.size,
                        ),
                        modifier = Modifier.semantics {
                            contentDescription = MANY_REQUEST_COMPLETED_COUNT_CONTENT_DESC
                        },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(PADDING.dp)) {
                        Text(
                            text = context.getString(R.string.many_request_success_count, successCount),
                            color = SUCCESS_COLOR,
                            modifier = Modifier.semantics {
                                contentDescription = MANY_REQUEST_SUCCESS_COUNT_CONTENT_DESC
                            },
                        )
                        Text(
                            text = context.getString(R.string.many_request_failure_count, failureCount),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics {
                                contentDescription = MANY_REQUEST_FAILURE_COUNT_CONTENT_DESC
                            },
                        )
                        Text(
                            text = context.getString(R.string.many_request_in_flight_count, inFlightCount),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.semantics {
                                contentDescription = MANY_REQUEST_IN_FLIGHT_COUNT_CONTENT_DESC
                            },
                        )
                        Text(
                            text = context.getString(R.string.many_request_queued_count, queuedCount),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.semantics {
                                contentDescription = MANY_REQUEST_QUEUED_COUNT_CONTENT_DESC
                            },
                        )
                    }
                    interruptionStatus?.let { status ->
                        Text(
                            text = status,
                            modifier = Modifier.semantics {
                                contentDescription = MANY_REQUEST_INTERRUPTION_STATUS_CONTENT_DESC
                            },
                        )
                    }
                }
            }
        }
    }

    selectedError?.let { error ->
        val detailText = buildErrorDetails(context, error)
        AlertDialog(
            onDismissRequest = { selectedError = null },
            title = { Text(context.getString(R.string.many_request_failed_title, error.index + 1)) },
            text = {
                Text(
                    text = detailText,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .semantics { contentDescription = MANY_REQUEST_ERROR_DETAILS_CONTENT_DESC },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { copyErrorDetails(context, detailText) },
                    modifier = Modifier.semantics {
                        contentDescription = MANY_REQUEST_ERROR_COPY_CONTENT_DESC
                    },
                ) {
                    Text(context.getString(R.string.copy_to_clipboard))
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedError = null }) {
                    Text(context.getString(R.string.ok))
                }
            },
        )
    }
}

private suspend fun runBatch(
    runId: Int,
    activeRunId: () -> Int,
    capturedClient: RestClient?,
    capturedAccessToken: String?,
    interruption: ManyRequestInterruption,
    requests: MutableList<ConcurrentRequestResult>,
    failedRequestIndex: Int?,
    updateInterruptionStatus: (String) -> Unit,
    revokeAction: suspend (RestClient?, String?) -> RequestResult,
    logoutAction: () -> Unit,
) = supervisorScope {
    val enteredFlight = AtomicInteger(0)
    val triggerThreshold = minOf(IN_FLIGHT_TRIGGER_COUNT, requests.size)
    val trigger = CompletableDeferred<Unit>()

    val interruptionJob = if (interruption == ManyRequestInterruption.MANUAL) {
        null
    } else {
        launch {
            trigger.await()
            if (activeRunId() != runId) return@launch
            when (interruption) {
                ManyRequestInterruption.REVOKE -> {
                    updateInterruptionStatus("Revoke requested")
                    val result = revokeAction(capturedClient, capturedAccessToken)
                    if (activeRunId() == runId) {
                        updateInterruptionStatus(
                            if (result.success) "Revoke completed" else "Revoke failed: ${result.displayValue}"
                        )
                    }
                }
                ManyRequestInterruption.LOGOUT -> {
                    updateInterruptionStatus("Logout requested")
                    logoutAction()
                }
                ManyRequestInterruption.MANUAL -> Unit
            }
        }
    }

    val requestJobs = requests.indices.map { index ->
        launch {
            if (activeRunId() != runId) return@launch
            val result = makeConcurrentRestRequest(
                client = capturedClient,
                index = index,
                apiVersion = ApiVersionStrings.VERSION_NUMBER,
                failedRequestIndex = failedRequestIndex,
                onSubmitted = {
                    requests[index] = requests[index].copy(state = ConcurrentRequestState.IN_FLIGHT)
                    val submitted = enteredFlight.incrementAndGet()
                    ConcurrentRequestTestHooks.submittedCount = submitted
                    if (submitted >= triggerThreshold) trigger.complete(Unit)
                },
            )
            if (activeRunId() == runId) requests[index] = result
        }
    }

    requestJobs.joinAll()
    if (!trigger.isCompleted) interruptionJob?.cancel()
    interruptionJob?.join()
}

@Composable
private fun RequestStatusSquare(result: ConcurrentRequestResult, onClick: () -> Unit) {
    val backgroundColor = when (result.state) {
        ConcurrentRequestState.QUEUED -> MaterialTheme.colorScheme.outlineVariant
        ConcurrentRequestState.IN_FLIGHT -> MaterialTheme.colorScheme.primary
        ConcurrentRequestState.SUCCEEDED -> SUCCESS_COLOR
        ConcurrentRequestState.FAILED -> MaterialTheme.colorScheme.error
    }
    val symbol = when (result.state) {
        ConcurrentRequestState.QUEUED -> "○"
        ConcurrentRequestState.IN_FLIGHT -> "…"
        ConcurrentRequestState.SUCCEEDED -> "✓"
        ConcurrentRequestState.FAILED -> "!"
    }

    Box(
        modifier = Modifier
            .size(26.dp)
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .clickable(enabled = result.state == ConcurrentRequestState.FAILED, onClick = onClick)
            .testTag(manyRequestSquareTestTag(result.index))
            .semantics {
                contentDescription = "Request ${result.index + 1}, ${result.type.displayName}"
                stateDescription = result.state.accessibilityName
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = symbol, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

private val ConcurrentRequestState.accessibilityName: String
    get() = when (this) {
        ConcurrentRequestState.QUEUED -> "Queued"
        ConcurrentRequestState.IN_FLIGHT -> "In flight"
        ConcurrentRequestState.SUCCEEDED -> "Succeeded"
        ConcurrentRequestState.FAILED -> "Failed"
    }

private fun buildErrorDetails(context: Context, error: ConcurrentRequestResult): String = buildString {
    appendLine(context.getString(R.string.many_request_number_detail, error.index + 1))
    appendLine(context.getString(R.string.many_request_type_detail, error.type.displayName))
    appendLine(context.getString(R.string.many_request_endpoint_detail, error.endpoint))
    error.statusCode?.let {
        appendLine(context.getString(R.string.many_request_status_detail, it))
    }
    error.errorDescription?.let {
        appendLine(context.getString(R.string.many_request_error_detail, it))
    }
    error.responseBody?.takeIf { it.isNotBlank() }?.let {
        appendLine()
        append(context.getString(R.string.many_request_response_detail, it))
    }
}

private fun copyErrorDetails(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("REST request error", text))
}

private val SUCCESS_COLOR = Color(0xFF2E844A)
