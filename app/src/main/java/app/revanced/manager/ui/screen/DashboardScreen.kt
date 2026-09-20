package app.revanced.manager.ui.screen

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.revanced.manager.R
import app.revanced.manager.network.dto.ProjectAnnouncement
import app.revanced.manager.ui.component.AlertDialogExtended
import app.revanced.manager.ui.component.AvailableUpdateDialog
import app.revanced.manager.ui.component.ConfirmDialog
import app.revanced.manager.ui.component.NotificationCard
import app.revanced.manager.ui.component.NotificationCardType
import app.revanced.manager.ui.component.NexoraLogoBadge
import app.revanced.manager.ui.component.NexoraOfficialBackdrop
import app.revanced.manager.ui.component.NexoraOfficialCyan
import app.revanced.manager.ui.component.NexoraOfficialBorder
import app.revanced.manager.ui.component.NexoraOfficialPanelStrong
import app.revanced.manager.ui.component.NexoraOfficialViolet
import app.revanced.manager.ui.component.NexoraNeonBackdrop
import app.revanced.manager.ui.component.TooltipIconButton
import app.revanced.manager.ui.component.haptics.HapticExtendedFloatingActionButton
import app.revanced.manager.ui.component.sources.ImportSourceDialog
import app.revanced.manager.ui.component.sources.ImportSourceDialogStrings
import app.revanced.manager.ui.model.SelectedApp
import app.revanced.manager.ui.model.navigation.SelectedApplicationInfo
import app.revanced.manager.ui.viewmodel.DashboardViewModel
import app.revanced.manager.ui.viewmodel.PatchesSelectorViewModel
import app.revanced.manager.util.RequestInstallAppsContract
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

enum class DashboardPage(
    val titleResId: Int,
    val icon: ImageVector
) {
    HOME(R.string.nexora_nav_panel, Icons.Outlined.Home),
    APPS(R.string.nexora_nav_apps, Icons.Outlined.Apps),
    LIBRARY(R.string.nexora_nav_library, Icons.Outlined.Source),
}

@SuppressLint("BatteryLife")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DashboardScreen(
    vm: DashboardViewModel = koinViewModel(),
    onSettingsClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onAnnouncementsClick: () -> Unit,
    onAnnouncementClick: (ProjectAnnouncement) -> Unit,
    onAppClick: (String) -> Unit,
    onPatchableAppClick: (String) -> Unit,
    onStorageSelect: (SelectedApp.Local) -> Unit,
    onBundleClick: (Int) -> Unit
) {
    val bundleDownloadError by vm.bundleDownloadError.collectAsStateWithLifecycle(null)
    val sourcesNotDownloaded by vm.sourcesNotDownloaded.collectAsStateWithLifecycle(false)
    val sourceUpdatesAvailable by vm.sourceUpdatesAvailable.collectAsStateWithLifecycle(false)
    val managerAutoUpdates by vm.prefs.managerAutoUpdates.getAsState()
    val showManagerUpdateDialogOnLaunch by vm.prefs.showManagerUpdateDialogOnLaunch.getAsState()
    val disablePatchVersionCompatCheck by vm.prefs.disablePatchVersionCompatCheck.getAsState()
    val disableSelectionWarning by vm.prefs.disableSelectionWarning.getAsState()
    val disableUniversalPatchCheck by vm.prefs.disableUniversalPatchCheck.getAsState()
    val suggestedVersionSafeguard by vm.prefs.suggestedVersionSafeguard.getAsState()
    val safeguardsToggled by remember(
        disablePatchVersionCompatCheck,
        disableSelectionWarning,
        disableUniversalPatchCheck,
        suggestedVersionSafeguard
    ) {
        derivedStateOf {
            disablePatchVersionCompatCheck ||
                    disableSelectionWarning ||
                    disableUniversalPatchCheck ||
                    !suggestedVersionSafeguard
        }
    }
    val hasUpdate by vm.hasUpdate.collectAsStateWithLifecycle()
    val updateVersion by vm.updateVersion.collectAsStateWithLifecycle()
    val androidContext = LocalContext.current
    val resources = LocalResources.current
    val logoPainter = rememberDrawablePainter(drawable = remember(resources) {
        AppCompatResources.getDrawable(androidContext, R.drawable.ic_logo_ring)
    })
    val composableScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = DashboardPage.HOME.ordinal,
        initialPageOffsetFraction = 0f
    ) { DashboardPage.entries.size }

    val appsLazyListState = rememberLazyListState()
    val appsSearchLazyListState = rememberLazyListState()
    var appsSearchExpanded by rememberSaveable { mutableStateOf(false) }
    val openApps: () -> Unit = {
        composableScope.launch {
            pagerState.animateScrollToPage(DashboardPage.APPS.ordinal)
            appsLazyListState.animateScrollToItem(0)
        }
    }

    val dashboardPatchesParams = remember {
        SelectedApplicationInfo.PatchesSelector.ViewModelParams(
            app = SelectedApp.Search("", null),
            currentSelection = null,
            options = emptyMap(),
            readOnly = true,
            browseAllBundles = true
        )
    }
    val dashboardPatchesViewModel = koinViewModel<PatchesSelectorViewModel>(key = "dashboard-patches") {
        parametersOf(dashboardPatchesParams)
    }
    val dashboardPatchesBundles by dashboardPatchesViewModel.bundlesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val dashboardSourceCount = remember(dashboardPatchesBundles) {
        dashboardPatchesBundles
            .distinctBy { bundle -> bundle.name.lowercase() to bundle.version }
            .size
    }

    var patchesSourceEditMode by rememberSaveable { mutableStateOf(false) }
    var sourceDeleteUid by rememberSaveable { mutableStateOf<Int?>(null) }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != DashboardPage.LIBRARY.ordinal) {
            patchesSourceEditMode = false
        }
    }

    sourceDeleteUid?.let { uid ->
        val sourceName = dashboardPatchesBundles.firstOrNull { it.uid == uid }?.name
            ?: return@let
        ConfirmDialog(
            onDismiss = { sourceDeleteUid = null },
            onConfirm = { vm.deleteSource(uid) },
            title = stringResource(R.string.delete),
            description = stringResource(R.string.patches_delete_single_dialog_description, sourceName),
            icon = Icons.Outlined.Delete
        )
    }

    var showAddBundleDialog by rememberSaveable { mutableStateOf(false) }
    if (showAddBundleDialog) {
        ImportSourceDialog(
            strings = ImportSourceDialogStrings.PATCHES,
            onDismiss = { showAddBundleDialog = false },
            onLocalSubmit = { patches ->
                showAddBundleDialog = false
                patchesSourceEditMode = false
                vm.createLocalSource(patches)
            },
            onRemoteSubmit = { url, autoUpdate ->
                showAddBundleDialog = false
                patchesSourceEditMode = false
                vm.createRemoteSource(url, autoUpdate)
            }
        )
    }

    var showUpdateDialog by rememberSaveable { mutableStateOf(true) }
    if (managerAutoUpdates && showUpdateDialog && showManagerUpdateDialogOnLaunch && hasUpdate) {
        AvailableUpdateDialog(
            onDismiss = { showUpdateDialog = false },
            setShowManagerUpdateDialogOnLaunch = vm::setShowManagerUpdateDialogOnLaunch,
            onConfirm = onUpdateClick,
            newVersion = updateVersion!!
        )
    }

    var pendingPatchablePackage by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingStorageSelection by rememberSaveable { mutableStateOf<SelectedApp.Local?>(null) }

    fun clearPendingSelection() {
        pendingPatchablePackage = null
        pendingStorageSelection = null
    }

    fun resumePendingSelection() {
        pendingPatchablePackage?.let {
            clearPendingSelection()
            onPatchableAppClick(it)
            return
        }

        pendingStorageSelection?.let {
            clearPendingSelection()
            onStorageSelect(it)
        }
    }

    var showAndroid11Dialog by rememberSaveable { mutableStateOf(false) }
    val installAppsPermissionLauncher =
        rememberLauncherForActivityResult(RequestInstallAppsContract) { granted ->
            showAndroid11Dialog = false
            if (granted) {
                resumePendingSelection()
            } else {
                clearPendingSelection()
            }
        }

    if (showAndroid11Dialog) {
        Android11Dialog(
            onDismissRequest = {
                showAndroid11Dialog = false
                clearPendingSelection()
            },
            onContinue = {
                installAppsPermissionLauncher.launch(androidContext.packageName)
            }
        )
    }

    fun onPatchableSelection(packageName: String) {
        if (vm.android11BugActive) {
            clearPendingSelection()
            pendingPatchablePackage = packageName
            showAndroid11Dialog = true
            return
        }

        onPatchableAppClick(packageName)
    }

    fun onStorageSelection(app: SelectedApp.Local) {
        if (vm.android11BugActive) {
            clearPendingSelection()
            pendingStorageSelection = app
            showAndroid11Dialog = true
            return
        }

        onStorageSelect(app)
    }

    NexoraOfficialBackdrop(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBarHeight + 96.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        )
                    )
            )

            val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            if (navBarHeight > 0.dp) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(navBarHeight)
                        .align(Alignment.BottomCenter)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                )
                            )
                        )
                )
            }

            Scaffold(
                topBar = {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF111827),
                        tonalElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            NexoraLogoBadge(painter = logoPainter, size = 34)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.nexora_app_name),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFFF9FAFB),
                                )
                                Text(
                                    text = stringResource(R.string.nexora_compact_tagline),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFA78BFA)
                                )
                            }
                            if (hasUpdate) {
                                TooltipIconButton(
                                    onClick = onUpdateClick,
                                    tooltip = stringResource(R.string.update),
                                ) { contentDescription ->
                                    BadgedBox(badge = { Badge(modifier = Modifier.size(6.dp)) }) {
                                        Icon(Icons.Filled.Update, contentDescription, tint = Color(0xFFF9FAFB))
                                    }
                                }
                            }
                            TooltipIconButton(
                                onClick = onAnnouncementsClick,
                                tooltip = stringResource(R.string.announcements),
                            ) { contentDescription ->
                                BadgedBox(
                                    badge = {
                                        if (vm.unreadAnnouncement != null) {
                                            Badge(modifier = Modifier.size(6.dp))
                                        }
                                    }
                                ) {
                                    Icon(Icons.Filled.Notifications, contentDescription, tint = Color(0xFFF9FAFB))
                                }
                            }
                            TooltipIconButton(
                                onClick = onSettingsClick,
                                tooltip = stringResource(R.string.settings),
                            ) { contentDescription ->
                                BadgedBox(
                                    badge = {
                                        if (safeguardsToggled) {
                                            Badge(
                                                modifier = Modifier.size(6.dp),
                                                containerColor = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                ) {
                                    Icon(Icons.Filled.Settings, contentDescription, tint = Color(0xFFF9FAFB))
                                }
                            }
                        }
                    }
                },
                bottomBar = {
                    NexoraDashboardBottomBar(
                        currentPage = pagerState.currentPage,
                        onPanel = {
                            composableScope.launch {
                                pagerState.animateScrollToPage(DashboardPage.HOME.ordinal)
                            }
                        },
                        onApps = openApps,
                        onLibrary = {
                            composableScope.launch {
                                pagerState.animateScrollToPage(DashboardPage.LIBRARY.ordinal)
                            }
                        },
                        onSettings = onSettingsClick,
                    )
                },
                containerColor = Color.Transparent,
                floatingActionButton = {
                    val currentScrollState =
                        if (appsSearchExpanded) appsSearchLazyListState else appsLazyListState
                    val showBackToTop by remember(currentScrollState) {
                        derivedStateOf { currentScrollState.firstVisibleItemIndex > 0 }
                    }

                    DashboardFab(
                        pagerState = pagerState,
                        showScrollToTop = showBackToTop,
                        onScrollToTop = {
                            composableScope.launch {
                                currentScrollState.animateScrollToItem(0)
                            }
                        }
                    )
                }
            ) { paddingValues ->
                Column(Modifier.padding(paddingValues)) {
                    if (pagerState.currentPage == DashboardPage.HOME.ordinal) {
                        Notifications(
                        if (bundleDownloadError != null) {
                            {
                                NotificationCard(
                                    type = NotificationCardType.ERROR,
                                    icon = Icons.Outlined.WarningAmber,
                                    title = stringResource(R.string.api_not_working_title),
                                    text = stringResource(R.string.api_not_working_description),
                                    onClick = onSettingsClick
                                )
                            }
                        } else null,
                        if (sourceUpdatesAvailable) {
                            {
                                NotificationCard(
                                    type = NotificationCardType.WARNING,
                                    icon = Icons.Outlined.Refresh,
                                    text = stringResource(R.string.banner_sources_not_updated_description),
                                    onClick = vm::downloadSources
                                )
                            }
                        } else if (sourcesNotDownloaded && bundleDownloadError == null) {
                            {
                                NotificationCard(
                                    type = NotificationCardType.WARNING,
                                    icon = Icons.Outlined.Refresh,
                                    text = stringResource(R.string.banner_sources_not_downloaded_description),
                                    onClick = vm::downloadSources
                                )
                            }
                        } else null,
                        vm.unreadAnnouncement?.let { announcement ->
                            {
                                NotificationCard(
                                    title = stringResource(R.string.new_announcement),
                                    text = announcement.title,
                                    icon = Icons.Filled.Notifications,
                                    type = if (announcement.level > 0) NotificationCardType.ERROR else NotificationCardType.NORMAL,
                                    onClick = {
                                        vm.markUnreadAnnouncementRead()
                                        onAnnouncementClick(announcement)
                                    },
                                    onDismiss = vm::markUnreadAnnouncementRead
                                )
                            }
                        }
                        )
                    }

                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = true,
                        modifier = Modifier.weight(1f)
                    ) { index ->
                        when (DashboardPage.entries[index]) {
                            DashboardPage.HOME -> {
                                HomeScreen(
                                    sourceCount = dashboardSourceCount,
                                    managerUpdateAvailable = hasUpdate,
                                    managerUpdateChecked = updateVersion != null,
                                    onAppsClick = openApps,
                                    onLibraryClick = {
                                        composableScope.launch {
                                            pagerState.animateScrollToPage(DashboardPage.LIBRARY.ordinal)
                                        }
                                    },
                                    onUpdatesClick = onUpdateClick,
                                    onSettingsClick = onSettingsClick,
                                )
                            }

                            DashboardPage.APPS -> {
                                AppsScreen(
                                    onAppClick = { onAppClick(it.currentPackageName) },
                                    onPatchableAppClick = ::onPatchableSelection,
                                    onStorageSelect = { selectedApp -> onStorageSelection(selectedApp) },
                                    lazyListState = appsLazyListState,
                                    searchLazyListState = appsSearchLazyListState,
                                    onSearchExpandedChange = { appsSearchExpanded = it },
                                )
                            }

                            DashboardPage.LIBRARY -> {
                                BackHandler {
                                    if (patchesSourceEditMode) {
                                        patchesSourceEditMode = false
                                        return@BackHandler
                                    }
                                    composableScope.launch {
                                        pagerState.animateScrollToPage(DashboardPage.HOME.ordinal)
                                    }
                                }

                                PatchesSelectorScreen(
                                    onSave = { _, _ -> },
                                    onBackClick = {
                                        if (patchesSourceEditMode) {
                                            patchesSourceEditMode = false
                                            return@PatchesSelectorScreen
                                        }
                                        composableScope.launch {
                                            pagerState.animateScrollToPage(DashboardPage.HOME.ordinal)
                                        }
                                    },
                                    onBundleInfoClick = onBundleClick,
                                    isSourceEditMode = patchesSourceEditMode,
                                    onSourceDeleteRequest = { sourceDeleteUid = it },
                                    onSyncAll = vm::downloadSources,
                                    onAddSource = { showAddBundleDialog = true },
                                    viewModel = dashboardPatchesViewModel,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NexoraDashboardBottomBar(
    currentPage: Int,
    onPanel: () -> Unit,
    onApps: () -> Unit,
    onLibrary: () -> Unit,
    onSettings: () -> Unit,
) {
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 10.dp,
                end = 10.dp,
                bottom = navInset + 8.dp,
            ),
        shape = RoundedCornerShape(26.dp),
        color = NexoraOfficialPanelStrong,
        border = BorderStroke(1.dp, NexoraOfficialBorder),
        tonalElevation = 12.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            NexoraBottomItem(
                icon = Icons.Outlined.Home,
                label = stringResource(R.string.nexora_nav_panel),
                selected = currentPage == DashboardPage.HOME.ordinal,
                onClick = onPanel,
                modifier = Modifier.weight(1f),
            )
            NexoraBottomItem(
                icon = Icons.Outlined.Apps,
                label = stringResource(R.string.nexora_nav_apps),
                selected = currentPage == DashboardPage.APPS.ordinal,
                onClick = onApps,
                modifier = Modifier.weight(1f),
            )
            NexoraPatchDockAction(
                onClick = onApps,
                modifier = Modifier.weight(1.08f),
            )
            NexoraBottomItem(
                icon = Icons.Outlined.Source,
                label = stringResource(R.string.nexora_nav_library),
                selected = currentPage == DashboardPage.LIBRARY.ordinal,
                onClick = onLibrary,
                modifier = Modifier.weight(1f),
            )
            NexoraBottomItem(
                icon = Icons.Filled.Settings,
                label = stringResource(R.string.nexora_nav_settings),
                selected = false,
                onClick = onSettings,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NexoraBottomItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) {
            NexoraOfficialViolet.copy(alpha = 0.18f)
        } else {
            Color.Transparent
        },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 1.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(21.dp),
                tint = if (selected) NexoraOfficialCyan else Color(0xFF8B96B8),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    letterSpacing = 0.sp,
                ),
                color = if (selected) NexoraOfficialViolet else Color(0xFF8B96B8),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NexoraPatchDockAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            NexoraOfficialViolet,
                            Color(0xFF7048FF),
                            NexoraOfficialCyan,
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = .22f), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = stringResource(R.string.fab_patch_app),
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = stringResource(R.string.patch),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                letterSpacing = 0.sp,
            ),
            color = NexoraOfficialCyan,
            maxLines = 1,
        )
    }
}


@Composable
private fun DashboardFab(
    pagerState: PagerState,
    showScrollToTop: Boolean,
    onScrollToTop: () -> Unit,
) {
    val visible = pagerState.currentPage == DashboardPage.APPS.ordinal && showScrollToTop

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
    ) {
        HapticExtendedFloatingActionButton(
            onClick = onScrollToTop,
            tooltip = stringResource(R.string.nexora_back_to_top),
            expanded = false,
            icon = {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null)
            },
            text = {},
        )
    }
}


@Composable
fun Notifications(
    vararg notifications: (@Composable () -> Unit)?,
) {
    val activeNotifications = notifications.filterNotNull()

    if (activeNotifications.isNotEmpty()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            activeNotifications.forEach { notification ->
                notification()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Android11Dialog(onDismissRequest: () -> Unit, onContinue: () -> Unit) {
    AlertDialogExtended(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onContinue, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.continue_))
            }
        },
        title = {
            Text(stringResource(R.string.android_11_bug_dialog_title))
        },
        icon = {
            Icon(Icons.Outlined.BugReport, null)
        },
        text = {
            Text(stringResource(R.string.android_11_bug_dialog_description))
        }
    )
}