package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MatchFixture
import com.example.model.MatchStatus
import com.example.model.SportType
import com.example.model.StreamFormat
import com.example.model.StreamSource
import com.example.repository.ApiDocumentation
import com.example.ui.components.AppLogoContainer
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyCardBorder
import com.example.ui.theme.TelegramBlue
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    onNavigateToPlaylists: () -> Unit,
    onPlayCustomStream: (MatchFixture, StreamSource) -> Unit,
    apiDoc: ApiDocumentation,
    onTelegramClick: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var forceLowQuality by remember { mutableStateOf(false) }
    var showNetworkStreamDialog by remember { mutableStateOf(false) }
    var customStreamUrl by remember { mutableStateOf("") }
    var customStreamTitle by remember { mutableStateOf("") }
    var showApiDocDialog by remember { mutableStateOf(false) }
    var showScoresDialog by remember { mutableStateOf<String?>(null) }
    var showHighlightsDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NavyBackground)
            .statusBarsPadding()
            .testTag("screen_menu")
    ) {
        // Top Bar (matching screenshot: "Menu" on left, Telegram, Refresh, Search on right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Menu",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(TelegramBlue)
                        .clickable { onTelegramClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Telegram",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                }

                IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Logo & App Name Header (matching screenshot!)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AppLogoContainer(
                        outerSize = 130.dp,
                        innerSize = 88.dp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Football Live Tv",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Version 7.3",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            // Section Header: FEATURES
            item {
                Text(
                    text = "FEATURES",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                )
            }

            // Feature 1: Network Stream
            item {
                MenuFeatureCard(
                    title = "Network Stream",
                    icon = Icons.Default.Link,
                    onClick = { showNetworkStreamDialog = true },
                    testTag = "menu_network_stream"
                )
            }

            // Feature 2: Playlists
            item {
                MenuFeatureCard(
                    title = "Playlists",
                    icon = Icons.AutoMirrored.Filled.List,
                    onClick = onNavigateToPlaylists,
                    testTag = "menu_playlists"
                )
            }

            // Feature 3: Highlights
            item {
                MenuFeatureCard(
                    title = "Highlights",
                    icon = Icons.Default.PlayCircleOutline,
                    onClick = { showHighlightsDialog = true },
                    testTag = "menu_highlights"
                )
            }

            // Feature 4: Cricket Score
            item {
                MenuFeatureCard(
                    title = "Cricket Score",
                    icon = Icons.Default.SportsCricket,
                    onClick = { showScoresDialog = "Cricket" },
                    testTag = "menu_cricket_score"
                )
            }

            // Feature 5: Football Score
            item {
                MenuFeatureCard(
                    title = "Football Score",
                    icon = Icons.Default.SportsSoccer,
                    onClick = { showScoresDialog = "Football" },
                    testTag = "menu_football_score"
                )
            }

            // Feature 6: Floating Player
            item {
                MenuFeatureCard(
                    title = "Floating Player",
                    icon = Icons.Default.PictureInPictureAlt,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            activity?.enterPictureInPictureMode()
                        }
                    },
                    testTag = "menu_floating_player"
                )
            }

            // Feature 7: Force Low Quality toggle card (matching screenshot!)
            item {
                val cardShape = RoundedCornerShape(14.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(cardShape)
                        .background(NavyCard)
                        .border(1.dp, NavyCardBorder, cardShape)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("menu_force_low_quality"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Force Low Quality",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Forces lowest resolution on mobile data",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Switch(
                        checked = forceLowQuality,
                        onCheckedChange = { forceLowQuality = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = CyanAccent,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = Color(0xFF0F1E33)
                        )
                    )
                }
            }

            // Admin / API Backend Integration & Timezone section
            item {
                Text(
                    text = "BACKEND & SYSTEM",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 4.dp)
                )
            }

            item {
                MenuFeatureCard(
                    title = "Sports API Configuration & Schema",
                    icon = Icons.Default.Code,
                    onClick = { showApiDocDialog = true },
                    subtitle = "Field specifications for external fixtures & streams API"
                )
            }

            item {
                MenuFeatureCard(
                    title = "Broadcasting & DMCA Compliance",
                    icon = Icons.Default.Info,
                    onClick = {},
                    subtitle = "All preview feeds use authorized public media test streams (Asia/Dhaka GMT+6)"
                )
            }
        }
    }

    // Network Stream Dialog
    if (showNetworkStreamDialog) {
        BasicAlertDialog(onDismissRequest = { showNetworkStreamDialog = false }) {
            val dialogShape = RoundedCornerShape(20.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(dialogShape)
                    .background(NavyBackground)
                    .border(1.5.dp, CyanAccent, dialogShape)
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        text = "Play Network Stream",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Enter any HLS (.m3u8), DASH (.mpd) or MP4 link",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = customStreamTitle,
                        onValueChange = { customStreamTitle = it },
                        label = { Text("Stream Title", color = TextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = NavyCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = customStreamUrl,
                        onValueChange = { customStreamUrl = it },
                        label = { Text("Stream URL", color = TextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = NavyCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Or fill test HLS stream",
                        color = CyanAccent,
                        fontSize = 11.sp,
                        modifier = Modifier.clickable {
                            customStreamTitle = "Test Mux Stream"
                            customStreamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { showNetworkStreamDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = NavyCard)
                        ) {
                            Text("Cancel", color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (customStreamUrl.isNotBlank()) {
                                    val format = if (customStreamUrl.contains(".mpd")) StreamFormat.DASH else StreamFormat.HLS
                                    val stream = StreamSource(
                                        id = "custom_${System.currentTimeMillis()}",
                                        name = customStreamTitle.ifBlank { "Custom Network Stream" },
                                        qualityLabel = if (format == StreamFormat.DASH) "DASH" else "HLS",
                                        streamUrl = customStreamUrl,
                                        format = format
                                    )
                                    val fixture = MatchFixture(
                                        id = "fix_custom",
                                        title = stream.name,
                                        sport = SportType.FOOTBALL,
                                        league = "NETWORK STREAM",
                                        team1Name = "Live",
                                        team1Abbr = "LIVE",
                                        team2Name = "Feed",
                                        team2Abbr = "FEED",
                                        startTimeEpochMs = System.currentTimeMillis(),
                                        status = MatchStatus.LIVE,
                                        liveCounter = "Broadcasting",
                                        streams = listOf(stream)
                                    )
                                    showNetworkStreamDialog = false
                                    onPlayCustomStream(fixture, stream)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                        ) {
                            Text("Play Stream", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // API Schema Documentation Dialog
    if (showApiDocDialog) {
        BasicAlertDialog(onDismissRequest = { showApiDocDialog = false }) {
            val dialogShape = RoundedCornerShape(20.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(dialogShape)
                    .background(NavyBackground)
                    .border(1.5.dp, CyanAccent, dialogShape)
                    .padding(20.dp)
            ) {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        Text(
                            text = apiDoc.title,
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = apiDoc.description,
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Recommended REST Endpoint:",
                            color = CyanAccent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = apiDoc.endpointSample,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Required JSON Schema:",
                            color = CyanAccent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF070B14))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = apiDoc.jsonSchemaExample,
                                color = Color(0xFF80CBC4),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = apiDoc.backendRecommendation,
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Button(
                                onClick = { showApiDocDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                            ) {
                                Text("Close", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Scores Dialog (Cricket / Football)
    showScoresDialog?.let { sportName ->
        BasicAlertDialog(onDismissRequest = { showScoresDialog = null }) {
            val dialogShape = RoundedCornerShape(20.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(dialogShape)
                    .background(NavyBackground)
                    .border(1.5.dp, CyanAccent, dialogShape)
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        text = "$sportName Live Scores & Stats",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Real-time scorecard integration for Asia/Dhaka matches",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(NavyCard)
                            .padding(14.dp)
                    ) {
                        Column {
                            Text("Flames 2 - 1 Hurricanes", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("OMA vs NEP • Starts in 2h", color = CyanAccent, fontSize = 12.sp)
                            Text("Punjab vs Mumbai City • Starts 8:00 PM (Asia/Dhaka)", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(
                            onClick = { showScoresDialog = null },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                        ) {
                            Text("Dismiss", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Highlights Dialog
    if (showHighlightsDialog) {
        BasicAlertDialog(onDismissRequest = { showHighlightsDialog = false }) {
            val dialogShape = RoundedCornerShape(20.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(dialogShape)
                    .background(NavyBackground)
                    .border(1.5.dp, CyanAccent, dialogShape)
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        text = "Match Highlights & Replays",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("• Arsenal 2 - 1 Man City (Full Highlights)", color = Color.White, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• Real Madrid 3 - 2 Barcelona (El Clasico Recaps)", color = Color.White, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• ICC World Test Championship Top Wickets", color = Color.White, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(
                            onClick = { showHighlightsDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                        ) {
                            Text("Close", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MenuFeatureCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    subtitle: String? = null,
    testTag: String = ""
) {
    val cardShape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(NavyCard)
            .border(1.dp, NavyCardBorder, cardShape)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .then(if (testTag.isNotBlank()) Modifier.testTag(testTag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CyanAccent,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}
