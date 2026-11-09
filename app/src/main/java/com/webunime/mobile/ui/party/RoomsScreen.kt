package com.webunime.mobile.ui.party

import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.webunime.mobile.WebunimeApp
import com.webunime.mobile.data.party.RoomSnapshot
import com.webunime.mobile.ui.account.AuthDialog
import com.webunime.mobile.ui.player.PlayerActivity
import com.webunime.mobile.ui.theme.WuBg
import com.webunime.mobile.ui.theme.WuStroke
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Daftar ruang nonton bareng yang hostnya online + gabung dengan kode. */
@Composable
fun RoomsScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val context = LocalContext.current
    val app = context.applicationContext as WebunimeApp
    val scope = rememberCoroutineScope()
    val user by app.webAuth.user.collectAsState()
    var rooms by remember { mutableStateOf<List<RoomSnapshot>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var joining by remember { mutableStateOf(false) }
    var askLogin by remember { mutableStateOf(user == null) }

    fun openRoom(roomCode: String) {
        context.startActivity(
            Intent(context, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_ROOM_CODE, roomCode),
        )
    }

    suspend fun load() {
        loading = true
        runCatching { app.watchParty.rooms() }
            .onSuccess { rooms = it; error = null }
            .onFailure { error = it.message ?: "Gagal memuat ruang" }
        loading = false
    }

    fun join() {
        val clean = code.trim().uppercase()
        if (clean.length != 8 || joining) {
            error = "Kode ruang 8 karakter."
            return
        }
        joining = true
        scope.launch {
            runCatching { app.watchParty.room(clean) }
                .onSuccess { openRoom(it.code); code = "" }
                .onFailure { error = it.message ?: "Gagal bergabung." }
            joining = false
        }
    }

    LaunchedEffect(user) {
        if (user == null) return@LaunchedEffect
        while (true) {
            load()
            delay(REFRESH_MS)
        }
    }

    if (askLogin && user == null) {
        AuthDialog(
            onDismiss = { askLogin = false },
            lead = "Nonton bareng butuh akun WEBUNIME.",
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .background(WuBg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
            }
            Text(
                "Nonton Bareng",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (user != null) {
                IconButton(onClick = { scope.launch { load() } }, enabled = !loading) {
                    Icon(Icons.Default.Refresh, contentDescription = "Muat ulang")
                }
            }
        }

        if (user == null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Masuk dengan akun WEBUNIME untuk membuat atau bergabung ke ruang.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { askLogin = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Masuk")
                }
            }
            return@Column
        }

        Text(
            "Buka episode atau film, lalu tekan Nonton bareng di pemutar untuk jadi host. " +
                "Atau gabung ke ruang di bawah.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase().filter(Char::isLetterOrDigit).take(8) },
                label = { Text("Kode ruang") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { join() }),
                modifier = Modifier.weight(1f),
            )
            Button(onClick = ::join, enabled = !joining) { Text("Gabung") }
        }
        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        Box(Modifier.fillMaxSize()) {
            if (loading && rooms.isEmpty()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else if (rooms.isEmpty()) {
                Text(
                    "Belum ada ruang aktif.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(24.dp),
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(rooms, key = { it.code }) { room -> RoomRow(room) { openRoom(room.code) } }
            }
        }
    }
}

@Composable
private fun RoomRow(room: RoomSnapshot, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, WuStroke.copy(alpha = 0.5f), shape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val episode = room.media.episodeLabel?.takeIf { it.isNotBlank() }?.let { " · Ep $it" }.orEmpty()
        Text(
            (room.media.title ?: room.media.slug) + episode,
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            "Host: ${room.hostName ?: "?"} · ${room.viewers.size} penonton",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val REFRESH_MS = 10_000L
