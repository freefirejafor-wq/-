package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanPillBg
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.NavyCardBorder
import com.example.ui.theme.TextMuted

enum class NavTab(val title: String, val icon: ImageVector, val tag: String) {
    FAVOURITES("Favourites", Icons.Default.Star, "nav_favourites"),
    SPORTS("Sports", Icons.Default.SportsSoccer, "nav_sports"),
    LIVE_EVENTS("Live Events", Icons.Default.Podcasts, "nav_live_events"),
    CATEGORIES("Categories", Icons.Default.LiveTv, "nav_categories"),
    MENU("Menu", Icons.AutoMirrored.Filled.List, "nav_menu")
}

@Composable
fun SportsBottomNavBar(
    currentTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(NavyBackground)
            .border(width = 0.8.dp, color = NavyCardBorder)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(64.dp)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavTab.values().forEach { tab ->
            val isSelected = tab == currentTab
            BottomNavItem(
                tab = tab,
                isSelected = isSelected,
                onClick = { onTabSelected(tab) }
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    tab: NavTab,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag(tab.tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Pill container for active icon
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (isSelected) CyanPillBg else Color.Transparent)
                .padding(horizontal = 14.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.title,
                tint = if (isSelected) CyanAccent else TextMuted,
                modifier = Modifier.size(22.dp)
            )
        }

        Text(
            text = tab.title,
            color = if (isSelected) CyanAccent else TextMuted,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
