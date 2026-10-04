package com.webunime.mobile.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.webunime.mobile.WebunimeApp
import com.webunime.mobile.data.AnimeCard
import com.webunime.mobile.ui.theme.Appear
import com.webunime.mobile.ui.theme.WuBg
import com.webunime.mobile.ui.theme.WuStroke
import com.webunime.mobile.ui.theme.WuSurface
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    catalogMode: String,
    onOpenTitle: (collection: String, slug: String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val app = LocalContext.current.applicationContext as WebunimeApp
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<AnimeCard>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun search(q: String) {
        job?.cancel()
        if (q.trim().length < 2) {
            items = emptyList()
            error = null
            loading = false
            return
        }
        job = scope.launch {
            delay(350)
            loading = true
            error = null
            runCatching { app.catalogApi.search(q.trim(), catalogMode = catalogMode) }
                .onSuccess { items = it.items }
                .onFailure { error = it.message }
            loading = false
        }
    }

    LaunchedEffect(catalogMode) {
        if (query.trim().length >= 2) search(query)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(WuSurface.copy(alpha = 0.55f), WuBg, WuBg)),
            )
            .padding(contentPadding),
    ) {
        Appear {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("Cari", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Ketik minimal 2 huruf",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        search(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    label = {
                        Text(if (catalogMode == "film") "Judul film atau series" else "Judul anime")
                    },
                    placeholder = {
                        Text(if (catalogMode == "film") "Contoh: Night of Blood" else "Contoh: One Piece")
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = WuStroke,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            error != null -> Text(
                error ?: "",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            items.isEmpty() && query.trim().length >= 2 -> Text(
                "Tidak ada hasil",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items, key = { it.slug ?: it.displayTitle() }) { item ->
                    AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
                        val shape = MaterialTheme.shapes.medium
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .border(1.dp, WuStroke.copy(alpha = 0.5f), shape)
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable {
                                    val slug = item.slug?.takeIf { it.isNotBlank() } ?: return@clickable
                                    onOpenTitle(item.catalog ?: catalogMode, slug)
                                }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = item.thumbnail,
                                contentDescription = item.displayTitle(),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(64.dp, 90.dp)
                                    .clip(MaterialTheme.shapes.small),
                            )
                            Column {
                                Text(item.displayTitle(), style = MaterialTheme.typography.titleSmall)
                                val kind = when (item.catalog) {
                                    "horror" -> "Horor"
                                    "series" -> "Series"
                                    "movies" -> "Film"
                                    else -> item.type
                                }
                                if (!kind.isNullOrBlank() || !item.rating.isNullOrBlank()) {
                                    Text(
                                        listOfNotNull(
                                            kind?.takeIf { it.isNotBlank() },
                                            item.rating?.takeIf { it.isNotBlank() }?.let { "★ $it" },
                                        ).joinToString(" · "),
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
