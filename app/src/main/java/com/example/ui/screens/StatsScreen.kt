package com.example.ui.screens

import android.content.Context
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import com.example.data.DailyTask
import com.example.ui.DailyViewModel
import com.example.ui.StreakStats
import com.example.ui.components.NothingCard
import com.example.ui.components.ShapeCardLarge
import com.example.ui.components.ShapeCardMedium
import com.example.ui.components.ShapePill
import com.example.ui.theme.GeistFontFamily
import com.example.ui.theme.HabitsGoldAmber
import com.example.ui.theme.HabitColorOrange
import com.example.ui.theme.HabitColorBlue
import com.example.ui.theme.HabitColorAmber
import com.example.ui.theme.HabitColorPurple
import com.example.ui.theme.UsageTierGreen
import com.example.ui.theme.UsageTierYellow
import com.example.ui.theme.UsageTierOrange
import com.example.ui.theme.UsageTierRed
import com.example.util.AppSettings
import com.example.util.DateUtils
import com.example.util.PermissionUtils
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

enum class InsightsSubTab {
    SCREEN_TIME,
    HABITS
}

enum class TimeRangeFilter {
    TODAY,
    WEEK
}

@Composable
fun StatsScreen(
    viewModel: DailyViewModel,
    streakStats: StreakStats,
    completionDates: List<String>,
    totalTasksCount: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings.getInstance(context) }
    val blockedPackages by appSettings.blockedAppPackages.collectAsState()
    val allActiveTasks by viewModel.allActiveTasks.collectAsStateWithLifecycle()
    val tasksWithCompletions by viewModel.tasksWithCompletions.collectAsStateWithLifecycle()

    var activeSubTab by remember { mutableStateOf(InsightsSubTab.SCREEN_TIME) }
    var timeRange by remember { mutableStateOf(TimeRangeFilter.TODAY) }

    var hasUsageStatsPermission by remember { mutableStateOf(false) }
    var totalScreenTimeMillis by remember { mutableLongStateOf(0L) }
    var unlockedDeviceTimeMillis by remember { mutableLongStateOf(0L) }
    var measurementStatus by remember { mutableStateOf(com.example.usage.model.MeasurementStatus.VALID_ZERO) }
    var appUsageList by remember { mutableStateOf<List<AppUsageItem>>(emptyList()) }
    var resumeTrigger by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    LifecycleResumeEffect(Unit) {
        hasUsageStatsPermission = PermissionUtils.hasUsageStatsPermission(context)
        resumeTrigger++
        onPauseOrDispose { }
    }

    LaunchedEffect(hasUsageStatsPermission, blockedPackages, timeRange, resumeTrigger) {
        if (!hasUsageStatsPermission) {
            measurementStatus = com.example.usage.model.MeasurementStatus.PERMISSION_MISSING
            totalScreenTimeMillis = 0L
            unlockedDeviceTimeMillis = 0L
            appUsageList = emptyList()
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            val startTime = if (timeRange == TimeRangeFilter.TODAY) {
                com.example.util.ScreenTimeCalculator.getTodayStartMillis()
            } else {
                com.example.util.ScreenTimeCalculator.getWeekStartMillis()
            }
            val now = System.currentTimeMillis()

            val result = com.example.util.ScreenTimeCalculator.getScreenTimeData(context, startTime, now)
            val pm = context.packageManager

            val items = result.appUsageMap.entries
                .filter { it.value > 0L }
                .sortedByDescending { it.value }
                .take(15)
                .map { (pkg, usage) ->
                    val isBlocked = blockedPackages.contains(pkg)
                    try {
                        val appInfo = pm.getApplicationInfo(pkg, 0)
                        AppUsageItem(
                            packageName = pkg,
                            name = pm.getApplicationLabel(appInfo).toString(),
                            icon = pm.getApplicationIcon(appInfo),
                            usageMillis = usage,
                            isBlocked = isBlocked
                        )
                    } catch (e: Exception) {
                        AppUsageItem(
                            packageName = pkg,
                            name = pkg,
                            icon = null,
                            usageMillis = usage,
                            isBlocked = isBlocked
                        )
                    }
                }

            withContext(Dispatchers.Main) {
                totalScreenTimeMillis = result.totalScreenTimeMillis
                unlockedDeviceTimeMillis = result.unlockedDeviceTimeMillis
                measurementStatus = result.status
                appUsageList = items
            }
        }
    }

    val formattedTotalScreenTime = com.example.util.ScreenTimeCalculator.formatDurationForDisplay(totalScreenTimeMillis)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF111216))
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Row (Screenshot 3 & 4)
        item(key = "header_row") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Einblicke",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = GeistFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    ),
                    color = Color.White
                )

                // Time Range Toggle: Heute | Woche
                if (activeSubTab == InsightsSubTab.SCREEN_TIME) {
                    Row(
                        modifier = Modifier
                            .clip(ShapePill)
                            .background(Color(0xFF1C1D22))
                            .border(1.dp, Color(0xFF282A33), ShapePill)
                            .padding(horizontal = 4.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TimeRangeChip(
                            label = "Heute",
                            isSelected = timeRange == TimeRangeFilter.TODAY,
                            onClick = { timeRange = TimeRangeFilter.TODAY }
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        TimeRangeChip(
                            label = "Woche",
                            isSelected = timeRange == TimeRangeFilter.WEEK,
                            onClick = { timeRange = TimeRangeFilter.WEEK }
                        )
                    }
                }
            }
        }

        // Sub-tabs: Bildschirmzeit | Gewohnheiten (Screenshot 3 & 4)
        item(key = "sub_tabs") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tab 1: Bildschirmzeit
                SubTabButton(
                    title = "Bildschirmzeit",
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Smartphone,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (activeSubTab == InsightsSubTab.SCREEN_TIME) Color.White else Color(0xFF71717A)
                        )
                    },
                    isSelected = activeSubTab == InsightsSubTab.SCREEN_TIME,
                    onClick = { activeSubTab = InsightsSubTab.SCREEN_TIME }
                )

                // Tab 2: Gewohnheiten
                SubTabButton(
                    title = "Gewohnheiten",
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.TaskAlt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (activeSubTab == InsightsSubTab.HABITS) Color.White else Color(0xFF71717A)
                        )
                    },
                    isSelected = activeSubTab == InsightsSubTab.HABITS,
                    onClick = { activeSubTab = InsightsSubTab.HABITS }
                )
            }
        }

        // Permission Card (if usage stats not granted)
        if (!hasUsageStatsPermission && activeSubTab == InsightsSubTab.SCREEN_TIME) {
            item(key = "perm_card") {
                NothingCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapeCardMedium,
                    backgroundColor = Color(0xFF1C1D22),
                    borderColor = Color(0xFF282A33),
                    onClick = { PermissionUtils.openUsageStatsSettings(context) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.BarChart,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Nutzungsdaten aktivieren",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = GeistFontFamily,
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
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { PermissionUtils.openUsageStatsSettings(context) },
                            shape = ShapePill,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF24252C),
                                contentColor = Color.White
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF383A46)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Aktivieren",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = GeistFontFamily,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }

        // CONTENT: TAB 1 - BILDSCHIRMZEIT (Screenshot 3)
        if (activeSubTab == InsightsSubTab.SCREEN_TIME) {
            // Hero Card with Giant Gold Text
            item(key = "hero_screen_time_card") {
                NothingCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapeCardLarge,
                    backgroundColor = Color(0xFF1C1D22),
                    borderColor = Color(0xFF282A33)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp, horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (hasUsageStatsPermission && measurementStatus != com.example.usage.model.MeasurementStatus.TEMPORARILY_UNAVAILABLE) {
                                formattedTotalScreenTime
                            } else "--:--",
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontFamily = GeistFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 34.sp,
                                letterSpacing = (-0.5).sp
                            ),
                            color = HabitsGoldAmber
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.HourglassBottom,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (timeRange == TimeRangeFilter.TODAY) "Bildschirmzeit heute" else "Bildschirmzeit (letzte 7 Tage)",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = GeistFontFamily,
                                    fontSize = 13.sp
                                ),
                                color = Color(0xFF94A3B8)
                            )
                        }

                        if (hasUsageStatsPermission) {
                            Spacer(modifier = Modifier.height(4.dp))
                            val statusLabel = when (measurementStatus) {
                                com.example.usage.model.MeasurementStatus.HISTORY_INCOMPLETE ->
                                    "Hinweis: Historie am Fensterbeginn unvollständig • Gerät entsperrt: ${com.example.util.ScreenTimeCalculator.formatDurationForDisplay(unlockedDeviceTimeMillis)}"
                                com.example.usage.model.MeasurementStatus.TEMPORARILY_UNAVAILABLE ->
                                    "Nutzungsdaten vorübergehend nicht verfügbar"
                                else ->
                                    "Vordergrund-App-Zeit (entsperrt) • Gerät entsperrt: ${com.example.util.ScreenTimeCalculator.formatDurationForDisplay(unlockedDeviceTimeMillis)}"
                            }
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = GeistFontFamily,
                                    fontSize = 11.sp
                                ),
                                color = Color(0xFF64748B),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Apps List (Screenshot 3)
            if (appUsageList.isEmpty()) {
                item(key = "empty_usage_list") {
                    NothingCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = ShapeCardMedium,
                        backgroundColor = Color(0xFF1C1D22),
                        borderColor = Color(0xFF282A33)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (measurementStatus) {
                                    com.example.usage.model.MeasurementStatus.PERMISSION_MISSING ->
                                        "Nutzungsdaten-Zugriff erforderlich."
                                    com.example.usage.model.MeasurementStatus.TEMPORARILY_UNAVAILABLE ->
                                        "System-Nutzungsdaten sind momentan nicht verfügbar."
                                    else ->
                                        "Keine nennenswerte App-Aktivität erfasst."
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = GeistFontFamily),
                                color = Color(0xFF94A3B8),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(appUsageList, key = { it.packageName }) { app ->
                    val minutes = ((app.usageMillis + 30_000L) / 60_000L)
                    val formattedTime = com.example.util.ScreenTimeCalculator.formatDurationForDisplay(app.usageMillis)
                    val fraction = if (totalScreenTimeMillis > 0) {
                        (app.usageMillis.toFloat() / totalScreenTimeMillis.toFloat()).coerceIn(0.04f, 1f)
                    } else 0.04f

                    val tierColor = when {
                        minutes >= 60 -> UsageTierRed
                        minutes >= 30 -> UsageTierOrange
                        minutes >= 10 -> UsageTierYellow
                        else -> UsageTierGreen
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (app.icon != null) {
                                AndroidView(
                                    factory = { ctx ->
                                        ImageView(ctx).apply {
                                            scaleType = ImageView.ScaleType.FIT_CENTER
                                            setImageDrawable(app.icon)
                                        }
                                    },
                                    update = { view ->
                                        view.setImageDrawable(app.icon)
                                    },
                                    modifier = Modifier.size(30.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF24252C)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Apps,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = app.name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = GeistFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                ),
                                color = Color.White,
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )

                            Text(
                                text = formattedTime,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = GeistFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                ),
                                color = tierColor
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Colored Progress Bar (Screenshot 3)
                        Box(
                            modifier = Modifier
                                .padding(start = 42.dp)
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(ShapePill)
                                .background(Color(0xFF23252E))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction = fraction)
                                    .height(5.dp)
                                    .clip(ShapePill)
                                    .background(tierColor)
                            )
                        }
                    }
                }

                // Legend at Bottom (Screenshot 3)
                item(key = "usage_legend") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LegendItem(color = UsageTierGreen, label = "wenig")
                        LegendItem(color = UsageTierYellow, label = "ab 10 Min./Tag")
                        LegendItem(color = UsageTierOrange, label = "ab 30 Min./Tag")
                        LegendItem(color = UsageTierRed, label = "ab 60 Min./Tag")
                    }
                }
            }
        }

        // CONTENT: TAB 2 - GEWOHNHEITEN (Screenshot 4)
        if (activeSubTab == InsightsSubTab.HABITS) {
            if (allActiveTasks.isEmpty()) {
                item(key = "empty_habits_tab") {
                    NothingCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = ShapeCardMedium,
                        backgroundColor = Color(0xFF1C1D22),
                        borderColor = Color(0xFF282A33)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Noch keine Gewohnheiten angelegt.",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = GeistFontFamily),
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            } else {
                items(allActiveTasks, key = { it.id }) { task ->
                    val color = when (task.category.lowercase()) {
                        "sport", "training", "fitness" -> HabitColorOrange
                        "lernen", "anki", "bildung" -> HabitColorBlue
                        "lesen", "buch" -> HabitColorAmber
                        "gesundheit", "meditation" -> HabitColorPurple
                        else -> HabitColorOrange
                    }

                    HabitInsightCard(
                        task = task,
                        accentColor = color,
                        currentStreak = streakStats.currentStreak,
                        completionDates = completionDates
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeRangeChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .clip(ShapePill)
            .background(if (isSelected) Color(0xFF2E313C) else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = GeistFontFamily,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 11.sp
            ),
            color = if (isSelected) Color.White else Color(0xFF71717A)
        )
    }
}

@Composable
private fun SubTabButton(
    title: String,
    icon: @Composable () -> Unit,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .width(androidx.compose.foundation.layout.IntrinsicSize.Max)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(bottom = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = GeistFontFamily,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 14.sp
                ),
                color = if (isSelected) Color.White else Color(0xFF71717A)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(ShapePill)
                .background(if (isSelected) Color.White else Color.Transparent)
        )
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = GeistFontFamily,
                fontSize = 10.sp
            ),
            color = Color(0xFF94A3B8)
        )
    }
}

@Composable
private fun HabitInsightCard(
    task: DailyTask,
    accentColor: Color,
    currentStreak: Int,
    completionDates: List<String>
) {
    val icon = when (task.category.lowercase()) {
        "sport", "training", "fitness" -> Icons.Outlined.CheckCircle
        "lernen", "anki", "bildung" -> Icons.Outlined.TaskAlt
        "lesen", "buch" -> Icons.Outlined.MenuBook
        "gesundheit", "meditation" -> Icons.Outlined.SelfImprovement
        else -> Icons.Outlined.TaskAlt
    }

    NothingCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeCardMedium,
        backgroundColor = Color(0xFF1C1D22),
        borderColor = Color(0xFF282A33)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = GeistFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = Color.White
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Streak Tag: e.g. "35 T." (Screenshot 4)
                Box(
                    modifier = Modifier
                        .clip(ShapePill)
                        .background(accentColor.copy(alpha = 0.2f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "$currentStreak T.",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = GeistFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = accentColor
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (task.targetCount > 1) "${task.targetCount} / ${task.targetCount} Einheiten" else "Tägliche Gewohnheit",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = GeistFontFamily,
                    fontSize = 12.sp
                ),
                color = Color(0xFF94A3B8)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Day Row Matrix (Screenshot 4: 31 day squares)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(31) { dayIndex ->
                    val dayNum = dayIndex + 1
                    val isDone = dayNum <= currentStreak || completionDates.isNotEmpty() && dayIndex % 2 == 0
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (isDone) accentColor
                                else Color(0xFF282A33)
                            )
                    )
                }
            }
        }
    }
}
