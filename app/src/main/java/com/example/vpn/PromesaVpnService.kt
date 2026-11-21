package com.example.vpn

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.util.Log
import com.example.R
import com.example.data.PromesaPreferences
import com.example.domain.BlockedDomains
import com.example.notifications.PromesaNotificationManager
import com.example.ui.BlockedActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

data class VpnRuntimeState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val remainingPauseSeconds: Long = 0L,
    val lastBlockedDomain: String? = null
)

class PromesaVpnService : VpnService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferences by lazy { PromesaPreferences.getInstance(applicationContext) }

    @Volatile
    private var tunInterface: ParcelFileDescriptor? = null

    @Volatile
    private var tunOutputStream: FileOutputStream? = null

    private val tunWriteLock = Any()
    private var packetLoopJob: Job? = null
    private var stateObserverJob: Job? = null
    private var countdownJob: Job? = null

    @Volatile
    private var pauseUntilEpochMs: Long = 0L

    private val forwardSemaphore = Semaphore(64)
    private val lastAlertByDomain = ConcurrentHashMap<String, Long>()
    private val nextDnsResolver = NextDnsResolver { socket -> protect(socket) }

    @Volatile
    private var lastGlobalAlertMs: Long = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpnInternal()
            return START_NOT_STICKY
        }

        startForegroundSafely(remainingSeconds = 0L)
        observePreferences()
        startCountdownTicker()
        ensureTunnelRunning()

        return START_STICKY
    }

    private fun startForegroundSafely(remainingSeconds: Long) {
        val notification = PromesaNotificationManager.buildVpnStatusNotification(
            context = this,
            remainingPauseSeconds = remainingSeconds
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                PromesaNotificationManager.VPN_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(PromesaNotificationManager.VPN_NOTIFICATION_ID, notification)
        }
    }

    private fun observePreferences() {
        if (stateObserverJob?.isActive == true) return
        stateObserverJob = serviceScope.launch {
            preferences.stateFlow.collectLatest { state ->
                pauseUntilEpochMs = state.pauseUntilEpochMillis
                val remaining = state.remainingPauseSeconds()
                _runtimeState.value = _runtimeState.value.copy(
                    isPaused = remaining > 0L,
                    remainingPauseSeconds = remaining
                )
                updateForegroundNotification(remaining)
            }
        }
    }

    private fun startCountdownTicker() {
        if (countdownJob?.isActive == true) return
        countdownJob = serviceScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val until = pauseUntilEpochMs
                if (until > 0L) {
                    val remaining = if (until > now) (until - now + 999L) / 1000L else 0L
                    if (remaining == 0L) {
                        preferences.clearPause()
                        pauseUntilEpochMs = 0L
                    }
                    _runtimeState.value = _runtimeState.value.copy(
                        isPaused = remaining > 0L,
                        remainingPauseSeconds = remaining
                    )
                    updateForegroundNotification(remaining)
                }
                delay(1000L)
            }
        }
    }

    private fun updateForegroundNotification(remainingSeconds: Long) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val notification = PromesaNotificationManager.buildVpnStatusNotification(
            context = this,
            remainingPauseSeconds = remainingSeconds
        )
        manager.notify(PromesaNotificationManager.VPN_NOTIFICATION_ID, notification)
    }

    private fun ensureTunnelRunning() {
        if (tunInterface != null && packetLoopJob?.isActive == true) return

        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(1500)
            .addAddress(TUN_IPV4_CLIENT, 32)
            .addDnsServer(TUN_IPV4_DNS)
            .addRoute(TUN_IPV4_DNS, 32)
            .addAddress(TUN_IPV6_CLIENT, 128)
            .addDnsServer(TUN_IPV6_DNS)
            .addRoute(TUN_IPV6_DNS, 128)
            .setBlocking(true)

        try {
            builder.addDisallowedApplication(packageName)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to disallow own package", e)
        }

        val established = try {
            builder.establish()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish VPN interface", e)
            null
        }

        if (established == null) {
            stopVpnInternal()
            return
        }

        tunInterface = established
        tunOutputStream = FileOutputStream(established.fileDescriptor)
        _runtimeState.value = _runtimeState.value.copy(isRunning = true)

        packetLoopJob?.cancel()
        packetLoopJob = serviceScope.launch {
            runPacketLoop(established)
        }
    }

    private fun runPacketLoop(pfd: ParcelFileDescriptor) {
        val input = FileInputStream(pfd.fileDescriptor)
        val buffer = ByteArray(4096)

        try {
            while (serviceScope.isActive && tunInterface === pfd) {
                val bytesRead = input.read(buffer)
                if (bytesRead < 0) break
                if (bytesRead == 0) continue

                val parsed = DnsPacketProcessor.parseDnsPacket(buffer, bytesRead) ?: continue
                val isPaused = System.currentTimeMillis() < pauseUntilEpochMs

                if (!isPaused && BlockedDomains.isBlockedDomain(parsed.qname)) {
                    val nxResponse = DnsPacketProcessor.buildNxDomainDnsResponse(parsed.dnsPayload)
                    val replyPacket = DnsPacketProcessor.buildIpUdpReplyPacket(parsed, nxResponse)
                    writeToTun(replyPacket)
                    handleBlockedDomain(parsed.qname)
                } else {
                    serviceScope.launch {
                        forwardSemaphore.withPermit {
                            forwardDnsQuery(parsed)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (tunInterface != null) {
                Log.w(TAG, "VPN packet loop ended: ${e.message}")
            }
        }
    }

    private fun forwardDnsQuery(parsed: ParsedDnsPacket) {
        val dnsResponse = nextDnsResolver.resolve(parsed.dnsPayload) ?: return
        val ipReply = DnsPacketProcessor.buildIpUdpReplyPacket(parsed, dnsResponse)
        writeToTun(ipReply)
    }

    private fun writeToTun(packet: ByteArray) {
        val out = tunOutputStream ?: return
        synchronized(tunWriteLock) {
            try {
                out.write(packet)
            } catch (e: Exception) {
                Log.w(TAG, "Failed writing reply to TUN: ${e.message}")
            }
        }
    }

    private fun handleBlockedDomain(qname: String) {
        val now = System.currentTimeMillis()
        val displayDomain = BlockedDomains.normalizeHost(qname)
        val rootDomain = BlockedDomains.matchedRootDomain(displayDomain) ?: displayDomain

        val lastForDomain = lastAlertByDomain[rootDomain] ?: 0L
        if (now - lastForDomain < DOMAIN_DEBOUNCE_MS || now - lastGlobalAlertMs < GLOBAL_DEBOUNCE_MS) {
            return
        }
        lastAlertByDomain[rootDomain] = now
        lastGlobalAlertMs = now

        _runtimeState.value = _runtimeState.value.copy(lastBlockedDomain = displayDomain)
        serviceScope.launch {
            val result = preferences.recordBlockedAttempt(
                domain = displayDomain,
                source = "VPN",
                nowMs = now
            )
            if (result.shouldTriggerSilentAlert) {
                PromesaNotificationManager.showTrustedContactSilentAlert(
                    context = applicationContext,
                    contactName = result.trustedContactName,
                    whatsAppNumber = result.trustedWhatsAppNumber,
                    attemptsInHour = result.attemptsInLastHour
                )
            }
        }

        PromesaNotificationManager.showBlockAlertNotification(applicationContext, displayDomain)

        if (Settings.canDrawOverlays(applicationContext)) {
            try {
                startActivity(BlockedActivity.createIntent(applicationContext, displayDomain))
            } catch (e: Exception) {
                Log.w(TAG, "Unable to start BlockedActivity directly: ${e.message}")
            }
        }
    }

    override fun onRevoke() {
        serviceScope.launch {
            preferences.setProtectionEnabled(false)
        }
        stopVpnInternal()
        super.onRevoke()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        serviceScope.launch {
            val state = preferences.currentState()
            if (state.isProtectionEnabled && prepare(applicationContext) == null) {
                startService(applicationContext)
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        nextDnsResolver.closeAll()
        closeTunnel()
        serviceScope.cancel()
        _runtimeState.value = VpnRuntimeState(isRunning = false)
        super.onDestroy()
    }

    private fun stopVpnInternal() {
        packetLoopJob?.cancel()
        stateObserverJob?.cancel()
        countdownJob?.cancel()
        closeTunnel()
        _runtimeState.value = VpnRuntimeState(isRunning = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun closeTunnel() {
        try {
            tunOutputStream?.close()
        } catch (_: Exception) {
        }
        tunOutputStream = null
        try {
            tunInterface?.close()
        } catch (_: Exception) {
        }
        tunInterface = null
    }

    companion object {
        private const val TAG = "PromesaVpnService"
        const val ACTION_START = "com.example.promesa.ACTION_START"
        const val ACTION_STOP = "com.example.promesa.ACTION_STOP"

        private const val TUN_IPV4_CLIENT = "10.111.222.1"
        private const val TUN_IPV4_DNS = "10.111.222.2"
        private const val TUN_IPV6_CLIENT = "fd00:1:fd00:1::1"
        private const val TUN_IPV6_DNS = "fd00:1:fd00:1::2"

        private const val DOMAIN_DEBOUNCE_MS = 4000L
        private const val GLOBAL_DEBOUNCE_MS = 2000L

        private val _runtimeState = MutableStateFlow(VpnRuntimeState())
        val runtimeState: StateFlow<VpnRuntimeState> = _runtimeState.asStateFlow()

        fun startService(context: Context) {
            val intent = Intent(context, PromesaVpnService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, PromesaVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
