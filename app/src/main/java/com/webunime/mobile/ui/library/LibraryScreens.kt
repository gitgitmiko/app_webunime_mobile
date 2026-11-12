package com.webunime.mobile.ui.library

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.webunime.mobile.WebunimeApp
import com.webunime.mobile.data.library.EpisodeAlert
import com.webunime.mobile.data.library.FavoriteItem
import com.webunime.mobile.ui.account.AuthDialog
import com.webunime.mobile.ui.theme.WuBg
import com.webunime.mobile.ui.theme.WuStroke
import kotlinx.coroutines.launch

/** Daftar favorit akun (sama dengan web). */
@Composable
fun FavoritesScreen(
    onBack: (() -> Unit)?,
    onOpenTitle: (collection: String, slug: String, episode: Int) -> Unit,
) {
    val app = LocalContext.current.applicationContext as WebunimeApp
    val scope = rememberCoroutineScope()
    val user by app.webAuth.user.collectAsState()
    val items by app.favorites.items.collectAsState()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var askLogin by remember { mutableStateOf(false) }

    suspend fun load() {
        loading = true
        runCatching { app.favorites.refresh() }
            .onSuccess { error = null }
            .onFailure { error = it.message ?: "Gagal memuat favorit" }
        loading = false
    }

    LaunchedEffect(user?.id) { if (user != null) load() }

    if (askLogin) AuthDialog(onDismiss = { askLogin = false })

    LibraryScaffold(
        title = "Favorit Saya",
        onBack = onBack,
        action = if (user != null) {
            {
                IconButton(onClick = { scope.launch { load() } }, enabled = !loading) {
                    Icon(Icons.Default.Refresh, contentDescription = "Muat ulang")
                }
            }
        } else {
            null
        },
    ) {
        when {
            user == null -> SignInPrompt(
                "Masuk untuk menyimpan anime dan series favorit, lalu dapat notifikasi episode baru.",
            ) { askLogin = true }
            loading && items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            items.isEmpty() -> EmptyText(
                error ?: "Belum ada favorit. Tekan ♥ di halaman judul untuk menambahkan.",
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.key }) { fav ->
                    FavoriteRow(
                        fav = fav,
                        onClick = { onOpenTitle(fav.appCollection(), fav.slug, -1) },
                        onRemove = {
                            scope.launch { runCatching { app.favorites.remove(fav.collection, fav.slug) } }
                        },
                    )
                }
            }
        }
    }
}

/** Kotak notifikasi episode baru untuk akun yang sedang masuk. */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenTitle: (collection: String, slug: String, episode: Int) -> Unit,
) {
    val app = LocalContext.current.applicationContext as WebunimeApp
    val scope = rememberCoroutineScope()
    val user by app.webAuth.user.collectAsState()
    val inbox by app.episodeAlerts.inbox.collectAsState()
    var checking by remember { mutableStateOf(false) }
    var askLogin by remember { mutableStateOf(false) }

    LaunchedEffect(user?.id) { app.episodeAlerts.loadInbox() }

    if (askLogin) AuthDialog(onDismiss = { askLogin = false })

    LibraryScaffold(
        title = "Notifikasi",
        onBack = onBack,
        action = if (user != null) {
            {
                IconButton(
                    onClick = {
                        checking = true
                        scope.launch {
                            runCatching { app.episodeAlerts.check() }
                            checking = false
                        }
                    },
                    enabled = !checking,
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Cek episode baru")
                }
            }
        } else {
            null
        },
    ) {
        when {
            user == null -> SignInPrompt("Masuk untuk menerima notifikasi episode baru dari favorit.") {
                askLogin = true
            }
            inbox.isEmpty() -> EmptyText(
                if (checking) {
                    "Mengecek episode baru…"
                } else {
                    "Belum ada notifikasi. Episode baru dari anime dan series favorit akan muncul di sini."
                },
            )
            else -> Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = app.episodeAlerts::markAllRead) { Text("Tandai dibaca") }
                    TextButton(onClick = app.episodeAlerts::clearInbox) { Text("Hapus semua") }
                }
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(inbox, key = { it.id }) { alert ->
                        AlertRow(alert) {
                            app.episodeAlerts.markRead(alert.id)
                            onOpenTitle(alert.appCollection(), alert.slug, alert.episode)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryScaffold(
    title: String,
    onBack: (() -> Unit)?,
    action: (@Composable () -> Unit)?,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(WuBg),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                }
            } else {
                Spacer(Modifier.width(16.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            action?.invoke()
        }
        content()
    }
}

@Composable
private fun SignInPrompt(text: String, onSignIn: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) { Text("Masuk") }
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(24.dp),
    )
}

@Composable
private fun RowCard(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, WuStroke.copy(alpha = 0.5f), shape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun Thumb(url: String?) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .width(56.dp)
            .aspectRatio(2f / 3f)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

@Composable
private fun FavoriteRow(fav: FavoriteItem, onClick: () -> Unit, onRemove: () -> Unit) {
    RowCard(onClick) {
        Thumb(fav.thumbnail)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(fav.displayTitle(), style = MaterialTheme.typography.titleSmall, maxLines = 2)
            Text(
                collectionLabel(fav.collection) + if (fav.hasEpisodes()) " · notifikasi episode baru aktif" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Favorite, contentDescription = "Hapus dari favorit", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AlertRow(alert: EpisodeAlert, onClick: () -> Unit) {
    RowCard(onClick) {
        Thumb(alert.thumbnail)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                alert.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (alert.read) FontWeight.Normal else FontWeight.SemiBold,
                maxLines = 2,
            )
            Text(alert.message(), style = MaterialTheme.typography.bodyMedium)
            Text(
                DateUtils.getRelativeTimeSpanString(alert.at).toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!alert.read) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

private fun collectionLabel(collection: String): String = when (collection) {
    "anime" -> "Anime"
    "anime-movies" -> "Anime Movie"
    "series" -> "Series"
    "movies" -> "Film"
    "horror" -> "Horor"
    else -> collection.replaceFirstChar { it.uppercase() }
}
