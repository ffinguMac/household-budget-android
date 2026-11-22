package com.householdbudget.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.householdbudget.app.R
import com.householdbudget.app.ui.feed.budgetStatus
import com.householdbudget.app.ui.theme.Space
import com.householdbudget.app.ui.util.formatWon
import kotlin.math.roundToInt

/**
 * 예산 진행 바(8dp) + 아래 두 줄: 왼쪽 "187,600원 씀", 오른쪽 "예산 1,200,000원 중 84% 남음".
 * 초과면 막대 전체가 error, 오른쪽은 "예산 1,200,000원 초과".
 */
@Composable
fun BudgetProgressBar(
    spentMinor: Long,
    budgetMinor: Long,
    modifier: Modifier = Modifier,
) {
    val status = budgetStatus(budgetMinor, spentMinor, daysRemaining = 1)
    val over = status.remainingMinor < 0
    val leftPercent = ((1f - status.usedFraction) * 100).roundToInt()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(status.usedFraction)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Text(
                text = stringResource(R.string.budget_bar_spent, spentMinor.formatWon()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text =
                    if (over) {
                        stringResource(R.string.budget_bar_over, budgetMinor.formatWon())
                    } else {
                        stringResource(R.string.budget_bar_left, budgetMinor.formatWon(), leftPercent)
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
    }
}
