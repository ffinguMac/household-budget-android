package com.householdbudget.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign

/** 여러 [FitText]가 나눠 쓰는 글자 배율. 한 칸이 넘치면 같은 그룹의 모든 칸이 같은 크기로 줄어든다. */
@Stable
class FitScale {
    var value by mutableFloatStateOf(1f)
}

/** [keys](보통 그 줄의 금액들)가 바뀌면 배율을 처음(1배)부터 다시 맞춘다. */
@Composable
fun rememberFitScale(vararg keys: Any?): FitScale = remember(*keys) { FitScale() }

/**
 * 금액처럼 잘리면 안 되는 한 줄 글자. 칸보다 길면 글자 크기를 줄여(최소 [minScale]배) 한 줄에 맞춘다.
 * 줄바꿈·말줄임 대신 쓰는 이유: "1,234,567원"이 두 줄로 쪼개지거나 "1,23…"이 되면 금액을 잘못 읽는다.
 * 나란한 칸들은 같은 [scale]을 넘겨 크기를 맞춘다.
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    scale: FitScale = rememberFitScale(text, style),
    minScale: Float = 0.6f,
    textAlign: TextAlign? = null,
) = FitText(AnnotatedString(text), style, color, modifier, scale, minScale, textAlign)

/** 부분 강조(굵은 금액 등)가 있는 한 줄 글자용. */
@Composable
fun FitText(
    text: AnnotatedString,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    scale: FitScale = rememberFitScale(text, style),
    minScale: Float = 0.6f,
    textAlign: TextAlign? = null,
) {
    var fits by remember(text, style) { mutableStateOf(false) }
    val s = scale.value
    Text(
        text = text,
        style = style.copy(fontSize = style.fontSize * s, lineHeight = style.lineHeight * s),
        color = color,
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
        // 크기를 맞추는 중간 프레임은 그리지 않아 깜빡임을 막는다.
        modifier = modifier.drawWithContent { if (fits) drawContent() },
        onTextLayout = { result ->
            if (result.didOverflowWidth && scale.value > minScale) {
                fits = false
                scale.value = (scale.value * 0.92f).coerceAtLeast(minScale)
            } else {
                fits = true
            }
        },
    )
}
