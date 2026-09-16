package com.webunime.mobile.ui.detail

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.webunime.mobile.WebunimeApp
import com.webunime.mobile.data.AnimeDetail
import com.webunime.mobile.data.EpisodeSummary
import com.webunime.mobile.data.SeasonGroup
import com.webunime.mobile.ui.components.MetaChip
import com.webunime.mobile.ui.components.SectionTitle
import com.webunime.mobile.ui.components.SimpleDropdown
import com.webunime.mobile.ui.player.PlayerActivity
import com.webunime.mobile.ui.theme.Appear
import com.webunime.mobile.ui.theme.WuBg
import com.webunime.mobile.ui.theme.WuStroke
import com.webunime.mobile.ui.theme.WuSurface
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    slug: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as WebunimeApp
    val scope = rememberCoroutineScope()

    var detail by remember { mutableStateOf<AnimeDetail?>(null) }
    var seasons by remember { mutableStateOf<List<SeasonGroup>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var selectedBySeason by remember { mutableStateOf<Map<String, EpisodeSummary>>(emptyMap()) }

    fun reload() {
        scope.launch {
            loading = true
            error = null
            runCatching {
                val d = app.catalogApi.anime(slug)
                val s = app.catalogApi.seasonsFor(slug)
                d to s
            }.onSuccess { (d, s) ->
                detail = d
                seasons = s
                selectedBySeason = s.associate { group ->
                    val pick = group.episodes.lastOrNull()
                        ?: group.episodes.firstOrNull()
                    group.animeSlug to (pick ?: EpisodeSummary())
                }.filterValues { it.episode != null }
            }.onFailure { error = it.message }
            loading = false
        }
    }

    fun openPlayer(animeSlug: String, episode: Int, title: String) {
        val i = Intent(context, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_SLUG, animeSlug)
            putExtra(PlayerActivity.EXTRA_EPISODE, episode)
            putExtra(PlayerActivity.EXTRA_TITLE, title)
        }
        context.startActivity(i)
        scope.launch {
            runCatching { app.userRepository.grantEpisodeXp() }
        }
    }

    LaunchedEffect(slug) { reload() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        detail?.displayTitle() ?: "Detail",
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = WuSurface.copy(alpha = 0.92f),
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(listOf(WuSurface.copy(alpha = 0.55f), WuBg, WuBg)),
                ),
        ) {
            AnimatedContent(
                targetState = when {
                    loading && detail == null -> "loading"
                    error != null && detail == null -> "error"
                    else -> "content"
                },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "detailState",
            ) { state ->
                when (state) {
                    "loading" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                    "error" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(error ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { reload() }) { Text("Coba lagi") }
                        }
                    }
                    else -> {
                        val data = detail ?: return@AnimatedContent
                        val shape = MaterialTheme.shapes.medium
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Appear {
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Box(
                                        Modifier
                                            .weight(0.38f)
                                            .aspectRatio(2f / 3f)
                                            .clip(shape)
                                            .border(1.dp, WuStroke.copy(alpha = 0.55f), shape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                    ) {
                                        AsyncImage(
                                            model = data.thumbnail,
                                            contentDescription = data.displayTitle(),
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .height(40.dp)
                                                .align(Alignment.BottomCenter)
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(
                                                            Color.Transparent,
                                                            Color.Black.copy(alpha = 0.45f),
                                                        ),
                                                    ),
                                                ),
                                        )
                                    }
                                    Column(modifier = Modifier.weight(0.62f)) {
                                        Text(
                                            data.displayTitle(),
                                            style = MaterialTheme.typography.titleLarge,
                                        )
                                        Spacer(Modifier.height(10.dp))
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            data.rating?.takeIf { it.isNotBlank() }?.let {
                                                MetaChip("\u2605 $it", accent = true)
                                            }
                                            data.type?.takeIf { it.isNotBlank() }?.let {
                                                MetaChip(it)
                                            }
                                            data.episodes_count?.let {
                                                MetaChip("$it episode")
                                            }
                                        }
                                        data.genre?.takeIf { it.isNotEmpty() }?.let { genres ->
                                            Text(
                                                genres.joinToString(" · "),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 10.dp),
                                            )
                                        }
                                    }
                                }
                            }

                            data.sinopsis?.takeIf { it.isNotBlank() }?.let { syn ->
                                Appear(delayMs = 80) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

                            Appear(delayMs = 140) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    HorizontalDivider(color = WuStroke.copy(alpha = 0.6f))
                                    SectionTitle(
                                        "Tonton",
                                        modifier = Modifier.padding(horizontal = 0.dp),
                                    )
                                    Text(
                                        "Pilih episode untuk mulai menonton",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )

                                    val groups = seasons.ifEmpty {
                                        listOf(
                                            SeasonGroup(
                                                label = "Episode",
                                                animeSlug = slug,
                                                episodes = data.episodes,
                                                isCurrent = true,
                                            ),
                                        )
                                    }

                                    groups.forEach { group ->
                                        val selected = selectedBySeason[group.animeSlug]
                                        val dropdownLabel =
                                            if (groups.size > 1) group.label else "Episode"
                                        SimpleDropdown(
                                            label = dropdownLabel,
                                            options = group.episodes,
                                            selected = selected,
                                            optionLabel = { it.displayLabel() },
                                            onSelect = { ep ->
                                                selectedBySeason =
                                                    selectedBySeason + (group.animeSlug to ep)
                                                val n = ep.episode ?: return@SimpleDropdown
                                                openPlayer(
                                                    group.animeSlug,
                                                    n,
                                                    data.displayTitle(),
                                                )
                                            },
                                        )
                                    }

                                    if (groups.all { it.episodes.isEmpty() }) {
                                        Text(
                                            "Belum ada episode.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}
