package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.MainViewModel
import com.example.ui.PinActionPurpose
import com.example.ui.components.PinPadDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.CrimsonAccent
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.MidnightBg
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.PromesaTheme
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

private object PromesaRoutes {
    const val DASHBOARD = "dashboard"
    const val SETTINGS = "settings"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        setContent {
            PromesaTheme {
                PromesaApp()
            }
        }
    }
}

@Composable
fun PromesaApp(
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: PromesaRoutes.DASHBOARD

    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermissions()
        onPauseOrDispose {}
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.refreshPermissions()
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startProtectionIfPrepared()
        }
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshPermissions()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshPermissions()
    }

    fun requestVpnAndStart() {
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            viewModel.startProtectionIfPrepared()
        }
    }

    fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        settingsLauncher.launch(intent)
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun requestAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        settingsLauncher.launch(intent)
    }

    fun requestBatteryOptimizationExemption() {
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        )
        settingsLauncher.launch(intent)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MidnightBg,
        bottomBar = {
            NavigationBar(
                containerColor = MidnightSurface
            ) {
                NavigationBarItem(
                    selected = currentRoute == PromesaRoutes.DASHBOARD,
                    onClick = {
                        navController.navigate(PromesaRoutes.DASHBOARD) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = if (currentRoute == PromesaRoutes.DASHBOARD) {
                                Icons.Rounded.Shield
                            } else {
                                Icons.Outlined.Shield
                            },
                            contentDescription = stringResource(R.string.nav_dashboard)
                        )
                    },
                    label = { Text(stringResource(R.string.nav_dashboard)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CrimsonAccent,
                        selectedTextColor = TextPrimary,
                        indicatorColor = CrimsonContainer,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("nav_dashboard_tab")
                )

                NavigationBarItem(
                    selected = currentRoute == PromesaRoutes.SETTINGS,
                    onClick = {
                        navController.navigate(PromesaRoutes.SETTINGS) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = if (currentRoute == PromesaRoutes.SETTINGS) {
                                Icons.Rounded.Settings
                            } else {
                                Icons.Outlined.Settings
                            },
                            contentDescription = stringResource(R.string.nav_settings)
                        )
                    },
                    label = { Text(stringResource(R.string.nav_settings)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CrimsonAccent,
                        selectedTextColor = TextPrimary,
                        indicatorColor = CrimsonContainer,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("nav_settings_tab")
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = PromesaRoutes.DASHBOARD,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(PromesaRoutes.DASHBOARD) {
                DashboardScreen(
                    uiState = uiState,
                    onSetupPinClick = {
                        val purpose = if (uiState.settings.hasPinConfigured) {
                            PinActionPurpose.CHANGE_PIN
                        } else {
                            PinActionPurpose.CREATE_PIN
                        }
                        viewModel.openPinFlow(purpose)
                    },
                    onActivateProtectionClick = { requestVpnAndStart() },
                    onRequestPauseClick = { viewModel.openPinFlow(PinActionPurpose.PAUSE_15_MIN) },
                    onResumeNowClick = { viewModel.resumeProtectionNow() },
                    onRequestVpnPermission = { requestVpnAndStart() },
                    onRequestOverlayPermission = { requestOverlayPermission() },
                    onRequestNotificationPermission = { requestNotificationPermission() },
                    onRequestAccessibilityGuard = { requestAccessibilitySettings() },
                    onRequestBatteryOptimizationExemption = { requestBatteryOptimizationExemption() }
                )
            }

            composable(PromesaRoutes.SETTINGS) {
                SettingsScreen(
                    uiState = uiState,
                    onCreateOrChangePinClick = {
                        val purpose = if (uiState.settings.hasPinConfigured) {
                            PinActionPurpose.CHANGE_PIN
                        } else {
                            PinActionPurpose.CREATE_PIN
                        }
                        viewModel.openPinFlow(purpose)
                    },
                    onRequestPauseClick = { viewModel.openPinFlow(PinActionPurpose.PAUSE_15_MIN) },
                    onResumeNowClick = { viewModel.resumeProtectionNow() },
                    onRequestStopVpnClick = { viewModel.openPinFlow(PinActionPurpose.STOP_VPN) },
                    onRequestClearHistoryClick = { viewModel.openPinFlow(PinActionPurpose.CLEAR_HISTORY) },
                    onSaveTrustedContact = { name, phone ->
                        viewModel.saveTrustedContact(name, phone)
                    }
                )
            }
        }
    }

    uiState.activePinPurpose?.let { purpose ->
        PinPadDialog(
            purpose = purpose,
            pinLockoutUntilEpochMillis = uiState.settings.pinLockoutUntilEpochMillis,
            onVerifyPin = { pin -> viewModel.verifyCurrentPin(pin) },
            onSaveNewPin = { pin -> viewModel.saveNewPin(pin) },
            onAuthorized = { viewModel.applyPinAuthorizedAction(purpose) },
            onDismiss = { viewModel.dismissPinFlow() }
        )
    }
}
