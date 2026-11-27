package com.krishna.tiledeck.system

import android.content.Context
import android.content.Intent
import com.krishna.tiledeck.data.TilePrefs
import java.util.concurrent.TimeUnit

object MotoTools {

    private val motoSecureCandidates = listOf(
        "com.motorola.securevault",
        "com.motorola.spaces",
        "com.motorola.securityhub",
        "com.motorola.motosecure"
    )

    /**
     * Triggers the actual KernelSU / Magisk SU prompt (`su -c id`) and synchronizes
     * root tile visibility immediately: if denied or unavailable, all root tiles are hidden.
     */
    fun requestRootAndSync(context: Context): Boolean {
        TilePrefs.markRootRequestedOnce(context)
        val granted = runCatching {
            val p = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
            val finished = p.waitFor(8, TimeUnit.SECONDS)
            if (!finished) {
                p.destroyForcibly()
                false
            } else {
                val out = p.inputStream.bufferedReader().readText()
                p.exitValue() == 0 && out.contains("uid=0")
            }
        }.getOrDefault(false)
        TilePrefs.syncRootTileVisibility(context.applicationContext, granted)
        return granted
    }

    fun runRoot(cmd: String): Pair<Boolean, String> = runCatching {
        val p = ProcessBuilder("su", "-c", cmd).start()
        val out = p.inputStream.bufferedReader().readText().trim()
        val err = p.errorStream.bufferedReader().readText().trim()
        val code = p.waitFor()
        (code == 0) to (out.ifBlank { err }.ifBlank { if (code == 0) "OK" else "Denied" })
    }.getOrElse { false to "No Root" }

    fun findMotoSecurePackage(context: Context): String? {
        val pm = context.packageManager
        for (pkg in motoSecureCandidates) {
            if (runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess) return pkg
        }
        return null
    }

    fun openMotoSecureFolder(context: Context): Pair<Boolean, String> {
        val pkg = findMotoSecurePackage(context) ?: "com.motorola.securevault"
        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            return true to "Opened"
        }
        if (TilePrefs.isRootGranted(context)) {
            runRoot("pm unhide $pkg; pm enable $pkg")
            val (ok, _) = runRoot("monkey -p $pkg -c android.intent.category.LAUNCHER 1")
            return ok to if (ok) "Launched" else "Not Found"
        }
        return false to "Hidden (Need SU)"
    }

    fun toggleMotoSecureVisibility(context: Context): Pair<Boolean, String> {
        val pkg = findMotoSecurePackage(context) ?: "com.motorola.securevault"
        val currentlyHidden = TilePrefs.isTileActive(context, "moto_secure_toggle")
        val hideNext = !currentlyHidden
        val cmd = if (hideNext) "pm hide $pkg || pm disable-user --user 0 $pkg" else "pm unhide $pkg; pm enable $pkg"
        val (ok, msg) = runRoot(cmd)
        if (!ok) return currentlyHidden to msg.take(18)
        return hideNext to if (hideNext) "Hidden" else "Visible"
    }

    fun toggleHiddenAppTray(context: Context): Pair<Boolean, String> {
        val pkgs = TilePrefs.getHiddenTrayPackages(context)
        if (pkgs.isEmpty()) return false to "Select Apps"
        val hideNext = !TilePrefs.isHiddenTrayActive(context)
        val cmds = pkgs.joinToString("; ") { pkg ->
            if (hideNext) "pm hide $pkg || pm disable-user --user 0 $pkg" else "pm unhide $pkg; pm enable $pkg"
        }
        val (ok, msg) = runRoot(cmds)
        if (!ok) return TilePrefs.isHiddenTrayActive(context) to msg.take(18)
        TilePrefs.setHiddenTrayActive(context, hideNext)
        return hideNext to if (hideNext) "Hidden (${pkgs.size})" else "Shown (${pkgs.size})"
    }

    fun applyMotoKeepAlive(context: Context): Pair<Boolean, String> {
        val targets = (TilePrefs.getLockedPackages(context) + context.packageName + "com.amazon.mp3").distinct()
        val cmd = targets.joinToString("; ") { pkg ->
            "dumpsys deviceidle whitelist +$pkg; am set-standby-bucket $pkg exempted"
        }
        val (ok, msg) = runRoot(cmd)
        return ok to if (ok) "Exempt (${targets.size})" else msg.take(18)
    }

    fun switchUserProfile(context: Context): Pair<Boolean, String> {
        val (ok, out) = runRoot("pm list users")
        if (!ok) return false to "Need Root"
        val userIds = Regex("""UserInfo\{(\d+):""").findAll(out).map { it.groupValues[1].toInt() }.toList()
        if (userIds.size < 2) return false to "1 User Only"
        val idx = (TilePrefs.getCounter(context, "switch_user_profile") + 1) % userIds.size
        TilePrefs.setCounter(context, "switch_user_profile", idx)
        val target = userIds[idx]
        val (swOk, _) = runRoot("am switch-user $target")
        return swOk to if (swOk) "User $target" else "Switch Err"
    }
}
