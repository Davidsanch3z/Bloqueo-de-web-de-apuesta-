package com.example.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.PinVerificationResult
import com.example.data.PromesaPreferences
import com.example.data.PromesaSettingsState
import com.example.ui.components.PinPadDialog
import com.example.ui.theme.CrimsonAccent
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonSoft
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.MidnightBg
import com.example.ui.theme.MidnightOutline
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.MidnightSurfaceElevated
import com.example.ui.theme.PromesaTheme
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.utils.WhatsAppHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private val ROTATING_MOTIVATIONAL_MESSAGES = listOf(
    "Se lo prometiste a tu mamá",
    "¿Vale la pena?",
    "Recuerda por qué empezaste",
    "Tú puedes más que esto"
)

class BlockedActivity : ComponentActivity() {

    private var blockedDomain by mutableStateOf("")
    private var guardMode by mutableStateOf(GUARD_MODE_BLOCK)
    private var settingsState by mutableStateOf(PromesaSettingsState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        applyIntentExtras(intent)

        val preferences = PromesaPreferences.getInstance(applicationContext)

        setContent {
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                preferences.stateFlow.collectLatest { state ->
                    settingsState = state
                }
            }

            PromesaTheme {
                BackHandler {
                    navigateToDeviceHome()
                }

                if (guardMode == GUARD_MODE_UNINSTALL_PIN) {
                    UninstallPinLockScreen(
                        settingsState = settingsState,
                        onVerifyPin = { pin -> preferences.verifyPinWithLockout(pin) },
                        onPinAuthorized = {
                            scope.launch {
                                preferences.grantUninstallUnlockWindow()
                                Toast.makeText(
                                    applicationContext,
                                    getString(R.string.uninstall_guard_unlocked),
                                    Toast.LENGTH_LONG
                                ).show()
                                finish()
                            }
                        },
                        onCancelToHome = { navigateToDeviceHome() }
                    )
                } else {
                    BlockedScreen(
                        blockedDomain = blockedDomain,
                        onCrisisClick = {
                            WhatsAppHelper.openWhatsAppCrisis(
                                context = this@BlockedActivity,
                                contactName = settingsState.trustedContactName,
                                phoneNumber = settingsState.trustedWhatsAppNumber
                            )
                        },
                        onCloseClick = { navigateToDeviceHome() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntentExtras(intent)
    }

    private fun applyIntentExtras(intent: Intent?) {
        blockedDomain = intent?.getStringExtra(EXTRA_BLOCKED_DOMAIN).orEmpty()
        guardMode = intent?.getStringExtra(EXTRA_GUARD_MODE) ?: GUARD_MODE_BLOCK
    }

    private fun navigateToDeviceHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finishAndRemoveTask()
    }

    companion object {
        const val EXTRA_BLOCKED_DOMAIN = "extra_blocked_domain"
        const val EXTRA_GUARD_MODE = "extra_guard_mode"
        const val GUARD_MODE_BLOCK = "block"
        const val GUARD_MODE_UNINSTALL_PIN = "uninstall_pin"

        fun createIntent(context: Context, domain: String): Intent {
            return Intent(context, BlockedActivity::class.java).apply {
                putExtra(EXTRA_BLOCKED_DOMAIN, domain)
                putExtra(EXTRA_GUARD_MODE, GUARD_MODE_BLOCK)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        }

        fun createUninstallGuardIntent(context: Context): Intent {
            return Intent(context, BlockedActivity::class.java).apply {
                putExtra(EXTRA_GUARD_MODE, GUARD_MODE_UNINSTALL_PIN)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        }
    }
}

@Composable
fun BlockedScreen(
    blockedDomain: String,
    onCrisisClick: () -> Unit,
    onCloseClick: () -> Unit
) {
    val pulseTransition = rememberInfiniteTransition(label = "lock_halo")
    val haloAlpha by pulseTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_alpha"
    )

    var messageIndex by remember(blockedDomain) {
        mutableIntStateOf(ROTATING_MOTIVATIONAL_MESSAGES.indices.random())
    }

    LaunchedEffect(blockedDomain) {
        while (true) {
            delay(4500L)
            messageIndex = (messageIndex + 1) % ROTATING_MOTIVATIONAL_MESSAGES.size
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBg)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            CrimsonAccent.copy(alpha = haloAlpha),
                            Color.Transparent
                        ),
                        center = center.copy(y = size.height * 0.32f),
                        radius = size.minDimension * 0.68f
                    )
                )
            }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 28.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.weight(0.8f))

            Box(
                modifier = Modifier
                    .size(116.dp)
                    .clip(CircleShape)
                    .background(CrimsonContainer.copy(alpha = 0.72f))
                    .border(
                        width = 2.dp,
                        color = CrimsonAccent.copy(alpha = 0.85f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = stringResource(R.string.block_icon_cd),
                    tint = CrimsonAccent,
                    modifier = Modifier.size(54.dp)
                )
            }

            Spacer(modifier = Modifier.height(30.dp))

            Text(
                text = stringResource(R.string.block_title_promise),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 31.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.block_subtitle_decision),
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Second rotating motivational message
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MidnightSurface.copy(alpha = 0.9f),
                border = BorderStroke(1.dp, MidnightOutline.copy(alpha = 0.8f))
            ) {
                AnimatedContent(
                    targetState = ROTATING_MOTIVATIONAL_MESSAGES[messageIndex],
                    transitionSpec = {
                        fadeIn(tween(400)) togetherWith fadeOut(tween(400))
                    },
                    label = "rotating_quote"
                ) { phrase ->
                    Text(
                        text = "“$phrase”",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = CrimsonSoft,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                            .testTag("rotating_block_message")
                    )
                }
            }

            if (blockedDomain.isNotBlank()) {
                Spacer(modifier = Modifier.height(18.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MidnightSurface,
                    border = BorderStroke(1.dp, CrimsonAccent.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = stringResource(R.string.block_domain_intercepted_label, blockedDomain),
                        style = MaterialTheme.typography.labelMedium,
                        color = CrimsonSoft,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            OutlinedButton(
                onClick = onCrisisClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("crisis_mode_button"),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, EmeraldShield.copy(alpha = 0.75f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MidnightSurfaceElevated.copy(alpha = 0.55f),
                    contentColor = EmeraldShield
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.block_button_crisis),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onCloseClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("close_block_screen_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CrimsonAccent,
                    contentColor = TextPrimary
                )
            ) {
                Text(
                    text = stringResource(R.string.block_button_close),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun UninstallPinLockScreen(
    settingsState: PromesaSettingsState,
    onVerifyPin: suspend (String) -> PinVerificationResult,
    onPinAuthorized: () -> Unit,
    onCancelToHome: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = CrimsonAccent,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.uninstall_guard_title),
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.uninstall_guard_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }

        PinPadDialog(
            purpose = PinActionPurpose.UNINSTALL_GUARD,
            pinLockoutUntilEpochMillis = settingsState.pinLockoutUntilEpochMillis,
            onVerifyPin = onVerifyPin,
            onSaveNewPin = { true },
            onAuthorized = onPinAuthorized,
            onDismiss = onCancelToHome
        )
    }
}
