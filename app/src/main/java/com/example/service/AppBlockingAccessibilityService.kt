package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.example.data.DailyDatabase
import com.example.ui.screens.LockOverlayActivity
import com.example.util.AppSettings
import com.example.util.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

class AppBlockingAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastBlockedPackage: String? = null
    private var lastBlockTime: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        val packageName = event.packageName?.toString() ?: return

        // Never block our own app or common system launchers
        if (packageName == applicationContext.packageName ||
            packageName == "com.android.systemui" ||
            packageName == "com.google.android.apps.nexuslauncher" ||
            packageName == "com.android.launcher" ||
            packageName == "com.nothing.launcher"
        ) {
            return
        }

        val appSettings = AppSettings.getInstance(applicationContext)
        val blockedPackages = appSettings.blockedAppPackages.value

        if (!blockedPackages.contains(packageName)) {
            return
        }

        // Debounce: prevent spamming overlay within 1.5 seconds for the same app
        val now = System.currentTimeMillis()
        if (packageName == lastBlockedPackage && (now - lastBlockTime) < 1500L) {
            return
        }

        serviceScope.launch {
            val shouldBlock = checkShouldBlock(appSettings)
            if (shouldBlock) {
                lastBlockedPackage = packageName
                lastBlockTime = now
                launchLockOverlay(packageName)
            }
        }
    }

    private fun checkShouldBlock(appSettings: AppSettings): Boolean {
        return try {
            val db = DailyDatabase.getInstance(applicationContext)
            val todayKey = DateUtils.getTodayKey()
            val activeTasks = db.dailyTaskDao().getAllActiveTasksSync()
            if (activeTasks.isEmpty()) return false

            val completions = db.dailyTaskDao().getCompletionsForDateSync(todayKey)
            val completedIds = completions.map { it.taskId }.toSet()

            val openTasksCount = activeTasks.count { !completedIds.contains(it.id) }
            if (openTasksCount == 0) {
                // All habits completed! Apps are unlocked!
                return false
            }

            // Check if deadline mode is enabled
            val ruleMode = appSettings.blockRuleMode.value
            if (ruleMode == "DEADLINE") {
                val deadlineHour = appSettings.blockDeadlineHour.value
                val deadlineMinute = appSettings.blockDeadlineMinute.value

                val calendar = Calendar.getInstance()
                val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
                val currentMinute = calendar.get(Calendar.MINUTE)

                val currentTimeMinutes = currentHour * 60 + currentMinute
                val deadlineMinutes = deadlineHour * 60 + deadlineMinute

                // If currently before deadline, don't lock yet
                if (currentTimeMinutes < deadlineMinutes) {
                    return false
                }
            }

            // Uncompleted tasks exist and rule applies -> block!
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun launchLockOverlay(blockedPackage: String) {
        val intent = Intent(this, LockOverlayActivity::class.java).apply {
            putExtra(LockOverlayActivity.EXTRA_BLOCKED_PACKAGE, blockedPackage)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    override fun onInterrupt() {
        // Accessibility service interrupted
    }
}
