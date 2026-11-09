package com.webunime.mobile.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.webunime.mobile.WebunimeApp
import com.webunime.mobile.data.AnimeCard
import com.webunime.mobile.data.FilmHome
import com.webunime.mobile.data.HomeResponse
import com.webunime.mobile.data.LatestItem
import com.webunime.mobile.ui.components.BrandHeader
import com.webunime.mobile.ui.components.HorizontalPosterRow
import com.webunime.mobile.ui.components.PagedPosterRow
import com.webunime.mobile.ui.components.PagedRowState
import com.webunime.mobile.ui.components.SectionTitle
import com.webunime.mobile.ui.components.rememberPagedRow
import com.webunime.mobile.ui.theme.WuBg
import com.webunime.mobile.ui.theme.WuSurface
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    catalogMode: String,
    onOpenAnime: (slug: String) -> Unit,
    onOpenTitle: (collection: String, slug: String, episode: Int) -> Unit,
    onChangeCatalog: () -> Unit,
    onOpenRooms: () -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
) {
    if (catalogMode == "film") {
        FilmCatalog(
            contentPadding = contentPadding,
            onBack = onChangeCatalog,
            onOpenTitle = onOpenTitle,
            onOpenRooms = onOpenRooms,
            onOpenFavorites = onOpenFavorites,
            onOpenNotifications = onOpenNotifications,
        )
        return
    }

    val app = LocalContext.current.applicationContext as WebunimeApp
    val scope = rememberCoroutineScope()
    var home by remember { mutableStateOf<HomeResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

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
                    val latestRow = rememberPagedRow(data.latest, LatestItem::rowKey) {
                        app.catalogApi.latestItemsPage("anime-latest", it)
                    }
                    val animeRow = rememberPagedRow(data.anime, AnimeCard::rowKey) {
                        app.catalogApi.cardsPage("anime", it)
                    }
                    val moviesRow = rememberPagedRow(data.movies, AnimeCard::rowKey) {
                        app.catalogApi.cardsPage("anime-movies", it)
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onChangeCatalog) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Katalog")
                                }
                                Text(
                                    "Anime",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                RoomsButton(onOpenRooms)
                                NotificationsButton(onOpenNotifications)
                            }
                        }
                        item { BrandHeader() }
                        favoritesRow(anime = true, onOpenTitle = onOpenTitle, onSeeAll = onOpenFavorites)
                        if (data.latest.isNotEmpty()) {
                            item { SectionTitle("Anime Terbaru") }
                            item {
                                PagedPosterRow(
                                    state = latestRow,
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
                        if (data.anime.isNotEmpty()) {
                            item { SectionTitle("Anime") }
                            item {
                                PagedPosterRow(
                                    state = animeRow,
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
                        if (data.movies.isNotEmpty()) {
                            item { SectionTitle("Anime Movie") }
                            item {
                                PagedPosterRow(
                                    state = moviesRow,
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
                        data.scheduleToday?.takeIf { it.items.isNotEmpty() }?.let { day ->
                            item { SectionTitle("Jadwal ${day.label ?: "Hari Ini"}") }
                            item {
                                HorizontalPosterRow(
                                    items = day.items,
                                    titleOf = { it.displayTitle() },
                                    thumbOf = { it.thumbnail },
                                    subtitleOf = { it.time },
                                    onClick = { item -> item.slug?.let(onOpenAnime) },
                                )
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
private fun FilmCatalog(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenTitle: (collection: String, slug: String, episode: Int) -> Unit,
    onOpenRooms: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenNotifications: () -> Unit,
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
                val moviesRow = rememberPagedRow(data.latestMovies, AnimeCard::rowKey) {
                    app.catalogApi.cardsPage("movies", it)
                }
                val horrorRow = rememberPagedRow(data.latestHorror, AnimeCard::rowKey) {
                    app.catalogApi.cardsPage("horror", it)
                }
                val latestSeriesRow = rememberPagedRow(data.latestSeries, LatestItem::rowKey) {
                    app.catalogApi.latestItemsPage("series-latest", it)
                }
                val seriesRow = rememberPagedRow(data.series, AnimeCard::rowKey) {
                    app.catalogApi.cardsPage("series", it)
                }
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
                                modifier = Modifier.weight(1f),
                            )
                            RoomsButton(onOpenRooms)
                            NotificationsButton(onOpenNotifications)
                        }
                    }
                    favoritesRow(anime = false, onOpenTitle = onOpenTitle, onSeeAll = onOpenFavorites)
                    filmRow("Film Terbaru", moviesRow, "movies", onOpenTitle)
                    filmRow("Top Film", data.topMovies, "movies", onOpenTitle)
                    filmRow("Horor Terbaru", horrorRow, "horror", onOpenTitle)
                    filmRow("Top Horor", data.topHorror, "horror", onOpenTitle)
                    if (data.latestSeries.isNotEmpty()) {
                        item { SectionTitle("Series Terbaru") }
                        item {
                            PagedPosterRow(
                                state = latestSeriesRow,
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
                    filmRow("Series", seriesRow, "series", onOpenTitle)
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
    items: List<AnimeCard>,
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
            subtitleOf = ::ratingLabel,
            onClick = { openFilm(it, collection, onOpenTitle) },
        )
    }
}

/** Baris film yang memuat page berikutnya setiap kali digeser sampai mentok. */
private fun androidx.compose.foundation.lazy.LazyListScope.filmRow(
    title: String,
    row: PagedRowState<AnimeCard>,
    collection: String,
    onOpenTitle: (collection: String, slug: String, episode: Int) -> Unit,
) {
    if (row.items.isEmpty()) return
    item { SectionTitle(title) }
    item {
        PagedPosterRow(
            state = row,
            titleOf = { it.displayTitle() },
            thumbOf = { it.thumbnail },
            subtitleOf = ::ratingLabel,
            onClick = { openFilm(it, collection, onOpenTitle) },
        )
    }
}

@Composable
private fun RoomsButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
        Text("Nonton Bareng")
    }
}

@Composable
private fun NotificationsButton(onClick: () -> Unit) {
    val app = LocalContext.current.applicationContext as WebunimeApp
    val user by app.webAuth.user.collectAsState()
    if (user == null) return
    val unread = app.episodeAlerts.inbox.collectAsState().value.count { !it.read }
    IconButton(onClick = onClick) {
        BadgedBox(badge = { if (unread > 0) Badge { Text(if (unread > 9) "9+" else "$unread") } }) {
            Icon(Icons.Default.Notifications, contentDescription = "Notifikasi")
        }
    }
}

/** Baris "Favorit Saya" (akun yang masuk saja), dipisah per katalog anime / film. */
private fun androidx.compose.foundation.lazy.LazyListScope.favoritesRow(
    anime: Boolean,
    onOpenTitle: (collection: String, slug: String, episode: Int) -> Unit,
    onSeeAll: () -> Unit,
) {
    item(key = "favorites-row") {
        val app = LocalContext.current.applicationContext as WebunimeApp
        val favorites by app.favorites.items.collectAsState()
        val shown = favorites.filter { it.isAnimeCatalog() == anime }
        if (shown.isEmpty()) return@item
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Favorit Saya", modifier = Modifier.weight(1f))
                TextButton(onClick = onSeeAll) { Text("Lihat semua") }
            }
            HorizontalPosterRow(
                items = shown,
                titleOf = { it.displayTitle() },
                thumbOf = { it.thumbnail },
                onClick = { fav -> onOpenTitle(fav.appCollection(), fav.slug, -1) },
            )
        }
    }
}

private fun ratingLabel(card: AnimeCard): String? =
    card.rating?.takeIf { it.isNotBlank() }?.let { "\u2605 $it" }

private fun openFilm(
    card: AnimeCard,
    collection: String,
    onOpenTitle: (collection: String, slug: String, episode: Int) -> Unit,
) {
    val slug = card.slug?.takeIf { it.isNotBlank() } ?: return
    onOpenTitle(collection, slug, -1)
}

private fun AnimeCard.rowKey(): String = slug?.takeIf { it.isNotBlank() } ?: displayTitle()

private fun LatestItem.rowKey(): String = "${catalogSlug()}#${episode ?: ""}#${displayTitle()}"
