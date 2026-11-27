package com.krishna.tiledeck.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krishna.tiledeck.data.TilePrefs
import com.krishna.tiledeck.system.MotoTools

class QuickActionDialogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra("mode") ?: "quick_note"
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                when (mode) {
                    "blackout_pocket" -> BlackoutOverlay { finish() }
                    "extra_dim" -> TintOverlay(Color(0xB3000000), "Tap to close Extra Dim") { finish() }
                    "warm_filter" -> TintOverlay(Color(0x55FF8C00), "Tap to close Amber Filter") { finish() }
                    "quick_calc" -> QuickCalcDialog { finish() }
                    "reboot_menu" -> RebootMenuDialog { finish() }
                    else -> QuickNoteDialog(
                        onSave = { note ->
                            if (note.isNotBlank()) TilePrefs.addQuickNote(this, note)
                            finish()
                        },
                        onClose = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
private fun BlackoutOverlay(onDismiss: () -> Unit) {
    var taps by remember { mutableIntStateOf(0) }
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black).clickable {
            taps++
            if (taps >= 3) onDismiss()
        },
        contentAlignment = Alignment.BottomCenter
    ) {
        Text("Tap 3 times to unlock screen", color = Color(0xFF333333), modifier = Modifier.padding(bottom = 32.dp))
    }
}

@Composable
private fun TintOverlay(tint: Color, label: String, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(tint).clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(label, color = Color(0xAAFFFFFF), modifier = Modifier.padding(bottom = 32.dp))
    }
}

@Composable
private fun QuickNoteDialog(onSave: (String) -> Unit, onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF14171D), modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Quick Note", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(value = text, onValueChange = { text = it }, placeholder = { Text("Capture a quick note...") }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClose) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onSave(text) }) { Text("Save") }
            }
        }
    }
}

@Composable
private fun QuickCalcDialog(onClose: () -> Unit) {
    var expr by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("0") }
    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF14171D), modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Floating Calculator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(value = expr, onValueChange = { expr = it; result = evalSimple(it) }, placeholder = { Text("e.g. 240 + 85") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("Result: $result", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF2DD4BF))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClose) { Text("Close") }
            }
        }
    }
}

private fun evalSimple(input: String): String {
    val clean = input.replace(" ", "")
    val match = Regex("""^(-?\d+(?:\.\d+)?)([+\-*/])(-?\d+(?:\.\d+)?)$""").find(clean) ?: return "..."
    val a = match.groupValues[1].toDoubleOrNull() ?: return "..."
    val b = match.groupValues[3].toDoubleOrNull() ?: return "..."
    val v = when (match.groupValues[2]) {
        "+" -> a + b
        "-" -> a - b
        "*" -> a * b
        "/" -> if (b != 0.0) a / b else Double.NaN
        else -> Double.NaN
    }
    return if (v.isNaN()) "Err" else if (v % 1.0 == 0.0) v.toLong().toString() else String.format("%.3f", v)
}

@Composable
private fun RebootMenuDialog(onClose: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF14171D), modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Root Reboot Menu", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            listOf(
                "Reboot" to "reboot",
                "Recovery" to "reboot recovery",
                "Bootloader" to "reboot bootloader",
                "Restart SystemUI" to "pkill -f com.android.systemui"
            ).forEach { (label, cmd) ->
                OutlinedButton(
                    onClick = { MotoTools.runRoot(cmd); onClose() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(label) }
            }
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) { Text("Cancel") }
        }
    }
}
