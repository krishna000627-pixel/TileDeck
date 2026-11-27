package com.krishna.tiledeck.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.service.quicksettings.TileService

object TilePrefs {
    private const val PREF_NAME = "tiledeck_v8_state"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isTileActive(context: Context, tileId: String): Boolean =
        prefs(context).getBoolean("active_$tileId", false)

    fun setTileActive(context: Context, tileId: String, active: Boolean) {
        prefs(context).edit().putBoolean("active_$tileId", active).apply()
    }

    fun getSubtitle(context: Context, tileId: String, fallback: String = "Off"): String =
        prefs(context).getString("sub_$tileId", fallback) ?: fallback

    fun setSubtitle(context: Context, tileId: String, subtitle: String) {
        prefs(context).edit().putString("sub_$tileId", subtitle).apply()
    }

    fun getCounter(context: Context, tileId: String): Int =
        prefs(context).getInt("cnt_$tileId", 0)

    fun setCounter(context: Context, tileId: String, value: Int) {
        prefs(context).edit().putInt("cnt_$tileId", value).apply()
    }

    fun isAppLockArmed(context: Context): Boolean =
        prefs(context).getBoolean("applock_armed", false)

    fun setAppLockArmed(context: Context, armed: Boolean) {
        prefs(context).edit().putBoolean("applock_armed", armed).apply()
    }

    fun getAppLockPin(context: Context): String =
        prefs(context).getString("applock_pin", "1234") ?: "1234"

    fun setAppLockPin(context: Context, pin: String) {
        if (pin.length >= 4) prefs(context).edit().putString("applock_pin", pin).apply()
    }

    fun getLockedPackages(context: Context): Set<String> =
        prefs(context).getStringSet("locked_pkgs", emptySet())?.toSet() ?: emptySet()

    fun setPackageLocked(context: Context, pkg: String, locked: Boolean) {
        val next = getLockedPackages(context).toMutableSet()
        if (locked) next.add(pkg) else next.remove(pkg)
        prefs(context).edit().putStringSet("locked_pkgs", next).apply()
    }

    fun getCustomAppSlot(context: Context, slot: Int): String =
        prefs(context).getString("custom_app_$slot", "") ?: ""

    fun setCustomAppSlot(context: Context, slot: Int, pkg: String) {
        prefs(context).edit().putString("custom_app_$slot", pkg).apply()
    }

    fun isDarkTheme(context: Context): Boolean =
        prefs(context).getBoolean("dark_theme", true)

    fun setDarkTheme(context: Context, dark: Boolean) {
        prefs(context).edit().putBoolean("dark_theme", dark).apply()
    }

    fun isTileEnabledInPanel(context: Context, spec: TileSpec): Boolean {
        val cn = ComponentName(context.packageName, "${context.packageName}.tiles.${spec.serviceClassName}")
        return when (context.packageManager.getComponentEnabledSetting(cn)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            else -> spec.defaultEnabled
        }
    }

    fun setTileEnabledInPanel(context: Context, spec: TileSpec, enabled: Boolean) {
        val cn = ComponentName(context.packageName, "${context.packageName}.tiles.${spec.serviceClassName}")
        val newState = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        context.packageManager.setComponentEnabledSetting(cn, newState, PackageManager.DONT_KILL_APP)
        if (enabled) runCatching { TileService.requestListeningState(context, cn) }
    }
}
