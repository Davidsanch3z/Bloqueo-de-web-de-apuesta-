package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.example.data.PromesaPreferences
import com.example.vpn.PromesaVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val state = PromesaPreferences.getInstance(appContext).currentState()
                val vpnPrepared = VpnService.prepare(appContext) == null
                if (state.isProtectionEnabled && vpnPrepared) {
                    PromesaVpnService.startService(appContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
