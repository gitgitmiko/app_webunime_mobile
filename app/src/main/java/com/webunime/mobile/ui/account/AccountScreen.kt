package com.webunime.mobile.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.webunime.mobile.BuildConfig
import com.webunime.mobile.ui.components.BrandLogo
import com.webunime.mobile.ui.components.SectionTitle
import com.webunime.mobile.ui.theme.Appear
import com.webunime.mobile.ui.theme.WuBg
import com.webunime.mobile.ui.theme.WuStroke
import com.webunime.mobile.ui.theme.WuSurface

@Composable
fun AccountScreen(
    contentPadding: PaddingValues = PaddingValues(),
    onCheckUpdate: () -> Unit = {},
) {
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
                        "Kelola pembaruan aplikasi",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Appear(delayMs = 80) {
                val shape = MaterialTheme.shapes.medium
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .border(1.dp, WuStroke.copy(alpha = 0.5f), shape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
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
