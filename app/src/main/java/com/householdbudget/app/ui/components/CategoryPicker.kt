@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.householdbudget.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.householdbudget.app.R
import com.householdbudget.app.data.local.entity.CategoryEntity
import com.householdbudget.app.ui.theme.Space
import com.householdbudget.app.ui.theme.categoryStyleOf

private const val GRID_COLUMNS = 4
private const val MAX_PARENT_TILES = 7

/**
 * 대분류 4열 격자. 타일을 누르면 같은 자리에서 그 대분류의 세부 칩으로 바뀐다.
 * 세부가 하나뿐인 대분류는 바로 [onLeafSelected]. 선택된 세부 칩만 강조한다.
 */
@Composable
fun CategoryPicker(
    parents: List<CategoryEntity>,
    childrenByParent: Map<Long, List<CategoryEntity>>,
    selectedLeafId: Long?,
    onLeafSelected: (CategoryEntity) -> Unit,
    modifier: Modifier = Modifier,
    onMore: (() -> Unit)? = null,
) {
    var expandedParentId by rememberSaveable { mutableStateOf<Long?>(null) }
    val expanded = parents.firstOrNull { it.id == expandedParentId }

    if (expanded != null) {
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            CategoryChip(
                label = stringResource(R.string.category_picker_back, expanded.name),
                selected = false,
                onClick = { expandedParentId = null },
            )
            childrenByParent[expanded.id].orEmpty().forEach { leaf ->
                CategoryChip(
                    label = leaf.name,
                    selected = leaf.id == selectedLeafId,
                    onClick = { onLeafSelected(leaf) },
                )
            }
        }
        return
    }

    // null 자리는 "더 보기" 타일.
    val tiles: List<CategoryEntity?> = parents.take(MAX_PARENT_TILES) + if (onMore != null) listOf(null) else emptyList()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        tiles.chunked(GRID_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                row.forEach { parent ->
                    if (parent == null) {
                        PickerTile(onClick = { onMore?.invoke() }, modifier = Modifier.weight(1f)) {
                            Icon(
                                Icons.Rounded.MoreHoriz,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                stringResource(R.string.category_picker_more),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        PickerTile(
                            onClick = {
                                val children = childrenByParent[parent.id].orEmpty()
                                if (children.size == 1) onLeafSelected(children.first()) else expandedParentId = parent.id
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(categoryStyleOf(parent.name).emoji, style = MaterialTheme.typography.titleLarge)
                            Text(parent.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    }
                }
                // 마지막 줄이 덜 찼어도 타일 폭을 4열에 맞춘다.
                repeat(GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PickerTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) { content() }
    }
}

/** 글자만 있는 원형 칩. 선택되면 `primaryContainer`, 아니면 1dp `outline` 테두리. */
@Composable
fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        modifier = modifier,
        shape = CircleShape,
        colors =
            FilterChipDefaults.filterChipColors(
                labelColor = MaterialTheme.colorScheme.onSurface,
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        border =
            FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selected,
                borderColor = MaterialTheme.colorScheme.outline,
                selectedBorderColor = Color.Transparent,
                borderWidth = 1.dp,
            ),
    )
}
