package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.ui.components.NavTab
import com.example.ui.components.SportsBottomNavBar
import com.example.ui.screens.CategoriesScreen
import com.example.ui.screens.FavouritesScreen
import com.example.ui.screens.LiveEventsScreen
import com.example.ui.screens.MenuScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.SportsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NavyBackground
import com.example.viewmodel.AppScreen
import com.example.viewmodel.SportsViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: SportsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainContent(viewModel: SportsViewModel) {
    val context = LocalContext.current
    val appScreen by viewModel.appScreen.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val selectedSport by viewModel.selectedSport.collectAsState()
    val selectedStatus by viewModel.selectedStatus.collectAsState()
    val fixtures by viewModel.fixtures.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val m3uChannels by viewModel.m3uChannels.collectAsState()

    val activeFixture by viewModel.activeFixture.collectAsState()
    val activeStream by viewModel.activeStream.collectAsState()

    fun openTelegram() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/livefootballtv"))
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Telegram channel: @livefootballtv", Toast.LENGTH_SHORT).show()
        }
    }

    when (appScreen) {
        AppScreen.Splash -> {
            SplashScreen(
                onSplashFinished = { viewModel.finishSplash() }
            )
        }
        AppScreen.Main -> {
            // Check if player is active
            if (activeFixture != null && activeStream != null) {
                PlayerScreen(
                    fixture = activeFixture!!,
                    initialStream = activeStream!!,
                    relatedFixtures = fixtures.filter { it.id != activeFixture!!.id },
                    favoriteIds = favoriteIds,
                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                    onBackClick = { viewModel.closePlayer() },
                    onFixtureClick = { newFixture ->
                        if (newFixture.streams.isNotEmpty()) {
                            viewModel.openPlayer(newFixture, newFixture.streams.first())
                        }
                    }
                )
            } else {
                // Secondary tab back handler -> pop back to Live Events
                if (currentTab != NavTab.LIVE_EVENTS) {
                    BackHandler {
                        viewModel.selectTab(NavTab.LIVE_EVENTS)
                    }
                }

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NavyBackground),
                    bottomBar = {
                        SportsBottomNavBar(
                            currentTab = currentTab,
                            onTabSelected = { viewModel.selectTab(it) }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = innerPadding.calculateBottomPadding())
                            .background(NavyBackground)
                    ) {
                        when (currentTab) {
                            NavTab.LIVE_EVENTS -> {
                                LiveEventsScreen(
                                    fixtures = fixtures,
                                    selectedSport = selectedSport,
                                    onSportSelected = { viewModel.selectSport(it) },
                                    selectedStatus = selectedStatus,
                                    onStatusSelected = { viewModel.selectStatus(it) },
                                    favoriteIds = favoriteIds,
                                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                                    onLaunchStream = { fixture, stream ->
                                        viewModel.openPlayer(fixture, stream)
                                    },
                                    onRefresh = { viewModel.refresh() },
                                    onTelegramClick = { openTelegram() }
                                )
                            }
                            NavTab.FAVOURITES -> {
                                val favoriteFixtures = fixtures.filter { favoriteIds.contains(it.id) }
                                FavouritesScreen(
                                    favoriteFixtures = favoriteFixtures,
                                    onFixtureClick = { fix, stream ->
                                        viewModel.openPlayer(fix, stream)
                                    },
                                    onToggleFavorite = { viewModel.toggleFavorite(it) }
                                )
                            }
                            NavTab.SPORTS -> {
                                SportsScreen(
                                    fixtures = fixtures,
                                    onSportSelected = { sport ->
                                        viewModel.selectSport(sport)
                                        viewModel.selectTab(NavTab.LIVE_EVENTS)
                                    }
                                )
                            }
                            NavTab.CATEGORIES -> {
                                CategoriesScreen(
                                    channels = m3uChannels,
                                    onImportUrl = { url -> viewModel.importM3uUrl(url) },
                                    onImportText = { text -> viewModel.importM3uText(text) },
                                    onPlayChannel = { fixture, stream ->
                                        viewModel.openPlayer(fixture, stream)
                                    }
                                )
                            }
                            NavTab.MENU -> {
                                MenuScreen(
                                    onNavigateToPlaylists = { viewModel.selectTab(NavTab.CATEGORIES) },
                                    onPlayCustomStream = { fixture, stream ->
                                        viewModel.openPlayer(fixture, stream)
                                    },
                                    apiDoc = viewModel.getApiDocs(),
                                    onTelegramClick = { openTelegram() },
                                    onRefresh = { viewModel.refresh() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
