package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.widget.PromesaStreakWidgetProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import java.io.File
import java.util.concurrent.TimeUnit

private val Context.promesaDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "promesa_preferences"
)

data class BlockedEvent(
    val domain: String,
    val timestampMillis: Long,
    val source: String = "VPN"
)

data class PromesaSettingsState(
    val hasPinConfigured: Boolean = false,
    val isProtectionEnabled: Boolean = false,
    val pauseUntilEpochMillis: Long = 0L,
    val totalBlockedCount: Int = 0,
    val recentBlocks: List<BlockedEvent> = emptyList(),
    val failedPinAttempts: Int = 0,
    val pinLockoutUntilEpochMillis: Long = 0L,
    val trustedContactName: String = "",
    val trustedWhatsAppNumber: String = "",
    val protectionStartEpochMillis: Long = 0L,
    val lastBlockedEpochMillis: Long = 0L,
    val lastSilentAlertEpochMillis: Long = 0L,
    val uninstallUnlockUntilEpochMillis: Long = 0L
) {
    fun isPaused(nowMs: Long = System.currentTimeMillis()): Boolean {
        return pauseUntilEpochMillis > nowMs
    }

    fun remainingPauseSeconds(nowMs: Long = System.currentTimeMillis()): Long {
        val diff = pauseUntilEpochMillis - nowMs
        return if (diff > 0L) (diff + 999L) / 1000L else 0L
    }

    fun isPinLockedOut(nowMs: Long = System.currentTimeMillis()): Boolean {
        return pinLockoutUntilEpochMillis > nowMs
    }

    fun remainingPinLockoutSeconds(nowMs: Long = System.currentTimeMillis()): Long {
        val diff = pinLockoutUntilEpochMillis - nowMs
        return if (diff > 0L) (diff + 999L) / 1000L else 0L
    }

    fun isUninstallUnlocked(nowMs: Long = System.currentTimeMillis()): Boolean {
        return uninstallUnlockUntilEpochMillis > nowMs
    }

    fun daysWithoutBlockedAttempts(nowMs: Long = System.currentTimeMillis()): Int {
        val reference = when {
            lastBlockedEpochMillis > 0L -> lastBlockedEpochMillis
            protectionStartEpochMillis > 0L -> protectionStartEpochMillis
            else -> return 0
        }
        val diff = (nowMs - reference).coerceAtLeast(0L)
        return TimeUnit.MILLISECONDS.toDays(diff).toInt()
    }
}

sealed interface PinVerificationResult {
    data object Success : PinVerificationResult
    data class Incorrect(val remainingAttempts: Int) : PinVerificationResult
    data class LockedOut(val remainingSeconds: Long) : PinVerificationResult
}

data class BlockedRecordResult(
    val attemptsInLastHour: Int,
    val shouldTriggerSilentAlert: Boolean,
    val trustedContactName: String,
    val trustedWhatsAppNumber: String
)

class PromesaPreferences private constructor(private val context: Context) {

    private object Keys {
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val PROTECTION_ENABLED = booleanPreferencesKey("protection_enabled")
        val PAUSE_UNTIL_EPOCH_MS = longPreferencesKey("pause_until_epoch_ms")
        val TOTAL_BLOCKED_COUNT = intPreferencesKey("total_blocked_count")
        val RECENT_BLOCKS = stringPreferencesKey("recent_blocks")
        val FAILED_PIN_ATTEMPTS = intPreferencesKey("failed_pin_attempts")
        val PIN_LOCKOUT_UNTIL_MS = longPreferencesKey("pin_lockout_until_ms")
        val TRUSTED_CONTACT_NAME = stringPreferencesKey("trusted_contact_name")
        val TRUSTED_WHATSAPP_NUMBER = stringPreferencesKey("trusted_whatsapp_number")
        val PROTECTION_START_EPOCH_MS = longPreferencesKey("protection_start_epoch_ms")
        val LAST_BLOCKED_EPOCH_MS = longPreferencesKey("last_blocked_epoch_ms")
        val LAST_SILENT_ALERT_EPOCH_MS = longPreferencesKey("last_silent_alert_epoch_ms")
        val UNINSTALL_UNLOCK_UNTIL_MS = longPreferencesKey("uninstall_unlock_until_ms")
    }

    private val logLock = Any()
    private val logFile = File(context.filesDir, "promesa_block_log.tsv")
    private val logEventsFlow = MutableStateFlow(readFullLogFromDisk())

    val stateFlow: Flow<PromesaSettingsState> = combine(
        context.promesaDataStore.data,
        logEventsFlow
    ) { prefs, fullLog ->
        val hasPin = !prefs[Keys.PIN_HASH].isNullOrBlank() && !prefs[Keys.PIN_SALT].isNullOrBlank()
        val combinedEvents = if (fullLog.isNotEmpty()) {
            fullLog
        } else {
            parseRecentBlocks(prefs[Keys.RECENT_BLOCKS].orEmpty())
        }
        PromesaSettingsState(
            hasPinConfigured = hasPin,
            isProtectionEnabled = prefs[Keys.PROTECTION_ENABLED] ?: false,
            pauseUntilEpochMillis = prefs[Keys.PAUSE_UNTIL_EPOCH_MS] ?: 0L,
            totalBlockedCount = prefs[Keys.TOTAL_BLOCKED_COUNT] ?: combinedEvents.size,
            recentBlocks = combinedEvents,
            failedPinAttempts = prefs[Keys.FAILED_PIN_ATTEMPTS] ?: 0,
            pinLockoutUntilEpochMillis = prefs[Keys.PIN_LOCKOUT_UNTIL_MS] ?: 0L,
            trustedContactName = prefs[Keys.TRUSTED_CONTACT_NAME].orEmpty(),
            trustedWhatsAppNumber = prefs[Keys.TRUSTED_WHATSAPP_NUMBER].orEmpty(),
            protectionStartEpochMillis = prefs[Keys.PROTECTION_START_EPOCH_MS] ?: 0L,
            lastBlockedEpochMillis = prefs[Keys.LAST_BLOCKED_EPOCH_MS] ?: 0L,
            lastSilentAlertEpochMillis = prefs[Keys.LAST_SILENT_ALERT_EPOCH_MS] ?: 0L,
            uninstallUnlockUntilEpochMillis = prefs[Keys.UNINSTALL_UNLOCK_UNTIL_MS] ?: 0L
        )
    }

    suspend fun currentState(): PromesaSettingsState = stateFlow.first()

    suspend fun setPin(pin: String) {
        val hashed = PinHasher.createHashedPin(pin)
        context.promesaDataStore.edit { prefs ->
            prefs[Keys.PIN_HASH] = hashed.hashBase64
            prefs[Keys.PIN_SALT] = hashed.saltBase64
            prefs[Keys.FAILED_PIN_ATTEMPTS] = 0
            prefs[Keys.PIN_LOCKOUT_UNTIL_MS] = 0L
        }
    }

    suspend fun verifyPinWithLockout(
        pin: String,
        nowMs: Long = System.currentTimeMillis()
    ): PinVerificationResult {
        val prefs = context.promesaDataStore.data.first()
        val lockoutUntil = prefs[Keys.PIN_LOCKOUT_UNTIL_MS] ?: 0L
        if (lockoutUntil > nowMs) {
            val remaining = (lockoutUntil - nowMs + 999L) / 1000L
            return PinVerificationResult.LockedOut(remaining)
        }

        val valid = PinHasher.verifyPin(
            pin = pin,
            expectedHashBase64 = prefs[Keys.PIN_HASH],
            saltBase64 = prefs[Keys.PIN_SALT]
        )
        if (valid) {
            context.promesaDataStore.edit { editable ->
                editable[Keys.FAILED_PIN_ATTEMPTS] = 0
                editable[Keys.PIN_LOCKOUT_UNTIL_MS] = 0L
            }
            return PinVerificationResult.Success
        }

        var lockedOutSeconds = 0L
        var remainingAttempts = MAX_PIN_ATTEMPTS
        context.promesaDataStore.edit { editable ->
            val currentFailures = (editable[Keys.FAILED_PIN_ATTEMPTS] ?: 0) + 1
            if (currentFailures >= MAX_PIN_ATTEMPTS) {
                editable[Keys.FAILED_PIN_ATTEMPTS] = 0
                editable[Keys.PIN_LOCKOUT_UNTIL_MS] = nowMs + PIN_LOCKOUT_DURATION_MS
                lockedOutSeconds = PIN_LOCKOUT_DURATION_MS / 1000L
                remainingAttempts = 0
            } else {
                editable[Keys.FAILED_PIN_ATTEMPTS] = currentFailures
                remainingAttempts = MAX_PIN_ATTEMPTS - currentFailures
            }
        }

        return if (lockedOutSeconds > 0L) {
            PinVerificationResult.LockedOut(lockedOutSeconds)
        } else {
            PinVerificationResult.Incorrect(remainingAttempts)
        }
    }

    suspend fun setProtectionEnabled(
        enabled: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ) {
        var referenceForWidget = 0L
        context.promesaDataStore.edit { prefs ->
            prefs[Keys.PROTECTION_ENABLED] = enabled
            if (enabled && (prefs[Keys.PROTECTION_START_EPOCH_MS] ?: 0L) == 0L) {
                prefs[Keys.PROTECTION_START_EPOCH_MS] = nowMs
            }
            if (!enabled) {
                prefs[Keys.PAUSE_UNTIL_EPOCH_MS] = 0L
            }
            val lastBlocked = prefs[Keys.LAST_BLOCKED_EPOCH_MS] ?: 0L
            val start = prefs[Keys.PROTECTION_START_EPOCH_MS] ?: 0L
            referenceForWidget = if (lastBlocked > 0L) lastBlocked else start
        }
        syncWidgetReference(referenceForWidget)
    }

    suspend fun startFifteenMinutePause(nowMs: Long = System.currentTimeMillis()) {
        context.promesaDataStore.edit { prefs ->
            prefs[Keys.PAUSE_UNTIL_EPOCH_MS] = nowMs + FIXED_PAUSE_DURATION_MS
        }
    }

    suspend fun clearPause() {
        context.promesaDataStore.edit { prefs ->
            prefs[Keys.PAUSE_UNTIL_EPOCH_MS] = 0L
        }
    }

    suspend fun grantUninstallUnlockWindow(nowMs: Long = System.currentTimeMillis()) {
        context.promesaDataStore.edit { prefs ->
            prefs[Keys.UNINSTALL_UNLOCK_UNTIL_MS] = nowMs + UNINSTALL_UNLOCK_WINDOW_MS
        }
    }

    suspend fun setTrustedContact(name: String, whatsAppNumber: String) {
        context.promesaDataStore.edit { prefs ->
            prefs[Keys.TRUSTED_CONTACT_NAME] = name.trim()
            prefs[Keys.TRUSTED_WHATSAPP_NUMBER] = whatsAppNumber.trim()
        }
    }

    suspend fun recordBlockedAttempt(
        domain: String,
        source: String = "VPN",
        nowMs: Long = System.currentTimeMillis()
    ): BlockedRecordResult {
        val sanitizedDomain = domain.replace("|", "").replace("\n", "").trim()
        val sanitizedSource = source.replace("|", "").replace("\n", "").trim().ifEmpty { "VPN" }
        if (sanitizedDomain.isEmpty()) {
            return BlockedRecordResult(0, false, "", "")
        }

        val event = BlockedEvent(
            domain = sanitizedDomain,
            timestampMillis = nowMs,
            source = sanitizedSource
        )
        val updatedLog = appendToDiskLog(event)
        logEventsFlow.value = updatedLog

        val oneHourAgo = nowMs - ONE_HOUR_MS
        val attemptsInLastHour = updatedLog.count { it.timestampMillis >= oneHourAgo }

        var shouldAlert = false
        var contactName = ""
        var contactNumber = ""

        context.promesaDataStore.edit { prefs ->
            val currentCount = prefs[Keys.TOTAL_BLOCKED_COUNT] ?: 0
            prefs[Keys.TOTAL_BLOCKED_COUNT] = currentCount + 1
            prefs[Keys.LAST_BLOCKED_EPOCH_MS] = nowMs
            prefs[Keys.RECENT_BLOCKS] = serializeRecentBlocks(updatedLog.take(MAX_PREF_RECENT_BLOCKS))

            contactName = prefs[Keys.TRUSTED_CONTACT_NAME].orEmpty()
            contactNumber = prefs[Keys.TRUSTED_WHATSAPP_NUMBER].orEmpty()
            val lastAlert = prefs[Keys.LAST_SILENT_ALERT_EPOCH_MS] ?: 0L
            if (attemptsInLastHour >= SILENT_ALERT_THRESHOLD &&
                nowMs - lastAlert >= ONE_HOUR_MS &&
                contactNumber.any { it.isDigit() }
            ) {
                prefs[Keys.LAST_SILENT_ALERT_EPOCH_MS] = nowMs
                shouldAlert = true
            }
        }

        syncWidgetReference(nowMs)

        return BlockedRecordResult(
            attemptsInLastHour = attemptsInLastHour,
            shouldTriggerSilentAlert = shouldAlert,
            trustedContactName = contactName,
            trustedWhatsAppNumber = contactNumber
        )
    }

    suspend fun clearHistory() {
        synchronized(logLock) {
            if (logFile.exists()) {
                logFile.delete()
            }
        }
        logEventsFlow.value = emptyList()
        context.promesaDataStore.edit { prefs ->
            prefs[Keys.RECENT_BLOCKS] = ""
            prefs[Keys.TOTAL_BLOCKED_COUNT] = 0
        }
    }

    private fun syncWidgetReference(referenceEpochMs: Long) {
        val widgetPrefs = context.getSharedPreferences(WIDGET_PREFS_NAME, Context.MODE_PRIVATE)
        widgetPrefs.edit().putLong(KEY_WIDGET_STREAK_REF_MS, referenceEpochMs).apply()
        PromesaStreakWidgetProvider.updateAllWidgets(context)
    }

    private fun appendToDiskLog(event: BlockedEvent): List<BlockedEvent> {
        synchronized(logLock) {
            val current = readFullLogFromDisk()
            val updated = (listOf(event) + current).take(MAX_DISK_LOG_ENTRIES)
            try {
                logFile.writeText(serializeRecentBlocks(updated), Charsets.UTF_8)
            } catch (_: Exception) {
            }
            return updated
        }
    }

    private fun readFullLogFromDisk(): List<BlockedEvent> {
        synchronized(logLock) {
            if (!logFile.exists()) return emptyList()
            return try {
                parseRecentBlocks(logFile.readText(Charsets.UTF_8))
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    private fun parseRecentBlocks(raw: String): List<BlockedEvent> {
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence()
            .mapNotNull { line ->
                val parts = line.split('|', limit = 3)
                when (parts.size) {
                    2 -> {
                        val ts = parts[0].toLongOrNull()
                        val domain = parts[1].trim()
                        if (ts != null && domain.isNotEmpty()) BlockedEvent(domain, ts, "VPN") else null
                    }
                    3 -> {
                        val ts = parts[0].toLongOrNull()
                        val domain = parts[1].trim()
                        val source = parts[2].trim().ifEmpty { "VPN" }
                        if (ts != null && domain.isNotEmpty()) BlockedEvent(domain, ts, source) else null
                    }
                    else -> null
                }
            }
            .toList()
    }

    private fun serializeRecentBlocks(events: List<BlockedEvent>): String {
        return events.joinToString("\n") { "${it.timestampMillis}|${it.domain}|${it.source}" }
    }

    companion object {
        const val FIXED_PAUSE_DURATION_MS: Long = 15L * 60L * 1000L
        const val PIN_LOCKOUT_DURATION_MS: Long = 30L * 60L * 1000L
        const val MAX_PIN_ATTEMPTS: Int = 3
        const val SILENT_ALERT_THRESHOLD: Int = 3
        private const val ONE_HOUR_MS: Long = 60L * 60L * 1000L
        private const val UNINSTALL_UNLOCK_WINDOW_MS: Long = 60L * 1000L
        private const val MAX_PREF_RECENT_BLOCKS = 30
        private const val MAX_DISK_LOG_ENTRIES = 500

        const val WIDGET_PREFS_NAME = "promesa_widget_prefs"
        const val KEY_WIDGET_STREAK_REF_MS = "streak_reference_epoch_ms"

        @Volatile
        private var instance: PromesaPreferences? = null

        fun getInstance(context: Context): PromesaPreferences {
            return instance ?: synchronized(this) {
                instance ?: PromesaPreferences(context.applicationContext).also { instance = it }
            }
        }
    }
}
