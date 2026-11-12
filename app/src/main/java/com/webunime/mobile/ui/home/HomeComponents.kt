package com.webunime.mobile.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import com.webunime.mobile.WebunimeApp
import com.webunime.mobile.ui.components.PosterBadge
import com.webunime.mobile.ui.theme.WuAccent
import com.webunime.mobile.ui.theme.WuGold
import com.webunime.mobile.ui.theme.WuMuted
import com.webunime.mobile.ui.theme.WuStroke
import com.webunime.mobile.ui.theme.WuSurfaceHigh
import com.webunime.mobile.ui.theme.WuText
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue

/** Data minimal satu slide hero. */
data class HeroItem(
    val title: String,
    val thumbnail: String?,
    val rating: String?,
    val genres: List<String>,
    val tag: String,
    val onClick: () -> Unit,
)

@Composable
fun HomeTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onOpenRooms: () -> Unit,
    onOpenNotifications: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Ganti katalog", onBack)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(title, color = WuText, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                subtitle,
                color = WuMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CircleIconButton(Icons.Filled.Groups, "Nonton bareng", onOpenRooms)
        NotificationsAction(onOpenNotifications)
    }
}

@Composable
private fun NotificationsAction(onClick: () -> Unit) {
    val app = LocalContext.current.applicationContext as WebunimeApp
    val user by app.webAuth.user.collectAsState()
    if (user == null) return
    val unread = app.episodeAlerts.inbox.collectAsState().value.count { !it.read }
    Spacer(Modifier.width(8.dp))
    BadgedBox(badge = { if (unread > 0) Badge { Text(if (unread > 9) "9+" else "$unread") } }) {
        CircleIconButton(Icons.Filled.Notifications, "Notifikasi", onClick)
    }
}

@Composable
fun CircleIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(WuSurfaceHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = WuText, modifier = Modifier.size(20.dp))
    }
}

/** Carousel besar di atas beranda; geser otomatis tiap beberapa detik. */
@Composable
fun HeroCarousel(items: List<HeroItem>) {
    if (items.isEmpty()) return
    val pager = rememberPagerState { items.size }
    LaunchedEffect(pager, items.size) {
        while (true) {
            delay(5_000)
            if (!pager.isScrollInProgress) {
                pager.animateScrollToPage((pager.currentPage + 1) % items.size)
            }
        }
    }
    Column(Modifier.padding(top = 8.dp)) {
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = 36.dp),
            pageSpacing = 12.dp,
        ) { page ->
            val item = items[page]
            val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
            Box(
                Modifier
                    .graphicsLayer {
                        val s = lerp(0.9f, 1f, 1f - offset)
                        scaleX = s
                        scaleY = s
                        alpha = lerp(0.55f, 1f, 1f - offset)
                    }
                    .fillMaxWidth()
                    .aspectRatio(4f / 5f)
                    .clip(RoundedCornerShape(26.dp))
                    .background(WuSurfaceHigh)
                    .clickable(onClick = item.onClick),
            ) {
                AsyncImage(
                    model = item.thumbnail,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.62f)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.45f to Color.Black.copy(alpha = 0.6f),
                                1f to Color.Black.copy(alpha = 0.92f),
                            ),
                        ),
                )
                PosterBadge(
                    text = item.tag,
                    accent = true,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp),
                )
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (item.genres.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item.genres.take(3).forEach { g ->
                                Text(
                                    g,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.16f))
                                        .padding(horizontal = 9.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
                    Text(
                        item.title,
                        color = Color.White,
                        fontSize = 24.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier
                                .clip(CircleShape)
                                .background(Color.White)
                                .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Tonton", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        item.rating?.takeIf { it.isNotBlank() }?.let { r ->
                            Spacer(Modifier.width(12.dp))
                            Text("★", color = WuGold, fontSize = 15.sp)
                            Spacer(Modifier.width(4.dp))
                            Text(r, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(items.size) { i ->
                val active = i == pager.currentPage
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .animateContentSize()
                        .height(6.dp)
                        .width(if (active) 20.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (active) WuAccent else WuStroke),
                )
            }
        }
    }
}

@Composable
fun HomeSection(title: String, subtitle: String? = null, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = WuText, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, color = WuMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (action != null) {
            TextButton(onClick = onAction) { Text(action, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** Baris "Top" bergaya peringkat: angka besar bergaris di belakang poster. */
@Composable
fun <T> RankedRow(
    items: List<T>,
    titleOf: (T) -> String,
    thumbOf: (T) -> String?,
    badgeOf: (T) -> String?,
    onClick: (T) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(items.take(10)) { index, item ->
            Box(
                Modifier
                    .width(if (index >= 9) 176.dp else 152.dp)
                    .height(184.dp)
                    .clickable { onClick(item) },
            ) {
                Text(
                    "${index + 1}",
                    style = TextStyle(
                        fontSize = 112.sp,
                        fontWeight = FontWeight.Black,
                        drawStyle = Stroke(width = 5f),
                        letterSpacing = (-8).sp,
                    ),
                    color = WuMuted.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(y = 14.dp),
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .width(112.dp)
                        .height(168.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(WuSurfaceHigh),
                ) {
                    AsyncImage(
                        model = thumbOf(item),
                        contentDescription = titleOf(item),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    badgeOf(item)?.let {
                        PosterBadge(it, accent = false, modifier = Modifier.padding(6.dp))
                    }
                }
            }
        }
    }
}

/** Kerangka beranda selama data dimuat. */
@Composable
fun HomeSkeleton() {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    val block = WuSurfaceHigh.copy(alpha = alpha)
    Column(Modifier.fillMaxSize().padding(top = 64.dp)) {
        Box(
            Modifier
                .padding(horizontal = 36.dp)
                .fillMaxWidth()
                .aspectRatio(4f / 5f)
                .clip(RoundedCornerShape(26.dp))
                .background(block),
        )
        repeat(2) {
            Box(
                Modifier
                    .padding(start = 16.dp, top = 26.dp, bottom = 12.dp)
                    .size(140.dp, 18.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(block),
            )
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) {
                    Box(
                        Modifier
                            .size(124.dp, 186.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(block),
                    )
                }
            }
        }
    }
}
