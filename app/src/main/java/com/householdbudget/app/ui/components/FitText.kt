package com.householdbudget.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * 금액처럼 잘리면 안 되는 한 줄 글자. 칸보다 길면 글자 크기를 줄여(최소 [minScale]배) 한 줄에 맞춘다.
 * 줄바꿈·말줄임 대신 쓰는 이유: "1,234,567원"이 두 줄로 쪼개지거나 "1,23…"이 되면 금액을 잘못 읽는다.
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    minScale: Float = 0.6f,
) {
    var scale by remember(text, style) { mutableFloatStateOf(1f) }
    var ready by remember(text, style) { mutableStateOf(false) }
    Text(
        text = text,
        style = style.copy(fontSize = style.fontSize * scale, lineHeight = style.lineHeight * scale),
        color = color,
        maxLines = 1,
        softWrap = false,
        // 크기를 맞추는 중간 프레임은 그리지 않아 깜빡임을 막는다.
        modifier = modifier.drawWithContent { if (ready) drawContent() },
        onTextLayout = { result ->
            if (result.didOverflowWidth && scale > minScale) {
                scale = (scale * 0.92f).coerceAtLeast(minScale)
            } else {
                ready = true
            }
        },
    )
}
