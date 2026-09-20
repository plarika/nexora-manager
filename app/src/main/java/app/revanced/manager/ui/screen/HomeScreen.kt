package app.revanced.manager.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.revanced.manager.BuildConfig
import app.revanced.manager.R
import app.revanced.manager.ui.component.LazyColumnWithScrollbar
import app.revanced.manager.ui.component.LoadingIndicator
import app.revanced.manager.ui.component.NexoraLogoBadge
import app.revanced.manager.ui.component.NexoraOfficialActionCard
import app.revanced.manager.ui.component.NexoraOfficialAmber
import app.revanced.manager.ui.component.NexoraOfficialBorder
import app.revanced.manager.ui.component.NexoraOfficialCyan
import app.revanced.manager.ui.component.NexoraOfficialGreen
import app.revanced.manager.ui.component.NexoraOfficialMetric
import app.revanced.manager.ui.component.NexoraOfficialMuted
import app.revanced.manager.ui.component.NexoraOfficialPanel
import app.revanced.manager.ui.component.NexoraOfficialPanelStrong
import app.revanced.manager.ui.component.NexoraOfficialStatusPill
import app.revanced.manager.ui.component.NexoraOfficialText
import app.revanced.manager.ui.component.NexoraOfficialViolet
import app.revanced.manager.ui.viewmodel.AppsViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeScreen(
    sourceCount: Int,
    managerUpdateAvailable: Boolean,
    managerUpdateChecked: Boolean,
    onAppsClick: () -> Unit,
    onLibraryClick: () -> Unit,
    onUpdatesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: AppsViewModel = koinViewModel(),
) {
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val patchableApps by viewModel.patchableApps.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val channelLabel = stringResource(
        if (BuildConfig.VERSION_NAME.contains('-')) R.string.nexora_compact_dev
        else R.string.nexora_compact_stable,
    )
    val updateStatus = stringResource(
        when {
            managerUpdateAvailable -> R.string.nexora_metric_updates_available
            !managerUpdateChecked -> R.string.nexora_updates_not_checked
            else -> R.string.nexora_metric_updates_current
        },
    )
    val updateAccent = when {
        managerUpdateAvailable -> NexoraOfficialAmber
        !managerUpdateChecked -> NexoraOfficialCyan
        else -> NexoraOfficialGreen
    }

    LazyColumnWithScrollbar(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        val patched = installedApps
        val patchable = patchableApps
        if (patched == null || patchable == null) {
            item(key = "HOME_LOADING") {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
            }
            return@LazyColumnWithScrollbar
        }

        val patchedPackages = patched
            .flatMap { listOf(it.currentPackageName, it.originalPackageName) }
            .toSet()
        val availableApps = patchable.count { it.packageName !in patchedPackages }

        item(key = "NEXORA_HOME_V35") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                NexoraCommandHero(
                    channelLabel = channelLabel,
                    updateStatus = updateStatus,
                    updateAccent = updateAccent,
                    onPatchClick = onAppsClick,
                )

                NexoraOfficialPanel(
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        NexoraOfficialMetric(
                            value = (patched.size + availableApps).toString(),
                            label = stringResource(R.string.nexora_metric_available),
                            icon = Icons.Default.Apps,
                            modifier = Modifier.weight(1f),
                            accent = NexoraOfficialViolet,
                        )
                        NexoraOfficialMetric(
                            value = patched.size.toString(),
                            label = stringResource(R.string.nexora_metric_modified),
                            icon = Icons.Default.AutoAwesome,
                            modifier = Modifier.weight(1f),
                            accent = NexoraOfficialGreen,
                        )
                        NexoraOfficialMetric(
                            value = sourceCount.toString(),
                            label = stringResource(R.string.nexora_metric_sources),
                            icon = Icons.Default.Storage,
                            modifier = Modifier.weight(1f),
                            accent = NexoraOfficialCyan,
                        )
                    }
                }

                NexoraLibraryHero(
                    onClick = onLibraryClick,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    NexoraOfficialActionCard(
                        icon = Icons.Default.Update,
                        title = stringResource(R.string.nexora_nav_updates),
                        subtitle = stringResource(R.string.nexora_updates_header_subtitle),
                        onClick = onUpdatesClick,
                        modifier = Modifier.weight(1f),
                        accent = updateAccent,
                    )
                    NexoraOfficialActionCard(
                        icon = Icons.Default.Settings,
                        title = stringResource(R.string.nexora_nav_settings),
                        subtitle = stringResource(R.string.nexora_compact_settings_desc),
                        onClick = onSettingsClick,
                        modifier = Modifier.weight(1f),
                        accent = NexoraOfficialViolet,
                    )
                }
            }
        }
    }
}

@Composable
private fun NexoraCommandHero(
    channelLabel: String,
    updateStatus: String,
    updateAccent: Color,
    onPatchClick: () -> Unit,
) {
    val shape = RoundedCornerShape(30.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF111642),
                        Color(0xFF11102F),
                        Color(0xFF07182B),
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        NexoraOfficialCyan.copy(alpha = .78f),
                        NexoraOfficialViolet.copy(alpha = .72f),
                        NexoraOfficialBorder,
                    )
                ),
                shape = shape,
            )
            .padding(20.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NexoraLogoBadge(
                    painter = painterResource(R.drawable.ic_logo_ring),
                    size = 52,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "N E X O R A",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = NexoraOfficialText,
                    )
                    Text(
                        text = channelLabel.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = NexoraOfficialCyan,
                    )
                }
                NexoraOfficialStatusPill(
                    text = updateStatus,
                    accent = updateAccent,
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.nexora_home_hero_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = NexoraOfficialText,
                )
                Text(
                    text = stringResource(R.string.nexora_home_hero_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NexoraOfficialMuted,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                NexoraOfficialViolet,
                                Color(0xFF7048FF),
                                NexoraOfficialCyan,
                            )
                        )
                    )
                    .clickable(onClick = onPatchClick)
                    .padding(horizontal = 18.dp, vertical = 15.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = .14f),
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.padding(8.dp).size(23.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.fab_patch_app),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            text = stringResource(R.string.nexora_metric_apps_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = .78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun NexoraLibraryHero(
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 116.dp),
        shape = RoundedCornerShape(22.dp),
        color = NexoraOfficialPanelStrong,
        border = BorderStroke(1.dp, NexoraOfficialCyan.copy(alpha = .55f)),
    ) {
        Row(
            modifier = Modifier.padding(17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                NexoraOfficialCyan.copy(alpha = .24f),
                                NexoraOfficialViolet.copy(alpha = .18f),
                            )
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = NexoraOfficialCyan,
                    modifier = Modifier.size(28.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.nexora_nav_library).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = NexoraOfficialViolet,
                )
                Text(
                    text = stringResource(R.string.nexora_library_hero_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = NexoraOfficialText,
                )
                Text(
                    text = stringResource(R.string.nexora_library_hero_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = NexoraOfficialMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = NexoraOfficialCyan,
            )
        }
    }
}