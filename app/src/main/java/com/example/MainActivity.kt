package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.DailyTask
import com.example.ui.DailyViewModel
import com.example.ui.screens.BlocksScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StatsScreen
import com.example.ui.screens.TaskEditDialog
import com.example.ui.screens.TodayScreen
import com.example.ui.theme.CourierPrimeFontFamily
import com.example.ui.theme.GeistFontFamily
import com.example.ui.theme.MyApplicationTheme
import com.example.util.AppAccentColor
import com.example.util.AppSettings
import com.example.util.AppStrings
import com.example.util.DateUtils
import com.example.util.LocalAccentColor
import com.example.util.LocalAppLanguage
import com.example.util.LocalBodyFontFamily
import com.example.util.LocalHeadingFontFamily
import com.example.util.AppLanguage

enum class DailyNavTab {
    TODAY,
    BLOCKS,
    STATS,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private val viewModel: DailyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val appSettings = remember { AppSettings.getInstance(context) }
            val headingFontFamily = CourierPrimeFontFamily
            val bodyFontFamily = GeistFontFamily

            CompositionLocalProvider(
                LocalAccentColor provides AppAccentColor,
                LocalAppLanguage provides AppLanguage.DE,
                LocalHeadingFontFamily provides headingFontFamily,
                LocalBodyFontFamily provides bodyFontFamily
            ) {
                MyApplicationTheme(
                    accentColor = AppAccentColor,
                    headingFont = headingFontFamily,
                    bodyFont = bodyFontFamily
                ) {
                    MainContent(viewModel = viewModel, appSettings = appSettings)
                }
            }
        }
    }
}

@Suppress("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainContent(viewModel: DailyViewModel, appSettings: AppSettings) {
    var currentTab by rememberSaveable { mutableStateOf(DailyNavTab.TODAY) }
    var showEditSheet by rememberSaveable { mutableStateOf(false) }
    var taskToEdit by rememberSaveable { mutableStateOf<DailyTask?>(null) }

    val selectedDateKey by viewModel.selectedDateKey.collectAsStateWithLifecycle()
    val tasksWithCompletions by viewModel.tasksWithCompletions.collectAsStateWithLifecycle()
    val allActiveTasks by viewModel.allActiveTasks.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val currentStreak by viewModel.currentStreak.collectAsStateWithLifecycle()
    val streakStats by viewModel.streakStats.collectAsStateWithLifecycle()
    val allCompletionDates by viewModel.allCompletionDates.collectAsStateWithLifecycle()

    val openTaskEditor: (DailyTask?) -> Unit = remember {
        { task ->
            taskToEdit = task
            showEditSheet = true
        }
    }
    val dismissTaskEditor = remember {
        {
            showEditSheet = false
            taskToEdit = null
        }
    }
    val onAddTaskClick = remember {
        {
            taskToEdit = null
            showEditSheet = true
        }
    }

    BackHandler(enabled = currentTab != DailyNavTab.TODAY) {
        currentTab = DailyNavTab.TODAY
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { _ ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            when (currentTab) {
                DailyNavTab.TODAY -> {
                    TodayScreen(
                        viewModel = viewModel,
                        selectedDateKey = selectedDateKey,
                        tasks = tasksWithCompletions,
                        selectedCategory = selectedCategory,
                        currentStreak = currentStreak,
                        completionDates = allCompletionDates,
                        onAddTaskClick = onAddTaskClick,
                        onEditTaskClick = { task ->
                            if (DateUtils.isToday(selectedDateKey)) {
                                openTaskEditor(task)
                            }
                        }
                    )
                }

                DailyNavTab.BLOCKS -> {
                    BlocksScreen(
                        viewModel = viewModel,
                        tasks = tasksWithCompletions
                    )
                }

                DailyNavTab.STATS -> {
                    StatsScreen(
                        viewModel = viewModel,
                        streakStats = streakStats,
                        completionDates = allCompletionDates,
                        totalTasksCount = allActiveTasks.size
                    )
                }

                DailyNavTab.SETTINGS -> {
                    SettingsScreen(
                        appSettings = appSettings
                    )
                }
            }

            NothingBottomNavigation(
                currentTab = currentTab,
                onTabSelected = { currentTab = it },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        if (showEditSheet) {
            TaskEditDialog(
                task = taskToEdit,
                onDismiss = dismissTaskEditor,
                onSave = { updatedTask ->
                    viewModel.saveTask(updatedTask)
                    dismissTaskEditor()
                },
                onDelete = { taskToDelete ->
                    viewModel.deleteTask(taskToDelete)
                    dismissTaskEditor()
                }
            )
        }
    }
}

@Composable
fun NothingBottomNavigation(
    currentTab: DailyNavTab,
    onTabSelected: (DailyNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = LocalAccentColor.current
    val currentLang = LocalAppLanguage.current
    val bodyFont = LocalBodyFontFamily.current

    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 12.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(32.dp)
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DailyNavTab.entries.forEach { tab ->
                ModernBottomNavItem(
                    tab = tab,
                    isSelected = tab == currentTab,
                    currentLang = currentLang,
                    accentColor = accentColor,
                    bodyFont = bodyFont,
                    onTabSelected = onTabSelected
                )
            }
        }
    }
}

@Composable
private fun ModernBottomNavItem(
    tab: DailyNavTab,
    isSelected: Boolean,
    currentLang: com.example.util.AppLanguage,
    accentColor: Color,
    bodyFont: FontFamily,
    onTabSelected: (DailyNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val contentColor = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
    val bgColor = if (isSelected) accentColor.copy(alpha = 0.12f) else Color.Transparent

    val (tabTitle, iconVector) = remember(tab, currentLang) {
        val isEn = currentLang == AppLanguage.EN
        when (tab) {
            DailyNavTab.TODAY -> "Habits" to Icons.Outlined.CheckCircle
            DailyNavTab.BLOCKS -> (if (isEn) "Blocks" else "Sperren") to Icons.Outlined.Lock
            DailyNavTab.STATS -> (if (isEn) "Insights" else "Einblicke") to Icons.Outlined.BarChart
            DailyNavTab.SETTINGS -> (if (isEn) "Settings" else "Optionen") to Icons.Outlined.Settings
        }
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onTabSelected(tab) }
            )
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("nav_tab_${tab.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = iconVector,
            contentDescription = tabTitle,
            tint = contentColor,
            modifier = Modifier.size(17.dp)
        )
        if (isSelected) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = tabTitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = bodyFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                ),
                color = contentColor
            )
        }
    }
}
