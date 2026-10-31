package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.DailyTask
import com.example.data.TaskWithCompletion
import com.example.ui.DailyViewModel
import com.example.ui.components.NothingCard
import com.example.ui.components.NothingCheckbox
import com.example.ui.components.NothingPill
import com.example.ui.components.NotificationPermissionCard
import com.example.ui.components.ShapeCardLarge
import com.example.ui.components.ShapeCardMedium
import com.example.ui.components.ShapeCardSmall
import com.example.ui.components.ShapePill
import com.example.ui.components.TaskSectionHeader
import com.example.ui.theme.GeistFontFamily
import com.example.util.AppLanguage
import com.example.util.AppSettings
import com.example.util.AppStrings
import com.example.util.DateUtils
import com.example.util.HapticUtils
import com.example.util.LocalAccentColor
import com.example.util.LocalAppLanguage
import com.example.util.LocalBodyFontFamily
import com.example.util.LocalHeadingFontFamily

@Composable
fun TodayScreen(
    viewModel: DailyViewModel,
    selectedDateKey: String,
    tasks: List<TaskWithCompletion>,
    selectedCategory: String?,
    currentStreak: Int,
    completionDates: List<String>,
    onAddTaskClick: () -> Unit,
    onEditTaskClick: (DailyTask) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showPermissionCard by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            } else false
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        showPermissionCard = !isGranted
    }

    val (openDueTasks, completedTasks, notDueTasks) = remember(tasks) {
        val open = mutableListOf<TaskWithCompletion>()
        val completed = mutableListOf<TaskWithCompletion>()
        val notDue = mutableListOf<TaskWithCompletion>()

        for (task in tasks) {
            val isDone = task.isCompleted || (task.task.frequencyType == "WEEKLY" && task.isWeeklyGoalMet)
            if (isDone) {
                completed.add(task)
            } else if (task.isDueToday) {
                open.add(task)
            } else {
                notDue.add(task)
            }
        }
        Triple(open, completed, notDue)
    }

    val completedCount = remember(tasks) {
        tasks.count { it.isCompleted || (it.task.frequencyType == "WEEKLY" && it.isWeeklyGoalMet) }
    }
    val requiredTodayTasks = remember(tasks) {
        val mandatory = tasks.filter {
            it.task.frequencyType == "DAILY" ||
            it.isMandatoryToday ||
            (it.isDueToday && !(it.task.frequencyType == "WEEKLY" && it.isWeeklyGoalMet))
        }
        val extraCompleted = tasks.filter { task ->
            !mandatory.any { it.task.id == task.task.id } &&
            (task.isCompleted || (task.task.frequencyType == "WEEKLY" && task.isWeeklyGoalMet))
        }
        mandatory + extraCompleted
    }
    val totalCount = remember(requiredTodayTasks) { requiredTodayTasks.size }
    val isViewingToday = DateUtils.isToday(selectedDateKey)

    val language = LocalAppLanguage.current
    val accentColor = LocalAccentColor.current
    val headingFont = LocalHeadingFontFamily.current
    val bodyFont = LocalBodyFontFamily.current
    fun t(key: String): String = AppStrings.get(key, language)

    val onToggleTask: (Long) -> Unit = remember(isViewingToday, tasks) {
        { taskId ->
            if (!isViewingToday) {
                Toast.makeText(context, t("past_day_readonly_toast"), Toast.LENGTH_SHORT).show()
            } else {
                val taskItem = tasks.firstOrNull { it.task.id == taskId }
                val isItemBlocked = taskItem != null && !taskItem.isCompleted && when (taskItem.task.frequencyType) {
                    "WEEKDAYS" -> !taskItem.isDueToday
                    "INTERVAL" -> !taskItem.isDueToday
                    "WEEKLY" -> taskItem.isWeeklyGoalMet
                    else -> false
                }
                if (isItemBlocked) {
                    val toastMsg = when (taskItem.task.frequencyType) {
                        "WEEKDAYS" -> String.format(t("not_due_toast"), DateUtils.formatDaysOfWeekShort(taskItem.task.daysOfWeek, language))
                        "INTERVAL" -> if (language == AppLanguage.EN) "Due in ${taskItem.daysUntilDue} days" else "In ${taskItem.daysUntilDue} Tagen fällig"
                        "WEEKLY" -> if (language == AppLanguage.EN) "Weekly goal reached (${taskItem.weeklyCompletedCount}/${taskItem.task.targetDaysPerWeek})" else "Wochenziel erreicht (${taskItem.weeklyCompletedCount}/${taskItem.task.targetDaysPerWeek})"
                        else -> t("not_due_cant_check")
                    }
                    Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.toggleTask(taskId)
                }
            }
        }
    }

    val onIncrementTask: (Long, Int) -> Unit = remember(isViewingToday, tasks) {
        { taskId, delta ->
            if (!isViewingToday) {
                Toast.makeText(context, t("past_day_readonly_toast"), Toast.LENGTH_SHORT).show()
            } else {
                val taskItem = tasks.firstOrNull { it.task.id == taskId }
                val isItemBlocked = taskItem != null && !taskItem.isCompleted && when (taskItem.task.frequencyType) {
                    "WEEKDAYS" -> !taskItem.isDueToday
                    "INTERVAL" -> !taskItem.isDueToday
                    "WEEKLY" -> taskItem.isWeeklyGoalMet
                    else -> false
                }
                if (isItemBlocked && delta > 0) {
                    val toastMsg = when (taskItem.task.frequencyType) {
                        "WEEKDAYS" -> String.format(t("not_due_toast"), DateUtils.formatDaysOfWeekShort(taskItem.task.daysOfWeek, language))
                        "INTERVAL" -> if (language == AppLanguage.EN) "Due in ${taskItem.daysUntilDue} days" else "In ${taskItem.daysUntilDue} Tagen fällig"
                        "WEEKLY" -> if (language == AppLanguage.EN) "Weekly goal reached (${taskItem.weeklyCompletedCount}/${taskItem.task.targetDaysPerWeek})" else "Wochenziel erreicht (${taskItem.weeklyCompletedCount}/${taskItem.task.targetDaysPerWeek})"
                        else -> t("not_due_cant_check")
                    }
                    Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.incrementCounter(taskId, delta)
                }
            }
        }
    }

    val onEditTask: (DailyTask) -> Unit = remember {
        { task -> onEditTaskClick(task) }
    }

    val displayDate = remember(selectedDateKey, language) {
        DateUtils.formatDisplayDate(selectedDateKey, language)
    }

    val listState = rememberLazyListState()

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF111216))
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item(key = "header", contentType = "header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Habits",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = GeistFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp
                            ),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = displayDate,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = GeistFontFamily,
                                fontSize = 13.sp
                            ),
                            color = Color(0xFF94A3B8)
                        )
                    }
                    com.example.ui.components.More2DayLogoTile(size = 40.dp)
                }
            }

            // Notification Permission Card (if permission not granted)
            if (showPermissionCard) {
                item(key = "permission_card", contentType = "permission") {
                    NotificationPermissionCard(
                        onGrantClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onDismissClick = { showPermissionCard = false }
                    )
                }
            }

            // Tasks List
            if (tasks.isEmpty()) {
                item(key = "empty_tasks_state", contentType = "empty") {
                    NothingCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        backgroundColor = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(accentColor)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = t("no_habits_title"),
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = headingFont),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = t("no_habits_subtitle"),
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = bodyFont),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                // 1. OPEN DUE TASKS (Must be done today!)
                if (openDueTasks.isNotEmpty()) {
                    item(key = "header_open_tasks", contentType = "section_header") {
                        TaskSectionHeader(
                            title = t("section_due_open"),
                            count = openDueTasks.size,
                            isAccent = true
                        )
                    }

                    items(
                        items = openDueTasks,
                        key = { it.task.id },
                        contentType = { "task_row" }
                    ) { item ->
                        TaskRowItem(
                            item = item,
                            onToggle = onToggleTask,
                            onIncrement = onIncrementTask,
                            onEdit = onEditTask,
                            isReadOnly = !isViewingToday
                        )
                    }
                } else if (totalCount > 0 && completedCount >= totalCount) {
                    // All due habits completed for today!
                    item(key = "all_done_card", contentType = "all_done") {
                        NothingCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = com.example.ui.components.ShapeCardMedium,
                            backgroundColor = Color(0xFF1C1D22),
                            borderColor = Color(0xFF282A33)
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
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = t("all_done_title"),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = headingFont,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = t("all_done_subtitle"),
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = bodyFont),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. COMPLETED TASKS
                if (completedTasks.isNotEmpty()) {
                    item(key = "header_completed_tasks", contentType = "section_header") {
                        TaskSectionHeader(
                            title = t("section_completed"),
                            count = completedTasks.size,
                            isAccent = false
                        )
                    }

                    items(
                        items = completedTasks,
                        key = { it.task.id },
                        contentType = { "task_row" }
                    ) { item ->
                        TaskRowItem(
                            item = item,
                            onToggle = onToggleTask,
                            onIncrement = onIncrementTask,
                            onEdit = onEditTask,
                            isReadOnly = !isViewingToday
                        )
                    }
                }

                // 3. NOT DUE TODAY / REST DAY TASKS
                if (notDueTasks.isNotEmpty()) {
                    item(key = "header_not_due_tasks", contentType = "section_header") {
                        TaskSectionHeader(
                            title = t("section_not_due"),
                            count = notDueTasks.size,
                            isAccent = false
                        )
                    }

                    items(
                        items = notDueTasks,
                        key = { it.task.id },
                        contentType = { "task_row" }
                    ) { item ->
                        TaskRowItem(
                            item = item,
                            onToggle = onToggleTask,
                            onIncrement = onIncrementTask,
                            onEdit = onEditTask,
                            isReadOnly = !isViewingToday
                        )
                    }
                }
            }
        }

        // Floating Action Button (+) cleanly positioned above bottom pill navigation (only on Today)
        if (isViewingToday) {
            FloatingActionButton(
                onClick = onAddTaskClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(bottom = 76.dp, end = 20.dp)
                    .border(1.dp, Color(0xFF383A46), CircleShape)
                    .testTag("add_task_fab"),
                containerColor = Color(0xFF24252C),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = t("add_habit"),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

private val STEP_OPTIONS_LARGE = listOf(1, 10, 25)
private val STEP_OPTIONS_MEDIUM = listOf(1, 5, 10)
private val STEP_OPTIONS_SMALL = listOf(1, 2, 5)



@Composable
fun TaskRowItem(
    item: TaskWithCompletion,
    onToggle: (Long) -> Unit,
    onIncrement: (Long, Int) -> Unit = { _, _ -> },
    onEdit: (DailyTask) -> Unit,
    isReadOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accentColor = LocalAccentColor.current
    val language = LocalAppLanguage.current
    val bodyFont = LocalBodyFontFamily.current
    fun t(key: String): String = AppStrings.get(key, language)

    val isDone = item.isCompleted
    val isNotDue = !isDone && when (item.task.frequencyType) {
        "WEEKDAYS" -> !item.isDueToday
        "INTERVAL" -> !item.isDueToday
        "WEEKLY" -> item.isWeeklyGoalMet
        else -> false
    }
    val isWeeklyOptional = !isDone && item.task.frequencyType == "WEEKLY" && !item.isWeeklyGoalMet && !item.isMandatoryToday
    val isOpenDue = !isDone && !isNotDue && !isWeeklyOptional

    val cardBg = remember(isNotDue, isDone) {
        when {
            isNotDue -> Color(0xFF16171D).copy(alpha = 0.5f)
            isDone -> Color(0xFF1C1D22).copy(alpha = 0.7f)
            else -> Color(0xFF1C1D22)
        }
    }

    val cardBorder = remember(isNotDue, isOpenDue) {
        when {
            isNotDue -> Color(0xFF282A33).copy(alpha = 0.4f)
            isOpenDue -> Color(0xFF383A46)
            else -> Color(0xFF282A33)
        }
    }

    val notDueToastMsg = remember(item.task.id, item.task.frequencyType, item.task.daysOfWeek, item.task.targetDaysPerWeek, item.task.intervalDays, item.daysUntilDue, item.weeklyCompletedCount, item.isWeeklyGoalMet, language) {
        when (item.task.frequencyType) {
            "WEEKDAYS" -> String.format(t("not_due_toast"), DateUtils.formatDaysOfWeekShort(item.task.daysOfWeek, language))
            "WEEKLY" -> {
                if (language == AppLanguage.EN) "Weekly goal reached (${item.weeklyCompletedCount}/${item.task.targetDaysPerWeek}) • Rest day"
                else "Wochenziel erreicht (${item.weeklyCompletedCount}/${item.task.targetDaysPerWeek}) • Heute Pausentag"
            }
            "INTERVAL" -> {
                if (language == AppLanguage.EN) "Due in ${item.daysUntilDue} days • Rest day"
                else "In ${item.daysUntilDue} Tagen fällig • Heute Pausentag"
            }
            else -> t("not_due_cant_check")
        }
    }

    val cardClickAction: (() -> Unit)? = remember(isReadOnly, isNotDue, item.task.isCounter, notDueToastMsg, onToggle) {
        when {
            isReadOnly -> null
            isNotDue -> {
                { Toast.makeText(context, notDueToastMsg, Toast.LENGTH_SHORT).show() }
            }
            item.task.isCounter -> null
            else -> {
                { onToggle(item.task.id) }
            }
        }
    }

    val onToggleItem = remember(item.task.id, onToggle) {
        { onToggle(item.task.id) }
    }
    val onEditItem = remember(item.task, onEdit) {
        { onEdit(item.task) }
    }

    val categoryText = remember(item.task.category) { item.task.category.uppercase() }

    val frequencyText = remember(item.task.frequencyType, item.task.daysOfWeek, item.task.targetDaysPerWeek, item.task.intervalDays, item.weeklyCompletedCount, item.isWeeklyGoalMet, isWeeklyOptional, item.daysUntilDue, item.isDueToday, item.isCompleted, language) {
        when (item.task.frequencyType) {
            "WEEKLY" -> {
                if (item.isWeeklyGoalMet) {
                    String.format(t("weekly_goal_met_tag"), item.weeklyCompletedCount, item.task.targetDaysPerWeek)
                } else if (isWeeklyOptional) {
                    val remainingDays = DateUtils.getRemainingDaysInWeek(DateUtils.getTodayKey())
                    String.format(t("weekly_optional_hint"), remainingDays)
                } else {
                    String.format(t("weekly_progress_tag"), item.weeklyCompletedCount, item.task.targetDaysPerWeek)
                }
            }
            "WEEKDAYS" -> DateUtils.formatDaysOfWeekShort(item.task.daysOfWeek, language)
            "INTERVAL" -> {
                if (item.isCompleted) {
                    if (language == AppLanguage.EN) "Every ${item.task.intervalDays}d • Done" else "Alle ${item.task.intervalDays} Tage • Erledigt"
                } else if (item.isDueToday) {
                    if (language == AppLanguage.EN) "Every ${item.task.intervalDays}d • Due today" else "Alle ${item.task.intervalDays} Tage • Heute fällig"
                } else {
                    if (language == AppLanguage.EN) "Every ${item.task.intervalDays}d • In ${item.daysUntilDue}d" else "Alle ${item.task.intervalDays} Tage • In ${item.daysUntilDue}d"
                }
            }
            else -> null
        }
    }

    val counterText = remember(item.task.isCounter, item.count, item.task.targetCount, item.task.unit) {
        if (item.task.isCounter) {
            "${item.count} / ${item.task.targetCount} ${item.task.unit}".trim()
        } else null
    }

    val reminderText = remember(item.task.reminderEnabled, item.task.reminderHour, item.task.reminderMinute) {
        if (item.task.reminderEnabled && item.task.reminderHour != null && item.task.reminderMinute != null) {
            DateUtils.formatTime(item.task.reminderHour, item.task.reminderMinute)
        } else null
    }

    NothingCard(
        modifier = modifier.fillMaxWidth(),
        shape = com.example.ui.components.ShapeCardMedium,
        backgroundColor = cardBg,
        borderColor = cardBorder,
        onClick = cardClickAction
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Checkbox, Counter Progress Circle, or Calm Rest Day Icon (Moon)
                if (isNotDue) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), CircleShape)
                            .clickable {
                                Toast.makeText(context, notDueToastMsg, Toast.LENGTH_SHORT).show()
                            }
                            .testTag("rest_moon_${item.task.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Bedtime,
                            contentDescription = t("section_not_due"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                } else if (!item.task.isCounter) {
                    NothingCheckbox(
                        checked = item.isCompleted,
                        onCheckedChange = onToggleItem,
                        enabled = !isReadOnly,
                        size = 28.dp,
                        testTag = "checkbox_${item.task.id}"
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (item.isCompleted) Color.White
                                else MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape
                            )
                            .border(
                                width = if (item.isCompleted) 0.dp else 1.5.dp,
                                color = if (item.isCompleted) Color.Transparent else MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                            .then(if (isReadOnly) Modifier else Modifier.clickable(onClick = onToggleItem)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (item.isCompleted) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            val pct = if (item.task.targetCount > 0) {
                                ((item.count.toFloat() / item.task.targetCount.toFloat()) * 100).toInt()
                            } else 0
                            Text(
                                text = "$pct%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Task Details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.task.title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = bodyFont,
                            fontSize = 16.sp,
                            fontWeight = if (isOpenDue) FontWeight.SemiBold else FontWeight.Normal,
                            textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = when {
                            item.isCompleted -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            isNotDue -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Secondary pills section (extracted to prevent full row recomposition)
                    TaskPillsSection(
                        categoryText = categoryText,
                        isOpenDue = isOpenDue,
                        frequencyText = frequencyText,
                        isWeeklyGoalMet = item.isWeeklyGoalMet,
                        isCompleted = item.isCompleted,
                        counterText = counterText,
                        reminderText = reminderText,
                        accentColor = accentColor
                    )
                }

                // Edit button (only visible on today; locked on past days)
                if (!isReadOnly) {
                    IconButton(
                        onClick = onEditItem,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Enhanced counter progress and steppers (extracted to prevent full row recomposition)
            if (item.task.isCounter) {
                CounterProgressSection(
                    taskId = item.task.id,
                    count = item.count,
                    targetCount = item.task.targetCount,
                    isCompleted = item.isCompleted,
                    isReadOnly = isReadOnly,
                    isNotDue = isNotDue,
                    pastDayTag = t("past_day_tag"),
                    accentColor = accentColor,
                    onIncrement = onIncrement
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskPillsSection(
    categoryText: String,
    isOpenDue: Boolean,
    frequencyText: String?,
    isWeeklyGoalMet: Boolean,
    isCompleted: Boolean,
    counterText: String?,
    reminderText: String?,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        // Category pill
        NothingPill(text = categoryText, isAccent = isOpenDue)

        // Frequency pill
        if (frequencyText != null) {
            NothingPill(
                text = frequencyText,
                isAccent = isWeeklyGoalMet || isCompleted || isOpenDue
            )
        }

        if (counterText != null) {
            NothingPill(
                text = counterText,
                isAccent = isCompleted
            )
        }

        // Reminder time pill at the bottom with other pills
        if (reminderText != null) {
            NothingPill(
                text = reminderText,
                isAccent = isOpenDue,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = null,
                        tint = if (isOpenDue) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                }
            )
        }
    }
}

@Composable
fun CounterProgressSection(
    taskId: Long,
    count: Int,
    targetCount: Int,
    isCompleted: Boolean,
    isReadOnly: Boolean,
    isNotDue: Boolean,
    pastDayTag: String,
    accentColor: Color,
    onIncrement: (Long, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(10.dp))

        // Progress Bar
        val progress = if (targetCount > 0) {
            (count.toFloat() / targetCount.toFloat()).coerceIn(0f, 1f)
        } else 0f

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, com.example.ui.components.ShapeBox6)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = progress)
                    .height(5.dp)
                    .background(
                        if (isCompleted) accentColor else accentColor.copy(alpha = 0.8f),
                        com.example.ui.components.ShapeBox6
                    )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Clean counter stepper buttons, read-only indicator, or rest-day pill
        if (isReadOnly) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NothingPill(
                    text = pastDayTag,
                    isAccent = false
                )
            }
        } else if (!isNotDue) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val canDecrement = count > 0
                val isLargeTarget = targetCount >= 50

                // Decrement button: always stays in place so UI doesn't jump and user cannot miss-click
                val primaryDecStep = if (isLargeTarget) -10 else -1
                val decLabel = if (isLargeTarget && count in 1..9) "-1" else if (primaryDecStep == -10) "-10" else "-1"
                val decValue = if (isLargeTarget && count in 1..9) -1 else primaryDecStep

                Box(
                    modifier = Modifier
                        .background(
                            if (canDecrement) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            com.example.ui.components.ShapeBox8
                        )
                        .clickable(enabled = canDecrement) {
                            if (canDecrement) {
                                onIncrement(taskId, decValue)
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = decLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (canDecrement) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    )
                }

                // For large targets, provide an additional fine-grained -1 button when count >= 10
                if (isLargeTarget && count >= 10) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, com.example.ui.components.ShapeBox8)
                            .clickable { onIncrement(taskId, -1) }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "-1",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Increments: +1, +5, +10 (or +25 for larger goals) from pre-allocated lists
                val stepOptions = when {
                    targetCount >= 100 -> STEP_OPTIONS_LARGE
                    targetCount >= 30 -> STEP_OPTIONS_MEDIUM
                    else -> STEP_OPTIONS_SMALL
                }

                stepOptions.forEach { step ->
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF24252C), com.example.ui.components.ShapeBox8)
                            .border(1.dp, Color(0xFF383A46), com.example.ui.components.ShapeBox8)
                            .clickable { onIncrement(taskId, step) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "+$step",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
