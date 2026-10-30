package com.example.ui.screens

import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.ImageView
import android.widget.Toast
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
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.reminder.ReminderManager
import com.example.service.AppBlockingAccessibilityService
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SelectableAppInfo(
    val name: String,
    val packageName: String,
    val icon: android.graphics.drawable.Drawable?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appSettings: AppSettings,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accentColor = AppAccentColor

    val reminderEnabled by appSettings.generalReminderEnabled.collectAsState()
    val reminderHour by appSettings.generalReminderHour.collectAsState()
    val reminderMinute by appSettings.generalReminderMinute.collectAsState()
    val blockedPackages by appSettings.blockedAppPackages.collectAsState()

    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    var hasUsageStats by remember { mutableStateOf(false) }
    var showAppPickerSheet by remember { mutableStateOf(false) }

    // Re-check permissions when returning from system settings
    LifecycleResumeEffect(Unit) {
        isAccessibilityEnabled = PermissionUtils.isAccessibilityServiceEnabled(
            context,
            AppBlockingAccessibilityService::class.java
        )
        hasUsageStats = PermissionUtils.hasUsageStatsPermission(context)
        onPauseOrDispose { }
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
                title = "EINSTELLUNGEN",
                subtitle = "App-Sperren, Berechtigungen & Fokus"
            )
        }

        // Section: System-Berechtigungen
        item(key = "section_permissions_title") {
            Text(
                text = "BERECHTIGUNGEN",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = CourierPrimeFontFamily,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Card: Barrierefreiheit (Accessibility)
        item(key = "perm_accessibility") {
            NothingCard(
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeCardMedium,
                backgroundColor = MaterialTheme.colorScheme.surface
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
                            .background(
                                if (isAccessibilityEnabled) Color(0xFF10B981).copy(alpha = 0.15f)
                                else Color(0xFFF59E0B).copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Security,
                            contentDescription = null,
                            tint = if (isAccessibilityEnabled) Color(0xFF10B981) else Color(0xFFF59E0B),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "App-Sperre (Barrierefreiheit)",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = GeistFontFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isAccessibilityEnabled) "Aktiv • Apps werden zuverlässig blockiert"
                            else "Erforderlich, um gesperrte Apps zu erkennen",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = GeistFontFamily),
                            color = if (isAccessibilityEnabled) Color(0xFF10B981) else Color(0xFFF59E0B)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { PermissionUtils.openAccessibilitySettings(context) },
                        shape = ShapePill,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAccessibilityEnabled) MaterialTheme.colorScheme.surfaceVariant else accentColor,
                            contentColor = if (isAccessibilityEnabled) MaterialTheme.colorScheme.onSurface else Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isAccessibilityEnabled) "Status" else "Aktivieren",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = GeistFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }

        // Card: Nutzungsdaten-Zugriff (Usage Stats)
        item(key = "perm_usage_stats") {
            NothingCard(
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeCardMedium,
                backgroundColor = MaterialTheme.colorScheme.surface
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
                            .background(
                                if (hasUsageStats) Color(0xFF10B981).copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DataUsage,
                            contentDescription = null,
                            tint = if (hasUsageStats) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Nutzungsdaten-Zugriff",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = GeistFontFamily,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (hasUsageStats) "Aktiv • Bildschirmzeit wird erfasst"
                            else "Für die Anzeige der Bildschirmzeit im Einblicke-Tab",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = GeistFontFamily),
                            color = if (hasUsageStats) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { PermissionUtils.openUsageStatsSettings(context) },
                        shape = ShapePill,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (hasUsageStats) "Aktiv" else "Erlauben",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = GeistFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }

        // Section: Gesperrte Apps
        item(key = "section_blocked_apps_title") {
            Text(
                text = "APP-AUSWAHL",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = CourierPrimeFontFamily,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Card: Manage Blocked Apps Button
        item(key = "manage_blocked_apps") {
            NothingCard(
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeCardMedium,
                backgroundColor = MaterialTheme.colorScheme.surface,
                onClick = { showAppPickerSheet = true }
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
                            .background(accentColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Apps,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Gesperrte Apps verwalten",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = GeistFontFamily,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${blockedPackages.size} Apps ausgewählt",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = GeistFontFamily),
                            color = accentColor
                        )
                    }

                    Icon(
                        imageVector = Icons.Outlined.OpenInNew,
                        contentDescription = "Öffnen",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Section: Tägliche Erinnerung
        item(key = "section_reminder_title") {
            Text(
                text = "BENACHRICHTIGUNGEN",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = CourierPrimeFontFamily,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Card: Daily Reminder
        item(key = "daily_reminder") {
            NothingCard(
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeCardMedium,
                backgroundColor = MaterialTheme.colorScheme.surface
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
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Alarm,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tägliche Erinnerung",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = GeistFontFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Erinnert dich an noch offene Aufgaben",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = GeistFontFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = reminderEnabled,
                            onCheckedChange = { isEnabled ->
                                appSettings.setGeneralReminderEnabled(isEnabled)
                                if (isEnabled) {
                                    ReminderManager.scheduleGeneralDailyReminder(context, reminderHour, reminderMinute)
                                } else {
                                    ReminderManager.cancelGeneralDailyReminder(context)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = accentColor,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }

                    if (reminderEnabled) {
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
                                            appSettings.setGeneralReminderTime(hour, minute)
                                            ReminderManager.scheduleGeneralDailyReminder(context, hour, minute)
                                        },
                                        reminderHour,
                                        reminderMinute,
                                        true
                                    ).show()
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Erinnerungszeit:",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = GeistFontFamily),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = String.format("%02d:%02d Uhr", reminderHour, reminderMinute),
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

        // Card: About More2Day
        item(key = "about_card") {
            NothingCard(
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeCardMedium,
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "More2Day",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = CourierPrimeFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Version 1.0 • Do the thing, then scroll.",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = GeistFontFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // App Picker BottomSheet
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerBottomSheet(
    currentlyBlocked: Set<String>,
    onDismiss: () -> Unit,
    onToggleApp: (String) -> Unit
) {
    val context = LocalContext.current
    val accentColor = AppAccentColor
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val allApps = remember { mutableStateListOf<SelectableAppInfo>() }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            val apps = resolveInfos.mapNotNull { ri ->
                val pkg = ri.activityInfo.packageName
                if (pkg == context.packageName) null
                else {
                    SelectableAppInfo(
                        name = ri.loadLabel(pm).toString(),
                        packageName = pkg,
                        icon = try { ri.loadIcon(pm) } catch (e: Exception) { null }
                    )
                }
            }.distinctBy { it.packageName }.sortedBy { it.name.lowercase() }

            withContext(Dispatchers.Main) {
                allApps.clear()
                allApps.addAll(apps)
                isLoading = false
            }
        }
    }

    val filteredApps = remember(searchQuery, allApps) {
        if (searchQuery.isBlank()) allApps
        else allApps.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Gesperrte Apps auswählen",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = CourierPrimeFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Schließen",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("App suchen...", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = GeistFontFamily)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Suchen",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = ShapePill,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Apps werden geladen...",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = GeistFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isBlocked = currentlyBlocked.contains(app.packageName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ShapeCardMedium)
                                .background(
                                    if (isBlocked) accentColor.copy(alpha = 0.08f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                                .clickable { onToggleApp(app.packageName) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (app.icon != null) {
                                AndroidView(
                                    factory = { ctx ->
                                        ImageView(ctx).apply {
                                            setImageDrawable(app.icon)
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Apps,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = GeistFontFamily,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = GeistFontFamily,
                                        fontSize = 11.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }

                            Checkbox(
                                checked = isBlocked,
                                onCheckedChange = { onToggleApp(app.packageName) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = accentColor,
                                    checkmarkColor = Color.White
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
