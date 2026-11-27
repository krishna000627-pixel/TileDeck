package com.krishna.tiledeck.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.service.quicksettings.TileService

object TilePrefs {
    private const val PREF_NAME = "tiledeck_v3_state"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isRootGranted(context: Context): Boolean =
        prefs(context).getBoolean("su_granted", false)

    fun setRootGranted(context: Context, granted: Boolean) {
        prefs(context).edit().putBoolean("su_granted", granted).apply()
    }

    fun hasRequestedRootOnce(context: Context): Boolean =
        prefs(context).getBoolean("su_prompted_once", false)

    fun markRootRequestedOnce(context: Context) {
        prefs(context).edit().putBoolean("su_prompted_once", true).apply()
    }

    /**
     * Automatically hides all root-required TileServices from the Android Quick Settings
     * panel when SU is not granted, and restores user/default root tiles when SU is granted.
     */
    fun syncRootTileVisibility(context: Context, hasRoot: Boolean) {
        setRootGranted(context, hasRoot)
        TileCatalog.allTiles.filter { it.requiresRoot }.forEach { spec ->
            if (!hasRoot) {
                setComponentEnabledRaw(context, spec, false)
            } else {
                val wantEnabled = prefs(context).getBoolean("user_en_${spec.id}", spec.defaultEnabled)
                setComponentEnabledRaw(context, spec, wantEnabled)
            }
        }
    }

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

    fun getHiddenTrayPackages(context: Context): Set<String> =
        prefs(context).getStringSet("hidden_tray_pkgs", emptySet())?.toSet() ?: emptySet()

    fun setPackageInHiddenTray(context: Context, pkg: String, inTray: Boolean) {
        val next = getHiddenTrayPackages(context).toMutableSet()
        if (inTray) next.add(pkg) else next.remove(pkg)
        prefs(context).edit().putStringSet("hidden_tray_pkgs", next).apply()
    }

    fun isHiddenTrayActive(context: Context): Boolean =
        prefs(context).getBoolean("hidden_tray_active", false)

    fun setHiddenTrayActive(context: Context, hidden: Boolean) {
        prefs(context).edit().putBoolean("hidden_tray_active", hidden).apply()
    }

    fun getCustomAppSlot(context: Context, slot: Int): String =
        prefs(context).getString("custom_app_$slot", "") ?: ""

    fun setCustomAppSlot(context: Context, slot: Int, pkg: String) {
        prefs(context).edit().putString("custom_app_$slot", pkg).apply()
    }

    fun getQuickNotes(context: Context): List<String> {
        val raw = prefs(context).getString("quick_notes", "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split("\n---\n")
    }

    fun addQuickNote(context: Context, note: String) {
        val updated = (listOf(note.trim()) + getQuickNotes(context)).take(30)
        prefs(context).edit().putString("quick_notes", updated.joinToString("\n---\n")).apply()
    }

    fun isDarkTheme(context: Context): Boolean =
        prefs(context).getBoolean("dark_theme", true)

    fun setDarkTheme(context: Context, dark: Boolean) {
        prefs(context).edit().putBoolean("dark_theme", dark).apply()
    }

    fun isTileEnabledInPanel(context: Context, spec: TileSpec): Boolean {
        if (spec.requiresRoot && !isRootGranted(context)) return false
        val cn = ComponentName(context.packageName, "${context.packageName}.tiles.${spec.serviceClassName}")
        val state = context.packageManager.getComponentEnabledSetting(cn)
        return when (state) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            else -> spec.defaultEnabled && !spec.requiresRoot
        }
    }

    fun setTileEnabledInPanel(context: Context, spec: TileSpec, enabled: Boolean) {
        if (spec.requiresRoot && !isRootGranted(context)) {
            setComponentEnabledRaw(context, spec, false)
            return
        }
        prefs(context).edit().putBoolean("user_en_${spec.id}", enabled).apply()
        setComponentEnabledRaw(context, spec, enabled)
    }

    private fun setComponentEnabledRaw(context: Context, spec: TileSpec, enabled: Boolean) {
        val cn = ComponentName(context.packageName, "${context.packageName}.tiles.${spec.serviceClassName}")
        val newState = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        context.packageManager.setComponentEnabledSetting(cn, newState, PackageManager.DONT_KILL_APP)
        if (enabled) {
            runCatching { TileService.requestListeningState(context, cn) }
        }
    }
}
