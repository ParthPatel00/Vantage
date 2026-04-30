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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vantage.ui.theme.Black
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
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 24.dp, vertical = 48.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedContent(
            targetState = suggestion?.takeIf { it.isNotBlank() },
            transitionSpec = {
                (fadeIn(tween(220)) togetherWith fadeOut(tween(180)))
            },
            label = "coachingSuggestion"
        ) { current ->
            if (current != null) {
                SuggestionBubble(text = current, direction = directionFor(current))
            } else {
                Spacer(Modifier.fillMaxWidth().height(0.dp))
            }
        }
    }
}

@Composable
private fun SuggestionBubble(text: String, direction: Direction?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (direction != null) {
            Box(
                modifier = Modifier
                    .shadow(8.dp, RoundedCornerShape(50))
                    .background(CoachingAmber, RoundedCornerShape(50))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    text = direction.glyph,
                    color = Black,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Box(
            modifier = Modifier
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
}

@Preview(showBackground = true, backgroundColor = 0xFF222222)
@Composable
private fun PreviewBubbleWithArrow() {
    Box(Modifier.background(Color.DarkGray).fillMaxSize()) {
        CoachingOverlay(suggestion = "Tilt the camera down a bit")
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF222222)
@Composable
private fun PreviewBubbleNoArrow() {
    Box(Modifier.background(Color.DarkGray).fillMaxSize()) {
        CoachingOverlay(suggestion = "Hold still — almost got it")
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF222222)
@Composable
private fun PreviewBubbleEmpty() {
    Box(Modifier.background(Color.DarkGray).fillMaxSize()) {
        CoachingOverlay(suggestion = null)
    }
}
