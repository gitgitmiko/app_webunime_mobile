package com.webunime.mobile.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

val WuMotionShort = 280
val WuMotionMedium = 420

fun wuFadeSlideIn(delayMs: Int = 0) = fadeIn(
    animationSpec = tween(WuMotionMedium, delayMillis = delayMs, easing = FastOutSlowInEasing),
) + slideInVertically(
    animationSpec = tween(WuMotionMedium, delayMillis = delayMs, easing = FastOutSlowInEasing),
    initialOffsetY = { it / 12 },
)

@Composable
fun Appear(
    delayMs: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (delayMs > 0) delay(delayMs.toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = wuFadeSlideIn(),
        exit = fadeOut(tween(WuMotionShort)),
        modifier = modifier,
        content = content,
    )
}

@Composable
fun ScreenFade(content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(WuMotionMedium, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(WuMotionShort)),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize()) { content() }
    }
}
