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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.webunime.mobile.WebunimeApp
import com.webunime.mobile.data.HomeResponse
import com.webunime.mobile.ui.components.BrandHeader
import com.webunime.mobile.ui.components.HorizontalPosterRow
import com.webunime.mobile.ui.components.SectionTitle
import com.webunime.mobile.ui.theme.Appear
import com.webunime.mobile.ui.theme.WuBg
import com.webunime.mobile.ui.theme.WuSurface
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onOpenAnime: (slug: String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
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
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
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
