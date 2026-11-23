package com.householdbudget.app.ui.autoentry

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.householdbudget.app.BudgetApplication
import com.householdbudget.app.R
import com.householdbudget.app.data.local.entity.AutoEntryEntity
import com.householdbudget.app.data.local.entity.CategoryEntity
import com.householdbudget.app.domain.CategoryKind
import com.householdbudget.app.ui.BudgetViewModel
import com.householdbudget.app.ui.components.AppTopBar
import com.householdbudget.app.ui.components.CategoryPicker
import com.householdbudget.app.ui.components.CategorySearchSheet
import com.householdbudget.app.ui.components.EmptyState
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import com.householdbudget.app.ui.components.TransactionRow
import com.householdbudget.app.ui.components.cardBorder
import com.householdbudget.app.ui.components.pushedBottomPadding
import com.householdbudget.app.ui.feed.budgetStatus
import com.householdbudget.app.ui.theme.Space
import com.householdbudget.app.ui.util.formatShortDayLabel
import com.householdbudget.app.ui.util.formatWon
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val NEXT_ITEM_LOCK_MS = 300L

fun defaultKindFor(direction: String): CategoryKind =
    if (direction == "IN" || direction == "REFUND") CategoryKind.INCOME else CategoryKind.EXPENSE

@StringRes
fun reasonLabelRes(reason: String?): Int =
    when (reason) {
        "DUP_CROSS_APP", "DUP_MANUAL", "DUP_REPOST" -> R.string.auto_reason_duplicate
        "UNKNOWN_DIRECTION" -> R.string.auto_reason_direction
        "RESTORED" -> R.string.auto_reason_restored
        "SELF_TRANSFER" -> R.string.auto_reason_self_transfer
        else -> R.string.auto_reason_unclassified
    }

/** "기본" → "으로", "식비"·"술" → "로". 받침이 없거나 ㄹ이면 "로". 한글이 아니면 "로". */
fun roParticle(word: String): String {
    val c = word.lastOrNull() ?: return "로"
    val jong = (c - '가') % 28
    return if (c in '가'..'힣' && jong != 0 && jong != 8) "으로" else "로"
}

@Composable
fun PendingReviewScreen(
    budgetViewModel: BudgetViewModel,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repo = remember(context) { (context.applicationContext as BudgetApplication).container.autoEntryRepository }
    val pending by remember(repo) { repo.observePending() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val categories by budgetViewModel.categories.collectAsStateWithLifecycle()
    var deferred by remember { mutableStateOf(emptySet<Long>()) }
    // 항목과 고른 세부 id(null = 기록 안 함).
    var resolved by remember { mutableStateOf(emptyList<Pair<AutoEntryEntity, Long?>>()) }
    // 저장 중에는 다시 누르지 못하게 하고, 마지막 항목 직후 빈 화면이 깜빡이지 않게 한다.
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Room 의 새 목록이 오기 전 프레임에도 이미 정리한 항목을 세지 않는다.
    val live = pending.filter { p -> resolved.none { it.first.id == p.id } }
    val state = triageState(live, deferred, resolved.size)

    // 다음 카드가 자리 잡기 전의 두 번째 탭이 보지 못한 항목을 정리하지 않게 잠깐 막는다.
    fun lockedAction(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                action()
                delay(NEXT_ITEM_LOCK_MS)
            } finally {
                busy = false
            }
        }
    }

    fun resolve(leafId: Long?) {
        val entry = state.current ?: return
        lockedAction { resolved = resolved + repo.resolvePending(entry.id, leafId).map { it to leafId } }
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val current = state.current
        when {
            current != null -> {
                AppTopBar(title = stringResource(R.string.auto_pending_title), onBack = onBack) {
                    Text(
                        stringResource(R.string.auto_pending_progress, state.total, state.position),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = Space.lg),
                    )
                }
                LinearProgressIndicator(
                    progress = { resolved.size.toFloat() / state.total },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                )
                TriageBody(
                    budgetViewModel = budgetViewModel,
                    categories = categories,
                    current = current,
                    upNext = state.upNext,
                    suggestedCategoryId = repo::suggestedCategoryId,
                    onResolve = ::resolve,
                    onDefer = {
                        lockedAction {
                            deferred = deferred + current.id
                            if (live.all { it.id in deferred }) onBack()
                        }
                    },
                )
            }
            resolved.isNotEmpty() -> DoneContent(budgetViewModel, categories, resolved, onDone)
            busy -> Unit
            else -> {
                AppTopBar(title = stringResource(R.string.auto_pending_title), onBack = onBack)
                EmptyState(icon = Icons.Filled.PendingActions, title = stringResource(R.string.auto_pending_empty))
            }
        }
    }
}

private fun leafLabel(categories: List<CategoryEntity>, leafId: Long): String? {
    val leaf = categories.firstOrNull { it.id == leafId } ?: return null
    val parent = categories.firstOrNull { it.id == leaf.parentId }
    return if (parent == null) leaf.name else "${parent.name} · ${leaf.name}"
}

@Composable
private fun TriageBody(
    budgetViewModel: BudgetViewModel,
    categories: List<CategoryEntity>,
    current: AutoEntryEntity,
    upNext: List<AutoEntryEntity>,
    suggestedCategoryId: suspend (AutoEntryEntity) -> Long?,
    onResolve: (Long?) -> Unit,
    onDefer: () -> Unit,
) {
    val parentsByKind by budgetViewModel.parentsByKind.collectAsStateWithLifecycle()
    val childrenByParent by budgetViewModel.childrenByParent.collectAsStateWithLifecycle()
    var kind by remember(current.id) { mutableStateOf(defaultKindFor(current.direction)) }
    var showCategorySheet by remember(current.id) { mutableStateOf(false) }
    val noName = stringResource(R.string.auto_pending_no_name)

    val scroll = rememberScrollState()
    LaunchedEffect(current.id) { scroll.scrollTo(0) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll)
            .padding(horizontal = ScreenHorizontalPadding)
            .padding(top = Space.lg, bottom = pushedBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        AnimatedContent(
            targetState = current,
            contentKey = { it.id },
            transitionSpec = {
                (slideInHorizontally { it / 3 } + fadeIn()).togetherWith(slideOutHorizontally { -it / 3 } + fadeOut())
            },
            label = "pendingCard",
        ) { entry ->
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = cardBorder(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(Space.xl), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    val day = LocalDate.ofEpochDay(entry.occurredEpochDay).formatShortDayLabel()
                    val reason = reasonLabelRes(entry.reason)
                    Text(
                        if (reason == R.string.auto_reason_unclassified) day else day + " · " + stringResource(reason),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        entry.merchant.ifBlank { noName },
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(entry.amountMinor.formatWon(), style = MaterialTheme.typography.displaySmall)
                }
            }
        }

        if (upNext.isNotEmpty()) {
            Column {
                Text(
                    stringResource(R.string.auto_pending_up_next),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                upNext.forEach { e ->
                    Row(
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.md),
                    ) {
                        Text(
                            e.merchant.ifBlank { noName },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            e.amountMinor.formatWon(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Text(stringResource(R.string.auto_pending_prompt), style = MaterialTheme.typography.titleMedium)

        // 항목이 바뀌면 추천과 펼쳐 둔 대분류를 새로 시작한다.
        key(current.id) {
            val suggestedId = produceState<Long?>(null) {
                // 중복 같은 항목에 한 번 누르기 추천을 내밀지 않는다.
                if (reasonLabelRes(current.reason) != R.string.auto_reason_duplicate) value = suggestedCategoryId(current)
            }.value
            val suggestion = suggestedId?.let { id -> leafLabel(categories, id)?.let { id to it } }
            if (suggestion != null) {
                Surface(
                    onClick = { onResolve(suggestion.first) },
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(horizontal = Space.lg, vertical = Space.md)) {
                        Text(
                            stringResource(R.string.auto_pending_suggest, suggestion.second, roParticle(suggestion.second)),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.auto_pending_suggest_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            key(kind) {
                CategoryPicker(
                    parents = parentsByKind[kind].orEmpty(),
                    childrenByParent = childrenByParent,
                    selectedLeafId = null,
                    onLeafSelected = { onResolve(it.id) },
                    onMore = { showCategorySheet = true },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            KIND_SWITCH_LABELS.filterKeys { it != kind }.forEach { (k, res) ->
                Surface(
                    onClick = { kind = k },
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(stringResource(res), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onResolve(null) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(
                    stringResource(R.string.auto_skip),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onDefer, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.auto_pending_later), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }

    if (showCategorySheet) {
        CategorySearchSheet(
            parents = parentsByKind[kind].orEmpty(),
            childrenByParent = childrenByParent,
            selectedLeafId = null,
            onLeafSelected = {
                showCategorySheet = false
                onResolve(it.id)
            },
            onDismiss = { showCategorySheet = false },
        )
    }
}

private val KIND_SWITCH_LABELS = linkedMapOf(
    CategoryKind.EXPENSE to R.string.auto_pending_kind_expense,
    CategoryKind.INCOME to R.string.auto_pending_kind_income,
    CategoryKind.SAVINGS to R.string.auto_pending_kind_savings,
)

@Composable
private fun DoneContent(
    budgetViewModel: BudgetViewModel,
    categories: List<CategoryEntity>,
    resolved: List<Pair<AutoEntryEntity, Long?>>,
    onDone: () -> Unit,
) {
    val summary by budgetViewModel.homeSummary.collectAsStateWithLifecycle()
    val budget by budgetViewModel.monthlyBudgetMinor.collectAsStateWithLifecycle()
    val days by budgetViewModel.daysRemainingInPeriod.collectAsStateWithLifecycle()
    val recorded = resolved.mapNotNull { (e, leafId) -> leafId?.let { e to it } }
    val recordedTotal = recorded.sumOf { it.first.amountMinor }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = ScreenHorizontalPadding)
            .padding(bottom = pushedBottomPadding()),
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = Space.xxxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Box(
                Modifier.size(72.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(Modifier.height(Space.lg))
            Text(
                stringResource(R.string.auto_pending_done_title, resolved.size),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            if (recordedTotal > 0) {
                Text(
                    stringResource(R.string.auto_pending_done_total, recordedTotal.formatWon()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Space.lg))
            recorded.forEach { (entry, leafId) ->
                val leaf = categories.firstOrNull { it.id == leafId }
                TransactionRow(
                    categoryName = leaf?.name.orEmpty(),
                    parentCategoryName = categories.firstOrNull { it.id == leaf?.parentId }?.name,
                    memo = entry.merchant,
                    amountMinor = entry.amountMinor,
                    kind = CategoryKind.fromStorage(leaf?.kind ?: CategoryKind.EXPENSE.storage),
                )
            }
            budget?.takeIf { it > 0 }?.let { b ->
                val remaining = budgetStatus(b, summary.totalExpenseMinor, days).remainingMinor
                Spacer(Modifier.height(Space.lg))
                Text(
                    stringResource(R.string.auto_pending_done_remaining),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    remaining.formatWon(),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (remaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Button(
            onClick = onDone,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(stringResource(R.string.auto_pending_done_back), style = MaterialTheme.typography.titleMedium)
        }
    }
}
