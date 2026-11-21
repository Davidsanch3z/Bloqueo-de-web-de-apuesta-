package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.PromesaPreferences
import com.example.domain.SearchKeywordBlocker
import com.example.notifications.PromesaNotificationManager
import com.example.ui.BlockedActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PromesaGuardAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferences by lazy { PromesaPreferences.getInstance(applicationContext) }

    @Volatile
    private var lastSearchTriggerMs = 0L

    @Volatile
    private var lastUninstallTriggerMs = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val ev = event ?: return
        val pkg = ev.packageName?.toString() ?: return
        if (pkg == packageName) return

        val root = rootInActiveWindow ?: return

        if (pkg in UNINSTALL_OR_SETTINGS_PACKAGES) {
            checkUninstallProtection(root, pkg)
        } else if (pkg in BROWSER_OR_SEARCH_PACKAGES) {
            checkGoogleSearchKeywords(root, pkg)
        }
    }

    private fun checkUninstallProtection(root: AccessibilityNodeInfo, pkg: String) {
        val now = System.currentTimeMillis()
        if (now - lastUninstallTriggerMs < UNINSTALL_DEBOUNCE_MS) return

        val texts = collectVisibleTexts(root, maxNodes = 60)
        val mentionsPromesa = texts.any { it.contains("promesa", ignoreCase = true) }
        if (!mentionsPromesa) return

        val isPackageInstaller = pkg.contains("packageinstaller")
        val hasDangerAction = texts.any { text ->
            val lower = text.lowercase()
            UNINSTALL_KEYWORDS.any { kw -> lower.contains(kw) }
        }

        if (isPackageInstaller || hasDangerAction) {
            scope.launch {
                val state = preferences.currentState()
                if (state.hasPinConfigured && !state.isUninstallUnlocked(now)) {
                    lastUninstallTriggerMs = now
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    startActivity(BlockedActivity.createUninstallGuardIntent(applicationContext))
                }
            }
        }
    }

    private fun checkGoogleSearchKeywords(root: AccessibilityNodeInfo, pkg: String) {
        val now = System.currentTimeMillis()
        if (now - lastSearchTriggerMs < SEARCH_DEBOUNCE_MS) return

        val texts = collectVisibleTexts(root, maxNodes = 80)
        if (texts.isEmpty()) return

        var matchedKeyword: String? = null
        val isGoogleApp = pkg == "com.google.android.googlequicksearchbox"

        for (text in texts) {
            matchedKeyword = SearchKeywordBlocker.findBlockedKeywordInGoogleSearch(text)
            if (matchedKeyword != null) break

            if (isGoogleApp && text.length in 3..120) {
                matchedKeyword = SearchKeywordBlocker.matchKeyword(text)
                if (matchedKeyword != null) break
            }
        }

        val keyword = matchedKeyword ?: return
        scope.launch {
            val state = preferences.currentState()
            if (state.isPaused(now)) return@launch

            lastSearchTriggerMs = now
            val label = "google:$keyword"
            val result = preferences.recordBlockedAttempt(
                domain = label,
                source = "Búsqueda",
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

            performGlobalAction(GLOBAL_ACTION_BACK)
            performGlobalAction(GLOBAL_ACTION_HOME)
            startActivity(BlockedActivity.createIntent(applicationContext, label))
        }
    }

    private fun collectVisibleTexts(
        root: AccessibilityNodeInfo,
        maxNodes: Int
    ): List<String> {
        val results = ArrayList<String>(maxNodes)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0

        while (queue.isNotEmpty() && visited < maxNodes) {
            val node = queue.removeFirst()
            visited++
            node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { results.add(it) }
            node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let {
                results.add(it)
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.addLast(it) }
            }
        }
        return results
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 4000L
        private const val UNINSTALL_DEBOUNCE_MS = 2500L

        private val UNINSTALL_OR_SETTINGS_PACKAGES = setOf(
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.settings",
            "com.miui.securitycenter",
            "com.samsung.android.lool"
        )

        private val BROWSER_OR_SEARCH_PACKAGES = setOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.sec.android.app.sbrowser",
            "org.mozilla.firefox",
            "com.microsoft.emmx",
            "com.opera.browser",
            "com.brave.browser",
            "com.duckduckgo.mobile.android",
            "com.google.android.googlequicksearchbox"
        )

        private val UNINSTALL_KEYWORDS = listOf(
            "desinstalar",
            "uninstall",
            "forzar detención",
            "forzar detencion",
            "force stop",
            "eliminar",
            "delete"
        )

        fun isServiceEnabled(context: Context): Boolean {
            val expected = ComponentName(context, PromesaGuardAccessibilityService::class.java)
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabledServices)
            while (splitter.hasNext()) {
                val componentName = ComponentName.unflattenFromString(splitter.next())
                if (componentName == expected) return true
            }
            return false
        }
    }
}
