package com.webunime.mobile.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.webunime.mobile.ui.components.BrandHeader
import com.webunime.mobile.ui.theme.WuBg
import com.webunime.mobile.ui.theme.WuStroke
import com.webunime.mobile.ui.theme.WuSurface
import com.webunime.mobile.ui.theme.WuSurfaceHigh

@Composable
fun CatalogChooserScreen(
    onPick: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .background(
                Brush.verticalGradient(listOf(WuSurface.copy(alpha = 0.9f), WuBg, WuBg)),
            )
            .padding(horizontal = 16.dp),
    ) {
        BrandHeader(subtitle = "Pilih Anime atau Film")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CatalogPickCard(
                title = "Anime",
                subtitle = "Episode, movie, jadwal",
                modifier = Modifier.weight(1f),
                onClick = { onPick("anime") },
            )
            CatalogPickCard(
                title = "Film",
                subtitle = "Film, horor, series",
                modifier = Modifier.weight(1f),
                onClick = { onPick("film") },
            )
        }
    }
}

@Composable
private fun CatalogPickCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .height(168.dp)
            .clip(shape)
            .background(WuSurfaceHigh)
            .border(1.dp, WuStroke, shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
