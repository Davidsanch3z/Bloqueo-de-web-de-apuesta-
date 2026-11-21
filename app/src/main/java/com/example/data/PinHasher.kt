package com.example.data

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

data class HashedPin(
    val hashBase64: String,
    val saltBase64: String
)

object PinHasher {
    const val MIN_PIN_LENGTH = 4
    private val secureRandom = SecureRandom()

    fun isValidPinFormat(pin: String): Boolean {
        return pin.length >= MIN_PIN_LENGTH && pin.all { it.isDigit() }
    }

    fun createHashedPin(pin: String): HashedPin {
        require(isValidPinFormat(pin)) {
            "El PIN debe contener al menos $MIN_PIN_LENGTH dígitos numéricos."
        }
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val saltBase64 = Base64.getEncoder().encodeToString(salt)
        val hashBytes = sha256(saltBase64, pin)
        val hashBase64 = Base64.getEncoder().encodeToString(hashBytes)
        return HashedPin(hashBase64 = hashBase64, saltBase64 = saltBase64)
    }

    fun verifyPin(
        pin: String,
        expectedHashBase64: String?,
        saltBase64: String?
    ): Boolean {
        if (expectedHashBase64.isNullOrBlank() || saltBase64.isNullOrBlank()) return false
        if (!isValidPinFormat(pin)) return false
        val candidateBytes = sha256(saltBase64, pin)
        val expectedBytes = try {
            Base64.getDecoder().decode(expectedHashBase64)
        } catch (_: IllegalArgumentException) {
            return false
        }
        return MessageDigest.isEqual(candidateBytes, expectedBytes)
    }

    private fun sha256(saltBase64: String, pin: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest("$saltBase64:$pin".toByteArray(Charsets.UTF_8))
    }
}
