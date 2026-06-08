package com.householdbudget.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Toss-style radius scale — friendly, generous rounding.
// extraSmall=8 (chips/pills), small=12 (buttons/list rows),
// medium=14 (inputs), large=20 (cards), extraLarge=24 (hero/sheets)
val AppShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(14.dp),
        large = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(24.dp),
    )
