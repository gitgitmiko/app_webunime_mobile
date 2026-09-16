package com.webunime.mobile.ui.detail

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.webunime.mobile.WebunimeApp
import com.webunime.mobile.data.AnimeDetail
import com.webunime.mobile.ui.account.WatchGateDialog
import com.webunime.mobile.ui.player.PlayerActivity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    slug: String,
    onBack: () -> Unit,
    onGoAccount: () -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context as Activity
    val app = context.applicationContext as WebunimeApp
    val scope = rememberCoroutineScope()
    val profile by app.userRepository.profileFlow.collectAsStateWithLifecycle(initialValue = null)

    var detail by remember { mutableStateOf<AnimeDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var pendingEpisode by remember { mutableStateOf<Int?>(null) }
    var showGate by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            loading = true
            error = null
            runCatching { app.catalogApi.anime(slug) }
                .onSuccess { detail = it }
                .onFailure { error = it.message }
            loading = false
        }
    }

    fun openPlayer(episode: Int, title: String) {
        val i = Intent(context, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_SLUG, slug)
            putExtra(PlayerActivity.EXTRA_EPISODE, episode)
            putExtra(PlayerActivity.EXTRA_TITLE, title)
        }
        context.startActivity(i)
    }

    fun tryPlay(episode: Int, title: String) {
        scope.launch {
            val p = app.userRepository.current()
            if (p.effectivePremium()) {
                openPlayer(episode, title)
                return@launch
            }
            if (p.keys <= 0) {
                pendingEpisode = episode
                showGate = true
                return@launch
            }
            val res = app.userRepository.consumeKeyForEpisode(slug, episode)
            res.onSuccess {
                openPlayer(episode, title)
            }.onFailure {
                pendingEpisode = episode
                showGate = true
            }
        }
    }

    LaunchedEffect(slug) { reload() }

    if (showGate) {
        WatchGateDialog(
            keys = profile?.keys ?: 0,
            isPremium = profile?.effectivePremium() == true,
            onWatchAd = {
                scope.launch {
                    val ok = app.rewardedAds.show(activity)
                    if (ok) {
                        app.userRepository.grantKeys(1)
                        Toast.makeText(context, "+1 kunci", Toast.LENGTH_SHORT).show()
                        val ep = pendingEpisode
                        val title = detail?.displayTitle().orEmpty()
                        showGate = false
                        if (ep != null) tryPlay(ep, title)
                    } else {
                        Toast.makeText(context, "Iklan belum siap", Toast.LENGTH_SHORT).show()
                        app.rewardedAds.preload()
                    }
                }
            },
            onGoAccount = {
                showGate = false
                onGoAccount()
            },
            onDismiss = { showGate = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.displayTitle() ?: "Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        when {
            loading && detail == null -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            error != null && detail == null -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: "")
                    TextButton(onClick = { reload() }) { Text("Coba lagi") }
                }
            }

            else -> {
                val data = detail ?: return@Scaffold
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        val keys = profile?.keys ?: 0
                        val premium = profile?.effectivePremium() == true
                        Text(
                            if (premium) "Premium aktif · nonton bebas"
                            else "Kunci: $keys · 1 episode = 1 kunci",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            AsyncImage(
                                model = data.thumbnail,
                                contentDescription = data.displayTitle(),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(0.38f)
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                            Column(modifier = Modifier.weight(0.62f)) {
                                Text(data.displayTitle(), style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(8.dp))
                                data.rating?.let {
                                    Text("★ $it", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                data.genre?.takeIf { it.isNotEmpty() }?.let {
                                    Text(
                                        it.joinToString(", "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                data.episodes_count?.let {
                                    Text("$it episode", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    data.sinopsis?.takeIf { it.isNotBlank() }?.let { syn ->
                        item {
                            Text("Sinopsis", style = MaterialTheme.typography.titleMedium)
                            Text(syn, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    item { Text("Episode", style = MaterialTheme.typography.titleMedium) }
                    items(data.episodes.asReversed()) { ep ->
                        val n = ep.episode ?: return@items
                        Button(
                            onClick = { tryPlay(n, data.displayTitle()) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(ep.title?.takeIf { it.isNotBlank() } ?: "Episode $n")
                        }
                    }
                }
            }
        }
    }
}
