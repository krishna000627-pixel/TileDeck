package com.krishna.tiledeck.ui

import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.krishna.tiledeck.accessibility.TileDeckAccessibilityService
import com.krishna.tiledeck.data.AppLockFeatures
import com.krishna.tiledeck.data.TilePrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class LockPalette(
    val bg: Color,
    val surface: Color,
    val border: Color,
    val accent: Color,
    val muted: Color
)

private fun drawableToBmp(d: Drawable, px: Int = 96): ImageBitmap? = runCatching {
    val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val c = Canvas(b)
    d.setBounds(0, 0, px, px)
    d.draw(c)
    b.asImageBitmap()
}.getOrNull()

class AppLockActivity : FragmentActivity() {

    private var targetPkg: String = ""
    private var isPreviewMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetPkg = intent.getStringExtra("target_pkg") ?: packageName
        isPreviewMode = intent.getBooleanExtra("preview_mode", false)
        val forceCover = intent.getStringExtra("force_cover")

        val correctPin = TilePrefs.getAppLockPin(this)
        val duressPin = AppLockFeatures.getDuressPin(this)
        val useDuress = AppLockFeatures.isEnabled(this, "duress_pin_enabled")
        val shuffleInit = AppLockFeatures.isEnabled(this, "shuffle_keypad")
        val reshuffleOnFail = AppLockFeatures.isEnabled(this, "reshuffle_on_fail")
        val stealthBlank = AppLockFeatures.isEnabled(this, "stealth_blank_keys")
        val invisibleDots = AppLockFeatures.isEnabled(this, "invisible_dots")
        val maskName = AppLockFeatures.isEnabled(this, "hide_app_name")
        val maskIcon = AppLockFeatures.isEnabled(this, "hide_app_icon")
        val showHud = !AppLockFeatures.isEnabled(this, "hide_hud_bar")
        val showClock = AppLockFeatures.isEnabled(this, "show_live_clock")
        val showBatt = AppLockFeatures.isEnabled(this, "show_live_battery")
        val animRing = AppLockFeatures.isEnabled(this, "animated_shield_ring")
        val oneHanded = AppLockFeatures.isEnabled(this, "one_handed_keypad")
        val flatKeys = AppLockFeatures.isEnabled(this, "flat_keypad_style")
        val useHaptics = AppLockFeatures.isEnabled(this, "key_haptics")
        val useBio = AppLockFeatures.isEnabled(this, "bio_enabled") && canUseBiometric()
        val autoBio = AppLockFeatures.isEnabled(this, "bio_auto_prompt") && useBio
        val patternModeInit = AppLockFeatures.isEnabled(this, "mode_pattern")
        val hidePatternLine = AppLockFeatures.isEnabled(this, "hide_pattern_line")
        val lockoutEnabled = AppLockFeatures.isEnabled(this, "lockout_3_fails")
        val lockoutSec = if (AppLockFeatures.isEnabled(this, "lockout_30s")) 30 else 15
        val mindfulWait = AppLockFeatures.isEnabled(this, "mindful_wait_5s")
        val showOpenCount = AppLockFeatures.isEnabled(this, "show_open_count")
        val showHint = AppLockFeatures.isEnabled(this, "show_pin_hint")
        val pinHint = AppLockFeatures.getHint(this)

        val initialCover = forceCover ?: when {
            AppLockFeatures.isEnabled(this, "cover_fake_crash") -> "crash"
            AppLockFeatures.isEnabled(this, "cover_calc") -> "calc"
            AppLockFeatures.isEnabled(this, "cover_sys_update") -> "update"
            AppLockFeatures.isEnabled(this, "cover_no_net") -> "nonet"
            else -> "none"
        }

        val appInfo = runCatching { packageManager.getApplicationInfo(targetPkg, 0) }.getOrNull()
        val realAppName = appInfo?.let { packageManager.getApplicationLabel(it).toString() } ?: targetPkg
        val realAppIcon = appInfo?.let { drawableToBmp(packageManager.getApplicationIcon(it)) }
        val opensToday = if (!isPreviewMode && AppLockFeatures.isEnabled(this, "track_open_count")) {
            AppLockFeatures.incrementOpenCount(this, targetPkg)
        } else {
            AppLockFeatures.getOpenCount(this, targetPkg)
        }

        val battIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val battPct = battIntent?.let {
            val l = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val s = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (l >= 0 && s > 0) (l * 100) / s else 100
        } ?: 100

        val palette = when (AppLockFeatures.activeThemeName(this)) {
            "AMOLED" -> LockPalette(Color.Black, Color(0xFF09090B), Color(0xFF1E1E22), Color(0xFF10B981), Color(0xFF52525B))
            "CRIMSON" -> LockPalette(Color(0xFF0C0708), Color(0xFF180D10), Color(0xFF3F1D24), Color(0xFFEF4444), Color(0xFFA1A1AA))
            "EMERALD" -> LockPalette(Color(0xFF050E0B), Color(0xFF0C1D17), Color(0xFF16382C), Color(0xFF34D399), Color(0xFF94A3B8))
            "TITANIUM" -> LockPalette(Color(0xFF0A0E14), Color(0xFF131A24), Color(0xFF263347), Color(0xFF38BDF8), Color(0xFF94A3B8))
            "AMBER" -> LockPalette(Color(0xFF0B0A08), Color(0xFF171410), Color(0xFF3A2F1D), Color(0xFFF59E0B), Color(0xFFA1A1AA))
            else -> LockPalette(Color(0xFF09090B), Color(0xFF131317), Color(0xFF27272A), Color(0xFF10B981), Color(0xFF71717A))
        }

        setContent {
            var activeCover by remember { mutableStateOf(initialCover) }
            var patternMode by remember { mutableStateOf(patternModeInit) }
            var entered by remember { mutableStateOf("") }
            var error by remember { mutableStateOf(false) }
            var failCount by remember { mutableIntStateOf(0) }
            var cooldownLeft by remember { mutableIntStateOf(0) }
            var mindfulLeft by remember { mutableIntStateOf(if (mindfulWait) 5 else 0) }
            var digits by remember {
                mutableStateOf(
                    if (shuffleInit) (0..9).map { it.toString() }.shuffled()
                    else listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
                )
            }
            val shake = remember { Animatable(0f) }
            val scope = rememberCoroutineScope()
            val haptic = LocalHapticFeedback.current

            LaunchedEffect(Unit) {
                if (autoBio && activeCover == "none") launchBiometric { unlockSuccess() }
            }
            LaunchedEffect(cooldownLeft) {
                if (cooldownLeft > 0) { delay(1000); cooldownLeft-- }
            }
            LaunchedEffect(mindfulLeft) {
                if (mindfulLeft > 0) { delay(1000); mindfulLeft-- }
            }

            BackHandler {
                if (isPreviewMode) finish()
                else exitToHome()
            }

            fun onWrongAttempt(attemptValue: String) {
                error = true
                entered = ""
                failCount++
                AppLockFeatures.logIntruder(this@AppLockActivity, realAppName, "Wrong PIN ($attemptValue) | Fail #$failCount")
                if (reshuffleOnFail) digits = (0..9).map { it.toString() }.shuffled()
                if (useHaptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                scope.launch { listOf(-18f, 18f, -12f, 12f, -6f, 0f).forEach { shake.animateTo(it, tween(38)) } }
                if (lockoutEnabled && failCount >= 3) {
                    cooldownLeft = lockoutSec
                    failCount = 0
                }
            }

            fun submitPin(value: String) {
                if (cooldownLeft > 0 || mindfulLeft > 0) return
                when {
                    value == correctPin -> unlockSuccess()
                    useDuress && value == duressPin -> {
                        AppLockFeatures.logIntruder(this@AppLockActivity, realAppName, "HIGH ALERT: Duress Panic PIN entered")
                        exitToHome()
                    }
                    else -> onWrongAttempt(value)
                }
            }

            MaterialTheme(colorScheme = darkColorScheme(primary = palette.accent, background = palette.bg, surface = palette.surface)) {
                Box(modifier = Modifier.fillMaxSize().background(palette.bg)) {
                    when (activeCover) {
                        "crash" -> FakeCrashCover(
                            appName = realAppName,
                            onReveal = { activeCover = "none" },
                            onClose = { if (isPreviewMode) finish() else exitToHome() }
                        )
                        "calc" -> CalculatorDisguiseCover(
                            correctPin = correctPin,
                            palette = palette,
                            onUnlock = { unlockSuccess() },
                            onReveal = { activeCover = "none" }
                        )
                        "update", "nonet" -> GenericDecoyCover(
                            title = if (activeCover == "update") "Optimizing $realAppName..." else "No Internet Connection",
                            body = if (activeCover == "update") "Android is updating app storage. Please wait." else "Check your mobile data or Wi-Fi and try again.",
                            buttonText = if (activeCover == "update") "Hide" else "Retry",
                            onReveal = { activeCover = "none" },
                            onClose = { if (isPreviewMode) finish() else exitToHome() }
                        )
                        else -> MainLockContent(
                            palette = palette,
                            appName = if (maskName) "Protected Application" else realAppName,
                            appIcon = if (maskIcon) null else realAppIcon,
                            showHud = showHud,
                            showClock = showClock,
                            showBatt = showBatt,
                            battPct = battPct,
                            animRing = animRing,
                            oneHanded = oneHanded,
                            flatKeys = flatKeys,
                            stealthBlank = stealthBlank,
                            invisibleDots = invisibleDots,
                            useHaptics = useHaptics,
                            useBio = useBio,
                            patternMode = patternMode,
                            hidePatternLine = hidePatternLine,
                            showOpenCount = showOpenCount,
                            opensToday = opensToday,
                            showHint = showHint,
                            pinHint = pinHint,
                            isPreviewMode = isPreviewMode,
                            pinLength = correctPin.length.coerceAtLeast(4),
                            entered = entered,
                            error = error,
                            failCount = failCount,
                            cooldownLeft = cooldownLeft,
                            mindfulLeft = mindfulLeft,
                            shakeOffset = shake.value,
                            digits = digits,
                            onSwitchMode = { patternMode = !patternMode; entered = ""; error = false },
                            onBioClick = { launchBiometric { unlockSuccess() } },
                            onDigit = { d ->
                                if (cooldownLeft > 0 || mindfulLeft > 0) return@MainLockContent
                                error = false
                                if (useHaptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (entered.length < 8) {
                                    entered += d
                                    if (entered.length == correctPin.length) submitPin(entered)
                                }
                            },
                            onBackspace = { if (entered.isNotEmpty()) entered = entered.dropLast(1); error = false },
                            onClear = { entered = ""; error = false },
                            onPatternComplete = { seq ->
                                if (seq == AppLockFeatures.getPattern(this@AppLockActivity)) unlockSuccess()
                                else onWrongAttempt("Pattern:$seq")
                            }
                        )
                    }
                }
            }
        }
    }

    private fun canUseBiometric(): Boolean {
        val bm = BiometricManager.from(this)
        return bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun launchBiometric(onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Protected App")
            .setSubtitle("Verify with fingerprint or system lock")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
        runCatching { prompt.authenticate(info) }
    }

    override fun onDestroy() {
        TileDeckAccessibilityService.isLockScreenShowing = false
        super.onDestroy()
    }

    private fun unlockSuccess() {
        if (!isPreviewMode) {
            TileDeckAccessibilityService.unlockedSessionPkg = targetPkg
        }
        TileDeckAccessibilityService.isLockScreenShowing = false
        finish()
    }

    private fun exitToHome() {
        TileDeckAccessibilityService.isLockScreenShowing = false
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}

@Composable
private fun MainLockContent(
    palette: LockPalette,
    appName: String,
    appIcon: ImageBitmap?,
    showHud: Boolean,
    showClock: Boolean,
    showBatt: Boolean,
    battPct: Int,
    animRing: Boolean,
    oneHanded: Boolean,
    flatKeys: Boolean,
    stealthBlank: Boolean,
    invisibleDots: Boolean,
    useHaptics: Boolean,
    useBio: Boolean,
    patternMode: Boolean,
    hidePatternLine: Boolean,
    showOpenCount: Boolean,
    opensToday: Int,
    showHint: Boolean,
    pinHint: String,
    isPreviewMode: Boolean,
    pinLength: Int,
    entered: String,
    error: Boolean,
    failCount: Int,
    cooldownLeft: Int,
    mindfulLeft: Int,
    shakeOffset: Float,
    digits: List<String>,
    onSwitchMode: () -> Unit,
    onBioClick: () -> Unit,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onPatternComplete: (String) -> Unit
) {
    val nowTime = remember { SimpleDateFormat("HH:mm", Locale.US).format(Date()) }
    val infinite = rememberInfiniteTransition(label = "radar")
    val ringPulse by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ring_p"
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (oneHanded) Arrangement.Bottom else Arrangement.SpaceBetween
    ) {
        // Top Telemetry HUD
        if (showHud) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(if (error) Color(0xFFEF4444) else palette.accent))
                    Text(
                        if (isPreviewMode) "PREVIEW MODE" else "SHIELD ACTIVE",
                        fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = palette.muted
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (showClock) Text(nowTime, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = palette.muted)
                    if (showBatt) Text("$battPct%", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = palette.accent)
                }
            }
        } else {
            Spacer(Modifier.height(12.dp))
        }

        // Center App Identity + Animated Radar Ring
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
                if (animRing) {
                    Canvas(modifier = Modifier.fillMaxSize().scale(ringPulse)) {
                        drawCircle(
                            color = (if (error) Color(0xFFEF4444) else palette.accent).copy(alpha = 0.22f),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                        drawCircle(
                            color = (if (error) Color(0xFFEF4444) else palette.accent).copy(alpha = 0.55f),
                            radius = size.minDimension * 0.39f,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = palette.surface,
                    border = BorderStroke(1.dp, if (error) Color(0xFFEF4444) else palette.border),
                    modifier = Modifier.size(58.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (appIcon != null) {
                            Image(bitmap = appIcon, contentDescription = appName, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
                        } else {
                            Icon(Icons.Rounded.Lock, contentDescription = null, tint = palette.accent, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(appName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
            if (showOpenCount) {
                Text("Opened $opensToday times today", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = palette.muted)
            }
            if (showHint) {
                Text(pinHint, fontSize = 11.sp, color = palette.accent.copy(alpha = 0.8f))
            }

            Spacer(Modifier.height(14.dp))

            when {
                cooldownLeft > 0 -> {
                    Surface(shape = RoundedCornerShape(999.dp), color = Color(0x22EF4444), border = BorderStroke(1.dp, Color(0xFFEF4444))) {
                        Text("LOCKED OUT: ${cooldownLeft}s", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFFEF4444), modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                    }
                }
                mindfulLeft > 0 -> {
                    Surface(shape = RoundedCornerShape(999.dp), color = palette.surface, border = BorderStroke(1.dp, palette.accent)) {
                        Text("PAUSE & BREATHE: ${mindfulLeft}s", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = palette.accent, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                    }
                }
                !patternMode -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.offset(x = shakeOffset.dp)) {
                        repeat(pinLength) { idx ->
                            val filled = !invisibleDots && idx < entered.length
                            Box(
                                modifier = Modifier
                                    .size(if (filled) 13.dp else 11.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            error -> Color(0xFFEF4444)
                                            filled -> palette.accent
                                            else -> palette.border
                                        }
                                    )
                            )
                        }
                    }
                    if (failCount > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text("${(3 - failCount).coerceAtLeast(0)} attempts before cooldown", fontSize = 10.5.sp, fontFamily = FontFamily.Monospace, color = Color(0xFFEF4444))
                    }
                }
            }
        }

        // Interactive Keypad OR 3x3 Pattern Grid
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 12.dp)) {
            if (patternMode) {
                PatternGrid3x3(palette = palette, hideLine = hidePatternLine, onComplete = onPatternComplete)
            } else {
                val rows = listOf(
                    digits.subList(0, 3),
                    digits.subList(3, 6),
                    digits.subList(6, 9)
                )
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                        row.forEach { d ->
                            KeypadCircle(
                                label = if (stealthBlank) "•" else d,
                                palette = palette,
                                flat = flatKeys,
                                onClick = { onDigit(d) }
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    // Bottom Left: Biometric or Pattern switch
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .background(if (flatKeys) Color.Transparent else palette.surface)
                            .border(1.dp, if (flatKeys) Color.Transparent else palette.border, CircleShape)
                            .clickable { if (useBio) onBioClick() else onSwitchMode() }
                    ) {
                        Icon(
                            if (useBio) Icons.Rounded.Fingerprint else Icons.Rounded.Pattern,
                            contentDescription = "Alt Unlock",
                            tint = palette.accent,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    // Bottom Center: 10th digit (0 or shuffled)
                    KeypadCircle(
                        label = if (stealthBlank) "•" else digits[9],
                        palette = palette,
                        flat = flatKeys,
                        onClick = { onDigit(digits[9]) }
                    )
                    // Bottom Right: Backspace (tap) / Clear All (long press)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .background(if (flatKeys) Color.Transparent else palette.surface)
                            .border(1.dp, if (flatKeys) Color.Transparent else palette.border, CircleShape)
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { onBackspace() }, onLongPress = { onClear() })
                            }
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    if (patternMode) "Switch to PIN" else "Pattern Mode",
                    fontSize = 11.5.sp, fontFamily = FontFamily.Monospace, color = palette.muted,
                    modifier = Modifier.clickable { onSwitchMode() }.padding(6.dp)
                )
                if (useBio) {
                    Text(
                        "Fingerprint",
                        fontSize = 11.5.sp, fontFamily = FontFamily.Monospace, color = palette.accent,
                        modifier = Modifier.clickable { onBioClick() }.padding(6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadCircle(label: String, palette: LockPalette, flat: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(70.dp)
            .clip(CircleShape)
            .background(if (flat) Color.Transparent else palette.surface)
            .border(1.dp, if (flat) Color.Transparent else palette.border, CircleShape)
            .clickable { onClick() }
    ) {
        Text(label, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Color.White)
    }
}

@Composable
private fun PatternGrid3x3(palette: LockPalette, hideLine: Boolean, onComplete: (String) -> Unit) {
    val selected = remember { mutableStateListOf<Int>() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(220.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cell = size.width / 3f
                fun centerOf(idx: Int): Offset = Offset((idx % 3) * cell + cell / 2f, (idx / 3) * cell + cell / 2f)
                if (!hideLine && selected.size > 1) {
                    for (i in 0 until selected.size - 1) {
                        drawLine(palette.accent, centerOf(selected[i]), centerOf(selected[i + 1]), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
            }
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                for (r in 0..2) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        for (c in 0..2) {
                            val idx = r * 3 + c
                            val active = idx in selected
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        if (idx !in selected) selected.add(idx)
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(if (active) 18.dp else 12.dp)
                                        .clip(CircleShape)
                                        .background(if (active && !hideLine) palette.accent else palette.border)
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { selected.clear() }) { Text("Reset") }
            Button(onClick = {
                val seq = selected.joinToString("-")
                selected.clear()
                onComplete(seq)
            }, enabled = selected.size >= 3) { Text("Unlock") }
        }
    }
}

@Composable
private fun FakeCrashCover(appName: String, onReveal: () -> Unit, onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF09090B)), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1C1C1F),
            border = BorderStroke(1.dp, Color(0xFF2E2E33)),
            modifier = Modifier.fillMaxWidth().padding(32.dp)
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text("$appName keeps stopping", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(Modifier.height(14.dp))
                Text(
                    "App info",
                    fontSize = 14.sp, color = Color(0xFFA1A1AA),
                    modifier = Modifier.fillMaxWidth().clickable { onClose() }.padding(vertical = 10.dp)
                )
                Text(
                    "Close app",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF60A5FA),
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { onClose() }, onLongPress = { onReveal() })
                        }
                        .padding(vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun CalculatorDisguiseCover(correctPin: String, palette: LockPalette, onUnlock: () -> Unit, onReveal: () -> Unit) {
    var display by remember { mutableStateOf("0") }
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF09090B)).padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Calculator", fontSize = 14.sp, color = Color(0xFF71717A))
            Text("v2.1", fontSize = 11.sp, color = Color(0xFF3F3F46), modifier = Modifier.clickable { onReveal() })
        }
        Text(display, fontSize = 46.sp, fontWeight = FontWeight.Light, color = Color.White, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(listOf("7","8","9","/"), listOf("4","5","6","*"), listOf("1","2","3","-"), listOf("C","0","=","+")).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { k ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (k == "=") palette.accent else Color(0xFF18181B),
                            modifier = Modifier.weight(1f).height(62.dp).clickable {
                                when (k) {
                                    "C" -> display = "0"
                                    "=" -> {
                                        if (display == correctPin) onUnlock()
                                        else display = "0"
                                    }
                                    else -> display = if (display == "0") k else (display + k).take(12)
                                }
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(k, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = if (k == "=") Color.Black else Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenericDecoyCover(title: String, body: String, buttonText: String, onReveal: () -> Unit, onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF09090B)).padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(body, fontSize = 13.sp, color = Color(0xFF71717A), textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier.pointerInput(Unit) { detectTapGestures(onTap = { onClose() }, onLongPress = { onReveal() }) }
            ) { Text(buttonText) }
        }
    }
}
