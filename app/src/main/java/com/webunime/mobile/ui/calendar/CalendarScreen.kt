package com.webunime.mobile.ui.calendar

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.webunime.mobile.data.CalendarResponse
import com.webunime.mobile.ui.theme.Appear
import com.webunime.mobile.ui.theme.WuBg
import com.webunime.mobile.ui.theme.WuStroke
import com.webunime.mobile.ui.theme.WuSurface
import kotlinx.coroutines.launch

@Composable
fun CalendarScreen(
    onOpenAnime: (slug: String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val app = LocalContext.current.applicationContext as WebunimeApp
    val scope = rememberCoroutineScope()
    var cal by remember { mutableStateOf<CalendarResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableIntStateOf(0) }

    fun reload() {
        scope.launch {
            loading = true
            error = null
            runCatching { app.catalogApi.calendar() }
                .onSuccess { data ->
                    cal = data
                    val idx = data.days.indexOfFirst { it.day == data.today }
                    selected = if (idx >= 0) idx else 0
                }
                .onFailure { error = it.message }
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    Box(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .background(
                Brush.verticalGradient(listOf(WuSurface.copy(alpha = 0.55f), WuBg, WuBg)),
            ),
    ) {
        AnimatedContent(
            targetState = when {
                loading && cal == null -> "loading"
                error != null && cal == null -> "error"
                else -> "content"
            },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "calendarState",
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
                    val data = cal ?: return@AnimatedContent
                    val day = data.days.getOrNull(selected)
                    Column(Modifier.fillMaxSize()) {
                        Appear {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                                Text(
                                    "Jadwal Rilis",
                                    style = MaterialTheme.typography.headlineMedium,
                                )
                                Text(
                                    "Pilih hari untuk melihat anime yang tayang",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                        Appear(delayMs = 60) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                itemsIndexed(data.days) { index, d ->
                                    val selectedDay = index == selected
                                    FilterChip(
                                        selected = selectedDay,
                                        onClick = { selected = index },
                                        label = {
                                            Text(
                                                d.label?.takeIf { it.isNotBlank() }
                                                    ?: d.day?.replaceFirstChar { it.uppercase() }
                                                    ?: "?",
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor =
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = selectedDay,
                                            borderColor = WuStroke,
                                            selectedBorderColor =
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                        ),
                                    )
                                }
                            }
                        }
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(day?.items.orEmpty()) { item ->
                                val shape = MaterialTheme.shapes.medium
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(shape)
                                        .border(1.dp, WuStroke.copy(alpha = 0.5f), shape)
                                        .background(MaterialTheme.colorScheme.surface)
                                        .clickable { item.slug?.let(onOpenAnime) }
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AsyncImage(
                                        model = item.thumbnail,
                                        contentDescription = item.displayTitle(),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(56.dp, 80.dp)
                                            .clip(MaterialTheme.shapes.small),
                                    )
                                    Column {
                                        Text(
                                            item.displayTitle(),
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        val meta = listOfNotNull(
                                            item.time?.takeIf { it.isNotBlank() },
                                            item.rating?.takeIf { it.isNotBlank() }
                                                ?.let { "\u2605 $it" },
                                        ).joinToString(" \u00B7 ")
                                        if (meta.isNotBlank()) {
                                            Text(
                                                meta,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodySmall,
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
    }
}
