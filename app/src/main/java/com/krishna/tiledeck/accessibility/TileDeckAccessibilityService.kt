package com.krishna.tiledeck.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.krishna.tiledeck.data.TilePrefs
import com.krishna.tiledeck.ui.AppLockOverlayActivity

class TileDeckAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (pkg != packageName && pkg != "com.android.systemui") {
                lastForegroundPackage = pkg
            }
            if (TilePrefs.isAppLockArmed(applicationContext)) {
                val locked = TilePrefs.getLockedPackages(applicationContext)
                if (pkg in locked && pkg != unlockedSessionPkg) {
                    val lockIntent = Intent(applicationContext, AppLockOverlayActivity::class.java).apply {
                        putExtra("target_pkg", pkg)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    }
                    startActivity(lockIntent)
                } else if (pkg !in locked && pkg != packageName && pkg != "com.android.systemui") {
                    unlockedSessionPkg = null
                }
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
    }

    fun lockScreenNow(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) else false

    fun takeScreenshotNow(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT) else false

    fun toggleSplitScreenNow(): Boolean = performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
    fun openPowerDialogNow(): Boolean = performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)

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
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (text.contains("clear all") || desc.contains("clear all") || text == "clear") {
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
    }
}
