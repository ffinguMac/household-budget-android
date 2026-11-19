package com.householdbudget.app.data.repository

import androidx.room.withTransaction
import com.householdbudget.app.data.local.AppDatabase
import com.householdbudget.app.data.local.entity.AutoEntryEntity
import com.householdbudget.app.data.local.entity.AutoEntryStatus
import com.householdbudget.app.data.local.entity.CategoryEntity
import com.householdbudget.app.data.local.entity.MerchantRuleEntity
import com.householdbudget.app.data.local.entity.NotificationLogEntity
import com.householdbudget.app.data.local.entity.TransactionEntity
import com.householdbudget.app.data.local.entity.TransactionSource
import com.householdbudget.app.data.local.model.MerchantRuleRow
import com.householdbudget.app.domain.autoentry.AutoClassifier
import com.householdbudget.app.domain.autoentry.Decision
import com.householdbudget.app.domain.autoentry.Direction
import com.householdbudget.app.domain.autoentry.LearnedRule
import com.householdbudget.app.domain.autoentry.MerchantNormalizer
import com.householdbudget.app.domain.autoentry.NotificationParsers
import com.householdbudget.app.domain.autoentry.ReviewReason
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class IncomingNotification(
    val packageName: String,
    val key: String,
    val postTimeMs: Long,
    val title: String,
    val text: String,
    val bigText: String,
    val isGroupSummary: Boolean = false,
)

enum class HandleOutcome { NOT_WATCHED, DUPLICATE, LOGGED_ONLY, IGNORED, PENDING, REGISTERED }

class AutoEntryRepository(
    private val database: AppDatabase,
    private val watchedPackages: suspend () -> Set<String>,
    private val zoneId: ZoneId = ZoneId.of("Asia/Seoul"),
) {
    private val logDao = database.notificationLogDao()
    private val entryDao = database.autoEntryDao()
    private val ruleDao = database.merchantRuleDao()
    private val transactionDao = database.transactionDao()
    private val categoryDao = database.categoryDao()

    // 알림 콜백이 겹쳐 와도 "있는지 확인 → 넣기"가 끼어들지 않게 한다.
    private val mutex = Mutex()

    suspend fun handleNotification(n: IncomingNotification): HandleOutcome = mutex.withLock {
        val watched = watchedPackages()
        if (n.packageName !in watched) return@withLock HandleOutcome.NOT_WATCHED
        database.withTransaction { process(n, watched) }
    }

    private suspend fun process(n: IncomingNotification, watched: Set<String>): HandleOutcome {
        val pkg = n.packageName
        val body = n.bigText.ifEmpty { n.text }
        // 요약 알림은 자식 알림과 해시가 겹쳐 실제 결제를 DUPLICATE로 삼키지 않게 한다.
        val hash = sha256Hex((if (n.isGroupSummary) "summary\n" else "") + "$pkg\n${n.title}\n$body")
        if (logDao.countSimilar(pkg, hash, n.postTimeMs - ECHO_WINDOW_MS, n.postTimeMs + ECHO_WINDOW_MS) > 0) {
            return HandleOutcome.DUPLICATE
        }
        val logId = logDao.insert(
            NotificationLogEntity(
                packageName = pkg, notificationKey = n.key, postTimeMs = n.postTimeMs,
                title = n.title, body = body, contentHash = hash,
            ),
        )
        if (logId == -1L) return HandleOutcome.DUPLICATE
        if (n.isGroupSummary) return HandleOutcome.LOGGED_ONLY

        val parsed = NotificationParsers.parse(pkg, n.title, body) ?: return HandleOutcome.LOGGED_ONLY
        // 같은 알림이 제자리에서 갱신(잔액 덧붙음 등)되면 해시가 달라도 같은 거래로 본다.
        // 앞 문구를 품지 않으면 알림 id를 하나만 쓰는 앱의 다른 결제일 수 있어 확인 필요로 보낸다.
        // shortcut: id 고정 앱이 똑같은 문구를 10초~5분 간격으로 두 번 보내면 재게시와 구분 못 해 DUPLICATE로 본다, 원본 로그에서 이런 일이 보이면 고친다.
        val sameKeyTexts = entryDao.textsWithSameNotificationKey(
            pkg, n.key, parsed.amountMinor, parsed.direction.name,
            n.postTimeMs - CROSS_APP_WINDOW_MS, n.postTimeMs + CROSS_APP_WINDOW_MS,
        )
        val newText = "${n.title.take(NotificationParsers.TITLE_CAP)}\n${body.take(NotificationParsers.BODY_CAP)}"
        if (sameKeyTexts.any { it in newText }) return HandleOutcome.DUPLICATE
        val key = MerchantNormalizer.normalize(parsed.merchant)
        val learned = if (key.isEmpty()) null else learnedRule(key, parsed.direction.name)
        val decision = AutoClassifier.classify(parsed, "${n.title}\n$body", pkg, watched, learned)

        val day = Instant.ofEpochMilli(n.postTimeMs).atZone(zoneId).toLocalDate().toEpochDay()
        val entry = AutoEntryEntity(
            logId = logId, packageName = pkg, occurredEpochDay = day, postedAtMs = n.postTimeMs,
            amountMinor = parsed.amountMinor, direction = parsed.direction.name,
            merchant = parsed.merchant, merchantKey = key, status = AutoEntryStatus.PENDING,
        )
        if (sameKeyTexts.isNotEmpty()) return pending(entry, ReviewReason.DUP_REPOST)
        val leaf = when (decision) {
            Decision.Ignore -> {
                entryDao.insert(entry.copy(status = AutoEntryStatus.IGNORED))
                return HandleOutcome.IGNORED
            }
            // 모르는 가맹점이라도 다른 앱의 같은 출금이면 DUP_CROSS_APP으로 둬야 일괄 적용 때 두 번 등록되지 않는다.
            is Decision.NeedsReview ->
                return pending(entry, if (crossAppDuplicate(entry)) ReviewReason.DUP_CROSS_APP else decision.reason)
            is Decision.RegisterById -> categoryDao.getById(decision.categoryId)
            is Decision.RegisterByPath ->
                decision.path.let { categoryDao.findLeafByPath(it.kind.storage, it.parent, it.child) }
        } ?: return pending(entry, ReviewReason.UNCLASSIFIED)

        if (crossAppDuplicate(entry)) return pending(entry, ReviewReason.DUP_CROSS_APP)
        if (transactionDao.countManualMatch(day, parsed.amountMinor, leaf.kind) > 0) {
            return pending(entry, ReviewReason.DUP_MANUAL)
        }
        register(entry.copy(id = entryDao.insert(entry)), leaf)
        return HandleOutcome.REGISTERED
    }

    private suspend fun crossAppDuplicate(e: AutoEntryEntity): Boolean =
        e.direction == Direction.OUT.name &&
            entryDao.countCrossAppOut(
                e.amountMinor, e.occurredEpochDay, e.packageName,
                e.postedAtMs - CROSS_APP_WINDOW_MS, e.postedAtMs + CROSS_APP_WINDOW_MS,
            ) > 0

    /** 규칙의 카테고리가 DB에 없으면 규칙이 없는 것으로 본다. */
    private suspend fun learnedRule(key: String, direction: String): LearnedRule? {
        val rule = ruleDao.getByKey(key, direction) ?: return null
        val categoryId = rule.categoryId ?: return LearnedRule(null)
        return categoryDao.getById(categoryId)?.let { LearnedRule(it.id) }
    }

    private suspend fun pending(entry: AutoEntryEntity, reason: ReviewReason): HandleOutcome {
        entryDao.insert(entry.copy(status = AutoEntryStatus.PENDING, reason = reason.name))
        return HandleOutcome.PENDING
    }

    /** 거래(출처 AUTO)를 넣고 항목을 REGISTERED로 바꾼다. 등록하는 모든 경로가 여기를 지난다. */
    private suspend fun register(entry: AutoEntryEntity, leaf: CategoryEntity) {
        val txId = transactionDao.insert(
            TransactionEntity(
                occurredEpochDay = entry.occurredEpochDay, amountMinor = entry.amountMinor,
                kind = leaf.kind, categoryId = leaf.id, memo = entry.merchant, source = TransactionSource.AUTO,
            ),
        )
        entryDao.update(entry.copy(status = AutoEntryStatus.REGISTERED, transactionId = txId))
    }

    fun observePending(): Flow<List<AutoEntryEntity>> = entryDao.observeByStatus(AutoEntryStatus.PENDING)

    fun observePendingCount(): Flow<Int> = entryDao.observeCountByStatus(AutoEntryStatus.PENDING)

    /** categoryId == null 이면 기록 안 함. */
    suspend fun resolvePending(entryId: Long, categoryId: Long?) = mutex.withLock {
        database.withTransaction {
            val entry = entryDao.getById(entryId)?.takeIf { it.status == AutoEntryStatus.PENDING }
                ?: return@withTransaction
            val leaf = categoryId?.let { categoryDao.getById(it) ?: error("Category not found: $it") }
            val apply: suspend (AutoEntryEntity) -> Unit = { e ->
                if (leaf == null) entryDao.update(e.copy(status = AutoEntryStatus.IGNORED)) else register(e, leaf)
            }
            apply(entry)
            if (entry.merchantKey.isEmpty() || entry.reason !in LEARNABLE_REASONS) return@withTransaction
            upsertRule(entry, categoryId)
            entryDao.listPendingByMerchantKey(entry.merchantKey, entry.direction)
                .filter { it.reason in LEARNABLE_REASONS }
                .forEach { apply(it) }
        }
    }

    private suspend fun upsertRule(entry: AutoEntryEntity, categoryId: Long?) {
        ruleDao.upsert(
            MerchantRuleEntity(
                merchantKey = entry.merchantKey, direction = entry.direction, displayName = entry.merchant,
                categoryId = categoryId, updatedAtMs = System.currentTimeMillis(),
            ),
        )
    }

    fun observeRules(): Flow<List<MerchantRuleRow>> = ruleDao.observeAll()

    suspend fun deleteRule(merchantKey: String, direction: String) = ruleDao.deleteByKey(merchantKey, direction)

    suspend fun learnFromCorrection(transactionId: Long, newCategoryId: Long) {
        val entry = entryDao.findByTransactionId(transactionId) ?: return
        if (entry.merchantKey.isNotEmpty()) upsertRule(entry, newCategoryId)
    }

    suspend fun purgeOldLogs(nowMs: Long = System.currentTimeMillis()): Int =
        logDao.deleteOlderThan(nowMs - TimeUnit.DAYS.toMillis(LOG_RETENTION_DAYS))

    private fun sha256Hex(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        const val ECHO_WINDOW_MS = 10_000L
        const val CROSS_APP_WINDOW_MS = 5 * 60_000L
        const val LOG_RETENTION_DAYS = 90L

        private val LEARNABLE_REASONS = setOf(ReviewReason.UNCLASSIFIED.name, ReviewReason.UNKNOWN_DIRECTION.name)
    }
}
