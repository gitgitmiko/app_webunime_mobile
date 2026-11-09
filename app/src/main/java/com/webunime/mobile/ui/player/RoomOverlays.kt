package com.webunime.mobile.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.webunime.mobile.data.party.RoomRole
import com.webunime.mobile.data.party.RoomState
import com.webunime.mobile.ui.theme.WuSurface

/** Status ruang di bawah pemutar: kode, peran, jumlah penonton, aksi. */
@Composable
fun RoomBar(
    state: RoomState,
    muted: Boolean,
    compact: Boolean,
    hostHint: Boolean,
    onShare: () -> Unit,
    onMute: () -> Unit,
    onLeave: () -> Unit,
) {
    val isHost = state.role == RoomRole.Host
    Column(
        Modifier
            .fillMaxWidth()
            .background(WuSurface.copy(alpha = 0.96f))
            .padding(horizontal = 12.dp, vertical = if (compact) 0.dp else 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (isHost) "Host · Ruang ${state.code}" else "Host: ${state.hostName ?: "?"}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (!compact) {
                    Text(
                        "${state.viewers.size} penonton" +
                            state.viewers.takeIf { it.isNotEmpty() }
                                ?.joinToString(prefix = ": ", limit = 4) { it }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share, "Bagikan ruang", tint = MaterialTheme.colorScheme.onSurface)
            }
            if (!isHost) {
                IconButton(onClick = onMute) {
                    Icon(
                        if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        if (muted) "Nyalakan suara" else "Bisukan",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            IconButton(onClick = onLeave) {
                Icon(
                    Icons.AutoMirrored.Filled.ExitToApp,
                    if (isHost) "Akhiri nonton bareng" else "Keluar dari ruang",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (hostHint && !compact) {
            Text(
                "Video belum terdeteksi. Tekan play di pemutar, atau ganti server.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

/**
 * Lapisan di atas pemutar penonton: sentuhan ditahan supaya video mengikuti host.
 * Sebelum video terdeteksi tetap tembus (server tertentu minta klik play di pemutar).
 */
@Composable
fun ViewerLock(
    state: RoomState,
    frameReady: Boolean,
    needsTap: Boolean,
    onTap: () -> Unit,
) {
    val message = when {
        !frameReady -> "Memuat video… tekan play di pemutar bila diminta"
        needsTap -> "Ketuk untuk menyalakan suara"
        !state.hostOnline -> "Host terputus, menunggu host kembali…"
        !state.playback.playing -> "Dijeda oleh host"
        else -> null
    }
    Box(
        Modifier
            .fillMaxSize()
            .then(
                if (frameReady) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTap,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        message?.let {
            Text(
                it,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
