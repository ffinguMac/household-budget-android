package com.householdbudget.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.householdbudget.app.R
import com.householdbudget.app.data.local.entity.CategoryEntity
import com.householdbudget.app.ui.theme.Space
import com.householdbudget.app.ui.theme.categoryLabel

/** [CategoryPicker]의 "더 보기": 모든 대분류의 세부를 검색해 고르는 시트. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CategorySearchSheet(
    parents: List<CategoryEntity>,
    childrenByParent: Map<Long, List<CategoryEntity>>,
    selectedLeafId: Long?,
    onLeafSelected: (CategoryEntity) -> Unit,
    onDismiss: () -> Unit,
    onEditCustom: ((leaf: CategoryEntity) -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenHorizontalPadding)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Text(
                text = stringResource(R.string.edit_category_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.edit_category_search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            )
            val trimmedQuery = query.trim()
            val sections =
                parents.mapNotNull { parent ->
                    val children = childrenByParent[parent.id].orEmpty()
                    val filtered =
                        when {
                            trimmedQuery.isEmpty() -> children
                            parent.name.contains(trimmedQuery) -> children
                            else -> children.filter { it.name.contains(trimmedQuery) }
                        }
                    if (filtered.isEmpty()) null else parent to filtered
                }
            if (sections.isEmpty()) {
                Text(
                    text = stringResource(R.string.edit_category_search_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Space.xl),
                )
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 440.dp),
                ) {
                    sections.forEach { (parent, children) ->
                        item(key = "parent-${parent.id}") {
                            Text(
                                text = categoryLabel(parent.name),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Space.md, bottom = Space.xs),
                            )
                        }
                        items(children, key = { "leaf-${it.id}" }) { leaf ->
                            val longPressLabel = stringResource(R.string.custom_category_long_press_label)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .then(
                                        if (onEditCustom != null && isCustomLeaf(parent, leaf)) {
                                            Modifier.combinedClickable(
                                                onClick = { onLeafSelected(leaf) },
                                                onLongClick = { onEditCustom(leaf) },
                                                onLongClickLabel = longPressLabel,
                                            )
                                        } else {
                                            Modifier.clickable { onLeafSelected(leaf) }
                                        },
                                    )
                                    .heightIn(min = 48.dp)
                                    .padding(horizontal = Space.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = leaf.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                if (selectedLeafId == leaf.id) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Space.lg))
        }
    }
}
