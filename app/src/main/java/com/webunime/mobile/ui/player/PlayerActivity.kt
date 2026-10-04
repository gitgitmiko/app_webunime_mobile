package com.webunime.mobile.ui.player

import android.annotation.SuppressLint
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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.webunime.mobile.WebunimeApp
import com.webunime.mobile.data.AnimeDetail
import com.webunime.mobile.data.EmbedPlayback
import com.webunime.mobile.data.FilmEmbed
import com.webunime.mobile.data.EpisodeSummary
import com.webunime.mobile.data.PlayerRouter
import com.webunime.mobile.data.PlayerServer
import com.webunime.mobile.data.WebPlayerProxy
import com.webunime.mobile.ui.components.MetaChip
import com.webunime.mobile.ui.components.SectionTitle
import com.webunime.mobile.ui.components.SimpleDropdown
import com.webunime.mobile.ui.theme.Appear
import com.webunime.mobile.ui.theme.WebunimeTheme
import com.webunime.mobile.ui.theme.WuBg
import com.webunime.mobile.ui.theme.WuStroke
import com.webunime.mobile.ui.theme.WuSurface

class PlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        val slug = intent.getStringExtra(EXTRA_SLUG).orEmpty()
        val startEpisode = intent.getIntExtra(EXTRA_EPISODE, 1)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val collection = intent.getStringExtra(EXTRA_COLLECTION)?.takeIf { it.isNotBlank() } ?: "anime"
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

                var episodes by remember { mutableStateOf<List<EpisodeSummary>>(emptyList()) }
                var currentEpisode by remember { mutableIntStateOf(startEpisode) }
                var players by remember { mutableStateOf<List<PlayerServer>>(emptyList()) }
                var selectedServer by remember { mutableIntStateOf(0) }
                var loading by remember { mutableStateOf(true) }
                var error by remember { mutableStateOf<String?>(null) }
                var epTitle by remember { mutableStateOf(title) }
                var animeTitle by remember { mutableStateOf(title) }
                var animeDetail by remember { mutableStateOf<AnimeDetail?>(null) }

                LaunchedEffect(slug, collection) {
                    runCatching { app.catalogApi.anime(slug, collection) }
                        .onSuccess { detail ->
                            animeDetail = detail
                            animeTitle = detail.displayTitle()
                            episodes = detail.episodes.sortedBy { it.episode ?: 0 }
                        }
                }

                LaunchedEffect(slug, collection, currentEpisode) {
                    loading = true
                    error = null
                    runCatching { app.catalogApi.episode(slug, currentEpisode, collection) }
                        .onSuccess { payload ->
                            epTitle = payload.episode?.title
                                ?: payload.judul
                                ?: animeTitle
                            players = PlayerRouter.preferred(
                                payload.episode?.players.orEmpty(),
                                film = collection == "movies" ||
                                    collection == "horror" ||
                                    collection == "series",
                            )
                            selectedServer = 0
                            if (players.isEmpty()) error = "Tidak ada server player"
                        }
                        .onFailure { error = it.message }
                    loading = false
                }

                val isMovieCatalog =
                    collection == "movies" || collection == "horror"
                val selectedEp = episodes.firstOrNull { it.episode == currentEpisode }
                    ?: episodes.firstOrNull()
                val url = players.getOrNull(selectedServer)?.url.orEmpty()

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
                                IconButton(onClick = { finish() }) {
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
                                url.isNotBlank() -> PlaybackSurface(url = url)
                            }
                        }

                        if (!landscape) {
                            PlayerInfoPanel(
                                animeTitle = animeTitle,
                                animeDetail = animeDetail,
                                hideEpisodes = isMovieCatalog,
                                episodes = episodes,
                                selectedEp = selectedEp,
                                players = players,
                                selectedServer = selectedServer,
                                onSelectEpisode = { n -> currentEpisode = n },
                                onSelectServer = { idx -> selectedServer = idx },
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
                            onClick = { finish() },
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
                    if (hideEpisodes) "Pilih server pemutar" else "Pilih episode dan server",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
private fun PlaybackSurface(url: String) {
    val context = LocalContext.current
    var playUrl by remember { mutableStateOf(url) }
    var resolving by remember { mutableStateOf(EmbedPlayback.isWibufileEmbed(url)) }

    LaunchedEffect(url) {
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
            onDispose { player.release() }
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
            },
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        val activity = context as? android.app.Activity
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
                    setLayerType(View.LAYER_TYPE_NONE, null)
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
                        FilmPlaybackBridge { next ->
                            activity?.runOnUiThread {
                                if (next.isBlank()) return@runOnUiThread
                                playUrl = next
                            }
                        },
                        "WebunimePlayback",
                    )
                    tag = ""
                }
            },
            update = { webView ->
                val loaded = webView.tag as? String
                if (loaded == playUrl) return@AndroidView
                webView.tag = playUrl
                when {
                    FilmEmbed.isIframe3(playUrl) -> {
                        val parsed = FilmEmbed.parseIframe3(playUrl)
                        if (parsed == null) {
                            webView.loadUrl(playUrl)
                        } else {
                            val (host, id) = parsed
                            webView.loadDataWithBaseURL(
                                FilmEmbed.wrapperOrigin(playUrl),
                                FilmEmbed.iframe3BootstrapHtml(host, id),
                                "text/html",
                                "utf-8",
                                null,
                            )
                        }
                    }
                    WebPlayerProxy.isAbyss(playUrl) -> {
                        webView.loadDataWithBaseURL(
                            WebPlayerProxy.ABYSS_WRAPPER_BASE,
                            WebPlayerProxy.abyssWrapperHtml(playUrl),
                            "text/html",
                            "utf-8",
                            null,
                        )
                    }
                    WebPlayerProxy.isTurbo(playUrl) -> {
                        webView.loadDataWithBaseURL(
                            WebPlayerProxy.ABYSS_WRAPPER_BASE,
                            WebPlayerProxy.turboWrapperHtml(playUrl),
                            "text/html",
                            "utf-8",
                            null,
                        )
                    }
                    WebPlayerProxy.isP2p(playUrl) -> {
                        webView.loadDataWithBaseURL(
                            WebPlayerProxy.VIDEONODE_WRAPPER_BASE,
                            WebPlayerProxy.turboWrapperHtml(playUrl),
                            "text/html",
                            "utf-8",
                            null,
                        )
                    }
                    useIframe -> {
                        webView.loadDataWithBaseURL(
                            EmbedPlayback.wrapperBaseUrl(playUrl),
                            EmbedPlayback.iframeWrapperHtml(playUrl),
                            "text/html",
                            "utf-8",
                            null,
                        )
                    }
                    else -> {
                        val headers = mutableMapOf<String, String>()
                        if (playUrl.contains("wibufile", ignoreCase = true)) {
                            headers["Referer"] = "https://api.wibufile.com/"
                        }
                        if (headers.isEmpty()) webView.loadUrl(playUrl)
                        else webView.loadUrl(playUrl, headers)
                    }
                }
            },
            onRelease = { webView ->
                webView.stopLoading()
                webView.destroy()
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private class FilmPlaybackBridge(
    private val onEmbed: (String) -> Unit,
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
    fun onPlay() { }

    @JavascriptInterface
    fun onPause() { }

    @JavascriptInterface
    fun onEnded() { }
}
