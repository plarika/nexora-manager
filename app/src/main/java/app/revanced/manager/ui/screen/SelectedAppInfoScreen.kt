package app.revanced.manager.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.revanced.manager.R
import app.revanced.manager.data.platform.NetworkInfo
import app.revanced.manager.network.downloader.LoadedDownloader
import app.revanced.manager.patcher.patch.PatchBundleInfo
import app.revanced.manager.patcher.patch.PatchInfo
import app.revanced.manager.ui.component.AlertDialogExtended
import app.revanced.manager.ui.component.ColumnWithScrollbar
import app.revanced.manager.ui.component.LoadingIndicator
import app.revanced.manager.ui.component.NexoraOfficialBackdrop
import app.revanced.manager.ui.component.NexoraOfficialBorder
import app.revanced.manager.ui.component.NexoraOfficialMuted
import app.revanced.manager.ui.component.NexoraOfficialPanelStrong
import app.revanced.manager.ui.component.NexoraOfficialViolet
import app.revanced.manager.ui.component.NexoraOfficialText
import app.revanced.manager.ui.component.NexoraFlowTopBar
import app.revanced.manager.ui.component.NexoraInlineWarning
import app.revanced.manager.ui.component.NexoraPatchingAppHeader
import app.revanced.manager.ui.component.NexoraPatchingOption
import app.revanced.manager.ui.component.haptics.HapticExtendedFloatingActionButton
import app.revanced.manager.ui.model.SelectedApp
import app.revanced.manager.ui.viewmodel.SelectedAppInfoViewModel
import app.revanced.manager.util.APK_MIMETYPE
import app.revanced.manager.util.EventEffect
import app.revanced.manager.util.Options
import app.revanced.manager.util.PatchSelection
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectedAppInfoScreen(
    onPatchSelectorClick: (SelectedApp, PatchSelection?, Options) -> Unit,
    onRequiredOptions: (SelectedApp, PatchSelection?, Options) -> Unit,
    onPatchClick: () -> Unit,
    onBackClick: () -> Unit,
    vm: SelectedAppInfoViewModel
) {
    val resources = LocalResources.current
    val networkInfo = koinInject<NetworkInfo>()
    val networkMetered = remember { !networkInfo.isUnmetered() }

    val packageName = vm.selectedApp.packageName
    val version = vm.selectedApp.version
    val bundles by vm.bundleInfoFlow.collectAsStateWithLifecycle(emptyList())

    val allowIncompatiblePatches by vm.prefs.disablePatchVersionCompatCheck.getAsState()
    val effectiveAllowIncompatible = allowIncompatiblePatches || vm.selectedApp.version == null

    val patches by remember(bundles, effectiveAllowIncompatible) {
        derivedStateOf {
            vm.getPatches(bundles, effectiveAllowIncompatible)
        }
    }
    val versionOptions by remember(bundles, patches, packageName, allowIncompatiblePatches) {
        derivedStateOf {
            buildVersionOptions(
                bundles = bundles,
                selectedPatches = patches,
                packageName = packageName,
                allowIncompatible = allowIncompatiblePatches
            )
        }
    }
    val strictVersionOptions by remember(bundles, patches, packageName) {
        derivedStateOf {
            buildVersionOptions(
                bundles = bundles,
                selectedPatches = patches,
                packageName = packageName,
                allowIncompatible = false
            )
        }
    }
    val selectedVersionLabel by remember(vm.selectedApp.version) {
        derivedStateOf {
            vm.selectedApp.version ?: resources.getString(R.string.selected_app_meta_any_version)
        }
    }
    var showVersionSelector by remember { mutableStateOf(false) }
    val selectedPatchCount = patches.values.sumOf { it.size }
    val hasModifiedPatchSelection by remember(bundles, effectiveAllowIncompatible) {
        derivedStateOf {
            vm.hasModifiedPatchSelection(bundles, effectiveAllowIncompatible)
        }
    }
    val showVersionCompatibilityWarning by remember(
        vm.selectedApp.version,
        allowIncompatiblePatches,
        strictVersionOptions
    ) {
        derivedStateOf {
            val selectedVersion = vm.selectedApp.version ?: return@derivedStateOf false
            allowIncompatiblePatches &&
                    strictVersionOptions.versions.isNotEmpty() &&
                    selectedVersion !in strictVersionOptions.versions
        }
    }

    LaunchedEffect(versionOptions, vm.selectedApp.version) {
        if (versionOptions.unrestricted) return@LaunchedEffect

        val selectedVersion = vm.selectedApp.version
        if (selectedVersion != null && selectedVersion in versionOptions.versions) {
            return@LaunchedEffect
        }

        vm.setTargetVersion(versionOptions.versions.firstOrNull())
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = vm::handleDownloaderActivityResult
    )
    EventEffect(flow = vm.launchActivityFlow) { intent ->
        launcher.launch(intent)
    }
    val composableScope = rememberCoroutineScope()

    val sourcePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri -> uri?.let(vm::handleStorageResult) }
    )
    EventEffect(flow = vm.storageSelectionFlow) { app ->
        vm.selectedApp = app
        vm.dismissSourceSelector()
    }

    val error by vm.errorFlow.collectAsStateWithLifecycle(null)
    val downloaders by vm.downloaders.collectAsStateWithLifecycle(emptyList())

    NexoraOfficialBackdrop(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
        topBar = {
            NexoraFlowTopBar(
                title = stringResource(R.string.app_info),
                backContentDescription = stringResource(R.string.back),
                onBackClick = onBackClick,
            )
        },
        floatingActionButton = {
            // Hide the FAB when no patches are selected.
            if (selectedPatchCount == 0) return@Scaffold

            // Only hide the FAB for errors that genuinely block patching.
            // No-downloader errors are NOT blocking because the storage picker is the fallback.
            val blockingError = error?.takeIf {
                it != SelectedAppInfoViewModel.Error.NoDownloadersInstalled
            }
            if (blockingError != null) return@Scaffold

            HapticExtendedFloatingActionButton(
                text = { Text(stringResource(R.string.patch)) },
                icon = {
                    Icon(
                        Icons.Default.AutoFixHigh,
                        stringResource(R.string.patch)
                    )
                },
                shape = RoundedCornerShape(18.dp),
                containerColor = NexoraOfficialViolet,
                contentColor = Color.White,
                onClick = patchClick@{
                    // If the selected source is Auto (Search) but nothing can be resolved
                    // (no installed app, no downloaded APK, no downloader), prompt the user
                    // to pick an APK from storage instead of failing silently.
                    if (vm.selectedApp is SelectedApp.Search &&
                        vm.resolveAutoSource(vm.selectedApp.version) is SelectedApp.Search &&
                        downloaders.isEmpty()
                    ) {
                        sourcePickerLauncher.launch(APK_MIMETYPE)
                        return@patchClick
                    }

                    composableScope.launch {
                        if (!vm.hasSetRequiredOptions(patches, effectiveAllowIncompatible)) {
                            onRequiredOptions(
                                vm.selectedApp,
                                vm.getCustomPatches(bundles, effectiveAllowIncompatible),
                                vm.options
                            )
                            return@launch
                        }

                        onPatchClick()
                    }
                }
            )
        },
    ) { paddingValues ->

        if (showVersionSelector) {
            VersionSelectorDialog(
                selectedVersion = vm.selectedApp.version,
                availableVersions = versionOptions.versions,
                allowAnyVersion = versionOptions.unrestricted,
                onDismissRequest = { showVersionSelector = false },
                onSelect = { version ->
                    vm.setTargetVersion(version)
                    showVersionSelector = false
                }
            )
        }

        if (vm.showSourceSelector) {
            val selectedVersion = vm.selectedApp.version
            val autoSelection = vm.resolveAutoSource(selectedVersion)

            AppSourceSelectorDialog(
                downloaders = downloaders,
                downloadedApps = vm.downloadedApps,
                activeSearchJob = vm.activeDownloader,
                requiredVersion = selectedVersion,
                autoSelection = autoSelection,
                onDismissRequest = vm::dismissSourceSelector,
                onSelectAuto = {
                    vm.selectedApp = autoSelection
                    vm.dismissSourceSelector()
                },
                onSelectDownloader = vm::searchUsingDownloader,
                onSelectFromStorage = { sourcePickerLauncher.launch(APK_MIMETYPE) },
                onSelect = {
                    vm.selectedApp = it
                    vm.dismissSourceSelector()
                }
            )
        }

        ColumnWithScrollbar(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            NexoraPatchingAppHeader(
                appInfo = vm.selectedAppInfo,
                placeholderLabel = packageName,
                version = version ?: stringResource(R.string.selected_app_meta_any_version),
            )

            PageItem(
                marker = "01",
                title = R.string.patch_selector_item,
                description = stringResource(
                    R.string.patch_selector_item_description,
                    selectedPatchCount
                ),
                warningDescription = if (hasModifiedPatchSelection) {
                    stringResource(R.string.patch_selection_changed_warning)
                } else {
                    null
                },
                onClick = {
                    onPatchSelectorClick(
                        vm.selectedApp,
                        vm.getCustomPatches(
                            bundles,
                            effectiveAllowIncompatible
                        ),
                        vm.options
                    )
                }
            )
            PageItem(
                marker = "02",
                title = R.string.version,
                description = selectedVersionLabel,
                warningDescription = if (showVersionCompatibilityWarning) {
                    stringResource(R.string.version_compatibility_warning)
                } else {
                    null
                },
                enabled = versionOptions.unrestricted || versionOptions.versions.isNotEmpty(),
                onClick = { showVersionSelector = true }
            )
            val autoSourceSubtitle = run {
                val resolved = vm.resolveAutoSource(vm.selectedApp.version)
                when {
                    resolved is SelectedApp.Installed -> stringResource(R.string.apk_source_auto_installed)
                    resolved is SelectedApp.Local -> stringResource(R.string.apk_source_auto_downloaded)
                    downloaders.isNotEmpty() -> stringResource(R.string.apk_source_auto_downloader)
                    else -> stringResource(R.string.apk_source_auto_storage)
                }
            }
            PageItem(
                marker = "03",
                title = R.string.apk_source_selector_item,
                description = when (val app = vm.selectedApp) {
                    is SelectedApp.Search -> autoSourceSubtitle
                    is SelectedApp.Installed -> stringResource(R.string.apk_source_installed)
                    is SelectedApp.Download -> stringResource(
                        R.string.apk_source_downloader,
                        downloaders.find { it.packageName == app.data.downloaderPackageName && it.name == app.data.downloaderClassName }?.name
                            ?: app.data.downloaderPackageName
                    )

                    is SelectedApp.Local -> stringResource(R.string.apk_source_local)
                },
                onClick = {
                    vm.showSourceSelector()
                }
            )
            // Only show inline error text for truly blocking errors, not no-downloader
            // errors which are handled gracefully via the storage picker fallback.
            val inlineError = error?.takeIf {
                it != SelectedAppInfoViewModel.Error.NoDownloadersInstalled
            }
            inlineError?.let {
                NexoraInlineWarning(text = stringResource(it.resourceId))
            }

            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val needsInternet =
                    vm.selectedApp.let { it is SelectedApp.Search || it is SelectedApp.Download }

                if (needsInternet && networkMetered) {
                    NexoraInlineWarning(
                        text = stringResource(R.string.network_metered_warning)
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun PageItem(
    marker: String,
    @StringRes title: Int,
    description: String,
    enabled: Boolean = true,
    warningDescription: String? = null,
    warningColor: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    NexoraPatchingOption(
        marker = marker,
        title = stringResource(title),
        description = description,
        enabled = enabled,
        warningDescription = warningDescription,
        onClick = onClick,
    )
}

private data class VersionOptions(
    val versions: List<String>,
    val unrestricted: Boolean
)

private fun buildVersionOptions(
    bundles: List<PatchBundleInfo.Scoped>,
    selectedPatches: PatchSelection,
    packageName: String,
    allowIncompatible: Boolean
): VersionOptions {
    val selected = bundles.flatMap { bundle ->
        val selectedNames = selectedPatches[bundle.uid].orEmpty()
        bundle.patches.filter { it.name in selectedNames }
    }

    val constraints = selected.mapNotNull { patch ->
        patch.versionConstraintFor(packageName)
    }

    if (constraints.isEmpty() || allowIncompatible) {
        val knownVersions = bundles.asSequence()
            .flatMap { bundle -> bundle.patches.asSequence() }
            .mapNotNull { patch -> patch.versionConstraintFor(packageName) }
            .flatMap { versions -> versions.asSequence() }
            .distinct()
            .sortedDescending()
            .toList()

        return VersionOptions(versions = knownVersions, unrestricted = true)
    }

    val intersection = constraints
        .map { it.toSet() }
        .reduce { acc, versions -> acc intersect versions }
        .toList()
        .sortedDescending()

    return VersionOptions(versions = intersection, unrestricted = false)
}

private fun PatchInfo.versionConstraintFor(packageName: String): Set<String>? {
    val pkg = compatiblePackages?.firstOrNull { it.packageName == packageName } ?: return null
    return pkg.versions?.toSet()?.takeIf { it.isNotEmpty() }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VersionSelectorDialog(
    selectedVersion: String?,
    availableVersions: List<String>,
    allowAnyVersion: Boolean,
    onDismissRequest: () -> Unit,
    onSelect: (String?) -> Unit
) {
    AlertDialogExtended(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(24.dp),
        containerColor = NexoraOfficialPanelStrong,
        tonalElevation = 0.dp,
        confirmButton = {
            TextButton(onClick = onDismissRequest, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.cancel), color = NexoraOfficialViolet)
            }
        },
        title = {
            Text(
                text = stringResource(R.string.version),
                color = NexoraOfficialText,
            )
        },
        textHorizontalPadding = PaddingValues(horizontal = 0.dp),
        text = {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (allowAnyVersion) {
                    item(key = "any") {
                        NexoraSelectorRow(
                            title = stringResource(R.string.selected_app_meta_any_version),
                            subtitle = if (selectedVersion == null) stringResource(R.string.this_version) else null,
                            selected = selectedVersion == null,
                            onClick = { onSelect(null) },
                        )
                    }
                }

                items(
                    items = availableVersions,
                    key = { version -> "version_$version" }
                ) { version ->
                    NexoraSelectorRow(
                        title = version,
                        subtitle = if (selectedVersion == version) stringResource(R.string.this_version) else null,
                        selected = selectedVersion == version,
                        onClick = { onSelect(version) },
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppSourceSelectorDialog(
    downloaders: List<LoadedDownloader>,
    downloadedApps: List<SelectedApp.Local>,
    activeSearchJob: LoadedDownloader?,
    requiredVersion: String?,
    autoSelection: SelectedApp,
    onDismissRequest: () -> Unit,
    onSelectAuto: () -> Unit,
    onSelectDownloader: (LoadedDownloader) -> Unit,
    onSelectFromStorage: () -> Unit,
    onSelect: (SelectedApp) -> Unit,
) {
    val canSelect = activeSearchJob == null

    AlertDialogExtended(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(24.dp),
        containerColor = NexoraOfficialPanelStrong,
        tonalElevation = 0.dp,
        confirmButton = {
            TextButton(onClick = onDismissRequest, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.cancel), color = NexoraOfficialViolet)
            }
        },
        title = {
            Text(
                text = stringResource(R.string.app_source_dialog_title),
                color = NexoraOfficialText,
            )
        },
        textHorizontalPadding = PaddingValues(horizontal = 0.dp),
        text = {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "auto") {
                    val hasDownloader = downloaders.isNotEmpty()
                    val hasDownloaded = downloadedApps.any { app -> requiredVersion == null || app.version == requiredVersion }
                    val hasAutoSource = hasDownloader || hasDownloaded || autoSelection is SelectedApp.Installed
                    NexoraSelectorRow(
                        title = stringResource(R.string.app_source_dialog_option_auto),
                        subtitle = if (hasAutoSource) {
                            stringResource(R.string.app_source_dialog_option_auto_description)
                        } else {
                            stringResource(R.string.app_source_dialog_option_auto_unavailable)
                        },
                        enabled = canSelect && hasAutoSource,
                        onClick = onSelectAuto,
                    )
                }

                items(
                    downloadedApps,
                    key = { "downloaded_${it.version}" }
                ) { app ->
                    val usable = requiredVersion == null || app.version == requiredVersion
                    NexoraSelectorRow(
                        title = stringResource(R.string.apk_source_downloaded),
                        subtitle = app.version,
                        enabled = canSelect && usable,
                        onClick = { onSelect(app) },
                    )
                }

                items(downloaders, key = { "downloader_${it.packageName}_${it.name}" }) { downloader ->
                    NexoraSelectorRow(
                        title = downloader.name,
                        subtitle = downloader.sourceName,
                        enabled = canSelect,
                        trailing = (@Composable { LoadingIndicator() }).takeIf { activeSearchJob == downloader },
                        onClick = { onSelectDownloader(downloader) },
                    )
                }

                item(key = "storage") {
                    NexoraSelectorRow(
                        title = stringResource(R.string.select_from_storage),
                        subtitle = stringResource(R.string.select_from_storage_description),
                        onClick = onSelectFromStorage,
                    )
                }
            }
        }
    )
}

@Composable
private fun NexoraSelectorRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = NexoraOfficialPanelStrong,
        border = BorderStroke(
            1.dp,
            if (selected) NexoraOfficialViolet.copy(alpha = 0.72f)
            else NexoraOfficialBorder.copy(alpha = if (enabled) 0.8f else 0.35f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (enabled) NexoraOfficialText else NexoraOfficialMuted.copy(alpha = 0.5f),
                )
                subtitle?.let {
                    Text(
                        text = it,
                        color = if (selected) NexoraOfficialViolet else NexoraOfficialMuted,
                    )
                }
            }
            trailing?.invoke()
        }
    }
}