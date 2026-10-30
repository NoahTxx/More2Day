package com.example.ui.screens

import android.app.usage.UsageStatsManager
import android.content.Context
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.ui.DailyViewModel
import com.example.ui.StreakStats
import com.example.ui.components.NothingCard
import com.example.ui.components.NothingHeader
import com.example.ui.components.ShapeCardLarge
import com.example.ui.components.ShapeCardMedium
import com.example.ui.components.ShapePill
import com.example.ui.theme.CourierPrimeFontFamily
import com.example.ui.theme.GeistFontFamily
import com.example.util.AppAccentColor
import com.example.util.AppSettings
import com.example.util.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

data class AppUsageItem(
    val packageName: String,
    val name: String,
    val icon: android.graphics.drawable.Drawable?,
    val usageMillis: Long,
    val isBlocked: Boolean
)

@Composable
fun StatsScreen(
    viewModel: DailyViewModel,
    streakStats: StreakStats,
    completionDates: List<String>,
    totalTasksCount: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accentColor = AppAccentColor
    val appSettings = remember { AppSettings.getInstance(context) }
    val blockedPackages by appSettings.blockedAppPackages.collectAsState()

    var hasUsageStatsPermission by remember { mutableStateOf(false) }
    var totalScreenTimeMillis by remember { mutableLongStateOf(0L) }
    var blockedAppsTimeMillis by remember { mutableLongStateOf(0L) }
    val appUsageList = remember { mutableStateListOf<AppUsageItem>() }

    LifecycleResumeEffect(Unit) {
        hasUsageStatsPermission = PermissionUtils.hasUsageStatsPermission(context)
        onPauseOrDispose { }
    }

    LaunchedEffect(hasUsageStatsPermission, blockedPackages) {
        if (!hasUsageStatsPermission) return@LaunchedEffect

        withContext(Dispatchers.IO) {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startTime = calendar.timeInMillis
            val endTime = System.currentTimeMillis()

            val stats = usageStatsManager?.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            ) ?: emptyList()

            val pm = context.packageManager
            var totalTime = 0L
            var blockedTime = 0L
            val usageMap = mutableMapOf<String, Long>()

            for (stat in stats) {
                val t = stat.totalTimeInForeground
                if (t > 0) {
                    usageMap[stat.packageName] = (usageMap[stat.packageName] ?: 0L) + t
                    totalTime += t
                    if (blockedPackages.contains(stat.packageName)) {
                        blockedTime += t
                    }
                }
            }

            // Map and resolve apps with notable usage
            val items = usageMap.entries
                .filter { it.value >= 30_000L } // at least 30 seconds
                .sortedByDescending { it.value }
                .take(10)
                .map { entry ->
                    val pkg = entry.key
                    val isBlocked = blockedPackages.contains(pkg)
                    try {
                        val appInfo = pm.getApplicationInfo(pkg, 0)
                        AppUsageItem(
                            packageName = pkg,
                            name = pm.getApplicationLabel(appInfo).toString(),
                            icon = pm.getApplicationIcon(appInfo),
                            usageMillis = entry.value,
                            isBlocked = isBlocked
                        )
                    } catch (e: Exception) {
                        AppUsageItem(
                            packageName = pkg,
                            name = pkg,
                            icon = null,
                            usageMillis = entry.value,
                            isBlocked = isBlocked
                        )
                    }
                }

            withContext(Dispatchers.Main) {
                totalScreenTimeMillis = totalTime
                blockedAppsTimeMillis = blockedTime
                appUsageList.clear()
                appUsageList.addAll(items)
            }
        }
    }

    val totalHours = totalScreenTimeMillis / (1000 * 60 * 60)
    val totalMinutes = (totalScreenTimeMillis / (1000 * 60)) % 60

    val blockedHours = blockedAppsTimeMillis / (1000 * 60 * 60)
    val blockedMinutes = (blockedAppsTimeMillis / (1000 * 60)) % 60

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 150.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Screen Header
        item(key = "header") {
            NothingHeader(
                title = "EINBLICKE",
                subtitle = "Bildschirmzeit & Fokus-Statistiken"
            )
        }

        // Permission Banner (if usage stats not granted)
        if (!hasUsageStatsPermission) {
            item(key = "perm_card") {
                NothingCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapeCardMedium,
                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    borderColor = accentColor.copy(alpha = 0.4f),
                    onClick = { PermissionUtils.openUsageStatsSettings(context) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BarChart,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Nutzungsdaten aktivieren",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = CourierPrimeFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Tippe hier, um More2Day Zugriff auf deine tägliche Bildschirmzeit zu geben.",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = GeistFontFamily),
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }
        }

        // Hero Screen-Time Card (Habits First Style)
        item(key = "hero_screen_time") {
            NothingCard(
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeCardLarge,
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "BILDSCHIRMZEIT HEUTE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = CourierPrimeFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 0.8.sp
                        ),
                        color = Color(0xFF94A3B8)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (hasUsageStatsPermission) "${totalHours}h ${totalMinutes}m" else "--:--",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontFamily = CourierPrimeFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 38.sp
                        ),
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Secondary metrics row (Habits First Premack stats)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Metric 1: Time in blocked apps
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(ShapeCardMedium)
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = "In gesperrten Apps",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = GeistFontFamily,
                                        fontSize = 11.sp
                                    ),
                                    color = Color(0xFF94A3B8)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (hasUsageStatsPermission) "${blockedHours}h ${blockedMinutes}m" else "--",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontFamily = CourierPrimeFontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (blockedHours > 1) Color(0xFFF59E0B) else accentColor
                                )
                            }
                        }

                        // Metric 2: Streak & Consistency
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(ShapeCardMedium)
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Habit-Streak",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = GeistFontFamily,
                                        fontSize = 11.sp
                                    ),
                                    color = Color(0xFF94A3B8)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${streakStats.currentStreak} Tage",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontFamily = CourierPrimeFontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color(0xFF10B981)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section: Gated Apps Usage Breakdown
        item(key = "section_usage_title") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "APP-NUTZUNG HEUTE",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = CourierPrimeFontFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Top ${appUsageList.size}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = GeistFontFamily),
                    color = accentColor
                )
            }
        }

        if (appUsageList.isEmpty()) {
            item(key = "empty_usage") {
                NothingCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapeCardMedium,
                    backgroundColor = MaterialTheme.colorScheme.surface
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (hasUsageStatsPermission) "Noch keine App-Aktivität für heute erfasst."
                            else "Nutzungsdaten-Zugriff erforderlich.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = GeistFontFamily),
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(appUsageList, key = { it.packageName }) { app ->
                val minutes = (app.usageMillis / (1000 * 60))
                val hours = minutes / 60
                val remainingMin = minutes % 60
                val formattedTime = if (hours > 0) "${hours}h ${remainingMin}m" else "${remainingMin}m"
                val fraction = if (totalScreenTimeMillis > 0) (app.usageMillis.toFloat() / totalScreenTimeMillis.toFloat()).coerceIn(0f, 1f) else 0f

                NothingCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapeCardMedium,
                    backgroundColor = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (app.icon != null) {
                                AndroidView(
                                    factory = { ctx ->
                                        ImageView(ctx).apply { setImageDrawable(app.icon) }
                                    },
                                    modifier = Modifier.size(32.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Apps,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = app.name,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontFamily = GeistFontFamily,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                    if (app.isBlocked) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(ShapePill)
                                                .background(accentColor.copy(alpha = 0.15f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "GESPERRT",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontFamily = CourierPrimeFontFamily,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = accentColor
                                            )
                                        }
                                    }
                                }
                            }

                            Text(
                                text = formattedTime,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = CourierPrimeFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (app.isBlocked) Color(0xFFF59E0B) else Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Progress Bar
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(ShapePill),
                            color = if (app.isBlocked) accentColor else Color(0xFF334155),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }
    }
}
