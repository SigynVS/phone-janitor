package com.sigynvs.phonejanitor.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sigynvs.phonejanitor.scan.ScanFilters
import com.sigynvs.phonejanitor.ui.common.SectionCard
import com.sigynvs.phonejanitor.ui.common.appContainer
import com.sigynvs.phonejanitor.update.UpdateState
import com.sigynvs.phonejanitor.util.formatBytes
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(context.appContainer))
    val filters by vm.filters.collectAsStateWithLifecycle()
    val updateState by vm.updateState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                SliderRow(
                    label = "Downloads older than",
                    value = filters.downloadsOlderThanDays,
                    unit = "days",
                    range = 30f..365f,
                    onCommit = vm::setDownloadsDays,
                )
            }
            item {
                SliderRow(
                    label = "Screenshots older than",
                    value = filters.screenshotsOlderThanDays,
                    unit = "days",
                    range = 14f..365f,
                    onCommit = vm::setScreenshotsDays,
                )
            }
            item {
                SliderRow(
                    label = "Large-file threshold",
                    value = filters.largeFileMinMb,
                    unit = "MB",
                    range = 25f..1000f,
                    helper = "Used by the Large Files scan.",
                    onCommit = vm::setLargeMb,
                )
            }
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Dry run", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "List files but never move them. Use it to preview a cleanup safely.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    Switch(checked = filters.dryRun, onCheckedChange = vm::setDryRun)
                }
            }
            item {
                UpdatesCard(
                    versionLabel = vm.versionLabel,
                    state = updateState,
                    onCheck = vm::checkForUpdates,
                    onDownload = vm::downloadUpdate,
                    onInstall = {
                        if (!vm.installUpdate()) {
                            runCatching { context.startActivity(vm.installPermissionIntent()) }
                        }
                    },
                )
            }
            item { SafetyNote() }
        }
    }
}

@Composable
private fun UpdatesCard(
    versionLabel: String,
    state: UpdateState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
) {
    SectionCard {
        Text("App version", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            versionLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        when (state) {
            is UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.height(0.dp))
                Text(
                    "  Checking…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            is UpdateState.UpToDate -> Column {
                Text(
                    "You're on the latest version.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onCheck) { Text("Check again") }
            }

            is UpdateState.Available -> Column {
                Text(
                    "Version ${state.info.versionName} is available" +
                        if (state.info.sizeBytes > 0) " · ${formatBytes(state.info.sizeBytes)}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (state.info.notes.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        state.info.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick = onDownload) { Text("Download") }
            }

            is UpdateState.Downloading -> Column {
                Text(
                    "Downloading… ${(state.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                )
            }

            is UpdateState.ReadyToInstall -> Column {
                Text(
                    "Version ${state.info.versionName} downloaded.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = onInstall) { Text("Install now") }
            }

            is UpdateState.Failed -> Column {
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onCheck) { Text("Try again") }
            }

            UpdateState.Idle -> OutlinedButton(onClick = onCheck) { Text("Check for updates") }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Int,
    unit: String,
    range: ClosedFloatingPointRange<Float>,
    onCommit: (Int) -> Unit,
    helper: String? = null,
) {
    // Local state so the thumb drags smoothly; commit to DataStore only when the drag ends.
    var sliderValue by remember(value) { mutableFloatStateOf(value.toFloat()) }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                "${sliderValue.roundToInt()} $unit",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            valueRange = range,
            onValueChangeFinished = { onCommit(sliderValue.roundToInt()) },
        )
        if (helper != null) {
            Text(
                helper,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun SafetyNote() {
    SectionCard {
        Text("How deletion works", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        listOf(
            "Files touched in the last ${ScanFilters.SAFETY_FLOOR_DAYS} days are never listed, whatever the sliders say.",
            "You review every file before anything happens — nothing is deleted automatically.",
            "“Move to Trash” relocates files to a quarantine folder. They restore for 14 days, then delete themselves.",
            "Junk email is only ever moved to Gmail’s Trash, never permanently removed.",
        ).forEach { line ->
            Text(
                "•  $line",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 3.dp),
            )
        }
    }
}
