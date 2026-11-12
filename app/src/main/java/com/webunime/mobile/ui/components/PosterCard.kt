package com.webunime.mobile.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.webunime.mobile.R
import com.webunime.mobile.ui.theme.WuMotionShort
import com.webunime.mobile.ui.theme.WuAccent
import com.webunime.mobile.ui.theme.WuGold
import com.webunime.mobile.ui.theme.WuStroke
import com.webunime.mobile.ui.theme.WuSurfaceHigh
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(width = 3.dp, height = 18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
fun MetaChip(text: String, accent: Boolean = false) {
    val bg = if (accent) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = if (accent) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(bg)
            .border(1.dp, WuStroke.copy(alpha = 0.45f), MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
fun BrandLogo(
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
) {
    Image(
        painter = painterResource(R.drawable.logo_transparan),
        contentDescription = stringResource(R.string.app_name),
        contentScale = ContentScale.Fit,
        alignment = Alignment.CenterStart,
        modifier = modifier.height(height),
    )
}

@Composable
fun BrandHeader(
    modifier: Modifier = Modifier,
    subtitle: String = "Katalog anime terbaru & jadwal rilis",
) {
    Column(modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Image(
            painter = painterResource(R.drawable.logo_transparan),
            contentDescription = stringResource(R.string.app_name),
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(720f / 177f),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun PosterCard(
    title: String,
    thumbnail: String?,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    badgeAccent: Boolean = false,
    wide: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(WuMotionShort),
        label = "posterScale",
    )
    val shape = RoundedCornerShape(14.dp)

    Column(
        modifier = modifier
            .width(if (wide) 236.dp else 124.dp)
            .scale(scale)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(if (wide) 16f / 9f else 2f / 3f)
                .clip(shape)
                .background(WuSurfaceHigh)
                .border(1.dp, Color.White.copy(alpha = 0.06f), shape),
        ) {
            AsyncImage(
                model = thumbnail,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                        ),
                    ),
            )
            if (!badge.isNullOrBlank()) {
                PosterBadge(
                    text = badge,
                    accent = badgeAccent,
                    modifier = Modifier
                        .align(if (badgeAccent) Alignment.BottomStart else Alignment.TopStart)
                        .padding(8.dp),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = if (wide) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Pil kecil di atas poster: rating (gelap, bintang emas) atau episode (aksen). */
@Composable
fun PosterBadge(text: String, accent: Boolean, modifier: Modifier = Modifier) {
    val star = text.startsWith("\u2605")
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (accent) WuAccent else Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (star) {
            Text("\u2605", color = WuGold, fontSize = 11.sp)
            Spacer(Modifier.width(3.dp))
        }
        Text(
            text = if (star) text.removePrefix("\u2605").trim() else text,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
fun <T> HorizontalPosterRow(
    items: List<T>,
    titleOf: (T) -> String,
    thumbOf: (T) -> String?,
    subtitleOf: (T) -> String? = { null },
    onClick: (T) -> Unit,
    loadingMore: Boolean = false,
    onReachEnd: (() -> Unit)? = null,
    badgeOf: (T) -> String? = { null },
    badgeAccent: Boolean = false,
    wide: Boolean = false,
) {
    val listState = rememberLazyListState()
    if (onReachEnd != null) {
        val reachEnd by rememberUpdatedState(onReachEnd)
        LaunchedEffect(listState) {
            snapshotFlow {
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@snapshotFlow false
                last >= info.totalItemsCount - 1 - REACH_END_PREFETCH
            }
                .distinctUntilChanged()
                .filter { it }
                .collect { reachEnd() }
        }
    }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(
            items = items,
            key = { index, item -> "${index}|${titleOf(item)}|${thumbOf(item).orEmpty()}" },
        ) { _, item ->
            PosterCard(
                title = titleOf(item),
                thumbnail = thumbOf(item),
                subtitle = subtitleOf(item),
                onClick = { onClick(item) },
                badge = badgeOf(item),
                badgeAccent = badgeAccent,
                wide = wide,
            )
        }
        if (loadingMore) {
            item(key = "loading-more") {
                Box(
                    Modifier
                        .width(64.dp)
                        .height(if (wide) 132.dp else 186.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** Mulai muat halaman berikutnya saat tinggal sekian kartu sebelum mentok. */
private const val REACH_END_PREFETCH = 2
