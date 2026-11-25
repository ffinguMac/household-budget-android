@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.householdbudget.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.res.stringResource
import com.householdbudget.app.R
import com.householdbudget.app.data.local.entity.CategoryEntity
import com.householdbudget.app.data.repository.CustomCategoryDeletePreview
import com.householdbudget.app.data.repository.CustomCategoryResult
import com.householdbudget.app.domain.category.CustomCategories
import com.householdbudget.app.ui.BudgetViewModel
import com.householdbudget.app.ui.autoentry.roParticle
import com.householdbudget.app.ui.theme.Space
import kotlinx.coroutines.launch

sealed interface CustomCategoryRequest {
    data class Add(val parent: CategoryEntity) : CustomCategoryRequest
    data class Edit(val leaf: CategoryEntity) : CustomCategoryRequest
}

/** 직접 추가 세부의 추가·이름 변경 대화상자와, 그 안에서 여는 삭제 확인. [request]가 null이면 아무것도 그리지 않는다. */
@Composable
fun CustomCategoryDialogs(
    request: CustomCategoryRequest?,
    budgetViewModel: BudgetViewModel,
    onDismiss: () -> Unit,
    onCreated: (CategoryEntity) -> Unit,
    onDeleted: (deleted: CategoryEntity, movedTo: CategoryEntity) -> Unit = { _, _ -> },
) {
    if (request == null) return
    // 요청이 바뀌면 입력·오류 상태를 새로 시작한다.
    key(request) {
        val scope = rememberCoroutineScope()
        val leaf = (request as? CustomCategoryRequest.Edit)?.leaf
        // 이름 바꾸기는 커서를 끝에 둔다.
        var name by remember { mutableStateOf(leaf?.name.orEmpty().let { TextFieldValue(it, TextRange(it.length)) }) }
        val focusRequester = remember { FocusRequester() }
        var error by remember { mutableStateOf<CustomCategories.NameError?>(null) }
        var busy by remember { mutableStateOf(false) }
        var preview by remember { mutableStateOf<CustomCategoryDeletePreview?>(null) }
        var targetId by remember { mutableLongStateOf(0L) }

        fun save() {
            if (busy) return
            busy = true
            scope.launch {
                val result =
                    when (request) {
                        is CustomCategoryRequest.Add -> budgetViewModel.addCustomCategory(request.parent.id, name.text)
                        is CustomCategoryRequest.Edit -> budgetViewModel.renameCustomCategory(request.leaf.id, name.text)
                    }
                busy = false
                when (result) {
                    null -> onDismiss()
                    is CustomCategoryResult.Invalid -> error = result.error
                    is CustomCategoryResult.Saved -> {
                        if (request is CustomCategoryRequest.Add) onCreated(result.category)
                        onDismiss()
                    }
                }
            }
        }

        val current = preview
        if (leaf != null && current != null) {
            val target = current.targets.firstOrNull { it.id == targetId } ?: current.targets.first()
            AlertDialog(
                onDismissRequest = { if (!busy) preview = null },
                shape = MaterialTheme.shapes.large,
                title = { Text(stringResource(R.string.custom_category_delete_title, leaf.name)) },
                text = {
                    if (current.transactionCount == 0) {
                        Text(stringResource(R.string.custom_category_delete_body_empty))
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                            Text(
                                stringResource(
                                    R.string.custom_category_delete_body_moved,
                                    current.transactionCount,
                                    target.name,
                                    roParticle(target.name),
                                ),
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                current.targets.forEach {
                                    CategoryChip(label = it.name, selected = it.id == target.id, onClick = { targetId = it.id })
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                val movedTo = budgetViewModel.deleteCustomCategory(leaf.id, target.id)
                                busy = false
                                if (movedTo != null) onDeleted(leaf, movedTo)
                                onDismiss()
                            }
                        },
                    ) {
                        Text(stringResource(R.string.edit_delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(enabled = !busy, onClick = { preview = null }) {
                        Text(stringResource(R.string.edit_dismiss))
                    }
                },
            )
        } else {
            AlertDialog(
                onDismissRequest = { if (!busy) onDismiss() },
                shape = MaterialTheme.shapes.large,
                title = {
                    Text(
                        when (request) {
                            is CustomCategoryRequest.Add -> stringResource(R.string.custom_category_add_title, request.parent.name)
                            is CustomCategoryRequest.Edit -> stringResource(R.string.custom_category_edit_title)
                        },
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = {
                                name = it
                                error = null
                            },
                            placeholder = { Text(stringResource(R.string.custom_category_name_hint)) },
                            singleLine = true,
                            isError = error != null,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { save() }),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        )
                        LaunchedEffect(Unit) { focusRequester.requestFocus() }
                        error?.let {
                            Text(
                                stringResource(
                                    when (it) {
                                        CustomCategories.NameError.EMPTY -> R.string.custom_category_error_empty
                                        CustomCategories.NameError.TOO_LONG -> R.string.custom_category_error_too_long
                                        CustomCategories.NameError.DUPLICATE -> R.string.custom_category_error_duplicate
                                    },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !busy,
                        onClick = ::save,
                    ) {
                        Text(
                            stringResource(
                                if (request is CustomCategoryRequest.Add) R.string.custom_category_add_confirm else R.string.edit_save,
                            ),
                        )
                    }
                },
                dismissButton = {
                    Row {
                        if (leaf != null) {
                            TextButton(
                                enabled = !busy,
                                onClick = {
                                    busy = true
                                    scope.launch {
                                        val p = budgetViewModel.customCategoryDeletePreview(leaf.id)
                                        busy = false
                                        // null = 이미 지워졌거나 옮길 고정 세부가 없다. 지울 수 없으니 닫는다.
                                        if (p == null) {
                                            onDismiss()
                                        } else {
                                            targetId = p.defaultTargetId
                                            preview = p
                                        }
                                    }
                                },
                            ) {
                                Text(stringResource(R.string.edit_delete), color = MaterialTheme.colorScheme.error)
                            }
                        }
                        TextButton(enabled = !busy, onClick = onDismiss) {
                            Text(stringResource(R.string.edit_dismiss))
                        }
                    }
                },
            )
        }
    }
}
