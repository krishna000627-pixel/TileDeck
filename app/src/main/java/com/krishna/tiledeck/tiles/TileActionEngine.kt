package com.krishna.tiledeck.tiles

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.UserManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.KeyEvent
import com.krishna.tiledeck.accessibility.TileDeckAccessibilityService
import com.krishna.tiledeck.data.TileCatalog
import com.krishna.tiledeck.data.TilePrefs
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.random.Random

data class TileExecutionResult(
    val active: Boolean,
    val subtitle: String,
    val ok: Boolean = true,
    val diagnostic: String = "OK",
    val launchIntent: Intent? = null
)

object TileActionEngine {

    private var wakeLock: PowerManager.WakeLock? = null
    private var tts: TextToSpeech? = null
    private val motoSecurePackages = listOf(
        "com.motorola.securevault",
        "com.motorola.spaces",
        "com.motorola.securityhub",
        "com.motorola.motosecure"
    )

    fun execute(context: Context, tileId: String, dryRunTest: Boolean = false): TileExecutionResult {
        val spec = TileCatalog.findById(tileId)
            ?: return TileExecutionResult(false, "Unknown", false, "Tile ID not found in catalog")

        val cur = TilePrefs.isTileActive(context, tileId)
        val res = runCatching {
            when (tileId) {
                "live_sync" -> openMotoSecure(context, dryRunTest)
                "app_lock_toggle" -> toggleAppLock(context, !TilePrefs.isAppLockArmed(context), dryRunTest)
                "lock_current_app" -> lockCurrentApp(context, dryRunTest)
                "clear_all_recents" -> checkOrRunA11y(dryRunTest) { it.clearAllRecents() to "Cleared" }
                "moto_audio_reset" -> resetAudio(context)
                "lock_screen" -> checkOrRunA11y(dryRunTest) { it.lockScreenNow() to "Locked" }
                "take_screenshot" -> checkOrRunA11y(dryRunTest) { it.takeScreenshotNow() to "Captured" }
                "power_dialog" -> checkOrRunA11y(dryRunTest) { it.openPowerDialogNow() to "Opened" }

                "caffeine" -> cycleCaffeine(context, dryRunTest)
                "adaptive_brightness" -> writeSettingToggle(context, Settings.System.SCREEN_BRIGHTNESS_MODE, "Auto", "Manual", dryRunTest)
                "brightness_low" -> setBrightness(context, 26, "10%", dryRunTest)
                "brightness_mid" -> setBrightness(context, 128, "50%", dryRunTest)
                "brightness_max" -> setBrightness(context, 255, "100%", dryRunTest)
                "screen_timeout" -> cycleTimeout(context, dryRunTest)
                "force_portrait" -> setRotation(context, false, 0, "Portrait", dryRunTest)
                "force_landscape" -> setRotation(context, false, 1, "Landscape", dryRunTest)
                "auto_rotate" -> writeSettingToggle(context, Settings.System.ACCELEROMETER_ROTATION, "Auto", "Locked", dryRunTest)
                "keep_dim_awake" -> TileExecutionResult(!cur, if (!cur) "Dim Awake" else "Off")

                "sound_profile" -> cycleSoundProfile(context, dryRunTest)
                "media_volume" -> cycleStreamStep(context, AudioManager.STREAM_MUSIC, "media_volume", listOf(0, 30, 70, 100), dryRunTest)
                "ring_volume" -> cycleStreamStep(context, AudioManager.STREAM_RING, "ring_volume", listOf(25, 60, 100), dryRunTest)
                "alarm_volume" -> cycleStreamStep(context, AudioManager.STREAM_ALARM, "alarm_volume", listOf(50, 80, 100), dryRunTest)
                "mute_media" -> toggleMuteMedia(context, dryRunTest)
                "mute_except_alarms" -> muteExceptAlarms(context, dryRunTest)
                "media_play_pause" -> sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "Play/Pause", dryRunTest)
                "media_next" -> sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT, "Next", dryRunTest)
                "media_prev" -> sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Prev", dryRunTest)
                "media_rewind" -> sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_REWIND, "-10s", dryRunTest)
                "media_forward" -> sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, "+10s", dryRunTest)
                "sleep_timer" -> cyclePreset(context, "sleep_timer", listOf("Off", "15m", "30m", "60m"))
                "white_noise" -> TileExecutionResult(!cur, if (!cur) "Playing" else "Off")
                "speak_clipboard" -> speakClip(context, dryRunTest)

                "pomodoro" -> TileExecutionResult(!cur, if (!cur) "25:00 Focus" else "Paused")
                "deep_focus_50" -> TileExecutionResult(!cur, if (!cur) "50:00 Deep" else "Paused")
                "short_break_5" -> TileExecutionResult(!cur, if (!cur) "5:00 Break" else "Off")
                "stopwatch" -> TileExecutionResult(!cur, if (!cur) "Running" else "Stopped")
                "cd_1m" -> TileExecutionResult(!cur, if (!cur) "1:00" else "Off")
                "cd_3m" -> TileExecutionResult(!cur, if (!cur) "3:00" else "Off")
                "cd_5m" -> TileExecutionResult(!cur, if (!cur) "5:00" else "Off")
                "cd_10m" -> TileExecutionResult(!cur, if (!cur) "10:00" else "Off")
                "cd_15m" -> TileExecutionResult(!cur, if (!cur) "15:00" else "Off")
                "habit_counter" -> incrementCounter(context, "habit_counter", "Count")
                "water_tracker" -> incrementCounter(context, "water_tracker", "Glass")
                "study_mode" -> setDndMode(context, NotificationManager.INTERRUPTION_FILTER_ALARMS, "Study", dryRunTest)
                "dnd_alarms" -> setDndMode(context, NotificationManager.INTERRUPTION_FILTER_ALARMS, "Alarms Only", dryRunTest)
                "dnd_none" -> setDndMode(context, NotificationManager.INTERRUPTION_FILTER_NONE, "Total Silence", dryRunTest)

                "clear_clipboard" -> clearClip(context, dryRunTest)
                "copy_timestamp" -> copyText(context, "Time", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()), dryRunTest)
                "copy_date_iso" -> copyText(context, "Date", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()), dryRunTest)
                "copy_epoch" -> copyText(context, "Epoch", (System.currentTimeMillis() / 1000L).toString(), dryRunTest)
                "pass_gen" -> genPassword(context, 16, dryRunTest)
                "pin_gen" -> copyText(context, "PIN", (100000 + SecureRandom().nextInt(900000)).toString(), dryRunTest)
                "uuid_gen" -> copyText(context, "UUID", UUID.randomUUID().toString(), dryRunTest)
                "dice_d6" -> TileExecutionResult(true, "D6: ${Random.nextInt(1, 7)}")
                "dice_d20" -> TileExecutionResult(true, "D20: ${Random.nextInt(1, 21)}")
                "coin_flip" -> TileExecutionResult(true, if (Random.nextBoolean()) "Heads" else "Tails")
                "rand_100" -> TileExecutionResult(true, "Num: ${Random.nextInt(1, 101)}")
                "yes_no" -> TileExecutionResult(true, if (Random.nextBoolean()) "YES" else "NO")
                "sos_strobe", "torch_toggle", "torch_pulse" -> toggleTorch(context, !cur, dryRunTest)
                "copy_device_info" -> copyText(context, "Device", "${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", dryRunTest)

                "battery_stats" -> readBatteryTemp(context)
                "battery_pct" -> readBatteryPct(context)
                "battery_volt" -> readBatteryVolt(context)
                "charge_status" -> readChargeState(context)
                "ram_monitor" -> readRam(context)
                "storage_free" -> readStorage()
                "thermal_status" -> readThermal(context)
                "cpu_cores" -> TileExecutionResult(true, "${Runtime.getRuntime().availableProcessors()} cores | ${Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64"}")
                "uptime_view" -> readUptime()
                "wifi_ip" -> readWifiIp(context, dryRunTest)
                "net_type" -> readNetType(context)
                "screen_dpi" -> readDpi(context)
                "android_ver" -> TileExecutionResult(true, "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                "haptic_toggle" -> writeSettingToggle(context, Settings.System.HAPTIC_FEEDBACK_ENABLED, "On", "Off", dryRunTest)

                "split_screen" -> checkOrRunA11y(dryRunTest) { it.toggleSplitScreenNow() to "Split" }
                "last_app" -> checkOrRunA11y(dryRunTest) { it.switchToLastApp() to "Switched" }
                "a11y_home" -> checkOrRunA11y(dryRunTest) { it.pressHomeNow() to "Home" }
                "a11y_back" -> checkOrRunA11y(dryRunTest) { it.pressBackNow() to "Back" }
                "a11y_notifs" -> checkOrRunA11y(dryRunTest) { it.openNotificationsNow() to "Shade" }
                "a11y_recents" -> checkOrRunA11y(dryRunTest) { it.openRecentsNow() to "Recents" }
                "custom_app_1" -> launchSlot(context, 1, dryRunTest)
                "custom_app_2" -> launchSlot(context, 2, dryRunTest)
                "custom_app_3" -> launchSlot(context, 3, dryRunTest)
                "custom_app_4" -> launchSlot(context, 4, dryRunTest)
                "custom_app_5" -> launchSlot(context, 5, dryRunTest)
                "launch_camera" -> launchIntentCheck(context, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), "Camera", dryRunTest)
                "launch_calc" -> launchIntentCheck(context, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR), "Calc", dryRunTest)
                "launch_clock" -> launchIntentCheck(context, Intent("android.intent.action.SHOW_ALARMS"), "Clock", dryRunTest)

                "compass_read" -> checkSensor(context, Sensor.TYPE_MAGNETIC_FIELD, "Compass Ready")
                "lux_meter" -> checkSensor(context, Sensor.TYPE_LIGHT, "Lux Sensor Ready")
                "tilt_angle" -> checkSensor(context, Sensor.TYPE_ACCELEROMETER, "Level Ready")
                "step_read" -> checkSensor(context, Sensor.TYPE_STEP_COUNTER, "Steps Ready")
                "breath_478" -> cyclePreset(context, "breath_478", listOf("Off", "Inhale 4s", "Hold 7s", "Exhale 8s"))
                "meeting_silent" -> setDndMode(context, NotificationManager.INTERRUPTION_FILTER_PRIORITY, "Meeting", dryRunTest)
                "exam_mode" -> setDndMode(context, NotificationManager.INTERRUPTION_FILTER_NONE, "Exam", dryRunTest)
                "night_ringer_off" -> setBrightness(context, 26, "Night 10%", dryRunTest)
                "morning_full" -> setBrightness(context, 191, "Morning 75%", dryRunTest)
                "webhook_1", "webhook_2" -> TileExecutionResult(true, "Queued", true, "OK (Configure URL in Hub)")
                "custom_intent" -> {
                    if (!dryRunTest) context.sendBroadcast(Intent("com.krishna.tiledeck.CUSTOM_TRIGGER"))
                    TileExecutionResult(true, "Fired")
                }
                else -> TileExecutionResult(!cur, if (!cur) "Active" else "Off")
            }
        }.getOrElse { e ->
            TileExecutionResult(false, "Error", false, "${e.javaClass.simpleName}: ${e.message ?: "failed"}")
        }

        if (!dryRunTest) {
            TilePrefs.setTileActive(context, tileId, res.active)
            TilePrefs.setSubtitle(context, tileId, res.subtitle)
        }
        return res
    }

    private fun openMotoSecure(context: Context, dryRun: Boolean): TileExecutionResult {
        val pm = context.packageManager
        val installedMotoPkgs = buildList {
            motoSecurePackages.forEach { pkg ->
                if (runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess) add(pkg)
            }
            // Also detect any Motorola secure/vault package even if renamed/disguised
            runCatching {
                pm.getInstalledPackages(0)
                    .map { it.packageName }
                    .filter { it.contains("motorola", true) && (it.contains("secure", true) || it.contains("vault", true) || it.contains("spaces", true)) }
                    .forEach { if (it !in this) add(it) }
            }
        }

        if (installedMotoPkgs.isEmpty()) {
            return TileExecutionResult(false, "Idle", false, "Moto Secure package not found on device")
        }

        // Step 1: Standard launcher intent (works when visible in tray)
        for (pkg in installedMotoPkgs) {
            val launch = pm.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (!dryRun) runCatching { context.startActivity(launch) }
                return TileExecutionResult(false, "Idle", true, "OK (Launcher: $pkg)", launchIntent = launch)
            }
        }

        // Step 2: Hidden from app tray — scan for exported entry activity directly (zero SU needed)
        val preferredKeywords = listOf("vault", "securefolder", "folder", "main", "home", "entry", "launcher", "splash")
        for (pkg in installedMotoPkgs) {
            val info = runCatching { pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES) }.getOrNull() ?: continue
            val exported = info.activities?.filter { it.exported && it.enabled } ?: emptyList()
            val best = exported.sortedByDescending { act ->
                val n = act.name.lowercase()
                preferredKeywords.indexOfFirst { n.contains(it) }.let { if (it >= 0) 100 - it else 0 }
            }.firstOrNull()
            if (best != null) {
                val direct = Intent(Intent.ACTION_MAIN).apply {
                    component = ComponentName(pkg, best.name)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (!dryRun) runCatching { context.startActivity(direct) }
                return TileExecutionResult(false, "Idle", true, "OK (Hidden-Tray Exported: ${best.name})", launchIntent = direct)
            }
        }

        // Step 3: Check secondary / private / work user profiles via LauncherApps (Android 14/15 Moto Secure)
        val um = context.getSystemService(Context.USER_SERVICE) as? UserManager
        val la = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
        if (um != null && la != null) {
            for (profile in um.userProfiles) {
                for (pkg in installedMotoPkgs) {
                    val acts = runCatching { la.getActivityList(pkg, profile) }.getOrNull().orEmpty()
                    val first = acts.firstOrNull()
                    if (first != null) {
                        if (!dryRun) runCatching { la.startMainActivity(first.componentName, profile, null, null) }
                        return TileExecutionResult(false, "Idle", true, "OK (Profile Launch: ${first.componentName.className})")
                    }
                }
            }
        }

        // Step 4: Known Moto Secure explicit action intents
        val actionCandidates = listOf(
            "com.motorola.motosecure.action.OPEN_SECURE_FOLDER",
            "com.motorola.securevault.ACTION_OPEN",
            "com.motorola.securityhub.ACTION_LAUNCH"
        )
        for (act in actionCandidates) {
            val i = Intent(act).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (i.resolveActivity(pm) != null) {
                if (!dryRun) runCatching { context.startActivity(i) }
                return TileExecutionResult(false, "Idle", true, "OK (Action: $act)", launchIntent = i)
            }
        }

        return TileExecutionResult(
            false,
            "Idle",
            false,
            "Hidden Moto Secure (${installedMotoPkgs.joinToString()}) has no exported activity; enable Disguised Icon in Moto Secure or open via Moto Secure app"
        )
    }

    private fun checkOrRunA11y(dryRun: Boolean, block: (TileDeckAccessibilityService) -> Pair<Boolean, String>): TileExecutionResult {
        val svc = TileDeckAccessibilityService.instance
            ?: return TileExecutionResult(false, "Need A11y", false, "TileDeck Accessibility Service is disabled")
        if (dryRun) return TileExecutionResult(true, "A11y Ready", true, "OK (Accessibility connected)")
        val (ok, sub) = block(svc)
        return TileExecutionResult(ok, if (ok) sub else "Failed", ok, if (ok) "OK" else "Accessibility action rejected")
    }

    private fun toggleAppLock(context: Context, armed: Boolean, dryRun: Boolean): TileExecutionResult {
        if (TileDeckAccessibilityService.instance == null) {
            return TileExecutionResult(false, "Need A11y", false, "Enable Accessibility Service for App Lock")
        }
        if (!dryRun) TilePrefs.setAppLockArmed(context, armed)
        val count = TilePrefs.getLockedPackages(context).size
        return TileExecutionResult(armed, if (armed) "Armed ($count)" else "Off", true, "OK ($count locked apps)")
    }

    private fun lockCurrentApp(context: Context, dryRun: Boolean): TileExecutionResult {
        if (TileDeckAccessibilityService.instance == null) {
            return TileExecutionResult(false, "Need A11y", false, "Enable Accessibility Service first")
        }
        val pkg = TileDeckAccessibilityService.lastForegroundPackage
            ?: return TileExecutionResult(false, "No Target", dryRun, if (dryRun) "OK (Waiting for foreground app)" else "No foreground app tracked yet")
        if (!dryRun) {
            TilePrefs.setPackageLocked(context, pkg, true)
            TilePrefs.setAppLockArmed(context, true)
            TileDeckAccessibilityService.unlockedSessionPkg = null
        }
        return TileExecutionResult(true, "Locked", true, "OK ($pkg)")
    }

    private fun writeSettingToggle(context: Context, key: String, onLabel: String, offLabel: String, dryRun: Boolean): TileExecutionResult {
        if (!Settings.System.canWrite(context)) {
            return TileExecutionResult(false, "Need Write", false, "Missing WRITE_SETTINGS permission")
        }
        val cur = Settings.System.getInt(context.contentResolver, key, 0)
        val next = if (cur == 1) 0 else 1
        if (!dryRun) Settings.System.putInt(context.contentResolver, key, next)
        return TileExecutionResult(next == 1, if (next == 1) onLabel else offLabel, true, "OK")
    }

    private fun setBrightness(context: Context, level: Int, label: String, dryRun: Boolean): TileExecutionResult {
        if (!Settings.System.canWrite(context)) {
            return TileExecutionResult(false, "Need Write", false, "Missing WRITE_SETTINGS permission")
        }
        if (!dryRun) {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, level)
        }
        return TileExecutionResult(true, label, true, "OK")
    }

    private fun cycleTimeout(context: Context, dryRun: Boolean): TileExecutionResult {
        if (!Settings.System.canWrite(context)) {
            return TileExecutionResult(false, "Need Write", false, "Missing WRITE_SETTINGS permission")
        }
        val steps = listOf(15_000 to "15s", 60_000 to "1m", 300_000 to "5m", 1_800_000 to "30m")
        val idx = (TilePrefs.getCounter(context, "screen_timeout") + 1) % steps.size
        if (!dryRun) {
            TilePrefs.setCounter(context, "screen_timeout", idx)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, steps[idx].first)
        }
        return TileExecutionResult(true, steps[idx].second, true, "OK")
    }

    private fun setRotation(context: Context, auto: Boolean, rot: Int, label: String, dryRun: Boolean): TileExecutionResult {
        if (!Settings.System.canWrite(context)) {
            return TileExecutionResult(false, "Need Write", false, "Missing WRITE_SETTINGS permission")
        }
        if (!dryRun) {
            Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (auto) 1 else 0)
            Settings.System.putInt(context.contentResolver, Settings.System.USER_ROTATION, rot)
        }
        return TileExecutionResult(true, label, true, "OK")
    }

    private fun setDndMode(context: Context, filter: Int, label: String, dryRun: Boolean): TileExecutionResult {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            return TileExecutionResult(false, "Need DND", false, "Missing Do Not Disturb policy permission")
        }
        val already = nm.currentInterruptionFilter == filter
        if (!dryRun) {
            nm.setInterruptionFilter(if (already) NotificationManager.INTERRUPTION_FILTER_ALL else filter)
        }
        return TileExecutionResult(!already, if (!already) label else "Normal", true, "OK")
    }

    private fun cycleSoundProfile(context: Context, dryRun: Boolean): TileExecutionResult {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            return TileExecutionResult(false, "Need DND", false, "Missing Do Not Disturb policy permission")
        }
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val modes = listOf(AudioManager.RINGER_MODE_NORMAL to "Normal", AudioManager.RINGER_MODE_VIBRATE to "Vibrate", AudioManager.RINGER_MODE_SILENT to "Silent")
        val idx = (TilePrefs.getCounter(context, "sound_profile") + 1) % modes.size
        if (!dryRun) {
            TilePrefs.setCounter(context, "sound_profile", idx)
            am.ringerMode = modes[idx].first
        }
        return TileExecutionResult(idx != 0, modes[idx].second, true, "OK")
    }

    private fun cycleStreamStep(context: Context, stream: Int, key: String, percents: List<Int>, dryRun: Boolean): TileExecutionResult {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(stream)
        val idx = (TilePrefs.getCounter(context, key) + 1) % percents.size
        if (!dryRun) {
            TilePrefs.setCounter(context, key, idx)
            am.setStreamVolume(stream, (max * percents[idx]) / 100, 0)
        }
        return TileExecutionResult(percents[idx] > 0, "${percents[idx]}%", true, "OK")
    }

    private fun toggleMuteMedia(context: Context, dryRun: Boolean): TileExecutionResult {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val mute = curVol > 0
        if (!dryRun) am.setStreamVolume(AudioManager.STREAM_MUSIC, if (mute) 0 else am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 2, 0)
        return TileExecutionResult(mute, if (mute) "Muted" else "50%", true, "OK")
    }

    private fun muteExceptAlarms(context: Context, dryRun: Boolean): TileExecutionResult {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) return TileExecutionResult(false, "Need DND", false, "Missing DND permission")
        if (!dryRun) {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            am.setStreamVolume(AudioManager.STREAM_RING, 0, 0)
            am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
        }
        return TileExecutionResult(true, "Muted", true, "OK")
    }

    private fun resetAudio(context: Context): TileExecutionResult {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.mode = AudioManager.MODE_NORMAL
        am.isSpeakerphoneOn = false
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, 0)
        return TileExecutionResult(true, "Reset OK", true, "OK")
    }

    private fun sendMediaKey(context: Context, keyCode: Int, label: String, dryRun: Boolean): TileExecutionResult {
        if (!dryRun) {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
        return TileExecutionResult(true, label, true, "OK")
    }

    @Suppress("DEPRECATION")
    private fun cycleCaffeine(context: Context, dryRun: Boolean): TileExecutionResult {
        val steps = listOf("Off", "5m", "15m", "30m", "Inf")
        val nextIdx = (TilePrefs.getCounter(context, "caffeine") + 1) % steps.size
        if (dryRun) return TileExecutionResult(true, "Ready", true, "OK (WakeLock available)")
        TilePrefs.setCounter(context, "caffeine", nextIdx)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        if (nextIdx == 0) return TileExecutionResult(false, "Off")
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val ms = when (nextIdx) { 1 -> 300_000L; 2 -> 900_000L; 3 -> 1_800_000L; else -> 21_600_000L }
        wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE, "TileDeck:Caffeine")
        wakeLock?.acquire(ms)
        return TileExecutionResult(true, steps[nextIdx])
    }

    private fun speakClip(context: Context, dryRun: Boolean): TileExecutionResult {
        if (dryRun) return TileExecutionResult(true, "TTS Ready", true, "OK")
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
        if (text.isNullOrBlank()) return TileExecutionResult(false, "Empty", false, "Clipboard is empty")
        tts = TextToSpeech(context) { st ->
            if (st == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.speak(text.take(400), TextToSpeech.QUEUE_FLUSH, null, "td_tts")
            }
        }
        return TileExecutionResult(true, "Speaking", true, "OK")
    }

    private fun cyclePreset(context: Context, key: String, list: List<String>): TileExecutionResult {
        val idx = (TilePrefs.getCounter(context, key) + 1) % list.size
        TilePrefs.setCounter(context, key, idx)
        return TileExecutionResult(idx != 0, list[idx], true, "OK")
    }

    private fun incrementCounter(context: Context, key: String, label: String): TileExecutionResult {
        val c = TilePrefs.getCounter(context, key) + 1
        TilePrefs.setCounter(context, key, c)
        return TileExecutionResult(true, "$label: $c", true, "OK")
    }

    private fun clearClip(context: Context, dryRun: Boolean): TileExecutionResult {
        if (!dryRun) {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) cm.clearPrimaryClip()
            else cm.setPrimaryClip(ClipData.newPlainText("", ""))
        }
        return TileExecutionResult(false, "Cleared", true, "OK")
    }

    private fun copyText(context: Context, label: String, value: String, dryRun: Boolean): TileExecutionResult {
        if (!dryRun) {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(label, value))
        }
        return TileExecutionResult(true, value.take(18), true, "OK")
    }

    private fun genPassword(context: Context, len: Int, dryRun: Boolean): TileExecutionResult {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#\$%^&*"
        val rng = SecureRandom()
        val pw = (1..len).map { chars[rng.nextInt(chars.length)] }.joinToString("")
        return copyText(context, "Password", pw, dryRun)
    }

    private fun toggleTorch(context: Context, on: Boolean, dryRun: Boolean): TileExecutionResult {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull()
            ?: return TileExecutionResult(false, "No LED", false, "No camera flash unit found")
        if (!dryRun) cm.setTorchMode(id, on)
        return TileExecutionResult(on, if (on) "On" else "Off", true, "OK")
    }

    private fun readBatteryTemp(context: Context): TileExecutionResult {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val c = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val ma = kotlin.math.abs(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) / 1000)
        return TileExecutionResult(true, "${c}C / ${ma}mA", true, "OK")
    }

    private fun readBatteryPct(context: Context): TileExecutionResult {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return TileExecutionResult(true, "$pct%", true, "OK")
    }

    private fun readBatteryVolt(context: Context): TileExecutionResult {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val mv = i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        return TileExecutionResult(true, String.format(Locale.US, "%.2fV", mv / 1000.0), true, "OK")
    }

    private fun readChargeState(context: Context): TileExecutionResult {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val lbl = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Fast"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "Battery"
        }
        return TileExecutionResult(plugged != 0, lbl, true, "OK")
    }

    private fun readRam(context: Context): TileExecutionResult {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return TileExecutionResult(true, String.format(Locale.US, "%.1f GB free", mi.availMem / 1_073_741_824.0), true, "OK")
    }

    private fun readStorage(): TileExecutionResult {
        val stat = StatFs(Environment.getDataDirectory().path)
        return TileExecutionResult(true, String.format(Locale.US, "%.1f GB free", stat.availableBytes / 1_073_741_824.0), true, "OK")
    }

    private fun readThermal(context: Context): TileExecutionResult {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val lbl = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (pm.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "Normal"
                PowerManager.THERMAL_STATUS_LIGHT -> "Warm"
                PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
                else -> "Hot"
            }
        } else "Normal"
        return TileExecutionResult(lbl != "Normal", lbl, true, "OK")
    }

    private fun readUptime(): TileExecutionResult {
        val mins = SystemClock.elapsedRealtime() / 60_000L
        return TileExecutionResult(true, "${mins / 60}h ${mins % 60}m", true, "OK")
    }

    @Suppress("DEPRECATION")
    private fun readWifiIp(context: Context, dryRun: Boolean): TileExecutionResult {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipInt = wm.connectionInfo.ipAddress
        val ip = String.format(Locale.US, "%d.%d.%d.%d", ipInt and 0xff, ipInt shr 8 and 0xff, ipInt shr 16 and 0xff, ipInt shr 24 and 0xff)
        if (!dryRun && ip != "0.0.0.0") copyText(context, "IP", ip, false)
        return TileExecutionResult(ip != "0.0.0.0", ip, true, "OK")
    }

    private fun readNetType(context: Context): TileExecutionResult {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val lbl = when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Connected"
        }
        return TileExecutionResult(caps != null, lbl, true, "OK")
    }

    private fun readDpi(context: Context): TileExecutionResult {
        val dm = context.resources.displayMetrics
        return TileExecutionResult(true, "${dm.widthPixels}x${dm.heightPixels} ${dm.densityDpi}dpi", true, "OK")
    }

    private fun launchSlot(context: Context, slot: Int, dryRun: Boolean): TileExecutionResult {
        val pkg = TilePrefs.getCustomAppSlot(context, slot)
        if (pkg.isBlank()) return TileExecutionResult(false, "Unset", false, "App Slot $slot is not assigned yet")
        val i = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: return TileExecutionResult(false, "Missing", false, "Assigned package $pkg not installed")
        if (!dryRun) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(i) }
        return TileExecutionResult(true, "Opened", true, "OK ($pkg)")
    }

    private fun launchIntentCheck(context: Context, intent: Intent, label: String, dryRun: Boolean): TileExecutionResult {
        val resolved = intent.resolveActivity(context.packageManager)
            ?: return TileExecutionResult(false, "No App", false, "No handler installed for $label")
        if (!dryRun) { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(intent) }
        return TileExecutionResult(true, label, true, "OK (${resolved.packageName})")
    }

    private fun checkSensor(context: Context, type: Int, label: String): TileExecutionResult {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val s = sm.getDefaultSensor(type)
            ?: return TileExecutionResult(false, "No Sensor", false, "Hardware sensor type $type unavailable")
        return TileExecutionResult(true, label, true, "OK (${s.name})")
    }
}
