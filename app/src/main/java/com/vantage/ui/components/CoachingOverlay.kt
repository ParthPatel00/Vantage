package com.vantage.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vantage.ui.theme.ChatBubbleBg
import com.vantage.ui.theme.CoachingAmber
import com.vantage.ui.theme.White

private enum class Direction(val glyph: String, val isCardinal: Boolean) {
    UP("↑", true),
    DOWN("↓", true),
    LEFT("←", true),
    RIGHT("→", true),
    BACK("↖", false),
    FORWARD("↗", false),
    ROTATE_LEFT("↺", false),
    ROTATE_RIGHT("↻", false),
    ZOOM_IN("➕", false),
    ZOOM_OUT("➖", false)
}

private fun directionFor(text: String): Direction? {
    val t = text.lowercase()
    return when {
        "rotate left" in t || "tilt left" in t -> Direction.ROTATE_LEFT
        "rotate right" in t || "tilt right" in t -> Direction.ROTATE_RIGHT
        "zoom in" in t || "closer" in t || "step forward" in t -> Direction.ZOOM_IN
        "zoom out" in t || "step back" in t || "back up" in t -> Direction.ZOOM_OUT
        "tilt down" in t || "angle down" in t || "lower" in t -> Direction.DOWN
        "tilt up" in t || "angle up" in t || "raise" in t -> Direction.UP
        "move left" in t || "pan left" in t || " left" in t -> Direction.LEFT
        "move right" in t || "pan right" in t || " right" in t -> Direction.RIGHT
        " up" in t -> Direction.UP
        " down" in t -> Direction.DOWN
        else -> null
    }
}

@Composable
fun CoachingOverlay(
    suggestion: String?,
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
            CoachingContent(text = current)
        } else {
            Box(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun CoachingContent(text: String) {
    val direction = directionFor(text)
    val isDownArrow = direction == Direction.DOWN

    Box(modifier = Modifier.fillMaxSize()) {
        if (direction != null && direction.isCardinal) {
            EdgeArrow(
                direction = direction,
                modifier = Modifier.align(arrowAlignment(direction)).padding(edgePadding(direction))
            )
        }
        SuggestionBubble(
            text = text,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    bottom = if (isDownArrow) 132.dp else 48.dp,
                    start = 24.dp,
                    end = 24.dp
                )
        )
    }
}

@Composable
private fun EdgeArrow(direction: Direction, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier,
        text = direction.glyph,
        style = TextStyle(
            color = CoachingAmber,
            fontSize = 88.sp,
            fontWeight = FontWeight.Black,
            shadow = Shadow(
                color = Color.Black.copy(alpha = 0.85f),
                offset = Offset(0f, 3f),
                blurRadius = 14f
            )
        )
    )
}

private fun arrowAlignment(direction: Direction): Alignment = when (direction) {
    Direction.UP -> Alignment.TopCenter
    Direction.DOWN -> Alignment.BottomCenter
    Direction.LEFT -> Alignment.CenterStart
    Direction.RIGHT -> Alignment.CenterEnd
    else -> Alignment.Center
}

private fun edgePadding(direction: Direction): androidx.compose.foundation.layout.PaddingValues =
    when (direction) {
        Direction.UP -> androidx.compose.foundation.layout.PaddingValues(top = 32.dp)
        Direction.DOWN -> androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)
        Direction.LEFT -> androidx.compose.foundation.layout.PaddingValues(start = 24.dp)
        Direction.RIGHT -> androidx.compose.foundation.layout.PaddingValues(end = 24.dp)
        else -> androidx.compose.foundation.layout.PaddingValues(0.dp)
    }

@Composable
private fun SuggestionBubble(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .widthIn(max = 320.dp)
            .shadow(12.dp, RoundedCornerShape(20.dp))
            .background(ChatBubbleBg.copy(alpha = 0.88f), RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Text(
            text = text,
            color = White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF222222)
@Composable
private fun PreviewDownArrow() {
    Box(Modifier.background(Color.DarkGray).fillMaxSize()) {
        CoachingOverlay(suggestion = "Tilt the camera down a bit")
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF222222)
@Composable
private fun PreviewLeftArrow() {
    Box(Modifier.background(Color.DarkGray).fillMaxSize()) {
        CoachingOverlay(suggestion = "Move slightly to the left")
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF222222)
@Composable
private fun PreviewNoArrow() {
    Box(Modifier.background(Color.DarkGray).fillMaxSize()) {
        CoachingOverlay(suggestion = "Hold still — almost got it")
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF222222)
@Composable
private fun PreviewEmpty() {
    Box(Modifier.background(Color.DarkGray).fillMaxSize()) {
        CoachingOverlay(suggestion = null)
    }
}
