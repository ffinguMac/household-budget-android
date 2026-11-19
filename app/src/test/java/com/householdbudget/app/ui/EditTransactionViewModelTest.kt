package com.householdbudget.app.ui

import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.householdbudget.app.data.local.AppDatabase
import com.householdbudget.app.data.local.entity.CategoryEntity
import com.householdbudget.app.data.preferences.UserPreferencesRepository
import com.householdbudget.app.data.repository.BudgetRepository
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class EditTransactionViewModelTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repo: BudgetRepository
    private var leafId = 0L

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(Runnable::run)
            .setTransactionExecutor(Runnable::run)
            .build()
        AppDatabase.seedCategoriesIfEmpty(db.categoryDao())
        repo = BudgetRepository(
            db, db.transactionDao(), db.categoryDao(), db.archivedPeriodDao(), db.categoryBudgetDao(),
            UserPreferencesRepository(context),
        )
        leafId = db.categoryDao().findLeafByPath("EXPENSE", "식비", "카페")!!.id
    }

    @After fun tearDown() = db.close()

    private fun txCount(): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM transactions").use { it.moveToFirst(); it.getInt(0) }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test fun save_calledTwice_insertsOnce() {
        val vm = EditTransactionViewModel(repo, null)
        vm.setAmountText("4500"); vm.setCategoryId(leafId)
        var successes = 0
        vm.save({ successes++ }, {}); vm.save({ successes++ }, {})
        idle()
        assertEquals(1, txCount()); assertEquals(1, successes)
        assertTrue(vm.uiState.value.isSaving)
    }

    @Test fun delete_calledTwice_callsSuccessOnce() {
        val id = runBlocking { repo.insertTransaction(LocalDate.of(2026, 11, 1), 1000, leafId, "") }
        val vm = EditTransactionViewModel(repo, id)
        idle()
        var successes = 0
        vm.delete { successes++ }; vm.delete { successes++ }
        idle()
        assertEquals(0, txCount()); assertEquals(1, successes)
    }

    @Test fun save_invalid_doesNotLockButton() {
        val vm = EditTransactionViewModel(repo, null)
        var invalid = 0
        vm.save({}, { invalid++ })
        assertEquals(1, invalid); assertFalse(vm.uiState.value.isSaving)
    }

    private fun cat(id: Long, parent: Long?) = CategoryEntity(id = id, name = "c$id", kind = "EXPENSE", parentId = parent, sortOrder = 0)
    private val p1 = cat(1, null)
    private val p2 = cat(2, null)
    private val children = mapOf(
        1L to listOf(cat(11, 1)),
        2L to listOf(cat(21, 2), cat(22, 2)),
    )

    @Test fun autoCategorySelection_childrenNotLoaded_keepsSelection() =
        assertNull(autoCategorySelection(listOf(p1, p2), emptyMap(), 2, 22))
    @Test fun autoCategorySelection_existingCategory_isNotChanged() =
        assertNull(autoCategorySelection(listOf(p1, p2), children, 2, 22))
    @Test fun autoCategorySelection_keepsCategoryNotAmongLeaves() =
        assertNull(autoCategorySelection(listOf(p1, p2), children, null, 2))
    @Test fun autoCategorySelection_newTransaction_picksFirstParentAndLeaf() =
        assertEquals(1L to 11L, autoCategorySelection(listOf(p1, p2), children, null, null))
    @Test fun autoCategorySelection_parentChosenNoLeafYet_picksFirstLeaf() =
        assertEquals(2L to 21L, autoCategorySelection(listOf(p1, p2), children, 2, null))
    @Test fun autoCategorySelection_parentsNotLoaded_keepsSelection() =
        assertNull(autoCategorySelection(emptyList(), children, null, null))
}
