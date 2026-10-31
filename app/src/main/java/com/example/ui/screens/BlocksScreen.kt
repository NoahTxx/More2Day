package com.example.ui.screens

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.data.TaskWithCompletion
import com.example.service.AppBlockingAccessibilityService
import com.example.ui.DailyViewModel
import com.example.ui.components.HabitsCapsuleIcon
import com.example.ui.components.NothingCard
import com.example.ui.components.OverlappingAppIcons
import com.example.ui.components.ShapeCardLarge
import com.example.ui.components.ShapeCardMedium
import com.example.ui.components.ShapePill
import com.example.ui.theme.GeistFontFamily
import com.example.ui.theme.HabitColorAmber
import com.example.ui.theme.HabitColorBlue
import com.example.ui.theme.HabitColorOrange
import com.example.ui.theme.HabitColorPurple
import com.example.ui.theme.HabitsSwitchGreen
import com.example.ui.theme.NDotFontFamily
import com.example.util.AppSettings
import com.example.util.HapticUtils
import com.example.util.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

data class BlockableAppItem(
    val packageName: String,
    val name: String,
    val icon: android.graphics.drawable.Drawable?,
    val todayUsageMillis: Long,
    val rankingScore: Long,
    val isRecommended: Boolean
)

@Composable
fun BlocksScreen(
    viewModel: DailyViewModel,
    tasks: List<TaskWithCompletion>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings.getInstance(context) }

    val blockedPackages by appSettings.blockedAppPackages.collectAsState()

    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    var hasUsageStatsPermission by remember { mutableStateOf(false) }

    var isBlockActive by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // "ALL", "BLOCKED", "RECOMMENDED"
    var isLoadingApps by remember { mutableStateOf(true) }

    var allInstalledApps by remember { mutableStateOf<List<BlockableAppItem>>(emptyList()) }

    LifecycleResumeEffect(Unit) {
        isAccessibilityEnabled = PermissionUtils.isAccessibilityServiceEnabled(
            context,
            AppBlockingAccessibilityService::class.java
        )
        hasUsageStatsPermission = PermissionUtils.hasUsageStatsPermission(context)
        onPauseOrDispose { }
    }

    // Load and sort apps by usage time
    LaunchedEffect(hasUsageStatsPermission) {
        isLoadingApps = true
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)

            val startOfDay = com.example.util.ScreenTimeCalculator.getTodayStartMillis()
            val now = System.currentTimeMillis()
            val screenTimeResult = com.example.util.ScreenTimeCalculator.getScreenTimeData(context, startOfDay, now)

            val rawApps = resolveInfos.mapNotNull { ri ->
                val pkg = ri.activityInfo.packageName
                if (pkg == context.packageName ||
                    pkg == "com.android.systemui" ||
                    pkg == "com.google.android.apps.nexuslauncher" ||
                    pkg == "com.android.launcher" ||
                    pkg == "com.nothing.launcher"
                ) {
                    null
                } else {
                    val label = ri.loadLabel(pm).toString()
                    val icon = try { ri.loadIcon(pm) } catch (e: Exception) { null }
                    val todayUsage = screenTimeResult.appUsageMap[pkg] ?: 0L
                    val rankingScore = todayUsage
                    pkg to Triple(label, icon, todayUsage to rankingScore)
                }
            }.distinctBy { it.first }

            // Determine top apps for recommendations
            val sortedByUsage = rawApps.sortedWith(
                compareByDescending<Pair<String, Triple<String, android.graphics.drawable.Drawable?, Pair<Long, Long>>>> { it.second.third.second }
                    .thenBy { it.second.first.lowercase() }
            )
            val recommendedSet = sortedByUsage
                .filter { it.second.third.second > 30_000L }
                .take(6)
                .map { it.first }
                .toSet()

            val mapped = sortedByUsage.map { (pkg, data) ->
                BlockableAppItem(
                    packageName = pkg,
                    name = data.first,
                    icon = data.second,
                    todayUsageMillis = data.third.first,
                    rankingScore = data.third.second,
                    isRecommended = recommendedSet.contains(pkg)
                )
            }

            withContext(Dispatchers.Main) {
                allInstalledApps = mapped
                isLoadingApps = false
            }
        }
    }

    // Filter apps based on search and selected filter
    val filteredApps = remember(allInstalledApps, blockedPackages, searchQuery, selectedFilter) {
        allInstalledApps.filter { app ->
            val matchesSearch = if (searchQuery.isBlank()) true else {
                app.name.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
            }
            val matchesFilter = when (selectedFilter) {
                "BLOCKED" -> blockedPackages.contains(app.packageName)
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    // Get icons of currently blocked apps for stacked display
    val blockedAppIcons = allInstalledApps
        .filter { blockedPackages.contains(it.packageName) }
        .map { it.icon }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF111216))
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header: "Sperren" + Round "+" Button
        item(key = "header_bar") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sperren",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = GeistFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    ),
                    color = Color.White
                )

                // Circular "+" Button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF24252C))
                        .clickable {
                            // Focus search or filter to pick apps
                            selectedFilter = "ALL"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Hinzufügen",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Accessibility Service Warning (if not granted)
        if (!isAccessibilityEnabled) {
            item(key = "perm_warning") {
                NothingCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapeCardMedium,
                    backgroundColor = Color(0xFF1C1D22),
                    borderColor = Color(0xFFF59E0B).copy(alpha = 0.5f),
                    onClick = { PermissionUtils.openAccessibilitySettings(context) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF59E0B).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "App-Sperre aktivieren",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = GeistFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Tippe hier, um More2Day in den Bedienungshilfen einzuschalten.",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = GeistFontFamily),
                                color = Color(0xFFF59E0B)
                            )
                        }
                    }
                }
            }
        }

        // Block Card (Screenshot 2: "Fokus" with stacked icons, green switch & "BEDINGUNG")
        item(key = "block_card_hero") {
            NothingCard(
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeCardLarge,
                backgroundColor = Color(0xFF1C1D22),
                borderColor = Color(0xFF282A33)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    // Card Top Row: Overlapping Icons + Title + Green Switch + Lock
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Stacked App Icons (Screenshot 2)
                        if (blockedAppIcons.isNotEmpty()) {
                            OverlappingAppIcons(
                                icons = blockedAppIcons,
                                iconSize = 32.dp,
                                overlapOffset = 14.dp
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF24252C)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        // Block Name
                        Text(
                            text = "Fokus",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = GeistFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )

                        // Monochrome Toggle Switch
                        Column(horizontalAlignment = Alignment.End) {
                            Switch(
                                checked = isBlockActive,
                                onCheckedChange = { isBlockActive = it },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = Color.White,
                                    checkedThumbColor = Color.Black,
                                    uncheckedTrackColor = Color(0xFF2E313C),
                                    uncheckedThumbColor = Color(0xFF94A3B8)
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Condition Section (Screenshot 2: "BEDINGUNG")
                    Text(
                        text = "BEDINGUNG",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = GeistFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 0.8.sp
                        ),
                        color = Color(0xFF71717A)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (tasks.isEmpty()) {
                        Text(
                            text = "Erstelle Gewohnheiten, um diesen Block freizuschalten.",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = GeistFontFamily),
                            color = Color(0xFF94A3B8)
                        )
                    } else {
                        // Habit rows with colored progress bars (Screenshot 2)
                        tasks.take(4).forEach { item ->
                            val habitColor = when (item.task.category.lowercase()) {
                                "sport", "training", "fitness" -> HabitColorOrange
                                "lernen", "anki", "bildung" -> HabitColorBlue
                                "lesen", "buch" -> HabitColorAmber
                                "gesundheit", "meditation" -> HabitColorPurple
                                else -> HabitColorOrange
                            }

                            val habitIcon = when (item.task.category.lowercase()) {
                                "sport", "training", "fitness" -> Icons.Outlined.FitnessCenter
                                "lernen", "anki", "bildung" -> Icons.Outlined.School
                                "lesen", "buch" -> Icons.Outlined.MenuBook
                                "gesundheit", "meditation" -> Icons.Outlined.SelfImprovement
                                else -> Icons.Outlined.CheckCircle
                            }

                            val fraction = if (item.isCompleted) 1f else if (item.task.targetCount > 0) {
                                (item.count.toFloat() / item.task.targetCount.toFloat()).coerceIn(0f, 1f)
                            } else 0f

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = habitIcon,
                                        contentDescription = null,
                                        tint = habitColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = item.task.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = GeistFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        ),
                                        color = Color.White,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = if (item.isCompleted) "Erledigt" else if (item.task.targetCount > 1) "${item.count} / ${item.task.targetCount}" else "0 / 1",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = GeistFontFamily,
                                            fontSize = 11.sp
                                        ),
                                        color = Color(0xFF94A3B8)
                                    )
                                }

                                Spacer(modifier = Modifier.height(5.dp))

                                // Colored horizontal bar (Screenshot 2)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(ShapePill)
                                        .background(Color(0xFF262830))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fraction = fraction)
                                            .height(4.dp)
                                            .clip(ShapePill)
                                            .background(habitColor)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section Title: Apps im Block
        item(key = "section_apps_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "APPS ZUM SPERREN",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = GeistFontFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = Color(0xFF71717A)
                )
                Text(
                    text = "${blockedPackages.size} gesperrt",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = GeistFontFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = if (blockedPackages.isNotEmpty()) Color.White else Color(0xFF71717A)
                )
            }
        }

        // Search Bar
        item(key = "search_bar") {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "App suchen...",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = GeistFontFamily),
                        color = Color(0xFF64748B)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Suchen",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Löschen",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = ShapePill,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color(0xFF282A33),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF1C1D22),
                    unfocusedContainerColor = Color(0xFF1C1D22)
                )
            )
        }

        // Filter Pills Row
        item(key = "filter_row") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                BlockFilterChip(
                    label = "Alle (${allInstalledApps.size})",
                    isSelected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" }
                )
                BlockFilterChip(
                    label = "Gesperrt (${blockedPackages.size})",
                    isSelected = selectedFilter == "BLOCKED",
                    onClick = { selectedFilter = "BLOCKED" }
                )
            }
        }

        // Loading or Empty or App Items
        if (isLoadingApps) {
            item(key = "loading_apps") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        } else if (filteredApps.isEmpty()) {
            item(key = "empty_filtered_apps") {
                NothingCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapeCardMedium,
                    backgroundColor = Color(0xFF1C1D22),
                    borderColor = Color(0xFF282A33)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (selectedFilter == "BLOCKED") "Keine Apps gesperrt"
                            else if (searchQuery.isNotEmpty()) "Keine App gefunden"
                            else "Keine Apps verfügbar",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = GeistFontFamily,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (selectedFilter == "BLOCKED") {
                                "Wähle aus deinen meistgenutzten Apps aus, um sie in den Fokus-Block aufzunehmen."
                            } else {
                                "Versuche einen anderen Suchbegriff."
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = GeistFontFamily),
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        } else {
            items(filteredApps, key = { it.packageName }) { app ->
                val isBlocked = blockedPackages.contains(app.packageName)
                val usageText = remember(app.todayUsageMillis) {
                    formatAppUsageText(app.todayUsageMillis)
                }

                NothingCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapeCardMedium,
                    backgroundColor = Color(0xFF1C1D22),
                    borderColor = if (isBlocked) Color.White.copy(alpha = 0.35f) else Color(0xFF282A33),
                    onClick = {
                        HapticUtils.vibrateTick(context)
                        appSettings.toggleBlockedPackage(app.packageName)
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                                modifier = Modifier.size(34.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF24252C)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Apps,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.name,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = GeistFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                ),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = usageText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = GeistFontFamily,
                                    fontSize = 12.sp
                                ),
                                color = if (app.todayUsageMillis > 0) Color(0xFFFBBF24) else Color(0xFF64748B)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Toggle Circle Check
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(if (isBlocked) Color.White else Color.Transparent)
                                .border(
                                    width = if (isBlocked) 0.dp else 1.5.dp,
                                    color = if (isBlocked) Color.Transparent else Color(0xFF383A46),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isBlocked) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = "Gesperrt",
                                    tint = Color.Black,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .clip(ShapePill)
            .background(
                if (isSelected) Color(0xFF2E313C)
                else Color(0xFF1C1D22)
            )
            .border(
                1.dp,
                if (isSelected) Color.White.copy(alpha = 0.5f) else Color(0xFF282A33),
                ShapePill
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = GeistFontFamily,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 11.sp
            ),
            color = if (isSelected) Color.White else Color(0xFF94A3B8)
        )
    }
}

private fun formatAppUsageText(millis: Long): String {
    if (millis <= 0L) return "Heute nicht genutzt"
    val minutes = (millis / (1000 * 60))
    val hours = minutes / 60
    val remMin = minutes % 60
    return when {
        hours > 0 && remMin > 0 -> "${hours}h ${remMin}m heute"
        hours > 0 -> "${hours}h heute"
        minutes > 0 -> "${minutes}m heute"
        else -> "< 1m heute"
    }
}
