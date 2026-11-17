package com.example.growwlock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/**
 * Two jobs:
 *  1. Watches which app is on screen. If Groww opens while you are locked, it sends you to the
 *     home screen and shows the "Limit reached" screen.
 *  2. Runs a background loop that reads your Groww orders (every 3 s while Groww is open,
 *     every 20 s otherwise) and updates the lock.
 * Android starts this service by itself after a phone restart once you have turned it on.
 */
class BlockerService : AccessibilityService() {

    @Volatile
    private var running = false
    private var worker: Thread? = null
    private var lastBlock = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = true
        worker = Thread {
            while (running) {
                try {
                    Monitor.checkOnce(applicationContext)
                } catch (e: Exception) {
                    // never let the loop die
                }
                try {
                    Thread.sleep(if (AppState.growwInFront) 3_000L else 20_000L)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }.also {
            it.isDaemon = true
            it.start()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        if (pkg == Prefs.growwPackage(this)) {
            AppState.growwInFront = true
            if (Prefs.isLocked(this)) block()
        } else if (!pkg.contains("inputmethod") && pkg != "com.android.systemui") {
            AppState.growwInFront = false
        }
    }

    private fun block() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlock < 1_500L) return
        lastBlock = now
        performGlobalAction(GLOBAL_ACTION_HOME)
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val i = Intent(this, LockActivity::class.java)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(i)
            } catch (e: Exception) {
                // if Android refuses, you are still on the home screen, so Groww is still blocked
            }
        }, 200L)
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        running = false
        worker?.interrupt()
        return super.onUnbind(intent)
    }
}
