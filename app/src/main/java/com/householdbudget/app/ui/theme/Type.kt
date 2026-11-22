package com.householdbudget.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.householdbudget.app.R

val Pretendard =
    FontFamily(
        Font(R.font.pretendard_regular, FontWeight.Normal),
        Font(R.font.pretendard_bold, FontWeight.Bold),
    )

// 화면 코드는 displaySmall·titleLarge·titleMedium·bodyLarge·labelLarge·bodySmall 여섯 개만 쓴다.
// 나머지 슬롯은 M3 컴포넌트 기본값용으로 같은 네 단계에 묶는다.
private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) =
    TextStyle(
        fontFamily = Pretendard,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.em,
        fontFeatureSettings = "tnum",
    )

private val Display = style(32, 40, FontWeight.Bold, -0.02)
private val Title = style(20, 28, FontWeight.Bold, -0.01)
private val TitleMedium = style(16, 24, FontWeight.Bold)
private val Body = style(16, 24, FontWeight.Normal)
private val Label = style(13, 18, FontWeight.Bold)
private val Caption = style(13, 18, FontWeight.Normal)

val Typography =
    Typography(
        displayLarge = Display,
        displayMedium = Display,
        displaySmall = Display,
        headlineLarge = Display,
        headlineMedium = Display,
        headlineSmall = Title,
        titleLarge = Title,
        titleMedium = TitleMedium,
        bodyLarge = Body,
        titleSmall = Label,
        labelLarge = Label,
        labelMedium = Label,
        bodyMedium = Caption,
        bodySmall = Caption,
        labelSmall = Caption,
    )
