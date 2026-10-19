package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.M3uChannel
import com.example.model.MatchFixture
import com.example.model.MatchStatus
import com.example.model.SportType
import com.example.model.StreamFormat
import com.example.model.StreamSource
import com.example.repository.SportsRepository
import com.example.repository.SportsRepositoryImpl
import com.example.ui.components.NavTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AppScreen {
    object Splash : AppScreen
    object Main : AppScreen
}

class SportsViewModel(
    private val repository: SportsRepository = SportsRepositoryImpl()
) : ViewModel() {

    private val _appScreen = MutableStateFlow<AppScreen>(AppScreen.Splash)
    val appScreen: StateFlow<AppScreen> = _appScreen.asStateFlow()

    private val _currentTab = MutableStateFlow(NavTab.LIVE_EVENTS)
    val currentTab: StateFlow<NavTab> = _currentTab.asStateFlow()

    private val _selectedSport = MutableStateFlow(SportType.ALL)
    val selectedSport: StateFlow<SportType> = _selectedSport.asStateFlow()

    private val _selectedStatus = MutableStateFlow(MatchStatus.ALL)
    val selectedStatus: StateFlow<MatchStatus> = _selectedStatus.asStateFlow()

    private val _activeFixture = MutableStateFlow<MatchFixture?>(null)
    val activeFixture: StateFlow<MatchFixture?> = _activeFixture.asStateFlow()

    private val _activeStream = MutableStateFlow<StreamSource?>(null)
    val activeStream: StateFlow<StreamSource?> = _activeStream.asStateFlow()

    val fixtures: StateFlow<List<MatchFixture>> = repository.fixturesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteIds: StateFlow<Set<String>> = repository.favoritesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val m3uChannels: StateFlow<List<M3uChannel>> = repository.m3uChannelsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun finishSplash() {
        _appScreen.value = AppScreen.Main
    }

    fun selectTab(tab: NavTab) {
        _currentTab.value = tab
    }

    fun selectSport(sport: SportType) {
        _selectedSport.value = sport
    }

    fun selectStatus(status: MatchStatus) {
        _selectedStatus.value = status
    }

    fun openPlayer(fixture: MatchFixture, stream: StreamSource) {
        _activeFixture.value = fixture
        _activeStream.value = stream
    }

    fun closePlayer() {
        _activeFixture.value = null
        _activeStream.value = null
    }

    fun toggleFavorite(fixtureId: String) {
        viewModelScope.launch {
            repository.toggleFavorite(fixtureId)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            repository.refreshFixtures()
        }
    }

    suspend fun importM3uUrl(url: String): Result<Int> {
        return repository.importM3uPlaylist(url, isUrl = true)
    }

    suspend fun importM3uText(text: String): Result<Int> {
        return repository.importM3uPlaylist(text, isUrl = false)
    }

    suspend fun addCustomStream(title: String, url: String, format: StreamFormat): Result<Unit> {
        return repository.addCustomStream(title, url, format)
    }

    fun getApiDocs() = repository.getApiConfigDocumentation()
}
