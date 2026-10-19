package com.example.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MatchStatus
import com.example.model.SportType
import com.example.ui.theme.BadgeCountRed
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanPillBg
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyCardBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SportCategoryBar(
    selectedSport: SportType,
    onSportSelected: (SportType) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(SportType.values()) { sport ->
            val isSelected = sport == selectedSport
            SportCategoryItem(
                sport = sport,
                isSelected = isSelected,
                onClick = { onSportSelected(sport) }
            )
        }
    }
}

@Composable
fun SportCategoryItem(
    sport: SportType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) CyanAccent else NavyCardBorder
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = Modifier
            .size(width = 62.dp, height = 66.dp)
            .clip(shape)
            .background(NavyCard)
            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, shape)
            .clickable { onClick() }
            .testTag("sport_category_${sport.name.lowercase()}")
    ) {
        // Red badge count on top left
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 4.dp, y = 4.dp)
                .clip(CircleShape)
                .background(BadgeCountRed)
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Text(
                text = "${sport.badgeCount}",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Sport Icon and Label centered
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 10.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            SportIconCanvas(sport = sport, isSelected = isSelected)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = sport.displayName,
                color = if (isSelected) CyanAccent else TextSecondary,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SportIconCanvas(
    sport: SportType,
    isSelected: Boolean
) {
    val tint = if (isSelected) CyanAccent else TextPrimary

    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height

        when (sport) {
            SportType.ALL -> {
                drawCircle(color = tint, radius = w * 0.45f, style = Stroke(width = 2f))
                drawCircle(color = tint, radius = w * 0.32f, style = Stroke(width = 1.5f))
            }
            SportType.CRICKET -> {
                // 3 stumps
                drawLine(tint, Offset(w * 0.3f, h * 0.85f), Offset(w * 0.3f, h * 0.35f), strokeWidth = 2.5f)
                drawLine(tint, Offset(w * 0.5f, h * 0.85f), Offset(w * 0.5f, h * 0.35f), strokeWidth = 2.5f)
                drawLine(tint, Offset(w * 0.7f, h * 0.85f), Offset(w * 0.7f, h * 0.35f), strokeWidth = 2.5f)
                // bails
                drawLine(tint, Offset(w * 0.22f, h * 0.32f), Offset(w * 0.78f, h * 0.32f), strokeWidth = 2f)
                // small cricket ball
                drawCircle(color = tint, radius = w * 0.12f, center = Offset(w * 0.82f, h * 0.65f))
            }
            SportType.FOOTBALL -> {
                drawCircle(color = tint, radius = w * 0.42f, style = Stroke(width = 2f))
                drawCircle(color = tint, radius = w * 0.16f)
                // speed trails
                drawLine(tint, Offset(w * 0.1f, h * 0.2f), Offset(w * 0.35f, h * 0.4f), strokeWidth = 1.5f)
                drawLine(tint, Offset(w * 0.05f, h * 0.5f), Offset(w * 0.25f, h * 0.5f), strokeWidth = 1.5f)
            }
        }
    }
}

@Composable
fun StatusFilterBar(
    selectedStatus: MatchStatus,
    onStatusSelected: (MatchStatus) -> Unit,
    counts: Map<MatchStatus, Int>,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(MatchStatus.values()) { status ->
            val isSelected = status == selectedStatus
            val count = counts[status] ?: 0
            val shape = RoundedCornerShape(20.dp)

            Row(
                modifier = Modifier
                    .clip(shape)
                    .background(if (isSelected) CyanPillBg else NavyCard)
                    .border(
                        width = 1.2.dp,
                        color = if (isSelected) CyanAccent else NavyCardBorder,
                        shape = shape
                    )
                    .clickable { onStatusSelected(status) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("filter_status_${status.name.lowercase()}"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = CyanAccent,
                        modifier = Modifier
                            .size(14.dp)
                            .padding(end = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = "${status.label} ($count)",
                    color = if (isSelected) Color.White else TextMuted,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}
