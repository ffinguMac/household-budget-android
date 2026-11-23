package com.householdbudget.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.householdbudget.app.data.local.AppDatabase
import com.householdbudget.app.data.local.entity.AutoEntryEntity
import com.householdbudget.app.data.local.entity.AutoEntryStatus
import com.householdbudget.app.data.local.entity.NotificationLogEntity
import com.householdbudget.app.data.local.entity.TransactionEntity
import com.householdbudget.app.data.local.entity.TransactionSource
import com.householdbudget.app.data.preferences.UserPreferencesRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AutoEntryRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repo: AutoEntryRepository
    private var watched = setOf(KBANK, CARD)

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        AppDatabase.seedCategoriesIfEmpty(db.categoryDao())
        repo = AutoEntryRepository(db, { watched })
    }

    @After fun tearDown() = db.close()

    private fun n(pkg: String, title: String, body: String, key: String = "k1", at: Long = T0) =
        IncomingNotification(pkg, key, at, title, body, "")

    private suspend fun handle(x: IncomingNotification) = repo.handleNotification(x)

    private suspend fun leafId(kind: String, parent: String, child: String): Long =
        db.categoryDao().findLeafByPath(kind, parent, child)!!.id

    private fun count(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    private fun txCount() = count("transactions")

    private fun strings(sql: String): List<String> =
        db.openHelper.readableDatabase.query(sql).use { c -> List(c.count) { c.moveToNext(); c.getString(0) } }

    private fun logTexts() = strings("SELECT title || body FROM notification_log")

    private fun logHashes() = strings("SELECT content_hash FROM notification_log")

    private suspend fun pending(): List<AutoEntryEntity> = repo.observePending().first()

    private suspend fun lastTx(): TransactionEntity =
        db.transactionDao().getById(
            db.openHelper.readableDatabase.query("SELECT MAX(id) FROM transactions").use { it.moveToFirst(); it.getLong(0) },
        )!!

    private suspend fun kimOut(key: String = "k1", at: Long = T0, amount: String = "30,000") =
        handle(n(KBANK, "출금 ${amount}원", "김가짜 | 가짜통장(1234)", key, at))

    private suspend fun kimIn(key: String = "k3", at: Long = T0 + 3_600_000) =
        handle(n(KBANK, "입금 50,000원", "김가짜 | 가짜통장(1234)", key, at))

    @Test fun notWatched_storesNothing() = runBlocking {
        assertEquals(HandleOutcome.NOT_WATCHED, handle(n("com.other.app", "가짜", "승인 4,500원\n가짜커피")))
        assertEquals(0, count("notification_log"))
        assertEquals(0, count("auto_entries"))
        assertEquals(0, txCount())
    }

    @Test fun noAmount_isLoggedOnly() = runBlocking {
        assertEquals(HandleOutcome.LOGGED_ONLY, handle(n(KBANK, "로그인 알림", "새 기기에서 로그인했어요")))
        assertEquals(1, count("notification_log"))
        assertEquals(0, count("auto_entries"))
    }

    @Test fun groupSummary_isLoggedOnly() = runBlocking {
        val summary = IncomingNotification(CARD, "k1", T0, "가짜카드", "승인 4,500원\n가짜커피", "", isGroupSummary = true)
        assertEquals(HandleOutcome.LOGGED_ONLY, handle(summary))
        assertEquals(1, count("notification_log"))
        assertEquals(0, count("auto_entries"))
        assertEquals(0, txCount())
    }

    @Test fun groupSummaryFirst_doesNotSwallowChild() = runBlocking {
        val summary = IncomingNotification(CARD, "s1", T0, "가짜카드", "승인 4,500원\n가짜커피", "", isGroupSummary = true)
        assertEquals(HandleOutcome.LOGGED_ONLY, handle(summary))
        assertEquals(HandleOutcome.REGISTERED, handle(n(CARD, "가짜카드", "승인 4,500원\n가짜커피", "k1", T0 + 1_000)))
        assertEquals(1, txCount())
    }

    @Test fun builtinMerchant_isRegisteredAsAutoTransaction() = runBlocking {
        assertEquals(HandleOutcome.REGISTERED, handle(n(CARD, "가짜카드", "승인 4,500원\n가짜커피 강남점")))
        assertEquals(1, txCount())
        val tx = lastTx()
        assertEquals(4500L, tx.amountMinor)
        assertEquals("EXPENSE", tx.kind)
        assertEquals(leafId("EXPENSE", "식비", "카페"), tx.categoryId)
        assertEquals("가짜커피 강남점", tx.memo)
        assertEquals(TransactionSource.AUTO, tx.source)
        assertEquals(Instant.ofEpochMilli(T0).atZone(SEOUL).toLocalDate().toEpochDay(), tx.occurredEpochDay)
    }

    @Test fun exactRedelivery_isDuplicate() = runBlocking {
        val x = n(CARD, "가짜카드", "승인 4,500원\n가짜커피")
        assertEquals(HandleOutcome.REGISTERED, handle(x))
        assertEquals(HandleOutcome.DUPLICATE, handle(x))
        assertEquals(1, txCount())
        assertEquals(1, count("notification_log"))
    }

    @Test fun sameContentWithin10s_isDuplicate() = runBlocking {
        handle(n(CARD, "가짜카드", "승인 4,500원\n가짜커피"))
        assertEquals(HandleOutcome.DUPLICATE, handle(n(CARD, "가짜카드", "승인 4,500원\n가짜커피", "k2", T0 + 9_000)))
        assertEquals(1, txCount())
    }

    @Test fun sameContentAfter10s_isNewPayment() = runBlocking {
        handle(n(CARD, "가짜카드", "승인 4,500원\n가짜커피"))
        assertEquals(HandleOutcome.REGISTERED, handle(n(CARD, "가짜카드", "승인 4,500원\n가짜커피", "k2", T0 + 11_000)))
        assertEquals(2, txCount())
    }

    @Test fun sameKeyUpdatedText_within5Min_isDuplicate() = runBlocking {
        assertEquals(HandleOutcome.REGISTERED, handle(n(CARD, "가짜카드", "승인 4,500원 가짜커피")))
        assertEquals(HandleOutcome.DUPLICATE, handle(n(CARD, "가짜카드", "승인 4,500원 가짜커피 잔액 10,000원", "k1", T0 + 30_000)))
        assertEquals(1, txCount())
        assertEquals(2, count("notification_log"))
        assertEquals(1, count("auto_entries"))
    }

    @Test fun sameKeyDifferentPayment_within5Min_goesToReview() = runBlocking {
        assertEquals(HandleOutcome.PENDING, kimOut(amount = "10,000"))
        repo.resolvePending(pending().single().id, leafId("EXPENSE", "유동 지출", "기본"))
        // 알림 id를 하나만 쓰는 앱: 키는 같지만 다른 결제다.
        val lee = n(KBANK, "출금 10,000원", "이가짜 | 가짜통장(1234)", "k1", T0 + 30_000)
        assertEquals(HandleOutcome.PENDING, handle(lee))
        assertEquals(1, txCount())
        assertEquals(2, count("auto_entries"))
        assertEquals("DUP_REPOST", pending().single().reason)
    }

    @Test fun differentKeySameText_afterEchoWindow_isStillTwoPayments() = runBlocking {
        handle(n(CARD, "가짜카드", "승인 4,500원 가짜커피"))
        assertEquals(HandleOutcome.REGISTERED, handle(n(CARD, "가짜카드", "승인 4,500원 가짜커피", "k2", T0 + 30_000)))
        assertEquals(2, txCount())
    }

    @Test fun unknownMerchant_goesPending_thenLearnsRule() = runBlocking {
        assertEquals(HandleOutcome.PENDING, kimOut())
        assertEquals(0, txCount())
        val p = pending().single()
        assertEquals("UNCLASSIFIED", p.reason)
        val flexible = leafId("EXPENSE", "유동 지출", "기본")
        repo.resolvePending(p.id, flexible)
        assertEquals(1, txCount())
        assertEquals("김가짜", lastTx().memo)
        assertEquals(TransactionSource.AUTO, lastTx().source)
        assertEquals(1, db.merchantRuleDao().count())

        assertEquals(HandleOutcome.REGISTERED, kimOut("k2", T0 + 86_400_000, "20,000"))
        assertEquals(flexible, lastTx().categoryId)
    }

    @Test fun resolveAsIgnore_learnsIgnoreRule() = runBlocking {
        kimOut()
        val p = pending().single()
        repo.resolvePending(p.id, null)
        assertEquals(0, txCount())
        assertEquals(AutoEntryStatus.IGNORED, db.autoEntryDao().getById(p.id)!!.status)
        assertEquals(HandleOutcome.IGNORED, kimOut("k2", T0 + 86_400_000))
        assertEquals(0, txCount())
    }

    @Test fun ruleForOut_doesNotApplyToIncoming() = runBlocking {
        val flexible = leafId("EXPENSE", "유동 지출", "기본")
        val allowance = leafId("INCOME", "수입", "용돈")
        kimOut()
        repo.resolvePending(pending().single().id, flexible)
        assertEquals(HandleOutcome.PENDING, kimIn())
        assertEquals(1, txCount())

        repo.resolvePending(pending().single().id, allowance)
        assertEquals(2, db.merchantRuleDao().count())
        assertEquals(HandleOutcome.REGISTERED, kimOut("k4", T0 + 86_400_000))
        assertEquals(flexible, lastTx().categoryId)
        assertEquals(HandleOutcome.REGISTERED, kimIn("k5", T0 + 2 * 86_400_000))
        assertEquals(allowance, lastTx().categoryId)
    }

    @Test fun resolvingOne_doesNotTouchPendingOfOtherDirection() = runBlocking {
        kimOut()
        kimIn()
        val out = pending().single { it.direction == "OUT" }
        repo.resolvePending(out.id, leafId("EXPENSE", "유동 지출", "기본"))
        assertEquals("IN", pending().single().direction)
    }

    @Test fun emptyMerchant_resolves_withoutRule() = runBlocking {
        assertEquals(HandleOutcome.PENDING, handle(n(KBANK, "출금 3,000원", "")))
        repo.resolvePending(pending().single().id, leafId("EXPENSE", "유동 지출", "기본"))
        assertEquals(1, txCount())
        assertEquals(0, db.merchantRuleDao().count())
    }

    @Test fun resolvingOne_appliesToOtherPendingOfSameMerchant() = runBlocking {
        kimOut()
        kimOut("k2", T0 + 86_400_000)
        assertEquals(2, pending().size)
        repo.resolvePending(pending().first().id, leafId("EXPENSE", "유동 지출", "기본"))
        assertEquals(2, txCount())
        assertEquals(0, pending().size)
        assertEquals(2, count("auto_entries WHERE status = 'REGISTERED'"))
    }

    @Test fun resolvePending_returnsTargetThenCascaded() = runBlocking {
        kimOut()
        kimOut("k2", T0 + 86_400_000)
        val target = pending().last()
        val resolved = repo.resolvePending(target.id, leafId("EXPENSE", "유동 지출", "기본"))
        assertEquals(2, resolved.size)
        assertEquals(target.id, resolved.first().id)
        assertEquals(0, pending().size)
    }

    @Test fun resolvePending_skip_returnsCascadedToo() = runBlocking {
        kimOut()
        kimOut("k2", T0 + 86_400_000)
        assertEquals(2, repo.resolvePending(pending().first().id, null).size)
    }

    @Test fun resolvePending_duplicateReason_returnsOnlyTarget() = runBlocking {
        insertManualCoffee()
        handle(n(CARD, "가짜카드", "승인 4,500원\n가짜커피"))
        val p = pending().single()
        assertEquals(listOf(p.id), repo.resolvePending(p.id, null).map { it.id })
    }

    @Test fun resolvePending_notPending_returnsEmpty() = runBlocking {
        kimOut()
        val id = pending().single().id
        repo.resolvePending(id, null)
        assertEquals(emptyList<AutoEntryEntity>(), repo.resolvePending(id, null))
        assertEquals(emptyList<AutoEntryEntity>(), repo.resolvePending(9_999L, null))
    }

    @Test fun suggestedCategoryId_returnsLearnedRule_orNull() = runBlocking {
        kimOut()
        val first = pending().single()
        assertNull(repo.suggestedCategoryId(first))
        val flexible = leafId("EXPENSE", "유동 지출", "기본")
        repo.resolvePending(first.id, flexible)
        assertEquals(flexible, repo.suggestedCategoryId(first))
        assertNull(repo.suggestedCategoryId(first.copy(merchantKey = "")))
        assertNull(repo.suggestedCategoryId(first.copy(merchantKey = "모르는가게")))
    }

    @Test fun crossApp_sameAmountWithin5Min_secondGoesPending() = runBlocking {
        assertEquals(HandleOutcome.REGISTERED, handle(n(CARD, "가짜카드", "승인 12,000원\n가짜마트")))
        assertEquals(HandleOutcome.PENDING, handle(n(KBANK, "출금 12,000원", "가짜마트 | 가짜통장(1234)", "k2", T0 + 120_000)))
        assertEquals("DUP_CROSS_APP", pending().single().reason)
        assertEquals(1, txCount())
    }

    @Test fun crossApp_unknownMerchant_resolvingOne_createsOneTransaction() = runBlocking {
        assertEquals(HandleOutcome.PENDING, handle(n(CARD, "가짜카드", "승인 7,000원\n김가짜")))
        assertEquals(HandleOutcome.PENDING, kimOut("k2", T0 + 120_000, "7,000"))
        assertEquals("DUP_CROSS_APP", pending().single { it.packageName == KBANK }.reason)
        repo.resolvePending(pending().single { it.packageName == CARD }.id, leafId("EXPENSE", "유동 지출", "기본"))
        assertEquals(1, txCount())
        assertEquals(KBANK, pending().single().packageName)
    }

    @Test fun crossApp_after5Min_isRegistered() = runBlocking {
        handle(n(CARD, "가짜카드", "승인 12,000원\n가짜마트"))
        assertEquals(HandleOutcome.REGISTERED, handle(n(KBANK, "출금 12,000원", "가짜마트 | 가짜통장(1234)", "k2", T0 + 360_000)))
        assertEquals(2, txCount())
    }

    @Test fun sameApp_sameAmountTwice_bothRegistered() = runBlocking {
        assertEquals(HandleOutcome.REGISTERED, handle(n(CARD, "가짜카드", "승인 4,500원\n가짜커피")))
        assertEquals(HandleOutcome.REGISTERED, handle(n(CARD, "가짜카드", "승인 4,500원\n가짜카페", "k2", T0 + 60_000)))
        assertEquals(2, txCount())
    }

    private suspend fun insertManualCoffee() {
        db.transactionDao().insert(
            TransactionEntity(
                occurredEpochDay = Instant.ofEpochMilli(T0).atZone(SEOUL).toLocalDate().toEpochDay(),
                amountMinor = 4500, kind = "EXPENSE", categoryId = leafId("EXPENSE", "식비", "식사"),
            ),
        )
    }

    @Test fun manualTransactionSameDayAmountKind_goesPending() = runBlocking {
        insertManualCoffee()
        assertEquals(HandleOutcome.PENDING, handle(n(CARD, "가짜카드", "승인 4,500원\n가짜커피")))
        assertEquals("DUP_MANUAL", pending().single().reason)
        assertEquals(1, txCount())
    }

    @Test fun resolvingDuplicate_doesNotLearnRule() = runBlocking {
        insertManualCoffee()
        handle(n(CARD, "가짜카드", "승인 4,500원\n가짜커피"))
        repo.resolvePending(pending().single().id, null)
        assertEquals(0, db.merchantRuleDao().count())
        assertEquals(HandleOutcome.REGISTERED, handle(n(CARD, "가짜카드", "승인 4,500원\n가짜커피", "k2", T0 + 86_400_000)))
    }

    @Test fun charge_ignored_andPayAppPaymentStillRegistered() = runBlocking {
        watched = setOf(KBANK, NPAY)
        assertEquals(HandleOutcome.IGNORED, handle(n(KBANK, "출금 10,000원", "네이버페이 충전 | 가짜통장(1234)")))
        assertEquals(HandleOutcome.REGISTERED, handle(n(NPAY, "가짜페이", "결제 10,000원\n가짜커피", "k2", T0 + 60_000)))
        assertEquals(1, txCount())
    }

    @Test fun charge_recordedWhenPayAppNotWatched() = runBlocking {
        watched = setOf(KBANK)
        assertEquals(HandleOutcome.REGISTERED, handle(n(KBANK, "출금 10,000원", "네이버페이 충전 | 가짜통장(1234)")))
        assertEquals(leafId("EXPENSE", "쇼핑", "간편결제 충전"), lastTx().categoryId)
    }

    @Test fun concurrentIdenticalNotifications_createOneTransaction() = runBlocking {
        val x = n(CARD, "가짜카드", "승인 4,500원\n가짜커피")
        val outcomes = List(20) { async(Dispatchers.Default) { repo.handleNotification(x) } }.awaitAll()
        assertEquals(1, outcomes.count { it == HandleOutcome.REGISTERED })
        assertEquals(19, outcomes.count { it == HandleOutcome.DUPLICATE })
        assertEquals(1, txCount())
    }

    @Test fun purgeOldLogs_keeps90Days() = runBlocking {
        val now = T0
        listOf(91L, 89L).forEachIndexed { i, days ->
            db.notificationLogDao().insert(
                NotificationLogEntity(
                    packageName = KBANK, notificationKey = "k$i", postTimeMs = now - TimeUnit.DAYS.toMillis(days),
                    title = "t", body = "b", contentHash = "h$i",
                ),
            )
        }
        assertEquals(1, repo.purgeOldLogs(now))
        assertEquals(1, count("notification_log"))
    }

    @Test fun unparsedNotification_storesHashOnly() = runBlocking {
        assertEquals(HandleOutcome.LOGGED_ONLY, handle(n(KBANK, "가짜 이벤트", "금액 없는 광고 문구")))
        assertEquals(listOf(""), logTexts())
        assertEquals(64, logHashes().single().length)
    }

    @Test fun groupSummary_storesHashOnly() = runBlocking {
        handle(n(CARD, "가짜카드", "승인 4,500원\n가짜커피").copy(isGroupSummary = true))
        assertEquals(listOf(""), logTexts())
    }

    @Test fun purge_keepsEntryTextFor30Days_thenClears() = runBlocking {
        handle(n(CARD, "가짜카드", "승인 4,500원 가짜커피"))
        repo.purgeOldLogs(T0 + 60_000)
        assertEquals(HandleOutcome.DUPLICATE,
            handle(n(CARD, "가짜카드", "승인 4,500원 가짜커피 잔액 10,000원", "k1", T0 + 90_000)))
        repo.purgeOldLogs(T0 + TimeUnit.DAYS.toMillis(29))                 // 파서 조정용으로 원문 유지
        assertEquals(listOf("가짜카드승인 4,500원 가짜커피"), logTexts().filter { it.isNotEmpty() })
        repo.purgeOldLogs(T0 + AutoEntryRepository.LOG_TEXT_RETENTION_MS + 1)
        assertEquals(setOf(""), logTexts().toSet())
        assertEquals(1, txCount())                                       // 거래는 건드리지 않는다
    }

    @Test fun purge_clearsLegacyTextOfLogsWithoutEntry() = runBlocking {
        db.notificationLogDao().insert(
            NotificationLogEntity(
                packageName = KBANK, notificationKey = "old", postTimeMs = T0,
                title = "가짜 광고", body = "옛 원문", contentHash = "h",
            ),
        )
        repo.purgeOldLogs(T0 + 60_000)
        assertEquals(listOf(""), logTexts())
    }

    @Test fun purge_deletesResolvedEntriesAfter90Days_keepsPendingAndTransactions() = runBlocking {
        handle(n(CARD, "가짜카드", "승인 4,500원\n가짜커피"))
        kimOut("k2")
        repo.purgeOldLogs(T0 + TimeUnit.DAYS.toMillis(91))
        assertEquals(1, count("auto_entries")); assertEquals(1, pending().size); assertEquals(1, txCount())
    }

    @Test fun notCandidate_returnsWithoutWaitingForMutex() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val exactCalls = AtomicInteger()
        repo = AutoEntryRepository(db, { exactCalls.incrementAndGet(); gate.await(); watched }, { it in watched })
        val held = async(Dispatchers.Default) { handle(n(CARD, "가짜카드", "승인 4,500원\n가짜커피")) }
        while (exactCalls.get() == 0) delay(5)
        assertEquals(HandleOutcome.NOT_WATCHED,
            withTimeout(1_000) { handle(n("com.other.app", "가짜", "승인 4,500원\n가짜커피")) })
        assertEquals(1, exactCalls.get())
        gate.complete(Unit); assertEquals(HandleOutcome.REGISTERED, held.await())
    }

    @Test fun restoreIgnored_chargeGoesToReview_thenRegisters_withoutLearningRule() = runBlocking {
        watched = setOf(KBANK, NPAY)
        assertEquals(HandleOutcome.IGNORED, handle(n(KBANK, "출금 10,000원", "네이버페이 충전 | 가짜통장(1234)")))
        val ignored = repo.observeIgnored().first().single()
        repo.restoreIgnored(ignored.id)
        assertEquals("RESTORED", pending().single().reason)
        assertEquals(emptyList<AutoEntryEntity>(), repo.observeIgnored().first())
        repo.resolvePending(ignored.id, leafId("EXPENSE", "쇼핑", "간편결제 충전"))
        assertEquals(1, txCount()); assertEquals(10_000L, lastTx().amountMinor)
        assertEquals(0, repo.observeRules().first().size)              // 충전 문구를 규칙으로 배우지 않는다
        assertEquals(HandleOutcome.IGNORED,
            handle(n(KBANK, "출금 10,000원", "네이버페이 충전 | 가짜통장(1234)", "k9", T0 + 86_400_000)))
    }

    @Test fun restoredCharge_correctingCategory_doesNotLearnRule() = runBlocking {
        watched = setOf(KBANK, NPAY)
        handle(n(KBANK, "출금 10,000원", "네이버페이 충전 | 가짜통장(1234)"))
        val id = repo.observeIgnored().first().single().id
        repo.restoreIgnored(id)
        repo.resolvePending(id, leafId("EXPENSE", "쇼핑", "간편결제 충전"))
        repo.learnFromCorrection(lastTx().id, leafId("EXPENSE", "식비", "간식"))
        assertEquals(0, repo.observeRules().first().size)
        assertEquals(HandleOutcome.IGNORED,
            handle(n(KBANK, "출금 10,000원", "네이버페이 충전 | 가짜통장(1234)", "k9", T0 + 86_400_000)))
    }

    @Test fun blankedSameKeyLog_isNotTreatedAsContained() = runBlocking {
        handle(n(CARD, "가짜카드", "승인 4,500원 가짜커피"))
        repo.purgeOldLogs(T0 + AutoEntryRepository.LOG_TEXT_RETENTION_MS + 1)   // 원문만 비고 행은 남는다
        assertEquals(HandleOutcome.PENDING,
            handle(n(CARD, "가짜카드", "승인 4,500원 가짜커피 잔액 10,000원", "k1", T0 + 90_000)))
        assertEquals("DUP_REPOST", pending().single().reason)
    }

    @Test fun restoreIgnored_onRegisteredEntry_doesNothing() = runBlocking {
        handle(n(CARD, "가짜카드", "승인 4,500원\n가짜커피"))
        val id = db.openHelper.readableDatabase.query("SELECT id FROM auto_entries").use { it.moveToFirst(); it.getLong(0) }
        repo.restoreIgnored(id)
        assertEquals(0, pending().size); assertEquals(1, txCount())
    }

    @Test fun learnFromCorrection_upsertsRuleForAutoTransaction() = runBlocking {
        handle(n(CARD, "가짜카드", "승인 4,500원\n가짜커피"))
        val snack = leafId("EXPENSE", "식비", "간식")
        repo.learnFromCorrection(lastTx().id, snack)
        assertEquals(snack, repo.observeRules().first().single().categoryId)
        assertEquals(HandleOutcome.REGISTERED, handle(n(CARD, "가짜카드", "승인 4,500원\n가짜커피", "k2", T0 + 86_400_000)))
        assertEquals(snack, lastTx().categoryId)
    }

    @Test fun deleteRule_returnsMerchantToBuiltinOrPending() = runBlocking {
        kimOut()
        repo.resolvePending(pending().single().id, leafId("EXPENSE", "유동 지출", "기본"))
        repo.deleteRule("김가짜", "OUT")
        assertEquals(HandleOutcome.PENDING, kimOut("k2", T0 + 86_400_000))
    }

    @Test fun budgetRepository_editingAutoCategory_callsHook() = runBlocking {
        val calls = mutableListOf<Pair<Long, Long>>()
        val budget = BudgetRepository(
            db, db.transactionDao(), db.categoryDao(), db.archivedPeriodDao(), db.categoryBudgetDao(),
            UserPreferencesRepository(context), onAutoRecategorized = { t, c -> calls += t to c },
        )
        handle(n(CARD, "가짜카드", "승인 4,500원\n가짜커피"))
        val tx = lastTx()
        val day = LocalDate.ofEpochDay(tx.occurredEpochDay)
        budget.updateTransaction(tx.id, day, tx.amountMinor, tx.categoryId, "메모만")
        assertEquals(emptyList<Pair<Long, Long>>(), calls)
        val snack = leafId("EXPENSE", "식비", "간식")
        budget.updateTransaction(tx.id, day, tx.amountMinor, snack, "")
        assertEquals(listOf(tx.id to snack), calls)
        assertEquals(TransactionSource.AUTO, lastTx().source)
    }

    private suspend fun cafeOut(at: Long = T0) = handle(n(CARD, "가짜카드", "승인 100,000원\n가짜커피", "c1", at))

    private suspend fun bankIn(at: Long = T0 + 60_000, amount: String = "100,000") =
        handle(n(KBANK, "입금 ${amount}원", "김가짜 | 가짜통장(1234)", "k9", at))

    private suspend fun ignored(): List<AutoEntryEntity> = repo.observeIgnored().first()

    @Test fun selfTransfer_outThenIn_bothSkipped_andRegisteredTxDeleted() = runBlocking {
        assertEquals(HandleOutcome.REGISTERED, cafeOut())
        assertEquals(HandleOutcome.IGNORED, bankIn())
        assertEquals(0, txCount())
        assertEquals(emptyList<AutoEntryEntity>(), pending())
        assertEquals(listOf("SELF_TRANSFER", "SELF_TRANSFER"), ignored().map { it.reason })
        assertEquals(listOf<Long?>(null, null), ignored().map { it.transactionId })
        repo.restoreIgnored(ignored().single { it.packageName == KBANK }.id)
        assertEquals("RESTORED", pending().single().reason)
        assertEquals(0, db.merchantRuleDao().count())
    }

    @Test fun selfTransfer_inThenOut_bothSkipped() = runBlocking {
        assertEquals(HandleOutcome.PENDING, bankIn(at = T0))
        assertEquals(HandleOutcome.IGNORED, handle(n(CARD, "가짜은행", "출금 100,000원\n김가짜", "c1", T0 + 150_000)))
        assertEquals(0, txCount())
        assertEquals(emptyList<AutoEntryEntity>(), pending())
        assertEquals(2, ignored().count { it.reason == "SELF_TRANSFER" })
    }

    @Test fun selfTransfer_withPaymentAppEcho_recordsNothing_eitherOrder() = runBlocking {
        watched = setOf(KBANK, CARD, NPAY)
        val echo = { at: Long -> runBlocking { handle(n(NPAY, "가짜페이", "출금 100,000원\n김가짜", "n$at", at)) } }
        cafeOut()
        assertEquals(HandleOutcome.PENDING, echo(T0 + 10_000))
        assertEquals(HandleOutcome.IGNORED, bankIn())
        assertEquals(0, txCount())
        assertEquals(emptyList<AutoEntryEntity>(), pending())
        assertEquals(3, ignored().count { it.reason == "SELF_TRANSFER" })

        cafeOut(T0 + 86_400_000)
        bankIn(T0 + 86_400_000 + 30_000)
        assertEquals(HandleOutcome.IGNORED, echo(T0 + 86_400_000 + 60_000))
        assertEquals(0, txCount())
        assertEquals(emptyList<AutoEntryEntity>(), pending())
    }

    @Test fun selfTransfer_nonCases_keepTodaysBehaviour() = runBlocking {
        kimOut(amount = "100,000")
        assertEquals(HandleOutcome.PENDING, bankIn())
        assertEquals(2, pending().size)
    }

    @Test fun selfTransfer_differentAmountOrOver3Min_notMatched() = runBlocking {
        cafeOut()
        assertEquals(HandleOutcome.PENDING, bankIn(amount = "99,000"))
        assertEquals(HandleOutcome.PENDING, bankIn(at = T0 + 181_000 + 60_000))
        assertEquals(1, txCount())
        assertEquals(0, ignored().size)
    }

    companion object {
        const val KBANK = "com.kbankwith.smartbank"
        const val CARD = "com.fake.card"
        const val NPAY = "com.naverfin.payapp"
        const val T0 = 1_800_000_000_000L
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
