package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.PinHasher
import com.example.data.PinVerificationResult
import com.example.notifications.PromesaNotificationManager
import com.example.ui.PinActionPurpose
import com.example.ui.theme.CrimsonAccent
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonSoft
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MidnightOutline
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.MidnightSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class PinStage {
    VERIFY_CURRENT,
    ENTER_NEW,
    CONFIRM_NEW
}

@Composable
fun PinPadDialog(
    purpose: PinActionPurpose,
    pinLockoutUntilEpochMillis: Long,
    onVerifyPin: suspend (String) -> PinVerificationResult,
    onSaveNewPin: suspend (String) -> Boolean,
    onAuthorized: () -> Unit,
    onDismiss: () -> Unit
) {
    val initialStage = when (purpose) {
        PinActionPurpose.CREATE_PIN -> PinStage.ENTER_NEW
        PinActionPurpose.CHANGE_PIN,
        PinActionPurpose.PAUSE_15_MIN,
        PinActionPurpose.STOP_VPN,
        PinActionPurpose.CLEAR_HISTORY,
        PinActionPurpose.UNINSTALL_GUARD -> PinStage.VERIFY_CURRENT
    }

    var stage by remember(purpose) { mutableStateOf(initialStage) }
    var currentInput by remember(purpose) { mutableStateOf("") }
    var pendingNewPin by remember(purpose) { mutableStateOf("") }
    var errorText by remember(purpose) { mutableStateOf<String?>(null) }
    var isSubmitting by remember(purpose) { mutableStateOf(false) }
    var localLockoutUntilMs by remember(pinLockoutUntilEpochMillis) {
        mutableLongStateOf(pinLockoutUntilEpochMillis)
    }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(localLockoutUntilMs) {
        while (localLockoutUntilMs > System.currentTimeMillis()) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
        nowMs = System.currentTimeMillis()
    }

    val lockoutRemainingSeconds = if (localLockoutUntilMs > nowMs) {
        (localLockoutUntilMs - nowMs + 999L) / 1000L
    } else {
        0L
    }
    val isLockedOut = stage == PinStage.VERIFY_CURRENT && lockoutRemainingSeconds > 0L

    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val minLengthMsg = stringResource(R.string.pin_error_min_length)
    val mismatchMsg = stringResource(R.string.pin_error_mismatch)
    val lockedOutBanner = if (isLockedOut) {
        stringResource(
            R.string.pin_error_locked_out,
            PromesaNotificationManager.formatMinutesSeconds(lockoutRemainingSeconds)
        )
    } else {
        null
    }

    val titleText = when (stage) {
        PinStage.VERIFY_CURRENT -> when (purpose) {
            PinActionPurpose.CHANGE_PIN -> stringResource(R.string.pin_current_title)
            else -> stringResource(R.string.pin_enter_title)
        }
        PinStage.ENTER_NEW -> when (purpose) {
            PinActionPurpose.CHANGE_PIN -> stringResource(R.string.pin_new_title)
            else -> stringResource(R.string.pin_create_title)
        }
        PinStage.CONFIRM_NEW -> stringResource(R.string.pin_confirm_title)
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    fun appendDigit(digit: String) {
        if (isSubmitting || isLockedOut || currentInput.length >= 8) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        errorText = null
        currentInput += digit
    }

    fun removeDigit() {
        if (isSubmitting || isLockedOut || currentInput.isEmpty()) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        errorText = null
        currentInput = currentInput.dropLast(1)
    }

    fun submitCurrent() {
        if (isSubmitting || isLockedOut) return
        if (!PinHasher.isValidPinFormat(currentInput)) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            errorText = minLengthMsg
            return
        }

        scope.launch {
            isSubmitting = true
            try {
                when (stage) {
                    PinStage.VERIFY_CURRENT -> {
                        when (val result = onVerifyPin(currentInput)) {
                            PinVerificationResult.Success -> {
                                if (purpose == PinActionPurpose.CHANGE_PIN) {
                                    currentInput = ""
                                    stage = PinStage.ENTER_NEW
                                } else {
                                    onAuthorized()
                                }
                            }
                            is PinVerificationResult.Incorrect -> {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                errorText = context.getString(
                                    R.string.pin_error_incorrect,
                                    result.remainingAttempts
                                )
                                currentInput = ""
                            }
                            is PinVerificationResult.LockedOut -> {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                val now = System.currentTimeMillis()
                                nowMs = now
                                localLockoutUntilMs = now + result.remainingSeconds * 1000L
                                currentInput = ""
                            }
                        }
                    }
                    PinStage.ENTER_NEW -> {
                        pendingNewPin = currentInput
                        currentInput = ""
                        stage = PinStage.CONFIRM_NEW
                    }
                    PinStage.CONFIRM_NEW -> {
                        if (currentInput != pendingNewPin) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            errorText = mismatchMsg
                            currentInput = ""
                            pendingNewPin = ""
                            stage = PinStage.ENTER_NEW
                        } else {
                            onSaveNewPin(currentInput)
                            onAuthorized()
                        }
                    }
                }
            } finally {
                isSubmitting = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth()
                .widthIn(max = 400.dp),
            shape = RoundedCornerShape(28.dp),
            color = MidnightSurface,
            border = BorderStroke(1.dp, MidnightOutline)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(CrimsonContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = CrimsonAccent,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.pin_subtitle_min_4),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MidnightSurfaceElevated)
                        .border(1.dp, MidnightOutline, RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val slotCount = maxOf(4, currentInput.length)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (index in 0 until slotCount) {
                            val filled = index < currentInput.length
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(if (filled) CrimsonAccent else TextMuted.copy(alpha = 0.35f))
                            )
                        }
                    }
                }

                val displayError = lockedOutBanner ?: errorText
                AnimatedVisibility(visible = displayError != null) {
                    displayError?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = if (isLockedOut) JetBrainsMonoFontFamily else MaterialTheme.typography.bodyMedium.fontFamily
                            ),
                            color = CrimsonSoft,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .testTag("pin_error_text")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9")
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { rowDigits ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                        ) {
                            rowDigits.forEach { digit ->
                                PinDigitKey(
                                    digit = digit,
                                    enabled = !isLockedOut,
                                    onClick = { appendDigit(digit) }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.size(68.dp))
                        PinDigitKey(
                            digit = "0",
                            enabled = !isLockedOut,
                            onClick = { appendDigit("0") }
                        )
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .background(MidnightSurfaceElevated.copy(alpha = if (isLockedOut) 0.35f else 0.7f))
                                .clickable(enabled = !isLockedOut, onClick = { removeDigit() })
                                .testTag("pin_backspace_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Backspace,
                                contentDescription = stringResource(R.string.pin_backspace_cd),
                                tint = if (isLockedOut) TextMuted else TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("pin_cancel_button")
                    ) {
                        Text(
                            text = stringResource(R.string.pin_button_cancel),
                            color = TextSecondary
                        )
                    }

                    Button(
                        onClick = { submitCurrent() },
                        enabled = !isLockedOut && currentInput.length >= PinHasher.MIN_PIN_LENGTH && !isSubmitting,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("pin_confirm_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CrimsonAccent,
                            contentColor = TextPrimary,
                            disabledContainerColor = MidnightSurfaceElevated,
                            disabledContentColor = TextMuted
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.pin_button_confirm),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PinDigitKey(
    digit: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(68.dp)
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(MidnightSurfaceElevated.copy(alpha = if (enabled) 1f else 0.4f))
            .border(1.dp, MidnightOutline.copy(alpha = 0.65f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag("pin_digit_$digit"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit,
            fontFamily = JetBrainsMonoFontFamily,
            style = MaterialTheme.typography.headlineMedium,
            color = if (enabled) TextPrimary else TextMuted
        )
    }
}
