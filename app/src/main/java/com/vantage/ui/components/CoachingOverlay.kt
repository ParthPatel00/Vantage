package com.vantage.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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

    Box(modifier = Modifier.fillMaxSize()) {
        when (direction) {
            Direction.DOWN -> {
                // Stack the arrow tight on top of the bubble so the pair reads
                // as a single bottom element instead of a chunky gap.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp, start = 24.dp, end = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    EdgeArrow(direction = Direction.DOWN)
                    SuggestionBubble(text = text)
                }
            }
            Direction.UP, Direction.LEFT, Direction.RIGHT -> {
                EdgeArrow(
                    direction = direction,
                    modifier = Modifier.align(arrowEdgeFor(direction)!!).padding(edgePadding(direction))
                )
                SuggestionBubble(
                    text = text,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp, start = 24.dp, end = 24.dp)
                )
            }
            else -> {
                SuggestionBubble(
                    text = text,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp, start = 24.dp, end = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun EdgeArrow(direction: Direction, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier,
        text = direction.glyph,
        style = TextStyle(
            color = CoachingAmber,
            fontSize = 64.sp,
            fontWeight = FontWeight.Black,
            shadow = Shadow(
                color = Color.Black.copy(alpha = 0.85f),
                offset = Offset(0f, 2f),
                blurRadius = 10f
            )
        )
    )
}

// DOWN intentionally returns null — the bubble at the bottom already communicates
// the direction; stacking an arrow below it looked cluttered against the controls.
private fun arrowEdgeFor(direction: Direction?): Alignment? = when (direction) {
    Direction.UP -> Alignment.TopCenter
    Direction.LEFT -> Alignment.CenterStart
    Direction.RIGHT -> Alignment.CenterEnd
    else -> null
}

private fun edgePadding(direction: Direction): androidx.compose.foundation.layout.PaddingValues =
    when (direction) {
        Direction.UP -> androidx.compose.foundation.layout.PaddingValues(top = 32.dp)
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
