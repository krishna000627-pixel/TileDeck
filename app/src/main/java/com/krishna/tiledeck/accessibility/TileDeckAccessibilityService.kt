package com.krishna.tiledeck.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.krishna.tiledeck.data.TilePrefs
import com.krishna.tiledeck.ui.AppLockActivity

class TileDeckAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingRelockRunnable: Runnable? = null

    private val transientSystemPackages = setOf(
        "android",
        "com.android.systemui",
        "com.android.documentsui",
        "com.google.android.documentsui",
        "com.android.providers.media",
        "com.android.providers.media.module",
        "com.google.android.providers.media.module",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.intentresolver",
        "com.google.android.gms",
        "com.android.camera",
        "com.motorola.camera3"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (pkg != packageName && pkg !in transientSystemPackages) {
                lastForegroundPackage = pkg
            }
            if (!TilePrefs.isAppLockArmed(applicationContext)) return

            val isIme = pkg.contains("inputmethod", true) || pkg.contains("keyboard", true) || pkg.contains("gboard", true) || pkg.contains("swiftkey", true)
            val locked = TilePrefs.getLockedPackages(applicationContext)
            when {
                // Returning to currently unlocked app -> cancel any pending 3s relock timer
                pkg == unlockedSessionPkg -> {
                    cancelRelockTimer()
                }
                // Transient file/photo picker, keyboard, or TileDeck itself -> keep current unlock alive
                pkg in transientSystemPackages || isIme || pkg == packageName -> {
                    // Do not clear unlockedSessionPkg
                }
                // Entered a locked app that is not unlocked -> launch isolated lock screen (with debounce)
                pkg in locked -> {
                    cancelRelockTimer()
                    val now = System.currentTimeMillis()
                    if (!isLockScreenShowing && (now - lastLockLaunchMs) > 600L) {
                        isLockScreenShowing = true
                        lastLockLaunchMs = now
                        val i = Intent(applicationContext, AppLockActivity::class.java).apply {
                            putExtra("target_pkg", pkg)
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                                    Intent.FLAG_ACTIVITY_NO_ANIMATION
                            )
                        }
                        startActivity(i)
                    }
                }
                // Left the unlocked app to another regular app -> wait 3 seconds before re-locking
                unlockedSessionPkg != null -> {
                    scheduleRelockAfter3s()
                }
            }
        }
    }

    private fun scheduleRelockAfter3s() {
        cancelRelockTimer()
        val task = Runnable { unlockedSessionPkg = null }
        pendingRelockRunnable = task
        handler.postDelayed(task, 3000L)
    }

    private fun cancelRelockTimer() {
        pendingRelockRunnable?.let { handler.removeCallbacks(it) }
        pendingRelockRunnable = null
    }

    override fun onInterrupt() {}
    override fun onDestroy() {
        cancelRelockTimer()
        super.onDestroy()
        if (instance === this) instance = null
    }

    fun lockScreenNow(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) else false
    fun takeScreenshotNow(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT) else false
    fun toggleSplitScreenNow(): Boolean = performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
    fun openPowerDialogNow(): Boolean = performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
    fun pressHomeNow(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun pressBackNow(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun openNotificationsNow(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun openRecentsNow(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    fun switchToLastApp(): Boolean {
        val ok = performGlobalAction(GLOBAL_ACTION_RECENTS)
        handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_RECENTS) }, 220)
        return ok
    }

    fun clearAllRecents(): Boolean {
        val ok = performGlobalAction(GLOBAL_ACTION_RECENTS)
        handler.postDelayed({
            val root = rootInActiveWindow ?: return@postDelayed
            findClearAllNode(root)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }, 420)
        return ok
    }

    private fun findClearAllNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val t = node.text?.toString()?.lowercase() ?: ""
        val d = node.contentDescription?.toString()?.lowercase() ?: ""
        if (t.contains("clear all") || d.contains("clear all") || t == "clear") {
            return if (node.isClickable) node else node.parent
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findClearAllNode(child)?.let { return it }
        }
        return null
    }

    companion object {
        @Volatile var instance: TileDeckAccessibilityService? = null
        @Volatile var lastForegroundPackage: String? = null
        @Volatile var unlockedSessionPkg: String? = null
        @Volatile var isLockScreenShowing: Boolean = false
        @Volatile var lastLockLaunchMs: Long = 0L
    }
}
