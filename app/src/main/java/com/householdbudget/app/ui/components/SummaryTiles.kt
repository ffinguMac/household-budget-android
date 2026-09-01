package com.householdbudget.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.householdbudget.app.R
import com.householdbudget.app.domain.CategoryKind
import com.householdbudget.app.ui.theme.Space
import com.householdbudget.app.ui.theme.kindAccent
import com.householdbudget.app.ui.util.formatSignedWon

/** 라벨 + 금액 타일 하나. accent 점(8dp)을 라벨 앞에 찍는다. */
@Composable
fun SummaryTile(
    label: String,
    amountText: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(
            // 긴 금액이 한 줄에 최대한 들어가도록 좌우 padding 은 12dp 로 좁게.
            modifier = Modifier.padding(horizontal = 12.dp, vertical = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accent),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = amountText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 수입/지출/저축 3분할 요약 행. Home / Ledger / Calendar 가 공유.
 * 부호 포함 표기: 수입 "+", 지출 "−", 저축 "↑".
 */
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
        SummaryTile(
            label = stringResource(R.string.summary_income),
            amountText = incomeMinor.formatSignedWon(CategoryKind.INCOME),
            accent = kindAccent(CategoryKind.INCOME),
            modifier = Modifier.weight(1f),
        )
        SummaryTile(
            label = stringResource(R.string.summary_expense),
            amountText = expenseMinor.formatSignedWon(CategoryKind.EXPENSE),
            accent = kindAccent(CategoryKind.EXPENSE),
            modifier = Modifier.weight(1f),
        )
        SummaryTile(
            label = stringResource(R.string.summary_savings),
            amountText = savingsMinor.formatSignedWon(CategoryKind.SAVINGS),
            accent = kindAccent(CategoryKind.SAVINGS),
            modifier = Modifier.weight(1f),
        )
    }
}
