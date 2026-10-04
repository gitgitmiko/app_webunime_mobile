package com.webunime.mobile.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.webunime.mobile.WebunimeApp
import com.webunime.mobile.data.FilmHome
import com.webunime.mobile.data.HomeResponse
import com.webunime.mobile.ui.components.BrandHeader
import com.webunime.mobile.ui.components.HorizontalPosterRow
import com.webunime.mobile.ui.components.SectionTitle
import com.webunime.mobile.ui.theme.Appear
import com.webunime.mobile.ui.theme.WuBg
import com.webunime.mobile.ui.theme.WuStroke
import com.webunime.mobile.ui.theme.WuSurface
import com.webunime.mobile.ui.theme.WuSurfaceHigh
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onOpenAnime: (slug: String) -> Unit,
    onOpenTitle: (collection: String, slug: String, episode: Int) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val app = LocalContext.current.applicationContext as WebunimeApp
    val scope = rememberCoroutineScope()
    var home by remember { mutableStateOf<HomeResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var mode by rememberSaveable { mutableStateOf<String?>(null) }

    if (mode == null) {
        CatalogChooser(contentPadding) { picked -> mode = picked }
        return
    }
    if (mode == "film") {
        FilmCatalog(
            contentPadding = contentPadding,
            onBack = { mode = null },
            onOpenTitle = onOpenTitle,
        )
        return
    }

    fun reload() {
        scope.launch {
            loading = true
            error = null
            runCatching { app.catalogApi.home() }
                .onSuccess { home = it }
                .onFailure { error = it.message ?: "Gagal memuat" }
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    Box(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .background(
                Brush.verticalGradient(
                    listOf(WuSurface.copy(alpha = 0.9f), WuBg, WuBg),
                ),
            ),
    ) {
        AnimatedContent(
            targetState = when {
                loading && home == null -> "loading"
                error != null && home == null -> "error"
                else -> "content"
            },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "homeState",
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
                    val data = home ?: return@AnimatedContent
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        item {
                            Appear {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { mode = null }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Katalog")
                                    }
                                    Text(
                                        "Anime",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                        item {
                            Appear { BrandHeader() }
                        }
                        if (data.latest.isNotEmpty()) {
                            item {
                                Appear(delayMs = 60) { SectionTitle("Anime Terbaru") }
                            }
                            item {
                                Appear(delayMs = 100) {
                                    HorizontalPosterRow(
                                        items = data.latest,
                                        titleOf = { it.displayTitle() },
                                        thumbOf = { it.thumbnail },
                                        subtitleOf = { it.episode?.let { e -> "Ep $e" } },
                                        onClick = { item ->
                                            val slug = item.catalogSlug()
                                            if (slug.isNotBlank()) onOpenAnime(slug)
                                        },
                                    )
                                }
                            }
                        }
                        if (data.anime.isNotEmpty()) {
                            item {
                                Appear(delayMs = 140) { SectionTitle("Anime") }
                            }
                            item {
                                Appear(delayMs = 180) {
                                    HorizontalPosterRow(
                                        items = data.anime,
                                        titleOf = { it.displayTitle() },
                                        thumbOf = { it.thumbnail },
                                        subtitleOf = {
                                            it.rating?.takeIf { r -> r.isNotBlank() }
                                                ?.let { r -> "\u2605 $r" }
                                        },
                                        onClick = { item -> item.slug?.let(onOpenAnime) },
                                    )
                                }
                            }
                        }
                        if (data.movies.isNotEmpty()) {
                            item {
                                Appear(delayMs = 220) { SectionTitle("Anime Movie") }
                            }
                            item {
                                Appear(delayMs = 260) {
                                    HorizontalPosterRow(
                                        items = data.movies,
                                        titleOf = { it.displayTitle() },
                                        thumbOf = { it.thumbnail },
                                        subtitleOf = {
                                            it.rating?.takeIf { r -> r.isNotBlank() }
                                                ?.let { r -> "\u2605 $r" }
                                        },
                                        onClick = { item -> item.slug?.let(onOpenAnime) },
                                    )
                                }
                            }
                        }
                        data.scheduleToday?.takeIf { it.items.isNotEmpty() }?.let { day ->
                            item {
                                Appear(delayMs = 300) {
                                    SectionTitle("Jadwal ${day.label ?: "Hari Ini"}")
                                }
                            }
                            item {
                                Appear(delayMs = 340) {
                                    HorizontalPosterRow(
                                        items = day.items,
                                        titleOf = { it.displayTitle() },
                                        thumbOf = { it.thumbnail },
                                        subtitleOf = { it.time },
                                        onClick = { item -> item.slug?.let(onOpenAnime) },
                                    )
                                }
                            }
                        }
                        item { Box(Modifier.fillMaxWidth().height(8.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogChooser(
    contentPadding: PaddingValues,
    onPick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .background(
                Brush.verticalGradient(listOf(WuSurface.copy(alpha = 0.9f), WuBg, WuBg)),
            )
            .padding(horizontal = 16.dp),
    ) {
        BrandHeader(subtitle = "Pilih Anime atau Film")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CatalogPickCard(
                title = "Anime",
                subtitle = "Episode, movie, jadwal",
                modifier = Modifier.weight(1f),
                onClick = { onPick("anime") },
            )
            CatalogPickCard(
                title = "Film",
                subtitle = "Film, horor, series",
                modifier = Modifier.weight(1f),
                onClick = { onPick("film") },
            )
        }
    }
}

@Composable
private fun CatalogPickCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .height(168.dp)
            .clip(shape)
            .background(WuSurfaceHigh)
            .border(1.dp, WuStroke, shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FilmCatalog(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenTitle: (collection: String, slug: String, episode: Int) -> Unit,
) {
    val app = LocalContext.current.applicationContext as WebunimeApp
    val scope = rememberCoroutineScope()
    var home by remember { mutableStateOf<FilmHome?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    fun reload() {
        scope.launch {
            loading = true
            error = null
            runCatching { app.catalogApi.filmHome() }
                .onSuccess { home = it }
                .onFailure { error = it.message ?: "Gagal memuat film" }
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    Box(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .background(
                Brush.verticalGradient(listOf(WuSurface.copy(alpha = 0.9f), WuBg, WuBg)),
            ),
    ) {
        when {
            loading && home == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            error != null && home == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { reload() }) { Text("Coba lagi") }
                }
            }
            else -> {
                val data = home ?: return@Box
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Katalog")
                            }
                            Text(
                                "Film",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    filmRow("Film Terbaru", data.latestMovies, "movies", onOpenTitle)
                    filmRow("Top Film", data.topMovies, "movies", onOpenTitle)
                    filmRow("Horor Terbaru", data.latestHorror, "horror", onOpenTitle)
                    filmRow("Top Horor", data.topHorror, "horror", onOpenTitle)
                    if (data.latestSeries.isNotEmpty()) {
                        item { SectionTitle("Series Terbaru") }
                        item {
                            HorizontalPosterRow(
                                items = data.latestSeries,
                                titleOf = { it.displayTitle() },
                                thumbOf = { it.thumbnail },
                                subtitleOf = { item ->
                                    item.episode?.let { ep -> "Ep $ep" }
                                },
                                onClick = { item ->
                                    val slug = item.catalogSlug()
                                    if (slug.isNotBlank()) {
                                        onOpenTitle("series", slug, item.episode ?: -1)
                                    }
                                },
                            )
                        }
                    }
                    filmRow("Series", data.series, "series", onOpenTitle)
                    if (
                        data.latestMovies.isEmpty() &&
                        data.latestHorror.isEmpty() &&
                        data.latestSeries.isEmpty() &&
                        data.series.isEmpty()
                    ) {
                        item {
                            Text(
                                "Katalog film belum bisa dimuat.",
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.filmRow(
    title: String,
    items: List<com.webunime.mobile.data.AnimeCard>,
    collection: String,
    onOpenTitle: (collection: String, slug: String, episode: Int) -> Unit,
) {
    if (items.isEmpty()) return
    item { SectionTitle(title) }
    item {
        HorizontalPosterRow(
            items = items,
            titleOf = { it.displayTitle() },
            thumbOf = { it.thumbnail },
            subtitleOf = { card ->
                card.rating?.takeIf { it.isNotBlank() }?.let { "\u2605 $it" }
            },
            onClick = { card ->
                val slug = card.slug?.takeIf { it.isNotBlank() } ?: return@HorizontalPosterRow
                onOpenTitle(collection, slug, -1)
            },
        )
    }
}
