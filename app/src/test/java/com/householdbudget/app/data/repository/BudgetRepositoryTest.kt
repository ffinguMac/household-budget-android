package com.householdbudget.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.householdbudget.app.data.local.AppDatabase
import com.householdbudget.app.data.preferences.UserPreferencesRepository
import com.householdbudget.app.domain.CategoryKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BudgetRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: BudgetRepository

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val prefs = UserPreferencesRepository(ctx)
        repo =
            BudgetRepository(
                database = db,
                transactionDao = db.transactionDao(),
                categoryDao = db.categoryDao(),
                recurringRuleDao = db.recurringRuleDao(),
                archivedPeriodDao = db.archivedPeriodDao(),
                userPreferencesRepository = prefs,
            )
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun addParentCategory_creates_default_leaf() = runTest {
        val result = repo.addParentCategory("식비", CategoryKind.EXPENSE)
        assertTrue(result.isSuccess)
        val parentId = result.getOrThrow()

        val all = repo.observeCategories().first()
        val parent = all.first { it.id == parentId }
        assertEquals("식비", parent.name)
        assertNull(parent.parentId)
        assertEquals("EXPENSE", parent.kind)

        val children = all.filter { it.parentId == parentId }
        assertEquals(1, children.size)
        assertEquals("기본", children[0].name)
        assertEquals("EXPENSE", children[0].kind)
    }

    @Test
    fun addParentCategory_rejects_duplicate_name_same_kind() = runTest {
        repo.addParentCategory("식비", CategoryKind.EXPENSE)
        val second = repo.addParentCategory("식비", CategoryKind.EXPENSE)
        assertTrue(second.isFailure)
        assertEquals(CategoryValidationError.DuplicateName, second.validationError())
    }

    @Test
    fun addParentCategory_allows_same_name_different_kind() = runTest {
        repo.addParentCategory("저축", CategoryKind.SAVINGS)
        val other = repo.addParentCategory("저축", CategoryKind.EXPENSE)
        assertTrue(other.isSuccess)
    }

    @Test
    fun addLeafCategory_removes_empty_default_leaf() = runTest {
        val parentId = repo.addParentCategory("식비", CategoryKind.EXPENSE).getOrThrow()
        // 이 시점에는 "기본" leaf 1개만 존재
        val before = repo.observeCategories().first().filter { it.parentId == parentId }
        assertEquals(1, before.size)
        assertEquals("기본", before[0].name)

        // 첫 번째 사용자 정의 자식 추가
        repo.addLeafCategory(parentId, "카페").getOrThrow()

        val after = repo.observeCategories().first().filter { it.parentId == parentId }
        assertEquals("빈 '기본' leaf는 자동 삭제되어야 함", 1, after.size)
        assertEquals("카페", after[0].name)
    }

    @Test
    fun addLeafCategory_keeps_default_leaf_when_it_has_transactions() = runTest {
        val parentId = repo.addParentCategory("식비", CategoryKind.EXPENSE).getOrThrow()
        val defaultLeafId = repo.observeCategories().first()
            .first { it.parentId == parentId && it.name == "기본" }.id

        // 거래 추가 → "기본" leaf에 참조 발생
        repo.insertTransaction(
            occurredDate = LocalDate.of(2024, 1, 1),
            amountMinor = 5000L,
            categoryId = defaultLeafId,
            memo = "",
        )

        // 이제 새 자식 추가
        repo.addLeafCategory(parentId, "카페").getOrThrow()

        val children = repo.observeCategories().first().filter { it.parentId == parentId }
        assertEquals(2, children.size)
        assertTrue(children.any { it.name == "기본" })
        assertTrue(children.any { it.name == "카페" })
    }

    @Test
    fun deleteCategory_reports_references_when_not_forced() = runTest {
        val parentId = repo.addParentCategory("식비", CategoryKind.EXPENSE).getOrThrow()
        val leafId = repo.addLeafCategory(parentId, "카페").getOrThrow()
        repo.insertTransaction(
            occurredDate = LocalDate.of(2024, 1, 1),
            amountMinor = 3000L,
            categoryId = leafId,
            memo = "",
        )

        val result = repo.deleteCategory(leafId, force = false)
        assertTrue(result is CategoryDeletionResult.HasReferences)
        val has = result as CategoryDeletionResult.HasReferences
        assertEquals(1, has.transactionCount)
        assertEquals(0, has.recurringCount)

        // force=true 시 거래와 leaf 모두 삭제
        val forced = repo.deleteCategory(leafId, force = true)
        assertTrue(forced is CategoryDeletionResult.Success)
        val leafAfter = repo.observeCategories().first().find { it.id == leafId }
        assertNull(leafAfter)
    }

    @Test
    fun moveLeaf_rejects_kind_mismatch() = runTest {
        val expenseParent = repo.addParentCategory("식비", CategoryKind.EXPENSE).getOrThrow()
        val incomeParent = repo.addParentCategory("월급", CategoryKind.INCOME).getOrThrow()
        val expenseLeaf = repo.addLeafCategory(expenseParent, "카페").getOrThrow()

        val result = repo.moveLeaf(expenseLeaf, incomeParent)
        assertTrue(result.isFailure)
        assertEquals(CategoryValidationError.KindMismatch, result.validationError())
    }

    @Test
    fun moveLeaf_succeeds_within_same_kind() = runTest {
        val p1 = repo.addParentCategory("식비", CategoryKind.EXPENSE).getOrThrow()
        val p2 = repo.addParentCategory("문화", CategoryKind.EXPENSE).getOrThrow()
        val leaf = repo.addLeafCategory(p1, "간식").getOrThrow()

        val result = repo.moveLeaf(leaf, p2)
        assertTrue(result.isSuccess)

        val moved = repo.observeCategories().first().first { it.id == leaf }
        assertEquals(p2, moved.parentId)
    }

    @Test
    fun renameCategory_rejects_duplicate_within_same_parent() = runTest {
        val parentId = repo.addParentCategory("식비", CategoryKind.EXPENSE).getOrThrow()
        val cafeId = repo.addLeafCategory(parentId, "카페").getOrThrow()
        repo.addLeafCategory(parentId, "간식")

        val result = repo.renameCategory(cafeId, "간식")
        assertTrue(result.isFailure)
        assertEquals(CategoryValidationError.DuplicateName, result.validationError())
    }

    @Test
    fun renameCategory_allows_same_name_different_parent() = runTest {
        val p1 = repo.addParentCategory("식비", CategoryKind.EXPENSE).getOrThrow()
        val p2 = repo.addParentCategory("문화", CategoryKind.EXPENSE).getOrThrow()
        val leaf1 = repo.addLeafCategory(p1, "카페").getOrThrow()
        val leaf2 = repo.addLeafCategory(p2, "간식").getOrThrow()

        val result = repo.renameCategory(leaf2, "카페")
        assertTrue(result.isSuccess)
    }

    @Test
    fun insertTransaction_copies_kind_from_leaf() = runTest {
        val parentId = repo.addParentCategory("저축", CategoryKind.SAVINGS).getOrThrow()
        val leafId = repo.addLeafCategory(parentId, "투자").getOrThrow()

        val id = repo.insertTransaction(
            occurredDate = LocalDate.of(2024, 3, 15),
            amountMinor = 100_000L,
            categoryId = leafId,
            memo = "ETF",
        )

        val row = repo.getTransaction(id)
        assertNotNull(row)
        assertEquals("SAVINGS", row!!.kind)
    }

}
