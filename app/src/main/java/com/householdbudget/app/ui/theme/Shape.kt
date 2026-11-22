package com.householdbudget.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// small=12 (버튼·입력·칩), large=20 (카드), extraLarge=28 (시트).
val AppShapes =
    Shapes(
        extraSmall = RoundedCornerShape(12.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )
