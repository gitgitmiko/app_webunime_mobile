package com.webunime.mobile.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.webunime.mobile.WebunimeApp
import com.webunime.mobile.ui.components.BrandLogo
import com.webunime.mobile.ui.theme.Appear
import com.webunime.mobile.ui.theme.WuAccent
import com.webunime.mobile.ui.theme.WuBg
import com.webunime.mobile.ui.theme.WuMuted
import com.webunime.mobile.ui.theme.WuStroke
import com.webunime.mobile.ui.theme.WuSurfaceHigh
import com.webunime.mobile.ui.theme.WuText
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.Calendar
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.style.TextOverflow
import com.webunime.mobile.data.auth.WebUser
import com.webunime.mobile.ui.account.AuthDialog
import kotlinx.coroutines.launch

private val PosterW = 92.dp
private val PosterH = 136.dp
private val PosterGap = 10.dp

@Composable
fun CatalogChooserScreen(
    onPick: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val app = LocalContext.current.applicationContext as WebunimeApp
    val user by app.webAuth.user.collectAsState()
    var animePosters by remember { mutableStateOf<List<String>>(emptyList()) }
    var filmPosters by remember { mutableStateOf<List<String>>(emptyList()) }

    // Poster asli dari katalog; layar tetap rapi (gradien saja) bila gagal dimuat.
    LaunchedEffect(Unit) {
        coroutineScope {
            val anime = async { runCatching { app.catalogApi.cardsPage("anime", 1).items }.getOrDefault(emptyList()) }
            val movies = async { runCatching { app.catalogApi.cardsPage("movies", 1).items }.getOrDefault(emptyList()) }
            animePosters = anime.await().mapNotNull { it.thumbnail?.takeIf(String::isNotBlank) }.take(12)
            filmPosters = movies.await().mapNotNull { it.thumbnail?.takeIf(String::isNotBlank) }.take(12)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .background(WuBg),
    ) {
        PosterWall(animePosters, filmPosters)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandLogo(height = 34.dp, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.weight(1f))
                AccountChip(user)
            }
            Spacer(Modifier.height(120.dp))

            Appear {
                Column {
                    Text(
                        greeting() + (user?.label()?.let { ", $it" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = WuMuted,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Mau nonton apa\nhari ini?",
                        color = WuText,
                        fontSize = 32.sp,
                        lineHeight = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Appear(delayMs = 80) {
                ModeCard(
                    label = "ANIME",
                    title = "Anime",
                    subtitle = "Episode terbaru, movie, dan jadwal rilis mingguan",
                    icon = Icons.Filled.AutoAwesome,
                    accent = listOf(Color(0xFFE11D48), Color(0xFF7C3AED)),
                    posters = animePosters,
                    onClick = { onPick("anime") },
                )
            }
            Spacer(Modifier.height(14.dp))
            Appear(delayMs = 160) {
                ModeCard(
                    label = "FILM & SERIES",
                    title = "Film",
                    subtitle = "Film terbaru, horor, Marvel, dan series TV",
                    icon = Icons.Filled.Movie,
                    accent = listOf(Color(0xFFF59E0B), Color(0xFF2563EB)),
                    posters = filmPosters,
                    onClick = { onPick("film") },
                )
            }

            Spacer(Modifier.height(22.dp))
            Appear(delayMs = 240) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FeatureTile(Icons.Filled.NotificationsActive, "Notifikasi", "Judul baru", Modifier.weight(1f))
                    FeatureTile(Icons.Filled.Groups, "Nonton bareng", "Sinkron teman", Modifier.weight(1f))
                    FeatureTile(Icons.Filled.Favorite, "Favorit", "Sinkron web", Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 4..10 -> "Selamat pagi"
    in 11..14 -> "Selamat siang"
    in 15..17 -> "Selamat sore"
    else -> "Selamat malam"
}

/** Dua baris poster yang bergeser pelan berlawanan arah, memudar ke latar. */
@Composable
private fun BoxScope.PosterWall(top: List<String>, bottom: List<String>) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(PosterH * 2 + PosterGap + 80.dp)
            .align(Alignment.TopStart)
            .graphicsLayer { alpha = 0.5f },
    ) {
        Column(
            Modifier.padding(top = 56.dp),
            verticalArrangement = Arrangement.spacedBy(PosterGap),
        ) {
            MarqueeRow(top, reverse = false)
            MarqueeRow(bottom, reverse = true)
        }
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to WuBg,
                        0.22f to WuBg.copy(alpha = 0.55f),
                        0.4f to WuBg.copy(alpha = 0.25f),
                        0.7f to WuBg.copy(alpha = 0.8f),
                        1f to WuBg,
                    ),
                ),
        )
    }
}

@Composable
private fun MarqueeRow(urls: List<String>, reverse: Boolean) {
    if (urls.isEmpty()) {
        Spacer(Modifier.height(PosterH))
        return
    }
    val step = PosterW + PosterGap
    val loopWidth = with(LocalDensity.current) { (step * urls.size).toPx() }
    val transition = rememberInfiniteTransition(label = "marquee")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(urls.size * 4_000, easing = LinearEasing), RepeatMode.Restart),
        label = "marqueeProgress",
    )
    Row(
        Modifier
            .wrapContentWidth(align = Alignment.Start, unbounded = true)
            .graphicsLayer {
                val shift = loopWidth * progress
                translationX = if (reverse) shift - loopWidth else -shift
            },
        horizontalArrangement = Arrangement.spacedBy(PosterGap),
    ) {
        // Daftar diulang dua kali supaya putaran tidak terlihat sambungannya.
        (urls + urls).forEach { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(PosterW, PosterH)
                    .clip(RoundedCornerShape(12.dp))
                    .background(WuSurfaceHigh),
            )
        }
    }
}

@Composable
private fun ModeCard(
    label: String,
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: List<Color>,
    posters: List<String>,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "cardScale")
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(196.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(Brush.linearGradient(accent.map { it.copy(alpha = 0.9f) }))
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        FannedPosters(posters.take(3), Modifier.align(Alignment.CenterEnd))
        // Gelapkan sisi kiri supaya teks selalu terbaca di atas poster.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.55f),
                        0.55f to Color.Black.copy(alpha = 0.15f),
                        1f to Color.Transparent,
                    ),
                ),
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }
            Column {
                Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(0.5f),
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Jelajahi", color = accent.first(), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = accent.first(),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/** Tiga poster bertumpuk miring di sisi kanan kartu. */
@Composable
private fun FannedPosters(urls: List<String>, modifier: Modifier = Modifier) {
    if (urls.isEmpty()) return
    val angles = listOf(-14f, 0f, 14f)
    val offsets = listOf((-62).dp, (-34).dp, (-6).dp)
    Box(modifier.padding(end = 18.dp).width(190.dp).height(196.dp)) {
        urls.forEachIndexed { i, url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = offsets[i] + 18.dp * i, y = 4.dp)
                    .graphicsLayer {
                        rotationZ = angles[i]
                        shadowElevation = 16f
                        shape = RoundedCornerShape(12.dp)
                        clip = true
                    }
                    .size(PosterW.scaled(0.95f), PosterH.scaled(0.95f))
                    .background(Color.Black.copy(alpha = 0.3f)),
            )
        }
    }
}

private fun Dp.scaled(f: Float): Dp = this * f

@Composable
private fun FeatureTile(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .background(WuSurfaceHigh.copy(alpha = 0.7f))
            .border(1.dp, WuStroke.copy(alpha = 0.6f), shape)
            .padding(12.dp),
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(WuAccent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = WuAccent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(title, color = WuText, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        Text(subtitle, color = WuMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/** Pojok kanan atas: tombol Masuk untuk tamu, atau info akun yang sedang masuk. */
@Composable
private fun AccountChip(user: WebUser?) {
    val app = LocalContext.current.applicationContext as WebunimeApp
    val scope = rememberCoroutineScope()
    var authDialog by remember { mutableStateOf<Boolean?>(null) }
    var menu by remember { mutableStateOf(false) }
    authDialog?.let { register -> AuthDialog(onDismiss = { authDialog = null }, initialRegister = register) }

    if (user == null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Tamu",
                color = WuMuted,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .border(1.dp, WuStroke, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(WuAccent)
                    .clickable { authDialog = false }
                    .padding(start = 10.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Person, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Masuk", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    Box {
        Row(
            Modifier
                .clip(CircleShape)
                .background(WuSurfaceHigh)
                .border(1.dp, WuStroke, CircleShape)
                .clickable { menu = true }
                .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFFE11D48), Color(0xFF7C3AED)))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    user.label().take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    user.label(),
                    color = WuText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp),
                )
                user.username?.let {
                    Text(
                        "@$it",
                        color = WuMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 120.dp),
                    )
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            user.email?.let {
                DropdownMenuItem(text = { Text(it, color = WuMuted, fontSize = 12.sp) }, onClick = {}, enabled = false)
            }
            DropdownMenuItem(
                text = { Text("Keluar") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
                onClick = {
                    menu = false
                    scope.launch { app.webAuth.logout() }
                },
            )
        }
    }
}
