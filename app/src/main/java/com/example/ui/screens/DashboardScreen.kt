package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.BlockedEvent
import com.example.domain.BlockedDomains
import com.example.notifications.PromesaNotificationManager
import com.example.ui.PromesaUiState
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberPause
import com.example.ui.theme.CrimsonAccent
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonSoft
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MidnightOutline
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.MidnightSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    uiState: PromesaUiState,
    onSetupPinClick: () -> Unit,
    onActivateProtectionClick: () -> Unit,
    onRequestPauseClick: () -> Unit,
    onResumeNowClick: () -> Unit,
    onRequestVpnPermission: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestAccessibilityGuard: () -> Unit,
    onRequestBatteryOptimizationExemption: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column {
                    Text(
                        text = stringResource(R.string.dashboard_title),
                        style = MaterialTheme.typography.displaySmall,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.dashboard_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            item {
                ProtectionHeroCard(
                    uiState = uiState,
                    onSetupPinClick = onSetupPinClick,
                    onActivateProtectionClick = onActivateProtectionClick,
                    onRequestPauseClick = onRequestPauseClick,
                    onResumeNowClick = onResumeNowClick
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        label = stringResource(R.string.stat_streak_days),
                        value = uiState.streakDays.toString(),
                        accentColor = EmeraldShield,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        label = stringResource(R.string.stat_blocked_total),
                        value = uiState.settings.totalBlockedCount.toString(),
                        accentColor = CrimsonAccent,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        label = stringResource(R.string.stat_domains_count),
                        value = BlockedDomains.DOMAINS.size.toString(),
                        accentColor = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.permissions_section_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            item {
                PermissionsChecklistCard(
                    uiState = uiState,
                    onSetupPinClick = onSetupPinClick,
                    onRequestVpnPermission = onRequestVpnPermission,
                    onRequestOverlayPermission = onRequestOverlayPermission,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onRequestAccessibilityGuard = onRequestAccessibilityGuard,
                    onRequestBatteryOptimizationExemption = onRequestBatteryOptimizationExemption
                )
            }

            item {
                Text(
                    text = stringResource(R.string.recent_blocks_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (uiState.settings.recentBlocks.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MidnightSurface,
                        border = BorderStroke(1.dp, MidnightOutline.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.GppGood,
                                contentDescription = null,
                                tint = EmeraldShield,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = stringResource(R.string.recent_blocks_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            } else {
                items(
                    items = uiState.settings.recentBlocks.take(30),
                    key = { "${it.timestampMillis}_${it.domain}_${it.source}" }
                ) { event ->
                    BlockedEventRow(event = event)
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ProtectionHeroCard(
    uiState: PromesaUiState,
    onSetupPinClick: () -> Unit,
    onActivateProtectionClick: () -> Unit,
    onRequestPauseClick: () -> Unit,
    onResumeNowClick: () -> Unit
) {
    val isPaused = uiState.isPaused
    val isActive = uiState.isProtectionActive

    val accentColor by animateColorAsState(
        targetValue = when {
            isPaused -> AmberPause
            isActive -> EmeraldShield
            else -> CrimsonAccent
        },
        animationSpec = tween(300),
        label = "hero_accent"
    )
    val badgeBg by animateColorAsState(
        targetValue = when {
            isPaused -> AmberContainer
            isActive -> EmeraldContainer
            else -> CrimsonContainer
        },
        animationSpec = tween(300),
        label = "hero_badge_bg"
    )

    val titleText = when {
        isPaused -> stringResource(R.string.status_paused_title)
        isActive -> stringResource(R.string.status_active_title)
        else -> stringResource(R.string.status_inactive_title)
    }
    val descText = when {
        isPaused -> stringResource(R.string.status_paused_desc)
        isActive -> stringResource(R.string.status_active_desc)
        else -> stringResource(R.string.status_inactive_desc)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MidnightSurface,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(accentColor.copy(alpha = 0.16f), Color.Transparent),
                            center = center.copy(x = size.width * 0.85f, y = size.height * 0.2f),
                            radius = size.maxDimension * 0.65f
                        )
                    )
                }
                .padding(22.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(badgeBg)
                        .border(1.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            isPaused -> Icons.Rounded.PauseCircle
                            isActive -> Icons.Rounded.Shield
                            else -> Icons.Rounded.Lock
                        },
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = descText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MidnightSurfaceElevated.copy(alpha = 0.8f),
                border = BorderStroke(1.dp, MidnightOutline.copy(alpha = 0.65f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Dns,
                        contentDescription = null,
                        tint = EmeraldShield,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.dashboard_dns_chip),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isPaused) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = AmberContainer.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, AmberPause.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = PromesaNotificationManager.formatMinutesSeconds(uiState.remainingPauseSeconds),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        color = AmberPause,
                        modifier = Modifier
                            .padding(vertical = 10.dp)
                            .fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            when {
                !uiState.settings.hasPinConfigured -> {
                    Button(
                        onClick = onSetupPinClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("setup_pin_cta"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CrimsonAccent,
                            contentColor = TextPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.action_setup_pin_first),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                isPaused -> {
                    Button(
                        onClick = onResumeNowClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("resume_now_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldShield,
                            contentColor = Color(0xFF0E1B1A)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.action_resume_now),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                !uiState.vpnRuntime.isRunning -> {
                    Button(
                        onClick = onActivateProtectionClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("enable_vpn_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CrimsonAccent,
                            contentColor = TextPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.action_enable_vpn),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                else -> {
                    FilledTonalButton(
                        onClick = onRequestPauseClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("pause_15_min_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MidnightSurfaceElevated,
                            contentColor = TextPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PauseCircle,
                            contentDescription = null,
                            tint = AmberPause,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.action_pause_with_pin),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MidnightSurface,
        border = BorderStroke(1.dp, MidnightOutline.copy(alpha = 0.65f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = value,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = accentColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = MaterialTheme.typography.bodyMedium.fontFamily
                ),
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PermissionsChecklistCard(
    uiState: PromesaUiState,
    onSetupPinClick: () -> Unit,
    onRequestVpnPermission: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestAccessibilityGuard: () -> Unit,
    onRequestBatteryOptimizationExemption: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MidnightSurface,
        border = BorderStroke(1.dp, MidnightOutline.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PermissionRow(
                icon = Icons.Rounded.Lock,
                title = stringResource(R.string.perm_pin_title),
                subtitle = stringResource(
                    if (uiState.settings.hasPinConfigured) R.string.perm_pin_ready else R.string.perm_pin_missing
                ),
                isGranted = uiState.settings.hasPinConfigured,
                onActionClick = onSetupPinClick,
                tag = "perm_pin_button"
            )
            PermissionRow(
                icon = Icons.Rounded.VpnKey,
                title = stringResource(R.string.perm_vpn_title),
                subtitle = stringResource(
                    if (uiState.permissions.hasVpnPermission) R.string.perm_vpn_ready else R.string.perm_vpn_missing
                ),
                isGranted = uiState.permissions.hasVpnPermission,
                onActionClick = onRequestVpnPermission,
                tag = "perm_vpn_button"
            )
            PermissionRow(
                icon = Icons.Rounded.Visibility,
                title = stringResource(R.string.perm_overlay_title),
                subtitle = stringResource(
                    if (uiState.permissions.hasOverlayPermission) R.string.perm_overlay_ready else R.string.perm_overlay_missing
                ),
                isGranted = uiState.permissions.hasOverlayPermission,
                onActionClick = onRequestOverlayPermission,
                tag = "perm_overlay_button"
            )
            PermissionRow(
                icon = Icons.Rounded.NotificationsActive,
                title = stringResource(R.string.perm_notifications_title),
                subtitle = stringResource(
                    if (uiState.permissions.hasNotificationPermission) {
                        R.string.perm_notifications_ready
                    } else {
                        R.string.perm_notifications_missing
                    }
                ),
                isGranted = uiState.permissions.hasNotificationPermission,
                onActionClick = onRequestNotificationPermission,
                tag = "perm_notifications_button"
            )
            PermissionRow(
                icon = Icons.Rounded.AccessibilityNew,
                title = stringResource(R.string.perm_accessibility_title),
                subtitle = stringResource(
                    if (uiState.permissions.hasAccessibilityGuard) {
                        R.string.perm_accessibility_ready
                    } else {
                        R.string.perm_accessibility_missing
                    }
                ),
                isGranted = uiState.permissions.hasAccessibilityGuard,
                onActionClick = onRequestAccessibilityGuard,
                tag = "perm_accessibility_button"
            )
            PermissionRow(
                icon = Icons.Rounded.BatteryChargingFull,
                title = stringResource(R.string.perm_battery_title),
                subtitle = stringResource(
                    if (uiState.permissions.isBatteryOptimizationIgnored) {
                        R.string.perm_battery_ready
                    } else {
                        R.string.perm_battery_missing
                    }
                ),
                isGranted = uiState.permissions.isBatteryOptimizationIgnored,
                onActionClick = onRequestBatteryOptimizationExemption,
                tag = "perm_battery_button"
            )
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onActionClick: () -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isGranted) EmeraldContainer else CrimsonContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) EmeraldShield else CrimsonAccent,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        if (isGranted) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = EmeraldShield,
                modifier = Modifier.size(22.dp)
            )
        } else {
            FilledTonalButton(
                onClick = onActionClick,
                modifier = Modifier
                    .height(48.dp)
                    .testTag(tag),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = CrimsonContainer,
                    contentColor = CrimsonSoft
                )
            ) {
                Text(
                    text = stringResource(R.string.perm_grant_action),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun BlockedEventRow(event: BlockedEvent) {
    val formatter = SimpleDateFormat("dd/MM/yyyy • HH:mm:ss", Locale.getDefault())
    val timeText = formatter.format(Date(event.timestampMillis))

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MidnightSurface,
        border = BorderStroke(1.dp, MidnightOutline.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = CrimsonAccent,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.domain,
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MidnightSurfaceElevated
            ) {
                Text(
                    text = event.source,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
