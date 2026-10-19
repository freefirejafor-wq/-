package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NavyCardBorder
import com.example.ui.theme.NavySurface

@Composable
fun AppLogoContainer(
    outerSize: Dp = 150.dp,
    innerSize: Dp = 100.dp,
    showContainerBorder: Boolean = true,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(outerSize)
            .then(
                if (showContainerBorder) {
                    Modifier
                        .clip(RoundedCornerShape(32.dp))
                        .background(NavySurface)
                        .border(1.dp, NavyCardBorder, RoundedCornerShape(32.dp))
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        AppLogoCard(size = innerSize)
    }
}

@Composable
fun AppLogoCard(
    size: Dp = 100.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.26f))
            .background(Color.White)
            .padding(size * 0.08f),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Striker silhouette + Ball
            Canvas(
                modifier = Modifier
                    .size(size * 0.52f)
            ) {
                drawStrikerAndBall(this)
            }

            Spacer(modifier = Modifier.height(size * 0.02f))

            // "F [flaming ball] [flaming ball] TBALL" logo text
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "F",
                    fontWeight = FontWeight.Black,
                    fontSize = (size.value * 0.16f).sp,
                    fontFamily = FontFamily.SansSerif,
                    color = Color.Black
                )

                // Flaming balls
                Canvas(
                    modifier = Modifier
                        .height((size.value * 0.18f).dp)
                        .width((size.value * 0.28f).dp)
                ) {
                    drawFlamingBalls(this)
                }

                Text(
                    text = "TBALL",
                    fontWeight = FontWeight.Black,
                    fontSize = (size.value * 0.16f).sp,
                    fontFamily = FontFamily.SansSerif,
                    color = Color.Black
                )
            }
        }
    }
}

private fun drawStrikerAndBall(scope: DrawScope) {
    val w = scope.size.width
    val h = scope.size.height

    // Head
    scope.drawCircle(
        color = Color.Black,
        radius = w * 0.11f,
        center = Offset(w * 0.58f, h * 0.16f)
    )

    // Body + Arms + Legs in dynamic kicking pose
    val bodyPath = Path().apply {
        // Torso
        moveTo(w * 0.52f, h * 0.28f)
        lineTo(w * 0.65f, h * 0.28f)
        // Right arm extended forward
        lineTo(w * 0.88f, h * 0.14f)
        lineTo(w * 0.84f, h * 0.19f)
        lineTo(w * 0.65f, h * 0.36f)
        // Torso down
        lineTo(w * 0.62f, h * 0.54f)
        // Kicking right leg forward-up
        lineTo(w * 0.70f, h * 0.58f)
        lineTo(w * 0.66f, h * 0.68f)
        lineTo(w * 0.55f, h * 0.58f)
        // Left supporting leg bent down-back
        lineTo(w * 0.49f, h * 0.72f)
        lineTo(w * 0.42f, h * 0.70f)
        lineTo(w * 0.50f, h * 0.52f)
        // Left arm back
        lineTo(w * 0.36f, h * 0.46f)
        lineTo(w * 0.34f, h * 0.40f)
        lineTo(w * 0.48f, h * 0.36f)
        close()
    }
    scope.drawPath(bodyPath, color = Color.Black)

    // Ball near the foot
    val ballCenter = Offset(w * 0.46f, h * 0.66f)
    scope.drawCircle(
        color = Color.Black,
        radius = w * 0.085f,
        center = ballCenter
    )
}

private fun drawFlamingBalls(scope: DrawScope) {
    val w = scope.size.width
    val h = scope.size.height

    // Fire flames rushing from left to right behind the soccer balls
    val flamePath = Path().apply {
        moveTo(0f, h * 0.60f)
        quadraticTo(w * 0.25f, h * 0.15f, w * 0.60f, h * 0.20f)
        quadraticTo(w * 0.40f, h * 0.55f, w * 0.75f, h * 0.45f)
        quadraticTo(w * 0.30f, h * 0.85f, 0f, h * 0.60f)
        close()
    }

    val flameBrush = Brush.linearGradient(
        colors = listOf(Color(0xFFFFB74D), Color(0xFFFF5722)),
        start = Offset(0f, h * 0.5f),
        end = Offset(w * 0.7f, h * 0.5f)
    )
    scope.drawPath(flamePath, brush = flameBrush)

    // Ball 1 (the first 'O')
    val r = h * 0.38f
    val center1 = Offset(w * 0.32f, h * 0.52f)
    scope.drawCircle(color = Color.Black, radius = r, center = center1)
    scope.drawCircle(color = Color.White, radius = r * 0.85f, center = center1)
    // Soccer pentagon markings
    scope.drawCircle(color = Color.Black, radius = r * 0.35f, center = center1)

    // Ball 2 (the second 'O')
    val center2 = Offset(w * 0.72f, h * 0.52f)
    scope.drawCircle(color = Color.Black, radius = r, center = center2)
    scope.drawCircle(color = Color.White, radius = r * 0.85f, center = center2)
    scope.drawCircle(color = Color.Black, radius = r * 0.35f, center = center2)
}
