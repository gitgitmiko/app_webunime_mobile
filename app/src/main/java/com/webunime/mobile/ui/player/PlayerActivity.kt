package com.webunime.mobile.ui.player

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.webunime.mobile.WebunimeApp
import com.webunime.mobile.data.AnimeDetail
import com.webunime.mobile.data.EmbedPlayback
import com.webunime.mobile.data.FilmEmbed
import com.webunime.mobile.data.EpisodeSummary
import com.webunime.mobile.data.PlayerRouter
import com.webunime.mobile.data.PlayerServer
import com.webunime.mobile.data.WebPlayerProxy
import com.webunime.mobile.data.party.RoomRole
import com.webunime.mobile.data.party.RoomSnapshot
import com.webunime.mobile.data.party.WatchPartyApi
import com.webunime.mobile.data.party.WatchPartySession
import com.webunime.mobile.data.party.WatchSyncAgent
import com.webunime.mobile.ui.account.AuthDialog
import com.webunime.mobile.ui.components.MetaChip
import com.webunime.mobile.ui.components.SectionTitle
import com.webunime.mobile.ui.components.SimpleDropdown
import com.webunime.mobile.ui.theme.Appear
import com.webunime.mobile.ui.theme.WebunimeTheme
import com.webunime.mobile.ui.theme.WuBg
import com.webunime.mobile.ui.theme.WuStroke
import com.webunime.mobile.ui.theme.WuSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class PlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        val startSlug = intent.getStringExtra(EXTRA_SLUG).orEmpty()
        val startEpisode = intent.getIntExtra(EXTRA_EPISODE, 1)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val startCollection = intent.getStringExtra(EXTRA_COLLECTION)?.takeIf { it.isNotBlank() } ?: "anime"
        val joinCode = intent.getStringExtra(EXTRA_ROOM_CODE)?.takeIf { it.isNotBlank() }
        val app = application as WebunimeApp

        setContent {
            WebunimeTheme {
                val configuration = LocalConfiguration.current
                val landscape =
                    configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

                DisposableEffect(landscape) {
                    applyImmersive(landscape)
                    onDispose { /* keep bars as-is until next apply */ }
                }

                // Penonton nonton bareng bisa dipindah host ke judul lain.
                var slug by remember { mutableStateOf(startSlug) }
                var collection by remember { mutableStateOf(startCollection) }
                var episodes by remember { mutableStateOf<List<EpisodeSummary>>(emptyList()) }
                var currentEpisode by remember { mutableIntStateOf(startEpisode) }
                var players by remember { mutableStateOf<List<PlayerServer>>(emptyList()) }
                var selectedUrl by remember { mutableStateOf<String?>(null) }
                var loading by remember { mutableStateOf(true) }
                var error by remember { mutableStateOf<String?>(null) }
                var epTitle by remember { mutableStateOf(title) }
                var animeTitle by remember { mutableStateOf(title) }
                var animeDetail by remember { mutableStateOf<AnimeDetail?>(null) }
                /** Slug episode & koleksi asli di server, untuk ruang nonton bareng. */
                var episodeSlug by remember { mutableStateOf<String?>(null) }
                var serverCollection by remember { mutableStateOf<String?>(null) }
                /** Server yang diminta ruang (penonton) — dipilih begitu daftar server ada. */
                var roomServerUrl by remember { mutableStateOf<String?>(null) }

                val sync = remember { PlayerSync() }
                var session by remember { mutableStateOf<WatchPartySession?>(null) }
                val roomState by remember(session) { session?.state ?: flowOf(null) }
                    .collectAsState(initial = session?.state?.value)
                val isViewer = roomState?.role == RoomRole.Viewer
                val isHost = roomState?.role == RoomRole.Host
                var askLogin by remember { mutableStateOf(false) }
                var afterLogin by remember { mutableStateOf<(() -> Unit)?>(null) }
                var confirmEnd by remember { mutableStateOf(false) }
                var partyBusy by remember { mutableStateOf(false) }
                var roomNotice by remember { mutableStateOf<String?>(null) }

                fun toast(text: String) {
                    Toast.makeText(this@PlayerActivity, text, Toast.LENGTH_LONG).show()
                }

                fun enterRoom(snapshot: RoomSnapshot) {
                    val me = app.webAuth.user.value ?: return
                    session?.leave()
                    val next = WatchPartySession(app.watchParty, snapshot, me.id, lifecycleScope)
                    sync.role = next.state.value.role
                    sync.onReport = { playing, time -> next.reportPlayback(playing, time) }
                    if (next.isHost) sync.hostResume(snapshot.playback.time)
                    session = next
                    next.start()
                }

                fun leaveRoom() {
                    session?.leave()
                    session = null
                    sync.leave()
                }

                fun requireLogin(action: () -> Unit) {
                    if (app.webAuth.user.value != null) return action()
                    afterLogin = action
                    askLogin = true
                }

                fun startRoom() {
                    if (partyBusy) return
                    val pick = selectedUrl?.takeIf { WatchPartyApi.isSyncableServer(it) }
                        ?: players.firstOrNull { WatchPartyApi.isSyncableServer(it.url) }?.url
                    if (pick == null) {
                        toast("Server judul ini tidak mendukung nonton bareng.")
                        return
                    }
                    partyBusy = true
                    lifecycleScope.launch {
                        runCatching {
                            app.watchParty.create(
                                collection = serverCollection ?: collection,
                                slug = slug,
                                episodeSlug = episodeSlug,
                                serverUrl = pick,
                            )
                        }
                            .onSuccess { snapshot ->
                                selectedUrl = pick
                                enterRoom(snapshot)
                                roomNotice = "Ruang dibuat. Bagikan kode ${snapshot.code} ke teman."
                            }
                            .onFailure { toast(it.message ?: "Gagal membuat ruang.") }
                        partyBusy = false
                    }
                }

                fun shareRoom() {
                    val code = session?.code ?: return
                    val link = WatchPartyApi.inviteLink(app.webAuth, code)
                    val text = "Nonton bareng \"$animeTitle\" di WEBUNIME.\nKode ruang: $code\n$link"
                    startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                            "Bagikan ruang",
                        ),
                    )
                }

                fun requestExit() {
                    if (isHost) confirmEnd = true else finish()
                }

                BackHandler(enabled = isHost) { confirmEnd = true }

                // Gabung lewat kode (dari daftar ruang).
                LaunchedEffect(joinCode) {
                    val code = joinCode ?: return@LaunchedEffect
                    if (app.webAuth.user.value == null) askLogin = true
                    app.webAuth.user.first { it != null }
                    runCatching { app.watchParty.room(code) }
                        .onSuccess { enterRoom(it) }
                        .onFailure {
                            loading = false
                            error = it.message ?: "Gagal bergabung."
                        }
                }

                // Penonton: ikuti judul/episode/server pilihan host.
                LaunchedEffect(roomState?.media, isViewer) {
                    val media = roomState?.media ?: return@LaunchedEffect
                    // Host yang membuka ruangnya dari daftar juga mulai dari media ruang.
                    if (!isViewer && slug.isNotBlank()) return@LaunchedEffect
                    sync.onNewMedia()
                    val sameEpisode = media.slug == slug && media.episodeSlug == episodeSlug && players.isNotEmpty()
                    if (sameEpisode) {
                        selectedUrl = media.serverUrl
                        return@LaunchedEffect
                    }
                    loading = true
                    error = null
                    runCatching { app.catalogApi.anime(media.slug, media.collection) }
                        .onSuccess { detail ->
                            val eps = detail.episodes.sortedBy { it.episode ?: 0 }
                            val n = eps.firstOrNull { it.slug == media.episodeSlug }?.episode
                                ?: eps.firstOrNull()?.episode
                                ?: 1
                            roomServerUrl = media.serverUrl
                            animeDetail = detail
                            animeTitle = detail.displayTitle()
                            episodes = eps
                            slug = media.slug
                            collection = if (media.collection == "anime-movies") "anime" else media.collection
                            currentEpisode = n
                        }
                        .onFailure {
                            loading = false
                            error = it.message
                        }
                }

                LaunchedEffect(slug, collection) {
                    if (slug.isBlank()) return@LaunchedEffect
                    if (isViewer && animeDetail != null && slug == roomState?.media?.slug) return@LaunchedEffect
                    runCatching { app.catalogApi.anime(slug, collection) }
                        .onSuccess { detail ->
                            animeDetail = detail
                            animeTitle = detail.displayTitle()
                            episodes = detail.episodes.sortedBy { it.episode ?: 0 }
                        }
                }

                LaunchedEffect(slug, collection, currentEpisode, joinCode == null || session != null) {
                    if (slug.isBlank()) return@LaunchedEffect
                    // Gabung ruang: tunggu media host dulu supaya tidak memutar episode lain.
                    if (joinCode != null && session == null) return@LaunchedEffect
                    loading = true
                    error = null
                    runCatching { app.catalogApi.episode(slug, currentEpisode, collection) }
                        .onSuccess { payload ->
                            epTitle = payload.episode?.title
                                ?: payload.judul
                                ?: animeTitle
                            episodeSlug = payload.episode?.slug
                            serverCollection = payload.collection
                            players = PlayerRouter.preferred(
                                payload.episode?.players.orEmpty(),
                                film = collection == "movies" ||
                                    collection == "horror" ||
                                    collection == "series",
                            )
                            val keep = selectedUrl?.takeIf { url -> players.any { it.url == url } }
                            selectedUrl = roomServerUrl?.takeIf { url -> players.any { it.url == url } }
                                ?: keep.takeIf { session != null }
                                ?: players.firstOrNull { session == null || WatchPartyApi.isSyncableServer(it.url) }?.url
                                ?: players.firstOrNull()?.url
                            roomServerUrl = null
                            if (players.isEmpty()) error = "Tidak ada server player"
                        }
                        .onFailure { error = it.message }
                    loading = false
                }

                // Host: ganti episode/server → kabari semua penonton.
                LaunchedEffect(session, slug, episodeSlug, selectedUrl, loading) {
                    val s = session ?: return@LaunchedEffect
                    val url = selectedUrl ?: return@LaunchedEffect
                    if (!s.isHost || loading) return@LaunchedEffect
                    s.pushMedia(serverCollection ?: collection, slug, episodeSlug, url)
                        .onSuccess { at ->
                            if (at != null) sync.hostResume(at)
                        }
                        .onFailure { toast(it.message ?: "Gagal mengganti media ruang.") }
                }

                // Tick sinkron seperti web (1,5 detik) + tiap status ruang berubah.
                LaunchedEffect(session) {
                    val s = session ?: return@LaunchedEffect
                    while (true) {
                        sync.sync(s.state.value)
                        delay(ROOM_TICK_MS)
                    }
                }
                LaunchedEffect(roomState?.playback, roomState?.playbackAt) {
                    roomState?.let(sync::sync)
                }

                LaunchedEffect(roomState?.closedReason) {
                    val reason = roomState?.closedReason ?: return@LaunchedEffect
                    val wasViewer = isViewer
                    leaveRoom()
                    when {
                        reason == "auth" -> toast("Sesi login berakhir. Masuk lagi untuk nonton bareng.")
                        reason == "replaced" -> toast("Ruang diganti dengan ruang baru.")
                        wasViewer || reason != "ended" -> toast("Nonton bareng sudah berakhir.")
                    }
                    if (wasViewer) finish()
                }

                DisposableEffect(Unit) {
                    onDispose { session?.leave() }
                }

                if (askLogin) {
                    AuthDialog(
                        onDismiss = {
                            askLogin = false
                            afterLogin = null
                            if (joinCode != null && app.webAuth.user.value == null) finish()
                        },
                        onSignedIn = {
                            val action = afterLogin
                            afterLogin = null
                            action?.invoke()
                        },
                        lead = "Nonton bareng butuh akun WEBUNIME.",
                    )
                }

                if (confirmEnd) {
                    AlertDialog(
                        onDismissRequest = { confirmEnd = false },
                        title = { Text("Akhiri nonton bareng?") },
                        text = { Text("Ruang akan ditutup untuk semua penonton.") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    confirmEnd = false
                                    val s = session
                                    leaveRoom()
                                    lifecycleScope.launch { s?.end() }
                                    finish()
                                },
                            ) { Text("Akhiri") }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmEnd = false }) { Text("Batal") }
                        },
                    )
                }

                roomNotice?.let { notice ->
                    AlertDialog(
                        onDismissRequest = { roomNotice = null },
                        title = { Text("Nonton bareng") },
                        text = { Text(notice) },
                        confirmButton = {
                            TextButton(onClick = { roomNotice = null; shareRoom() }) { Text("Bagikan") }
                        },
                        dismissButton = {
                            TextButton(onClick = { roomNotice = null }) { Text("Tutup") }
                        },
                    )
                }

                val isMovieCatalog =
                    collection == "movies" || collection == "horror"
                val selectedEp = episodes.firstOrNull { it.episode == currentEpisode }
                    ?: episodes.firstOrNull()
                val url = selectedUrl.orEmpty()
                // Di ruang hanya server yang bisa disinkronkan (sama dengan web).
                val shownPlayers = if (session != null) {
                    players.filter { WatchPartyApi.isSyncableServer(it.url) }
                } else {
                    players
                }

                // Satu pohon Compose: player TIDAK di-if/else portrait↔landscape
                // supaya ExoPlayer/WebView tidak di-dispose (video lanjut).
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .then(if (landscape) Modifier else Modifier.statusBarsPadding()),
                    ) {
                        if (!landscape) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(WuSurface.copy(alpha = 0.96f))
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(onClick = { requestExit() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        null,
                                        tint = MaterialTheme.colorScheme.onBackground,
                                    )
                                }
                                Text(
                                    epTitle,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                )
                                IconButton(
                                    onClick = {
                                        requestedOrientation =
                                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    },
                                ) {
                                    Icon(
                                        Icons.Default.Fullscreen,
                                        "Fullscreen",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }

                        Box(
                            Modifier
                                .fillMaxWidth()
                                .then(
                                    if (landscape) {
                                        Modifier.weight(1f)
                                    } else {
                                        Modifier.aspectRatio(16f / 9f)
                                    },
                                )
                                .background(Color.Black),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                loading && players.isEmpty() ->
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                error != null && players.isEmpty() ->
                                    Text(error ?: "", color = Color.White)
                                url.isNotBlank() -> PlaybackSurface(url = url, sync = sync)
                            }
                            roomState?.takeIf { isViewer && url.isNotBlank() }?.let { state ->
                                ViewerLock(
                                    state = state,
                                    frameReady = sync.frameReady,
                                    needsTap = sync.needsTap,
                                    onTap = { if (sync.needsTap) sync.toggleMute() },
                                )
                            }
                        }

                        roomState?.let { state ->
                            RoomBar(
                                state = state,
                                muted = sync.muted,
                                compact = landscape,
                                hostHint = isHost && !sync.frameReady && url.isNotBlank(),
                                onShare = ::shareRoom,
                                onMute = sync::toggleMute,
                                onLeave = { requestExit() },
                            )
                        }

                        if (!landscape) {
                            PlayerInfoPanel(
                                animeTitle = animeTitle,
                                animeDetail = animeDetail,
                                hideEpisodes = isMovieCatalog,
                                locked = isViewer,
                                inRoom = session != null,
                                partyBusy = partyBusy,
                                episodes = episodes,
                                selectedEp = selectedEp,
                                players = shownPlayers,
                                selectedServer = shownPlayers.indexOfFirst { it.url == url },
                                onSelectEpisode = { n -> currentEpisode = n },
                                onSelectServer = { idx -> selectedUrl = shownPlayers.getOrNull(idx)?.url },
                                onStartParty = { requireLogin(::startRoom) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            )
                        }
                    }

                    if (landscape) {
                        IconButton(
                            onClick = {
                                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .statusBarsPadding()
                                .padding(4.dp),
                        ) {
                            Icon(Icons.Default.FullscreenExit, "Keluar fullscreen", tint = Color.White)
                        }
                        IconButton(
                            onClick = { requestExit() },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(4.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = Color.White)
                        }
                    }
                }
            }
        }
    }

    private fun applyImmersive(enabled: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(window, !enabled)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller.show(WindowInsetsCompat.Type.systemBars())
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    companion object {
        const val EXTRA_SLUG = "slug"
        const val EXTRA_EPISODE = "episode"
        const val EXTRA_TITLE = "title"
        const val EXTRA_COLLECTION = "collection"
        /** Gabung ruang nonton bareng; judul & episode mengikuti host. */
        const val EXTRA_ROOM_CODE = "room_code"
        private const val ROOM_TICK_MS = 1_500L
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerInfoPanel(
    animeTitle: String,
    animeDetail: AnimeDetail?,
    hideEpisodes: Boolean,
    episodes: List<EpisodeSummary>,
    selectedEp: EpisodeSummary?,
    players: List<PlayerServer>,
    selectedServer: Int,
    onSelectEpisode: (Int) -> Unit,
    onSelectServer: (Int) -> Unit,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    inRoom: Boolean = false,
    partyBusy: Boolean = false,
    onStartParty: () -> Unit = {},
) {
    Column(
        modifier
            .background(
                Brush.verticalGradient(listOf(WuSurface.copy(alpha = 0.7f), WuBg, WuBg)),
            )
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Appear {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    animeTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    animeDetail?.rating?.takeIf { it.isNotBlank() }?.let {
                        MetaChip("\u2605 $it", accent = true)
                    }
                    animeDetail?.type?.takeIf { it.isNotBlank() }?.let {
                        MetaChip(it)
                    }
                    if (!hideEpisodes) {
                        animeDetail?.episodes_count?.let {
                            MetaChip("$it episode")
                        }
                    }
                }
                animeDetail?.genre?.takeIf { it.isNotEmpty() }?.let { genres ->
                    Text(
                        genres.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Appear(delayMs = 80) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider(color = WuStroke.copy(alpha = 0.6f))
                SectionTitle(
                    "Putar",
                    modifier = Modifier.padding(horizontal = 0.dp),
                )
                Text(
                    when {
                        locked -> "Episode dan server diatur oleh host"
                        hideEpisodes -> "Pilih server pemutar"
                        else -> "Pilih episode dan server"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!inRoom) {
                    OutlinedButton(
                        onClick = onStartParty,
                        enabled = !partyBusy && players.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(Icons.Default.Groups, null, modifier = Modifier.padding(end = 8.dp))
                        Text(if (partyBusy) "Membuat ruang…" else "Nonton bareng")
                    }
                }
                if (!hideEpisodes && episodes.isNotEmpty()) {
                    SimpleDropdown(
                        label = "Episode",
                        options = episodes,
                        selected = selectedEp,
                        optionLabel = { it.displayLabel() },
                        onSelect = { ep ->
                            val n = ep.episode ?: return@SimpleDropdown
                            onSelectEpisode(n)
                        },
                        enabled = !locked,
                    )
                }
                if (players.isNotEmpty()) {
                    SimpleDropdown(
                        label = "Server",
                        options = players,
                        selected = players.getOrNull(selectedServer),
                        optionLabel = { it.displayLabel() },
                        onSelect = { server ->
                            val idx = players.indexOf(server)
                            if (idx >= 0) onSelectServer(idx)
                        },
                        enabled = !locked,
                    )
                }
            }
        }

        animeDetail?.sinopsis?.takeIf { it.isNotBlank() }?.let { syn ->
            Appear(delayMs = 140) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = WuStroke.copy(alpha = 0.6f))
                    SectionTitle(
                        "Sinopsis",
                        modifier = Modifier.padding(horizontal = 0.dp),
                    )
                    Text(
                        syn,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PlaybackSurface(url: String, sync: PlayerSync) {
    val context = LocalContext.current
    var playUrl by remember { mutableStateOf(url) }
    var resolving by remember { mutableStateOf(EmbedPlayback.isWibufileEmbed(url)) }
    val viewer = sync.role == RoomRole.Viewer

    LaunchedEffect(url) {
        sync.onNewMedia()
        resolving = EmbedPlayback.isWibufileEmbed(url)
        val mapped = FilmEmbed.mapLegacyIframe(url)
        playUrl = mapped ?: url
        if (EmbedPlayback.isWibufileEmbed(url)) {
            val mp4 = EmbedPlayback.resolveDirectMedia(url)
            if (!mp4.isNullOrBlank()) playUrl = mp4
            resolving = false
        } else {
            resolving = false
        }
    }

    if (resolving) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val direct = remember(playUrl) { PlayerRouter.isDirectMedia(playUrl) }
    val useIframe = remember(playUrl) {
        !direct && EmbedPlayback.needsIframeWrapper(playUrl)
    }

    if (direct) {
        val player = remember {
            ExoPlayer.Builder(context).build().also {
                it.repeatMode = Player.REPEAT_MODE_OFF
            }
        }
        LaunchedEffect(playUrl) {
            val current = player.currentMediaItem?.localConfiguration?.uri?.toString()
            if (current == playUrl) return@LaunchedEffect
            player.setMediaItem(MediaItem.fromUri(playUrl))
            player.prepare()
            player.playWhenReady = true
        }
        DisposableEffect(player) {
            sync.attach(player)
            onDispose {
                sync.detach(player)
                player.release()
            }
        }
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            update = { view ->
                if (view.player !== player) view.player = player
                // Penonton nonton bareng: kontrol dikunci, host yang mengendalikan.
                view.useController = !viewer
            },
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        val activity = context as? android.app.Activity
        var webViewRef by remember { mutableStateOf<WebView?>(null) }
        var playing by remember { mutableStateOf(true) }
        var muted by remember { mutableStateOf(false) }
        val filmWeb = remember(playUrl) {
            WebPlayerProxy.isAbyss(playUrl) ||
                WebPlayerProxy.isTurbo(playUrl) ||
                WebPlayerProxy.isP2p(playUrl) ||
                FilmEmbed.isIframe3(playUrl)
        }
        val playUrlState = rememberUpdatedState(playUrl)
        fun runPlayerJs(code: String) {
            webViewRef?.evaluateJavascript(code, null)
        }

        Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    @SuppressLint("SetJavaScriptEnabled")
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webChromeClient = object : WebChromeClient() {
                        private var customView: View? = null
                        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                            val parent = this@apply.parent as? ViewGroup ?: return
                            customView?.let { old -> (old.parent as? ViewGroup)?.removeView(old) }
                            customView = view
                            parent.addView(
                                view,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                ),
                            )
                        }

                        override fun onHideCustomView() {
                            customView?.let { view ->
                                (view.parent as? ViewGroup)?.removeView(view)
                            }
                            customView = null
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse? = WebPlayerProxy.intercept(request)
                    }
                    addJavascriptInterface(
                        FilmPlaybackBridge(
                            onEmbed = { next ->
                                activity?.runOnUiThread {
                                    if (next.isBlank()) return@runOnUiThread
                                    playUrl = next
                                }
                            },
                            notifyPlay = {
                                activity?.runOnUiThread { playing = true }
                            },
                            notifyPause = {
                                activity?.runOnUiThread { playing = false }
                            },
                        ),
                        "WebunimePlayback",
                    )
                    addJavascriptInterface(sync.bridge, WatchSyncAgent.BRIDGE)
                    // Agen nonton bareng di semua frame (embed bersarang); diam di luar ruang.
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                        WebViewCompat.addDocumentStartJavaScript(this, WatchSyncAgent.SOURCE, setOf("*"))
                    }
                    sync.attach(this)
                    tag = ""
                    webViewRef = this
                }
            },
            update = { webView ->
                webViewRef = webView
                val loaded = webView.tag as? String
                if (loaded == playUrlState.value) return@AndroidView
                webView.tag = playUrlState.value
                val current = playUrlState.value
                when {
                    FilmEmbed.isIframe3(current) -> {
                        val parsed = FilmEmbed.parseIframe3(current)
                        if (parsed == null) {
                            webView.loadUrl(current)
                        } else {
                            val (host, id) = parsed
                            webView.loadDataWithBaseURL(
                                FilmEmbed.wrapperOrigin(current),
                                FilmEmbed.iframe3BootstrapHtml(host, id),
                                "text/html",
                                "utf-8",
                                null,
                            )
                        }
                    }
                    WebPlayerProxy.isAbyss(current) -> {
                        webView.loadDataWithBaseURL(
                            WebPlayerProxy.ABYSS_WRAPPER_BASE,
                            WebPlayerProxy.abyssWrapperHtml(current),
                            "text/html",
                            "utf-8",
                            null,
                        )
                    }
                    WebPlayerProxy.isTurbo(current) -> {
                        webView.loadDataWithBaseURL(
                            WebPlayerProxy.ABYSS_WRAPPER_BASE,
                            WebPlayerProxy.turboWrapperHtml(current),
                            "text/html",
                            "utf-8",
                            null,
                        )
                    }
                    WebPlayerProxy.isP2p(current) -> {
                        webView.loadDataWithBaseURL(
                            WebPlayerProxy.VIDEONODE_WRAPPER_BASE,
                            WebPlayerProxy.turboWrapperHtml(current),
                            "text/html",
                            "utf-8",
                            null,
                        )
                    }
                    useIframe -> {
                        webView.loadDataWithBaseURL(
                            EmbedPlayback.wrapperBaseUrl(current),
                            EmbedPlayback.iframeWrapperHtml(current),
                            "text/html",
                            "utf-8",
                            null,
                        )
                    }
                    else -> {
                        val headers = mutableMapOf<String, String>()
                        if (current.contains("wibufile", ignoreCase = true)) {
                            headers["Referer"] = "https://api.wibufile.com/"
                        }
                        if (headers.isEmpty()) webView.loadUrl(current)
                        else webView.loadUrl(current, headers)
                    }
                }
            },
            onRelease = { webView ->
                sync.detach(webView)
                webViewRef = null
                webView.stopLoading()
                webView.destroy()
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (filmWeb && !viewer) {
            var controlsVisible by remember(playUrl) { mutableStateOf(true) }
            var controlsEpoch by remember(playUrl) { mutableIntStateOf(0) }
            fun revealControls() {
                controlsVisible = true
                controlsEpoch++
            }

            LaunchedEffect(playing) {
                if (!playing) revealControls()
            }
            LaunchedEffect(playing, controlsVisible, controlsEpoch) {
                if (!controlsVisible || !playing) return@LaunchedEffect
                delay(3_000)
                controlsVisible = false
            }

            // Area sentuh: tampilkan / sembunyikan kontrol (WebView tidak menerima tap ini).
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        if (controlsVisible) controlsVisible = false
                        else revealControls()
                    },
            )
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            ) {
                FilmNativeControls(
                    playing = playing,
                    muted = muted,
                    onPlayPause = {
                        revealControls()
                        if (playing) runPlayerJs("try{window.__wuPause&&window.__wuPause()}catch(e){}")
                        else runPlayerJs("try{window.__wuPlay&&window.__wuPlay()}catch(e){}")
                        playing = !playing
                    },
                    onSeekBack = {
                        revealControls()
                        runPlayerJs("try{window.__wuSeekBy&&window.__wuSeekBy(-10)}catch(e){}")
                    },
                    onSeekForward = {
                        revealControls()
                        runPlayerJs("try{window.__wuSeekBy&&window.__wuSeekBy(10)}catch(e){}")
                    },
                    onMute = {
                        revealControls()
                        muted = !muted
                        runPlayerJs("try{window.__wuMuteToggle&&window.__wuMuteToggle()}catch(e){}")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        }
    }
}

@Composable
private fun FilmNativeControls(
    playing: Boolean,
    muted: Boolean,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { /* tahan tap agar tidak menembus overlay hide */ },
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onSeekBack) {
            Icon(Icons.Default.Replay10, "Mundur 10 detik", tint = Color.White)
        }
        IconButton(onClick = onPlayPause) {
            Icon(
                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                if (playing) "Jeda" else "Putar",
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }
        IconButton(onClick = onSeekForward) {
            Icon(Icons.Default.Forward10, "Maju 10 detik", tint = Color.White)
        }
        IconButton(onClick = onMute) {
            Icon(
                if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                if (muted) "Unmute" else "Mute",
                tint = Color.White,
            )
        }
    }
}

private class FilmPlaybackBridge(
    private val onEmbed: (String) -> Unit,
    private val notifyPlay: () -> Unit = {},
    private val notifyPause: () -> Unit = {},
) {
    @JavascriptInterface
    fun onResolvedEmbed(embedUrl: String) {
        onEmbed(embedUrl.trim())
    }

    @JavascriptInterface
    fun onQualities(json: String) { }

    @JavascriptInterface
    fun onProgress(position: Double, duration: Double) { }

    @JavascriptInterface
    fun onPlay() { notifyPlay() }

    @JavascriptInterface
    fun onPause() { notifyPause() }

    @JavascriptInterface
    fun onEnded() { }
}
