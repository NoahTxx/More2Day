package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.BrandBlack

/**
 * The bare More2Day logo mark (white "M" + orange sun dot), transparent background.
 */
@Composable
fun More2DayLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    contentDescription: String? = "More2Day"
) {
    Image(
        painter = painterResource(id = R.drawable.ic_more2day_logo),
        contentDescription = contentDescription,
        modifier = modifier.size(size)
    )
}

/**
 * The More2Day logo as an app-icon style tile (black rounded square), matching the launcher icon.
 */
@Composable
fun More2DayLogoTile(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    borderColor: Color = Color(0xFF282A33),
    contentDescription: String? = "More2Day"
) {
    val shape = RoundedCornerShape(size * 0.28f)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(BrandBlack)
            .border(1.dp, borderColor, shape),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_more2day_logo),
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .padding(size * 0.2f)
        )
    }
}

@Preview(backgroundColor = 0xFF111216, showBackground = true)
@Composable
private fun More2DayLogoTilePreview() {
    More2DayLogoTile(size = 96.dp)
}
