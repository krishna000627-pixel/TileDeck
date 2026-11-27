package com.krishna.tiledeck.ui

import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.krishna.tiledeck.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

fun categoryIcon(category: String): ImageVector = when (category) {
    "Moto & Private Space" -> Icons.Rounded.Security
    "Display & Screen" -> Icons.Rounded.Brightness6
    "Audio & Media" -> Icons.Rounded.GraphicEq
    "Accessibility & Nav" -> Icons.Rounded.TouchApp
    "Focus & Capture" -> Icons.Rounded.Timer
    "System & Hardware" -> Icons.Rounded.Memory
    "Root & Automation" -> Icons.Rounded.Terminal
    else -> Icons.Rounded.Dashboard
}

fun tileIcon(tileId: String, category: String): ImageVector = when (tileId) {
    "moto_secure_open", "moto_secure_toggle" -> Icons.Rounded.EnhancedEncryption
    "hidden_tray_toggle" -> Icons.Rounded.VisibilityOff
    "app_lock_toggle", "lock_current_app" -> Icons.Rounded.Lock
    "moto_keep_alive" -> Icons.Rounded.Bolt
    "clear_all_recents", "kill_foreground" -> Icons.Rounded.CleaningServices
    "restart_systemui", "reboot_menu" -> Icons.Rounded.RestartAlt
    "caffeine" -> Icons.Rounded.LocalCafe
    "blackout_pocket" -> Icons.Rounded.Bedtime
    "sound_profile", "mute_except_alarms" -> Icons.Rounded.VolumeUp
    "pomodoro", "stopwatch", "quick_countdown" -> Icons.Rounded.AvTimer
    "quick_note" -> Icons.Rounded.EditNote
    "quick_calc" -> Icons.Rounded.Calculate
    "battery_stats", "charge_limit" -> Icons.Rounded.BatteryChargingFull
    "torch_toggle", "sos_strobe" -> Icons.Rounded.FlashlightOn
    "private_dns" -> Icons.Rounded.Dns
    "adb_wireless" -> Icons.Rounded.WifiTethering
    else -> categoryIcon(category)
}

@Composable
fun LivingDeckBackground(darkTheme: Boolean, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "deck_bg")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bg_phase"
    )

    val base = if (darkTheme) Color(0xFF070A10) else Color(0xFFF2F6FB)
    val orb1 = if (darkTheme) Color(0x332DD4BF) else Color(0x220F766E)
    val orb2 = if (darkTheme) Color(0x2B38BDF8) else Color(0x1E0284C7)
    val gridColor = if (darkTheme) Color(0x12FFFFFF) else Color(0x120F172A)

    Canvas(modifier = modifier.fillMaxSize().background(base)) {
        val w = size.width
        val h = size.height

        val c1 = Offset(w * (0.22f + 0.14f * cos(phase)), h * (0.16f + 0.08f * sin(phase)))
        val c2 = Offset(w * (0.78f + 0.12f * sin(phase)), h * (0.34f + 0.10f * cos(phase)))

        drawCircle(
            brush = Brush.radialGradient(listOf(orb1, Color.Transparent), center = c1, radius = w * 0.72f),
            radius = w * 0.72f,
            center = c1
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(orb2, Color.Transparent), center = c2, radius = w * 0.65f),
            radius = w * 0.65f,
            center = c2
        )

        val step = 52f
        var x = step
        while (x < w) {
            var y = step
            while (y < h) {
                drawCircle(color = gridColor, radius = 1.4f, center = Offset(x, y))
                y += step
            }
            x += step
        }
    }
}

@Composable
fun AmbientHeroVideo(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            VideoView(ctx).apply {
                val uri = Uri.parse("android.resource://${ctx.packageName}/${R.raw.deck_ambient_loop}")
                setVideoURI(uri)
                setOnPreparedListener { mp ->
                    mp.isLooping = true
                    mp.setVolume(0f, 0f)
                    start()
                }
                setOnErrorListener { _, _, _ -> true }
            }
        },
        update = { view ->
            if (!view.isPlaying) runCatching { view.start() }
        }
    )
}

@Composable
fun AnimatedRingGauge(
    progress: Float,
    label: String,
    valueText: String,
    accent: Color,
    size: Dp = 62.dp
) {
    val animProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "ring_progress"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 5.dp.toPx()
                drawArc(
                    color = accent.copy(alpha = 0.18f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * animProgress,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
            Text(valueText, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.72f))
    }
}

@Composable
fun PulsingDot(color: Color, active: Boolean) {
    val infinite = rememberInfiniteTransition(label = "pulse_dot")
    val alpha by infinite.animateFloat(
        initialValue = if (active) 0.35f else 0.4f,
        targetValue = if (active) 1f else 0.4f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "dot_alpha"
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}
