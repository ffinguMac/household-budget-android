# 내 세부 카테고리(직접 추가) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 고정 대분류 아래에 사용자가 세부 카테고리를 선택 화면에서 바로 추가·이름 변경·삭제하고, 90일 안 쓴 항목은 칩에서 저절로 숨긴다.

**Architecture:** 스키마 변경 없이 기존 `categories` 행을 쓴다. 고정/직접 추가는 `FixedCategories.TREE`에 이름이 있는지로 파생한다. 규칙(이름 검증·삭제 대상·숨김)은 `core`의 순수 함수, 저장은 `BudgetRepository`의 Room 트랜잭션, 화면은 기존 `CategoryPicker`/`CategorySearchSheet`에 칩 하나와 길게 누르기를 더한다.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Room(v8 유지), JUnit4 + Robolectric.

**Spec:** `docs/superpowers/specs/2026-11-25-custom-subcategories-design.md`

## Global Constraints

- 새 브랜치 `feat/custom-subcategories`에서 작업한다(`master`에 직접 커밋 금지).
- **DB 스키마·마이그레이션 변경 없음.** `AppDatabase.version`은 8 그대로. 엔티티 필드를 추가하지 않는다.
- 새 의존성 금지. 휴대폰·에뮬레이터·adb를 조작하지 않는다. APK를 복사하지 않는다.
- 저장소의 `.xlsx/.xls/.numbers/.csv`는 열지도 `git add` 하지도 않는다. `git add`는 파일 경로를 지정한다(`-A`, `.` 금지). `core/bin/`은 건드리지 않는다.
- 만들지 않는다: 대분류 추가, 순서 변경, 색·이모지 선택, 카테고리 관리/설정 화면, 검색 시트에서의 추가, 대분류 타일 길게 누르기.
- 이름 규칙: 앞뒤 공백 제거 후 1–10자(코드포인트), 같은 대분류 안에서 대소문자·공백 무시하고 유일, 그 대분류의 고정 세부 이름과 같을 수 없음.
- 숨김: 직접 추가 항목이고 거래가 1건 이상이며 마지막 거래일이 오늘보다 90일 넘게 전일 때만. 한 번도 안 쓴 항목과 고정 항목은 숨기지 않는다.
- 고정 세부가 하나뿐인 대분류(지금은 유동 지출)에는 직접 추가할 수 없다. 화면은 추가 칩을 내지 않고 저장소는 거부한다.
- 삭제 시 옮길 곳: 사용자가 같은 대분류의 고정 세부 중에서 고른다. 기본 선택은 "기타" → "기본" → 첫 번째. 저장소는 고른 id가 같은 대분류의 고정 세부인지 확인한다.
- UI 문구는 한국어 해요체, `app/src/main/res/values/strings_components.xml`. 색은 `MaterialTheme.colorScheme`만, 글자는 여섯 슬롯(`displaySmall`·`titleLarge`·`titleMedium`·`bodyLarge`·`labelLarge`·`bodySmall`), 간격은 `Space`, 모서리는 `shapes.small`·`shapes.large`·`shapes.extraLarge`·`CircleShape`, 터치 대상 48dp 이상. `fontWeight =`/`fontSize =`/`Color(0x…)` 금지.
- 빌드·테스트(저장소에 `./gradlew` 없음). 아래에서 `GRADLE`은 이 줄 전체다:
  `export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=~/Library/Android/sdk; ~/.gradle/wrapper/dists/gradle-8.10.2-bin/a04bxjujx95o3nb99gddekhwo/gradle-8.10.2/bin/gradle`
- 모든 태스크의 마지막 단계는 같다: `GRADLE :app:assembleDebug test` → `BUILD SUCCESSFUL`. 그 뒤 태스크당 커밋 하나(한국어 `feat:`/`fix:`/`test:`), 마지막 줄 `Co-Authored-By: Claude <실제 모델 이름> <noreply@anthropic.com>`.

## Review Focus

1. **거래·학습 규칙이 달린 항목 삭제.** 거래 수가 줄거나 규칙이 죽은 id를 가리키면 안 된다 → Task 2 `delete_movesTransactionsAndRulesToChosenTarget`.
2. **고정 항목·대분류 id로 이름 변경/삭제 호출, 잘못된 옮길 곳(다른 대분류·다른 직접 추가 항목·자기 자신).** 아무것도 바뀌면 안 된다 → Task 2 `renameAndDelete_refuseFixedAndParents`, `delete_refusesInvalidTarget`.
3. **이름 변형 입력**("카 페", "GYM"/"gym", 공백만, 11자, 이모지). 겹침·길이 판정이 흔들리면 안 된다 → Task 1 `validateName_*`.
4. **고정 세부가 하나뿐인 대분류(유동 지출)에 추가, 직접 추가가 전부 숨겨진 대분류.** 앞은 거부되고 "기본" 바로 선택이 유지돼야 하며, 뒤는 빈 칩 줄이 나오면 안 된다 → Task 1 `allowsCustomChildren_*`, Task 2 `add_refusesSingleFixedChildParent`, Task 3 `visibleLeaves_*`.
5. **거래 입력 중 선택된 항목을 삭제.** 저장이 "Category not found"로 죽으면 안 된다 → 자동 테스트 없음. Task 3 Step 5 리뷰 항목.
6. **빈 DB가 아닌 상태에서 앱 재시작.** 시드가 직접 추가 항목을 건드리면 안 된다 → Task 2 `seed_leavesCustomChildrenAlone`.

---

### Task 1: 순수 규칙 (core)

**Files:**
- Modify: `core/src/main/kotlin/com/householdbudget/app/domain/category/FixedCategories.kt`
- Create: `core/src/main/kotlin/com/householdbudget/app/domain/category/CustomCategories.kt`
- Test: `core/src/test/kotlin/com/householdbudget/app/domain/category/CustomCategoriesTest.kt`

**Interfaces:**
- Produces:
  - `FixedCategories.fixedChildrenOf(kind: CategoryKind, parentName: String): List<String>` — 없는 대분류면 빈 목록.
  - `FixedCategories.isFixedChild(kind: CategoryKind, parentName: String, childName: String): Boolean` — 정확히 일치할 때만 true.
  - `FixedCategories.allowsCustomChildren(kind: CategoryKind, parentName: String): Boolean` — `fixedChildrenOf(...).size >= 2`.
  - `object CustomCategories`:
    - `const val MAX_NAME_LENGTH = 10`, `const val HIDE_AFTER_DAYS = 90L`
    - `enum class NameError { EMPTY, TOO_LONG, DUPLICATE }`
    - `fun validateName(raw: String, takenNames: Collection<String>): NameError?` — `raw.trim()` 기준. 길이는 `codePointCount`. 비교 키는 "공백 문자 전부 제거 + `lowercase()`". 호출자가 `takenNames`에 형제 이름(자기 자신 제외)과 고정 세부 이름을 넣는다.
    - `fun deleteTargetName(fixedChildren: List<String>): String?` — 삭제 대화상자의 **기본 선택**. "기타" → "기본" → 첫 번째 → 빈 목록이면 null.
    - `fun isHidden(lastUsedEpochDay: Long?, todayEpochDay: Long): Boolean` — `lastUsedEpochDay != null && todayEpochDay - lastUsedEpochDay > HIDE_AFTER_DAYS`.

- [ ] **Step 1: 실패하는 테스트 작성** (`CustomCategoriesTest.kt`, 기존 `FixedCategoriesTest`와 같은 JUnit4 스타일)

```kotlin
@Test fun validateName_acceptsTrimmedOneToTenChars() {
    assertNull(CustomCategories.validateName("  반려동물 ", listOf("통신")))
    assertNull(CustomCategories.validateName("가나다라마바사아자차", emptyList()))      // 10자
    assertNull(CustomCategories.validateName("🐶🐶🐶🐶🐶🐶🐶🐶🐶🐶", emptyList()))  // 코드포인트 10
}
@Test fun validateName_rejectsEmptyAndTooLong() {
    assertEquals(NameError.EMPTY, CustomCategories.validateName("   ", emptyList()))
    assertEquals(NameError.TOO_LONG, CustomCategories.validateName("가나다라마바사아자차카", emptyList())) // 11자
}
@Test fun validateName_duplicateIgnoresCaseAndSpaces() {
    assertEquals(NameError.DUPLICATE, CustomCategories.validateName("카 페", listOf("카페")))
    assertEquals(NameError.DUPLICATE, CustomCategories.validateName("GYM", listOf("gym")))
    assertNull(CustomCategories.validateName("카페2", listOf("카페")))
}
@Test fun deleteTargetName_prefersEtcThenDefaultThenFirst() {
    assertEquals("기타", CustomCategories.deleteTargetName(listOf("월급", "캐시백/이자", "용돈", "기타")))
    assertEquals("기본", CustomCategories.deleteTargetName(listOf("기본")))
    assertEquals("식사", CustomCategories.deleteTargetName(listOf("식사", "카페", "간식", "배달", "장보기")))
    assertNull(CustomCategories.deleteTargetName(emptyList()))
}
@Test fun deleteTargetName_existsForEveryFixedParent() {
    FixedCategories.TREE.forEach { assertNotNull(CustomCategories.deleteTargetName(it.children)) }
}
@Test fun isHidden_onlyWhenUsedMoreThan90DaysAgo() {
    assertFalse(CustomCategories.isHidden(null, 1000))      // 한 번도 안 씀
    assertFalse(CustomCategories.isHidden(910, 1000))       // 딱 90일
    assertTrue(CustomCategories.isHidden(909, 1000))        // 91일
    assertFalse(CustomCategories.isHidden(1005, 1000))      // 미래 날짜
}
@Test fun isFixedChild_matchesTreeExactly() {
    assertTrue(FixedCategories.isFixedChild(CategoryKind.EXPENSE, "식비", "카페"))
    assertFalse(FixedCategories.isFixedChild(CategoryKind.EXPENSE, "식비", "반려동물"))
    assertFalse(FixedCategories.isFixedChild(CategoryKind.INCOME, "식비", "카페"))
    assertEquals(listOf("기본"), FixedCategories.fixedChildrenOf(CategoryKind.EXPENSE, "유동 지출"))
}
@Test fun allowsCustomChildren_falseOnlyForSingleFixedChildParents() {
    assertFalse(FixedCategories.allowsCustomChildren(CategoryKind.EXPENSE, "유동 지출"))
    assertFalse(FixedCategories.allowsCustomChildren(CategoryKind.EXPENSE, "없는 대분류"))
    assertEquals(listOf("유동 지출"), FixedCategories.TREE.filterNot { FixedCategories.allowsCustomChildren(it.kind, it.name) }.map { it.name })
}
```

- [ ] **Step 2: 실패 확인** — `GRADLE :core:test --tests '*CustomCategoriesTest*'` → 컴파일 실패(`CustomCategories` 없음).
- [ ] **Step 3: 구현** — 위 Interfaces의 시그니처대로. `FixedCategories`에는 세 함수만 더한다(기존 `TREE`·`otherLeaf` 변경 없음).
- [ ] **Step 4: 통과 확인** — `GRADLE :core:test --tests '*CustomCategoriesTest*'` → PASS.
- [ ] **Step 5: 빌드·테스트·커밋** (Global Constraints)

```bash
git add core/src/main/kotlin/com/householdbudget/app/domain/category/FixedCategories.kt core/src/main/kotlin/com/householdbudget/app/domain/category/CustomCategories.kt core/src/test/kotlin/com/householdbudget/app/domain/category/CustomCategoriesTest.kt
git commit -m "feat: 직접 추가 세부 카테고리의 이름·삭제 대상·숨김 규칙"
```

---

### Task 2: DAO 쿼리와 저장소 동작 (data)

**Files:**
- Modify: `app/src/main/java/com/householdbudget/app/data/local/dao/CategoryDao.kt`
- Modify: `app/src/main/java/com/householdbudget/app/data/local/dao/TransactionDao.kt`
- Modify: `app/src/main/java/com/householdbudget/app/data/local/dao/MerchantRuleDao.kt`
- Create: `app/src/main/java/com/householdbudget/app/data/local/model/CategoryLastUsedRow.kt`
- Modify: `app/src/main/java/com/householdbudget/app/data/repository/BudgetRepository.kt`
- Test: `app/src/test/java/com/householdbudget/app/data/repository/CustomCategoryRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 1의 `FixedCategories.fixedChildrenOf/isFixedChild/allowsCustomChildren`, `CustomCategories.validateName/deleteTargetName/isHidden`, `NameError`.
- Produces (DAO):

```kotlin
// CategoryDao
@Query("SELECT * FROM categories WHERE parent_id = :parentId ORDER BY sort_order ASC, id ASC")
suspend fun childrenOf(parentId: Long): List<CategoryEntity>
@Query("UPDATE categories SET name = :name WHERE id = :id")
suspend fun rename(id: Long, name: String)
@Query("DELETE FROM categories WHERE id = :id")
suspend fun deleteById(id: Long)

// TransactionDao
@Query("SELECT COUNT(*) FROM transactions WHERE category_id = :categoryId")
suspend fun countByCategory(categoryId: Long): Int
@Query("UPDATE transactions SET category_id = :toId WHERE category_id = :fromId")
suspend fun moveCategory(fromId: Long, toId: Long): Int
@Query("SELECT category_id AS categoryId, MAX(occurred_epoch_day) AS lastEpochDay FROM transactions GROUP BY category_id")
fun observeLastUsedByCategory(): Flow<List<CategoryLastUsedRow>>

// MerchantRuleDao
@Query("UPDATE merchant_rules SET category_id = :toId WHERE category_id = :fromId")
suspend fun repointCategory(fromId: Long, toId: Long): Int
```

  `data class CategoryLastUsedRow(val categoryId: Long, val lastEpochDay: Long)`

- Produces (`BudgetRepository`, 파일 맨 아래에 타입 선언):

```kotlin
sealed interface CustomCategoryResult {
    data class Saved(val category: CategoryEntity) : CustomCategoryResult
    data class Invalid(val error: CustomCategories.NameError) : CustomCategoryResult
}
/** 삭제 확인 대화상자용. targets = 같은 대분류의 고정 세부(정렬 순), defaultTargetId = 기본 선택. */
data class CustomCategoryDeletePreview(val targets: List<CategoryEntity>, val defaultTargetId: Long, val transactionCount: Int)

suspend fun addCustomCategory(parentId: Long, rawName: String): CustomCategoryResult
suspend fun renameCustomCategory(id: Long, rawName: String): CustomCategoryResult
/** 직접 추가 항목이 아니거나 옮길 고정 세부가 없으면 null. */
suspend fun customCategoryDeletePreview(id: Long): CustomCategoryDeletePreview?
/** 거래·학습 규칙을 targetId로 옮기고 지운다. 반환값은 옮겨 간 고정 세부. 지우지 않았으면 null. */
suspend fun deleteCustomCategory(id: Long, targetId: Long): CategoryEntity?
/** 선택 칩에서 숨길 직접 추가 세부 id. */
fun observeHiddenCustomCategoryIds(): Flow<Set<Long>>
```

  동작 규칙(시그니처와 테스트가 정하지 않는 것만):
  - "직접 추가 항목"의 판정은 한 곳(private 함수)에서: `parentId != null`이고, 부모 행이 있고, `!FixedCategories.isFixedChild(kind, parent.name, name)`.
  - `add`: `parentId`가 대분류(`parentId == null`인 행)가 아니거나 `!FixedCategories.allowsCustomChildren(kind, parent.name)`이면 `error(...)`(화면이 이 경로를 내지 않으므로 결과 타입을 늘리지 않는다). `takenNames` = `childrenOf(parentId)`의 이름 + `fixedChildrenOf(kind, parent.name)`. 저장값은 `name = rawName.trim()`, `kind = parent.kind`, `sortOrder = (형제 최댓값 ?: -1) + 1`, `icon = null`. 검증과 insert를 `database.withTransaction` 하나로 묶는다.
  - `rename`: 직접 추가 항목이 아니면 `error(...)`. `takenNames`에서 자기 자신은 뺀다(같은 이름으로 저장하면 `Saved`).
  - `preview`: `targets` = 형제 중 `isFixedChild`인 행. `defaultTargetId` = `deleteTargetName(fixedChildrenOf(...))` 이름의 행, 없으면 `targets.first()`.
  - `delete`: `database.withTransaction` 안에서 검증(지울 것이 직접 추가 항목이고, `targetId` 행이 **같은 `parentId`의 고정 세부**) → `moveCategory` → `database.merchantRuleDao().repointCategory` → `deleteById`. 검증에 실패하면 아무것도 바꾸지 않고 null.
  - `observeHidden…`: `combine(categoryDao.observeAll(), transactionDao.observeLastUsedByCategory(), today)`. 직접 추가 항목 중 `CustomCategories.isHidden(last[id], today.toEpochDay())`인 id.

- [ ] **Step 1: 실패하는 테스트 작성** — `CustomCategoryRepositoryTest.kt`. 설정은 `BudgetRepositoryTest`와 동일(Robolectric, `@Config(sdk = [34], application = android.app.Application::class)`, 인메모리 DB, `seedCategoriesIfEmpty`, `todayProvider = { fakeToday }`, `fakeToday = LocalDate.of(2026, 11, 25)`). 도우미: `parent(kind, name)` = `observeAll().first()`에서 `parentId == null && kind/name 일치`, `leaf(kind, p, c)` = `findLeafByPath(...)!!`, `add(parentId, name)` = `(repo.addCustomCategory(parentId, name) as CustomCategoryResult.Saved).category`, `tx(categoryId, day)` = `transactionDao().insert(TransactionEntity(occurredEpochDay = day, amountMinor = 1000, kind = "EXPENSE", categoryId = categoryId))`.

```kotlin
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
```

  `TODAY = LocalDate.of(2026, 11, 25).toEpochDay()`. `MerchantRuleEntity`의 `direction` 값은 기존 `AutoEntryRepositoryTest`가 쓰는 `Direction.name` 값을 확인해 맞춘다.

- [ ] **Step 2: 실패 확인** — `GRADLE :app:testDebugUnitTest --tests '*CustomCategoryRepositoryTest*'` → 컴파일 실패.
- [ ] **Step 3: DAO 쿼리와 `CategoryLastUsedRow` 추가** (위 Interfaces 그대로).
- [ ] **Step 4: `BudgetRepository`에 다섯 함수와 두 타입 추가.** 생성자 시그니처는 바꾸지 않는다(`database.merchantRuleDao()`를 쓴다 — `deleteArchivedPeriod`가 `database.autoEntryDao()`를 쓰는 것과 같은 방식).
- [ ] **Step 5: 통과 확인** — `GRADLE :app:testDebugUnitTest --tests '*CustomCategoryRepositoryTest*'` → PASS.
- [ ] **Step 6: `AppDatabase.seedCategoriesIfEmpty` KDoc에 한 줄 추가** — "고정 대분류 아래의 고정 아닌 세부는 사용자가 만든 것이다. 시드·보충 로직은 지우거나 바꾸지 않는다." (코드 변경 없음)
- [ ] **Step 7: 빌드·테스트·커밋** (Global Constraints)

```bash
git add app/src/main/java/com/householdbudget/app/data/local/dao/CategoryDao.kt app/src/main/java/com/householdbudget/app/data/local/dao/TransactionDao.kt app/src/main/java/com/householdbudget/app/data/local/dao/MerchantRuleDao.kt app/src/main/java/com/householdbudget/app/data/local/model/CategoryLastUsedRow.kt app/src/main/java/com/householdbudget/app/data/repository/BudgetRepository.kt app/src/main/java/com/householdbudget/app/data/local/AppDatabase.kt app/src/test/java/com/householdbudget/app/data/repository/CustomCategoryRepositoryTest.kt
git commit -m "feat: 직접 추가 세부 카테고리 저장·이름 변경·삭제(거래와 학습 규칙 옮기기)"
```

---

### Task 3: 선택 화면에서 추가·편집·숨김 (ui)

**Files:**
- Modify: `app/src/main/java/com/householdbudget/app/ui/BudgetViewModel.kt`
- Modify: `app/src/main/java/com/householdbudget/app/ui/components/CategoryPicker.kt`
- Modify: `app/src/main/java/com/householdbudget/app/ui/components/CategorySearchSheet.kt`
- Create: `app/src/main/java/com/householdbudget/app/ui/components/CustomCategoryDialogs.kt`
- Modify: `app/src/main/java/com/householdbudget/app/ui/edit/EditTransactionScreen.kt` (선택기 호출 ~399행, 시트 ~451행)
- Modify: `app/src/main/java/com/householdbudget/app/ui/autoentry/PendingReviewScreen.kt` (선택기 ~305행, 시트 ~345행)
- Modify: `app/src/main/res/values/strings_components.xml`
- Test: `app/src/test/java/com/householdbudget/app/ui/components/CategoryPickerLogicTest.kt`

**Interfaces:**
- Consumes: Task 2의 `BudgetRepository` 함수 다섯 개, `CustomCategoryResult`, `CustomCategoryDeletePreview`; Task 1의 `FixedCategories.isFixedChild`, `CustomCategories.NameError`, `MAX_NAME_LENGTH`.
- Produces:

```kotlin
// BudgetViewModel
val hiddenCustomLeafIds: StateFlow<Set<Long>>   // repository.observeHiddenCustomCategoryIds(), 기존 stateIn 패턴, 초기값 emptySet()
suspend fun addCustomCategory(parentId: Long, name: String): CustomCategoryResult
suspend fun renameCustomCategory(id: Long, name: String): CustomCategoryResult
suspend fun customCategoryDeletePreview(id: Long): CustomCategoryDeletePreview?
suspend fun deleteCustomCategory(id: Long, targetId: Long): CategoryEntity?

// CategoryPicker.kt
internal fun isCustomLeaf(parent: CategoryEntity, leaf: CategoryEntity): Boolean
internal fun visibleLeaves(leaves: List<CategoryEntity>, hiddenLeafIds: Set<Long>, selectedLeafId: Long?): List<CategoryEntity>

@Composable fun CategoryPicker(
    parents: List<CategoryEntity>,
    childrenByParent: Map<Long, List<CategoryEntity>>,
    selectedLeafId: Long?,
    onLeafSelected: (CategoryEntity) -> Unit,
    modifier: Modifier = Modifier,
    onMore: (() -> Unit)? = null,
    hiddenLeafIds: Set<Long> = emptySet(),
    onAddCustom: ((parent: CategoryEntity) -> Unit)? = null,
    onEditCustom: ((leaf: CategoryEntity) -> Unit)? = null,
)
@Composable fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null)

// CategorySearchSheet.kt — 끝에 매개변수 하나 추가
onEditCustom: ((leaf: CategoryEntity) -> Unit)? = null

// CustomCategoryDialogs.kt
sealed interface CustomCategoryRequest {
    data class Add(val parent: CategoryEntity) : CustomCategoryRequest
    data class Edit(val leaf: CategoryEntity) : CustomCategoryRequest
}
@Composable fun CustomCategoryDialogs(
    request: CustomCategoryRequest?,
    budgetViewModel: BudgetViewModel,
    onDismiss: () -> Unit,
    onCreated: (CategoryEntity) -> Unit,
    onDeleted: (deleted: CategoryEntity, movedTo: CategoryEntity) -> Unit = { _, _ -> },
)
```

- [ ] **Step 1: 실패하는 테스트 작성** (`CategoryPickerLogicTest.kt`, 순수 JUnit — `TransactionRowTextsTest`와 같은 스타일)

```kotlin
private fun cat(id: Long, name: String, parentId: Long? = null) =
    CategoryEntity(id = id, name = name, kind = "EXPENSE", parentId = parentId, sortOrder = id.toInt())

@Test fun visibleLeaves_dropsHiddenButKeepsSelected() {
    val leaves = listOf(cat(1, "통신", 9), cat(2, "오래됨", 9), cat(3, "옛날", 9))
    assertEquals(listOf(1L), visibleLeaves(leaves, setOf(2, 3), null).map { it.id })
    assertEquals(listOf(1L, 3L), visibleLeaves(leaves, setOf(2, 3), 3).map { it.id })
    assertEquals(leaves, visibleLeaves(leaves, emptySet(), null))
}
@Test fun isCustomLeaf_falseForFixedNames() {
    val food = cat(9, "식비")
    assertFalse(isCustomLeaf(food, cat(1, "카페", 9)))
    assertTrue(isCustomLeaf(food, cat(2, "반려동물", 9)))
}
```

- [ ] **Step 2: 실패 확인** — `GRADLE :app:testDebugUnitTest --tests '*CategoryPickerLogicTest*'` → 컴파일 실패.

- [ ] **Step 3: 문자열 추가** (`strings_components.xml`, 문구는 이대로)

```xml
<!-- 직접 추가 세부 카테고리 (ui/components/CategoryPicker.kt, CustomCategoryDialogs.kt) -->
<string name="category_picker_add">+ 직접 추가</string>
<string name="custom_category_add_title">%1$s에 세부 항목 추가</string>
<string name="custom_category_edit_title">세부 항목 이름 바꾸기</string>
<string name="custom_category_name_hint">이름 (10자까지)</string>
<string name="custom_category_add_confirm">추가</string>
<string name="custom_category_error_empty">이름을 입력해 주세요</string>
<string name="custom_category_error_too_long">10자까지 쓸 수 있어요</string>
<string name="custom_category_error_duplicate">이미 있는 이름이에요</string>
<string name="custom_category_long_press_label">이름 바꾸기·삭제</string>
<string name="custom_category_delete_title">\'%1$s\' 항목을 삭제할까요?</string>
<string name="custom_category_delete_body_moved">거래 %1$d건은 \'%2$s\'%3$s 옮겨요.</string>
<string name="custom_category_delete_body_empty">거래가 없어서 바로 지워요.</string>
```

  "저장"·"삭제"·"취소"는 기존 `edit_save`·`edit_delete`·`edit_dismiss`를 쓴다. `%3$s`에는 기존 `roParticle(targetName)`(`ui/autoentry/PendingReviewScreen.kt`)을 넣는다.

- [ ] **Step 4: `CategoryPicker`·`CategoryChip`·`CategorySearchSheet` 수정**
  - 타일 탭: `visibleLeaves(...)` 기준으로 0 → 무반응, 1 → 바로 선택, 그 외 → 펼침(지금 로직에서 목록만 바꾼다).
  - 대분류 타일은 지금 그대로(길게 누르기 없음).
  - 펼친 상태: 뒤로 칩 → `visibleLeaves` 칩들 → `onAddCustom != null && FixedCategories.allowsCustomChildren(kind, expanded.name)`이면 마지막에 `category_picker_add` 칩(선택 안 됨, 누르면 `onAddCustom(expanded)`).
  - 세부 칩: `onEditCustom != null && isCustomLeaf(expanded, leaf)`일 때만 `onLongClick = { onEditCustom(leaf) }`.
  - `CategoryChip`: `onLongClick == null`이면 지금의 `FilterChip` 그대로. non-null이면 같은 모양(`CircleShape`, 1dp `outline` 테두리, 선택 시 `primaryContainer`/`onPrimaryContainer`, `labelLarge`)의 `Surface` + `combinedClickable(onLongClickLabel = custom_category_long_press_label)` + `minimumInteractiveComponentSize()`.
  - 시트: 목록은 전부 그대로(숨김 적용 안 함). 행의 `clickable`을 `onEditCustom != null && isCustomLeaf(parent, leaf)`일 때 `combinedClickable`로 바꾼다.

- [ ] **Step 5: `CustomCategoryDialogs` 작성과 두 화면 연결**
  - 대화상자 하나(`AlertDialog`, `shape = MaterialTheme.shapes.large` — 기존 삭제 확인과 같은 틀): 제목은 Add면 `custom_category_add_title`(대분류 이름), Edit면 `custom_category_edit_title`. 본문은 `OutlinedTextField`(한 줄, Edit면 현재 이름으로 시작, 입력은 `MAX_NAME_LENGTH`보다 길어도 받되 저장 때 오류 표시) + 오류 문구(`bodySmall`, `colorScheme.error`). 확인 버튼은 Add면 "추가", Edit면 "저장". Edit일 때만 "삭제"(`colorScheme.error`) 버튼 → `customCategoryDeletePreview` 결과로 확인 대화상자. 고른 옮길 곳은 `var targetId`(초기값 `defaultTargetId`). `transactionCount == 0`이면 `…_body_empty`만. 아니면 `…_body_moved`(고른 칩의 이름으로, 칩을 바꾸면 문구도 바뀜) 아래에 `targets`를 `FlowRow`의 `CategoryChip`(`selected = it.id == targetId`)으로 보여 준다.
  - 저장: `rememberCoroutineScope().launch` → `Saved`면 Add는 `onCreated(category)` 후 `onDismiss()`, Edit는 `onDismiss()`. `Invalid`면 오류 문구만 바꾸고 닫지 않는다. 저장·삭제 진행 중에는 버튼을 막는다.
  - 삭제 확인: `deleteCustomCategory(leaf.id, targetId)` 결과가 non-null이면 `onDeleted(leaf, movedTo)` 후 `onDismiss()`.
  - `EditTransactionScreen`: `var customRequest by remember { mutableStateOf<CustomCategoryRequest?>(null) }`. 선택기에 `hiddenLeafIds`(`budgetViewModel.hiddenCustomLeafIds`), `onAddCustom = { customRequest = Add(it) }`, `onEditCustom = { customRequest = Edit(it) }`; 시트에 `onEditCustom`. `onCreated = { vm.setParent(it.parentId!!, it) }`. **`onDeleted = { deleted, movedTo -> if (ui.categoryId == deleted.id) vm.setParent(movedTo.parentId!!, movedTo) }`** (Review Focus 5).
  - `PendingReviewScreen`: 같은 연결. `onCreated = { onResolve(it.id) }`(기존 `onLeafSelected`와 같은 동작). `onDeleted`는 기본값.

- [ ] **Step 6: 통과 확인** — `GRADLE :app:testDebugUnitTest --tests '*CategoryPickerLogicTest*'` → PASS.

- [ ] **Step 7: 자가 점검(코드 읽기)** — ① 고정 칩·고정 행·대분류 타일에는 길게 누르기가 붙지 않고, 유동 지출은 눌렀을 때 "기본"이 바로 선택된다 ② 두 화면 모두 `key(kind)` 안의 선택기에 새 인자가 들어갔다 ③ 새 코드에 `Color(0x`, `fontWeight =`, `fontSize =`, 하드코딩 한글 문자열이 없다(`grep -n "Color(0x\|fontWeight =\|fontSize =" app/src/main/java/com/householdbudget/app/ui/components/CustomCategoryDialogs.kt` → 출력 없음) ④ 새 터치 대상이 48dp 이상.

- [ ] **Step 8: 빌드·테스트·커밋** (Global Constraints)

```bash
git add app/src/main/java/com/householdbudget/app/ui/BudgetViewModel.kt app/src/main/java/com/householdbudget/app/ui/components/CategoryPicker.kt app/src/main/java/com/householdbudget/app/ui/components/CategorySearchSheet.kt app/src/main/java/com/householdbudget/app/ui/components/CustomCategoryDialogs.kt app/src/main/java/com/householdbudget/app/ui/edit/EditTransactionScreen.kt app/src/main/java/com/householdbudget/app/ui/autoentry/PendingReviewScreen.kt app/src/main/res/values/strings_components.xml app/src/test/java/com/householdbudget/app/ui/components/CategoryPickerLogicTest.kt
git commit -m "feat: 카테고리 선택 화면에서 세부 항목 직접 추가·이름 변경·삭제, 90일 안 쓴 항목 숨김"
```
