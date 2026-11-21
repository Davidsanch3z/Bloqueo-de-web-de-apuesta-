package com.example.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.ui.BlockedActivity
import com.example.utils.WhatsAppHelper
import java.util.Locale

object PromesaNotificationManager {

    const val VPN_NOTIFICATION_ID = 1001
    private const val BLOCK_ALERT_NOTIFICATION_ID = 1002
    private const val TRUSTED_SILENT_ALERT_ID = 1003
    private const val CHANNEL_VPN_STATUS = "promesa_vpn_status_priority"
    private const val CHANNEL_BLOCK_ALERT = "promesa_block_alert"
    private const val CHANNEL_TRUSTED_SILENT = "promesa_trusted_silent_alert"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        // High-importance silent channel for maximum foreground survival
        val vpnChannel = NotificationChannel(
            CHANNEL_VPN_STATUS,
            context.getString(R.string.notif_channel_vpn_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_vpn_desc)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val alertChannel = NotificationChannel(
            CHANNEL_BLOCK_ALERT,
            context.getString(R.string.notif_channel_alert_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_alert_desc)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
        }

        val trustedSilentChannel = NotificationChannel(
            CHANNEL_TRUSTED_SILENT,
            context.getString(R.string.notif_channel_trusted_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notif_channel_trusted_desc)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(true)
        }

        manager.createNotificationChannel(vpnChannel)
        manager.createNotificationChannel(alertChannel)
        manager.createNotificationChannel(trustedSilentChannel)
    }

    fun buildVpnStatusNotification(
        context: Context,
        remainingPauseSeconds: Long
    ): Notification {
        ensureChannels(context)
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isPaused = remainingPauseSeconds > 0L
        val title = if (isPaused) {
            context.getString(R.string.notif_vpn_paused_title)
        } else {
            context.getString(R.string.notif_vpn_active_title)
        }
        val text = if (isPaused) {
            val formatted = formatMinutesSeconds(remainingPauseSeconds)
            context.getString(R.string.notif_vpn_paused_text, formatted)
        } else {
            context.getString(R.string.notif_vpn_active_text)
        }

        return NotificationCompat.Builder(context, CHANNEL_VPN_STATUS)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun showBlockAlertNotification(context: Context, domain: String) {
        if (!hasPostNotificationPermission(context)) return

        ensureChannels(context)
        val blockIntent = BlockedActivity.createIntent(context, domain)
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            domain.hashCode(),
            blockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_BLOCK_ALERT)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(context.getString(R.string.notif_block_alert_title))
            .setContentText(context.getString(R.string.notif_block_alert_text, domain))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .build()

        NotificationManagerCompat.from(context).notify(BLOCK_ALERT_NOTIFICATION_ID, notification)
    }

    fun showTrustedContactSilentAlert(
        context: Context,
        contactName: String,
        whatsAppNumber: String,
        attemptsInHour: Int
    ) {
        if (!hasPostNotificationPermission(context)) return
        ensureChannels(context)

        val message = WhatsAppHelper.buildThresholdAlertMessage(contactName, attemptsInHour)
        val whatsAppIntent = WhatsAppHelper.createWhatsAppIntent(whatsAppNumber, message)
        val pendingIntent = PendingIntent.getActivity(
            context,
            TRUSTED_SILENT_ALERT_ID,
            whatsAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayTarget = contactName.ifBlank { whatsAppNumber }
        val notification = NotificationCompat.Builder(context, CHANNEL_TRUSTED_SILENT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.notif_trusted_alert_title))
            .setContentText(
                context.getString(R.string.notif_trusted_alert_text, attemptsInHour, displayTarget)
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    context.getString(R.string.notif_trusted_alert_text, attemptsInHour, displayTarget)
                )
            )
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(TRUSTED_SILENT_ALERT_ID, notification)
    }

    private fun hasPostNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun formatMinutesSeconds(totalSeconds: Long): String {
        val safe = totalSeconds.coerceAtLeast(0L)
        val mins = safe / 60L
        val secs = safe % 60L
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}
