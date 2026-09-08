package com.example.voiceapp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voiceapp.BoundingBoxBounds

/**
 * Accessible Overlay Box component placing high-contrast Canvas bounding box and label badge over video stream.
 */
@Composable
fun BoundingBoxOverlayContainer(
    isVisible: Boolean,
    bounds: BoundingBoxBounds,
    label: String,
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFF00FF66)
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val leftPx = bounds.left * size.width
                val topPx = bounds.top * size.height
                val rightPx = bounds.right * size.width
                val bottomPx = bounds.bottom * size.height

                val rectW = rightPx - leftPx
                val rectH = bottomPx - topPx

                // Inner fill
                drawRoundRect(
                    color = accentColor.copy(alpha = 0.15f),
                    topLeft = Offset(leftPx, topPx),
                    size = Size(rectW, rectH),
                    cornerRadius = CornerRadius(16f, 16f)
                )

                // Outer border rect
                drawRoundRect(
                    color = accentColor,
                    topLeft = Offset(leftPx, topPx),
                    size = Size(rectW, rectH),
                    cornerRadius = CornerRadius(16f, 16f),
                    style = Stroke(width = 6f)
                )

                // Corner accents for high-contrast accessibility
                val cornerLen = 32f
                val strokeW = 10f

                // Top-Left
                drawLine(accentColor, Offset(leftPx, topPx), Offset(leftPx + cornerLen, topPx), strokeWidth = strokeW)
                drawLine(accentColor, Offset(leftPx, topPx), Offset(leftPx, topPx + cornerLen), strokeWidth = strokeW)

                // Top-Right
                drawLine(accentColor, Offset(rightPx, topPx), Offset(rightPx - cornerLen, topPx), strokeWidth = strokeW)
                drawLine(accentColor, Offset(rightPx, topPx), Offset(rightPx, topPx + cornerLen), strokeWidth = strokeW)

                // Bottom-Left
                drawLine(accentColor, Offset(leftPx, bottomPx), Offset(leftPx + cornerLen, bottomPx), strokeWidth = strokeW)
                drawLine(accentColor, Offset(leftPx, bottomPx), Offset(leftPx, bottomPx - cornerLen), strokeWidth = strokeW)

                // Bottom-Right
                drawLine(accentColor, Offset(rightPx, bottomPx), Offset(rightPx - cornerLen, bottomPx), strokeWidth = strokeW)
                drawLine(accentColor, Offset(rightPx, bottomPx), Offset(rightPx, bottomPx - cornerLen), strokeWidth = strokeW)
            }

            // Position Label Badge inside screen bounds
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 24.dp, top = 24.dp)
            ) {
                Text(
                    text = label,
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(accentColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}
