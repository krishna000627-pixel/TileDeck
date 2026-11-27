package com.krishna.tiledeck.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishna.tiledeck.accessibility.TileDeckAccessibilityService
import com.krishna.tiledeck.data.TilePrefs
import kotlinx.coroutines.launch

class AppLockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val targetPkg = intent.getStringExtra("target_pkg") ?: ""
        val correctPin = TilePrefs.getAppLockPin(this)

        setContent {
            var entered by remember { mutableStateOf("") }
            var error by remember { mutableStateOf(false) }
            val shake = remember { Animatable(0f) }
            val scope = rememberCoroutineScope()

            BackHandler {
                startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                finish()
            }

            fun wrong() {
                error = true
                entered = ""
                scope.launch { listOf(-16f, 16f, -10f, 10f, 0f).forEach { shake.animateTo(it, tween(40)) } }
            }

            MaterialTheme(colorScheme = darkColorScheme()) {
                Column(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF09090B)).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("LOCKED", fontSize = 11.sp, letterSpacing = 3.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF10B981))
                    Spacer(Modifier.height(6.dp))
                    Text("Enter PIN", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(targetPkg, fontSize = 11.sp, color = Color(0xFF71717A))
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.offset(x = shake.value.dp)) {
                        repeat(correctPin.length.coerceAtLeast(4)) { i ->
                            Box(
                                modifier = Modifier.size(12.dp).clip(CircleShape).background(
                                    when {
                                        error -> Color(0xFFEF4444)
                                        i < entered.length -> Color(0xFF10B981)
                                        else -> Color(0xFF27272A)
                                    }
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    listOf(listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9"), listOf("C","0","OK")).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 7.dp)) {
                            row.forEach { k ->
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(68.dp).clip(CircleShape).background(Color(0xFF141417)).border(1.dp, Color(0xFF27272A), CircleShape).clickable {
                                        error = false
                                        when (k) {
                                            "C" -> entered = ""
                                            "OK" -> if (entered == correctPin) { TileDeckAccessibilityService.unlockedSessionPkg = targetPkg; finish() } else wrong()
                                            else -> if (entered.length < 8) {
                                                entered += k
                                                if (entered == correctPin) { TileDeckAccessibilityService.unlockedSessionPkg = targetPkg; finish() }
                                                else if (entered.length >= correctPin.length) wrong()
                                            }
                                        }
                                    }
                                ) {
                                    Text(k, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
