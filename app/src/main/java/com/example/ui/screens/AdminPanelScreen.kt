package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MatchFixture
import com.example.model.MatchStatus
import com.example.model.M3uChannel
import com.example.model.SportType
import com.example.repository.LocalSportsDataStore
import com.example.repository.checkStreamAvailability
import com.example.repository.formatAdminDateTime
import com.example.repository.parseAdminDateTime
import com.example.repository.parseAdminStreamSources
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.NavyCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun AdminPanelScreen(
    fixtures: List<MatchFixture>,
    onBack: () -> Unit,
    onDataChanged: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { LocalSportsDataStore(context) }
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var isAdmin by remember { mutableStateOf(false) }
    var hasPassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var channels by remember { mutableStateOf<List<M3uChannel>>(emptyList()) }
    var activeSection by remember { mutableStateOf(0) }
    var showChannelEditor by remember { mutableStateOf(false) }
    var showM3uImportDialog by remember { mutableStateOf(false) }
    var editingChannel by remember { mutableStateOf<M3uChannel?>(null) }
    var showMatchEditor by remember { mutableStateOf(false) }
    var editingMatch by remember { mutableStateOf<MatchFixture?>(null) }

    LaunchedEffect(store) {
        hasPassword = store.hasLocalPassword()
        channels = store.loadAdminChannels()
        loading = false
    }

    LaunchedEffect(isAdmin) {
        if (isAdmin) channels = store.loadAdminChannels()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NavyBackground)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) { Text("‹  Back") }
            Text(
                text = "ADMIN PANEL",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
            if (isAdmin) {
                TextButton(
                    onClick = {
                        isAdmin = false
                        password = ""
                        confirmation = ""
                        message = "Admin panel locked."
                    }
                ) { Text("Lock") }
            } else {
                Spacer(Modifier.width(64.dp))
            }
        }

        when {
            loading -> CircularProgressIndicator(color = CyanAccent)
            !isAdmin -> {
                LocalAdminAccessForm(
                    hasPassword = hasPassword,
                    password = password,
                    onPasswordChange = { password = it },
                    confirmation = confirmation,
                    onConfirmationChange = { confirmation = it },
                    message = message,
                    busy = busy,
                    onSubmit = {
                        message = null
                        busy = true
                        scope.launch {
                            runCatching {
                                if (hasPassword) {
                                    check(store.verifyLocalPassword(password)) { "Incorrect local password." }
                                } else {
                                    require(password.length >= 8) { "Choose a password with at least 8 characters." }
                                    require(password == confirmation) { "Passwords do not match." }
                                    store.setLocalPassword(password)
                                    hasPassword = true
                                }
                            }.onSuccess {
                                isAdmin = true
                                password = ""
                                confirmation = ""
                                channels = store.loadAdminChannels()
                                message = "Unlocked. Edits are stored on this device only."
                            }.onFailure {
                                message = it.message ?: "Could not unlock the admin panel."
                            }
                            busy = false
                        }
                    }
                )
            }
            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = { activeSection = 0 }) {
                        Text(
                            "Channels & streams",
                            color = if (activeSection == 0) CyanAccent else TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    TextButton(onClick = { activeSection = 1 }) {
                        Text(
                            "Matches",
                            color = if (activeSection == 1) CyanAccent else TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                message?.let {
                    Text(
                        text = it,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 6.dp),
                        fontSize = 13.sp
                    )
                }

                if (busy) {
                    CircularProgressIndicator(color = CyanAccent)
                } else if (activeSection == 0) {
                    ChannelAdminList(
                        channels = channels,
                        onImportM3u = { showM3uImportDialog = true },
                        onAdd = {
                            editingChannel = null
                            showChannelEditor = true
                        },
                        onEdit = {
                            editingChannel = it
                            showChannelEditor = true
                        },
                        onDelete = { channel ->
                            busy = true
                            scope.launch {
                                runCatching {
                                    store.deleteChannel(channel.id)
                                    store.loadAdminChannels()
                                }.onSuccess {
                                    channels = it
                                    message = "Channel removed from this device."
                                    onDataChanged()
                                }.onFailure {
                                    message = it.message ?: "Could not remove channel."
                                }
                                busy = false
                            }
                        },
                        onMove = { index, offset ->
                            val reordered = channels.toMutableList()
                            val destination = index + offset
                            if (destination in reordered.indices) {
                                val item = reordered.removeAt(index)
                                reordered.add(destination, item)
                                channels = reordered.mapIndexed { order, channel ->
                                    channel.copy(displayOrder = order)
                                }
                                busy = true
                                scope.launch {
                                    val saveResult = runCatching { store.saveChannelOrder(channels) }
                                    if (saveResult.isFailure) {
                                        message = saveResult.exceptionOrNull()?.message
                                            ?: "Could not reorder channels."
                                        channels = runCatching { store.loadAdminChannels() }
                                            .getOrDefault(channels)
                                    } else {
                                        onDataChanged()
                                    }
                                    busy = false
                                }
                            }
                        }
                    )
                } else {
                    MatchAdminList(
                        fixtures = fixtures,
                        onAdd = {
                            editingMatch = null
                            showMatchEditor = true
                        },
                        onEdit = {
                            editingMatch = it
                            showMatchEditor = true
                        },
                        onDelete = { match ->
                            busy = true
                            scope.launch {
                                runCatching { store.deleteMatchOverride(match.id) }
                                    .onSuccess { message = "Match override removed from this device."; onDataChanged() }
                                    .onFailure { message = it.message ?: "Could not remove match override." }
                                busy = false
                            }
                        }
                    )
                }
            }
        }
    }

    if (showChannelEditor) {
        ChannelEditorDialog(
            channel = editingChannel,
            onDismiss = { showChannelEditor = false },
            onSave = { channel ->
                showChannelEditor = false
                busy = true
                message = null
                scope.launch {
                    runCatching {
                        store.saveChannel(channel)
                        store.loadAdminChannels()
                    }.onSuccess {
                        channels = it
                        message = "Channel saved on this device."
                        onDataChanged()
                    }.onFailure {
                        message = it.message ?: "Could not save channel."
                    }
                    busy = false
                }
            }
        )
    }

    if (showMatchEditor) {
        MatchEditorDialog(
            match = editingMatch,
            onDismiss = { showMatchEditor = false },
            onSave = { fixture, isManual ->
                showMatchEditor = false
                busy = true
                message = null
                scope.launch {
                    runCatching { store.saveMatchOverride(fixture, isManual) }
                        .onSuccess {
                            message = "Match saved on this device."
                            onDataChanged()
                        }
                        .onFailure {
                            message = it.message ?: "Could not save match."
                        }
                    busy = false
                }
            }
        )
    if (showM3uImportDialog) {
        M3uImportDialog(
            busy = busy,
            message = message,
            onDismiss = { if (!busy) showM3uImportDialog = false },
            onImport = { source, isUrl ->
                message = null
                busy = true
                scope.launch {
                    val result = store.importM3uPlaylist(source, isUrl)
                    result.onSuccess { count ->
                        channels = store.loadAdminChannels()
                        message = "Imported " + count + " channels on this device."
                        onDataChanged()
                        showM3uImportDialog = false
                    }.onFailure {
                        message = it.message ?: "Could not import the playlist."
                    }
                    busy = false
                }
            }
        )
    }
    }
}

@Composable
private fun LocalAdminAccessForm(
    hasPassword: Boolean,
    password: String,
    onPasswordChange: (String) -> Unit,
    confirmation: String,
    onConfirmationChange: (String) -> Unit,
    message: String?,
    busy: Boolean,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            if (hasPassword) "Unlock local admin panel" else "Create a local admin password",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "This password is stored on this device only. Each app installation has separate channels and match edits.",
            color = TextSecondary,
            fontSize = 13.sp
        )
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text(if (hasPassword) "Admin password" else "Create password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        if (!hasPassword) {
            OutlinedTextField(
                value = confirmation,
                onValueChange = onConfirmationChange,
                label = { Text("Confirm password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Text("Use at least 8 characters. No shared/default password is included in the APK.", color = TextMuted, fontSize = 12.sp)
        }
        message?.let { Text(it, color = TextSecondary, fontSize = 13.sp) }
        Button(
            onClick = onSubmit,
            enabled = !busy && password.isNotBlank() && (hasPassword || confirmation.isNotBlank()),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (busy) "Please wait…" else if (hasPassword) "Unlock" else "Set password")
        }
    }
}

@Composable
private fun ChannelAdminList(
    channels: List<M3uChannel>,
    onImportM3u: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (M3uChannel) -> Unit,
    onDelete: (M3uChannel) -> Unit,
    onMove: (Int, Int) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(channels.size.toString() + " channels", color = TextSecondary, modifier = Modifier.padding(top = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onImportM3u) { Text("Import M3U") }
                Button(onClick = onAdd) { Text("Add channel") }
            }
        }
        if (channels.isEmpty()) {
            Text("No channels yet. Add a channel and one or more stream URLs.", color = TextSecondary)
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
                    ChannelAdminCard(
                        channel = channel,
                        index = index,
                        onEdit = { onEdit(channel) },
                        onDelete = { onDelete(channel) },
                        onMoveUp = { onMove(index, -1) },
                        onMoveDown = { onMove(index, 1) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelAdminCard(
    channel: M3uChannel,
    index: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    var showDeleteConfirmation by remember(channel.id) { mutableStateOf(false) }
    val sourceCount = channel.streams.size.takeIf { it > 0 } ?: 1
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(channel.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                "${channel.group} • $sourceCount stream${if (sourceCount == 1) "" else "s"} • ${if (channel.enabled) "Active" else "Hidden"}",
                color = TextSecondary,
                fontSize = 12.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = onMoveUp, enabled = index > 0) { Text("↑") }
                TextButton(onClick = onMoveDown) { Text("↓") }
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = { showDeleteConfirmation = true }) { Text("Delete") }
            }
        }
    }
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete channel?") },
            text = { Text("This removes ${channel.name} from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MatchAdminList(
    fixtures: List<MatchFixture>,
    onAdd: () -> Unit,
    onEdit: (MatchFixture) -> Unit,
    onDelete: (MatchFixture) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("${fixtures.size} matches", color = TextSecondary, modifier = Modifier.padding(top = 10.dp))
            Button(onClick = onAdd) { Text("Add match") }
        }
        if (fixtures.isEmpty()) {
            Text("No feed matches are loaded. You can still add a manual match.", color = TextSecondary)
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                itemsIndexed(fixtures.sortedBy { it.startTimeEpochMs }, key = { _, match -> match.id }) { _, match ->
                    var showDeleteConfirmation by remember(match.id) { mutableStateOf(false) }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = NavyCard),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "${match.team1Name} vs ${match.team2Name}",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            val score = listOfNotNull(match.team1Score, match.team2Score)
                                .takeIf { it.isNotEmpty() }
                                ?.joinToString(" - ")
                                ?: "No score yet"
                            Text(
                                "${match.sport.displayName} • ${match.status.label} • $score • ${match.streams.size} streams",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { onEdit(match) }) { Text("Edit match") }
                                TextButton(onClick = { showDeleteConfirmation = true }) { Text("Remove override") }
                            }
                        }
                    }
                    if (showDeleteConfirmation) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirmation = false },
                            title = { Text("Remove match override?") },
                            text = {
                                Text(
                                    if (match.id.startsWith("admin_")) {
                                        "This removes the manual match from this device."
                                    } else {
                                        "This discards your saved edits and returns the match to its sports feed data."
                                    }
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    showDeleteConfirmation = false
                                    onDelete(match)
                                }) { Text("Remove") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelEditorDialog(
    channel: M3uChannel?,
    onDismiss: () -> Unit,
    onSave: (M3uChannel) -> Unit
) {
    var name by remember(channel?.id) { mutableStateOf(channel?.name.orEmpty()) }
    var group by remember(channel?.id) { mutableStateOf(channel?.group ?: "General") }
    var logoUrl by remember(channel?.id) { mutableStateOf(channel?.logoUrl.orEmpty()) }
    var enabled by remember(channel?.id) { mutableStateOf(channel?.enabled ?: true) }
    var order by remember(channel?.id) { mutableStateOf(channel?.displayOrder?.toString() ?: "0") }
    var streamText by remember(channel?.id) {
        mutableStateOf(
            channel?.streams?.takeIf { it.isNotEmpty() }
                ?.joinToString("\n") { "${it.name} | ${it.qualityLabel} | ${it.streamUrl}" }
                ?: channel?.streamUrl.orEmpty()
        )
    }
    var error by remember(channel?.id) { mutableStateOf<String?>(null) }
    var checkResult by remember(channel?.id) { mutableStateOf<String?>(null) }
    var checking by remember(channel?.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (channel == null) "Add channel" else "Edit channel") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Channel name") }, singleLine = true)
                OutlinedTextField(group, { group = it }, label = { Text("Category") }, singleLine = true)
                OutlinedTextField(logoUrl, { logoUrl = it }, label = { Text("Channel image URL (optional)") }, singleLine = true)
                OutlinedTextField(
                    value = streamText,
                    onValueChange = { streamText = it; checkResult = null },
                    label = { Text("Stream sources") },
                    placeholder = { Text("HD | 720p | https://...m3u8") },
                    minLines = 4,
                    maxLines = 7,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "One source per line: Name | Quality | URL. HLS (.m3u8), DASH (.mpd), and direct HTTP(S) links are supported.",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        enabled = !checking,
                        onClick = {
                            val sources = runCatching { parseAdminStreamSources(streamText) }
                            if (sources.isFailure || sources.getOrNull().isNullOrEmpty()) {
                                checkResult = sources.exceptionOrNull()?.message ?: "Add at least one stream first."
                            } else {
                                checking = true
                                scope.launch {
                                    val checks = sources.getOrThrow().map { source ->
                                        val result = checkStreamAvailability(source.streamUrl)
                                        "${source.name}: ${result.getOrElse { it.message ?: "Not reachable" }}"
                                    }
                                    checkResult = checks.joinToString("\n")
                                    checking = false
                                }
                            }
                        }
                    ) { Text(if (checking) "Checking…" else "Check links") }
                    TextButton(onClick = { enabled = !enabled }) {
                        Text(if (enabled) "Active" else "Hidden")
                    }
                }
                checkResult?.let { Text(it, color = TextSecondary, fontSize = 11.sp) }
                OutlinedTextField(
                    value = order,
                    onValueChange = { order = it.filter(Char::isDigit) },
                    label = { Text("Display order") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                error?.let { Text(it, color = TextSecondary, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = null
                val sources = runCatching { parseAdminStreamSources(streamText) }
                    .getOrElse {
                        error = it.message ?: "Check the stream source lines."
                        return@TextButton
                    }
                if (name.isBlank()) {
                    error = "Enter a channel name."
                    return@TextButton
                }
                if (sources.isEmpty()) {
                    error = "Add at least one valid stream source."
                    return@TextButton
                }
                val primary = sources.first()
                onSave(
                    M3uChannel(
                        id = channel?.id ?: UUID.randomUUID().toString(),
                        name = name.trim(),
                        group = group.trim().ifBlank { "General" },
                        logoUrl = logoUrl.trim().ifBlank { null },
                        streamUrl = primary.streamUrl,
                        format = primary.format,
                        streams = sources,
                        enabled = enabled,
                        displayOrder = order.toIntOrNull() ?: channel?.displayOrder ?: 0
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun MatchEditorDialog(
    match: MatchFixture?,
    onDismiss: () -> Unit,
    onSave: (MatchFixture, Boolean) -> Unit
) {
    var team1 by remember(match?.id) { mutableStateOf(match?.team1Name.orEmpty()) }
    var team2 by remember(match?.id) { mutableStateOf(match?.team2Name.orEmpty()) }
    var league by remember(match?.id) { mutableStateOf(match?.league ?: "Football") }
    var sport by remember(match?.id) {
        mutableStateOf(match?.sport?.takeIf { it != SportType.ALL } ?: SportType.FOOTBALL)
    }
    var status by remember(match?.id) {
        mutableStateOf(match?.status?.takeIf { it != MatchStatus.ALL } ?: MatchStatus.UPCOMING)
    }
    var dateTime by remember(match?.id) {
        mutableStateOf(formatAdminDateTime(match?.startTimeEpochMs ?: System.currentTimeMillis() + 3_600_000L))
    }
    var score1 by remember(match?.id) { mutableStateOf(match?.team1Score.orEmpty()) }
    var score2 by remember(match?.id) { mutableStateOf(match?.team2Score.orEmpty()) }
    var liveCounter by remember(match?.id) { mutableStateOf(match?.liveCounter.orEmpty()) }
    var streamText by remember(match?.id) {
        mutableStateOf(match?.streams.orEmpty().joinToString("\n") {
            "${it.name} | ${it.qualityLabel} | ${it.streamUrl}"
        })
    }
    var error by remember(match?.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (match == null) "Add match" else "Edit match") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(team1, { team1 = it }, label = { Text("First team") }, singleLine = true)
                OutlinedTextField(team2, { team2 = it }, label = { Text("Second team") }, singleLine = true)
                OutlinedTextField(league, { league = it }, label = { Text("League / category") }, singleLine = true)
                Text("Sport", color = TextSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(SportType.FOOTBALL, SportType.CRICKET).forEach { option ->
                        TextButton(onClick = { sport = option }) {
                            Text(option.displayName, color = if (sport == option) CyanAccent else TextSecondary)
                        }
                    }
                }
                OutlinedTextField(
                    value = dateTime,
                    onValueChange = { dateTime = it },
                    label = { Text("Start time (Asia/Dhaka)") },
                    placeholder = { Text("2026-10-20 20:30") },
                    singleLine = true
                )
                Text("Status", color = TextSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    listOf(MatchStatus.UPCOMING, MatchStatus.LIVE, MatchStatus.FINISHED).forEach { option ->
                        TextButton(onClick = { status = option }) {
                            Text(option.label, color = if (status == option) CyanAccent else TextSecondary)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        score1,
                        { score1 = it },
                        label = { Text("Score 1") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        score2,
                        { score2 = it },
                        label = { Text("Score 2") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    liveCounter,
                    { liveCounter = it },
                    label = { Text("Clock / live counter (optional)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = streamText,
                    onValueChange = { streamText = it },
                    label = { Text("Match stream sources") },
                    placeholder = { Text("HD | 720p | https://...m3u8") },
                    minLines = 3,
                    maxLines = 6
                )
                Text(
                    "One source per line: Name | Quality | URL. Leave empty if no stream is attached.",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                error?.let { Text(it, color = TextSecondary, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = null
                if (team1.isBlank() || team2.isBlank()) {
                    error = "Enter both team names."
                    return@TextButton
                }
                val startTime = parseAdminDateTime(dateTime)
                if (startTime == null) {
                    error = "Enter a valid date and time as yyyy-MM-dd HH:mm."
                    return@TextButton
                }
                val sources = runCatching { parseAdminStreamSources(streamText) }
                    .getOrElse {
                        error = it.message ?: "Check the stream source lines."
                        return@TextButton
                    }
                val first = team1.trim()
                val second = team2.trim()
                val updated = MatchFixture(
                    id = match?.id ?: "admin_${UUID.randomUUID()}",
                    title = "$first vs $second",
                    sport = sport,
                    league = league.trim().ifBlank { sport.displayName },
                    team1Name = first,
                    team1LogoUrl = match?.team1LogoUrl,
                    team1Abbr = match?.team1Abbr?.takeIf { it.isNotBlank() } ?: first.take(3).uppercase(),
                    team2Name = second,
                    team2LogoUrl = match?.team2LogoUrl,
                    team2Abbr = match?.team2Abbr?.takeIf { it.isNotBlank() } ?: second.take(3).uppercase(),
                    startTimeEpochMs = startTime,
                    status = status,
                    liveCounter = liveCounter.trim().ifBlank { null },
                    team1Score = score1.trim().ifBlank { null },
                    team2Score = score2.trim().ifBlank { null },
                    streams = sources,
                    isDemo = false
                )
                onSave(updated, match == null || match.id.startsWith("admin_"))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun M3uImportDialog(
    busy: Boolean,
    message: String?,
    onDismiss: () -> Unit,
    onImport: (String, Boolean) -> Unit
) {
    var source by remember { mutableStateOf("") }
    var isUrl by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import M3U playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { isUrl = true }) { Text("Playlist URL") }
                    TextButton(onClick = { isUrl = false }) { Text("Paste M3U text") }
                }
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    label = { Text(if (isUrl) "M3U URL" else "Playlist content") },
                    placeholder = { Text(if (isUrl) "https://example.com/channels.m3u" else "#EXTM3U …") },
                    singleLine = isUrl,
                    minLines = if (isUrl) 1 else 6,
                    maxLines = if (isUrl) 1 else 10,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Imported channels are saved only on this device.", color = TextMuted, fontSize = 12.sp)
                message?.let { Text(it, color = TextSecondary, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onImport(source.trim(), isUrl) },
                enabled = !busy && source.isNotBlank()
            ) { Text(if (busy) "Importing…" else "Import") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } }
    )
}
