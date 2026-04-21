package com.householdbudget.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Matches DESIGN.md border-radius scale:
// extraSmall=6 (subtle), small=8 (standard cards/buttons),
// medium=12 (inputs/primary buttons), large=16 (featured containers), extraLarge=24 (hero)
val AppShapes =
    Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(24.dp),
    )
