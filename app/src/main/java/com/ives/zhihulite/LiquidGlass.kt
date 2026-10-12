// Copyright (c) 2026 ives-cheung and contributors.
// Adapted from the same-author Weibo Lite glass UI; see ACKNOWLEDGEMENTS.md and LICENSE.
package com.ives.zhihulite

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.animation.core.*
import kotlin.math.roundToInt

internal val LocalBackdrop = staticCompositionLocalOf<GraphicsLayer?> { null }
internal val LocalReaderInsets = staticCompositionLocalOf { PaddingValues() }

/** Replays only the content layer, never the controls, into a GPU-blurred glass surface. */
@Composable internal fun GlassPanel(modifier: Modifier = Modifier, radius: Int = 28, frosted: Boolean = false, content: @Composable () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.red < 0.2f
    val shape = RoundedCornerShape(radius.dp)
    val backdrop = LocalBackdrop.current
    var position by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    var glassSize by remember { mutableStateOf(IntSize.Zero) }
    val blur = remember(density, glassSize, frosted) { glassEffect(glassSize, density.density, if (frosted) 9f else 3f) }
    Box(modifier.shadow(10.dp, shape, ambientColor = Blue.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.12f))
        .clip(shape).onSizeChanged { glassSize = it }.onGloballyPositioned { position = it.positionInRoot() }
        .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = if (dark) 0.48f else 0.9f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.55f))), shape)) {
        Canvas(Modifier.matchParentSize().graphicsLayer { renderEffect = blur }) {
            if (backdrop != null) translate(-position.x, -position.y) { drawLayer(backdrop) }
        }
        Box(Modifier.matchParentSize().background(Brush.linearGradient(if (frosted && dark) listOf(Color(0xB325374D), Color(0xA6101C2D), Color(0xB32D405B)) else if (frosted) listOf(Color(0xB3FFFFFF), Color(0xA6E3F0FF), Color(0xB3FFFFFF)) else if (dark) listOf(Color(0x7725374D), Color(0x66101C2D), Color(0x772D405B)) else listOf(Color(0x77FFFFFF), Color(0x66E3F0FF), Color(0x77FFFFFF)))))
        Canvas(Modifier.matchParentSize()) {
            drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = if (dark) 0.12f else 0.35f), Color.Transparent), center = Offset(size.width * 0.15f, 0f), radius = size.width * 0.65f))
        }
        content()
    }
}

/** A single spring-driven bubble travels between tabs; its leading edge moves first. */
@Composable internal fun BubbleNavigation(tab: Int, select: (Int) -> Unit) {
    val transition = updateTransition(tab, label = "navigationBubble")
    val left by transition.animateFloat(transitionSpec = {
        spring(dampingRatio = 0.72f, stiffness = if (targetState > initialState) 220f else 500f)
    }, label = "bubbleLeft") { it.toFloat() }
    val right by transition.animateFloat(transitionSpec = {
        spring(dampingRatio = 0.72f, stiffness = if (targetState > initialState) 500f else 220f)
    }, label = "bubbleRight") { it + 1f }
    BoxWithConstraints(Modifier.fillMaxWidth().height(66.dp).padding(5.dp).clip(RoundedCornerShape(29.dp))) {
        val slot = maxWidth / 4
        val density = LocalDensity.current
        Box(Modifier.offset { IntOffset(with(density) { (slot * left).toPx() }.roundToInt(), 0) }
            .width(slot * (right - left).coerceAtLeast(0.65f)).fillMaxHeight().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Blue.copy(alpha = 0.22f), Blue.copy(alpha = 0.10f))))
            .border(0.7.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(28.dp)))
        Row(Modifier.fillMaxSize()) {
            val labels = listOf("首页", "热榜", "关注", "我的")
            val icons = listOf(Icons.Default.Home, Icons.Default.Star, Icons.Default.Favorite, Icons.Default.Person)
            labels.forEachIndexed { i, label ->
                Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(28.dp)).selectable(selected = tab == i, role = Role.Tab, onClick = { select(i) }), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(icons[i], label, Modifier.size(23.dp), tint = if (tab == i) Blue else MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(3.dp))
                    Text(label, fontSize = 11.sp, color = if (tab == i) Blue else MaterialTheme.colorScheme.onSurface, fontWeight = if (tab == i) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

