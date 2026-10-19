package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.M3uChannel
import com.example.model.MatchFixture
import com.example.model.MatchStatus
import com.example.model.SportType
import com.example.model.StreamSource
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanPillBg
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyCardBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    channels: List<M3uChannel>,
    onImportUrl: suspend (String) -> Result<Int>,
    onImportText: suspend (String) -> Result<Int>,
    onPlayChannel: (MatchFixture, StreamSource) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedGroup by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }
    var importUrlInput by remember { mutableStateOf("") }
    var importTextInput by remember { mutableStateOf("") }
    var importMode by remember { mutableStateOf(0) } // 0: URL, 1: Raw Text
    var isImporting by remember { mutableStateOf(false) }
    var importResultMsg by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()

    val groups = remember(channels) {
        listOf("All") + channels.map { it.group }.distinct()
    }

    val filteredChannels = remember(channels, selectedGroup, searchQuery) {
        channels.filter { ch ->
            val matchesGroup = (selectedGroup == "All") || (ch.group.equals(selectedGroup, ignoreCase = true))
            val matchesQuery = searchQuery.isBlank() || ch.name.contains(searchQuery, ignoreCase = true)
            matchesGroup && matchesQuery
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NavyBackground)
            .statusBarsPadding()
            .testTag("screen_categories")
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Categories & Playlists",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${channels.size} Channels Available",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = {
                    showImportDialog = true
                    importResultMsg = null
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.testTag("btn_open_import_dialog")
            ) {
                Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import M3U", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        // Search Channels input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search channel or category...", color = TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted)
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyanAccent,
                unfocusedBorderColor = NavyCardBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = CyanAccent,
                focusedContainerColor = NavyCard,
                unfocusedContainerColor = NavyCard
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag("search_channels_input")
        )

        // Group / Category Chips
        LazyRow(
            modifier = Modifier.padding(vertical = 8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(groups) { group ->
                val isSelected = group == selectedGroup
                val pillShape = RoundedCornerShape(20.dp)

                Box(
                    modifier = Modifier
                        .clip(pillShape)
                        .background(if (isSelected) CyanPillBg else NavyCard)
                        .border(
                            width = 1.2.dp,
                            color = if (isSelected) CyanAccent else NavyCardBorder,
                            shape = pillShape
                        )
                        .clickable { selectedGroup = group }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = group,
                        color = if (isSelected) CyanAccent else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        // Channels List
        if (filteredChannels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.LiveTv,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No channels found", color = TextSecondary, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredChannels, key = { it.id }) { channel ->
                    ChannelListItem(
                        channel = channel,
                        onPlayClick = {
                            val tempFixture = MatchFixture(
                                id = "channel_${channel.id}",
                                title = channel.name,
                                sport = SportType.FOOTBALL,
                                league = "LIVE TV | ${channel.group.uppercase()}",
                                team1Name = channel.name,
                                team1Abbr = channel.name.take(3).uppercase(),
                                team2Name = "Live Feed",
                                team2Abbr = "LIVE",
                                startTimeEpochMs = System.currentTimeMillis(),
                                status = MatchStatus.LIVE,
                                liveCounter = "Broadcasting",
                                streams = listOf(
                                    StreamSource(
                                        id = "src_${channel.id}",
                                        name = channel.name,
                                        qualityLabel = if (channel.streamUrl.contains(".mpd")) "DASH" else "HLS",
                                        streamUrl = channel.streamUrl,
                                        format = channel.format
                                    )
                                ),
                                isDemo = false
                            )
                            onPlayChannel(tempFixture, tempFixture.streams.first())
                        }
                    )
                }
            }
        }
    }

    // Import M3U Dialog
    if (showImportDialog) {
        BasicAlertDialog(
            onDismissRequest = { showImportDialog = false }
        ) {
            val dialogShape = RoundedCornerShape(22.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(dialogShape)
                    .background(NavyBackground)
                    .border(1.5.dp, CyanAccent, dialogShape)
                    .padding(20.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Import M3U Playlist",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Add IPTV stream channels via URL or raw text",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Mode switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(NavyCard)
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (importMode == 0) CyanAccent else Color.Transparent)
                                .clickable { importMode = 0 }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Playlist URL", color = if (importMode == 0) Color.Black else TextSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (importMode == 1) CyanAccent else Color.Transparent)
                                .clickable { importMode = 1 }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Paste M3U Text", color = if (importMode == 1) Color.Black else TextSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (importMode == 0) {
                        OutlinedTextField(
                            value = importUrlInput,
                            onValueChange = { importUrlInput = it },
                            placeholder = { Text("https://example.com/playlist.m3u", color = TextMuted, fontSize = 12.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanAccent,
                                unfocusedBorderColor = NavyCardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = CyanAccent
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("import_url_input")
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Or try demo public sports stream link",
                            color = CyanAccent,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clickable {
                                    importUrlInput = "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/sports.m3u"
                                }
                                .padding(vertical = 2.dp)
                        )
                    } else {
                        OutlinedTextField(
                            value = importTextInput,
                            onValueChange = { importTextInput = it },
                            placeholder = { Text("#EXTM3U\n#EXTINF:-1 group-title=\"Sports\",Channel 1\nhttps://...", color = TextMuted, fontSize = 11.sp) },
                            maxLines = 6,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanAccent,
                                unfocusedBorderColor = NavyCardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = CyanAccent
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .testTag("import_text_input")
                        )
                    }

                    if (importResultMsg != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = importResultMsg ?: "",
                            color = if (importResultMsg!!.startsWith("Success")) CyanAccent else Color(0xFFFF5252),
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { showImportDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = NavyCard),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Cancel", color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                isImporting = true
                                importResultMsg = null
                                coroutineScope.launch {
                                    val result = if (importMode == 0) {
                                        onImportUrl(importUrlInput)
                                    } else {
                                        onImportText(importTextInput)
                                    }
                                    isImporting = false
                                    result.onSuccess { count ->
                                        importResultMsg = "Success: Imported $count channels!"
                                        showImportDialog = false
                                    }.onFailure { err ->
                                        importResultMsg = "Error: ${err.message}"
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !isImporting,
                            modifier = Modifier.testTag("btn_submit_import")
                        ) {
                            if (isImporting) {
                                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(16.dp))
                            } else {
                                Text("Import", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelListItem(
    channel: M3uChannel,
    onPlayClick: () -> Unit
) {
    val cardShape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(NavyCard)
            .border(1.dp, NavyCardBorder, cardShape)
            .clickable { onPlayClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("channel_item_${channel.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0F2636)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LiveTv,
                    contentDescription = null,
                    tint = CyanAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = channel.name,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = channel.group,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(CyanPillBg)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = channel.format.name,
                    color = CyanAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(CyanAccent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
