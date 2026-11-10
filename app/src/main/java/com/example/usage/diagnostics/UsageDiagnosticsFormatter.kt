package com.example.usage.diagnostics

import android.os.Build
import com.example.usage.model.UsageDiagnosticsReport
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Formats a [UsageDiagnosticsReport] into a human- and machine-readable local diagnostic text
 * for explicit user inspection or clipboard/share export.
 *
 * Privacy guarantee:
 * - Only contains timestamps, event types, package names, activity class names, and calculated interval durations.
 * - Never includes notification text, window content, URLs, or personal habit titles.
 */
object UsageDiagnosticsFormatter {

    private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    private fun fmtTime(epochMs: Long, zoneId: ZoneId): String {
        if (epochMs <= 0L) return "0"
        return try {
            Instant.ofEpochMilli(epochMs).atZone(zoneId).format(TIME_FMT)
        } catch (_: Exception) {
            epochMs.toString()
        }
    }

    fun formatReportForExport(
        report: UsageDiagnosticsReport,
        hasUsagePermission: Boolean,
        isAccessibilityEnabled: Boolean
    ): String {
        val zone = try {
            ZoneId.of(report.zoneId)
        } catch (_: Exception) {
            ZoneId.systemDefault()
        }

        val sb = StringBuilder(4096)
        sb.appendLine("=== MORE2DAY SCREEN TIME DIAGNOSTICS ===")
        sb.appendLine("AlgorithmVersion: v${report.algorithmVersion}")
        sb.appendLine("Android SDK: ${Build.VERSION.SDK_INT} (${Build.MANUFACTURER} ${Build.MODEL})")
        sb.appendLine("ZoneId: ${report.zoneId}")
        sb.appendLine("UsageStatsPermission: $hasUsagePermission")
        sb.appendLine("AccessibilityEnabled: $isAccessibilityEnabled")
        sb.appendLine("MeasurementStatus: ${report.status.name}")
        sb.appendLine("QueryLookbackStart: ${fmtTime(report.queryLookbackStartMs, zone)} (${report.queryLookbackStartMs})")
        sb.appendLine("TargetWindowStart:  ${fmtTime(report.targetWindowStartMs, zone)} (${report.targetWindowStartMs})")
        sb.appendLine("TargetWindowEnd:    ${fmtTime(report.targetWindowEndMs, zone)} (${report.targetWindowEndMs})")
        sb.appendLine()

        sb.appendLine("--- UNROUNDED AGGREGATES (MS) ---")
        sb.appendLine("InteractiveDeviceMs: ${report.unroundedInteractiveDeviceMs} ms")
        sb.appendLine("UnlockedDeviceMs:    ${report.unroundedUnlockedDeviceMs} ms")
        val totalAppFgMs = report.unroundedAppForegroundMs.values.sum()
        sb.appendLine("TotalAppForegroundMs: $totalAppFgMs ms")
        sb.appendLine("OngoingProjection:   ${report.ongoingProjectionPackage ?: "none"} (${report.ongoingProjectionDurationMs} ms)")
        sb.appendLine()

        sb.appendLine("--- PER-APP FOREGROUND (UNROUNDED MS) ---")
        if (report.unroundedAppForegroundMs.isEmpty()) {
            sb.appendLine("(none)")
        } else {
            report.unroundedAppForegroundMs.entries
                .sortedByDescending { it.value }
                .forEach { (pkg, ms) ->
                    sb.appendLine("  $pkg: $ms ms (${ms / 1000}s)")
                }
        }
        sb.appendLine()

        sb.appendLine("--- RECONSTRUCTED INTERVALS (${report.reconstructedIntervals.size}) ---")
        if (report.reconstructedIntervals.isEmpty()) {
            sb.appendLine("(none)")
        } else {
            report.reconstructedIntervals.takeLast(120).forEach { interval ->
                val tag = if (interval.isOngoingProjection) " [ONGOING]" else ""
                sb.appendLine(
                    "  [${interval.sessionType.name}] ${interval.packageName} (${interval.className}) " +
                        "${fmtTime(interval.startEpochMs, zone)} -> ${fmtTime(interval.endEpochMs, zone)} " +
                        "(${interval.durationMs} ms)$tag"
                )
            }
        }
        sb.appendLine()

        sb.appendLine("--- EXCLUDED / UNKNOWN INTERVALS (${report.excludedIntervals.size}) ---")
        if (report.excludedIntervals.isEmpty()) {
            sb.appendLine("(none)")
        } else {
            report.excludedIntervals.takeLast(80).forEach { ex ->
                sb.appendLine(
                    "  [${ex.reason.name}] pkg=${ex.packageName} cls=${ex.className} " +
                        "${fmtTime(ex.timestampOrStartMs, zone)} -> ${fmtTime(ex.endEpochMs, zone)} " +
                        "(${ex.details})"
                )
            }
        }
        sb.appendLine()

        sb.appendLine("--- RAW EVENTS SAMPLE (total=${report.rawEventCount}, showing last ${report.rawEventsSample.size}) ---")
        if (report.rawEventsSample.isEmpty()) {
            sb.appendLine("(none)")
        } else {
            report.rawEventsSample.takeLast(100).forEach { ev ->
                sb.appendLine(
                    "  #${ev.sequenceIndex} ${fmtTime(ev.timestampMs, zone)} [${ev.eventType.name}] " +
                        "${ev.packageName} / ${ev.className}"
                )
            }
        }

        return sb.toString()
    }
}
