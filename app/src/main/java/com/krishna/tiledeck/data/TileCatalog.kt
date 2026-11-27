package com.krishna.tiledeck.data

data class TileSpec(
    val number: Int,
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val permissionTier: String,
    val serviceClassName: String,
    val defaultEnabled: Boolean,
    val requiresRoot: Boolean
)

object TileCatalog {
    val allTiles: List<TileSpec> = listOf(

        TileSpec(1, "moto_secure_open", "Moto Secure Folder", "Moto & Private Space", "Opens Motorola Secure Folder directly (uses root fallback if hidden).", "Direct", "MotoSecureOpenTileService", true, false),
        TileSpec(2, "moto_secure_toggle", "Hide/Show Moto Secure", "Moto & Private Space", "Toggles Moto Secure Folder visibility in the app tray and launcher.", "Root", "MotoSecureToggleTileService", true, true),
        TileSpec(3, "hidden_tray_toggle", "Hidden App Tray", "Moto & Private Space", "Batch hides or unhides your selected secret apps in launcher and Moto Secure.", "Root", "HiddenTrayToggleTileService", true, true),
        TileSpec(4, "app_lock_toggle", "App Lock Shield", "Moto & Private Space", "Arms or disarms instant PIN App Lock across all selected apps.", "Accessibility", "AppLockToggleTileService", true, false),
        TileSpec(5, "lock_current_app", "Lock Current App", "Moto & Private Space", "Adds the currently open app to App Lock and locks it immediately.", "Accessibility", "LockCurrentAppTileService", false, false),
        TileSpec(6, "switch_user_profile", "Switch User Space", "Moto & Private Space", "Switches directly between Main User (0) and Secondary User profile.", "Root", "SwitchUserProfileTileService", false, true),
        TileSpec(7, "moto_keep_alive", "Moto Keep-Alive", "Moto & Private Space", "Applies deviceidle whitelist and EXEMPTED standby bucket to stop Moto kills.", "Root", "MotoKeepAliveTileService", true, true),
        TileSpec(8, "clear_all_recents", "Clear All Recents", "Moto & Private Space", "Opens Recents via Accessibility and auto-triggers Clear All.", "Accessibility", "ClearAllRecentsTileService", false, false),
        TileSpec(9, "kill_foreground", "Kill Current App", "Moto & Private Space", "Force-stops the currently focused foreground app immediately.", "Root", "KillForegroundTileService", false, true),
        TileSpec(10, "restart_systemui", "Restart SystemUI", "Moto & Private Space", "Restarts stuck notification shade or Moto Hello UI controls cleanly.", "Root", "RestartSystemuiTileService", false, true),
        TileSpec(11, "caffeine", "Caffeine", "Display & Screen", "Cycles screen-on wake-lock timers (5m, 15m, 30m, Inf) directly.", "Standard", "CaffeineTileService", true, false),
        TileSpec(12, "adaptive_brightness", "Auto Brightness", "Display & Screen", "Toggles system adaptive brightness directly via WRITE_SETTINGS.", "WriteSettings", "AdaptiveBrightnessTileService", false, false),
        TileSpec(13, "brightness_preset", "Brightness Step", "Display & Screen", "Sets screen brightness directly to 10%, 40%, 75%, or 100%.", "WriteSettings", "BrightnessPresetTileService", false, false),
        TileSpec(14, "extra_dim", "Extra Dim Overlay", "Display & Screen", "Applies a dark screen-dimming overlay below hardware minimum.", "Overlay", "ExtraDimTileService", false, false),
        TileSpec(15, "blackout_pocket", "Blackout Pocket", "Display & Screen", "Pure-black touch-blocking screen while audio/tasks keep running.", "Overlay", "BlackoutPocketTileService", true, false),
        TileSpec(16, "reading_grayscale", "Grayscale Mode", "Display & Screen", "Toggles monochrome display directly via daltonizer secure setting.", "Root", "ReadingGrayscaleTileService", false, true),
        TileSpec(17, "screen_timeout", "Screen Timeout", "Display & Screen", "Writes system timeout directly (15s, 1m, 5m, 30m) without Settings.", "WriteSettings", "ScreenTimeoutTileService", false, false),
        TileSpec(18, "rotation_control", "Force Rotation", "Display & Screen", "Cycles Auto, Portrait, Landscape, and Reverse Portrait directly.", "WriteSettings", "RotationControlTileService", false, false),
        TileSpec(19, "warm_filter", "Amber Night Filter", "Display & Screen", "Toggles a warm blue-light reducing overlay across all apps.", "Overlay", "WarmFilterTileService", false, false),
        TileSpec(20, "refresh_rate", "60Hz / 120Hz Switch", "Display & Screen", "Writes peak and min refresh rate directly for battery or smoothness.", "Root", "RefreshRateTileService", false, true),
        TileSpec(21, "sound_profile", "Sound Profile", "Audio & Media", "Cycles Normal, Vibrate, and Silent ringer profiles directly.", "DND", "SoundProfileTileService", true, false),
        TileSpec(22, "media_volume", "Media Volume Step", "Audio & Media", "Sets media stream volume directly to 0%, 30%, 70%, or 100%.", "Standard", "MediaVolumeTileService", false, false),
        TileSpec(23, "mute_except_alarms", "Mute Except Alarms", "Audio & Media", "Zeroes media, ring, and notification streams while keeping alarms.", "DND", "MuteExceptAlarmsTileService", false, false),
        TileSpec(24, "media_play_pause", "Play / Pause Media", "Audio & Media", "Dispatches global media play/pause key event directly.", "Standard", "MediaPlayPauseTileService", false, false),
        TileSpec(25, "media_next", "Next Track", "Audio & Media", "Skips to the next audio track from anywhere.", "Standard", "MediaNextTileService", false, false),
        TileSpec(26, "sleep_timer", "Media Sleep Timer", "Audio & Media", "Auto-pauses playback after 15m, 30m, or 60m.", "Standard", "SleepTimerTileService", false, false),
        TileSpec(27, "moto_audio_reset", "Fix Silent Audio", "Audio & Media", "Resets stuck Moto Dolby/media audio focus and restores stream routing.", "Standard", "MotoAudioResetTileService", false, false),
        TileSpec(28, "white_noise", "Focus White Noise", "Audio & Media", "Plays or stops an offline synthesized ambient focus loop.", "Standard", "WhiteNoiseTileService", false, false),
        TileSpec(29, "speak_clipboard", "Speak Clipboard", "Audio & Media", "Reads currently copied clipboard text aloud via on-device TTS.", "Standard", "SpeakClipboardTileService", false, false),
        TileSpec(30, "lock_screen", "Instant Lock", "Accessibility & Nav", "Locks the screen immediately via Accessibility without power key.", "Accessibility", "LockScreenTileService", false, false),
        TileSpec(31, "take_screenshot", "Instant Screenshot", "Accessibility & Nav", "Captures a system screenshot directly via Accessibility action.", "Accessibility", "TakeScreenshotTileService", false, false),
        TileSpec(32, "split_screen", "Toggle Split Screen", "Accessibility & Nav", "Enters or exits split-screen mode directly via Accessibility.", "Accessibility", "SplitScreenTileService", false, false),
        TileSpec(33, "last_app", "Last App Switch", "Accessibility & Nav", "Double-triggers Recents via Accessibility to snap to previous app.", "Accessibility", "LastAppTileService", false, false),
        TileSpec(34, "power_dialog", "Power Menu", "Accessibility & Nav", "Opens Android power dialog via Accessibility without power button.", "Accessibility", "PowerDialogTileService", false, false),
        TileSpec(35, "custom_app_1", "Quick App Slot 1", "Accessibility & Nav", "Launches your pinned primary app directly from the shade.", "Standard", "CustomApp1TileService", false, false),
        TileSpec(36, "custom_app_2", "Quick App Slot 2", "Accessibility & Nav", "Launches your pinned secondary app directly from the shade.", "Standard", "CustomApp2TileService", false, false),
        TileSpec(37, "custom_app_3", "Quick App Slot 3", "Accessibility & Nav", "Launches your pinned third app directly from the shade.", "Standard", "CustomApp3TileService", false, false),
        TileSpec(38, "pomodoro", "Pomodoro Timer", "Focus & Capture", "Starts or pauses a 25m focus countdown with live tile subtitle.", "Standard", "PomodoroTileService", true, false),
        TileSpec(39, "stopwatch", "Stopwatch", "Focus & Capture", "Starts or stops a quick elapsed timer directly on the tile.", "Standard", "StopwatchTileService", false, false),
        TileSpec(40, "quick_countdown", "Quick Countdown", "Focus & Capture", "Cycles 3m, 5m, 10m, and 15m quick countdown presets.", "Standard", "QuickCountdownTileService", false, false),
        TileSpec(41, "habit_counter", "Habit Counter", "Focus & Capture", "Increments a daily counter shown live in the tile subtitle.", "Standard", "HabitCounterTileService", false, false),
        TileSpec(42, "study_mode", "Study Mode", "Focus & Capture", "Arms App Lock, enables Silent DND, and starts Pomodoro in one tap.", "Accessibility", "StudyModeTileService", false, false),
        TileSpec(43, "quick_note", "Quick Note Box", "Focus & Capture", "Pops a floating note input box over your current app.", "Standard", "QuickNoteTileService", true, false),
        TileSpec(44, "clipboard_vault", "Clipboard Vault", "Focus & Capture", "Shows a floating picker of your saved text snippets.", "Standard", "ClipboardVaultTileService", false, false),
        TileSpec(45, "clear_clipboard", "Clear Clipboard", "Focus & Capture", "Wipes the Android system clipboard immediately.", "Standard", "ClearClipboardTileService", false, false),
        TileSpec(46, "quick_calc", "Floating Calc", "Focus & Capture", "Opens a compact floating calculator dialog over any app.", "Standard", "QuickCalcTileService", false, false),
        TileSpec(47, "battery_stats", "Battery Temp & mA", "System & Hardware", "Reads live battery temperature and charge/discharge current.", "Standard", "BatteryStatsTileService", true, false),
        TileSpec(48, "ram_monitor", "RAM Free", "System & Hardware", "Displays available RAM in GB right on the tile subtitle.", "Standard", "RamMonitorTileService", false, false),
        TileSpec(49, "storage_free", "Storage Free", "System & Hardware", "Displays free internal storage in GB at a glance.", "Standard", "StorageFreeTileService", false, false),
        TileSpec(50, "thermal_status", "Thermal State", "System & Hardware", "Shows live device thermal throttling status.", "Standard", "ThermalStatusTileService", false, false),
        TileSpec(51, "wifi_ip", "Copy Wi-Fi IP", "System & Hardware", "Displays local Wi-Fi IP in subtitle and copies it to clipboard.", "Standard", "WifiIpTileService", false, false),
        TileSpec(52, "haptic_toggle", "Touch Haptics", "System & Hardware", "Toggles system haptic feedback directly via WRITE_SETTINGS.", "WriteSettings", "HapticToggleTileService", false, false),
        TileSpec(53, "torch_toggle", "Flashlight Toggle", "System & Hardware", "Toggles camera LED torch directly with zero delay.", "Standard", "TorchToggleTileService", false, false),
        TileSpec(54, "sos_strobe", "SOS Strobe", "System & Hardware", "Pulses the rear LED in an emergency SOS pattern.", "Standard", "SosStrobeTileService", false, false),
        TileSpec(55, "pass_gen", "Password Gen", "System & Hardware", "Generates a secure 16-char password and copies it directly.", "Standard", "PassGenTileService", false, false),
        TileSpec(56, "private_dns", "AdGuard DNS Toggle", "Root & Automation", "Switches Private DNS directly between Off and dns.adguard-dns.com.", "Root", "PrivateDnsTileService", false, true),
        TileSpec(57, "adb_wireless", "Wireless ADB", "Root & Automation", "Toggles adb_wifi_enabled directly via root and shows IP:Port.", "Root", "AdbWirelessTileService", false, true),
        TileSpec(58, "anim_scale", "Animation Speed", "Root & Automation", "Cycles system animation scales (0x, 0.5x, 1x) directly via root.", "Root", "AnimScaleTileService", false, true),
        TileSpec(59, "show_touches", "Show Touches", "Root & Automation", "Toggles visual tap dots directly via settings put system.", "Root", "ShowTouchesTileService", false, true),
        TileSpec(60, "layout_bounds", "Layout Bounds", "Root & Automation", "Toggles developer UI layout bounds directly via setprop.", "Root", "LayoutBoundsTileService", false, true),
        TileSpec(61, "charge_limit", "80% Charge Cap", "Root & Automation", "Toggles battery charging suspension at 80% on supported kernels.", "Root", "ChargeLimitTileService", false, true),
        TileSpec(62, "immersive_mode", "Immersive Bars", "Root & Automation", "Hides status and navigation bars directly via policy_control.", "Root", "ImmersiveModeTileService", false, true),
        TileSpec(63, "clear_cache", "Trim All Caches", "Root & Automation", "Runs pm trim-caches directly via root to reclaim space.", "Root", "ClearCacheTileService", false, true),
        TileSpec(64, "reboot_menu", "Root Reboot Menu", "Root & Automation", "Floating dialog for Reboot, Recovery, Bootloader, or Soft Reboot.", "Root", "RebootMenuTileService", false, true),
        TileSpec(65, "custom_shell", "Custom Root Cmd", "Root & Automation", "Executes a custom root shell command and prints first output line.", "Root", "CustomShellTileService", false, true)
    )

    private val byId: Map<String, TileSpec> = allTiles.associateBy { it.id }
    fun findById(id: String): TileSpec? = byId[id]

    fun visibleTiles(hasRoot: Boolean): List<TileSpec> =
        if (hasRoot) allTiles else allTiles.filter { !it.requiresRoot }

    fun visibleCategories(hasRoot: Boolean): List<String> =
        listOf("All") + visibleTiles(hasRoot).map { it.category }.distinct()

    fun hiddenRootCount(): Int = allTiles.count { it.requiresRoot }
}
