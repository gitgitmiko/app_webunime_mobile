package com.webunime.mobile.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.webunime.mobile.BuildConfig
import com.webunime.mobile.WebunimeApp
import com.webunime.mobile.data.push.ContentKind
import com.webunime.mobile.ui.components.BrandLogo
import com.webunime.mobile.ui.components.SectionTitle
import com.webunime.mobile.ui.theme.Appear
import com.webunime.mobile.ui.theme.WuBg
import com.webunime.mobile.ui.theme.WuStroke
import com.webunime.mobile.ui.theme.WuSurface
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(
    contentPadding: PaddingValues = PaddingValues(),
    onCheckUpdate: () -> Unit = {},
    onOpenRooms: () -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
) {
    val app = LocalContext.current.applicationContext as WebunimeApp
    val scope = rememberCoroutineScope()
    val user by app.webAuth.user.collectAsState()
    var authDialog by remember { mutableStateOf<Boolean?>(null) }

    authDialog?.let { register ->
        AuthDialog(onDismiss = { authDialog = null }, initialRegister = register)
    }

    Box(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .background(
                Brush.verticalGradient(listOf(WuSurface.copy(alpha = 0.55f), WuBg, WuBg)),
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Appear {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BrandLogo(
                        height = 48.dp,
                        modifier = Modifier.fillMaxWidth(0.85f),
                    )
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "Akun, nonton bareng, dan pembaruan aplikasi",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Appear(delayMs = 40) {
                SettingsCard {
                    SectionTitle(
                        "Akun WEBUNIME",
                        modifier = Modifier.padding(horizontal = 0.dp, vertical = 0.dp),
                    )
                    val signedIn = user
                    if (signedIn == null) {
                        Text(
                            "Masuk dengan akun yang sama seperti di web untuk nonton bareng.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { authDialog = false },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium,
                            ) { Text("Masuk") }
                            OutlinedButton(
                                onClick = { authDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium,
                            ) { Text("Daftar") }
                        }
                    } else {
                        Text(signedIn.label(), style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(signedIn.username?.let { "@$it" }, signedIn.email)
                                .joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = { scope.launch { app.webAuth.logout() } },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                        ) { Text("Keluar") }
                    }
                }
            }

            if (user != null) {
                Appear(delayMs = 50) {
                    val favCount = app.favorites.items.collectAsState().value.size
                    val unread = app.episodeAlerts.inbox.collectAsState().value.count { !it.read }
                    SettingsCard {
                        SectionTitle(
                            "Favorit & Notifikasi",
                            modifier = Modifier.padding(horizontal = 0.dp, vertical = 0.dp),
                        )
                        Text(
                            "Episode baru dari anime dan series favorit akan muncul sebagai notifikasi.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = onOpenFavorites,
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium,
                            ) { Text("Favorit ($favCount)") }
                            OutlinedButton(
                                onClick = onOpenNotifications,
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium,
                            ) { Text(if (unread > 0) "Notifikasi ($unread)" else "Notifikasi") }
                        }
                    }
                }
            }

            Appear(delayMs = 55) {
                val pushOn by app.newContentPush.enabled.collectAsState()
                SettingsCard {
                    SectionTitle(
                        "Notifikasi Konten Baru",
                        modifier = Modifier.padding(horizontal = 0.dp, vertical = 0.dp),
                    )
                    Text(
                        "Kabari saat ada judul baru di katalog. Tidak perlu masuk akun.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ContentKind.entries.forEach { kind ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${kind.label} baru",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = pushOn[kind] ?: true,
                                onCheckedChange = { app.newContentPush.setEnabled(kind, it) },
                            )
                        }
                    }
                }
            }

            Appear(delayMs = 60) {
                SettingsCard {
                    SectionTitle(
                        "Nonton Bareng",
                        modifier = Modifier.padding(horizontal = 0.dp, vertical = 0.dp),
                    )
                    Text(
                        "Buka episode atau film lalu tekan Nonton bareng di pemutar untuk jadi host, " +
                            "atau gabung ke ruang yang sedang aktif.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = onOpenRooms,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                    ) { Text("Lihat ruang aktif") }
                }
            }

            Appear(delayMs = 80) {
                SettingsCard {
                    SectionTitle(
                        "Aplikasi",
                        modifier = Modifier.padding(horizontal = 0.dp, vertical = 0.dp),
                    )
                    Text(
                        "Versi ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = onCheckUpdate,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) { Text("Cek update") }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, WuStroke.copy(alpha = 0.5f), shape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}
