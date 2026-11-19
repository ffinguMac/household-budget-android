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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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

    companion object {
        const val KBANK = "com.kbankwith.smartbank"
        const val CARD = "com.fake.card"
        const val NPAY = "com.naverfin.payapp"
        const val T0 = 1_800_000_000_000L
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
