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
import androidx.compose.ui.unit.dp
import com.householdbudget.app.R
import com.householdbudget.app.ui.theme.Space
import com.householdbudget.app.ui.theme.onHeroColor
import com.householdbudget.app.ui.theme.onHeroMutedColor
import com.householdbudget.app.ui.util.formatWon
import kotlin.math.roundToInt

/**
 * 예산 진행 바 + 아래 메타 한 줄. [budgetMinor] 가 null(또는 0 이하)이면 아무것도 그리지 않는다.
 *
 * 채움: 사용률 ≤100% 는 민트, 100% 초과분은 레드로 캡(민트=예산분, 레드=초과분).
 * 메타: 왼쪽 "예산 1,900,000원 중 42% 사용",
 *       오른쪽 "하루 53,500원 여유" ([daysRemaining] ≤ 0 이면 "오늘까지 53,500원").
 *
 * @param onHero 히어로 카드(그라디언트) 위에서는 트랙/텍스트 색을 히어로용으로.
 */
@Composable
fun BudgetProgressBar(
    spentMinor: Long,
    budgetMinor: Long?,
    daysRemaining: Int,
    modifier: Modifier = Modifier,
    onHero: Boolean = false,
) {
    val budget = budgetMinor ?: return
    if (budget <= 0L) return

    val spent = spentMinor.coerceAtLeast(0L)
    val usedFraction = spent.toFloat() / budget.toFloat()
    // 초과 시 민트는 예산분(=budget/spent 비율)까지만, 나머지는 레드.
    val mintFraction = if (usedFraction > 1f) 1f / usedFraction else usedFraction.coerceIn(0f, 1f)
    val usedPercent = (spent * 100.0 / budget).roundToInt()
    val leftoverPerDay = (budget - spent).coerceAtLeast(0L) / daysRemaining.coerceAtLeast(1)

    val trackColor =
        if (onHero) {
            onHeroColor().copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    val metaColor =
        if (onHero) onHeroMutedColor() else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(trackColor),
        ) {
            if (usedFraction > 1f) {
                // 초과분 레드가 트랙 끝까지 깔리고, 그 위에 예산분 민트가 덮인다.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.error),
                )
            }
            Box(
                Modifier
                    .fillMaxWidth(mintFraction)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Text(
                text = stringResource(R.string.budget_bar_meta, budget.formatWon(), usedPercent),
                style = MaterialTheme.typography.labelSmall,
                color = metaColor,
                modifier = Modifier.weight(1f),
            )
            Text(
                text =
                    if (daysRemaining <= 0) {
                        stringResource(R.string.budget_bar_until_today, leftoverPerDay.formatWon())
                    } else {
                        stringResource(R.string.budget_bar_daily_leeway, leftoverPerDay.formatWon())
                    },
                style = MaterialTheme.typography.labelSmall,
                color = metaColor,
            )
        }
    }
}
