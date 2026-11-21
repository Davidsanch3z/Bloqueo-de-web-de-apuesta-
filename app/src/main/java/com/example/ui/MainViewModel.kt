package com.example.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.PinHasher
import com.example.data.PinVerificationResult
import com.example.data.PromesaPreferences
import com.example.data.PromesaSettingsState
import com.example.service.PromesaGuardAccessibilityService
import com.example.vpn.PromesaVpnService
import com.example.vpn.VpnRuntimeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PinActionPurpose {
    CREATE_PIN,
    CHANGE_PIN,
    PAUSE_15_MIN,
    STOP_VPN,
    CLEAR_HISTORY,
    UNINSTALL_GUARD
}

data class PermissionStatus(
    val hasVpnPermission: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val hasAccessibilityGuard: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = false
)

data class PromesaUiState(
    val settings: PromesaSettingsState = PromesaSettingsState(),
    val vpnRuntime: VpnRuntimeState = VpnRuntimeState(),
    val permissions: PermissionStatus = PermissionStatus(),
    val remainingPauseSeconds: Long = 0L,
    val streakDays: Int = 0,
    val activePinPurpose: PinActionPurpose? = null
) {
    val isProtectionActive: Boolean
        get() = vpnRuntime.isRunning && remainingPauseSeconds == 0L

    val isPaused: Boolean
        get() = remainingPauseSeconds > 0L
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = PromesaPreferences.getInstance(application)
    private val permissionStatus = MutableStateFlow(readCurrentPermissions())
    private val activePinPurpose = MutableStateFlow<PinActionPurpose?>(null)

    private val tickerFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1000L)
        }
    }

    val uiState: StateFlow<PromesaUiState> = combine(
        preferences.stateFlow,
        PromesaVpnService.runtimeState,
        permissionStatus,
        activePinPurpose,
        tickerFlow
    ) { settings, vpnRuntime, permissions, pinPurpose, nowMs ->
        val remaining = settings.remainingPauseSeconds(nowMs)
        PromesaUiState(
            settings = settings,
            vpnRuntime = vpnRuntime,
            permissions = permissions,
            remainingPauseSeconds = remaining,
            streakDays = settings.daysWithoutBlockedAttempts(nowMs),
            activePinPurpose = pinPurpose
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = PromesaUiState(permissions = readCurrentPermissions())
    )

    fun refreshPermissions() {
        permissionStatus.value = readCurrentPermissions()
    }

    private fun readCurrentPermissions(): PermissionStatus {
        val ctx = getApplication<Application>()
        val vpnGranted = VpnService.prepare(ctx) == null
        val overlayGranted = Settings.canDrawOverlays(ctx)
        val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val accessibilityGranted = PromesaGuardAccessibilityService.isServiceEnabled(ctx)
        val powerManager = ctx.getSystemService(PowerManager::class.java)
        val batteryIgnored = powerManager?.isIgnoringBatteryOptimizations(ctx.packageName) ?: false

        return PermissionStatus(
            hasVpnPermission = vpnGranted,
            hasOverlayPermission = overlayGranted,
            hasNotificationPermission = notificationsGranted,
            hasAccessibilityGuard = accessibilityGranted,
            isBatteryOptimizationIgnored = batteryIgnored
        )
    }

    fun openPinFlow(purpose: PinActionPurpose) {
        activePinPurpose.value = purpose
    }

    fun dismissPinFlow() {
        activePinPurpose.value = null
    }

    fun startProtectionIfPrepared() {
        val ctx = getApplication<Application>()
        if (VpnService.prepare(ctx) == null) {
            viewModelScope.launch {
                preferences.setProtectionEnabled(true)
                preferences.clearPause()
                PromesaVpnService.startService(ctx)
            }
        }
    }

    fun resumeProtectionNow() {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            preferences.clearPause()
            preferences.setProtectionEnabled(true)
            if (VpnService.prepare(ctx) == null && !uiState.value.vpnRuntime.isRunning) {
                PromesaVpnService.startService(ctx)
            }
        }
    }

    suspend fun verifyCurrentPin(pin: String): PinVerificationResult {
        return preferences.verifyPinWithLockout(pin)
    }

    suspend fun saveNewPin(pin: String): Boolean {
        if (!PinHasher.isValidPinFormat(pin)) return false
        preferences.setPin(pin)
        return true
    }

    fun saveTrustedContact(name: String, whatsAppNumber: String) {
        viewModelScope.launch {
            preferences.setTrustedContact(name, whatsAppNumber)
        }
    }

    fun applyPinAuthorizedAction(purpose: PinActionPurpose) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            when (purpose) {
                PinActionPurpose.PAUSE_15_MIN -> {
                    preferences.startFifteenMinutePause()
                }
                PinActionPurpose.STOP_VPN -> {
                    preferences.setProtectionEnabled(false)
                    PromesaVpnService.stopService(ctx)
                }
                PinActionPurpose.CLEAR_HISTORY -> {
                    preferences.clearHistory()
                }
                PinActionPurpose.CREATE_PIN,
                PinActionPurpose.CHANGE_PIN,
                PinActionPurpose.UNINSTALL_GUARD -> Unit
            }
            activePinPurpose.value = null
        }
    }
}
