// Copyright (c) 2026 ives-cheung and contributors.
// Adapted from the same-author Weibo Lite glass UI; see ACKNOWLEDGEMENTS.md and LICENSE.
package com.ives.zhihulite

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.unit.IntSize

/** Hardware effects only; no screen capture, bitmap allocation, or CPU blur in the frame loop. */
internal fun glassEffect(size: IntSize, density: Float, blurDp: Float = 3f): androidx.compose.ui.graphics.RenderEffect? {
    if (Build.VERSION.SDK_INT < 31) return null
    val blur = RenderEffect.createBlurEffect(blurDp * density, blurDp * density, Shader.TileMode.CLAMP)
    return if (Build.VERSION.SDK_INT >= 33 && size.width > 0 && size.height > 0) {
        lensEffect(size, density, blur).asComposeRenderEffect()
    } else blur.asComposeRenderEffect()
}

@RequiresApi(33)
private fun lensEffect(size: IntSize, density: Float, blur: RenderEffect): RenderEffect {
    val lens = RuntimeShader("""
        uniform shader content;
        uniform float2 resolution;
        uniform float rimWidth;
        uniform float bend;
        half4 main(float2 p) {
            float2 delta = p - resolution * 0.5;
            float edge = min(min(p.x, resolution.x - p.x), min(p.y, resolution.y - p.y));
            float rim = 1.0 - smoothstep(0.0, rimWidth, edge);
            float2 normal = delta / max(length(delta), 1.0);
            float2 samplePoint = clamp(p - normal * rim * rim * bend, float2(0.0), resolution);
            return content.eval(samplePoint);
        }
    """.trimIndent())
    lens.setFloatUniform("resolution", size.width.toFloat(), size.height.toFloat())
    lens.setFloatUniform("rimWidth", 18f * density)
    lens.setFloatUniform("bend", 3.5f * density)
    return RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(lens, "content"), blur)
}
