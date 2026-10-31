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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
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
            val headingFontFamily = GeistFontFamily
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
    val currentLang = LocalAppLanguage.current
    val bodyFont = LocalBodyFontFamily.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF141519))
    ) {
        // Clean top hairline border only (no side borders)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(Color(0xFF23252E))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 2.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DailyNavTab.entries.forEach { tab ->
                HabitsBottomNavItem(
                    tab = tab,
                    isSelected = tab == currentTab,
                    currentLang = currentLang,
                    bodyFont = bodyFont,
                    onTabSelected = onTabSelected
                )
            }
        }
    }
}

@Composable
private fun HabitsBottomNavItem(
    tab: DailyNavTab,
    isSelected: Boolean,
    currentLang: com.example.util.AppLanguage,
    bodyFont: FontFamily,
    onTabSelected: (DailyNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isEn = currentLang == AppLanguage.EN

    val (tabTitle, iconVector) = remember(tab, currentLang) {
        when (tab) {
            DailyNavTab.BLOCKS -> (if (isEn) "Blocks" else "Blocks") to Icons.Outlined.Security
            DailyNavTab.STATS -> (if (isEn) "Einblicke" else "Einblicke") to Icons.Outlined.BarChart
            DailyNavTab.TODAY -> "Habits" to Icons.Outlined.CheckCircle
            DailyNavTab.SETTINGS -> (if (isEn) "Einstellungen" else "Einstellungen") to Icons.Outlined.Settings
        }
    }

    val iconColor = if (isSelected) Color.White else Color(0xFF71717A)
    val textColor = if (isSelected) Color.White else Color(0xFF71717A)

    Column(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onTabSelected(tab) }
            )
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .testTag("nav_tab_${tab.name.lowercase()}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isSelected) Color(0xFF282A33) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = tabTitle,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = tabTitle,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = bodyFont,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 10.sp
            ),
            color = textColor
        )
    }
}
