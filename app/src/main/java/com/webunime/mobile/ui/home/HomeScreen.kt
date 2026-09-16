package com.webunime.mobile.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.webunime.mobile.WebunimeApp
import com.webunime.mobile.data.HomeResponse
import com.webunime.mobile.ui.components.HorizontalPosterRow
import com.webunime.mobile.ui.components.SectionTitle
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

    when {
        loading && home == null -> Box(
            Modifier.fillMaxSize().padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        error != null && home == null -> Box(
            Modifier.fillMaxSize().padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { reload() }) { Text("Coba lagi") }
            }
        }

        else -> {
            val data = home ?: return
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        text = "WEBUNIME",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                if (data.latest.isNotEmpty()) {
                    item { SectionTitle("Anime Terbaru") }
                    item {
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
                data.scheduleToday?.takeIf { it.items.isNotEmpty() }?.let { day ->
                    item { SectionTitle("Jadwal ${day.label ?: "Hari Ini"}") }
                    item {
                        HorizontalPosterRow(
                            items = day.items,
                            titleOf = { it.displayTitle() },
                            thumbOf = { it.thumbnail },
                            subtitleOf = { it.time },
                            onClick = { item ->
                                item.slug?.let(onOpenAnime)
                            },
                        )
                    }
                }
                if (data.movies.isNotEmpty()) {
                    item { SectionTitle("Anime Movie") }
                    item {
                        HorizontalPosterRow(
                            items = data.movies,
                            titleOf = { it.displayTitle() },
                            thumbOf = { it.thumbnail },
                            subtitleOf = { it.rating?.let { r -> "â˜… $r" } },
                            onClick = { item ->
                                item.slug?.let(onOpenAnime)
                            },
                        )
                    }
                }
            }
        }
    }
}
