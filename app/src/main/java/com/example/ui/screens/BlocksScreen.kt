package com.example.ui.screens

import android.app.TimePickerDialog
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.data.TaskWithCompletion
import com.example.service.AppBlockingAccessibilityService
import com.example.ui.DailyViewModel
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

data class ResolvedBlockedApp(
    val packageName: String,
    val name: String,
    val icon: android.graphics.drawable.Drawable?
)

@Composable
fun BlocksScreen(
    viewModel: DailyViewModel,
    tasks: List<TaskWithCompletion>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accentColor = AppAccentColor
    val appSettings = remember { AppSettings.getInstance(context) }

    val blockedPackages by appSettings.blockedAppPackages.collectAsState()
    val ruleMode by appSettings.blockRuleMode.collectAsState()
    val deadlineHour by appSettings.blockDeadlineHour.collectAsState()
    val deadlineMinute by appSettings.blockDeadlineMinute.collectAsState()

    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    var showAppPickerSheet by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        isAccessibilityEnabled = PermissionUtils.isAccessibilityServiceEnabled(
            context,
            AppBlockingAccessibilityService::class.java
        )
        onPauseOrDispose { }
    }

    // Calculate pending habits for today
    val openHabits = remember(tasks) {
        tasks.filter { it.isDueToday && !it.isCompleted }
    }
    val allHabitsCompleted = remember(tasks, openHabits) {
        tasks.isNotEmpty() && openHabits.isEmpty()
    }

    // Check if deadline is currently passed
    val isPastDeadline = remember(deadlineHour, deadlineMinute) {
        val cal = Calendar.getInstance()
        val curMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val dMinutes = deadlineHour * 60 + deadlineMinute
        curMinutes >= dMinutes
    }

    val isCurrentlyLocked = remember(allHabitsCompleted, ruleMode, isPastDeadline, tasks) {
        if (tasks.isEmpty()) false
        else if (allHabitsCompleted) false
        else if (ruleMode == "DEADLINE") isPastDeadline
        else true
    }

    // Resolve app names and icons for active blocked packages
    val resolvedApps = remember { mutableStateListOf<ResolvedBlockedApp>() }
    LaunchedEffect(blockedPackages) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val list = blockedPackages.map { pkg ->
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    ResolvedBlockedApp(
                        packageName = pkg,
                        name = pm.getApplicationLabel(appInfo).toString(),
                        icon = pm.getApplicationIcon(appInfo)
                    )
                } catch (e: Exception) {
                    ResolvedBlockedApp(pkg, pkg, null)
                }
            }.sortedBy { it.name.lowercase() }

            withContext(Dispatchers.Main) {
                resolvedApps.clear()
                resolvedApps.addAll(list)
            }
        }
    }

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
                title = "SPERREN",
                subtitle = "Do the thing, then scroll"
            )
        }

        // Accessibility Permission Notice (if not enabled)
        if (!isAccessibilityEnabled) {
            item(key = "perm_warning") {
                NothingCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapeCardMedium,
                    backgroundColor = Color(0xFFF59E0B).copy(alpha = 0.12f),
                    borderColor = Color(0xFFF59E0B).copy(alpha = 0.4f),
                    onClick = { PermissionUtils.openAccessibilitySettings(context) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "App-Sperre aktivieren",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = CourierPrimeFontFamily,
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

        // Hero Status Card (Habits First Style)
        item(key = "hero_status") {
            val statusColor = if (!isCurrentlyLocked) Color(0xFF10B981) else Color(0xFF6366F1)
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(statusColor.copy(alpha = 0.15f))
                                .border(1.dp, statusColor.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (!isCurrentlyLocked) Icons.Outlined.CheckCircle else Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (!isCurrentlyLocked) "Apps freigeschaltet" else "Apps gesperrt",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = CourierPrimeFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = if (!isCurrentlyLocked) {
                                    "Alle Pflicht-Habits erledigt! Du kannst scrollen."
                                } else if (ruleMode == "DEADLINE" && !isPastDeadline) {
                                    String.format("Offen bis %02d:%02d Uhr. Schließe deine Habits rechtzeitig ab.", deadlineHour, deadlineMinute)
                                } else {
                                    "${openHabits.size} offene Aufgabe(n) blockieren deine Apps."
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = GeistFontFamily),
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Progress indicator pill
                    val completedCount = tasks.count { it.isCompleted }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ShapePill)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (ruleMode == "DEADLINE") "Modus: Deadline-Sperre" else "Modus: Alle Pflicht-Habits",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = GeistFontFamily),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$completedCount / ${tasks.size} erledigt",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = CourierPrimeFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = statusColor
                        )
                    }
                }
            }
        }

        // Section: Entsperr-Regeln (Unlock Rules)
        item(key = "section_rules_title") {
            Text(
                text = "ENTSPERR-REGELN",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = CourierPrimeFontFamily,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Rule Option 1: Alle Habits erledigen
        item(key = "rule_all_habits") {
            val isSelected = ruleMode == "ALL_HABITS"
            NothingCard(
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeCardMedium,
                backgroundColor = if (isSelected) accentColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                borderColor = if (isSelected) accentColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline,
                onClick = { appSettings.setBlockRuleMode("ALL_HABITS") }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { appSettings.setBlockRuleMode("ALL_HABITS") },
                        colors = RadioButtonDefaults.colors(selectedColor = accentColor)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Alle Habits erledigen",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = GeistFontFamily,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Apps sind den ganzen Tag gesperrt, bis alle heutigen Aufgaben erledigt sind.",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = GeistFontFamily),
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }

        // Rule Option 2: Deadline-Sperre (z. B. 20:30 Uhr)
        item(key = "rule_deadline") {
            val isSelected = ruleMode == "DEADLINE"
            NothingCard(
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeCardMedium,
                backgroundColor = if (isSelected) accentColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                borderColor = if (isSelected) accentColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline,
                onClick = { appSettings.setBlockRuleMode("DEADLINE") }
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
                        RadioButton(
                            selected = isSelected,
                            onClick = { appSettings.setBlockRuleMode("DEADLINE") },
                            colors = RadioButtonDefaults.colors(selectedColor = accentColor)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Deadline-Sperre",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = GeistFontFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Sperrt deine Apps erst ab einer festen Uhrzeit, wenn bis dahin noch Aufgaben offen sind.",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = GeistFontFamily),
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    if (isSelected) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ShapePill)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    TimePickerDialog(
                                        context,
                                        { _, hour, minute ->
                                            appSettings.setBlockDeadlineTime(hour, minute)
                                        },
                                        deadlineHour,
                                        deadlineMinute,
                                        true
                                    ).show()
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.LockClock,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Sperr-Uhrzeit:",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = GeistFontFamily),
                                    color = Color.White
                                )
                            }
                            Text(
                                text = String.format("%02d:%02d Uhr", deadlineHour, deadlineMinute),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = CourierPrimeFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = accentColor
                            )
                        }
                    }
                }
            }
        }

        // Section: Gesperrte Apps
        item(key = "section_apps_title") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AKTIVE SPERR-LISTE",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = CourierPrimeFontFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${resolvedApps.size} Apps",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = GeistFontFamily),
                    color = accentColor
                )
            }
        }

        // List of blocked apps or empty placeholder
        if (resolvedApps.isEmpty()) {
            item(key = "empty_apps") {
                NothingCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapeCardMedium,
                    backgroundColor = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Keine Apps ausgewählt",
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = GeistFontFamily),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Wähle Apps aus, die blockiert werden sollen.",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = GeistFontFamily),
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        } else {
            items(resolvedApps, key = { it.packageName }) { app ->
                NothingCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapeCardMedium,
                    backgroundColor = MaterialTheme.colorScheme.surface
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
                                    ImageView(ctx).apply { setImageDrawable(app.icon) }
                                },
                                modifier = Modifier.size(34.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Apps,
                                    contentDescription = null,
                                    tint = accentColor,
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
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color.White,
                                maxLines = 1
                            )
                            Text(
                                text = if (isCurrentlyLocked) "Aktuell gesperrt" else "Freigeschaltet",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = GeistFontFamily,
                                    fontSize = 11.sp
                                ),
                                color = if (isCurrentlyLocked) Color(0xFF6366F1) else Color(0xFF10B981)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isCurrentlyLocked) Color(0xFF6366F1).copy(alpha = 0.15f)
                                    else Color(0xFF10B981).copy(alpha = 0.15f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isCurrentlyLocked) Icons.Outlined.Lock else Icons.Outlined.Check,
                                contentDescription = null,
                                tint = if (isCurrentlyLocked) Color(0xFF6366F1) else Color(0xFF10B981),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // Manage Apps Button
        item(key = "btn_manage_apps") {
            Button(
                onClick = { showAppPickerSheet = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = ShapePill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Apps,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Gesperrte Apps anpassen",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = GeistFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }

    // App Picker Sheet
    if (showAppPickerSheet) {
        AppPickerBottomSheet(
            currentlyBlocked = blockedPackages,
            onDismiss = { showAppPickerSheet = false },
            onToggleApp = { packageName ->
                appSettings.toggleBlockedPackage(packageName)
            }
        )
    }
}
