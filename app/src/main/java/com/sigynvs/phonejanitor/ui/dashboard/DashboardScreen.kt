package com.sigynvs.phonejanitor.ui.dashboard

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sigynvs.phonejanitor.permission.StorageAccess
import com.sigynvs.phonejanitor.quarantine.QuarantineSummary
import com.sigynvs.phonejanitor.scan.ScanResult
import com.sigynvs.phonejanitor.scan.ScanType
import com.sigynvs.phonejanitor.ui.common.InfoBanner
import com.sigynvs.phonejanitor.ui.common.SectionCard
import com.sigynvs.phonejanitor.ui.common.appContainer
import com.sigynvs.phonejanitor.update.UpdateState
import com.sigynvs.phonejanitor.util.StorageInfo
import com.sigynvs.phonejanitor.util.formatBytes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenReview: (ScanType) -> Unit,
    onOpenJunkEmail: () -> Unit,
    onOpenQuarantine: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val vm: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(context.appContainer))
    val state by vm.uiState.collectAsStateWithLifecycle()
    val updateState by vm.updateState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Phone Janitor") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            item { StorageCard(state.storage) }

            updateBannerItem(
                state = updateState,
                onAction = {
                    if (!vm.onUpdateAction()) {
                        runCatching { context.startActivity(vm.installPermissionIntent()) }
                    }
                },
                onDismiss = vm::dismissUpdate,
            )

            if (!state.hasStorageAccess) {
                item {
                    InfoBanner(
                        text = "Phone Janitor needs “All files access” to scan your storage.",
                        icon = Icons.Filled.Lock,
                        actionLabel = "Grant",
                        onAction = { openAllFilesAccess(context) },
                    )
                }
            }

            if (state.dryRun) {
                item {
                    InfoBanner(
                        text = "Dry run is on. Scans still list files, but nothing will be moved to Trash.",
                        icon = Icons.Filled.Info,
                    )
                }
            }

            items(ScanType.entries.toList(), key = { it.name }) { type ->
                val needsFiles = type != ScanType.JUNK_EMAIL
                ScanCard(
                    type = type,
                    result = state.scans[type],
                    hasAccess = state.hasStorageAccess || !needsFiles,
                    onClick = {
                        when {
                            type == ScanType.JUNK_EMAIL -> onOpenJunkEmail()
                            !state.hasStorageAccess -> openAllFilesAccess(context)
                            else -> {
                                vm.startScan(type)
                                onOpenReview(type)
                            }
                        }
                    },
                )
            }

            item {
                QuarantineCard(summary = state.quarantine, onClick = onOpenQuarantine)
            }
        }
    }
}

@Composable
private fun StorageCard(storage: StorageInfo) {
    SectionCard {
        Text("Storage", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        if (storage.totalBytes <= 0) {
            Text(
                "Storage info unavailable",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        } else {
            LinearProgressIndicator(
                progress = { storage.usedFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${formatBytes(storage.usedBytes)} used of ${formatBytes(storage.totalBytes)}" +
                    "  ·  ${formatBytes(storage.freeBytes)} free",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun ScanCard(
    type: ScanType,
    result: ScanResult?,
    hasAccess: Boolean,
    onClick: () -> Unit,
) {
    val available = type.enabled
    val contentColor =
        if (available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = available, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                iconFor(type),
                contentDescription = null,
                tint = if (available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(type.title, style = MaterialTheme.typography.titleMedium, color = contentColor)
                Text(
                    subtitleFor(type, result),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            when {
                result?.isScanning == true ->
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)

                !available ->
                    Text(
                        "Soon",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )

                !hasAccess ->
                    Text(
                        "Needs access",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )

                else ->
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                    )
            }
        }
    }
}

@Composable
private fun QuarantineCard(summary: QuarantineSummary, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Quarantine", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (summary.count == 0) {
                        "Empty"
                    } else {
                        "${summary.count} item(s) · ${formatBytes(summary.totalBytes)} · auto-deletes after 14 days"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

private fun iconFor(type: ScanType): ImageVector = when (type) {
    ScanType.DOWNLOADS_SCREENSHOTS -> Icons.Filled.PhotoLibrary
    ScanType.LARGE_FILES -> Icons.Filled.Folder
    ScanType.DUPLICATES -> Icons.Filled.ContentCopy
    ScanType.JUNK_EMAIL -> Icons.Filled.Email
    ScanType.APP_CACHE -> Icons.Filled.CleaningServices
}

private fun subtitleFor(type: ScanType, result: ScanResult?): String = when {
    result?.isScanning == true -> "Scanning…"
    result?.hasRun == true ->
        "${result.items.size} file(s) · ${formatBytes(result.totalBytes)}"

    else -> type.subtitle
}

private fun openAllFilesAccess(context: Context) {
    try {
        context.startActivity(StorageAccess.appSettingsIntent(context))
    } catch (_: ActivityNotFoundException) {
        runCatching { context.startActivity(StorageAccess.listSettingsIntent()) }
    }
}

/** Adds a dashboard banner while an update is available / downloading / ready. Nothing otherwise. */
private fun LazyListScope.updateBannerItem(
    state: UpdateState,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        is UpdateState.Available -> item(key = "update-banner") {
            UpdateBanner(
                title = "Update available — ${state.info.versionName}",
                subtitle = if (state.info.sizeBytes > 0) formatBytes(state.info.sizeBytes) else null,
                actionLabel = "Download",
                progress = null,
                onAction = onAction,
                onDismiss = onDismiss,
            )
        }
        is UpdateState.Downloading -> item(key = "update-banner") {
            UpdateBanner(
                title = "Downloading update…",
                subtitle = "${(state.progress * 100).toInt()}%",
                actionLabel = null,
                progress = state.progress,
                onAction = onAction,
                onDismiss = null,
            )
        }
        is UpdateState.ReadyToInstall -> item(key = "update-banner") {
            UpdateBanner(
                title = "Update ready — ${state.info.versionName}",
                subtitle = "Tap to install",
                actionLabel = "Install",
                progress = null,
                onAction = onAction,
                onDismiss = onDismiss,
            )
        }
        else -> Unit
    }
}

@Composable
private fun UpdateBanner(
    title: String,
    subtitle: String?,
    actionLabel: String?,
    progress: Float?,
    onAction: () -> Unit,
    onDismiss: (() -> Unit)?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                if (actionLabel != null) {
                    Button(onClick = onAction) { Text(actionLabel) }
                }
                if (onDismiss != null) {
                    TextButton(onClick = onDismiss) { Text("Later") }
                }
            }
            if (progress != null) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                )
            }
        }
    }
}
