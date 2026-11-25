package com.householdbudget.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.householdbudget.app.data.local.AppDatabase
import com.householdbudget.app.data.local.entity.ArchivedPeriodEntity
import com.householdbudget.app.data.local.entity.AutoEntryEntity
import com.householdbudget.app.data.local.entity.CashEntryEntity
import com.householdbudget.app.data.local.entity.CategoryBudgetEntity
import com.householdbudget.app.data.local.entity.CategoryEntity
import com.householdbudget.app.data.local.entity.MerchantRuleEntity
import com.householdbudget.app.data.local.entity.NotificationLogEntity
import com.householdbudget.app.data.local.entity.TransactionEntity
import com.householdbudget.app.data.local.entity.TransactionSource
import com.householdbudget.app.data.preferences.UserPreferencesRepository
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BackupRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var prefs: UserPreferencesRepository
    private lateinit var repo: BackupRepository
    private lateinit var dir: File
    private val lock = Mutex()
    private val now = Instant.parse("2026-11-25T00:00:00Z")

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        AppDatabase.seedCategoriesIfEmpty(db.categoryDao())
        prefs = UserPreferencesRepository(context)
        dir = File(context.filesDir, "backups-test").apply { deleteRecursively() }
        repo = newRepo()
        savedPrefs = BackupCodec.fromJson(repo.exportJson()).getOrThrow().prefs
        savedCursor = prefs.getLastSeenPeriodStartSnapshot()
    }

    private val restoredPrefs = mutableListOf<BackupPrefs>()

    private fun newRepo() = BackupRepository(
        context, db, prefs, lock, dir, "1.0", scope = CoroutineScope(Dispatchers.Default),
        onPrefsRestored = { restoredPrefs += it }, clock = { now },
    )

    private var savedCursor: Long? = null

    // DataStore는 프로세스에 하나라 다른 테스트 클래스로 새지 않게 되돌린다.
    private lateinit var savedPrefs: BackupPrefs

    @After fun tearDown() = runBlocking {
        db.close()
        dir.deleteRecursively()
        with(savedPrefs) {
            prefs.restoreBackupPrefs(paydayDom, monthlyBudgetMinor, reminderEnabled, reminderHour, darkTheme, savedCursor)
        }
    }

    private suspend fun parentId(name: String) = db.categoryDao().all().first { it.name == name && it.parentId == null }.id

    /** 사용자 세부 카테고리·예산·규칙·지난 기록·비상금·설정까지 채운다. */
    private suspend fun seed(): Long {
        val food = parentId("식비")
        val custom = db.categoryDao().insert(CategoryEntity(name = "빵집", kind = "EXPENSE", parentId = food, sortOrder = 99, icon = "🥐"))
        db.transactionDao().insert(TransactionEntity(occurredEpochDay = 20_000, amountMinor = 4_500, kind = "EXPENSE", categoryId = custom, memo = "크루아상"))
        db.transactionDao().insert(
            TransactionEntity(occurredEpochDay = 20_001, amountMinor = 12_000, kind = "EXPENSE", categoryId = custom, source = TransactionSource.AUTO),
        )
        db.categoryBudgetDao().upsert(CategoryBudgetEntity(custom, 50_000, enabled = false))
        db.merchantRuleDao().upsert(MerchantRuleEntity("빵집", "OUT", "동네빵집", custom, 1_000))
        db.merchantRuleDao().upsert(MerchantRuleEntity("가짜", "IN", "가짜", null, 2_000))
        db.archivedPeriodDao().insert(ArchivedPeriodEntity(startEpochDay = 19_990, endEpochDay = 20_020, totalIncomeMinor = 1, totalExpenseMinor = 2, totalSavingsMinor = 3, archivedAtEpochMs = 4))
        db.cashEntryDao().insert(CashEntryEntity(occurredEpochDay = 20_000, amountMinor = 30_000, memo = "용돈", createdAtMs = 5))
        prefs.setPaydayDom(10)
        prefs.setMonthlyBudgetMinor(1_500_000)
        prefs.setReminderEnabled(true)
        prefs.setReminderHour(7)
        prefs.setDarkTheme(true)
        return custom
    }

    /** 복원할 때와 똑같이 검사 → 복원. 검사에서 걸리면 아무것도 쓰지 않는다. */
    private suspend fun restoreText(text: String): Result<RestoreResult> =
        BackupCodec.fromJson(text).map { repo.restore(it) }

    private suspend fun scramble() {
        db.transactionDao().all().forEach { db.transactionDao().delete(it) }
        db.cashEntryDao().insert(CashEntryEntity(occurredEpochDay = 1, amountMinor = 7, createdAtMs = 1))
        prefs.setPaydayDom(25)
        prefs.setMonthlyBudgetMinor(null)
        prefs.setReminderEnabled(false)
        prefs.setReminderHour(21)
        prefs.setDarkTheme(false)
    }

    @Test fun roundTrip_restoresEverythingExactly() = runBlocking {
        seed()
        val json = repo.exportJson()
        scramble()
        assertEquals(RestoreResult.RESTORED, restoreText(json).getOrThrow())
        assertEquals(json, repo.exportJson())
        assertEquals(10, prefs.paydayDom.first())
        assertEquals(1_500_000L, prefs.monthlyBudgetMinor.first())
        assertEquals("빵집", db.categoryDao().all().single { it.icon == "🥐" }.name)
    }

    @Test fun export_hasHeaderAndLeavesOutNotificationsAndAppLock() = runBlocking {
        seed()
        prefs.setAppLockEnabled(true)
        val root = JSONObject(repo.exportJson())
        assertEquals("household-budget-backup", root.getString("format"))
        assertEquals(1, root.getInt("formatVersion"))
        assertEquals(9, root.getInt("dbVersion"))
        assertEquals("2026-11-25T00:00:00Z", root.getString("exportedAt"))
        assertFalse(root.has("autoEntries") || root.has("notificationLog"))
        assertFalse(root.getJSONObject("prefs").has("appLockEnabled"))
        prefs.setAppLockEnabled(false)
    }

    private fun autoEntry(txId: Long?, amount: Long, status: String) = AutoEntryEntity(
        logId = 1, packageName = "p", occurredEpochDay = 20_000, postedAtMs = 1, amountMinor = amount, direction = "OUT",
        merchant = "m", merchantKey = "m", status = status, transactionId = txId,
    )

    @Test fun restore_keepsLogAndPending_andUnlinksAutoEntriesWhoseTransactionChanged() = runBlocking {
        seed()
        val autoTx = db.transactionDao().all().single { it.source == TransactionSource.AUTO }
        val manualTx = db.transactionDao().all().single { it.source == TransactionSource.MANUAL }
        val json = repo.exportJson()
        db.notificationLogDao().insert(NotificationLogEntity(packageName = "p", notificationKey = "k", postTimeMs = 1, title = "t", body = "b", contentHash = "h"))
        val pending = db.autoEntryDao().insert(autoEntry(null, 1_000, "PENDING"))
        val linked = db.autoEntryDao().insert(autoEntry(autoTx.id, autoTx.amountMinor, "REGISTERED"))
        val wrongTx = db.autoEntryDao().insert(autoEntry(manualTx.id, manualTx.amountMinor, "REGISTERED"))
        val gone = db.autoEntryDao().insert(autoEntry(9_999, 1, "REGISTERED"))
        restoreText(json).getOrThrow()
        assertEquals(1, db.notificationLogDao().count())
        assertEquals(1, db.autoEntryDao().observeCountByStatus("PENDING").first())
        assertNotNull(db.autoEntryDao().getById(pending))
        assertEquals(autoTx.id, db.autoEntryDao().getById(linked)?.transactionId)
        assertNull(db.autoEntryDao().getById(wrongTx)?.transactionId)
        assertNull(db.autoEntryDao().getById(gone)?.transactionId)
    }

    @Test fun restore_setsArchiveCursorAndResyncsReminder() = runBlocking {
        seed()
        val json = repo.exportJson()
        scramble()
        restoreText(json).getOrThrow()
        assertEquals(20_020L, prefs.getLastSeenPeriodStartSnapshot())
        assertEquals(7, restoredPrefs.last().reminderHour)
        assertTrue(restoredPrefs.last().reminderEnabled)
    }

    @Test fun dbVersionMatchesDatabase() {
        assertEquals(db.openHelper.readableDatabase.version, BackupCodec.DB_VERSION)
    }

    @Test fun asyncExportAndLoad_reportThroughStatus() = runBlocking {
        seed()
        val file = File(dir.apply { mkdirs() }, "out.json")
        repo.exportTo(Uri.fromFile(file))
        assertEquals(BackupStatus.Done(BackupOutcome.EXPORTED), repo.status.first { it is BackupStatus.Done })
        repo.dismissResult()
        repo.load(Uri.fromFile(file))
        val confirm = repo.status.first { it !is BackupStatus.Idle && it !is BackupStatus.Working } as BackupStatus.Confirm
        assertEquals(2, confirm.snapshot.transactions.size)
        repo.confirmRestore(confirm.snapshot)
        assertEquals(BackupStatus.Done(BackupOutcome.RESTORED), repo.status.first { it is BackupStatus.Done })
        assertNotNull(repo.safetyCopy.value)
        file.writeText("{bad")
        repo.load(Uri.fromFile(file))
        assertEquals(
            BackupStatus.Done(BackupOutcome.INVALID_FILE, BackupError.NOT_BACKUP),
            repo.status.first { it is BackupStatus.Done && it.outcome == BackupOutcome.INVALID_FILE },
        )
    }

    @Test fun staleSafetyCopy_isDroppedAfterSevenDays() = runBlocking {
        restoreText(repo.exportJson()).getOrThrow()
        repo.refreshSafetyCopy()
        assertNotNull(repo.safetyCopy.value)
        repo.safetyFile.setLastModified(now.toEpochMilli() - BackupRepository.SAFETY_COPY_TTL_MS - 1)
        repo.refreshSafetyCopy()
        assertNull(repo.safetyCopy.value)
        assertFalse(repo.hasSafetyCopy())
    }

    @Test fun idsContinueAboveRestoredMax() = runBlocking {
        val custom = seed()
        val maxTx = db.transactionDao().all().maxOf { it.id }
        val maxCat = db.categoryDao().all().maxOf { it.id }
        val json = repo.exportJson()
        // 새 DB: 시퀀스가 낮은 상태에서 복원해도 새 id가 겹치지 않아야 한다.
        db.close()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        AppDatabase.seedCategoriesIfEmpty(db.categoryDao())
        repo = newRepo()
        restoreText(json).getOrThrow()
        val newTx = db.transactionDao().insert(TransactionEntity(occurredEpochDay = 20_005, amountMinor = 1, kind = "EXPENSE", categoryId = custom))
        val newCat = db.categoryDao().insert(CategoryEntity(name = "새것", kind = "EXPENSE", parentId = parentId("식비"), sortOrder = 0))
        assertTrue(newTx > maxTx)
        assertTrue(newCat > maxCat)
    }

    @Test fun safetyCopyHoldsStateBeforeRestore_andUndoSwaps() = runBlocking {
        seed()
        val before = repo.exportJson()
        scramble()
        val scrambled = repo.exportJson()
        assertFalse(repo.hasSafetyCopy())
        assertNull(repo.undoRestore())
        restoreText(before).getOrThrow()
        assertEquals(scrambled, repo.safetyFile.readText())
        // 되돌리기는 맞바꾸기: 되돌린 뒤 사본은 복원된 상태라 다시 되돌릴 수 있다.
        assertEquals(RestoreResult.RESTORED, repo.undoRestore())
        assertEquals(scrambled, repo.exportJson())
        assertEquals(before, repo.safetyFile.readText())
        assertEquals(RestoreResult.RESTORED, repo.undoRestore())
        assertEquals(before, repo.exportJson())
    }

    @Test fun restore_waitsForSharedLock() = runBlocking {
        val json = repo.exportJson()
        lock.lock()
        val job = async { restoreText(json) }
        delay(300)
        assertFalse(job.isCompleted)
        lock.unlock()
        assertTrue(job.await().isSuccess)
    }

    @Test fun invalidFiles_areRejectedAndNothingChanges() = runBlocking {
        seed()
        val good = repo.exportJson()
        val state = good
        fun edit(block: JSONObject.() -> Unit) = JSONObject(good).apply(block).toString()
        val firstTx = { o: JSONObject -> o.getJSONArray("transactions").getJSONObject(0) }
        val cases = mapOf(
            "malformed" to ("{not json" to BackupError.NOT_BACKUP),
            "wrong format" to (edit { put("format", "something-else") } to BackupError.NOT_BACKUP),
            "future format" to (edit { put("formatVersion", 2) } to BackupError.NEWER_VERSION),
            "future db" to (edit { put("dbVersion", 10) } to BackupError.NEWER_VERSION),
            "dangling category" to (edit { firstTx(this).put("categoryId", 9_999) } to BackupError.DAMAGED),
            "duplicate tx id" to (edit { getJSONArray("transactions").getJSONObject(1).put("id", firstTx(this).getLong("id")) } to BackupError.DAMAGED),
            "duplicate category id" to (edit { getJSONArray("categories").getJSONObject(1).put("id", getJSONArray("categories").getJSONObject(0).getLong("id")) } to BackupError.DAMAGED),
            "missing parent" to (edit { getJSONArray("categories").getJSONObject(1).put("parentId", 9_999) } to BackupError.DAMAGED),
            "budget dangling" to (edit { getJSONArray("categoryBudgets").getJSONObject(0).put("categoryId", 9_999) } to BackupError.DAMAGED),
            "missing field" to (edit { firstTx(this).remove("amountMinor") } to BackupError.DAMAGED),
            "number as string" to (edit { firstTx(this).put("amountMinor", "4500") } to BackupError.DAMAGED),
            "fraction" to (edit { firstTx(this).put("amountMinor", 1.5) } to BackupError.DAMAGED),
            "negative amount" to (edit { firstTx(this).put("amountMinor", -1) } to BackupError.DAMAGED),
            "huge amount" to (edit { firstTx(this).put("amountMinor", 1_000_000_000_000L) } to BackupError.DAMAGED),
            "bad payday" to (edit { getJSONObject("prefs").put("paydayDom", 32) } to BackupError.DAMAGED),
            "no categories" to (edit { put("categories", org.json.JSONArray()) } to BackupError.DAMAGED),
        )
        for ((name, case) in cases) {
            val (text, expected) = case
            val error = restoreText(text).exceptionOrNull()
            assertEquals(name, expected, (error as? BackupFormatException)?.error)
            assertEquals(name, state, repo.exportJson())
            assertFalse(name, repo.hasSafetyCopy())
        }
    }

    @Test fun tooLargeFile_isRejectedWhileReading() {
        val big = ByteArray(BackupCodec.MAX_BYTES + 1) { ' '.code.toByte() }
        val error = runCatching { BackupCodec.readCapped(ByteArrayInputStream(big)) }.exceptionOrNull()
        assertEquals(BackupError.TOO_LARGE, (error as? BackupFormatException)?.error)
        assertEquals("{}", BackupCodec.readCapped(ByteArrayInputStream("{}".toByteArray())))
    }

    @Test fun unknownFieldsIgnored_andRulesWithMissingCategoryDropped() = runBlocking {
        seed()
        val text = JSONObject(repo.exportJson()).apply {
            put("futureThing", JSONObject().put("x", 1))
            getJSONArray("transactions").getJSONObject(0).put("tags", "새 필드")
            getJSONArray("merchantRules").put(
                JSONObject().put("merchantKey", "없는곳").put("direction", "OUT").put("displayName", "없는곳")
                    .put("categoryId", 9_999).put("updatedAtMs", 1),
            )
        }.toString()
        val snapshot = BackupCodec.fromJson(text).getOrThrow()
        assertEquals(2, snapshot.transactions.size)
        assertEquals(listOf("가짜", "빵집"), snapshot.merchantRules.map { it.merchantKey }.sorted())
        assertNotNull(restoreText(text).getOrNull())
    }
}
