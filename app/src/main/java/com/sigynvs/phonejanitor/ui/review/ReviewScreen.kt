package com.sigynvs.phonejanitor.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureAsPdf
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.sigynvs.phonejanitor.scan.ScanItem
import com.sigynvs.phonejanitor.scan.ScanType
import com.sigynvs.phonejanitor.ui.common.EmptyState
import com.sigynvs.phonejanitor.ui.common.InfoBanner
import com.sigynvs.phonejanitor.ui.common.appContainer
import com.sigynvs.phonejanitor.util.formatBytes
import com.sigynvs.phonejanitor.util.relativeAge
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    scanType: ScanType,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val vm: ReviewViewModel =
        viewModel(factory = ReviewViewModel.factory(context.appContainer, scanType))
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.outcome) {
        val outcome = state.outcome ?: return@LaunchedEffect
        val message = when {
            outcome.dryRun ->
                "Dry run: ${formatBytes(outcome.freedBytes)} across the selected files would be moved. Nothing was deleted."
            outcome.moved > 0 && outcome.failed == 0 ->
                "Moved ${outcome.moved} file(s) · ${formatBytes(outcome.freedBytes)} freed. Restore from Quarantine within 14 days."
            outcome.moved > 0 ->
                "Moved ${outcome.moved}, ${outcome.failed} failed."
            else ->
                "Nothing moved — ${outcome.failed} file(s) could not be moved."
        }
        // Stay on the screen — the list re-scans in place (remaining files, or the empty state).
        snackbarHostState.showSnackbar(message)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(scanType.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (state.hasRun && state.rows.isNotEmpty()) {
                ReviewBottomBar(
                    selectedCount = state.selectedCount,
                    selectedBytes = state.selectedBytes,
                    dryRun = state.dryRun,
                    committing = state.committing,
                    onCommit = { showConfirm = true },
                )
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                !state.hasRun -> LoadingState()

                state.rows.isEmpty() -> EmptyState(
                    icon = Icons.Filled.CheckCircle,
                    title = "Nothing to clean",
                    body = when (scanType) {
                        ScanType.LARGE_FILES ->
                            "Nothing on internal storage is above your size threshold. " +
                                "Lower it in Settings to widen the search."
                        ScanType.DUPLICATES ->
                            "No byte-identical duplicate files turned up on internal storage."
                        else ->
                            "No files here are older than your current thresholds. " +
                                "Loosen them in Settings to widen the search."
                    },
                    actionLabel = if (scanType == ScanType.DUPLICATES) null else "Adjust filters",
                    onAction = if (scanType == ScanType.DUPLICATES) null else onOpenSettings,
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> Column(Modifier.fillMaxSize()) {
                    ReviewHeader(
                        selectedCount = state.selectedCount,
                        total = state.total,
                        allSelected = state.allSelected,
                        onToggleAll = { vm.setAll(!state.allSelected) },
                    )
                    if (state.dryRun) {
                        InfoBanner(
                            text = "Dry run is on — the button below only simulates the move.",
                            icon = Icons.Filled.Info,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.rows, key = { it.item.path }) { row ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.groupHeader?.let { header ->
                                    Text(
                                        header,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                                    )
                                }
                                ReviewItemRow(
                                    item = row.item,
                                    selected = row.selected,
                                    isKeeper = row.isKeeper,
                                    onToggle = { vm.toggle(row.item.path) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConfirm) {
        val count = state.selectedCount
        val bytes = state.selectedBytes
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(if (state.dryRun) "Simulate cleanup" else "Move to Trash") },
            text = {
                Text(
                    if (state.dryRun) {
                        "Simulate moving $count file(s) (${formatBytes(bytes)})? Nothing will be deleted."
                    } else {
                        "Move $count file(s) (${formatBytes(bytes)}) to the quarantine folder? " +
                            "They can be restored for 14 days, then they are deleted automatically."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    vm.commit()
                }) { Text(if (state.dryRun) "Simulate" else "Move") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun LoadingState() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.width(1.dp))
        Text(
            "Scanning your storage…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun ReviewHeader(
    selectedCount: Int,
    total: Int,
    allSelected: Boolean,
    onToggleAll: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$selectedCount of $total selected",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onToggleAll) {
            Text(if (allSelected) "Select none" else "Select all")
        }
    }
}

@Composable
private fun ReviewItemRow(
    item: ScanItem,
    selected: Boolean,
    onToggle: () -> Unit,
    isKeeper: Boolean = false,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Thumbnail(item)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${formatBytes(item.sizeBytes)} · ${relativeAge(item.lastModified)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Text(
                    item.path.removePrefix("/storage/emulated/0/"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isKeeper) {
                    Text(
                        "suggested keep",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun Thumbnail(item: ScanItem) {
    val shape = RoundedCornerShape(8.dp)
    if (item.isImage || item.isVideo) {
        AsyncImage(
            model = File(item.path),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(shape),
        )
    } else {
        Box(
            Modifier
                .size(52.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                iconForMime(item.mimeType),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun ReviewBottomBar(
    selectedCount: Int,
    selectedBytes: Long,
    dryRun: Boolean,
    committing: Boolean,
    onCommit: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(16.dp),
        ) {
            Button(
                onClick = onCommit,
                enabled = selectedCount > 0 && !committing,
                modifier = Modifier.fillMaxWidth(),
                colors = if (dryRun) {
                    ButtonDefaults.buttonColors()
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                },
            ) {
                if (committing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current,
                    )
                } else {
                    Text(
                        if (dryRun) {
                            "Simulate cleanup ($selectedCount)"
                        } else {
                            "Move $selectedCount to Trash · ${formatBytes(selectedBytes)}"
                        }
                    )
                }
            }
        }
    }
}

private fun iconForMime(mime: String?): ImageVector = when {
    mime == null -> Icons.Filled.Description
    mime.startsWith("video/") -> Icons.Filled.Movie
    mime.startsWith("audio/") -> Icons.Filled.Audiotrack
    mime == "application/pdf" -> Icons.Filled.PictureAsPdf
    mime == "application/archive" -> Icons.Filled.Archive
    else -> Icons.Filled.Description
}
