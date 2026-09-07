package com.sigynvs.phonejanitor.ui.email

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sigynvs.phonejanitor.email.GmailBulkMover
import com.sigynvs.phonejanitor.ui.common.EmptyState
import com.sigynvs.phonejanitor.ui.common.SectionCard
import com.sigynvs.phonejanitor.ui.common.appContainer
import com.sigynvs.phonejanitor.util.formatBytes
import com.sigynvs.phonejanitor.util.relativeAge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JunkEmailScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: JunkEmailViewModel = viewModel(factory = JunkEmailViewModel.factory(context.appContainer))
    val state by vm.state.collectAsStateWithLifecycle()
    val bulkState by vm.bulkState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showConfirm by rememberSaveable { mutableStateOf(false) }
    var showBulkConfirm by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.movedCount) {
        val n = state.movedCount ?: return@LaunchedEffect
        snackbar.showSnackbar(
            "Moved $n message(s) to Gmail Trash. They're recoverable there for 30 days.",
        )
        vm.consumeMoved()
    }
    LaunchedEffect(state.error) {
        val e = state.error ?: return@LaunchedEffect
        snackbar.showSnackbar(e)
        vm.consumeError()
    }
    LaunchedEffect(bulkState) {
        val done = bulkState as? GmailBulkMover.State.Done ?: return@LaunchedEffect
        snackbar.showSnackbar(
            buildString {
                append("Moved ${done.moved}")
                if (done.total > 0) append(" of ${done.total}")
                append(" to Gmail Trash.")
                done.error?.let { append(" $it") }
            },
        )
        vm.acknowledgeBulk()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Junk Email") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (state.rows.isNotEmpty() && bulkState !is GmailBulkMover.State.Running) {
                MoveBar(
                    count = state.selectedCount,
                    bytes = state.selectedBytes,
                    busy = state.busy == MailBusy.Moving,
                    onMove = { showConfirm = true },
                )
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val running = bulkState as? GmailBulkMover.State.Running
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                running != null -> BulkProgress(
                    moved = running.moved,
                    total = running.total,
                    onStop = vm::cancelBulk,
                    onOpenAppSettings = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.fromParts("package", context.packageName, null),
                                ),
                            )
                        }
                    },
                    modifier = Modifier.align(Alignment.Center),
                )

                !state.configured -> SetupCard(
                    verifying = state.busy == MailBusy.Verifying,
                    onSave = vm::saveAndVerify,
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                else -> Column(Modifier.fillMaxSize()) {
                    AccountRow(address = state.address, onChange = vm::changeAccount)
                    QueryRow(
                        query = state.query,
                        searching = state.busy == MailBusy.Searching,
                        onQueryChange = vm::setQuery,
                        onSearch = vm::search,
                    )
                    when {
                        state.busy == MailBusy.Searching ->
                            CircularProgressIndicator(
                                Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(top = 40.dp),
                            )

                        state.searched && state.rows.isEmpty() ->
                            EmptyState(
                                icon = Icons.Filled.Email,
                                title = "No matches",
                                body = "No messages in All Mail match that search. " +
                                    "Adjust the query above and search again.",
                                modifier = Modifier.fillMaxWidth(),
                            )

                        else -> {
                            if (state.rows.isNotEmpty()) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        if (state.hasMoreThanShown) {
                                            "${state.totalMatched} matches · showing first ${state.rows.size}"
                                        } else {
                                            "${state.selectedCount} of ${state.rows.size} selected"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = { vm.setAll(!state.allSelected) }) {
                                        Text(if (state.allSelected) "Select none" else "Select all")
                                    }
                                }
                                if (state.hasMoreThanShown) {
                                    OutlinedButton(
                                        onClick = { showBulkConfirm = true },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp),
                                    ) {
                                        Text("Move all ${state.totalMatched} to Trash (skip review)")
                                    }
                                }
                            }
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(state.rows, key = { it.summary.uid }) { row ->
                                    MailRowItem(
                                        row = row,
                                        onToggle = { vm.toggle(row.summary.uid) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Move to Gmail Trash") },
            text = {
                Text(
                    "Move ${state.selectedCount} message(s) to Gmail's Trash? " +
                        "Gmail keeps trashed mail for 30 days, then deletes it. Nothing is expunged now.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    vm.moveSelected()
                }) { Text("Move") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancel") }
            },
        )
    }

    if (showBulkConfirm) {
        AlertDialog(
            onDismissRequest = { showBulkConfirm = false },
            title = { Text("Move all ${state.totalMatched}?") },
            text = {
                Text(
                    "Move every one of the ${state.totalMatched} messages matching this search into " +
                        "Gmail's Trash, without reviewing them individually. They're recoverable in " +
                        "Trash for 30 days. This can take several minutes — keep this screen open.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBulkConfirm = false
                    vm.moveAllMatching()
                }) { Text("Move all") }
            },
            dismissButton = {
                TextButton(onClick = { showBulkConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun BulkProgress(
    moved: Int,
    total: Int,
    onStop: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Moving to Gmail Trash", style = MaterialTheme.typography.titleMedium)
        Text(
            if (total > 0) "$moved of $total" else "$moved so far",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        if (total > 0) {
            LinearProgressIndicator(
                progress = { (moved.toFloat() / total).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(6.dp))
        }
        Text(
            "Runs in the background with a notification. If it stalls when the screen is off, " +
                "set this app's battery usage to Unrestricted.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onStop) { Text("Stop") }
            TextButton(onClick = onOpenAppSettings) { Text("App settings") }
        }
    }
}

@Composable
private fun SetupCard(
    verifying: Boolean,
    onSave: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var address by rememberSaveable { mutableStateOf("") }
    var appPassword by remember { mutableStateOf("") }
    var reveal by rememberSaveable { mutableStateOf(false) }

    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard {
            Text("Connect a Gmail account", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "This uses IMAP with a Google App Password — not your normal password. " +
                    "In your Google Account: Security → 2-Step Verification → App passwords. " +
                    "Paste the 16-character code below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("Gmail address") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = appPassword,
            onValueChange = { appPassword = it },
            label = { Text("App password") },
            singleLine = true,
            visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                TextButton(onClick = { reveal = !reveal }) {
                    Text(if (reveal) "Hide" else "Show")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onSave(address, appPassword) },
            enabled = !verifying && address.isNotBlank() && appPassword.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (verifying) {
                CircularProgressIndicator(
                    modifier = Modifier.height(18.dp),
                    strokeWidth = 2.dp,
                    color = LocalContentColor.current,
                )
            } else {
                Text("Save & verify")
            }
        }
    }
}

@Composable
private fun AccountRow(address: String, onChange: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            address,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onChange) { Text("Change") }
    }
}

@Composable
private fun QueryRow(
    query: String,
    searching: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Gmail search") },
            supportingText = { Text("Ordinary Gmail search syntax, e.g. category:promotions older_than:180d") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onSearch,
            enabled = !searching && query.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (searching) "Searching…" else "Find junk")
        }
    }
}

@Composable
private fun MailRowItem(row: MailRow, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    row.summary.subject,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    row.summary.from,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${formatBytes(row.summary.sizeBytes)} · ${relativeAge(row.summary.dateMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Checkbox(checked = row.selected, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun MoveBar(count: Int, bytes: Long, busy: Boolean, onMove: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(16.dp),
        ) {
            Button(
                onClick = onMove,
                enabled = count > 0 && !busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current,
                    )
                } else {
                    Text("Move $count to Gmail Trash · ${formatBytes(bytes)}")
                }
            }
        }
    }
}
