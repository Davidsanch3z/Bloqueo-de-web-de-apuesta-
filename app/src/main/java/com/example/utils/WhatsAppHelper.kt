package com.example.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.R
import java.net.URLEncoder

object WhatsAppHelper {

    fun sanitizePhoneDigits(rawNumber: String): String {
        return rawNumber.filter { it.isDigit() }
    }

    fun buildCrisisMessage(contactName: String): String {
        val greeting = if (contactName.isNotBlank()) "Hola $contactName" else "Hola"
        return "$greeting, estoy pasando por un momento difícil con un impulso de apostar y Promesa bloqueó mi intento. ¿Podemos hablar un momento?"
    }

    fun buildThresholdAlertMessage(contactName: String, attemptsInHour: Int): String {
        val greeting = if (contactName.isNotBlank()) "Hola $contactName" else "Hola"
        return "$greeting, aviso automático de Promesa: se registraron $attemptsInHour intentos bloqueados en menos de una hora. Necesito apoyo para mantener mi promesa."
    }

    fun createWhatsAppIntent(
        phoneNumber: String,
        message: String
    ): Intent {
        val digits = sanitizePhoneDigits(phoneNumber)
        val encodedText = URLEncoder.encode(message, Charsets.UTF_8.name())
        return if (digits.isNotEmpty()) {
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://wa.me/$digits?text=$encodedText")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
                setPackage("com.whatsapp")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
    }

    fun openWhatsAppCrisis(
        context: Context,
        contactName: String,
        phoneNumber: String
    ) {
        val message = buildCrisisMessage(contactName)
        val intent = createWhatsAppIntent(phoneNumber, message)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // Fallback to generic share chooser if WhatsApp is unavailable
            try {
                val fallback = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(
                    Intent.createChooser(fallback, context.getString(R.string.crisis_chooser_title)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
            } catch (_: Exception) {
                Toast.makeText(
                    context,
                    context.getString(R.string.whatsapp_not_found_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
