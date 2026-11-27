package com.krishna.tiledeck.tiles

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.KeyEvent
import android.widget.Toast
import com.krishna.tiledeck.accessibility.TileDeckAccessibilityService
import com.krishna.tiledeck.data.TileCatalog
import com.krishna.tiledeck.data.TilePrefs
import com.krishna.tiledeck.system.MotoTools
import com.krishna.tiledeck.ui.QuickActionDialogActivity
import java.security.SecureRandom
import java.util.Locale

data class TileExecutionResult(
    val active: Boolean,
    val subtitle: String
)

object TileActionEngine {

    private var wakeLock: PowerManager.WakeLock? = null
    private var tts: TextToSpeech? = null

    fun execute(context: Context, tileId: String): TileExecutionResult {
        val spec = TileCatalog.findById(tileId)
            ?: return TileExecutionResult(false, "Unknown")

        val currentlyActive = TilePrefs.isTileActive(context, tileId)
        val result = when (tileId) {
            // Moto & Private Space
            "moto_secure_open" -> MotoTools.openMotoSecureFolder(context).let { TileExecutionResult(it.first, it.second) }
            "moto_secure_toggle" -> MotoTools.toggleMotoSecureVisibility(context).let { TileExecutionResult(it.first, it.second) }
            "hidden_tray_toggle" -> MotoTools.toggleHiddenAppTray(context).let { TileExecutionResult(it.first, it.second) }
            "app_lock_toggle" -> toggleAppLock(context, !TilePrefs.isAppLockArmed(context))
            "lock_current_app" -> lockCurrentApp(context)
            "switch_user_profile" -> MotoTools.switchUserProfile(context).let { TileExecutionResult(it.first, it.second) }
            "moto_keep_alive" -> MotoTools.applyMotoKeepAlive(context).let { TileExecutionResult(it.first, it.second) }
            "clear_all_recents" -> runA11y(context) { it.clearAllRecents() to "Cleared" }
            "kill_foreground" -> killForegroundApp()
            "restart_systemui" -> MotoTools.runRoot("pkill -f com.android.systemui").let { TileExecutionResult(it.first, if (it.first) "Restarted" else it.second.take(16)) }

            // Direct Display & Screen
            "caffeine" -> cycleCaffeine(context)
            "adaptive_brightness" -> toggleAutoBrightness(context)
            "brightness_preset" -> cycleBrightness(context)
            "extra_dim" -> launchQuickDialog(context, "extra_dim", if (!currentlyActive) "Dimmed" else "Off")
            "blackout_pocket" -> launchQuickDialog(context, "blackout_pocket", "Blackout")
            "reading_grayscale" -> toggleGrayscale(!currentlyActive)
            "screen_timeout" -> cycleScreenTimeout(context)
            "rotation_control" -> cycleRotation(context)
            "warm_filter" -> launchQuickDialog(context, "warm_filter", if (!currentlyActive) "Amber On" else "Off")
            "refresh_rate" -> toggleRefreshRate(!currentlyActive)

            // Audio & Media
            "sound_profile" -> cycleSoundProfile(context)
            "media_volume" -> cycleMediaVolume(context)
            "mute_except_alarms" -> muteExceptAlarms(context)
            "media_play_pause" -> sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "Play/Pause")
            "media_next" -> sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT, "Skipped")
            "sleep_timer" -> cyclePreset(context, "sleep_timer", listOf("Off", "15m", "30m", "60m"))
            "moto_audio_reset" -> resetMotoAudio(context)
            "white_noise" -> TileExecutionResult(!currentlyActive, if (!currentlyActive) "Noise On" else "Off")
            "speak_clipboard" -> speakClipboard(context)

            // Accessibility & Navigation
            "lock_screen" -> runA11y(context) { it.lockScreenNow() to "Locked" }
            "take_screenshot" -> runA11y(context) { it.takeScreenshotNow() to "Captured" }
            "split_screen" -> runA11y(context) { it.toggleSplitScreenNow() to "Split" }
            "last_app" -> runA11y(context) { it.switchToLastApp() to "Switched" }
            "power_dialog" -> runA11y(context) { it.openPowerDialogNow() to "Power" }
            "custom_app_1" -> launchAppSlot(context, 1)
            "custom_app_2" -> launchAppSlot(context, 2)
            "custom_app_3" -> launchAppSlot(context, 3)

            // Focus & Quick Capture
            "pomodoro" -> TileExecutionResult(!currentlyActive, if (!currentlyActive) "25:00 Focus" else "Paused")
            "stopwatch" -> TileExecutionResult(!currentlyActive, if (!currentlyActive) "Running" else "Stopped")
            "quick_countdown" -> cyclePreset(context, "quick_countdown", listOf("Off", "3m", "5m", "10m", "15m"))
            "habit_counter" -> incrementHabit(context)
            "study_mode" -> toggleStudyMode(context, !currentlyActive)
            "quick_note" -> launchQuickDialog(context, "quick_note", "Note Box")
            "clipboard_vault" -> launchQuickDialog(context, "clipboard_vault", "Vault")
            "clear_clipboard" -> clearClipboard(context)
            "quick_calc" -> launchQuickDialog(context, "quick_calc", "Calc")

            // Live System & Hardware
            "battery_stats" -> readBatteryStats(context)
            "ram_monitor" -> readRamStats(context)
            "storage_free" -> readStorageStats(context)
            "thermal_status" -> readThermalStatus(context)
            "wifi_ip" -> readWifiIp(context)
            "haptic_toggle" -> toggleHaptics(context)
            "torch_toggle" -> toggleTorch(context, !currentlyActive)
            "sos_strobe" -> toggleTorch(context, !currentlyActive)
            "pass_gen" -> generatePassword(context)

            // Root & Automation
            "private_dns" -> togglePrivateDns(!currentlyActive)
            "adb_wireless" -> toggleWirelessAdb(!currentlyActive)
            "anim_scale" -> cycleAnimScale(context)
            "show_touches" -> rootSettingToggle("system", "show_touches", !currentlyActive)
            "layout_bounds" -> toggleLayoutBounds(!currentlyActive)
            "charge_limit" -> toggleChargeLimit(!currentlyActive)
            "immersive_mode" -> toggleImmersive(!currentlyActive)
            "clear_cache" -> MotoTools.runRoot("pm trim-caches 1024G").let { TileExecutionResult(it.first, if (it.first) "Trimmed" else "No Root") }
            "reboot_menu" -> launchQuickDialog(context, "reboot_menu", "Power")
            "custom_shell" -> MotoTools.runRoot("uname -r").let { TileExecutionResult(it.first, it.second.take(20)) }
            else -> TileExecutionResult(!currentlyActive, if (!currentlyActive) "Active" else "Off")
        }

        TilePrefs.setTileActive(context, tileId, result.active)
        TilePrefs.setSubtitle(context, tileId, result.subtitle)
        return result
    }

    private fun runA11y(context: Context, block: (TileDeckAccessibilityService) -> Pair<Boolean, String>): TileExecutionResult {
        val svc = TileDeckAccessibilityService.instance
        if (svc == null) {
            Toast.makeText(context, "Enable TileDeck Accessibility in Permissions tab", Toast.LENGTH_SHORT).show()
            return TileExecutionResult(false, "Need A11y")
        }
        val (ok, sub) = block(svc)
        return TileExecutionResult(ok, if (ok) sub else "Failed")
    }

    private fun toggleAppLock(context: Context, armed: Boolean): TileExecutionResult {
        if (armed && TileDeckAccessibilityService.instance == null) {
            Toast.makeText(context, "Enable Accessibility for App Lock", Toast.LENGTH_SHORT).show()
            return TileExecutionResult(false, "Need A11y")
        }
        TilePrefs.setAppLockArmed(context, armed)
        val count = TilePrefs.getLockedPackages(context).size
        return TileExecutionResult(armed, if (armed) "Armed ($count)" else "Off")
    }

    private fun lockCurrentApp(context: Context): TileExecutionResult {
        val pkg = TileDeckAccessibilityService.lastForegroundPackage
            ?: return TileExecutionResult(false, "No App")
        TilePrefs.setPackageLocked(context, pkg, true)
        TilePrefs.setAppLockArmed(context, true)
        TileDeckAccessibilityService.unlockedSessionPkg = null
        return TileExecutionResult(true, "Locked")
    }

    private fun killForegroundApp(): TileExecutionResult {
        val pkg = TileDeckAccessibilityService.lastForegroundPackage
            ?: return TileExecutionResult(false, "No Target")
        val (ok, _) = MotoTools.runRoot("am force-stop $pkg")
        return TileExecutionResult(ok, if (ok) "Killed" else "No Root")
    }

    private fun toggleAutoBrightness(context: Context): TileExecutionResult {
        if (!Settings.System.canWrite(context)) return TileExecutionResult(false, "Need Write")
        val cr = context.contentResolver
        val cur = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
        val next = if (cur == 1) 0 else 1
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, next)
        return TileExecutionResult(next == 1, if (next == 1) "Auto" else "Manual")
    }

    private fun cycleBrightness(context: Context): TileExecutionResult {
        if (!Settings.System.canWrite(context)) return TileExecutionResult(false, "Need Write")
        val steps = listOf(26 to "10%", 102 to "40%", 191 to "75%", 255 to "100%")
        val idx = (TilePrefs.getCounter(context, "brightness_preset") + 1) % steps.size
        TilePrefs.setCounter(context, "brightness_preset", idx)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, steps[idx].first)
        return TileExecutionResult(true, steps[idx].second)
    }

    private fun cycleScreenTimeout(context: Context): TileExecutionResult {
        if (!Settings.System.canWrite(context)) return TileExecutionResult(false, "Need Write")
        val steps = listOf(15_000 to "15s", 60_000 to "1m", 300_000 to "5m", 1_800_000 to "30m")
        val idx = (TilePrefs.getCounter(context, "screen_timeout") + 1) % steps.size
        TilePrefs.setCounter(context, "screen_timeout", idx)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, steps[idx].first)
        return TileExecutionResult(true, steps[idx].second)
    }

    private fun cycleRotation(context: Context): TileExecutionResult {
        if (!Settings.System.canWrite(context)) return TileExecutionResult(false, "Need Write")
        val idx = (TilePrefs.getCounter(context, "rotation_control") + 1) % 4
        TilePrefs.setCounter(context, "rotation_control", idx)
        val cr = context.contentResolver
        return when (idx) {
            0 -> { Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, 1); TileExecutionResult(true, "Auto") }
            1 -> { Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0); Settings.System.putInt(cr, Settings.System.USER_ROTATION, 0); TileExecutionResult(false, "Portrait") }
            2 -> { Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0); Settings.System.putInt(cr, Settings.System.USER_ROTATION, 1); TileExecutionResult(true, "Landscape") }
            else -> { Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0); Settings.System.putInt(cr, Settings.System.USER_ROTATION, 2); TileExecutionResult(true, "Reverse") }
        }
    }

    private fun toggleHaptics(context: Context): TileExecutionResult {
        if (!Settings.System.canWrite(context)) return TileExecutionResult(false, "Need Write")
        val cur = Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1)
        val next = if (cur == 1) 0 else 1
        Settings.System.putInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, next)
        return TileExecutionResult(next == 1, if (next == 1) "On" else "Off")
    }

    private fun toggleGrayscale(enable: Boolean): TileExecutionResult {
        val cmd = if (enable) {
            "settings put secure accessibility_display_daltonizer_enabled 1; settings put secure accessibility_display_daltonizer 0"
        } else {
            "settings put secure accessibility_display_daltonizer_enabled 0"
        }
        val (ok, _) = MotoTools.runRoot(cmd)
        return TileExecutionResult(ok && enable, if (!ok) "No Root" else if (enable) "Mono" else "Color")
    }

    private fun toggleRefreshRate(high: Boolean): TileExecutionResult {
        val hz = if (high) "120.0" else "60.0"
        val (ok, _) = MotoTools.runRoot("settings put system peak_refresh_rate $hz; settings put system min_refresh_rate $hz")
        return TileExecutionResult(ok && high, if (!ok) "No Root" else if (high) "120Hz" else "60Hz")
    }

    private fun togglePrivateDns(adguard: Boolean): TileExecutionResult {
        val cmd = if (adguard) {
            "settings put global private_dns_mode hostname; settings put global private_dns_specifier dns.adguard-dns.com"
        } else {
            "settings put global private_dns_mode opportunistic"
        }
        val (ok, _) = MotoTools.runRoot(cmd)
        return TileExecutionResult(ok && adguard, if (!ok) "No Root" else if (adguard) "AdGuard" else "Auto")
    }

    private fun toggleWirelessAdb(enable: Boolean): TileExecutionResult {
        val v = if (enable) "1" else "0"
        val (ok, _) = MotoTools.runRoot("settings put global adb_wifi_enabled $v; setprop service.adb.tcp.port ${if (enable) "5555" else "-1"}")
        return TileExecutionResult(ok && enable, if (!ok) "No Root" else if (enable) "Port 5555" else "Off")
    }

    private fun cycleAnimScale(context: Context): TileExecutionResult {
        val scales = listOf("0.0" to "0x", "0.5" to "0.5x", "1.0" to "1x")
        val idx = (TilePrefs.getCounter(context, "anim_scale") + 1) % scales.size
        TilePrefs.setCounter(context, "anim_scale", idx)
        val s = scales[idx].first
        val (ok, _) = MotoTools.runRoot("settings put global window_animation_scale $s; settings put global transition_animation_scale $s; settings put global animator_duration_scale $s")
        return TileExecutionResult(ok, if (ok) scales[idx].second else "No Root")
    }

    private fun rootSettingToggle(ns: String, key: String, enable: Boolean): TileExecutionResult {
        val (ok, _) = MotoTools.runRoot("settings put $ns $key ${if (enable) 1 else 0}")
        return TileExecutionResult(ok && enable, if (!ok) "No Root" else if (enable) "On" else "Off")
    }

    private fun toggleLayoutBounds(enable: Boolean): TileExecutionResult {
        val (ok, _) = MotoTools.runRoot("setprop debug.layout ${if (enable) "true" else "false"}; service call activity 1599295570")
        return TileExecutionResult(ok && enable, if (!ok) "No Root" else if (enable) "Bounds On" else "Off")
    }

    private fun toggleChargeLimit(enable: Boolean): TileExecutionResult {
        val v = if (enable) "0" else "1"
        val (ok, _) = MotoTools.runRoot("echo $v > /sys/class/power_supply/battery/charging_enabled 2>/dev/null || echo ${if (enable) 1 else 0} > /sys/class/power_supply/battery/input_suspend")
        return TileExecutionResult(ok && enable, if (!ok) "Unsupported" else if (enable) "80% Cap" else "Normal")
    }

    private fun toggleImmersive(enable: Boolean): TileExecutionResult {
        val valStr = if (enable) "immersive.full=*" else "null"
        val (ok, _) = MotoTools.runRoot("settings put global policy_control $valStr")
        return TileExecutionResult(ok && enable, if (!ok) "No Root" else if (enable) "Full" else "Normal")
    }

    private fun resetMotoAudio(context: Context): TileExecutionResult {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.mode = AudioManager.MODE_NORMAL
        am.isSpeakerphoneOn = false
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, 0)
        return TileExecutionResult(true, "Audio Reset")
    }

    private fun speakClipboard(context: Context): TileExecutionResult {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
        if (text.isNullOrBlank()) return TileExecutionResult(false, "Empty")
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.speak(text.take(400), TextToSpeech.QUEUE_FLUSH, null, "tiledeck_tts")
            }
        }
        return TileExecutionResult(true, "Speaking")
    }

    private fun launchAppSlot(context: Context, slot: Int): TileExecutionResult {
        val pkg = TilePrefs.getCustomAppSlot(context, slot)
        if (pkg.isBlank()) return TileExecutionResult(false, "Set in Hub")
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: return TileExecutionResult(false, "Missing")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return TileExecutionResult(true, "Launched")
    }

    private fun toggleStudyMode(context: Context, on: Boolean): TileExecutionResult {
        TilePrefs.setAppLockArmed(context, on)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(
                if (on) NotificationManager.INTERRUPTION_FILTER_ALARMS
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
        }
        return TileExecutionResult(on, if (on) "Study Active" else "Off")
    }

    @Suppress("DEPRECATION")
    private fun cycleCaffeine(context: Context): TileExecutionResult {
        val steps = listOf("Off", "5m", "15m", "30m", "Inf")
        val nextIdx = (TilePrefs.getCounter(context, "caffeine") + 1) % steps.size
        TilePrefs.setCounter(context, "caffeine", nextIdx)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        if (nextIdx == 0) return TileExecutionResult(false, "Off")
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val timeoutMs = when (nextIdx) {
            1 -> 5 * 60_000L
            2 -> 15 * 60_000L
            3 -> 30 * 60_000L
            else -> 6 * 60 * 60_000L
        }
        wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE, "TileDeck:Caffeine")
        wakeLock?.acquire(timeoutMs)
        return TileExecutionResult(true, steps[nextIdx])
    }

    private fun cycleSoundProfile(context: Context): TileExecutionResult {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val modes = listOf(
            AudioManager.RINGER_MODE_NORMAL to "Normal",
            AudioManager.RINGER_MODE_VIBRATE to "Vibrate",
            AudioManager.RINGER_MODE_SILENT to "Silent"
        )
        val idx = (TilePrefs.getCounter(context, "sound_profile") + 1) % modes.size
        TilePrefs.setCounter(context, "sound_profile", idx)
        runCatching { am.ringerMode = modes[idx].first }
        return TileExecutionResult(idx != 0, modes[idx].second)
    }

    private fun cycleMediaVolume(context: Context): TileExecutionResult {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val percents = listOf(0, 30, 70, 100)
        val idx = (TilePrefs.getCounter(context, "media_volume") + 1) % percents.size
        TilePrefs.setCounter(context, "media_volume", idx)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, (max * percents[idx]) / 100, 0)
        return TileExecutionResult(percents[idx] > 0, "${percents[idx]}%")
    }

    private fun muteExceptAlarms(context: Context): TileExecutionResult {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        runCatching {
            am.setStreamVolume(AudioManager.STREAM_RING, 0, 0)
            am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
        }
        return TileExecutionResult(true, "Muted")
    }

    private fun sendMediaKey(context: Context, keyCode: Int, label: String): TileExecutionResult {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        return TileExecutionResult(true, label)
    }

    private fun cyclePreset(context: Context, key: String, list: List<String>): TileExecutionResult {
        val idx = (TilePrefs.getCounter(context, key) + 1) % list.size
        TilePrefs.setCounter(context, key, idx)
        return TileExecutionResult(idx != 0, list[idx])
    }

    private fun incrementHabit(context: Context): TileExecutionResult {
        val count = TilePrefs.getCounter(context, "habit_counter") + 1
        TilePrefs.setCounter(context, "habit_counter", count)
        return TileExecutionResult(true, "Count: $count")
    }

    private fun launchQuickDialog(context: Context, mode: String, sub: String): TileExecutionResult {
        val intent = Intent(context, QuickActionDialogActivity::class.java).apply {
            putExtra("mode", mode)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return TileExecutionResult(true, sub)
    }

    private fun clearClipboard(context: Context): TileExecutionResult {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) cm.clearPrimaryClip()
        else cm.setPrimaryClip(ClipData.newPlainText("", ""))
        return TileExecutionResult(false, "Cleared")
    }

    private fun generatePassword(context: Context): TileExecutionResult {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#\$%^&*"
        val rng = SecureRandom()
        val pw = (1..16).map { chars[rng.nextInt(chars.length)] }.joinToString("")
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("TileDeck Password", pw))
        return TileExecutionResult(true, "Copied")
    }

    private fun readBatteryStats(context: Context): TileExecutionResult {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val tempC = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val ma = kotlin.math.abs(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) / 1000)
        return TileExecutionResult(true, "${tempC}C / ${ma}mA")
    }

    private fun readRamStats(context: Context): TileExecutionResult {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return TileExecutionResult(true, String.format("%.1f GB free", mi.availMem / 1_073_741_824.0))
    }

    private fun readStorageStats(context: Context): TileExecutionResult {
        val stat = StatFs(Environment.getDataDirectory().path)
        return TileExecutionResult(true, String.format("%.1f GB free", stat.availableBytes / 1_073_741_824.0))
    }

    private fun readThermalStatus(context: Context): TileExecutionResult {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val label = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (pm.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "Normal"
                PowerManager.THERMAL_STATUS_LIGHT -> "Warm"
                PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
                else -> "Hot"
            }
        } else "Normal"
        return TileExecutionResult(label != "Normal", label)
    }

    @Suppress("DEPRECATION")
    private fun readWifiIp(context: Context): TileExecutionResult {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipInt = wm.connectionInfo.ipAddress
        val ip = String.format("%d.%d.%d.%d", ipInt and 0xff, ipInt shr 8 and 0xff, ipInt shr 16 and 0xff, ipInt shr 24 and 0xff)
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("IP", ip))
        return TileExecutionResult(ip != "0.0.0.0", ip)
    }

    private fun toggleTorch(context: Context, on: Boolean): TileExecutionResult {
        runCatching {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cm.setTorchMode(cm.cameraIdList.first(), on)
        }
        return TileExecutionResult(on, if (on) "On" else "Off")
    }
}
