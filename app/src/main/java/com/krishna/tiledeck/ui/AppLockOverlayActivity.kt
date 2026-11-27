package com.krishna.tiledeck.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishna.tiledeck.accessibility.TileDeckAccessibilityService
import com.krishna.tiledeck.data.TilePrefs
import kotlinx.coroutines.launch

class AppLockOverlayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val targetPkg = intent.getStringExtra("target_pkg") ?: ""
        val correctPin = TilePrefs.getAppLockPin(this)

        setContent {
            var entered by remember { mutableStateOf("") }
            var error by remember { mutableStateOf(false) }
            val shake = remember { Animatable(0f) }
            val scope = rememberCoroutineScope()

            val infinite = rememberInfiniteTransition(label = "shield_pulse")
            val pulseScale by infinite.animateFloat(
                initialValue = 0.94f,
                targetValue = 1.06f,
                animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "shield_scale"
            )

            BackHandler {
                val home = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(home)
                finish()
            }

            fun triggerWrongPin() {
                error = true
                entered = ""
                scope.launch {
                    listOf(-18f, 18f, -12f, 12f, -6f, 0f).forEach {
                        shake.animateTo(it, tween(45))
                    }
                }
            }

            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    LivingDeckBackground(darkTheme = true)
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(76.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(Color(0x222DD4BF))
                                .border(1.5.dp, Color(0xFF2DD4BF), CircleShape)
                        ) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, tint = Color(0xFF2DD4BF), modifier = Modifier.size(34.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("TileDeck App Lock", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Text(targetPkg, fontSize = 12.sp, color = Color(0xFF94A3B8))
                        Spacer(Modifier.height(22.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.offset(x = shake.value.dp)
                        ) {
                            repeat(correctPin.length.coerceAtLeast(4)) { idx ->
                                val filled = idx < entered.length
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                error -> Color(0xFFF87171)
                                                filled -> Color(0xFF2DD4BF)
                                                else -> Color(0x33FFFFFF)
                                            }
                                        )
                                )
                            }
                        }
                        Spacer(Modifier.height(28.dp))

                        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("CLR", "0", "OK"))
                        rows.forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 7.dp)) {
                                row.forEach { key ->
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(68.dp)
                                            .clip(CircleShape)
                                            .background(Color(0x22FFFFFF))
                                            .border(1.dp, Color(0x33FFFFFF), CircleShape)
                                            .clickable {
                                                error = false
                                                when (key) {
                                                    "CLR" -> entered = ""
                                                    "OK" -> if (entered == correctPin) {
                                                        TileDeckAccessibilityService.unlockedSessionPkg = targetPkg
                                                        finish()
                                                    } else triggerWrongPin()
                                                    else -> if (entered.length < 8) {
                                                        entered += key
                                                        if (entered == correctPin) {
                                                            TileDeckAccessibilityService.unlockedSessionPkg = targetPkg
                                                            finish()
                                                        } else if (entered.length >= correctPin.length) {
                                                            triggerWrongPin()
                                                        }
                                                    }
                                                }
                                            }
                                    ) {
                                        Text(key, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
