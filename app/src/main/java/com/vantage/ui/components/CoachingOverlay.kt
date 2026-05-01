package com.vantage.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import com.vantage.ui.theme.CoachingAmber
import com.vantage.ui.theme.White

private enum class Direction(val glyph: String) {
    UP("↑"),
    DOWN("↓"),
    LEFT("←"),
    RIGHT("→"),
    BACK("↖"),
    FORWARD("↗"),
    ROTATE_LEFT("↺"),
    ROTATE_RIGHT("↻"),
    ZOOM_IN("➕"),
    ZOOM_OUT("➖")
}

private fun directionFor(text: String): Direction? {
    val t = text.lowercase()
    return when {
        "tilt left" in t || "rotate left" in t -> Direction.ROTATE_LEFT
        "tilt right" in t || "rotate right" in t -> Direction.ROTATE_RIGHT
        "tilt up" in t || "angle up" in t -> Direction.UP
        "tilt down" in t || "angle down" in t -> Direction.DOWN
        "left" in t -> Direction.LEFT
        "right" in t -> Direction.RIGHT
        "up" in t -> Direction.UP
        "down" in t -> Direction.DOWN
        "forward" in t || "closer" in t || "zoom in" in t -> Direction.ZOOM_IN
        "back" in t || "zoom out" in t -> Direction.ZOOM_OUT
        else -> null
    }
}

@Composable
fun CoachingOverlay(
    suggestion: String?,
    revision: Int = 0,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
        targetState = suggestion?.takeIf { it.isNotBlank() },
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
        label = "coachingSuggestion"
    ) { current ->
        if (current != null) {
            CoachingContent(text = current, revision = revision)
        } else {
            Box(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun CoachingContent(text: String, revision: Int) {
    val direction = directionFor(text)

    Box(modifier = Modifier.fillMaxSize()) {
        if (direction != null) {
            val alignment = arrowEdgeFor(direction) ?: Alignment.Center
            EdgeArrow(
                direction = direction,
                revision = revision,
                modifier = Modifier
                    .align(alignment)
                    .padding(edgePadding(direction))
            )
        }

        SuggestionBubble(
            text = text,
            revision = revision,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp, start = 24.dp, end = 24.dp)
        )
    }
}

@Composable
private fun EdgeArrow(direction: Direction, revision: Int, modifier: Modifier = Modifier) {
    val scale = remember { Animatable(1f) }

    LaunchedEffect(revision) {
        if (revision == 0) return@LaunchedEffect
        scale.snapTo(0.7f)
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    Text(
        modifier = modifier.graphicsLayer { 
            scaleX = scale.value
            scaleY = scale.value
        },
        text = direction.glyph,
        style = TextStyle(
            color = CoachingAmber,
            fontSize = 84.sp,
            fontWeight = FontWeight.Black,
            shadow = Shadow(
                color = Color.Black.copy(alpha = 0.85f),
                offset = Offset(0f, 4f),
                blurRadius = 15f
            )
        )
    )
}

private fun arrowEdgeFor(direction: Direction?): Alignment? = when (direction) {
    Direction.UP -> Alignment.TopCenter
    Direction.DOWN -> Alignment.Center
    Direction.LEFT -> Alignment.CenterStart
    Direction.RIGHT -> Alignment.CenterEnd
    Direction.BACK -> Alignment.Center
    Direction.FORWARD -> Alignment.Center
    Direction.ROTATE_LEFT -> Alignment.Center
    Direction.ROTATE_RIGHT -> Alignment.Center
    Direction.ZOOM_IN -> Alignment.Center
    Direction.ZOOM_OUT -> Alignment.Center
    else -> null
}

private fun edgePadding(direction: Direction): androidx.compose.foundation.layout.PaddingValues =
    when (direction) {
        Direction.UP -> androidx.compose.foundation.layout.PaddingValues(top = 80.dp)
        Direction.LEFT -> androidx.compose.foundation.layout.PaddingValues(start = 32.dp)
        Direction.RIGHT -> androidx.compose.foundation.layout.PaddingValues(end = 32.dp)
        else -> androidx.compose.foundation.layout.PaddingValues(0.dp)
    }

@Composable
private fun SuggestionBubble(text: String, revision: Int, modifier: Modifier = Modifier) {
    val scale = remember { Animatable(1f) }

    // Pop animation fires on every new analysis, even if the text didn't change,
    // so the user knows Gemma re-read the scene.
    LaunchedEffect(revision) {
        if (revision == 0) return@LaunchedEffect
        scale.snapTo(0.88f)
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .widthIn(max = 320.dp)
            .shadow(16.dp, RoundedCornerShape(28.dp))
            .background(White.copy(alpha = 0.93f), RoundedCornerShape(28.dp))
            .padding(horizontal = 22.dp, vertical = 15.dp)
    ) {
        Text(
            text = text,
            color = Color(0xFF111111),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF222222)
@Composable
private fun PreviewDownArrow() {
    Box(Modifier.background(Color.DarkGray).fillMaxSize()) {
        CoachingOverlay(suggestion = "Tilt down", revision = 1)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF222222)
@Composable
private fun PreviewLeftArrow() {
    Box(Modifier.background(Color.DarkGray).fillMaxSize()) {
        CoachingOverlay(suggestion = "Move left", revision = 1)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF222222)
@Composable
private fun PreviewNoArrow() {
    Box(Modifier.background(Color.DarkGray).fillMaxSize()) {
        CoachingOverlay(suggestion = "Hold still", revision = 1)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF222222)
@Composable
private fun PreviewEmpty() {
    Box(Modifier.background(Color.DarkGray).fillMaxSize()) {
        CoachingOverlay(suggestion = null)
    }
}
