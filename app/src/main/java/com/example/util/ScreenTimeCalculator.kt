package com.example.util

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.view.inputmethod.InputMethodManager
import java.util.Calendar

data class ScreenTimeResult(
    val totalScreenTimeMillis: Long,
    val appUsageMap: Map<String, Long> // packageName -> usageMillis
)

object ScreenTimeCalculator {

    private val EXCLUDED_PACKAGES = setOf(
        "com.android.systemui",
        "com.google.android.apps.nexuslauncher",
        "com.android.launcher",
        "com.android.launcher3",
        "com.nothing.launcher",
        "com.sec.android.app.launcher",
        "com.miui.home",
        "com.huawei.android.launcher",
        "com.oppo.launcher",
        "com.oneplus.launcher"
    )

    fun getTodayStartMillis(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun getWeekStartMillis(): Long {
        return Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -6)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun getScreenTimeData(
        context: Context,
        startTime: Long,
        endTime: Long = System.currentTimeMillis()
    ): ScreenTimeResult {
        if (!PermissionUtils.hasUsageStatsPermission(context)) {
            return ScreenTimeResult(0L, emptyMap())
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return ScreenTimeResult(0L, emptyMap())

        val pm = context.packageManager
        val ourPkg = context.packageName

        // Resolve default home launcher
        val homeIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val defaultLauncher = try {
            pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        } catch (e: Exception) {
            null
        }

        // Identify installed keyboards/IMEs to exclude them from user app screen time
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val imePackages = try {
            imm?.inputMethodList?.map { it.packageName }?.toSet() ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }

        // 1. Query aggregated stats (built-in Android method matching system settings)
        val aggregatedMap = try {
            usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
        } catch (e: Exception) {
            emptyMap<String, UsageStats>()
        }

        // 2. Query daily interval buckets
        val dailyStatsList = try {
            usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        } catch (e: Exception) {
            emptyList<UsageStats>()
        }

        // Sum daily interval buckets that were active within the queried window
        val dailySumMap = mutableMapOf<String, Long>()
        for (stat in dailyStatsList) {
            if (stat.lastTimeStamp < startTime) continue
            val pkg = stat.packageName ?: continue
            val foreground = stat.totalTimeInForeground
            if (foreground > 0) {
                dailySumMap[pkg] = (dailySumMap[pkg] ?: 0L) + foreground
            }
        }

        // Combine all candidate packages from both queries
        val candidatePackages = aggregatedMap.keys + dailySumMap.keys
        val finalAppUsageMap = mutableMapOf<String, Long>()

        for (pkg in candidatePackages) {
            if (!isValidTrackableApp(pm, pkg, ourPkg, defaultLauncher, imePackages)) {
                continue
            }

            val fromAggregated = aggregatedMap[pkg]?.totalTimeInForeground ?: 0L
            val fromDaily = dailySumMap[pkg] ?: 0L
            val bestDuration = maxOf(fromAggregated, fromDaily)

            if (bestDuration > 0L) {
                finalAppUsageMap[pkg] = bestDuration
            }
        }

        val maxPossible = (endTime - startTime).coerceAtLeast(0L)
        val clampedAppUsage = finalAppUsageMap.mapValues { (_, dur) -> dur.coerceIn(0L, maxPossible) }
        val totalScreenTime = clampedAppUsage.values.sum().coerceIn(0L, maxPossible)

        return ScreenTimeResult(totalScreenTime, clampedAppUsage)
    }

    private fun isValidTrackableApp(
        pm: PackageManager,
        pkg: String,
        ourPkg: String,
        defaultLauncher: String?,
        imePackages: Set<String>
    ): Boolean {
        if (pkg == ourPkg || pkg == defaultLauncher || EXCLUDED_PACKAGES.contains(pkg) || imePackages.contains(pkg)) {
            return false
        }
        if (pkg == "com.android.systemui") return false

        return try {
            // Check if app has a launcher activity (user app)
            if (pm.getLaunchIntentForPackage(pkg) != null) {
                return true
            }
            // Or if it's a non-system user-installed app
            val appInfo = pm.getApplicationInfo(pkg, 0)
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            !isSystem
        } catch (e: Exception) {
            false
        }
    }
}
