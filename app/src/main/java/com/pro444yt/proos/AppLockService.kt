package com.pro444yt.proos

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class AppLockService : AccessibilityService() {
    companion object {
        var unlockedSessionPackage: String? = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            if (pkg == "com.android.systemui" || pkg.contains("inputmethod") || pkg.contains("keyboard")) return

            // If user navigated away from the previously unlocked app, re-lock it immediately
            if (unlockedSessionPackage != null && pkg != unlockedSessionPackage) {
                unlockedSessionPackage = null
            }

            if (pkg == packageName) return

            if (OSController.lockedPackages.contains(pkg) && unlockedSessionPackage != pkg) {
                val lockIntent = Intent(this, LauncherActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("LOCKED_PACKAGE", pkg)
                }
                startActivity(lockIntent)
            }
        }
    }

    override fun onInterrupt() {}
}
