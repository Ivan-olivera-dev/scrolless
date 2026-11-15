package com.scrolless.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * In-app version of the launcher mark: a half sun resting on the horizon with its
 * reflection on the water. Geometry mirrors res/drawable/ic_launcher_foreground.xml
 * (108-unit viewport), re-centred and scaled to fill the badge.
 */
@Composable
fun ScrollessLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    val palette = LocalPlayaPalette.current
    val shape = RoundedCornerShape(size * 0.28f)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(palette.surfaceColor)
            .border(1.dp, palette.borderColor, shape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Composition spans x 31..77 and y 44..77 in the 108-unit viewport.
            val unit = this.size.minDimension * 0.72f / 46f
            fun p(x: Float, y: Float) = Offset(
                this.size.width / 2f + (x - 54f) * unit,
                this.size.height / 2f + (y - 60.5f) * unit
            )

            val sunRadius = 14f * unit
            val sunCenter = p(54f, 58f)
            val sun = Path().apply {
                moveTo(sunCenter.x - sunRadius, sunCenter.y)
                arcTo(
                    rect = androidx.compose.ui.geometry.Rect(
                        center = sunCenter,
                        radius = sunRadius
                    ),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false
                )
                close()
            }
            drawPath(path = sun, color = palette.warning)

            drawLine(
                color = palette.textPrimary,
                start = p(31f, 58f),
                end = p(77f, 58f),
                strokeWidth = 2.4f * unit,
                cap = StrokeCap.Round
            )
            listOf(
                Triple(43f, 65f, 65.5f),
                Triple(47.5f, 60.5f, 71.5f),
                Triple(51.5f, 56.5f, 77f)
            ).forEach { (startX, endX, y) ->
                drawLine(
                    color = palette.success,
                    start = p(startX, y),
                    end = p(endX, y),
                    strokeWidth = 2.2f * unit,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
