package com.krishna.tiledeck.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishna.tiledeck.accessibility.TileDeckAccessibilityService
import com.krishna.tiledeck.data.TilePrefs

class AppLockOverlayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val targetPkg = intent.getStringExtra("target_pkg") ?: ""
        val correctPin = TilePrefs.getAppLockPin(this)

        setContent {
            var entered by remember { mutableStateOf("") }
            var error by remember { mutableStateOf(false) }

            BackHandler {
                val home = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(home)
                finish()
            }

            MaterialTheme(colorScheme = darkColorScheme()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0A0D13))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("TileDeck App Lock", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(6.dp))
                    Text(targetPkg, fontSize = 12.sp, color = Color(0xFF8B9AB0))
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = "*".repeat(entered.length).ifEmpty { "Enter PIN" },
                        fontSize = 24.sp,
                        letterSpacing = 6.sp,
                        color = if (error) Color(0xFFF87171) else Color(0xFF2DD4BF)
                    )
                    Spacer(Modifier.height(24.dp))

                    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("CLR", "0", "OK"))
                    rows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                            row.forEach { key ->
                                Button(
                                    onClick = {
                                        error = false
                                        when (key) {
                                            "CLR" -> entered = ""
                                            "OK" -> {
                                                if (entered == correctPin) {
                                                    TileDeckAccessibilityService.unlockedSessionPkg = targetPkg
                                                    finish()
                                                } else {
                                                    error = true
                                                    entered = ""
                                                }
                                            }
                                            else -> if (entered.length < 8) {
                                                entered += key
                                                if (entered == correctPin) {
                                                    TileDeckAccessibilityService.unlockedSessionPkg = targetPkg
                                                    finish()
                                                }
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.size(width = 76.dp, height = 54.dp)
                                ) {
                                    Text(key, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
