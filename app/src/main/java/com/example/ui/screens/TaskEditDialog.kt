package com.example.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DailyTask
import com.example.util.AppLanguage
import com.example.util.AppStrings
import com.example.util.DateUtils
import com.example.util.LocalAccentColor
import com.example.util.LocalAppLanguage
import com.example.util.LocalBodyFontFamily
import com.example.util.LocalHeadingFontFamily

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditDialog(
    task: DailyTask?,
    onDismiss: () -> Unit,
    onSave: (DailyTask) -> Unit,
    onDelete: ((DailyTask) -> Unit)? = null
) {
    val context = LocalContext.current
    val accentColor = LocalAccentColor.current
    val language = LocalAppLanguage.current
    val headingFont = LocalHeadingFontFamily.current
    val bodyFont = LocalBodyFontFamily.current
    val isEn = language == AppLanguage.EN
    fun t(key: String): String = AppStrings.get(key, language)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember { mutableStateOf(task?.title ?: "") }
    var category by remember { mutableStateOf(task?.category ?: if (isEn) "Supplements" else "Supplements") }
    var isCounter by remember { mutableStateOf(task?.isCounter ?: false) }
    var frequencyType by remember { mutableStateOf(task?.frequencyType ?: "DAILY") }
    var targetDaysPerWeek by remember { mutableIntStateOf(task?.targetDaysPerWeek ?: 1) }
    var intervalDays by remember { mutableIntStateOf(task?.intervalDays ?: 5) }
    var intervalAnchorDate by remember { mutableStateOf(task?.intervalAnchorDate?.ifBlank { DateUtils.getTodayKey() } ?: DateUtils.getTodayKey()) }
    var selectedDaysOfWeek by remember {
        val initialDays = (task?.daysOfWeek ?: "1,2,3,4,5,6,7").split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        mutableStateOf(if (initialDays.isEmpty()) setOf(1, 2, 3, 4, 5, 6, 7) else initialDays)
    }
    var targetCountText by remember { mutableStateOf(task?.targetCount?.toString() ?: "10") }
    var unitText by remember { mutableStateOf(task?.unit ?: if (isEn) "Reps" else "Wdh.") }
    var reminderEnabled by remember { mutableStateOf(task?.reminderEnabled ?: false) }
    var reminderHour by remember { mutableIntStateOf(task?.reminderHour ?: 8) }
    var reminderMinute by remember { mutableIntStateOf(task?.reminderMinute ?: 0) }

    val categories = if (isEn) {
        listOf("Supplements", "Health", "Focus", "Workout", "Routine")
    } else {
        listOf("Supplements", "Gesundheit", "Fokus", "Sport", "Routine")
    }

    val commonUnits = if (isEn) {
        listOf("Reps", "ml", "Pages", "Mins", "km")
    } else {
        listOf("Wdh.", "ml", "Seiten", "Min.", "km")
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (task == null) t("dialog_new_task") else t("dialog_edit_task"),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = headingFont,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Title input
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Default, fontSize = 16.sp),
                label = { Text(t("task_name_label"), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = bodyFont)) },
                placeholder = { Text(t("task_name_placeholder"), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = bodyFont)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_title_input"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = accentColor,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Category selection
            Text(
                text = t("category_label"),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = bodyFont),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    val isSelected = category.equals(cat, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                1.dp,
                                if (isSelected) accentColor else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(10.dp)
                            )
                            .background(
                                if (isSelected) accentColor.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { category = cat }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cat.uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = bodyFont,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Habit Schedule / Frequency Selection (Daily vs Weekly vs Weekdays)
            Text(
                text = t("frequency_label"),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = bodyFont),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "DAILY" to t("freq_daily"),
                    "WEEKLY" to t("freq_weekly"),
                    "WEEKDAYS" to t("freq_weekdays"),
                    "INTERVAL" to t("freq_interval")
                ).forEach { (type, label) ->
                    val isSelected = frequencyType == type
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                1.dp,
                                if (isSelected) accentColor else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(10.dp)
                            )
                            .background(
                                if (isSelected) accentColor.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { frequencyType = type }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = bodyFont,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Sub-settings for Interval (Alle X Tage)
            if (frequencyType == "INTERVAL") {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(14.dp)
                ) {
                    // Preset Chips
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(2, 3, 4, 5, 7, 10, 14, 30).forEach { days ->
                            val isSel = intervalDays == days
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        1.dp,
                                        if (isSel) accentColor else MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .background(
                                        if (isSel) accentColor.copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.surface
                                    )
                                    .clickable { intervalDays = days }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isEn) "Every $days d" else "Alle $days Tage",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = bodyFont,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isSel) accentColor else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Custom Stepper
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format(t("every_x_days"), intervalDays),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = headingFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            ),
                            color = accentColor
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Decrement button
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                    .clickable { if (intervalDays > 1) intervalDays-- },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "-",
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = headingFont),
                                    color = if (intervalDays > 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }

                            // Increment button
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                    .clickable { if (intervalDays < 365) intervalDays++ },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+",
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = headingFont),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Sub-settings for Weekly
            if (frequencyType == "WEEKLY") {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        (1..6).forEach { count ->
                            val isSel = targetDaysPerWeek == count
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        1.dp,
                                        if (isSel) accentColor else MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .background(
                                        if (isSel) accentColor.copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.surface
                                    )
                                    .clickable { targetDaysPerWeek = count }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${count}×",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = headingFont,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isSel) accentColor else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Sub-settings for Specific Weekdays
            if (frequencyType == "WEEKDAYS") {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(14.dp)
                ) {
                    val dayLabels = if (isEn) {
                        listOf(1 to "M", 2 to "T", 3 to "W", 4 to "T", 5 to "F", 6 to "S", 7 to "S")
                    } else {
                        listOf(1 to "Mo", 2 to "Di", 3 to "Mi", 4 to "Do", 5 to "Fr", 6 to "Sa", 7 to "So")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        dayLabels.forEach { (dayNum, dayLabel) ->
                            val isSel = selectedDaysOfWeek.contains(dayNum)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        1.dp,
                                        if (isSel) accentColor else MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .background(
                                        if (isSel) accentColor.copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.surface
                                    )
                                    .clickable {
                                        val newSet = if (isSel) {
                                            if (selectedDaysOfWeek.size > 1) selectedDaysOfWeek - dayNum else selectedDaysOfWeek
                                        } else {
                                            selectedDaysOfWeek + dayNum
                                        }
                                        selectedDaysOfWeek = newSet
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dayLabel,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = headingFont,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isSel) accentColor else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = DateUtils.formatDaysOfWeekShort(selectedDaysOfWeek.sorted().joinToString(","), language),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = bodyFont),
                        color = accentColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Habit Type Selection (Checkbox vs Counter)
            Text(
                text = t("habit_type_label"),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = bodyFont),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Checkbox type
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            1.dp,
                            if (!isCounter) accentColor else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(12.dp)
                        )
                        .background(
                            if (!isCounter) accentColor.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { isCounter = false }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = t("type_simple"),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = bodyFont,
                            fontWeight = if (!isCounter) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (!isCounter) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Counter type
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            1.dp,
                            if (isCounter) accentColor else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(12.dp)
                        )
                        .background(
                            if (isCounter) accentColor.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { isCounter = true }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = t("type_counter"),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = bodyFont,
                            fontWeight = if (isCounter) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isCounter) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Improved counter controls (quick suggestions removed as requested)
            if (isCounter) {
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Target count with steppers
                    OutlinedTextField(
                        value = targetCountText,
                        onValueChange = { targetCountText = it.filter { ch -> ch.isDigit() } },
                        label = { Text(t("target_amount_label"), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = bodyFont)) },
                        placeholder = { Text("10", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = bodyFont)) },
                        modifier = Modifier.weight(1.1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = accentColor
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Target unit input
                    OutlinedTextField(
                        value = unitText,
                        onValueChange = { unitText = it },
                        label = { Text(t("target_unit_label"), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = bodyFont)) },
                        placeholder = { Text(if (isEn) "Reps" else "Wdh.", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = bodyFont)) },
                        modifier = Modifier.weight(0.9f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = accentColor
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Numerical Steppers row for easy target adjustment
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(-10, -1, 1, 10).forEach { delta ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    val currentVal = targetCountText.toIntOrNull() ?: 1
                                    val newVal = (currentVal + delta).coerceAtLeast(1)
                                    targetCountText = newVal.toString()
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (delta > 0) "+$delta" else "$delta",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = headingFont,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Clean Unit Chips (reps, ml, pages, mins, km)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    commonUnits.forEach { u ->
                        val isSelected = unitText.equals(u, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    1.dp,
                                    if (isSelected) accentColor else MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(8.dp)
                                )
                                .background(
                                    if (isSelected) accentColor.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { unitText = u }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = u,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = bodyFont,
                                    fontSize = 11.sp
                                ),
                                color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Reminder section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = if (reminderEnabled) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = t("reminder_label"),
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = bodyFont),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (reminderEnabled) {
                        val timeStr = DateUtils.formatTime(reminderHour, reminderMinute)
                        Text(
                            text = String.format(t("reminder_set_at"), timeStr),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = bodyFont),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 26.dp, top = 2.dp)
                        )
                    }
                }

                Switch(
                    checked = reminderEnabled,
                    onCheckedChange = { reminderEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = if (accentColor == Color(0xFFF8FAFC) || accentColor == Color(0xFFF5F5F7) || accentColor == Color.White) Color.Black else Color.White,
                        checkedTrackColor = accentColor,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surface
                    )
                )
            }

            // Time Picker Button if enabled
            if (reminderEnabled) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable {
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    reminderHour = hour
                                    reminderMinute = minute
                                },
                                reminderHour,
                                reminderMinute,
                                true
                            ).show()
                        }
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = t("set_time_button"),
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = bodyFont),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = DateUtils.formatTime(reminderHour, reminderMinute),
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = headingFont),
                            color = accentColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (task != null && onDelete != null) {
                    IconButton(
                        onClick = {
                            onDelete(task)
                            onDismiss()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = t("delete"),
                            tint = accentColor
                        )
                    }
                }

                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            val targetCnt = targetCountText.toIntOrNull()?.coerceAtLeast(1) ?: 10
                            val daysCsv = if (frequencyType == "WEEKDAYS") {
                                selectedDaysOfWeek.sorted().joinToString(",")
                            } else {
                                "1,2,3,4,5,6,7"
                            }
                            val updated = (task ?: DailyTask(title = title.trim())).copy(
                                title = title.trim(),
                                category = category,
                                frequencyType = frequencyType,
                                targetDaysPerWeek = if (frequencyType == "WEEKLY") targetDaysPerWeek else 1,
                                daysOfWeek = daysCsv,
                                intervalDays = if (frequencyType == "INTERVAL") intervalDays.coerceAtLeast(1) else 1,
                                intervalAnchorDate = if (frequencyType == "INTERVAL") intervalAnchorDate.ifBlank { DateUtils.getTodayKey() } else "",
                                isCounter = isCounter,
                                targetCount = if (isCounter) targetCnt else 1,
                                unit = if (isCounter) unitText.trim() else "",
                                reminderEnabled = reminderEnabled,
                                reminderHour = if (reminderEnabled) reminderHour else null,
                                reminderMinute = if (reminderEnabled) reminderMinute else null
                            )
                            onSave(updated)
                            onDismiss()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("save_task_button"),
                    enabled = title.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E313C),
                        contentColor = Color.White,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = Color(0xFF64748B)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF383A46))
                ) {
                    Text(
                        text = if (task == null) t("save_task") else t("save_changes"),
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = bodyFont)
                    )
                }
            }
        }
    }
}
