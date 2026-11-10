package com.example.usage.source

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.UserManager
import android.view.inputmethod.InputMethodManager
import com.example.usage.engine.PackageClassifier
import com.example.usage.model.ExclusionReason
import com.example.usage.model.MeasurementStatus
import com.example.usage.model.RawUsageEvent
import com.example.usage.model.UsageEventType
import com.example.util.PermissionUtils

/**
 * Result of querying the system event source.
 */
data class EventQueryResponse(
    val availabilityStatus: MeasurementStatus,
    val events: List<RawUsageEvent>
)

/**
 * Abstraction over Android's [UsageStatsManager.queryEvents] so that repositories can be tested
 * deterministically without requiring a real system service.
 */
interface UsageEventSource {
    fun queryRawEvents(startEpochMs: Long, endEpochMs: Long): EventQueryResponse
    fun createPackageClassifier(): PackageClassifier
}

/**
 * Production implementation backed by Android's [UsageStatsManager.queryEvents].
 */
class AndroidUsageEventSource(
    private val context: Context
) : UsageEventSource {

    companion object {
        private val KNOWN_LAUNCHER_PACKAGES = setOf(
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher",
            "com.android.launcher3",
            "com.nothing.launcher",
            "com.sec.android.app.launcher",
            "com.miui.home",
            "com.huawei.android.launcher",
            "com.oppo.launcher",
            "com.oneplus.launcher",
            "com.teslacoilsw.launcher",
            "bitpit.launcher"
        )

        private val KNOWN_SYSTEM_UI_PACKAGES = setOf(
            "com.android.systemui",
            "com.android.settings.intelligence",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller"
        )
    }

    override fun queryRawEvents(startEpochMs: Long, endEpochMs: Long): EventQueryResponse {
        if (!PermissionUtils.hasUsageStatsPermission(context)) {
            return EventQueryResponse(
                availabilityStatus = MeasurementStatus.PERMISSION_MISSING,
                events = emptyList()
            )
        }

        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
        if (userManager != null && !userManager.isUserUnlocked) {
            return EventQueryResponse(
                availabilityStatus = MeasurementStatus.TEMPORARILY_UNAVAILABLE,
                events = emptyList()
            )
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return EventQueryResponse(
                availabilityStatus = MeasurementStatus.TEMPORARILY_UNAVAILABLE,
                events = emptyList()
            )

        if (endEpochMs <= startEpochMs) {
            return EventQueryResponse(
                availabilityStatus = MeasurementStatus.VALID_ZERO,
                events = emptyList()
            )
        }

        val usageEvents: UsageEvents? = try {
            usageStatsManager.queryEvents(startEpochMs, endEpochMs)
        } catch (_: Exception) {
            null
        }

        if (usageEvents == null) {
            return EventQueryResponse(
                availabilityStatus = MeasurementStatus.TEMPORARILY_UNAVAILABLE,
                events = emptyList()
            )
        }

        val rawList = ArrayList<RawUsageEvent>(256)
        val reusableEvent = UsageEvents.Event()
        var seq = 0

        try {
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(reusableEvent)
                val normalizedType = UsageEventType.fromFrameworkCode(reusableEvent.eventType)
                if (normalizedType != UsageEventType.OTHER) {
                    rawList.add(
                        RawUsageEvent(
                            timestampMs = reusableEvent.timeStamp,
                            eventType = normalizedType,
                            packageName = reusableEvent.packageName ?: "",
                            className = reusableEvent.className ?: "",
                            sequenceIndex = seq++
                        )
                    )
                } else {
                    seq++
                }
            }
        } catch (e: Exception) {
            return EventQueryResponse(
                availabilityStatus = MeasurementStatus.TEMPORARILY_UNAVAILABLE,
                events = emptyList()
            )
        }

        val status = if (rawList.isEmpty()) MeasurementStatus.VALID_ZERO else MeasurementStatus.VALID_USAGE
        return EventQueryResponse(
            availabilityStatus = status,
            events = rawList
        )
    }

    override fun createPackageClassifier(): PackageClassifier {
        val pm = context.packageManager
        val ourPkg = context.packageName

        val homeLaunchers = HashSet<String>(KNOWN_LAUNCHER_PACKAGES)
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
            pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo
                ?.packageName
                ?.let { homeLaunchers.add(it) }

            val allHomeActivities = pm.queryIntentActivities(homeIntent, 0)
            for (ri in allHomeActivities) {
                ri.activityInfo?.packageName?.let { homeLaunchers.add(it) }
            }
        } catch (_: Exception) {
        }

        val imePackages = HashSet<String>()
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.inputMethodList?.forEach { info ->
                imePackages.add(info.packageName)
            }
        } catch (_: Exception) {
        }

        // Cache per-package classification for fast lookups during event stream traversal
        val classificationCache = HashMap<String, ExclusionReason?>()

        return PackageClassifier { packageName, className ->
            if (packageName.isBlank()) return@PackageClassifier ExclusionReason.NON_TRACKABLE_SYSTEM_PACKAGE

            // Explicitly exclude our own app and specifically our LockOverlayActivity
            if (packageName == ourPkg || className.contains("LockOverlayActivity")) {
                return@PackageClassifier ExclusionReason.OWN_APP_OR_LOCK_OVERLAY
            }
            if (KNOWN_SYSTEM_UI_PACKAGES.contains(packageName)) {
                return@PackageClassifier ExclusionReason.SYSTEM_UI
            }
            if (homeLaunchers.contains(packageName)) {
                return@PackageClassifier ExclusionReason.LAUNCHER_OR_HOME
            }
            if (imePackages.contains(packageName)) {
                return@PackageClassifier ExclusionReason.INPUT_METHOD_EDITOR
            }

            if (classificationCache.containsKey(packageName)) {
                return@PackageClassifier classificationCache[packageName]
            }

            val computedReason: ExclusionReason? = try {
                if (pm.getLaunchIntentForPackage(packageName) != null) {
                    null // Valid user-facing launchable app
                } else {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    if (isSystem && !isUpdatedSystem) {
                        ExclusionReason.NON_TRACKABLE_SYSTEM_PACKAGE
                    } else {
                        null
                    }
                }
            } catch (_: Exception) {
                // If an app was uninstalled after usage or package info is not visible,
                // keep it if it is not a core android.* system package
                if (packageName.startsWith("com.android.providers.") ||
                    packageName == "android"
                ) {
                    ExclusionReason.NON_TRACKABLE_SYSTEM_PACKAGE
                } else {
                    null
                }
            }

            classificationCache[packageName] = computedReason
            computedReason
        }
    }
}
