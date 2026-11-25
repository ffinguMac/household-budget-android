package com.householdbudget.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.householdbudget.app.data.local.AppDatabase
import com.householdbudget.app.data.local.entity.CategoryEntity
import com.householdbudget.app.data.local.entity.MerchantRuleEntity
import com.householdbudget.app.data.local.entity.TransactionEntity
import com.householdbudget.app.data.preferences.UserPreferencesRepository
import com.householdbudget.app.domain.category.CustomCategories.NameError
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CustomCategoryRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repo: BudgetRepository
    private val fakeToday = LocalDate.of(2026, 11, 25)

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        AppDatabase.seedCategoriesIfEmpty(db.categoryDao())
        repo = BudgetRepository(
            db, db.transactionDao(), db.categoryDao(), db.archivedPeriodDao(), db.categoryBudgetDao(),
            UserPreferencesRepository(context), todayProvider = { fakeToday },
        )
    }

    @After fun tearDown() = db.close()

    private suspend fun parent(kind: String, name: String): CategoryEntity =
        db.categoryDao().observeAll().first().single { it.parentId == null && it.kind == kind && it.name == name }

    private suspend fun leaf(kind: String, p: String, c: String): CategoryEntity = db.categoryDao().findLeafByPath(kind, p, c)!!

    private suspend fun add(parentId: Long, name: String): CategoryEntity =
        (repo.addCustomCategory(parentId, name) as CustomCategoryResult.Saved).category

    private suspend fun tx(categoryId: Long, day: Long) {
        db.transactionDao().insert(TransactionEntity(occurredEpochDay = day, amountMinor = 1000, kind = "EXPENSE", categoryId = categoryId))
    }

    @Test fun add_appendsAfterFixedChildrenWithParentKind() = runBlocking {
        val life = parent("EXPENSE", "생활")
        val pet = add(life.id, "  반려동물 ")
        assertEquals("반려동물", pet.name); assertEquals("EXPENSE", pet.kind); assertNull(pet.icon)
        assertEquals(listOf("통신", "공과금", "생활용품", "구독", "반려동물"), db.categoryDao().childrenOf(life.id).map { it.name })
    }

    @Test fun add_rejectsFixedNameAndDuplicateButAllowsOtherParent() = runBlocking {
        val life = parent("EXPENSE", "생활"); val food = parent("EXPENSE", "식비")
        assertEquals(CustomCategoryResult.Invalid(NameError.DUPLICATE), repo.addCustomCategory(life.id, "통 신"))
        add(life.id, "Gym")
        assertEquals(CustomCategoryResult.Invalid(NameError.DUPLICATE), repo.addCustomCategory(life.id, "gym"))
        assertEquals(CustomCategoryResult.Invalid(NameError.EMPTY), repo.addCustomCategory(life.id, "  "))
        assertTrue(repo.addCustomCategory(food.id, "Gym") is CustomCategoryResult.Saved)
    }

    @Test fun add_worksUnderIncomeAndSavings() = runBlocking {
        assertEquals("INCOME", add(parent("INCOME", "수입").id, "부업").kind)
        assertEquals("SAVINGS", add(parent("SAVINGS", "저축").id, "비상금").kind)
    }

    @Test fun rename_keepsTransactionsAndAllowsSameName() = runBlocking {
        val pet = add(parent("EXPENSE", "생활").id, "반려동물"); tx(pet.id, TODAY)
        assertTrue(repo.renameCustomCategory(pet.id, "반려동물") is CustomCategoryResult.Saved)
        assertTrue(repo.renameCustomCategory(pet.id, "강아지") is CustomCategoryResult.Saved)
        assertEquals("강아지", db.categoryDao().getById(pet.id)!!.name)
        assertEquals(1, db.transactionDao().countByCategory(pet.id))
        assertEquals(CustomCategoryResult.Invalid(NameError.DUPLICATE), repo.renameCustomCategory(pet.id, "구독"))
    }

    @Test fun add_refusesSingleFixedChildParent() = runBlocking {
        val flex = parent("EXPENSE", "유동 지출")
        assertTrue(runCatching { repo.addCustomCategory(flex.id, "모임") }.isFailure)
        assertEquals(listOf("기본"), db.categoryDao().childrenOf(flex.id).map { it.name })
    }

    @Test fun preview_listsFixedSiblingsWithDefault() = runBlocking {
        val life = parent("EXPENSE", "생활"); val pet = add(life.id, "반려동물"); add(life.id, "헬스장"); tx(pet.id, TODAY)
        val p = repo.customCategoryDeletePreview(pet.id)!!
        assertEquals(listOf("통신", "공과금", "생활용품", "구독"), p.targets.map { it.name })   // 직접 추가 형제는 빠진다
        assertEquals(leaf("EXPENSE", "생활", "통신").id, p.defaultTargetId); assertEquals(1, p.transactionCount)
        val side = add(parent("INCOME", "수입").id, "부업")
        assertEquals(leaf("INCOME", "수입", "기타").id, repo.customCategoryDeletePreview(side.id)!!.defaultTargetId)
    }

    @Test fun delete_movesTransactionsAndRulesToChosenTarget() = runBlocking {
        val life = parent("EXPENSE", "생활"); val pet = add(life.id, "반려동물")
        val target = leaf("EXPENSE", "생활", "생활용품")   // 기본값(통신)이 아닌 것을 고른다
        tx(pet.id, TODAY); tx(pet.id, TODAY - 1); tx(target.id, TODAY)
        db.merchantRuleDao().upsert(MerchantRuleEntity("petshop", "OUT", "펫샵", pet.id, 0))
        db.merchantRuleDao().upsert(MerchantRuleEntity("skip", "OUT", "건너뜀", null, 0))
        val total = db.transactionDao().countBetween(0, Long.MAX_VALUE)
        assertEquals(target.id, repo.deleteCustomCategory(pet.id, target.id)!!.id)
        assertNull(db.categoryDao().getById(pet.id))
        assertEquals(total, db.transactionDao().countBetween(0, Long.MAX_VALUE))
        assertEquals(3, db.transactionDao().countByCategory(target.id))
        assertEquals(0, db.transactionDao().countByCategory(leaf("EXPENSE", "생활", "통신").id))
        assertEquals(target.id, db.merchantRuleDao().getByKey("petshop", "OUT")!!.categoryId)
        assertNull(db.merchantRuleDao().getByKey("skip", "OUT")!!.categoryId)
    }

    @Test fun delete_refusesInvalidTarget() = runBlocking {
        val life = parent("EXPENSE", "생활"); val pet = add(life.id, "반려동물"); val gym = add(life.id, "헬스장"); tx(pet.id, TODAY)
        assertNull(repo.deleteCustomCategory(pet.id, gym.id))                          // 다른 직접 추가 항목
        assertNull(repo.deleteCustomCategory(pet.id, leaf("EXPENSE", "식비", "카페").id)) // 다른 대분류
        assertNull(repo.deleteCustomCategory(pet.id, pet.id)); assertNull(repo.deleteCustomCategory(pet.id, life.id))
        assertNull(repo.deleteCustomCategory(pet.id, 999_999))
        assertEquals(1, db.transactionDao().countByCategory(pet.id))
    }

    @Test fun renameAndDelete_refuseFixedAndParents() = runBlocking {
        val fixed = leaf("EXPENSE", "식비", "카페"); val p = parent("EXPENSE", "식비")
        val before = db.categoryDao().observeAll().first()
        val other = leaf("EXPENSE", "식비", "식사")
        assertNull(repo.customCategoryDeletePreview(fixed.id)); assertNull(repo.deleteCustomCategory(fixed.id, other.id))
        assertNull(repo.deleteCustomCategory(p.id, other.id)); assertNull(repo.deleteCustomCategory(999_999, other.id))
        assertTrue(runCatching { repo.renameCustomCategory(fixed.id, "커피") }.isFailure)
        assertEquals(before, db.categoryDao().observeAll().first())
    }

    @Test fun hidden_onlyCustomUsedMoreThan90DaysAgo() = runBlocking {
        val life = parent("EXPENSE", "생활")
        val old = add(life.id, "오래됨"); val fresh = add(life.id, "최근"); add(life.id, "안씀")
        tx(old.id, TODAY - 91); tx(fresh.id, TODAY - 90); tx(leaf("EXPENSE", "생활", "구독").id, TODAY - 400)
        assertEquals(setOf(old.id), repo.observeHiddenCustomCategoryIds().first())
        tx(old.id, TODAY)   // 다시 쓰면 돌아온다
        assertEquals(emptySet<Long>(), repo.observeHiddenCustomCategoryIds().first())
    }

    @Test fun seed_leavesCustomChildrenAlone() = runBlocking {
        val pet = add(parent("EXPENSE", "생활").id, "반려동물")
        val before = db.categoryDao().observeAll().first()
        AppDatabase.seedCategoriesIfEmpty(db.categoryDao())
        assertEquals(before, db.categoryDao().observeAll().first())
        assertEquals("반려동물", db.categoryDao().getById(pet.id)!!.name)
    }

    companion object {
        private val TODAY = LocalDate.of(2026, 11, 25).toEpochDay()
    }
}
