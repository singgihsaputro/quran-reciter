package com.singgih.quranreciter.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Word results. Warm orange for a slip rather than red: a nudge, not an alarm.
// Deep enough to read at 3:1 on white, the large-text contrast floor.
val Correct = Color(0xFF0E9F77)
val Misread = Color(0xFFE8590C)
val Missed = Color(0xFF8C87A8)

val Sun = Color(0xFFFFC53D)
val Grape = Color(0xFF6A4CFF)
val Ink = Color(0xFF2B2250)

val Sky = Brush.verticalGradient(listOf(Color(0xFF6A4CFF), Color(0xFF8E7BFF), Color(0xFF5CC8FF)))
val Night = Brush.verticalGradient(listOf(Color(0xFF0B1033), Color(0xFF1E1650), Color(0xFF3A2470)))
val Moonlight = Color(0xFFEDE9FF)

/** Level tile colours, cycled by surah. */
val Candy = listOf(
    Color(0xFFFF6F91), Color(0xFF3DBE6C), Color(0xFFFF9F1C),
    Color(0xFF2EA8FF), Color(0xFF9B7BFF), Color(0xFFFF7F50),
)

@Composable
fun KidTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = lightColorScheme(
        primary = Grape, onPrimary = Color.White,
        secondary = Sun, onSecondary = Ink,
        surface = Color.White, onSurface = Ink,
        onSurfaceVariant = Color(0xFF6B6690),
    ),
    shapes = Shapes(
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(22.dp),
        large = RoundedCornerShape(28.dp),
    ),
    content = content,
)

@Composable
fun StarPill(text: String) {
    Row(
        Modifier
            .background(Color.White.copy(alpha = 0.22f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("★", color = Sun, fontSize = 18.sp)
        Spacer(Modifier.width(4.dp))
        Text(text, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

/** A rounded progress bar that grows to [fraction]. */
@Composable
fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val grown by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "bar")
    Box(modifier.height(10.dp).clip(CircleShape).background(color.copy(alpha = 0.18f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(grown).clip(CircleShape).background(color))
    }
}
