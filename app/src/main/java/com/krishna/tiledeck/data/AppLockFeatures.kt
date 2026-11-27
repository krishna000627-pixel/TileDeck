package com.krishna.tiledeck.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LockFeatureSpec(
    val number: Int,
    val key: String,
    val title: String,
    val category: String,
    val description: String,
    val defaultEnabled: Boolean
)

object AppLockFeatures {
    private const val PREF_NAME = "tiledeck_applock_100"

    val all: List<LockFeatureSpec> = listOf(

        LockFeatureSpec(1, "bio_enabled", "Fingerprint / Biometric Unlock", "Unlock & Biometrics", "Shows biometric button on lock screen for instant fingerprint unlock.", true),
        LockFeatureSpec(2, "bio_auto_prompt", "Auto-Prompt Fingerprint", "Unlock & Biometrics", "Triggers system fingerprint dialog immediately when a locked app opens.", false),
        LockFeatureSpec(3, "mode_pin", "Numeric PIN Mode", "Unlock & Biometrics", "Uses tactile circular numeric keypad (4-8 digits).", true),
        LockFeatureSpec(4, "mode_pattern", "3x3 Pattern Lock Mode", "Unlock & Biometrics", "Switches lock screen to an interactive 3x3 canvas pattern grid.", false),
        LockFeatureSpec(5, "auto_submit_pin", "Auto-Submit on Exact Length", "Unlock & Biometrics", "Unlocks immediately when entered digits match your PIN length.", true),
        LockFeatureSpec(6, "key_haptics", "Tactile Key Haptics", "Unlock & Biometrics", "Triggers crisp haptic feedback on every keypad tap.", true),
        LockFeatureSpec(7, "key_spring_anim", "Spring Key Press Animation", "Unlock & Biometrics", "Animates keypad buttons with subtle tactile scale feedback.", true),
        LockFeatureSpec(8, "single_backspace", "Single-Digit Backspace", "Unlock & Biometrics", "Replaces C with single-digit delete (long-press clears all).", true),
        LockFeatureSpec(9, "quick_clear_btn", "Quick Clear All", "Unlock & Biometrics", "Clears all entered PIN digits in one tap.", false),
        LockFeatureSpec(10, "show_pin_hint", "Show Security Hint", "Unlock & Biometrics", "Displays a subtle custom hint text under the PIN dots.", false),
        LockFeatureSpec(11, "shuffle_keypad", "Shuffled Keypad Digits", "Stealth & Anti-Peek", "Randomizes 0-9 keypad positions every time the lock screen opens.", false),
        LockFeatureSpec(12, "reshuffle_on_fail", "Re-Shuffle After Wrong PIN", "Stealth & Anti-Peek", "Scrambles keypad positions again after every failed attempt.", false),
        LockFeatureSpec(13, "stealth_blank_keys", "Blank Key Labels", "Stealth & Anti-Peek", "Hides key numbers until pressed to stop over-the-shoulder peeking.", false),
        LockFeatureSpec(14, "invisible_dots", "Invisible PIN Dots", "Stealth & Anti-Peek", "Hides filled PIN indicator dots so onlookers cannot count digits.", false),
        LockFeatureSpec(15, "hide_pattern_line", "Invisible Pattern Trail", "Stealth & Anti-Peek", "Hides drawn connecting lines while tracing your 3x3 pattern.", false),
        LockFeatureSpec(16, "hide_app_name", "Mask Target App Name", "Stealth & Anti-Peek", "Replaces locked app name with generic Protected Application.", false),
        LockFeatureSpec(17, "hide_app_icon", "Mask Target App Icon", "Stealth & Anti-Peek", "Shows generic shield badge instead of the real locked app icon.", false),
        LockFeatureSpec(18, "amoled_stealth", "Pure AMOLED Pitch Black", "Stealth & Anti-Peek", "Forces pure #000000 background with ultra-dim borders.", false),
        LockFeatureSpec(19, "dim_lock_ui", "Low-Glare Night Lock", "Stealth & Anti-Peek", "Dims lock screen text and keypad contrast for dark rooms.", false),
        LockFeatureSpec(20, "hide_hud_bar", "Hide Top Telemetry HUD", "Stealth & Anti-Peek", "Removes clock and battery status bar from lock screen.", false),
        LockFeatureSpec(21, "cover_fake_crash", "Fake Crash Dialog Cover", "Decoy & Disguise", "Shows realistic App keeps stopping dialog; hold Close 2s to reveal PIN.", false),
        LockFeatureSpec(22, "cover_calc", "Working Calculator Disguise", "Decoy & Disguise", "Opens a working calculator; type your PIN then = to unlock.", false),
        LockFeatureSpec(23, "cover_sys_update", "Fake System Update Cover", "Decoy & Disguise", "Shows Optimizing app storage screen; hold title 2s to unlock.", false),
        LockFeatureSpec(24, "cover_no_net", "Fake No Internet Cover", "Decoy & Disguise", "Shows Network unavailable decoy; hold Retry 2s to reveal PIN.", false),
        LockFeatureSpec(25, "cover_storage_full", "Fake Storage Full Cover", "Decoy & Disguise", "Shows Device storage low decoy; hold Dismiss 2s to unlock.", false),
        LockFeatureSpec(26, "custom_decoy_msg", "Custom Decoy Subtitle", "Decoy & Disguise", "Displays your own custom decoy message on cover screens.", false),
        LockFeatureSpec(27, "duress_pin_enabled", "Duress / Panic PIN (9999)", "Decoy & Disguise", "Entering Panic PIN silently exits to Home and logs an intrusion.", true),
        LockFeatureSpec(28, "decoy_realistic_delay", "Realistic Decoy Delay", "Decoy & Disguise", "Adds a subtle 400ms transition before decoy cover appears.", false),
        LockFeatureSpec(29, "triple_tap_reveal", "Triple-Tap Corner Reveal", "Decoy & Disguise", "Triple-tap top-right corner of any decoy to open PIN keypad.", true),
        LockFeatureSpec(30, "exit_on_decoy_back", "Decoy Back Exits to Home", "Decoy & Disguise", "Pressing Back on decoy screen immediately returns to Home.", true),
        LockFeatureSpec(31, "log_intruders", "Intruder Attempt Logger", "Intruder Defense", "Logs timestamp, app name, and wrong PIN for every failed attempt.", true),
        LockFeatureSpec(32, "lockout_3_fails", "Lockout After 3 Failures", "Intruder Defense", "Starts a mandatory cooldown timer after 3 consecutive wrong PINs.", true),
        LockFeatureSpec(33, "lockout_15s", "15-Second Cooldown", "Intruder Defense", "Locks keypad input for 15 seconds during intruder cooldown.", true),
        LockFeatureSpec(34, "lockout_30s", "30-Second Strict Cooldown", "Intruder Defense", "Extends intruder cooldown lockout to 30 seconds.", false),
        LockFeatureSpec(35, "progressive_cooldown", "Exponential Cooldown", "Intruder Defense", "Doubles lockout duration on repeated consecutive failure waves.", false),
        LockFeatureSpec(36, "vibrate_on_fail", "Heavy Fail Vibration", "Intruder Defense", "Triggers a distinct double haptic shake on wrong PIN entry.", true),
        LockFeatureSpec(37, "red_flash_error", "Red Alert Border Flash", "Intruder Defense", "Flashes crimson security border when an incorrect PIN is entered.", true),
        LockFeatureSpec(38, "home_on_3_fails", "Kick to Home on 3 Fails", "Intruder Defense", "Automatically forces intruder back to launcher after 3 wrong PINs.", false),
        LockFeatureSpec(39, "show_attempts_left", "Show Remaining Attempts", "Intruder Defense", "Displays live attempts-remaining warning on the lock screen.", true),
        LockFeatureSpec(40, "log_duress_events", "Flag Duress Events", "Intruder Defense", "Highlights Panic PIN entries in the Intruder Log as HIGH ALERT.", true),
        LockFeatureSpec(41, "relock_immediate", "Relock Immediately on Exit", "Smart Re-Lock", "Re-locks protected app the moment you leave it.", true),
        LockFeatureSpec(42, "relock_screen_off", "Relock on Screen Off", "Smart Re-Lock", "Keeps app unlocked until you turn the display off.", false),
        LockFeatureSpec(43, "grace_30s", "30-Second Grace Period", "Smart Re-Lock", "Allows returning to a recently unlocked app within 30 seconds.", false),
        LockFeatureSpec(44, "grace_1m", "1-Minute Grace Period", "Smart Re-Lock", "Keeps unlocked session valid for 1 minute after leaving.", false),
        LockFeatureSpec(45, "grace_5m", "5-Minute Grace Period", "Smart Re-Lock", "Keeps unlocked session valid for 5 minutes after leaving.", false),
        LockFeatureSpec(46, "grace_15m", "15-Minute Grace Period", "Smart Re-Lock", "Keeps unlocked session valid for 15 minutes during active work.", false),
        LockFeatureSpec(47, "master_unlock_all", "Unlock All Together", "Smart Re-Lock", "One successful PIN unlocks all protected apps for the session.", false),
        LockFeatureSpec(48, "per_app_sessions", "Per-App Strict Isolation", "Smart Re-Lock", "Requires separate authentication for every locked app.", true),
        LockFeatureSpec(49, "relock_on_home", "Relock on Launcher Home", "Smart Re-Lock", "Immediately clears unlocked session whenever Home is reached.", true),
        LockFeatureSpec(50, "clear_session_quick", "Clear Session on Shield Tap", "Smart Re-Lock", "Tapping App Lock Shield tile wipes all active grace sessions.", true),
        LockFeatureSpec(51, "theme_obsidian", "Obsidian Mono Theme", "UI & Themes", "Crisp #09090B matte obsidian with emerald signal accents.", true),
        LockFeatureSpec(52, "theme_emerald", "Cyber Emerald Theme", "UI & Themes", "Deep forest charcoal with luminous emerald telemetry accents.", false),
        LockFeatureSpec(53, "theme_titanium", "Titanium Slate Theme", "UI & Themes", "Cool architectural slate with ice-cyan precision accents.", false),
        LockFeatureSpec(54, "theme_crimson", "Crimson Tactical Theme", "UI & Themes", "High-alert matte black with tactical red security accents.", false),
        LockFeatureSpec(55, "theme_amber", "Amber Industrial Theme", "UI & Themes", "Graphite mono surfaces with warm amber instrument highlights.", false),
        LockFeatureSpec(56, "show_live_clock", "Live Digital Clock HUD", "UI & Themes", "Shows crisp monospace time in the top lock screen bar.", true),
        LockFeatureSpec(57, "show_live_battery", "Live Battery % HUD", "UI & Themes", "Displays real-time battery percentage in the lock header.", true),
        LockFeatureSpec(58, "animated_shield_ring", "Animated Radar Shield Ring", "UI & Themes", "Draws a smooth pulsing concentric ring around the app icon.", true),
        LockFeatureSpec(59, "one_handed_keypad", "One-Handed Bottom Keypad", "UI & Themes", "Anchors keypad closer to thumb reach at the bottom of the screen.", false),
        LockFeatureSpec(60, "flat_keypad_style", "Minimal Flat Keys", "UI & Themes", "Switches circular bordered keys to ultra-clean borderless numerals.", false),
        LockFeatureSpec(61, "preset_stranger", "Stranger Handover Mode", "Profiles & Presets", "One-tap preset protecting social, gallery, messaging, and banking.", false),
        LockFeatureSpec(62, "preset_study", "Study Lockdown Preset", "Profiles & Presets", "Locks distracting video and social apps during study sessions.", false),
        LockFeatureSpec(63, "preset_sleep", "Sleep Night Guard", "Profiles & Presets", "Locks late-night distraction apps automatically after bedtime.", false),
        LockFeatureSpec(64, "preset_work", "Work Focus Preset", "Profiles & Presets", "Isolates personal apps while keeping productivity tools open.", false),
        LockFeatureSpec(65, "preset_exam", "Exam Strict Shield", "Profiles & Presets", "Enforces immediate relock + 30s cooldown on all locked apps.", false),
        LockFeatureSpec(66, "preset_minimal_anim", "Zero-Animation Fast Lock", "Profiles & Presets", "Disables entrance transitions for instant zero-frame lock.", false),
        LockFeatureSpec(67, "show_profile_badge", "Show Active Mode Badge", "Profiles & Presets", "Displays current active lock profile tag on the lock screen.", true),
        LockFeatureSpec(68, "quick_lock_toast", "Silent Lock Mode", "Profiles & Presets", "Suppresses toast messages when arming or disarming App Lock.", false),
        LockFeatureSpec(69, "auto_arm_boot", "Remember Armed State", "Profiles & Presets", "Preserves App Lock armed state cleanly across app restarts.", true),
        LockFeatureSpec(70, "badge_locked_count", "Show Locked Count Badge", "Profiles & Presets", "Displays count of protected apps in the App Lock header.", true),
        LockFeatureSpec(71, "mindful_wait_5s", "5-Second Mindfulness Wait", "Mindfulness & Time", "Requires a 5-second pause countdown before PIN entry unlocks.", false),
        LockFeatureSpec(72, "track_open_count", "Daily App Open Counter", "Mindfulness & Time", "Counts how many times each locked app is opened today.", true),
        LockFeatureSpec(73, "show_open_count", "Show Open Count on Lock", "Mindfulness & Time", "Displays Opened N times today right on the lock screen.", true),
        LockFeatureSpec(74, "limit_10_opens", "Soft Limit: 10 Opens/Day", "Mindfulness & Time", "Shows a warning banner once a locked app exceeds 10 opens today.", false),
        LockFeatureSpec(75, "temp_pass_15m", "15-Min Temporary Pass", "Mindfulness & Time", "Allows pausing App Lock for 15 minutes with automatic re-arm.", false),
        LockFeatureSpec(76, "temp_pass_1h", "1-Hour Temporary Pass", "Mindfulness & Time", "Allows pausing App Lock for 1 hour with automatic re-arm.", false),
        LockFeatureSpec(77, "night_curfew_lock", "Night Curfew (11PM-6AM)", "Mindfulness & Time", "Enforces strict immediate relock between 11:00 PM and 6:00 AM.", false),
        LockFeatureSpec(78, "study_window_lock", "Study Window (4PM-9PM)", "Mindfulness & Time", "Enforces strict lock behavior during afternoon/evening study hours.", false),
        LockFeatureSpec(79, "weekday_strict", "Weekday Strict Mode", "Mindfulness & Time", "Applies strict lockout rules Monday through Friday.", false),
        LockFeatureSpec(80, "pomodoro_link", "Sync With Pomodoro Tile", "Mindfulness & Time", "Automatically arms App Lock whenever Pomodoro 25m is active.", false),
        LockFeatureSpec(81, "protect_settings", "Guard Android Settings", "Anti-Tamper", "Adds Android Settings to protected apps to block force-stop.", false),
        LockFeatureSpec(82, "protect_installer", "Guard Package Installer", "Anti-Tamper", "Protects Package Installer to stop unauthorized uninstalls.", false),
        LockFeatureSpec(83, "protect_play_store", "Guard Play Store", "Anti-Tamper", "Locks Google Play Store from unapproved installs or removals.", false),
        LockFeatureSpec(84, "protect_files", "Guard File Managers", "Anti-Tamper", "Locks system Files app to protect downloads and documents.", false),
        LockFeatureSpec(85, "exclude_recents", "Hide Lock from Recents", "Anti-Tamper", "Keeps lock screen invisible in Android Overview/Recents.", true),
        LockFeatureSpec(86, "pin_for_lock_tab", "Require PIN for Lock Tab", "Anti-Tamper", "Protects App Lock settings inside TileDeck with your PIN.", false),
        LockFeatureSpec(87, "pin_to_disarm", "Require PIN to Disarm", "Anti-Tamper", "Prevents turning off App Lock Shield without entering your PIN.", false),
        LockFeatureSpec(88, "block_back_bypass", "Strict Back-Button Trap", "Anti-Tamper", "Always routes Back press on lock screen directly to Home.", true),
        LockFeatureSpec(89, "a11y_watchdog", "Accessibility Watchdog", "Anti-Tamper", "Shows live warning banner if Accessibility Service is turned off.", true),
        LockFeatureSpec(90, "moto_secure_compat", "Moto Secure Co-Exist", "Anti-Tamper", "Never double-locks Moto Secure Folder when opened via Live Sync.", true),
        LockFeatureSpec(91, "diag_test_pin", "Live Lock Screen Simulator", "Diagnostics & Hub", "Tests your exact active theme, keypad, and cover right from Lock tab.", true),
        LockFeatureSpec(92, "diag_test_crash", "Quick Test Fake Crash", "Diagnostics & Hub", "Preview the Fake Crash decoy screen in 1 tap.", true),
        LockFeatureSpec(93, "diag_test_calc", "Quick Test Calc Disguise", "Diagnostics & Hub", "Preview the Working Calculator disguise in 1 tap.", true),
        LockFeatureSpec(94, "diag_bio_check", "Biometric Hardware Audit", "Diagnostics & Hub", "Checks if device fingerprint/biometric sensor is enrolled and ready.", true),
        LockFeatureSpec(95, "copy_security_audit", "Copy Security Audit", "Diagnostics & Hub", "Copies active lock feature summary and stats to clipboard.", true),
        LockFeatureSpec(96, "copy_intruder_log", "Copy Intruder Log", "Diagnostics & Hub", "Copies full failed-attempt audit trail straight to clipboard.", true),
        LockFeatureSpec(97, "clear_intruder_log", "Wipe Intruder Logs", "Diagnostics & Hub", "Clears stored failed-attempt records cleanly.", true),
        LockFeatureSpec(98, "reset_open_counters", "Reset Daily Counters", "Diagnostics & Hub", "Resets per-app daily open tallies back to zero.", true),
        LockFeatureSpec(99, "export_lock_json", "Export Config to Clipboard", "Diagnostics & Hub", "Exports enabled feature flags and settings as compact JSON.", true),
        LockFeatureSpec(100, "hub_lock_broadcast", "App Hub Lock Sync", "Diagnostics & Hub", "Exposes live App Lock armed status to TileDeck App Hub API.", true)
    )

    val categories: List<String> = all.map { it.category }.distinct()
    private val byKey: Map<String, LockFeatureSpec> = all.associateBy { it.key }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context, key: String): Boolean {
        val def = byKey[key]?.defaultEnabled ?: false
        return prefs(context).getBoolean("f_$key", def)
    }

    fun setEnabled(context: Context, key: String, enabled: Boolean) {
        prefs(context).edit().putBoolean("f_$key", enabled).apply()
    }

    fun enabledCount(context: Context): Int = all.count { isEnabled(context, it.key) }

    fun getPattern(context: Context): String =
        prefs(context).getString("lock_pattern", "0-1-2-5-8") ?: "0-1-2-5-8"

    fun setPattern(context: Context, pattern: String) {
        prefs(context).edit().putString("lock_pattern", pattern).apply()
    }

    fun getDuressPin(context: Context): String =
        prefs(context).getString("duress_pin", "9999") ?: "9999"

    fun setDuressPin(context: Context, pin: String) {
        if (pin.length >= 4) prefs(context).edit().putString("duress_pin", pin).apply()
    }

    fun getHint(context: Context): String =
        prefs(context).getString("pin_hint", "Use your TileDeck PIN") ?: "Use your TileDeck PIN"

    fun setHint(context: Context, hint: String) {
        prefs(context).edit().putString("pin_hint", hint).apply()
    }

    fun getOpenCount(context: Context, pkg: String): Int =
        prefs(context).getInt("opens_$pkg", 0)

    fun incrementOpenCount(context: Context, pkg: String): Int {
        val next = getOpenCount(context, pkg) + 1
        prefs(context).edit().putInt("opens_$pkg", next).apply()
        return next
    }

    fun resetOpenCounts(context: Context) {
        val editor = prefs(context).edit()
        prefs(context).all.keys.filter { it.startsWith("opens_") }.forEach { editor.remove(it) }
        editor.apply()
    }

    fun getIntruderLogs(context: Context): List<String> {
        val raw = prefs(context).getString("intruder_logs", "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split("\n")
    }

    fun logIntruder(context: Context, appLabel: String, detail: String) {
        if (!isEnabled(context, "log_intruders")) return
        val ts = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date())
        val entry = "[$ts] $appLabel | $detail"
        val updated = (listOf(entry) + getIntruderLogs(context)).take(80)
        prefs(context).edit().putString("intruder_logs", updated.joinToString("\n")).apply()
    }

    fun clearIntruderLogs(context: Context) {
        prefs(context).edit().remove("intruder_logs").apply()
    }

    fun activeThemeName(context: Context): String = when {
        isEnabled(context, "amoled_stealth") -> "AMOLED"
        isEnabled(context, "theme_crimson") -> "CRIMSON"
        isEnabled(context, "theme_emerald") -> "EMERALD"
        isEnabled(context, "theme_titanium") -> "TITANIUM"
        isEnabled(context, "theme_amber") -> "AMBER"
        else -> "OBSIDIAN"
    }

    fun exportSummaryJson(context: Context): String {
        val activeKeys = all.filter { isEnabled(context, it.key) }.map { it.key }
        return "{\"app\":\"TileDeck\",\"version\":\"11.0.0\",\"enabled_features\":${activeKeys.size},\"theme\":\"${activeThemeName(context)}\",\"keys\":[${activeKeys.joinToString(",") { "\"$it\"" }}]}"
    }
}
