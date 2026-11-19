package com.householdbudget.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.householdbudget.app.domain.category.FixedCategories
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
class Migration6To7Test {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test.db"
    private var room: AppDatabase? = null

    @Before fun setUp() {
        context.deleteDatabase(dbName)
    }

    @After fun tearDown() {
        room?.close()
        context.deleteDatabase(dbName)
    }

    @Test fun transactionCountAndSumsByKind_arePreserved() {
        val db = migrate(seeded = true)
        assertEquals(9L, db.long("SELECT COUNT(*) FROM transactions"))
        val sums = db.rows("SELECT kind, SUM(amount_minor) FROM transactions GROUP BY kind") { it.getString(0) to it.getLong(1) }.toMap()
        assertEquals(mapOf("EXPENSE" to 195000L, "INCOME" to 3000000L, "SAVINGS" to 200000L), sums)
    }

    @Test fun categories_equalFixedTreeExactly() {
        val db = migrate(seeded = true)
        assertTree(db)
    }

    @Test fun everyTransaction_pointsToLeafOfSameKind() {
        val db = migrate(seeded = true)
        assertEquals(
            9L,
            db.long(
                "SELECT COUNT(*) FROM transactions t JOIN categories c ON c.id = t.category_id " +
                    "WHERE c.parent_id IS NOT NULL AND c.kind = t.kind",
            ),
        )
    }

    @Test fun mappedTransactions_landOnSpecTargets() {
        val db = migrate(seeded = true)
        assertEquals(Triple("식비", "장보기", ""), db.txByAmount(32000))
        assertEquals(Triple("식비", "식사", "점심"), db.txByAmount(9000))
        assertEquals(Triple("저축", "기타", "[적금/예금>파킹통장]"), db.txByAmount(200000))
    }

    @Test fun unmapped_goToOther_withOriginalNameInMemo() {
        val db = migrate(seeded = true)
        assertEquals(Triple("기타", "기타", "[교육>도서] 책"), db.txByAmount(15000))
        assertEquals(Triple("기타", "기타", "[교육>강의/학원]"), db.txByAmount(50000))
        assertEquals(Triple("기타", "기타", "[취미생활>레고] 선물용"), db.txByAmount(70000))
        assertEquals(Triple("기타", "기타", "잡비"), db.txByAmount(1000))
    }

    @Test fun customChildUnderKnownParent_keepsParentMeaning() {
        val db = migrate(seeded = true)
        assertEquals(Triple("식비", "식사", "치킨"), db.txByAmount(18000))
    }

    @Test fun recurringRulesTable_isDropped() {
        val db = migrate(seeded = true)
        assertEquals(0L, db.long("SELECT COUNT(*) FROM sqlite_master WHERE name = 'recurring_rules'"))
    }

    @Test fun budgets_followMajorityAndMerge() {
        val db = migrate(seeded = true)
        val budgets =
            db.rows(
                "SELECT p.name, b.monthly_amount_minor, b.enabled FROM category_budgets b " +
                    "JOIN categories p ON p.id = b.category_id WHERE p.parent_id IS NULL",
            ) { it.getString(0) to (it.getLong(1) to it.getInt(2)) }.toMap()
        assertEquals(
            mapOf("식비" to (100000L to 1), "생활" to (50000L to 1), "교통" to (50000L to 1), "기타" to (10000L to 1)),
            budgets,
        )
        assertEquals(4L, db.long("SELECT COUNT(*) FROM category_budgets"))
    }

    @Test fun emptyV6Database_getsFixedTree() {
        val db = migrate(seeded = false)
        assertTree(db)
        assertEquals(0L, db.long("SELECT COUNT(*) FROM transactions"))
    }

    @Test fun freshInstallSeed_equalsFixedTree() {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        room = db
        runBlocking { AppDatabase.seedCategoriesIfEmpty(db.categoryDao()) }
        val sql = db.openHelper.writableDatabase
        assertTree(sql)
        val count = sql.long("SELECT COUNT(*) FROM categories")
        runBlocking { AppDatabase.seedCategoriesIfEmpty(db.categoryDao()) }
        assertEquals(count, sql.long("SELECT COUNT(*) FROM categories"))
    }

    // ── 준비 ──────────────────────────────────────────────────────────

    private fun migrate(seeded: Boolean): SupportSQLiteDatabase {
        V6Schema.create(context, dbName).apply {
            if (seeded) seedV6(this)
            close()
        }
        val db =
            Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .addMigrations(*AppDatabase.ALL_MIGRATIONS)
                .allowMainThreadQueries()
                .build()
        room = db
        return db.openHelper.writableDatabase
    }

    private fun seedV6(db: SupportSQLiteDatabase) {
        val leafIds = mutableMapOf<String, Long>()
        val parentIds = mutableMapOf<String, Long>()
        val parentKinds = mutableMapOf<String, String>()
        fun insert(name: String, kind: String, parentId: Long?, sort: Int): Long {
            db.execSQL(
                "INSERT INTO categories (name, kind, parent_id, sort_order) VALUES (?, ?, ?, ?)",
                arrayOf(name, kind, parentId, sort),
            )
            return db.long("SELECT last_insert_rowid()")
        }
        (OLD_TREE + Triple("EXPENSE", "취미생활", listOf("레고"))).forEachIndexed { i, (kind, parent, children) ->
            val pid = insert(parent, kind, null, i)
            parentIds[parent] = pid
            parentKinds[parent] = kind
            children.forEachIndexed { j, child -> leafIds["$parent>$child"] = insert(child, kind, pid, j) }
        }
        leafIds["식비>야식"] = insert("야식", "EXPENSE", parentIds.getValue("식비"), 5)

        listOf(
            Triple("식비>식사", 9000L, "점심"),
            Triple("생활/마트>장보기", 32000L, ""),
            Triple("교육>도서", 15000L, "책"),
            Triple("교육>강의/학원", 50000L, ""),
            Triple("취미생활>레고", 70000L, "선물용"),
            Triple("식비>야식", 18000L, "치킨"),
            Triple("기타>기본", 1000L, "잡비"),
            Triple("월급>기본", 3000000L, "11월"),
            Triple("적금/예금>파킹통장", 200000L, ""),
        ).forEach { (path, amount, memo) ->
            val kind = parentKinds.getValue(path.substringBefore('>'))
            db.execSQL(
                "INSERT INTO transactions (occurred_epoch_day, amount_minor, kind, category_id, memo) VALUES (20000, ?, ?, ?, ?)",
                arrayOf(amount, kind, leafIds.getValue(path), memo),
            )
        }
        db.execSQL(
            "INSERT INTO recurring_rules (name, day_of_month, amount_minor, kind, category_id, memo, enabled, last_applied_year_month) " +
                "VALUES ('월세', 25, 500000, 'EXPENSE', ?, '', 1, NULL)",
            arrayOf(leafIds.getValue("주거/공과금>월세/관리비")),
        )
        listOf("식비" to 100000L, "생활/마트" to 50000L, "차량" to 30000L, "교통" to 20000L, "교육" to 10000L).forEach { (p, amount) ->
            db.execSQL(
                "INSERT INTO category_budgets (category_id, monthly_amount_minor, enabled) VALUES (?, ?, 1)",
                arrayOf(parentIds.getValue(p), amount),
            )
        }
    }

    private fun assertTree(db: SupportSQLiteDatabase) {
        val expected =
            FixedCategories.TREE.flatMap { p -> p.children.map { Triple(p.kind.storage, p.name, it) } }.toSet()
        val actual =
            db.rows(
                "SELECT c.kind, p.name, c.name FROM categories c JOIN categories p ON p.id = c.parent_id",
            ) { Triple(it.getString(0), it.getString(1), it.getString(2)) }.toSet()
        assertEquals(expected, actual)
        assertEquals(9L, db.long("SELECT COUNT(*) FROM categories WHERE parent_id IS NULL"))
        assertEquals(31L, db.long("SELECT COUNT(*) FROM categories WHERE parent_id IS NOT NULL"))
    }

    private fun SupportSQLiteDatabase.txByAmount(amount: Long): Triple<String, String, String> =
        rows(
            "SELECT p.name, c.name, t.memo FROM transactions t JOIN categories c ON c.id = t.category_id " +
                "JOIN categories p ON p.id = c.parent_id WHERE t.amount_minor = $amount",
        ) { Triple(it.getString(0), it.getString(1), it.getString(2)) }.single()

    private fun SupportSQLiteDatabase.long(sql: String): Long = query(sql).use { it.moveToFirst(); it.getLong(0) }

    private fun <T> SupportSQLiteDatabase.rows(sql: String, read: (android.database.Cursor) -> T): List<T> =
        query(sql).use { c -> buildList { while (c.moveToNext()) add(read(c)) } }

    private companion object {
        /** v6까지의 기본 트리(옛 AppDatabase.DEFAULT_CATEGORY_SEEDS) 복사본. */
        val OLD_TREE =
            listOf(
                Triple("INCOME", "월급", listOf("기본", "상여/보너스")),
                Triple("INCOME", "부수입", listOf("이자/배당", "캐시백/포인트", "중고 판매", "용돈")),
                Triple("INCOME", "기타 수입", listOf("기본", "환급/지원금")),
                Triple("EXPENSE", "식비", listOf("식사", "카페", "간식", "배달", "술/모임")),
                Triple("EXPENSE", "생활/마트", listOf("장보기", "생필품", "가전/가구")),
                Triple("EXPENSE", "주거/공과금", listOf("월세/관리비", "전기/가스", "수도", "인터넷/TV")),
                Triple("EXPENSE", "통신", listOf("휴대폰", "구독 서비스")),
                Triple("EXPENSE", "교통", listOf("대중교통", "택시", "기차/시외버스")),
                Triple("EXPENSE", "차량", listOf("주유", "주차/통행료", "정비/세차", "보험/자동차세")),
                Triple("EXPENSE", "쇼핑", listOf("의류/패션", "뷰티/미용", "온라인 쇼핑")),
                Triple("EXPENSE", "문화/여가", listOf("영화/공연", "취미", "운동/헬스", "게임")),
                Triple("EXPENSE", "여행", listOf("숙소", "항공/교통", "현지 경비")),
                Triple("EXPENSE", "의료", listOf("병원", "약국", "영양제/건강식품")),
                Triple("EXPENSE", "교육", listOf("도서", "강의/학원", "시험/자격증")),
                Triple("EXPENSE", "경조사/선물", listOf("경조사비", "선물")),
                Triple("EXPENSE", "금융", listOf("보험료", "수수료/이자", "세금")),
                Triple("EXPENSE", "반려동물", listOf("사료/간식", "병원/미용")),
                Triple("EXPENSE", "기타", listOf("기본")),
                Triple("SAVINGS", "적금/예금", listOf("적금", "예금", "파킹통장")),
                Triple("SAVINGS", "투자", listOf("주식", "펀드/ETF", "코인")),
                Triple("SAVINGS", "연금", listOf("연금저축", "IRP")),
                Triple("SAVINGS", "청약", listOf("기본")),
                Triple("SAVINGS", "비상금", listOf("기본")),
            )
    }
}
