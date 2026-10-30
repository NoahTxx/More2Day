package com.example.ui.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.AppStrings
import com.example.util.LocalAccentColor
import com.example.util.LocalAppLanguage
import com.example.util.LocalBodyFontFamily
import com.example.util.LocalHeadingFontFamily

// Pre-allocated shapes - modern clean rounded corners
val ShapePill = RoundedCornerShape(100.dp)
val ShapeCardLarge = RoundedCornerShape(20.dp)
val ShapeCardMedium = RoundedCornerShape(16.dp)
val ShapeCardSmall = RoundedCornerShape(12.dp)
val ShapeBox8 = RoundedCornerShape(8.dp)
val ShapeBox6 = RoundedCornerShape(6.dp)

@Composable
fun NothingCard(
    modifier: Modifier = Modifier,
    shape: Shape = ShapeCardLarge,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember(onClick) { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    } else Modifier

    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor, shape)
            .border(1.dp, borderColor, shape)
            .then(clickableModifier)
    ) {
        content()
    }
}

@Composable
fun NothingCheckbox(
    checked: Boolean,
    onCheckedChange: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp,
    enabled: Boolean = true,
    testTag: String = "habit_checkbox"
) {
    val accentColor = LocalAccentColor.current
    val outlineColor = MaterialTheme.colorScheme.outline
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant

    val clickModifier = if (enabled) {
        Modifier.clickable(
            interactionSource = remember(onCheckedChange) { MutableInteractionSource() },
            indication = null,
            onClick = onCheckedChange
        )
    } else Modifier

    Box(
        modifier = modifier
            .size(size)
            .testTag(testTag)
            .clip(CircleShape)
            .background(
                if (checked) accentColor else surfaceColor.copy(alpha = 0.5f),
                CircleShape
            )
            .border(
                width = if (checked) 0.dp else 1.5.dp,
                color = if (checked) Color.Transparent else outlineColor,
                shape = CircleShape
            )
            .then(clickModifier),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            val checkColor = if (accentColor == Color(0xFFF5F5F7) || accentColor == Color(0xFFF8FAFC)) Color.Black else Color.White
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Completed",
                tint = checkColor,
                modifier = Modifier.size(size * 0.62f)
            )
        }
    }
}

@Composable
fun NothingPill(
    text: String,
    modifier: Modifier = Modifier,
    isAccent: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val accentColor = LocalAccentColor.current
    val bg = if (isAccent) accentColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isAccent) accentColor else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .clip(ShapePill)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium
            ),
            color = textColor,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun NothingHeader(
    title: String,
    subtitle: String? = null,
    badgeText: String? = null,
    badgeFontSize: TextUnit = 20.sp,
    badgeFontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    modifier: Modifier = Modifier
) {
    val accentColor = LocalAccentColor.current
    val headingFont = LocalHeadingFontFamily.current
    val bodyFont = LocalBodyFontFamily.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = if (badgeText != null) {
                Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            } else {
                Modifier.fillMaxWidth()
            }
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = headingFont,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                ),
                maxLines = 1,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = bodyFont,
                        lineHeight = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }

        if (badgeText != null) {
            Box(
                modifier = Modifier
                    .clip(ShapePill)
                    .background(accentColor.copy(alpha = 0.12f))
                    .border(1.dp, accentColor.copy(alpha = 0.3f), ShapePill)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = badgeFontFamily ?: headingFont,
                        fontSize = badgeFontSize,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp
                    ),
                    color = accentColor,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun DotMatrixProgress(
    total: Int,
    completed: Int,
    modifier: Modifier = Modifier
) {
    if (total <= 0) return
    val accentColor = LocalAccentColor.current
    val progress = (completed.toFloat() / total.toFloat()).coerceIn(0f, 1f)

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(ShapePill)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = progress)
                    .height(8.dp)
                    .clip(ShapePill)
                    .background(accentColor)
            )
        }
    }
}

@Composable
fun NotificationPermissionCard(
    onGrantClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = LocalAccentColor.current
    val language = LocalAppLanguage.current
    val headingFont = LocalHeadingFontFamily.current
    val bodyFont = LocalBodyFontFamily.current

    val title = AppStrings.get("perm_title", language)
    val description = AppStrings.get("perm_desc", language)
    val buttonText = AppStrings.get("perm_allow", language)

    NothingCard(
        modifier = modifier.fillMaxWidth(),
        shape = ShapeCardMedium,
        backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
        borderColor = accentColor.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = headingFont,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = bodyFont),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .clip(ShapePill)
                    .background(accentColor)
                    .clickable(onClick = onGrantClick)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = buttonText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = bodyFont,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
            }

            IconButton(
                onClick = onDismissClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun TaskSectionHeader(
    title: String,
    count: Int,
    subtitle: String? = null,
    isAccent: Boolean = false,
    modifier: Modifier = Modifier
) {
    val accentColor = LocalAccentColor.current
    val headingFont = LocalHeadingFontFamily.current
    val bodyFont = LocalBodyFontFamily.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = headingFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    letterSpacing = 0.2.sp
                ),
                color = if (isAccent) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "• $subtitle",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = bodyFont,
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .clip(ShapePill)
                .background(if (isAccent) accentColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 9.dp, vertical = 3.dp)
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = bodyFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                ),
                color = if (isAccent) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
