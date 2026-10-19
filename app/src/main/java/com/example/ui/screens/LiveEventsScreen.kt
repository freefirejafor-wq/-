package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MatchFixture
import com.example.model.MatchStatus
import com.example.model.SportType
import com.example.model.StreamSource
import com.example.ui.components.FixtureCard
import com.example.ui.components.SportCategoryBar
import com.example.ui.components.SportsTopAppBar
import com.example.ui.components.StatusFilterBar
import com.example.ui.components.StreamPickerModal
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyCardBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun LiveEventsScreen(
    fixtures: List<MatchFixture>,
    selectedSport: SportType,
    onSportSelected: (SportType) -> Unit,
    selectedStatus: MatchStatus,
    onStatusSelected: (MatchStatus) -> Unit,
    favoriteIds: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onLaunchStream: (MatchFixture, StreamSource) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean = false,
    refreshError: String? = null,
    onTelegramClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }
    var pickerFixture by remember { mutableStateOf<MatchFixture?>(null) }

    // Calculate dynamic counts for status pills
    val counts = remember(fixtures, selectedSport) {
        val filteredBySport = if (selectedSport == SportType.ALL) {
            fixtures
        } else {
            fixtures.filter { it.sport == selectedSport }
        }

        mapOf(
            MatchStatus.ALL to filteredBySport.size,
            MatchStatus.LIVE to filteredBySport.count { it.status == MatchStatus.LIVE },
            MatchStatus.UPCOMING to filteredBySport.count { it.status == MatchStatus.UPCOMING },
            MatchStatus.FINISHED to filteredBySport.count { it.status == MatchStatus.FINISHED }
        )
    }

    // Filter fixtures
    val displayedFixtures = remember(fixtures, selectedSport, selectedStatus, searchQuery) {
        fixtures.filter { f ->
            val matchesSport = (selectedSport == SportType.ALL) || (f.sport == selectedSport)
            val matchesStatus = (selectedStatus == MatchStatus.ALL) || (f.status == selectedStatus)
            val matchesQuery = searchQuery.isBlank() ||
                f.title.contains(searchQuery, ignoreCase = true) ||
                f.league.contains(searchQuery, ignoreCase = true) ||
                f.team1Name.contains(searchQuery, ignoreCase = true) ||
                f.team2Name.contains(searchQuery, ignoreCase = true)

            matchesSport && matchesStatus && matchesQuery
        }
    }

    // Modal stream picker
    pickerFixture?.let { fix ->
        StreamPickerModal(
            fixture = fix,
            onStreamSelected = { selectedSource ->
                pickerFixture = null
                onLaunchStream(fix, selectedSource)
            },
            onDismiss = { pickerFixture = null }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NavyBackground)
            .testTag("screen_live_events")
    ) {
        // Top App Bar
        SportsTopAppBar(
            title = "Football Live Tv",
            onTelegramClick = onTelegramClick,
            onRefreshClick = onRefresh,
            onSearchClick = { isSearchVisible = !isSearchVisible }
        )

        // Search Bar (Expandable)
        AnimatedVisibility(
            visible = isSearchVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by team, league or match...", color = TextMuted) },
                    singleLine = true,
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted)
                            }
                        }
                    },
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
                        .testTag("search_matches_input")
                )
            }
        }

        // Horizontal Sports Category Icons
        SportCategoryBar(
            selectedSport = selectedSport,
            onSportSelected = onSportSelected,
            cricketMatchCount = fixtures.count { it.sport == SportType.CRICKET }
        )

        // Status Filter Pills Bar
        StatusFilterBar(
            selectedStatus = selectedStatus,
            onStatusSelected = onStatusSelected,
            counts = counts
        )

        // Fixtures List
        if (displayedFixtures.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    if (selectedSport == SportType.CRICKET && isRefreshing) {
                        CircularProgressIndicator(
                            color = CyanAccent,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Podcasts,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                    Text(
                        text = when {
                            selectedSport == SportType.CRICKET && isRefreshing -> "Loading cricket matches…"
                            selectedSport == SportType.CRICKET && refreshError != null -> refreshError
                            else -> "No matches found in this category"
                        },
                        color = TextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            onSportSelected(SportType.ALL)
                            onStatusSelected(MatchStatus.ALL)
                            searchQuery = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyCard),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NavyCardBorder),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Reset Filters", color = CyanAccent, fontSize = 13.sp)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("list_fixtures"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(displayedFixtures, key = { it.id }) { fixture ->
                    FixtureCard(
                        fixture = fixture,
                        isFavorite = favoriteIds.contains(fixture.id),
                        onCardClick = {
                            if (fixture.streams.size > 1) {
                                pickerFixture = fixture
                            } else if (fixture.streams.isNotEmpty()) {
                                onLaunchStream(fixture, fixture.streams.first())
                            }
                        },
                        onToggleFavorite = { onToggleFavorite(fixture.id) }
                    )
                }
            }
        }
    }
}
