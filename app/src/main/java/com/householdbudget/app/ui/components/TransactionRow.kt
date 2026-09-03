package com.householdbudget.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.householdbudget.app.domain.CategoryKind
import com.householdbudget.app.ui.theme.Space
import com.householdbudget.app.ui.theme.kindAccent
import com.householdbudget.app.ui.theme.kindContainer
import com.householdbudget.app.ui.theme.kindOnContainer
import com.householdbudget.app.ui.util.formatSignedWon

/**
 * 거래 한 줄. 아바타(카테고리 첫 글자) + 제목/보조텍스트 + 금액.
 * Home / Ledger / Calendar 상세 / Archive 상세가 전부 이 하나를 쓴다.
 * 배경은 투명 — 호출자가 Surface/카드로 감싼다.
 *
 * @param categoryName 소분류 이름
 * @param parentCategoryName 대분류 이름 (있으면 제목에 "대분류 · 소분류" 로 표시)
 * @param memo 비어 있으면 보조줄에 표시하지 않음
 * @param dateLabel null 이면 보조줄에 날짜 표시하지 않음 (날짜별 그룹 목록에서는 null)
 * @param onClick null 이면 클릭 불가 + ripple 없음
 */
@Composable
fun TransactionRow(
    categoryName: String,
    parentCategoryName: String?,
    memo: String,
    amountMinor: Long,
    kind: CategoryKind,
    dateLabel: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val title =
        if (parentCategoryName.isNullOrBlank()) {
            categoryName
        } else {
            "$parentCategoryName · $categoryName"
        }
    val supporting =
        listOfNotNull(
            dateLabel?.takeIf { it.isNotBlank() },
            memo.takeIf { it.isNotBlank() },
        ).joinToString(" · ")

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .defaultMinSize(minHeight = 64.dp)
                .padding(horizontal = Space.lg, vertical = Space.sm),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 아바타 첫 글자는 장식 — 제목이 곧 카테고리명이라 스크린리더 중복 읽기를 막는다.
        // EXPENSE 는 목록의 대다수라 레드 컨테이너가 시끄럽다 — 중립(surfaceVariant)으로 가라앉힌다(목업 기준).
        val avatarBg =
            if (kind == CategoryKind.EXPENSE) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                kindContainer(kind)
            }
        val avatarFg =
            if (kind == CategoryKind.EXPENSE) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                kindOnContainer(kind)
            }
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(avatarBg)
                    .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = categoryName.take(1),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = avatarFg,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Space.xxs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (supporting.isNotEmpty()) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = amountMinor.formatSignedWon(kind),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = kindAccent(kind),
        )
    }
}
