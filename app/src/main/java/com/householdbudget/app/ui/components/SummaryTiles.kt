package com.householdbudget.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.householdbudget.app.R
import com.householdbudget.app.domain.CategoryKind
import com.householdbudget.app.ui.theme.Space
import com.householdbudget.app.ui.theme.kindDotColor
import com.householdbudget.app.ui.util.formatWon

/** 컨테이너 없는 통계 한 칸: 위 `bodySmall` 라벨(점 8dp), 아래 `titleMedium` 값. */
@Composable
fun StatColumn(
    label: String,
    value: String,
    dotColor: Color?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            if (dotColor != null) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FitText(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 수입/지출/저축 3칸 요약. Home / Calendar / Archive 가 공유. 금액은 부호 없이. */
@Composable
fun KindSummaryRow(
    incomeMinor: Long,
    expenseMinor: Long,
    savingsMinor: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        StatColumn(
            label = stringResource(R.string.summary_income),
            value = incomeMinor.formatWon(),
            dotColor = kindDotColor(CategoryKind.INCOME),
            modifier = Modifier.weight(1f),
        )
        StatColumn(
            label = stringResource(R.string.summary_expense),
            value = expenseMinor.formatWon(),
            dotColor = kindDotColor(CategoryKind.EXPENSE),
            modifier = Modifier.weight(1f),
        )
        StatColumn(
            label = stringResource(R.string.summary_savings),
            value = savingsMinor.formatWon(),
            dotColor = kindDotColor(CategoryKind.SAVINGS),
            modifier = Modifier.weight(1f),
        )
    }
}
