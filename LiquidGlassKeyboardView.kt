package com.liquidboard.ime.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liquidboard.ime.ai.SentimentSnapshot
import com.liquidboard.ime.theme.ThemeConfig

private const val LIQUID_GLASS_AGSL = """
    uniform shader inputShader;
    uniform float2 resolution;
    uniform float rimIntensity;
    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / resolution;
        float2 centerOffset = (uv - 0.5) * 0.018;
        half4 r = inputShader.eval(fragCoord + centerOffset * resolution * 1.15);
        half4 g = inputShader.eval(fragCoord + centerOffset * resolution);
        half4 b = inputShader.eval(fragCoord + centerOffset * resolution * 0.85);
        half4 refracted = half4(r.r, g.g, b.b, g.a);
        float topRim = smoothstep(0.14, 0.0, uv.y) * rimIntensity * 0.42;
        float bottomCaustic = smoothstep(0.82, 1.0, uv.y) * rimIntensity * 0.16;
        return refracted + half4(topRim + bottomCaustic, topRim + bottomCaustic, topRim + bottomCaustic, 0.0);
    }
"""

@Composable
fun LiquidGlassKeyboardView(
    config: ThemeConfig,
    suggestions: List<String>,
    sentiment: SentimentSnapshot,
    translateEnabled: Boolean,
    liveTranslation: String,
    onKeyTap: (String) -> Unit,
    onBackspace: () -> Unit,
    onDeleteWord: () -> Unit,
    onCursorScrub: (Int) -> Unit,
    onGestureSwipe: (List<Offset>) -> Unit,
    onSuggestionTap: (String) -> Unit,
    onToggleTranslate: () -> Unit
) {
    val swipePoints = remember { mutableStateListOf<Offset>() }

    val glassModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && config.blurRadiusDp > 0f) {
        val shader = remember { RuntimeShader(LIQUID_GLASS_AGSL) }
        Modifier.graphicsLayer {
            shader.setFloatUniform("resolution", size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f))
            shader.setFloatUniform("rimIntensity", config.specularRimIntensity)
            renderEffect = RenderEffect.createChainEffect(
                RenderEffect.createRuntimeShaderEffect(shader, "inputShader"),
                RenderEffect.createBlurEffect(config.blurRadiusDp, config.blurRadiusDp, Shader.TileMode.CLAMP)
            ).asComposeRenderEffect()
        }
    } else {
        Modifier
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(glassModifier)
            .background(Color(0xFF0F172A).copy(alpha = config.glassOpacity))
            .padding(horizontal = 6.dp, vertical = 8.dp)
    ) {
        // AI Suggestion, Sentiment & Translate Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                suggestions.take(3).forEach { word ->
                    Text(
                        text = word,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.14f))
                            .clickable { onSuggestionTap(word) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Text(
                text = if (translateEnabled) "Translate On" else sentiment.label,
                color = config.primaryTint,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleTranslate() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        if (translateEnabled && liveTranslation.isNotBlank()) {
            Text(
                text = liveTranslation,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .background(config.primaryTint.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            )
        }

        // QWERTY Key Grid + Gesture Trail Overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(config.glideTypingEnabled) {
                    if (!config.glideTypingEnabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offset -> swipePoints.clear(); swipePoints.add(offset) },
                        onDrag = { change, _ -> swipePoints.add(change.position) },
                        onDragEnd = {
                            if (swipePoints.size > 4) {
                                val norm = swipePoints.map {
                                    Offset(it.x / size.width.coerceAtLeast(1), it.y / size.height.coerceAtLeast(1))
                                }
                                onGestureSwipe(norm)
                            }
                            swipePoints.clear()
                        }
                    )
                }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val rows = listOf(
                    listOf("q","w","e","r","t","y","u","i","o","p"),
                    listOf("a","s","d","f","g","h","j","k","l"),
                    listOf("z","x","c","v","b","n","m")
                )
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)
                    ) {
                        row.forEach { keyChar ->
                            GlassKeycap(
                                label = keyChar,
                                config = config,
                                modifier = Modifier.weight(1f),
                                onClick = { onKeyTap(keyChar) }
                            )
                        }
                    }
                }
            }

            // Tapered Liquid Glass Glide Trail
            Canvas(modifier = Modifier.matchParentSize()) {
                if (swipePoints.size > 1) {
                    val path = Path().apply {
                        moveTo(swipePoints.first().x, swipePoints.first().y)
                        swipePoints.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(
                        path = path,
                        brush = Brush.linearGradient(listOf(config.primaryTint, config.secondaryTint)),
                        style = Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassKeycap(
    label: String,
    config: ThemeConfig,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(config.keyCornerRadiusDp.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(46.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = config.glassOpacity * 0.6f))
            .then(
                if (config.showKeyBorders) {
                    Modifier.border(1.dp, Color.White.copy(alpha = config.specularRimIntensity * 0.45f), shape)
                } else Modifier
            )
            .clickable { onClick() }
    ) {
        Text(text = label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}
