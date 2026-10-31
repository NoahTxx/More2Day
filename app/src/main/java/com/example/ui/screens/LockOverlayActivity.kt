package com.example.ui.screens

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainActivity
import com.example.data.DailyDatabase
import com.example.data.DailyTask
import com.example.ui.components.NothingCard
import com.example.ui.components.ShapeCardLarge
import com.example.ui.components.ShapeCardMedium
import com.example.ui.components.ShapePill
import com.example.ui.theme.GeistFontFamily
import com.example.ui.theme.MyApplicationTheme
import com.example.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LockOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val blockedPackage = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE) ?: ""

        setContent {
            MyApplicationTheme {
                LockOverlayContent(
                    blockedPackage = blockedPackage,
                    onOpenMore2Day = {
                        val mainIntent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(mainIntent)
                        finish()
                    },
                    onClose = {
                        goToHomeScreen()
                    }
                )
            }
        }
    }

    private fun goToHomeScreen() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    override fun onStop() {
        super.onStop()
        finish()
    }

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "extra_blocked_package"
    }
}

@Composable
fun LockOverlayContent(
    blockedPackage: String,
    onOpenMore2Day: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler {
        onClose()
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    var blockedAppName by remember { mutableStateOf(blockedPackage) }
    val openTasks = remember { mutableStateListOf<DailyTask>() }

    LaunchedEffect(blockedPackage) {
        withContext(Dispatchers.IO) {
            // Resolve app name
            if (blockedPackage.isNotEmpty()) {
                try {
                    val pm = context.packageManager
                    val appInfo = pm.getApplicationInfo(blockedPackage, 0)
                    blockedAppName = pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    blockedAppName = blockedPackage
                }
            }

            // Resolve open habits for today
            try {
                val db = DailyDatabase.getInstance(context)
                val todayKey = DateUtils.getTodayKey()
                val activeTasks = db.dailyTaskDao().getAllActiveTasksSync()
                val completions = db.dailyTaskDao().getCompletionsForDateSync(todayKey)
                val completedIds = completions.map { it.taskId }.toSet()

                val dueTasks = activeTasks.filter { task ->
                    !completedIds.contains(task.id)
                }
                withContext(Dispatchers.Main) {
                    openTasks.clear()
                    openTasks.addAll(dueTasks)
                }
            } catch (e: Exception) {
                // Ignore DB error
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111216))
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Lock Icon Badge
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1C1D22))
                    .border(1.5.dp, Color(0xFF282A33), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = "Gesperrt",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Headline
            Text(
                text = "Erst die Gewohnheiten,\ndann scrollen.",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = GeistFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    lineHeight = 30.sp,
                    textAlign = TextAlign.Center
                ),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle
            Text(
                text = if (blockedAppName.isNotEmpty() && blockedAppName != blockedPackage) {
                    "$blockedAppName ist gesperrt, bis du deine heutigen Gewohnheiten erledigt hast."
                } else {
                    "Diese App ist gesperrt, bis du deine heutigen Gewohnheiten erledigt hast."
                },
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = GeistFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center
                ),
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Remaining Habits List Card
            if (openTasks.isNotEmpty()) {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "NOCH OFFEN HEUTE (${openTasks.size}):",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = GeistFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    letterSpacing = 0.5.sp
                                ),
                                color = Color(0xFF94A3B8)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (openTasks.size > 3) 130.dp else (openTasks.size * 36).dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(openTasks, key = { it.id }) { task ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = task.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = GeistFontFamily,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.sp
                                        ),
                                        color = Color.White,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (task.category.isNotEmpty()) {
                                        Text(
                                            text = task.category,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = GeistFontFamily,
                                                fontSize = 10.sp
                                            ),
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Primary Button: "Schließen"
                Button(
                    onClick = onClose,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = ShapePill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF24252C),
                        contentColor = Color.White
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF383A46))
                ) {
                    Text(
                        text = "Schließen",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = GeistFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    )
                }

                // Secondary Text Button: "In More2Day erledigen"
                Button(
                    onClick = onOpenMore2Day,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = ShapePill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color(0xFF94A3B8)
                    )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.OpenInNew,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "In More2Day erledigen",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = GeistFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
