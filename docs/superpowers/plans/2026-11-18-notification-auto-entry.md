# 알림 자동 기록 + 고정 카테고리 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 은행·결제 앱 알림을 읽어 거래를 자동 등록하고, 카테고리를 앱이 정한 고정 목록으로 바꾸며, 반복 거래·카테고리 관리 기능을 없앤다.

**Architecture:** 순수 로직(고정 카테고리 목록, 옛 카테고리 매핑, 알림 파서, 이름 정규화, 분류기)은 Android 의존성이 없는 `core` 모듈에 두고 JVM 테스트로 검증한다. `app` 모듈은 Room 스키마를 v6 → v7(카테고리 이전·반복 규칙 삭제) → v8(자동 기록 테이블)로 올리고, `NotificationListenerService`가 받은 알림을 `AutoEntryRepository` 한 곳에서 직렬(Mutex + Room 트랜잭션)로 처리한다. 화면은 기존 설정 탭의 `SettingsPane` 방식에 "자동 기록"과 "확인 필요" 두 개를 더한다.

**Tech Stack:** Kotlin 2.0.21, Compose(BOM 2024.10.01), Room 2.6.1(KSP), DataStore, WorkManager 2.9.1, JUnit4, Robolectric 4.14.1(테스트 전용).

**Spec:** `docs/superpowers/specs/2026-11-18-notification-auto-entry-design.md` — 각 태스크 구현자는 자기 태스크 + 이 Global Constraints + 스펙을 함께 읽는다.

## Global Constraints

- minSdk 26, 타임존은 `ZoneId.of("Asia/Seoul")` 고정. 운영 코드에 신규 의존성을 추가하지 않는다(테스트 전용 의존성은 Task 3에서 정한 것만).
- 알림 원문·가맹점 이름·금액을 Logcat/`println`에 찍지 않는다. 저장은 Room에만 한다. 네트워크 호출을 추가하지 않는다.
- 저장소 루트의 `.xlsx/.xls/.numbers/.csv` 파일은 열거나 읽거나 `git add` 하지 않는다. `git add`는 항상 파일 경로를 지정한다(`git add -A`, `git add .` 금지).
- 테스트 픽스처는 가짜 데이터만 쓴다(이름 `김가짜`, 가맹점 `가짜커피`, 통장 `가짜통장(1234)` 식). 실제 알림 문구를 붙여 넣지 않는다.
- 기존 거래는 지우지 않는다. v6 → v7 마이그레이션 뒤 거래 수와 kind별 합계가 같아야 한다.
- 카테고리는 `FixedCategories.TREE`(Task 1)가 유일한 출처다. 사용자 추가·이름 변경·삭제·순서 변경 화면과 코드는 두지 않는다.
- UI 문구는 한국어, `res/values/strings_*.xml`에 둔다. 화면은 기존 컴포넌트(`ScreenHeader`, `SectionHeader`, `SettingsGroupPanel`, `SettingsActionRow`, `EmptyState`, `Space`)를 쓴다.
- 커밋 메시지는 한국어(`feat: …`, `refactor: …`, `test: …`), 태스크 하나에 커밋 하나 이상. 브랜치는 `feat/notification-auto-entry` 유지.
- 빌드·테스트 명령(저장소에 `./gradlew` 없음). 아래에서 `GRADLE`은 이 줄 전체를 뜻한다:
  `export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=~/Library/Android/sdk; ~/.gradle/wrapper/dists/gradle-8.10.2-bin/a04bxjujx95o3nb99gddekhwo/gradle-8.10.2/bin/gradle`
- app 단위 테스트(Robolectric)는 모두 `@RunWith(RobolectricTestRunner::class)` + `@Config(sdk = [34], application = android.app.Application::class)`를 붙인다. `BudgetApplication`이 뜨면 실제 DB·WorkManager를 건드리기 때문이다. 코루틴은 `runBlocking`으로 돌린다.
- 모델 배정: 각 태스크 제목의 `[Sonnet·기계적]` / `[Opus·까다로움]`을 따른다.

## Review Focus

스펙이 직접 테스트를 요구하지 않지만 실제 사용에서 가장 먼저 터질 입력들. 각 줄의 테스트는 괄호 안 태스크에 들어 있다.

1. 같은 알림이 내용 그대로 다시 게시된다(앱이 알림을 갱신, 그룹 요약 알림, key나 postTime만 다름) → 거래는 한 번만 생긴다. (Task 7 `sameContentWithin10s_isDuplicate`, Task 8 그룹 요약 건너뛰기)
2. 금융 앱의 광고·혜택 알림에 금액과 "결제"가 들어 있다, 또는 결제 금액 옆에 잔액·누적 금액이 같이 나온다 → 확인 필요에 쌓이지 않고, 잔액이 결제 금액으로 잡히지 않는다. (Task 4 `ad_returnsNull`, `balanceBeforeAmount_isSkipped`)
3. 지출로 학습시킨 이름(예: 친구)이 반대로 돈을 보내온다 → 지출로 자동 등록하지 않고 확인 필요로 보낸다. (Task 5 `learnedExpenseRule_onIncoming_needsReview`, Task 7 동일 흐름)
4. "중복 같아요" 항목을 사용자가 "기록 안 함"으로 정리한다 → 그 가맹점이 영구 무시 규칙이 되면 안 된다. (Task 7 `resolvingDuplicate_doesNotLearnRule`)
5. 기본 대분류 아래에 사용자가 만든 소분류, 또는 v4 이전 설치의 `식비>기본` 같은 잎에 거래가 있다 → "기타"로 섞이지 않고 그 대분류의 대표 소분류로 간다. (Task 1 `unknownChildUnderKnownParent_usesWildcard`, Task 3 `customChildUnderKnownParent_keepsParentMeaning`)

## PR #8 (`fix/category-ui-and-data-bugs`, 미병합) 처리

- PR #8이 건드리는 것: `BudgetRepository`의 반복 규칙 함수 2곳 + `tryArchiveCompletedPeriods`의 보관 범위 수정 1줄, `CategoryManagementScreen/ViewModel`, `EditTransactionScreen`의 날짜 선택 UTC 수정, 문자열 `category_ok`·`category_more_actions`.
- 이 계획은 `EditTransactionScreen.kt`를 수정하지 않는다(충돌 없음). 반복 규칙 함수와 카테고리 관리 화면은 Task 2에서 통째로 지우므로 PR #8의 그 부분은 버려진다.
- **Task 2 시작 전** 확인: `git fetch origin && git merge-base --is-ancestor origin/fix/category-ui-and-data-bugs origin/master && echo MERGED`. `MERGED`면 `git merge origin/master`로 먼저 받아온 뒤 Task 2를 한다(그러면 지울 파일에 충돌이 남지 않는다).
- PR #8이 이 브랜치보다 나중에 병합되면 충돌 해소 규칙: 지운 파일은 삭제 유지, `BudgetRepository`는 `tryArchiveCompletedPeriods`의 `if (startEx < lastSeen) return@repeat` 줄만 살리고 반복 규칙 쪽은 버림, 문자열 `category_ok`·`category_more_actions`는 삭제.

## 파일 구조

| 파일 | 책임 | 태스크 |
|---|---|---|
| `core/.../domain/category/FixedCategories.kt` | 고정 카테고리 트리 | 1 |
| `core/.../domain/category/LegacyCategoryMapping.kt` | 옛 (대분류, 소분류) → 새 경로 | 1 |
| `app/.../data/local/Migration6To7.kt` | 카테고리 이전, 예산 합치기, `recurring_rules` 삭제 | 3 |
| `core/.../domain/autoentry/NotificationParsers.kt` | 공통 파서 + 케이뱅크 입금 파서 | 4 |
| `core/.../domain/autoentry/MerchantNormalizer.kt` | 이름 정규화 | 4 |
| `core/.../domain/autoentry/KnownApps.kt` | 금융 앱 패키지, 충전 키워드 표 | 5 |
| `core/.../domain/autoentry/AutoClassifier.kt` | 분류 규칙 | 5 |
| `app/.../data/local/entity/{NotificationLog,AutoEntry,MerchantRule}Entity.kt`, `dao/…Dao.kt` | v8 테이블 | 6 |
| `app/.../data/repository/AutoEntryRepository.kt` | 알림 1건 처리, 중복 방지, 확인 필요 처리, 규칙, 로그 정리 | 7 |
| `app/.../autoentry/NotificationCaptureService.kt`, `WatchedApps.kt` | 알림 수신, 대상 앱 기본값 | 8 |
| `app/.../data/work/DailyMaintenanceWorker.kt`, `DailyMaintenanceScheduler.kt` | 기간 보관 + 로그 정리 | 2, 8 |
| `app/.../ui/autoentry/AutoEntrySettingsScreen.kt`, `InstalledApps.kt` | 자동 기록 설정 | 9 |
| `app/.../ui/autoentry/PendingReviewScreen.kt` | 확인 필요 | 10 |

경로의 `core/...` = `core/src/main/kotlin/com/householdbudget/app`, `app/...` = `app/src/main/java/com/householdbudget/app`. 테스트는 `core/src/test/kotlin/...`, `app/src/test/java/...`의 같은 패키지.

---

### Task 1: 고정 카테고리 목록과 옛 카테고리 매핑 (core) `[Sonnet·기계적]`

**Files:**
- Create: `core/.../domain/category/FixedCategories.kt`, `core/.../domain/category/LegacyCategoryMapping.kt`
- Test: `core/src/test/kotlin/com/householdbudget/app/domain/category/FixedCategoriesTest.kt`, `LegacyCategoryMappingTest.kt`

**Interfaces:**
- Consumes: `com.householdbudget.app.domain.CategoryKind` (기존).
- Produces:
  ```kotlin
  package com.householdbudget.app.domain.category
  data class FixedParent(val kind: CategoryKind, val name: String, val icon: String, val children: List<String>)
  data class CategoryPath(val kind: CategoryKind, val parent: String, val child: String)
  object FixedCategories {
      val TREE: List<FixedParent>
      fun otherLeaf(kind: CategoryKind): CategoryPath   // EXPENSE→기타>기타, INCOME→수입>기타, SAVINGS→저축>기타
  }
  data class LegacyTarget(val path: CategoryPath, val tagMemo: Boolean)
  object LegacyCategoryMapping { fun map(kind: CategoryKind, parent: String, child: String): LegacyTarget }
  ```

`TREE` 값(이 순서 그대로. 수입·저축은 대분류 하나씩으로 결정):

| kind | 대분류 | 아이콘 | 소분류 |
|---|---|---|---|
| EXPENSE | 식비 | 🍽️ | 식사, 카페, 간식, 배달, 장보기 |
| EXPENSE | 교통 | 🚌 | 대중교통, 택시, 주유/주차 |
| EXPENSE | 생활 | 🏠 | 통신, 공과금, 생활용품, 구독 |
| EXPENSE | 쇼핑 | 🛒 | 의류/미용, 온라인쇼핑, 간편결제 충전 |
| EXPENSE | 여가 | 🎭 | 문화, 여행, 술/모임 |
| EXPENSE | 기타 | 📦 | 의료, 경조사/선물, 기타 |
| EXPENSE | 유동 지출 | 💸 | 기본 |
| INCOME | 수입 | 💰 | 월급, 캐시백/이자, 용돈, 기타 |
| SAVINGS | 저축 | 🏦 | 적금, 투자, 연금, 청약, 기타 |

매핑표(정확히 일치하는 소분류 먼저, 없으면 `*`, 대분류도 없으면 `otherLeaf(kind)`):

| kind | 옛 대분류 | 옛 소분류 → 새 경로 |
|---|---|---|
| EXPENSE | 식비 | 식사→식비>식사 · 카페→식비>카페 · 간식→식비>간식 · 배달→식비>배달 · 술/모임→여가>술/모임 · `*`→식비>식사 |
| EXPENSE | 생활/마트 | 장보기→식비>장보기 · `*`→생활>생활용품 |
| EXPENSE | 주거/공과금 | `*`→생활>공과금 |
| EXPENSE | 통신 | 구독 서비스→생활>구독 · `*`→생활>통신 |
| EXPENSE | 교통 | 택시→교통>택시 · `*`→교통>대중교통 |
| EXPENSE | 차량 | 보험/자동차세→기타>기타 · `*`→교통>주유/주차 |
| EXPENSE | 쇼핑 | 의류/패션→쇼핑>의류/미용 · 뷰티/미용→쇼핑>의류/미용 · `*`→쇼핑>온라인쇼핑 |
| EXPENSE | 문화/여가 | `*`→여가>문화 |
| EXPENSE | 여행 | `*`→여가>여행 |
| EXPENSE | 의료 | `*`→기타>의료 |
| EXPENSE | 경조사/선물 | `*`→기타>경조사/선물 |
| EXPENSE | 기타 | `*`→기타>기타 |
| INCOME | 월급 | `*`→수입>월급 |
| INCOME | 부수입 | 이자/배당→수입>캐시백/이자 · 캐시백/포인트→수입>캐시백/이자 · 용돈→수입>용돈 · `*`→수입>기타 |
| INCOME | 기타 수입 | `*`→수입>기타 |
| SAVINGS | 적금/예금 | 적금→저축>적금 · `*`→저축>기타 |
| SAVINGS | 투자 | `*`→저축>투자 |
| SAVINGS | 연금 | `*`→저축>연금 |
| SAVINGS | 청약 | `*`→저축>청약 |
| SAVINGS | 저축 | 투자→저축>투자 · 연금저축→저축>연금 · 청약→저축>청약 · `*`→저축>기타 |

`tagMemo` 규칙: 새 소분류가 `기타`이면 `true`. 단 옛 경로가 `기타>기본` 또는 `기타 수입>기본`이면 `false`(원래 기타였으므로 흔적이 필요 없다). 그 외는 `false`.

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
// FixedCategoriesTest
@Test fun tree_hasNineParentsAndThirtyOneLeaves() {
    assertEquals(9, FixedCategories.TREE.size)
    assertEquals(31, FixedCategories.TREE.sumOf { it.children.size })
}
@Test fun floatingExpense_hasSingleDefaultLeaf() {
    val p = FixedCategories.TREE.single { it.name == "유동 지출" }
    assertEquals(CategoryKind.EXPENSE, p.kind); assertEquals(listOf("기본"), p.children)
}
@Test fun otherLeaf_existsInTreeForEveryKind() {
    for (k in CategoryKind.entries) {
        val o = FixedCategories.otherLeaf(k)
        assertTrue(FixedCategories.TREE.any { it.kind == k && it.name == o.parent && o.child in it.children })
    }
}
// LegacyCategoryMappingTest — E/I/S = CategoryKind.EXPENSE/INCOME/SAVINGS
private fun t(k: CategoryKind, p: String, c: String, tag: Boolean) = LegacyTarget(CategoryPath(k, p, c), tag)
@Test fun specExamples() {
    assertEquals(t(E, "식비", "장보기", false), map(E, "생활/마트", "장보기"))
    assertEquals(t(E, "교통", "주유/주차", false), map(E, "차량", "주유"))
    assertEquals(t(E, "생활", "공과금", false), map(E, "주거/공과금", "수도"))
    assertEquals(t(E, "생활", "통신", false), map(E, "통신", "휴대폰"))
    assertEquals(t(E, "생활", "구독", false), map(E, "통신", "구독 서비스"))
    assertEquals(t(E, "기타", "의료", false), map(E, "의료", "약국"))
}
@Test fun unmapped_goesToOtherLeafWithTag() {
    assertEquals(t(E, "기타", "기타", true), map(E, "교육", "도서"))
    assertEquals(t(E, "기타", "기타", true), map(E, "취미생활", "레고"))
    assertEquals(t(I, "수입", "기타", true), map(I, "부수입", "중고 판매"))
    assertEquals(t(S, "저축", "기타", true), map(S, "비상금", "기본"))
}
@Test fun plainOther_isNotTagged() {
    assertEquals(t(E, "기타", "기타", false), map(E, "기타", "기본"))
    assertEquals(t(I, "수입", "기타", false), map(I, "기타 수입", "기본"))
}
@Test fun unknownChildUnderKnownParent_usesWildcard() {
    assertEquals(t(E, "식비", "식사", false), map(E, "식비", "야식"))
    assertEquals(t(E, "교통", "대중교통", false), map(E, "교통", "기본"))
}
@Test fun sameNameDifferentKind_doesNotCross() {
    assertEquals(t(I, "수입", "기타", true), map(I, "식비", "식사"))
}
@Test fun everyTarget_existsInFixedTree() { /* 위 표의 모든 (kind, 옛 대분류, 옛 소분류/임의 문자열) 결과가 TREE에 있는 경로인지 */ }
```

- [ ] **Step 2: 실패 확인** — Run: `GRADLE :core:test --tests "*category*"` → Expected: 컴파일 실패(`FixedCategories` 없음).
- [ ] **Step 3: `FixedCategories`, `LegacyCategoryMapping` 구현** — 위 두 표를 그대로 데이터로 옮긴다. 매핑은 `Map<Pair<CategoryKind, String>, Map<String, CategoryPath>>` 한 개, `"*"` 키가 와일드카드.
- [ ] **Step 4: 통과 확인** — Run: `GRADLE :core:test` → Expected: `BUILD SUCCESSFUL`, 기존 `PeriodResolverTest` 포함 전부 통과.
- [ ] **Step 5: 커밋** — `git add core/src/main/kotlin/com/householdbudget/app/domain/category core/src/test/kotlin/com/householdbudget/app/domain/category && git commit -m "feat: 고정 카테고리 목록과 옛 카테고리 매핑표 추가"`

---

### Task 2: 반복 거래·카테고리 관리 기능 제거 (스키마는 그대로) `[Sonnet·기계적]`

스키마 v6은 이 태스크에서 바꾸지 않는다. `RecurringRuleEntity`는 `@Database(entities)`에 남겨 두고(Task 3에서 제거), 코드에서 쓰는 곳만 없앤다. 시작 전에 위 "PR #8 처리"의 확인 명령을 실행한다.

**Files:**
- Delete: `app/.../ui/recurring/`(3개), `app/.../ui/settings/categories/`(2개), `app/.../data/work/RecurringRuleWorker.kt`, `app/.../data/work/RecurringWorkScheduler.kt`, `app/.../data/local/dao/RecurringRuleDao.kt`
- Create: `app/.../data/work/DailyMaintenanceWorker.kt`, `app/.../data/work/DailyMaintenanceScheduler.kt`
- Modify: `data/repository/BudgetRepository.kt`, `AppContainer.kt`, `BudgetApplication.kt`, `data/local/AppDatabase.kt`, `data/local/dao/CategoryDao.kt`, `data/local/dao/TransactionDao.kt`, `data/preferences/UserPreferencesRepository.kt`, `ui/settings/SettingsScreen.kt`, `ui/MainTabScaffold.kt`, `ui/stats/StatsScreen.kt`, `ui/stats/StatsViewModel.kt`, `ui/settings/BudgetSettingsScreen.kt`, `ui/archive/ArchiveDetailScreen.kt`, `res/values/strings.xml`, `strings_more.xml`, `strings_stats.xml`

**Interfaces:**
- Produces:
  - `class DailyMaintenanceWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker` — `doWork()`는 `budgetRepository.tryArchiveCompletedPeriods()`만 호출(실패 시 `Result.retry()`, 기존과 동일).
  - `object DailyMaintenanceScheduler { fun schedule(context: Context) }`
  - `SettingsScreen(budgetViewModel, modifier, onOpenArchive, onOpenBudget, onExportCsv)` — `onOpenRecurringRules`, `onOpenCategoryManagement` 파라미터 삭제.
  - `BudgetRepository(database, transactionDao, categoryDao, archivedPeriodDao, categoryBudgetDao, userPreferencesRepository, periodResolver, zoneId)` — `recurringRuleDao` 파라미터 삭제.
  - 문자열 `action_back`("뒤로") — `recurring_back`을 대체.

- [ ] **Step 1: 워커 교체** — `DailyMaintenanceScheduler.schedule`은 먼저 `WorkManager.cancelUniqueWork("household_budget_recurring_rules")`를 부르고(업데이트한 기기에 남은 옛 주기 작업이 삭제된 `RecurringRuleWorker` 클래스를 찾다 실패하지 않게), 이어서 고유 이름 `"household_budget_daily_maintenance"`, `ExistingPeriodicWorkPolicy.KEEP`, 주기 1일로 `DailyMaintenanceWorker`를 등록한다. `BudgetApplication.onCreate`는 `tryArchiveCompletedPeriods()` 호출과 `DailyMaintenanceScheduler.schedule(this)`, 리마인더 블록만 남긴다(`topUpDefaultCategories`, `categorySeedVersion`, `applyDueRecurringRules` 호출 삭제).
- [ ] **Step 2: 저장소·DAO 정리** — `BudgetRepository`에서 삭제: 반복 규칙 함수 전부(`observeRecurringRules`~`applyDueRecurringRules`, `buildAutoMemo`, `clampDayInMonth`, `YM_FORMAT`), 카테고리 관리 함수 전부(`addParentCategory`, `addLeafCategory`, `renameCategory`, `deleteCategory`, `moveLeaf`, `reorderTopLevel`, `reorderChildren`), `topUpDefaultCategories`, `DEFAULT_LEAF_NAME`, `CategoryDeletionResult`, `CategoryValidationError`, `ValidationException`, `validationError()`. `tryArchiveCompletedPeriods`는 손대지 않는다. `AppDatabase`에서 `recurringRuleDao()`, `topUpDefaultCategories`, `CATEGORY_SEED_VERSION` 삭제(`DEFAULT_CATEGORY_SEEDS`와 `seedCategoriesIfEmpty`는 Task 3까지 유지). `UserPreferencesRepository`에서 `categorySeedVersion`, `setCategorySeedVersion`, 키 삭제. `CategoryDao.countRecurringRulesForLeaf`, `TransactionDao.deleteByCategoryId`와, 지운 뒤 호출자가 없어진 `CategoryDao` 메서드를 삭제한다(`count`, `insert`, `observeAll`, `getById`는 남긴다).
- [ ] **Step 3: 화면 정리** — `MainTabScaffold`: `SettingsPane.Categories/RecurringList/RecurringEdit`, `recurringAddNonce`, 해당 `when` 가지·import·`BackHandler` 가지 삭제. `SettingsScreen`: 반복 규칙 행(자동화 그룹 첫 행과 그 뒤 구분선), 카테고리 관리 행 삭제 — 데이터 그룹은 보관 기록 행이 첫 행이 되므로 그 앞 `SettingsRowDivider()`도 함께 정리한다. `StatsScreen`: `item(key = "upcoming")`, `UpcomingRulesCard`, `nextOccurrence`, `recurringRules` 수집 삭제. `StatsViewModel.recurringRules` 삭제. `BudgetSettingsScreen`, `ArchiveDetailScreen`의 `R.string.recurring_back` → `R.string.action_back`.
- [ ] **Step 4: 문자열 정리** — 삭제: `recurring_*` 전부, `settings_open_recurring`, `settings_recurring_subtitle`, `settings_open_categories*`, `category_*` 전부(`strings.xml`의 "카테고리 관리" 블록과 `strings_more.xml`의 `category_move_up`~`category_collapse`), `settings_kbank_card_*`, `edit_cashback_*`, `stats_upcoming_*`. 추가: `<string name="action_back">뒤로</string>`. `budget_settings_empty_categories` 값은 `지출 대분류가 없어요.`로 바꾼다. `edit_category_*`, `stats_category_*`, `budget_settings_category_*`는 쓰이므로 남긴다.
- [ ] **Step 5: 검증** — Run: `GRADLE :app:assembleDebug` → Expected: `BUILD SUCCESSFUL`.
  Run: `grep -rnEi "recurring|CategoryManagement|kbank_card|cashback|topUp|categorySeedVersion|upcoming" app/src/main | grep -v -E "AppDatabase.kt|RecurringRuleEntity.kt|DailyMaintenanceScheduler.kt"` → Expected: 출력 없음(스케줄러에는 취소할 옛 작업 이름 문자열이 남는다).
- [ ] **Step 6: 커밋** — 삭제는 `git rm -r app/src/main/java/com/householdbudget/app/ui/recurring app/src/main/java/com/householdbudget/app/ui/settings/categories` 및 개별 `git rm`, 수정·신규는 경로를 지정해 `git add`. `git commit -m "refactor: 반복 거래와 카테고리 관리 기능 제거"`

---

### Task 3: Room v6 → v7 마이그레이션 + 고정 목록 시드 + 테스트 기반 `[Opus·까다로움]`

**Files:**
- Create: `app/.../data/local/Migration6To7.kt`
- Modify: `app/.../data/local/AppDatabase.kt`, `app/.../data/local/dao/CategoryDao.kt`, `app/build.gradle.kts`
- Delete: `app/.../data/local/entity/RecurringRuleEntity.kt`
- Test: `app/src/test/java/com/householdbudget/app/data/local/V6Schema.kt`, `Migration6To7Test.kt`

**Interfaces:**
- Consumes: Task 1의 `FixedCategories.TREE`, `LegacyCategoryMapping.map`, `CategoryPath`.
- Produces:
  - `val MIGRATION_6_7: Migration` (`Migration6To7.kt` 최상위)
  - `AppDatabase.ALL_MIGRATIONS: Array<Migration>` (`internal`, companion) — 1→2 … 6→7 순서. `getInstance`도 이것을 쓴다.
  - `AppDatabase.seedCategoriesIfEmpty(categoryDao: CategoryDao)` (`internal suspend`, companion) — 비어 있으면 `FixedCategories.TREE`를 넣는다(대분류 `sort_order` = 목록 순번, 소분류 = 대분류 안 순번, 아이콘은 대분류만).
  - `CategoryDao.findLeafByPath(kind: String, parentName: String, childName: String): CategoryEntity?` — `SELECT c.* FROM categories c JOIN categories p ON p.id = c.parent_id WHERE c.kind = :kind AND p.name = :parentName AND c.name = :childName LIMIT 1`
  - 테스트 도우미 `object V6Schema { fun create(context: Context, name: String): SupportSQLiteDatabase }` — `FrameworkSQLiteOpenHelperFactory`로 version 6 DB를 만들고 v6 테이블 5개(`categories`, `transactions`, `recurring_rules`, `archived_periods`, `category_budgets`)와 인덱스를 생성해 돌려준다. DDL은 `AppDatabase.kt`의 `MIGRATION_3_4`~`MIGRATION_5_6`이 만든 최종 형태와 똑같이 쓴다.

**테스트 방식 결정:** 마이그레이션은 에뮬레이터 `androidTest`가 아니라 **Robolectric JVM 테스트**로 검증한다. 서브에이전트가 에뮬레이터 부팅 없이 `GRADLE :app:testDebugUnitTest`로 반복 실행할 수 있고, `exportSchema = false`라 `MigrationTestHelper`용 v6 스키마 JSON이 없어도 `V6Schema`로 손수 만든 v6 DB를 Room으로 열면 Room이 v7 엔티티와 스키마를 대조해 준다. 실제 기기 확인은 Task 11이 맡는다. 첫 실행은 Robolectric이 android-all jar를 내려받으므로 네트워크가 필요하다.

- [ ] **Step 1: 테스트 설정 추가** (`app/build.gradle.kts`)
  ```kotlin
  android { testOptions { unitTests { isIncludeAndroidResources = true } } }
  dependencies {
      testImplementation("junit:junit:4.13.2")
      testImplementation("org.robolectric:robolectric:4.14.1")
      testImplementation("androidx.test:core:1.6.1")
      testImplementation("androidx.test.ext:junit:1.2.1")
  }
  ```
- [ ] **Step 2: 실패하는 테스트 작성** — `Migration6To7Test`. 공통 준비 `seedV6(db)`: `AppDatabase.kt`의 현재 `DEFAULT_CATEGORY_SEEDS` 트리 전체(이 태스크에서 지워지므로 테스트 파일 안에 복사)를 넣고, 추가로 사용자 대분류 `취미생활>레고`(EXPENSE), 사용자 소분류 `식비>야식`을 넣는다. 거래(모두 `occurred_epoch_day = 20000`):

  | 옛 카테고리 | kind | 금액 | 메모 |
  |---|---|---|---|
  | 식비>식사 | EXPENSE | 9000 | 점심 |
  | 생활/마트>장보기 | EXPENSE | 32000 | (빈 문자열) |
  | 교육>도서 | EXPENSE | 15000 | 책 |
  | 교육>강의/학원 | EXPENSE | 50000 | (빈 문자열) |
  | 취미생활>레고 | EXPENSE | 70000 | 선물용 |
  | 식비>야식 | EXPENSE | 18000 | 치킨 |
  | 기타>기본 | EXPENSE | 1000 | 잡비 |
  | 월급>기본 | INCOME | 3000000 | 11월 |
  | 적금/예금>파킹통장 | SAVINGS | 200000 | (빈 문자열) |

  반복 규칙 1행(`category_id` = 주거/공과금>월세/관리비). 예산: 식비 100000, 생활/마트 50000, 차량 30000, 교통 20000, 교육 10000(모두 `enabled = 1`).

  DB는 `Room.databaseBuilder(ctx, AppDatabase::class.java, name).addMigrations(*AppDatabase.ALL_MIGRATIONS).allowMainThreadQueries().build()`로 열고 `openHelper.writableDatabase`를 한 번 호출해 마이그레이션을 일으킨다.
  ```kotlin
  @Test fun transactionCountAndSumsByKind_arePreserved()
  // COUNT(*) == 9; SUM(amount_minor) GROUP BY kind == EXPENSE 195000, INCOME 3000000, SAVINGS 200000
  @Test fun categories_equalFixedTreeExactly()
  // (kind, 대분류, 소분류) 집합 == FixedCategories.TREE 를 편 집합; 대분류 9행, 소분류 31행
  @Test fun everyTransaction_pointsToLeafOfSameKind()
  // transactions t JOIN categories c: c.parent_id IS NOT NULL AND c.kind = t.kind 가 9건 전부
  @Test fun mappedTransactions_landOnSpecTargets()
  // 32000 → 식비>장보기, memo ""; 9000 → 식비>식사, memo "점심"; 200000 → 저축>기타, memo "[적금/예금>파킹통장]"
  @Test fun unmapped_goToOther_withOriginalNameInMemo()
  // 15000 → 기타>기타, memo "[교육>도서] 책"; 50000 → memo "[교육>강의/학원]"; 70000 → memo "[취미생활>레고] 선물용"; 1000 → 기타>기타, memo "잡비"
  @Test fun customChildUnderKnownParent_keepsParentMeaning()
  // 18000 → 식비>식사, memo "치킨"
  @Test fun recurringRulesTable_isDropped()
  // SELECT COUNT(*) FROM sqlite_master WHERE name = 'recurring_rules' == 0
  @Test fun budgets_followMajorityAndMerge()
  // 식비 100000; 생활 50000(생활/마트의 잎 3개 중 2개가 생활); 교통 50000(차량 30000 + 교통 20000); 기타 10000; 그 외 대분류는 행 없음
  @Test fun emptyV6Database_getsFixedTree()
  // 카테고리·거래 0행인 v6 DB → 대분류 9, 소분류 31
  @Test fun freshInstallSeed_equalsFixedTree()
  // Room.inMemoryDatabaseBuilder + AppDatabase.seedCategoriesIfEmpty → 같은 집합, 두 번 불러도 행 수 불변
  ```
- [ ] **Step 3: 실패 확인** — Run: `GRADLE :app:testDebugUnitTest --tests "*Migration6To7Test"` → Expected: 컴파일 실패(`ALL_MIGRATIONS` 없음).
- [ ] **Step 4: `MIGRATION_6_7` 구현** — 순서가 결과를 정하므로 아래 그대로 한다(Room은 마이그레이션을 한 트랜잭션에서 돌리므로 중간 실패는 롤백된다).
  1. 옛 카테고리 전부를 메모리로 읽는다(`id, name, kind, parent_id`). 커서를 연 채로 INSERT하지 않는다.
  2. `FixedCategories.TREE`를 새 행으로 INSERT하고 `CategoryPath → 새 id` 맵을 만든다. 새 id는 AUTOINCREMENT라 모든 옛 id보다 크다 — 그래서 3번의 옛 id별 UPDATE가 서로 겹치지 않는다.
  3. 옛 잎(= `parent_id`가 있는 행)마다 `LegacyCategoryMapping.map(kind, 대분류 이름, 잎 이름)`으로 대상을 구해 `UPDATE transactions SET category_id = ?, memo = … WHERE category_id = ?`. `tagMemo`면 메모를 `[대분류>소분류] 메모`로, 원래 메모가 빈 문자열이면 `[대분류>소분류]`만. 거래가 옛 대분류 id에 직접 달려 있으면 `map(kind, 이름, "기본")`으로 같은 처리. `transactions.kind`는 건드리지 않는다.
  4. 예산: 옛 예산 행마다 대상 새 대분류를 구한다 — 옛 대분류의 예산이면 그 대분류의 잎들이 매핑되는 새 대분류 중 잎 수가 가장 많은 것(동률이면 `TREE`에서 앞선 것, 잎이 없으면 `map(kind, 이름, "기본")`의 대분류), 잎에 달린 예산이면 그 잎이 매핑되는 새 대분류. `category_budgets`를 비우고 대상별로 금액 합계, `enabled`는 하나라도 1이면 1로 다시 넣는다.
  5. `DROP TABLE recurring_rules`.
  6. 1번에서 읽은 옛 카테고리 id를 전부 DELETE(잎 먼저, 그다음 대분류).
- [ ] **Step 5: `AppDatabase` 갱신** — `version = 7`, `entities`에서 `RecurringRuleEntity` 제거 후 파일 삭제, `DEFAULT_CATEGORY_SEEDS`/`DefaultSeed` 삭제, `seedCategoriesIfEmpty`를 `FixedCategories.TREE` 기반 `internal`로, `ALL_MIGRATIONS` 추가, `CategoryDao.findLeafByPath` 추가.
- [ ] **Step 6: 통과 확인** — Run: `GRADLE :app:testDebugUnitTest --tests "*Migration6To7Test"` → Expected: 10개 통과. Run: `GRADLE :app:assembleDebug` → `BUILD SUCCESSFUL`.
- [ ] **Step 7: 커밋** — `git add app/build.gradle.kts app/src/main/java/com/householdbudget/app/data/local app/src/test/java/com/householdbudget/app/data/local && git commit -m "feat: 고정 카테고리로 옮기는 v7 마이그레이션과 반복 규칙 테이블 삭제"`

---

### Task 4: 알림 파서와 이름 정규화 (core) `[Opus·까다로움]`

**Files:**
- Create: `core/.../domain/autoentry/NotificationParsers.kt`, `core/.../domain/autoentry/MerchantNormalizer.kt`
- Test: `core/src/test/kotlin/com/householdbudget/app/domain/autoentry/NotificationParsersTest.kt`, `MerchantNormalizerTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  package com.householdbudget.app.domain.autoentry
  enum class Direction { OUT, IN, REFUND, UNKNOWN }
  data class ParsedNotification(val amountMinor: Long, val direction: Direction, val merchant: String)
  object NotificationParsers {
      const val KBANK_PACKAGE = "com.kbankwith.smartbank"
      /** body = bigText가 비어 있지 않으면 bigText, 아니면 text. 금액을 못 찾으면 null. */
      fun parse(packageName: String, title: String, body: String): ParsedNotification?
  }
  object MerchantNormalizer { fun normalize(name: String): String }
  ```

공통 파서 규칙(테스트만으로는 정해지지 않는 부분이라 그대로 적는다). `full = title + "\n" + body`.
1. **광고 차단**: `full`에 `(광고)` 또는 `수신거부`가 있으면 `null`.
2. **금액**: 정규식 `(\d{1,3}(?:,\d{3})+|\d+)\s*원`의 일치를 앞에서부터 본다. 일치 위치 앞 8글자(같은 줄 안)에 `잔액`, `누적`, `한도`, `잔여` 중 하나가 있으면 건너뛴다. 값이 0이면 건너뛴다. 남는 첫 일치가 금액, 없으면 `null`.
3. **방향**: `full`에서 `입출금`을 지운 문자열로 판단한다(통장 이름 `입출금통장`이 출금으로 읽히지 않게). `취소`·`환불` → `REFUND`, 아니면 `입금`·`받았`·`받음`·`캐시백` → `IN`, 아니면 `승인`·`결제`·`출금`·`이체`·`송금`·`보냈`·`보냄`·`충전`·`납부` → `OUT`, 아니면 `UNKNOWN`. (`이자`는 `이자카야` 때문에 방향 키워드로 쓰지 않는다.)
4. **이름**: 제목에 금액도 방향 키워드도 없으면(제목이 그냥 앱·카드 이름) 본문만, 있으면 `full`을 대상으로 한다. 차례로 지운다 — 2번 정규식의 모든 일치 → 날짜 `\d{4}[./-]\d{1,2}[./-]\d{1,2}`, `\d{1,2}월\s*\d{1,2}일`, `\d{1,2}[/.]\d{1,2}(?!\d)` → 시각 `\d{1,2}:\d{2}(?::\d{2})?` → 마스킹 토큰 `\S*\*+\S*` → `\(\d{3,4}\)` → 통장 이름 `\S*(?:통장|계좌)` → 키워드 `입출금, 체크카드, 신용카드, 일시불, 승인, 결제, 출금, 이체, 송금, 입금, 취소, 환불, 충전, 납부, 잔액, 누적, 완료`. 남은 문자열을 `|`와 줄바꿈으로 나누고, 조각마다 연속 공백을 하나로 줄이고 양끝의 공백과 `[]()·,.:-`를 떼어낸 뒤 **가장 긴 조각**(동률이면 앞의 것)을 이름으로 한다. 없으면 `""`.

케이뱅크 입금 파서(공통 파서보다 먼저): `packageName == KBANK_PACKAGE`이고 제목이 `^입금\s+([\d,]+)원$`에 맞고 본문에 `|`가 있으면 → 금액 = 제목의 숫자, `IN`, 이름 = 본문을 `|`로 나눈 첫 조각을 trim. 조건이 하나라도 안 맞으면 공통 파서로 넘긴다.

정규화: `(주)`, `㈜`, `주식회사`를 지우고 → 괄호 문자 `()[]{}`를 지우고(안의 글자는 남김) → 모든 공백을 지우고 → `lowercase(Locale.ROOT)`.

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
private fun p(title: String, body: String, pkg: String = "com.fake.bank") = NotificationParsers.parse(pkg, title, body)
@Test fun cardApproval_multiLine() =
    assertEquals(ParsedNotification(12000, OUT, "별다방 강남점"), p("가짜카드", "승인 12,000원\n별다방 강남점\n11/18 12:34 잔액 345,000원"))
@Test fun balanceBeforeAmount_isSkipped() =
    assertEquals(ParsedNotification(3300, OUT, "가나다마트"), p("가짜은행", "잔액 345,000원 | 출금 3,300원 | 가나다마트"))
@Test fun onlyBalance_returnsNull() = assertNull(p("가짜은행", "잔액 10,000원 안내"))
@Test fun genericDeposit() =
    assertEquals(ParsedNotification(50000, IN, "김가짜"), p("입금 50,000원", "김가짜 | 생활비통장(1234)"))
@Test fun refund_winsOverPaymentKeyword() =
    assertEquals(ParsedNotification(8900, REFUND, "테스트상점"), p("가짜카드", "결제 취소 8,900원\n테스트상점"))
@Test fun amountWithoutDirection_isUnknown() =
    assertEquals(ParsedNotification(7000, UNKNOWN, "테스트상점"), p("가짜은행", "7,000원 테스트상점"))
@Test fun accountNameWithChulgeum_doesNotMeanOut() =
    assertEquals(ParsedNotification(5000, UNKNOWN, "김가짜"), p("가짜은행", "5,000원 김가짜 입출금통장(1234)"))
@Test fun izakaya_isNotIncoming() =
    assertEquals(ParsedNotification(35000, OUT, "가짜이자카야"), p("가짜카드", "승인 35,000원\n가짜이자카야"))
@Test fun maskedCardToken_isRemovedFromMerchant() =
    assertEquals(ParsedNotification(4500, OUT, "테스트커피"), p("가짜카드", "가짜카드(1*3*) 승인 4,500원 일시불\n테스트커피"))
@Test fun chargeKeepsPayName() =
    assertEquals(ParsedNotification(10000, OUT, "네이버페이"), p("출금 10,000원", "네이버페이 충전 | 가짜통장(1234)"))
@Test fun noAmount_returnsNull() = assertNull(p("로그인 알림", "새 기기에서 로그인했어요"))
@Test fun ad_returnsNull() = assertNull(p("(광고) 가짜카드", "지금 결제하면 5,000원 할인\n수신거부: 설정 > 알림"))
@Test fun emptyBody_givesEmptyMerchant() = assertEquals(ParsedNotification(3000, OUT, ""), p("출금 3,000원", ""))
@Test fun kbankDeposit_oneWon() =
    assertEquals(ParsedNotification(1, IN, "김가짜"), p("입금 1원", "김가짜 | 가짜통장(1234)", NotificationParsers.KBANK_PACKAGE))
@Test fun kbankDeposit_withoutPipe_fallsBackToGeneric() =
    assertEquals(ParsedNotification(2000, IN, "김가짜"), p("입금 2,000원", "김가짜", NotificationParsers.KBANK_PACKAGE))
// MerchantNormalizerTest
@Test fun normalize() {
    assertEquals("가짜커피코리아", MerchantNormalizer.normalize("(주) 가짜커피 코리아"))
    assertEquals("fakecafe강남점", MerchantNormalizer.normalize("Fake Cafe (강남점)"))
    assertEquals("가짜상사", MerchantNormalizer.normalize("주식회사 가짜상사"))
    assertEquals("", MerchantNormalizer.normalize("  "))
}
```

- [ ] **Step 2: 실패 확인** — Run: `GRADLE :core:test --tests "*autoentry*"` → Expected: 컴파일 실패.
- [ ] **Step 3: 구현** — 위 규칙대로. 정규식은 `private val`로 한 번만 컴파일한다.
- [ ] **Step 4: 통과 확인** — Run: `GRADLE :core:test` → Expected: 전부 통과.
- [ ] **Step 5: 커밋** — `git add core/src/main/kotlin/com/householdbudget/app/domain/autoentry core/src/test/kotlin/com/householdbudget/app/domain/autoentry && git commit -m "feat: 알림 공통 파서와 케이뱅크 입금 파서, 이름 정규화 추가"`

---

### Task 5: 자동 분류기와 금융 앱 표 (core) `[Sonnet·기계적]`

**Files:**
- Create: `core/.../domain/autoentry/KnownApps.kt`, `core/.../domain/autoentry/AutoClassifier.kt`
- Test: `core/src/test/kotlin/com/householdbudget/app/domain/autoentry/AutoClassifierTest.kt`

**Interfaces:**
- Consumes: Task 4의 `ParsedNotification`, `Direction`, `MerchantNormalizer.normalize`; Task 1의 `CategoryPath`.
- Produces:
  ```kotlin
  package com.householdbudget.app.domain.autoentry
  data class ChargeApp(val keyword: String, val packages: Set<String>)
  object KnownApps {
      val FINANCE_PACKAGES: List<String>   // 대상 앱 기본 체크 후보
      val CHARGE_APPS: List<ChargeApp>
  }
  enum class ReviewReason { UNKNOWN_DIRECTION, UNCLASSIFIED, DUP_CROSS_APP, DUP_MANUAL }
  /** categoryId == null 이면 "기록 안 함". categoryKind는 categoryId가 있을 때만 non-null. */
  data class LearnedRule(val categoryId: Long?, val categoryKind: CategoryKind?)
  sealed interface Decision {
      data class RegisterById(val categoryId: Long) : Decision
      data class RegisterByPath(val path: CategoryPath) : Decision
      data object Ignore : Decision
      data class NeedsReview(val reason: ReviewReason) : Decision
  }
  object AutoClassifier {
      fun isCompatible(direction: Direction, kind: CategoryKind): Boolean
      fun classify(parsed: ParsedNotification, fullText: String, sourcePackage: String,
                   watchedPackages: Set<String>, learned: LearnedRule?): Decision
  }
  ```

값:
- `FINANCE_PACKAGES`: `com.kbankwith.smartbank`, `viva.republica.toss`, `com.kakaobank.channel`, `com.kakaopay.app`, `com.naverfin.payapp`, `kvp.jjy.MispAndroid320`, `com.nhnent.payapp`, `com.kbstar.kbbank`, `com.shinhan.sbanking`, `com.shcard.smartpay`, `com.hyundaicard.appcard`, `com.kbcard.cxh.appcard`, `com.lcacApp`, `com.wooribank.smart.npib`, `com.wooricard.smartapp`, `com.kebhana.hanapush`, `com.hanaskcard.paycla`, `nh.smart.banking`. (계획 작성 시 Play 스토어에 존재하는 것만 확인했다. 앱 이름이 맞는지는 Task 9에서 라벨로 보인다.)
- `CHARGE_APPS`: 네이버페이 → {`com.naverfin.payapp`, `com.nhn.android.search`} · 페이북 → {`kvp.jjy.MispAndroid320`} · 카카오페이 → {`com.kakaopay.app`, `com.kakao.talk`} · 토스페이 → {`viva.republica.toss`} · 페이코 → {`com.nhnent.payapp`}
- 기본 가맹점 규칙(정규화한 이름에 키워드가 들어 있으면 일치, 위에서부터 첫 일치):

  | 키워드 | 경로(EXPENSE) |
  |---|---|
  | 배달의민족, 배민, 요기요, 쿠팡이츠 | 식비>배달 |
  | 택시, 카카오t | 교통>택시 |
  | 버스, 지하철, 티머니 | 교통>대중교통 |
  | 주유, 주차 | 교통>주유/주차 |
  | 카페, 커피, 제과, 베이커리, 스타벅스 | 식비>카페 |
  | 마트, 편의점, gs25, 세븐일레븐, 씨유 | 식비>장보기 |
  | 텔레콤, 유플러스, 케이티, 알뜰폰 | 생활>통신 |
  | 넷플릭스, netflix, 유튜브, youtube | 생활>구독 |

- `isCompatible`: `UNKNOWN` → 항상 true · `OUT` → EXPENSE 또는 SAVINGS · `IN`, `REFUND` → INCOME.
- `classify` 순서(`key = normalize(parsed.merchant)`, `text = normalize(fullText)`):
  1. `learned != null`: `categoryId == null` → `Ignore`. 아니면 `isCompatible(direction, categoryKind)`이면 `RegisterById`, 아니면 `NeedsReview(UNCLASSIFIED)`.
  2. `direction == UNKNOWN` → `NeedsReview(UNKNOWN_DIRECTION)`.
  3. `OUT`이고 `text`에 `CHARGE_APPS` 키워드가 있는 첫 항목 `app`: `sourcePackage in app.packages`이면 → `text`에 `충전`이 있으면 `Ignore`(결제 앱 자신의 충전 알림), 없으면 4번으로. `sourcePackage`가 그 앱이 아니면 → `watchedPackages`와 `app.packages`가 겹치면 `Ignore`, 안 겹치면 `RegisterByPath(EXPENSE 쇼핑>간편결제 충전)`.
  4. `OUT`이고 `key`가 기본 가맹점 규칙에 맞으면 `RegisterByPath`.
  5. `IN`이고 `text`에 `캐시백` 또는 `이자`가 있으면 `RegisterByPath(INCOME 수입>캐시백/이자)`.
  6. `REFUND` → `RegisterByPath(INCOME 수입>기타)`.
  7. 그 외 → `NeedsReview(UNCLASSIFIED)`. 이 분류기는 `유동 지출`을 고르지 않는다.

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
private val KBANK = "com.kbankwith.smartbank"; private val NPAY = "com.naverfin.payapp"
private fun c(amount: Long, dir: Direction, merchant: String, text: String = merchant, pkg: String = KBANK,
              watched: Set<String> = setOf(KBANK), learned: LearnedRule? = null) =
    AutoClassifier.classify(ParsedNotification(amount, dir, merchant), text, pkg, watched, learned)
private fun path(k: CategoryKind, p: String, ch: String) = Decision.RegisterByPath(CategoryPath(k, p, ch))
@Test fun builtinRules() {
    assertEquals(path(EXPENSE, "식비", "카페"), c(4500, OUT, "가짜커피 강남점"))
    assertEquals(path(EXPENSE, "식비", "장보기"), c(4800, OUT, "가짜마트"))
    assertEquals(path(EXPENSE, "교통", "택시"), c(9800, OUT, "카카오T 택시"))
    assertEquals(path(EXPENSE, "교통", "대중교통"), c(1500, OUT, "가짜버스운송"))
    assertEquals(path(EXPENSE, "식비", "배달"), c(23000, OUT, "배달의민족"))
    assertEquals(path(EXPENSE, "생활", "구독"), c(17000, OUT, "NETFLIX"))
}
@Test fun unknownMerchant_needsReview() = assertEquals(Decision.NeedsReview(UNCLASSIFIED), c(30000, OUT, "김가짜"))
@Test fun unknownDirection_needsReview() = assertEquals(Decision.NeedsReview(UNKNOWN_DIRECTION), c(7000, UNKNOWN, "가짜커피"))
@Test fun learnedRule_beatsBuiltin() =
    assertEquals(Decision.RegisterById(77), c(4500, OUT, "가짜커피", learned = LearnedRule(77, EXPENSE)))
@Test fun learnedIgnore() = assertEquals(Decision.Ignore, c(4500, OUT, "가짜커피", learned = LearnedRule(null, null)))
@Test fun learnedExpenseRule_onIncoming_needsReview() =
    assertEquals(Decision.NeedsReview(UNCLASSIFIED), c(30000, IN, "김가짜", learned = LearnedRule(77, EXPENSE)))
@Test fun learnedRule_appliesToUnknownDirection() =
    assertEquals(Decision.RegisterById(77), c(7000, UNKNOWN, "김가짜", learned = LearnedRule(77, EXPENSE)))
@Test fun charge_whenPayAppWatched_isIgnored() =
    assertEquals(Decision.Ignore, c(10000, OUT, "네이버페이", "출금 10,000원 네이버페이 충전", watched = setOf(KBANK, NPAY)))
@Test fun charge_whenPayAppNotWatched_isRecordedAsCharge() =
    assertEquals(path(EXPENSE, "쇼핑", "간편결제 충전"), c(10000, OUT, "네이버페이", "출금 10,000원 네이버페이 충전"))
@Test fun payAppOwnPurchase_usesBuiltinRule() =
    assertEquals(path(EXPENSE, "식비", "카페"), c(4500, OUT, "가짜커피", "네이버페이 결제 4,500원 가짜커피", pkg = NPAY, watched = setOf(KBANK, NPAY)))
@Test fun payAppOwnTopUp_isIgnored() =
    assertEquals(Decision.Ignore, c(10000, OUT, "", "네이버페이 충전 10,000원 완료", pkg = NPAY, watched = setOf(KBANK, NPAY)))
@Test fun cashbackAndInterest_goToIncome() {
    assertEquals(path(INCOME, "수입", "캐시백/이자"), c(120, IN, "캐시백", "입금 120원 캐시백"))
    assertEquals(path(INCOME, "수입", "캐시백/이자"), c(35, IN, "가짜은행", "입금 35원 예금이자"))
}
@Test fun plainDeposit_needsReview() = assertEquals(Decision.NeedsReview(UNCLASSIFIED), c(50000, IN, "김가짜", "입금 50,000원 김가짜"))
@Test fun refund_goesToIncomeOther() = assertEquals(path(INCOME, "수입", "기타"), c(8900, REFUND, "테스트상점"))
@Test fun everyBuiltinPath_existsInFixedTree() { /* 규칙표·충전·캐시백·환불 경로가 FixedCategories.TREE에 있는지 */ }
```

- [ ] **Step 2: 실패 확인** — Run: `GRADLE :core:test --tests "*AutoClassifierTest"` → Expected: 컴파일 실패.
- [ ] **Step 3: 구현** — 위 표와 순서 그대로.
- [ ] **Step 4: 통과 확인** — Run: `GRADLE :core:test` → Expected: 전부 통과.
- [ ] **Step 5: 커밋** — `git add core/src/main/kotlin/com/householdbudget/app/domain/autoentry core/src/test/kotlin/com/householdbudget/app/domain/autoentry && git commit -m "feat: 자동 분류 규칙과 금융 앱 표 추가"`

---

### Task 6: Room v8 — 자동 기록 테이블, 거래 출처, 대상 앱 설정 `[Sonnet·기계적]`

**Files:**
- Create: `app/.../data/local/entity/NotificationLogEntity.kt`, `AutoEntryEntity.kt`, `MerchantRuleEntity.kt`; `app/.../data/local/dao/NotificationLogDao.kt`, `AutoEntryDao.kt`, `MerchantRuleDao.kt`; `app/.../data/local/model/MerchantRuleRow.kt`
- Modify: `app/.../data/local/AppDatabase.kt`, `entity/TransactionEntity.kt`, `dao/TransactionDao.kt`, `model/TransactionWithCategoryRow.kt`, `data/preferences/UserPreferencesRepository.kt`
- Test: `app/src/test/java/com/householdbudget/app/data/local/Migration7To8Test.kt`

**Interfaces:**
- Consumes: Task 3의 `V6Schema.create`, `AppDatabase.ALL_MIGRATIONS`.
- Produces (컬럼은 모두 `NOT NULL`, `?` 표시만 nullable):
  - `transactions.source TEXT NOT NULL DEFAULT 'MANUAL'` — `TransactionEntity`에 `@ColumnInfo(name = "source", defaultValue = "MANUAL") val source: String = TransactionSource.MANUAL`; `object TransactionSource { const val MANUAL = "MANUAL"; const val AUTO = "AUTO" }`. `TransactionWithCategoryRow`에 `val source: String` 추가, `TransactionDao`의 두 조회 쿼리에 `t.source AS source` 추가.
  - `notification_log`: `id`(PK auto), `package_name`, `notification_key`, `post_time_ms`, `title`, `body`, `content_hash`. 유니크 인덱스 `(package_name, notification_key, post_time_ms, content_hash)`, 인덱스 `(post_time_ms)`.
  - `auto_entries`: `id`(PK auto), `log_id`, `package_name`, `occurred_epoch_day`, `posted_at_ms`, `amount_minor`, `direction`(`Direction.name`), `merchant`, `merchant_key`, `status`(`"REGISTERED" | "PENDING" | "IGNORED"`), `reason?`(`ReviewReason.name`), `transaction_id?`. 인덱스 `(status)`, `(occurred_epoch_day, amount_minor)`. 외래 키 없음(로그는 90일 뒤 지워지고 거래는 사용자가 지울 수 있다). `object AutoEntryStatus { const val REGISTERED; const val PENDING; const val IGNORED }`.
  - `merchant_rules`: `merchant_key`(PK), `display_name`, `category_id?`(null = 기록 안 함), `updated_at_ms`.
  - DAO:
    ```kotlin
    interface NotificationLogDao {
        @Insert(onConflict = IGNORE) suspend fun insert(e: NotificationLogEntity): Long   // 충돌 시 -1
        suspend fun countSimilar(pkg: String, hash: String, fromMs: Long, toMs: Long): Int // package_name, content_hash 일치 AND post_time_ms BETWEEN
        suspend fun deleteOlderThan(cutoffMs: Long): Int                                   // post_time_ms < cutoffMs
        suspend fun count(): Int
    }
    interface AutoEntryDao {
        suspend fun insert(e: AutoEntryEntity): Long
        suspend fun update(e: AutoEntryEntity)
        suspend fun getById(id: Long): AutoEntryEntity?
        suspend fun findByTransactionId(transactionId: Long): AutoEntryEntity?
        suspend fun listPendingByMerchantKey(merchantKey: String): List<AutoEntryEntity>
        fun observeByStatus(status: String): Flow<List<AutoEntryEntity>>                   // posted_at_ms DESC
        fun observeCountByStatus(status: String): Flow<Int>
        /** direction='OUT', 금액·날짜 같고 package_name != :excludePackage, status IN ('REGISTERED','PENDING'), posted_at_ms BETWEEN */
        suspend fun countCrossAppOut(amountMinor: Long, epochDay: Long, excludePackage: String, fromMs: Long, toMs: Long): Int
    }
    interface MerchantRuleDao {
        @Insert(onConflict = REPLACE) suspend fun upsert(e: MerchantRuleEntity)
        suspend fun getByKey(merchantKey: String): MerchantRuleEntity?
        suspend fun deleteByKey(merchantKey: String)
        suspend fun count(): Int
        fun observeAll(): Flow<List<MerchantRuleRow>>   // categories 두 번 LEFT JOIN, display_name ASC
    }
    data class MerchantRuleRow(val merchantKey: String, val displayName: String, val categoryId: Long?,
                               val categoryName: String?, val parentCategoryName: String?)
    // TransactionDao 추가
    suspend fun countManualMatch(epochDay: Long, amountMinor: Long, kind: String): Int   // source = 'MANUAL'
    ```
  - `AppDatabase`: `version = 8`, 엔티티 3개와 `notificationLogDao()`, `autoEntryDao()`, `merchantRuleDao()` 추가, `MIGRATION_7_8`(ALTER 1개 + CREATE TABLE 3개 + 인덱스)을 `ALL_MIGRATIONS` 끝에 추가.
  - `UserPreferencesRepository`: `val watchedPackagesOrNull: Flow<Set<String>?>`(한 번도 저장한 적 없으면 null), `suspend fun setWatchedPackages(value: Set<String>)`. 키 `stringSetPreferencesKey("auto_entry_watched_packages")`.

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
@Test fun migratesFromV6ToV8_andKeepsExistingTransactionsAsManual()
// V6Schema로 만든 DB에 식비>식사 거래 1건 → ALL_MIGRATIONS로 열기(스키마 대조 통과) → 그 거래 source == "MANUAL"
@Test fun notificationLog_ignoresExactDuplicate()
// 같은 (package, key, postTime, hash) 두 번 insert → 두 번째 반환 -1, count() == 1
@Test fun countCrossAppOut_excludesSamePackageAndIgnored()
// OUT 12000 REGISTERED(pkg A), IGNORED(pkg C) 넣고 excludePackage="B" → 1, excludePackage="A" → 0
@Test fun countManualMatch_ignoresAutoTransactions()
// 같은 날 4500 EXPENSE: MANUAL 1건 + AUTO 1건 → 1
@Test fun merchantRuleRow_joinsCategoryNames()
// 규칙(category = 식비>카페 id)과 규칙(category null) → ("카페","식비"), (null, null)
```
  (뒤 4개는 `Room.inMemoryDatabaseBuilder` + `AppDatabase.seedCategoriesIfEmpty`.)
- [ ] **Step 2: 실패 확인** — Run: `GRADLE :app:testDebugUnitTest --tests "*Migration7To8Test"` → Expected: 컴파일 실패.
- [ ] **Step 3: 엔티티·DAO·`MIGRATION_7_8`·설정 구현** — `MIGRATION_7_8`의 DDL은 엔티티 선언과 글자 단위로 맞아야 한다(컬럼 순서 무관, 타입·NOT NULL·기본값·인덱스 이름 `index_<table>_<col>_<col>` 일치). 어긋나면 Step 1의 첫 테스트가 Room 스키마 대조 오류로 알려 준다.
- [ ] **Step 4: 통과 확인** — Run: `GRADLE :app:testDebugUnitTest` → Expected: Task 3 테스트 포함 전부 통과. Run: `GRADLE :app:assembleDebug` → `BUILD SUCCESSFUL`.
- [ ] **Step 5: 커밋** — `git add app/src/main/java/com/householdbudget/app/data app/src/test/java/com/householdbudget/app/data/local/Migration7To8Test.kt && git commit -m "feat: 자동 기록용 테이블과 거래 출처 컬럼 추가 (v8)"`

---

### Task 7: AutoEntryRepository — 알림 처리, 중복 방지, 확인 필요, 학습 `[Opus·까다로움]`

**Files:**
- Create: `app/.../data/repository/AutoEntryRepository.kt`
- Modify: `app/.../data/repository/BudgetRepository.kt` (`updateTransaction`, 생성자)
- Test: `app/src/test/java/com/householdbudget/app/data/repository/AutoEntryRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 4 `NotificationParsers.parse`, `MerchantNormalizer.normalize`; Task 5 `AutoClassifier`, `Decision`, `LearnedRule`, `ReviewReason`; Task 6의 DAO·엔티티; Task 3 `CategoryDao.findLeafByPath`.
- Produces:
  ```kotlin
  data class IncomingNotification(val packageName: String, val key: String, val postTimeMs: Long,
                                  val title: String, val text: String, val bigText: String)
  enum class HandleOutcome { NOT_WATCHED, DUPLICATE, LOGGED_ONLY, IGNORED, PENDING, REGISTERED }
  class AutoEntryRepository(
      private val database: AppDatabase,
      private val watchedPackages: suspend () -> Set<String>,
      private val zoneId: ZoneId = ZoneId.of("Asia/Seoul"),
  ) {
      suspend fun handleNotification(n: IncomingNotification): HandleOutcome
      fun observePending(): Flow<List<AutoEntryEntity>>
      fun observePendingCount(): Flow<Int>
      /** categoryId == null 이면 기록 안 함. */
      suspend fun resolvePending(entryId: Long, categoryId: Long?)
      fun observeRules(): Flow<List<MerchantRuleRow>>
      suspend fun deleteRule(merchantKey: String)
      suspend fun learnFromCorrection(transactionId: Long, newCategoryId: Long)
      suspend fun purgeOldLogs(nowMs: Long = System.currentTimeMillis()): Int
      companion object {
          const val ECHO_WINDOW_MS = 10_000L
          const val CROSS_APP_WINDOW_MS = 5 * 60_000L
          const val LOG_RETENTION_DAYS = 90L
      }
  }
  ```
  - `BudgetRepository` 생성자 끝에 `private val onAutoRecategorized: suspend (transactionId: Long, categoryId: Long) -> Unit = { _, _ -> }` 추가. `updateTransaction`에서 기존 거래의 `source == TransactionSource.AUTO`이고 카테고리가 바뀌었으면 저장 뒤 호출한다. `updateTransaction`은 `existing.copy(...)`를 쓰므로 `source`는 그대로 보존된다.

`handleNotification` 처리 순서 — 전체를 클래스의 `Mutex` 하나로 감싸고(`withLock`), 2~7번은 `database.withTransaction` 안에서 한다. 알림 콜백이 겹쳐 와도 "있는지 확인 → 넣기"가 끼어들지 않게 하기 위함이다.
1. `n.packageName !in watchedPackages()` → `NOT_WATCHED`(아무것도 저장하지 않음).
2. `body = bigText가 비어 있지 않으면 bigText, 아니면 text`. `hash = SHA-256("$packageName\n$title\n$body")`의 hex. `countSimilar(pkg, hash, postTimeMs - ECHO_WINDOW_MS, postTimeMs + ECHO_WINDOW_MS) > 0`이면 → `DUPLICATE`. 아니면 로그를 insert하고, 반환이 -1이면 → `DUPLICATE`.
3. `NotificationParsers.parse(pkg, title, body)`가 null → `LOGGED_ONLY`.
4. `key = normalize(merchant)`. `key`가 비어 있지 않으면 `merchantRuleDao.getByKey(key)`로 `LearnedRule`을 만든다(규칙의 카테고리가 DB에 없으면 규칙이 없는 것으로 본다). `AutoClassifier.classify(parsed, "$title\n$body", pkg, watched, learned)`.
5. `Ignore` → `auto_entries`에 `IGNORED`로 저장 → `IGNORED`. `NeedsReview(r)` → `PENDING`, `reason = r` → `PENDING`.
6. `RegisterById/ByPath` → 카테고리 잎을 구한다(`ByPath`는 `findLeafByPath`; 못 찾으면 `PENDING/UNCLASSIFIED`). `occurredEpochDay = Instant.ofEpochMilli(postTimeMs).atZone(zoneId).toLocalDate().toEpochDay()`. 중복 검사:
   - `direction == OUT`이고 `countCrossAppOut(amount, day, pkg, postTimeMs - CROSS_APP_WINDOW_MS, postTimeMs + CROSS_APP_WINDOW_MS) > 0` → `PENDING/DUP_CROSS_APP`.
   - 아니면 `transactionDao.countManualMatch(day, amount, leaf.kind) > 0` → `PENDING/DUP_MANUAL`.
7. 통과하면 거래 insert(`kind = leaf.kind`, `memo = merchant`, `source = AUTO`) + `auto_entries`에 `REGISTERED`, `transaction_id` → `REGISTERED`.

`resolvePending(entryId, categoryId)` — 한 트랜잭션:
- 대상이 `PENDING`이 아니면 아무것도 하지 않는다.
- `categoryId == null` → `IGNORED`. 아니면 거래 insert(`occurred_epoch_day`·금액은 항목 값, `memo = merchant`, `source = AUTO`) 후 `REGISTERED`.
- **규칙 학습**: `merchant_key`가 비어 있지 않고 `reason`이 `UNCLASSIFIED` 또는 `UNKNOWN_DIRECTION`일 때만 `merchant_rules`에 upsert(`display_name = merchant`). `DUP_*`는 학습하지 않는다.
- 규칙을 학습했으면 같은 `merchant_key`의 다른 `PENDING` 항목(`reason`이 `UNCLASSIFIED`/`UNKNOWN_DIRECTION`) 중 `categoryId == null`이거나 `AutoClassifier.isCompatible(direction, kind)`인 것에 같은 결과를 바로 적용한다(같은 가게가 여러 건 쌓였을 때 한 번만 고르게 하려는 것).

`learnFromCorrection`: `findByTransactionId`로 항목을 찾아 `merchant_key`가 비어 있지 않으면 규칙 upsert. 항목이 없으면 아무것도 하지 않는다. `purgeOldLogs`: `deleteOlderThan(nowMs - 90일)`. `deleteRule`: `deleteByKey`.

- [ ] **Step 1: 실패하는 테스트 작성** — 준비: `Room.inMemoryDatabaseBuilder(...).allowMainThreadQueries()` + `seedCategoriesIfEmpty`, `var watched = setOf(KBANK, CARD)` (`KBANK = "com.kbankwith.smartbank"`, `CARD = "com.fake.card"`, `NPAY = "com.naverfin.payapp"`), `repo = AutoEntryRepository(db, { watched })`, `T0 = 1_800_000_000_000L`, 도우미 `n(pkg, title, body, key = "k1", at = T0) = IncomingNotification(pkg, key, at, title, body, "")`, `leafId(kind, parent, child)`.

```kotlin
@Test fun notWatched_storesNothing()
// handle(n("com.other.app", "가짜", "승인 4,500원\n가짜커피")) == NOT_WATCHED; 로그 0, 항목 0, 거래 0
@Test fun noAmount_isLoggedOnly()
// handle(n(KBANK, "로그인 알림", "새 기기에서 로그인했어요")) == LOGGED_ONLY; 로그 1, 항목 0
@Test fun builtinMerchant_isRegisteredAsAutoTransaction()
// handle(n(CARD, "가짜카드", "승인 4,500원\n가짜커피 강남점")) == REGISTERED
// 거래 1건: amount 4500, kind "EXPENSE", category == leafId(식비>카페), memo "가짜커피 강남점", source "AUTO",
// occurred_epoch_day == Instant.ofEpochMilli(T0).atZone(Asia/Seoul).toLocalDate().toEpochDay()
@Test fun exactRedelivery_isDuplicate()
// 같은 알림 두 번 → REGISTERED, DUPLICATE; 거래 1, 로그 1
@Test fun sameContentWithin10s_isDuplicate()
// 같은 내용, key "k2", at = T0 + 9_000 → DUPLICATE; 거래 1
@Test fun sameContentAfter10s_isNewPayment()
// 같은 내용, key "k2", at = T0 + 11_000 → REGISTERED; 거래 2
@Test fun unknownMerchant_goesPending_thenLearnsRule()
// handle(n(KBANK, "출금 30,000원", "김가짜 | 가짜통장(1234)")) == PENDING (reason UNCLASSIFIED), 거래 0
// resolvePending(id, leafId(유동 지출>기본)) → 거래 1(memo "김가짜", source AUTO), 규칙 1
// 다음 날(at = T0 + 86_400_000, key "k2") 같은 이름 20,000원 → REGISTERED, category == 유동 지출>기본
@Test fun resolveAsIgnore_learnsIgnoreRule()
// PENDING → resolvePending(id, null) → 거래 0, 항목 IGNORED; 다음 같은 이름 → IGNORED, 거래 0
@Test fun learnedExpenseRule_incomingFromSameName_goesPending()
// 위처럼 김가짜 → 유동 지출 학습 후 n(KBANK, "입금 50,000원", "김가짜 | 가짜통장(1234)", key "k3", at = T0 + 3_600_000) → PENDING, 거래 수 불변
@Test fun emptyMerchant_resolves_withoutRule()
// n(KBANK, "출금 3,000원", "") → PENDING; resolvePending(id, 유동 지출>기본) → 거래 1, 규칙 0
@Test fun resolvingOne_appliesToOtherPendingOfSameMerchant()
// 김가짜 출금 2건(다른 날) PENDING → 하나 resolve → 둘 다 REGISTERED, 거래 2, 남은 PENDING 0
@Test fun crossApp_sameAmountWithin5Min_secondGoesPending()
// CARD "승인 12,000원\n가짜마트" at T0 → REGISTERED; KBANK "출금 12,000원" / "가짜마트 | 가짜통장(1234)" at T0 + 120_000 → PENDING(DUP_CROSS_APP); 거래 1
@Test fun crossApp_after5Min_isRegistered()
// 두 번째가 T0 + 360_000 → REGISTERED; 거래 2
@Test fun sameApp_sameAmountTwice_bothRegistered()
// CARD에서 내용이 다른(가맹점 다른) 4,500원 두 건, 1분 간격 → 거래 2
@Test fun manualTransactionSameDayAmountKind_goesPending()
// 수동 거래(같은 날, 4500, EXPENSE, source MANUAL) 넣은 뒤 CARD "승인 4,500원\n가짜커피" → PENDING(DUP_MANUAL); 거래 1
@Test fun resolvingDuplicate_doesNotLearnRule()
// 위 PENDING을 resolvePending(id, null) → 규칙 0; 다음 날 가짜커피 4,500원 → REGISTERED
@Test fun charge_ignored_andPayAppPaymentStillRegistered()
// watched = {KBANK, NPAY}; KBANK "출금 10,000원" / "네이버페이 충전 | 가짜통장(1234)" → IGNORED;
// NPAY "가짜페이" / "결제 10,000원\n가짜커피" at T0 + 60_000 → REGISTERED (DUP_CROSS_APP 아님); 거래 1
@Test fun charge_recordedWhenPayAppNotWatched()
// watched = {KBANK}; 같은 충전 알림 → REGISTERED, category == 쇼핑>간편결제 충전
@Test fun concurrentIdenticalNotifications_createOneTransaction()
// runBlocking { List(20) { async(Dispatchers.Default) { repo.handleNotification(같은 알림) } }.awaitAll() }
// → REGISTERED 정확히 1개, 나머지 DUPLICATE; 거래 1
@Test fun purgeOldLogs_keeps90Days()
// 로그 2건(postTime = now - 91일, now - 89일) → purgeOldLogs(now) == 1, 남은 로그 1
@Test fun learnFromCorrection_upsertsRuleForAutoTransaction()
// REGISTERED된 가짜커피 거래 id로 learnFromCorrection(txId, leafId(식비>간식)) → 규칙의 category == 식비>간식; 다음 가짜커피 → 식비>간식
@Test fun deleteRule_returnsMerchantToBuiltinOrPending()
// 김가짜 규칙 삭제 후 같은 이름 → PENDING
```

- [ ] **Step 2: 실패 확인** — Run: `GRADLE :app:testDebugUnitTest --tests "*AutoEntryRepositoryTest"` → Expected: 컴파일 실패.
- [ ] **Step 3: `AutoEntryRepository` 구현 + `BudgetRepository.updateTransaction` 연결** — 위 순서 그대로. 원문·이름·금액을 로그로 찍지 않는다.
- [ ] **Step 4: 통과 확인** — Run: `GRADLE :app:testDebugUnitTest` → Expected: 전부 통과. Run: `GRADLE :app:assembleDebug` → `BUILD SUCCESSFUL`.
- [ ] **Step 5: 커밋** — `git add app/src/main/java/com/householdbudget/app/data/repository app/src/test/java/com/householdbudget/app/data/repository && git commit -m "feat: 알림 자동 등록·중복 방지·확인 필요 처리 저장소 추가"`

---

### Task 8: 알림 수신 서비스, 대상 앱 기본값, 연결 `[Sonnet·기계적]`

**Files:**
- Create: `app/.../autoentry/NotificationCaptureService.kt`, `app/.../autoentry/WatchedApps.kt`
- Modify: `app/src/main/AndroidManifest.xml`, `app/.../AppContainer.kt`, `app/.../BudgetApplication.kt`, `app/.../data/work/DailyMaintenanceWorker.kt`
- Test: `app/src/test/java/com/householdbudget/app/autoentry/WatchedAppsTest.kt`

**Interfaces:**
- Consumes: Task 7 `AutoEntryRepository`, `IncomingNotification`; Task 6 `watchedPackagesOrNull`; Task 5 `KnownApps.FINANCE_PACKAGES`.
- Produces:
  - `object WatchedApps { const val SHELL_PACKAGE = "com.android.shell"; fun defaults(context: Context): Set<String>; fun effective(stored: Set<String>?, context: Context): Set<String> }` — `defaults` = `FINANCE_PACKAGES` 중 기기에 설치된 것(`packageManager.getPackageInfo` 성공) + 디버그 빌드(`applicationInfo.flags and FLAG_DEBUGGABLE != 0`)면 `SHELL_PACKAGE`. `effective` = `stored ?: defaults(context)`. 설치되지 않은 결제 앱을 "대상"으로 세면 충전이 "기록 안 함"이 되어 지출이 사라지므로 설치 여부를 본다.
  - `AppContainer.autoEntryRepository: AutoEntryRepository` — `watchedPackages = { WatchedApps.effective(userPreferencesRepository.watchedPackagesOrNull.first(), appContext) }`. `budgetRepository`는 `onAutoRecategorized = autoEntryRepository::learnFromCorrection`으로 만든다(선언 순서: `autoEntryRepository` 먼저).
  - `class NotificationCaptureService : NotificationListenerService()`

- [ ] **Step 1: 실패하는 테스트 작성**
  ```kotlin
  @Test fun effective_prefersStoredSet_evenWhenEmpty() = assertEquals(emptySet<String>(), WatchedApps.effective(emptySet(), ctx))
  @Test fun defaults_onlyIncludeInstalledKnownApps()
  // shadowOf(ctx.packageManager).installPackage(PackageInfo().apply { packageName = "com.kbankwith.smartbank" })
  // → WatchedApps.defaults(ctx)에 케이뱅크는 있고 "viva.republica.toss"는 없다
  ```
- [ ] **Step 2: 실패 확인** — Run: `GRADLE :app:testDebugUnitTest --tests "*WatchedAppsTest"` → Expected: 컴파일 실패.
- [ ] **Step 3: `WatchedApps`, 서비스, 매니페스트, 연결 구현**
  - 서비스: `SupervisorJob() + Dispatchers.IO` 스코프, `onDestroy`에서 취소. `onNotificationPosted(sbn)`: `sbn.packageName == packageName`이면 무시, `sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0`이면 무시(요약 알림은 같은 결제의 반복), 그 외에는 `extras`의 `EXTRA_TITLE`/`EXTRA_TEXT`/`EXTRA_BIG_TEXT`를 `CharSequence?.toString().orEmpty()`로 꺼내 `scope.launch { runCatching { container.autoEntryRepository.handleNotification(IncomingNotification(sbn.packageName, sbn.key, sbn.postTime, title, text, bigText)) } }`. 실패는 삼키고 내용은 로그에 남기지 않는다.
  - 매니페스트: `<service android:name=".autoentry.NotificationCaptureService" android:exported="true" android:label="@string/app_name" android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">` + `<intent-filter><action android:name="android.service.notification.NotificationListenerService" /></intent-filter>`. `<manifest>` 바로 아래에 `<queries><intent><action android:name="android.intent.action.MAIN" /><category android:name="android.intent.category.LAUNCHER" /></intent></queries>`(Android 11+에서 설치된 앱 목록·설치 여부 확인용. `QUERY_ALL_PACKAGES`는 쓰지 않는다). `allowBackup`은 바꾸지 않는다(스펙 5.7).
  - `DailyMaintenanceWorker.doWork`와 `BudgetApplication.onCreate`의 시작 블록에 `autoEntryRepository.purgeOldLogs()` 호출 추가.
- [ ] **Step 4: 통과 확인** — Run: `GRADLE :app:testDebugUnitTest` → 전부 통과. Run: `GRADLE :app:assembleDebug` → `BUILD SUCCESSFUL`.
- [ ] **Step 5: 커밋** — `git add app/src/main/AndroidManifest.xml app/src/main/java/com/householdbudget/app/autoentry app/src/main/java/com/householdbudget/app/AppContainer.kt app/src/main/java/com/householdbudget/app/BudgetApplication.kt app/src/main/java/com/householdbudget/app/data/work/DailyMaintenanceWorker.kt app/src/test/java/com/householdbudget/app/autoentry && git commit -m "feat: 알림 수신 서비스와 대상 앱 기본값 연결"`

---

### Task 9: 설정 — "자동 기록" 화면 (권한, 대상 앱, 학습 규칙) `[Sonnet·기계적]`

**Files:**
- Create: `app/.../ui/autoentry/AutoEntrySettingsScreen.kt`, `app/.../ui/autoentry/InstalledApps.kt`, `app/src/main/res/values/strings_auto.xml`
- Modify: `app/.../ui/settings/SettingsScreen.kt`, `app/.../ui/MainTabScaffold.kt`
- Test: `app/src/test/java/com/householdbudget/app/ui/autoentry/InstalledAppsTest.kt`

**Interfaces:**
- Consumes: Task 8 `WatchedApps`, `AppContainer.autoEntryRepository`; Task 6 `watchedPackagesOrNull`, `setWatchedPackages`; Task 7 `observeRules`, `deleteRule`, `observePendingCount`; Task 5 `KnownApps.FINANCE_PACKAGES`.
- Produces:
  - `data class AppInfo(val packageName: String, val label: String)`
  - `fun sortSelectableApps(apps: List<AppInfo>, known: List<String>): List<AppInfo>` — `known`에 있는 앱을 `known` 순서대로 먼저, 나머지는 라벨 가나다순. 패키지 중복 제거.
  - `fun loadSelectableApps(context: Context): List<AppInfo>` — `queryIntentActivities(MAIN/LAUNCHER)` 결과(자기 앱 제외)에 디버그 빌드면 `AppInfo(WatchedApps.SHELL_PACKAGE, "Shell (테스트)")`를 더해 `sortSelectableApps`.
  - `@Composable fun AutoEntrySettingsScreen(onBack: () -> Unit, onOpenPending: () -> Unit, modifier: Modifier = Modifier)` — 저장소·설정은 `SettingsScreen`처럼 `LocalContext`의 `BudgetApplication.container`에서 얻는다.
  - `SettingsScreen`에 `onOpenAutoEntry: () -> Unit` 파라미터 추가(`budgetViewModel` 다음). `SettingsPane.AutoEntry` 추가.

화면 구성(위에서 아래로, `LazyColumn`):
1. 머리말 `자동 기록` + 뒤로(`action_back`).
2. **알림 접근**: 상태 문구(`허용됨` / `꺼져 있어요`)와 버튼 `알림 접근 설정 열기` → `Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)`. 상태 = `NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)`, `LifecycleResumeEffect`로 화면에 돌아올 때마다 다시 읽는다.
3. **확인 필요** 행: `확인이 필요한 거래 %d건` → `onOpenPending`(0건이어도 보인다).
4. **알림을 읽을 앱**: `loadSelectableApps` 목록, 행마다 라벨 + 패키지명(작게) + `Switch`. 체크 상태 = `WatchedApps.effective(stored, context)`에 포함 여부. 토글하면 현재 유효 집합에서 더하거나 뺀 전체 집합을 `setWatchedPackages`로 저장한다.
5. **학습한 규칙**: `observeRules()` 행마다 `displayName` + (`대분류 · 소분류` 또는 `기록 안 함`) + 삭제 아이콘 버튼(`contentDescription = 규칙 삭제`) → `deleteRule`. 비어 있으면 `아직 학습한 규칙이 없어요`.

`SettingsScreen`의 "자동화 · 알림" 그룹 첫 행으로 `자동 기록` 행을 넣는다(부제: 확인 필요가 있으면 `확인 필요 %d건`, 없으면 `은행·결제 알림을 읽어 자동으로 적어요`). `MainTabScaffold`는 `SettingsPane.AutoEntry`일 때 `AutoEntrySettingsScreen`을 보이고 뒤로 가면 `Main`으로 간다(`onOpenPending`은 Task 10에서 연결 — 이 태스크에서는 `{}`).

- [ ] **Step 1: 실패하는 테스트 작성**
  ```kotlin
  @Test fun knownAppsComeFirst_thenLabelOrder() = assertEquals(
      listOf("com.kbankwith.smartbank", "viva.republica.toss", "com.fake.a", "com.fake.b"),
      sortSelectableApps(
          listOf(AppInfo("com.fake.b", "나"), AppInfo("viva.republica.toss", "토스"), AppInfo("com.fake.a", "가"),
                 AppInfo("com.kbankwith.smartbank", "케이뱅크"), AppInfo("com.fake.a", "가")),
          KnownApps.FINANCE_PACKAGES).map { it.packageName })
  ```
- [ ] **Step 2: 실패 확인** — Run: `GRADLE :app:testDebugUnitTest --tests "*InstalledAppsTest"` → Expected: 컴파일 실패.
- [ ] **Step 3: 구현** — 위 구성대로. 문자열은 `strings_auto.xml`에 `auto_` 접두어로 둔다.
- [ ] **Step 4: 통과 확인** — Run: `GRADLE :app:testDebugUnitTest` → 전부 통과. Run: `GRADLE :app:assembleDebug` → `BUILD SUCCESSFUL`.
- [ ] **Step 5: 커밋** — `git add app/src/main/java/com/householdbudget/app/ui/autoentry app/src/main/java/com/householdbudget/app/ui/settings/SettingsScreen.kt app/src/main/java/com/householdbudget/app/ui/MainTabScaffold.kt app/src/main/res/values/strings_auto.xml app/src/test/java/com/householdbudget/app/ui/autoentry && git commit -m "feat: 자동 기록 설정 화면 추가"`

---

### Task 10: "확인 필요" 화면, 홈 배너, 자동 기록 표시 `[Sonnet·기계적]`

**Files:**
- Create: `app/.../ui/autoentry/PendingReviewScreen.kt`
- Modify: `app/.../ui/MainTabScaffold.kt`, `app/.../ui/feed/FeedScreen.kt`, `app/.../ui/components/TransactionRow.kt`, `app/.../ui/calendar/CalendarScreen.kt`, `app/.../ui/archive/ArchiveDetailScreen.kt`, `app/.../ui/stats/StatsScreen.kt`, `app/src/main/res/values/strings_auto.xml`
- Test: `app/src/test/java/com/householdbudget/app/ui/autoentry/PendingLabelsTest.kt`

**Interfaces:**
- Consumes: Task 7 `observePending`, `observePendingCount`, `resolvePending`; Task 6 `AutoEntryEntity`, `TransactionWithCategoryRow.source`, `TransactionSource.AUTO`; `BudgetViewModel.parentsByKind`, `childrenByParent`(기존).
- Produces:
  - `@Composable fun PendingReviewScreen(budgetViewModel: BudgetViewModel, onBack: () -> Unit, modifier: Modifier = Modifier)`
  - `fun defaultKindFor(direction: String): CategoryKind` — `"IN"`, `"REFUND"` → `INCOME`, 그 외 → `EXPENSE`.
  - `@StringRes fun reasonLabelRes(reason: String?): Int` — `DUP_CROSS_APP`, `DUP_MANUAL` → `R.string.auto_reason_duplicate`("중복 같아요"), `UNKNOWN_DIRECTION` → `R.string.auto_reason_direction`("입금인지 출금인지 몰라요"), 그 외 → `R.string.auto_reason_unclassified`("어디에 쓴 돈인지 몰라요").
  - `TransactionRow(…, isAuto: Boolean = false, …)` — true면 보조줄 맨 앞에 `자동`(`R.string.auto_badge`)을 붙인다(기존 `dateLabel · memo` 조합의 첫 조각).
  - `FeedScreen(…, pendingCount: Int = 0, onOpenPending: () -> Unit = {})`
  - `SettingsPane.Pending`

화면 구성:
- 목록 행: 이름(비어 있으면 `이름 없음`) · 금액 · 날짜(`occurred_epoch_day`) · 사유 라벨. 비어 있으면 `EmptyState`(`확인할 거래가 없어요`).
- 행을 누르면 `ModalBottomSheet`: 종류 선택(지출/수입/저축, 기본값 `defaultKindFor(direction)`) → 그 종류의 대분류별 소분류 칩(`FlowRow`) → 칩을 누르면 `resolvePending(id, leafId)` 후 닫기. 맨 아래 `기록 안 함` 버튼 → `resolvePending(id, null)`. 종류 선택은 `EditTransactionScreen`의 private 컴포넌트를 건드리지 않고 `FilterChip` 3개로 새로 만든다(그 파일은 PR #8과 겹치므로 수정 금지).
- `MainTabScaffold`: 컨테이너에서 `observePendingCount()`를 수집해 `FeedScreen`에 넘기고, `onOpenPending`은 `selectedOrdinal = MainTab.SETTINGS.ordinal; settingsPane = SettingsPane.Pending`. Task 9의 `AutoEntrySettingsScreen.onOpenPending`도 `SettingsPane.Pending`으로 연결. 뒤로 가기: `Pending → AutoEntry`(`BackHandler` 가지 추가). 탭이 바뀔 때 `settingsPane`을 `Main`으로 되돌리는 기존 `LaunchedEffect(selected)`가 방금 설정한 `Pending`을 지우지 않는지 확인하고, 지우면 `selected`가 `SETTINGS`가 아닐 때만 초기화하는 현재 조건을 유지한 채 설정 순서를 맞춘다.
- `FeedScreen`: `pendingCount > 0`이면 목록 맨 위에 한 줄 배너 `확인이 필요한 거래 %d건` (누르면 `onOpenPending`).
- `TransactionRow` 호출 4곳(`FeedScreen`, `CalendarScreen`, `ArchiveDetailScreen`, `StatsScreen`)에 `isAuto = row.source == TransactionSource.AUTO` 전달.

- [ ] **Step 1: 실패하는 테스트 작성**
  ```kotlin
  @Test fun defaultKind() {
      assertEquals(CategoryKind.INCOME, defaultKindFor("IN")); assertEquals(CategoryKind.INCOME, defaultKindFor("REFUND"))
      assertEquals(CategoryKind.EXPENSE, defaultKindFor("OUT")); assertEquals(CategoryKind.EXPENSE, defaultKindFor("UNKNOWN"))
  }
  @Test fun reasonLabels() {
      assertEquals(R.string.auto_reason_duplicate, reasonLabelRes("DUP_MANUAL"))
      assertEquals(R.string.auto_reason_duplicate, reasonLabelRes("DUP_CROSS_APP"))
      assertEquals(R.string.auto_reason_direction, reasonLabelRes("UNKNOWN_DIRECTION"))
      assertEquals(R.string.auto_reason_unclassified, reasonLabelRes("UNCLASSIFIED"))
      assertEquals(R.string.auto_reason_unclassified, reasonLabelRes(null))
  }
  ```
- [ ] **Step 2: 실패 확인** — Run: `GRADLE :app:testDebugUnitTest --tests "*PendingLabelsTest"` → Expected: 컴파일 실패.
- [ ] **Step 3: 구현** — 위 구성대로.
- [ ] **Step 4: 통과 확인** — Run: `GRADLE :app:testDebugUnitTest` → 전부 통과. Run: `GRADLE :app:assembleDebug` → `BUILD SUCCESSFUL`.
- [ ] **Step 5: 커밋** — 수정·신규 파일 경로를 지정해 `git add` 후 `git commit -m "feat: 확인 필요 화면과 자동 기록 표시 추가"`

---

### Task 11: 에뮬레이터 종단 확인 `[Sonnet·기계적]`

코드를 더하지 않는 확인 태스크다. 문제가 나오면 원인 태스크의 테스트를 먼저 추가하고 고친다.

**Files:** 없음(확인 결과는 최종 보고에 적는다. 스크린샷은 저장소 밖 임시 폴더에 둔다).

**Interfaces:**
- Consumes: 디버그 빌드에서 `com.android.shell`이 기본 대상 앱에 들어간다(Task 8). `adb shell cmd notification post`가 올리는 알림의 패키지가 `com.android.shell`이다.

`ADB=~/Library/Android/sdk/platform-tools/adb`, `Q() { $ADB shell "run-as com.householdbudget.app sqlite3 databases/household_budget.db \"$1\""; }` (에뮬레이터에 `sqlite3`가 없으면 `run-as … cat`으로 `household_budget.db`, `-wal`, `-shm`을 임시 폴더로 꺼내 호스트 `sqlite3`로 본다).

- [ ] **Step 1: 에뮬레이터와 앱 준비** — `~/Library/Android/sdk/emulator/emulator -avd test35 -no-snapshot-save &` 후 `$ADB wait-for-device`. Run: `GRADLE :app:installDebug` → `BUILD SUCCESSFUL`. 앱을 한 번 실행해 DB를 만든다: `$ADB shell monkey -p com.householdbudget.app 1`.
- [ ] **Step 2: 고정 카테고리 확인** — `Q "SELECT COUNT(*) FROM categories WHERE parent_id IS NULL; SELECT COUNT(*) FROM categories WHERE parent_id IS NOT NULL;"` → Expected: `9`, `31`.
- [ ] **Step 3: 알림 접근 허용** — `$ADB shell cmd notification allow_listener com.householdbudget.app/com.householdbudget.app.autoentry.NotificationCaptureService`. 앱의 설정 > 자동 기록에서 "허용됨"으로 보이는지 스크린샷(`$ADB exec-out screencap -p`)으로 확인.
- [ ] **Step 4: 자동 등록** — `$ADB shell cmd notification post -S bigtext -t '가짜카드' tag1 '승인 4,500원 가짜커피 강남점'` → `Q "SELECT amount_minor, kind, source, memo FROM transactions ORDER BY id DESC LIMIT 1;"` → Expected: `4500|EXPENSE|AUTO|가짜커피 강남점`. 홈 목록에서 그 거래에 `자동` 표시가 보인다.
- [ ] **Step 5: 재수신 중복 없음** — 같은 명령을 바로 한 번 더 실행 → `Q "SELECT COUNT(*) FROM transactions WHERE source='AUTO';"` → Expected: `1`.
- [ ] **Step 6: 확인 필요 → 학습** — `… post -S bigtext -t '출금 30,000원' tag2 '김가짜 | 가짜통장(1234)'` → `Q "SELECT status, reason FROM auto_entries ORDER BY id DESC LIMIT 1;"` → `PENDING|UNCLASSIFIED`. 홈 배너 `확인이 필요한 거래 1건` → 눌러서 `유동 지출 > 기본` 선택 → 거래 생성, `Q "SELECT COUNT(*) FROM merchant_rules;"` → `1`. 11초 뒤 `… post -S bigtext -t '출금 20,000원' tag3 '김가짜 | 가짜통장(1234)'` → 바로 등록(`REGISTERED`).
- [ ] **Step 7: 금액 없는 알림** — `… post -t '로그인 알림' tag4 '새 기기에서 로그인했어요'` → `auto_entries` 행 수 불변, `notification_log` 행 수 +1.
- [ ] **Step 8: 로그 미노출 확인** — `$ADB logcat -d | grep -E "가짜커피|김가짜"` 결과 중 `com.householdbudget.app` 프로세스가 찍은 줄이 없어야 한다(시스템 `NotificationService` 줄은 대상이 아니다).
- [ ] **Step 9: 업그레이드 경로** — `git stash` 없이 확인: `git worktree add /tmp/hb-master origin/master`에서 `GRADLE :app:installDebug`로 v6 앱을 깔고(먼저 `$ADB uninstall com.householdbudget.app`), 수동 거래 2건과 반복 규칙 1건을 UI로 만든 뒤, 이 브랜치의 디버그 빌드를 덮어 설치(`installDebug`) → 앱이 뜨고, 거래 2건이 남아 있고, `Q "SELECT COUNT(*) FROM sqlite_master WHERE name='recurring_rules';"` → `0`. 끝나면 `git worktree remove /tmp/hb-master`.
- [ ] **Step 10: 전체 테스트** — Run: `GRADLE :core:test :app:testDebugUnitTest :app:assembleDebug` → `BUILD SUCCESSFUL`.

---

## 스펙 대비 확인 (self-review 결과)

| 스펙 | 태스크 |
|---|---|
| 5.1 고정 카테고리, 유동 지출, 관리 화면 제거 | 1, 2, 3 |
| 5.2 v6→v7 이전, 예산 합치기, 메모 흔적, top-up 제거 | 1, 2, 3 |
| 5.3 설정 섹션, 대상 앱만 처리, 공통·앱별 파서, 자동 표시 | 4, 6, 7, 8, 9, 10 |
| 5.4 분류 우선순위, 확인 필요, 규칙 목록 | 5, 7, 9, 10 |
| 5.5 원문 로그 90일 | 6, 7, 8 |
| 5.6 재수신·충전·이중 알림·수동 중복, 반복 규칙 제거, kbank 문자열 삭제 | 2, 3, 5, 7, 8 |
| 5.7 백업 설정 유지 | 8(변경 없음을 명시) |
| 7 검증(JVM 테스트, 마이그레이션 테스트, 에뮬레이터) | 1, 3, 4, 5, 7, 11 |

스펙에 없지만 계획이 정한 것(사용자 확인 대상): 옛 대분류 아래 모르는 소분류는 "기타"가 아니라 그 대분류의 대표 소분류로 보냄(Task 1 `*`), 확인 필요 하나를 고르면 같은 이름의 다른 대기 건에도 적용(Task 7), 자동 등록된 거래의 카테고리를 편집 화면에서 바꾸면 규칙으로 학습(Task 7), "중복 같아요" 처리는 학습하지 않음(Task 7), 이중 알림 기준 5분·같은 내용 재게시 기준 10초(Task 7), 기본 대상 앱은 설치된 금융 앱만(Task 8).
