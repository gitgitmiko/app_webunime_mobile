package com.webunime.mobile.ui.player

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.webunime.mobile.WebunimeApp
import com.webunime.mobile.data.PlayerRouter
import com.webunime.mobile.data.PlayerServer
import com.webunime.mobile.ui.theme.WebunimeTheme

class PlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val slug = intent.getStringExtra(EXTRA_SLUG).orEmpty()
        val episode = intent.getIntExtra(EXTRA_EPISODE, 1)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val app = application as WebunimeApp

        setContent {
            WebunimeTheme {
                var players by remember { mutableStateOf<List<PlayerServer>>(emptyList()) }
                var selected by remember { mutableIntStateOf(0) }
                var loading by remember { mutableStateOf(true) }
                var error by remember { mutableStateOf<String?>(null) }
                var epTitle by remember { mutableStateOf(title) }

                LaunchedEffect(slug, episode) {
                    loading = true
                    error = null
                    runCatching { app.catalogApi.episode(slug, episode) }
                        .onSuccess { payload ->
                            epTitle = payload.episode?.title
                                ?: payload.judul
                                ?: title
                            players = PlayerRouter.preferred(payload.episode?.players.orEmpty())
                            selected = 0
                            if (players.isEmpty()) error = "Tidak ada server player"
                            else runCatching { app.userRepository.grantEpisodeXp() }
                        }
                        .onFailure { error = it.message }
                    loading = false
                }

                Column(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                        }
                        Text(
                            epTitle,
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    when {
                        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        error != null && players.isEmpty() -> Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(error ?: "", color = Color.White)
                        }
                        else -> {
                            if (players.isNotEmpty()) {
                                LazyRow(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                    itemsIndexed(players) { index, server ->
                                        FilterChip(
                                            selected = index == selected,
                                            onClick = { selected = index },
                                            label = { Text(server.displayLabel()) },
                                            modifier = Modifier.padding(end = 6.dp),
                                        )
                                    }
                                }
                                val url = players.getOrNull(selected)?.url.orEmpty()
                                Box(Modifier.weight(1f).fillMaxWidth()) {
                                    if (url.isNotBlank()) {
                                        PlaybackSurface(url = url)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_SLUG = "slug"
        const val EXTRA_EPISODE = "episode"
        const val EXTRA_TITLE = "title"
    }
}

@Composable
private fun PlaybackSurface(url: String) {
    val context = LocalContext.current
    val direct = remember(url) { PlayerRouter.isDirectMedia(url) }

    if (direct) {
        val player = remember(url) {
            ExoPlayer.Builder(context).build().also {
                it.setMediaItem(MediaItem.fromUri(url))
                it.prepare()
                it.playWhenReady = true
                it.repeatMode = Player.REPEAT_MODE_OFF
            }
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
            modifier = Modifier.fillMaxSize(),
        )
    } else {
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
                    webChromeClient = WebChromeClient()
                    webViewClient = WebViewClient()
                    loadUrl(url)
                }
            },
            update = { it.loadUrl(url) },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
