# 안정성 수정 + 가계부 화살표 UX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 가계부 피드의 달 이동을 매끄럽게 만들고(전환 애니메이션·레이아웃 고정·release 최적화), 정적 점검에서 나온 버그(H1·H2·M1·M3·M4·M5·M6)와 쉬운 낮음 항목을 한 묶음으로 고친다.

**Architecture:** "오늘"과 현재 회계월을 `BudgetRepository` 한곳의 Flow로 모아 화면·ViewModel이 전부 그것만 보게 한다(자정 넘김·CSV·통계가 한 번에 맞는다). 지난 기록 합계는 저장 스냅샷 대신 SQL JOIN으로 실시간 계산한다. 자동 기록은 스키마 변경 없이 알림 원문 보관을 줄이고(해시만, 24시간 뒤 비움), 감시 대상 확인을 뮤텍스 앞의 값싼 검사와 뮤텍스 안의 정확한 검사 두 단계로 나눈다. 피드 전환은 `AnimatedContent`가 아니라 기간 키로 다시 만드는 `Animatable` 하나를 `graphicsLayer`로 읽는 "들어오는 쪽만" 애니메이션이다.

**Tech Stack:** Kotlin 2.0.21, Compose BOM 2024.10.01(foundation 1.7), Room 2.6.1, DataStore 1.1.1, lifecycle 2.8.6, JUnit4 + Robolectric 4.14.1(`runBlocking`, coroutines-test 없음).

**Spec:** `docs/superpowers/specs/2026-11-19-stability-and-feed-ux-design.md` — 각 태스크 구현자는 자기 태스크 + Global Constraints + 스펙을 함께 읽는다.

## Global Constraints

- 작업 위치: `/Users/ffingumac/orca/household-budget-android`, 브랜치 `fix/stability-and-feed-ux`. 다른 브랜치로 옮기지 않는다.
- 신규 의존성 금지(운영·테스트 모두). `profileinstaller`도 추가하지 않는다(아래 "빌더 결정" 5번).
- UI 문구는 한국어, `app/src/main/res/values/strings_*.xml`에 둔다. 코드에 문자열 하드코딩 금지.
- 로그(Logcat/`println`)에 알림 원문·금액·메모를 출력하지 않는다. 새 로그를 추가하지 않는다.
- 기존 거래(`transactions` 행)는 삭제하지 않는다. DB 스키마·버전(8) 변경 없음, 마이그레이션 추가 없음.
- 저장소 루트의 `.xlsx/.xls/.numbers/.csv`는 열지도 `git add` 하지도 않는다. `git add`는 항상 파일 경로를 지정한다(`git add -A`, `git add .` 금지).
- `core/bin/` 아래는 IDE 복사본이다. 고치지도 `git add` 하지도 않는다.
- UI 확인은 에뮬레이터 AVD `test35`에서만, 가짜 데이터로. 실제 폰에는 adb 명령을 보내지 않는다. adb는 항상 `-s emulator-5580`을 붙인다(Task 7이 이 포트로 띄운다).
- 커밋 메시지는 한국어(`fix: …`, `feat: …`, `test: …`), 태스크 하나에 커밋 하나. 마지막 줄 트레일러는 그 태스크를 실제로 돌린 모델 이름: `Co-Authored-By: Claude <모델 이름> <noreply@anthropic.com>`.
- 빌드·테스트 명령(저장소 루트에서, `./gradlew` 없음). 아래에서 `GRADLE`은 이 줄 전체다:
  `export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=~/Library/Android/sdk; ~/.gradle/wrapper/dists/gradle-8.10.2-bin/a04bxjujx95o3nb99gddekhwo/gradle-8.10.2/bin/gradle`
- 앱 단위 테스트는 기존 형식을 따른다: `@RunWith(RobolectricTestRunner::class) @Config(sdk = [34], application = android.app.Application::class)`, `runBlocking`, `Room.inMemoryDatabaseBuilder(...).allowMainThreadQueries()`, `AppDatabase.seedCategoriesIfEmpty(db.categoryDao())`. 순수 함수 테스트는 러너 없이 JUnit4.
- 모델 배정: 태스크 제목의 `[Sonnet·기계적]` / `[Opus·까다로움]`.
- 제외(스펙): M2(월급일 1–5일 달 제목), 환불의 수입 처리.

## 빌더 결정 (스펙이 맡긴 선택)

1. **M4 누락 방지 = "기록하지 않은 알림" 목록 + 되살리기** (10분 타이머 아님). 타이머는 "어느 페이 앱을 기다리는지"를 저장할 컬럼(마이그레이션)과 재검사 시점이 필요하고, 충전 뒤 한참 있다 결제하는 정상 흐름을 오탐한다. 목록은 이미 있는 `auto_entries.status = 'IGNORED'` 행을 보여주기만 하면 되고 유실이 없다. 되살린 항목은 학습 대상이 아닌 새 사유 `RESTORED`로 확인 필요에 들어간다(충전 문구를 규칙으로 배워 이후 충전이 이중 기록되는 일을 막는다).
2. **M4 교집합의 테스트 계정 예외:** `com.android.shell`은 런처 액티비티가 없어 `<queries>` 가시성 밖이라 "설치 안 됨"으로 보인다. debuggable 빌드에서는 설치 확인 없이 통과시킨다.
3. **M6 캐시 방식:** prefs Flow를 `shareIn(replay = 1)`으로 캐시한다. 뮤텍스 앞 검사는 `저장값 ?: KnownApps.FINANCE_PACKAGES`(PackageManager 호출 0회)로 하고, 통과한 알림(금융 앱 알림뿐)에서만 `WatchedApps.effective`를 새로 계산한다. 스펙의 "기본값 1회 계산"을 글자 그대로 하면(프로세스 수명 동안 기본값 고정) 은행 앱을 새로 깔았을 때 설정 화면과 실제 동작이 어긋나고, 페이 앱을 지웠을 때 M4가 다시 생긴다.
4. **H2 백업:** Room은 DB 파일 하나(`household_budget.db`)라 테이블 단위 제외가 불가능하다. 파일 전체를 빼면 거래 내역 백업도 사라진다. 대안으로 (a) 항목을 만들지 않은 로그는 처음부터 해시만 저장, (b) 항목을 만든 로그의 원문도 24시간 뒤 비움(원문을 읽는 유일한 코드가 ±5분 창의 재게시 판별이다), (c) 클라우드 백업은 종단간 암호화가 될 때만(`disableIfNoEncryptionCapabilities`, API 30 이하는 `requireFlags="clientSideEncryption"`) 허용한다. `auto_entries.merchant`는 확인 필요 화면과 거래 메모에 쓰이므로 남긴다.
5. **A4:** R8(`isMinifyEnabled`, `isShrinkResources`)를 켠다. 리플렉션·직렬화 점검 결과 앱 코드에는 없고(`Class.forName`·kotlin-reflect·Parcelize·Gson 없음), Room·WorkManager·Navigation·FileProvider는 라이브러리/aapt 규칙이 지킨다. DataStore Preferences의 protobuf-lite 필드만 keep 규칙 한 줄을 넣는다. `profileinstaller`는 추가하지 않는다 — 이미 Compose가 1.3.1을 전이 의존성으로 끌어와 APK에 들어 있어(`:app:dependencies`로 확인) 라이브러리 baseline profile은 설치된다. 앱 전용 baseline profile은 Macrobenchmark 모듈(신규 의존성)이 필요해 보류.
6. **release 서명:** release에 서명 설정이 없어 지금은 설치 가능한 release APK가 나오지 않는다. debug 키로 서명해 debug 설치본 위에 데이터 유지한 채 덮어 깔 수 있게 한다.
7. **A1 애니메이션 방식:** 새 내용이 들어올 때만 180ms 페이드 + 24dp 슬라이드. 나가는 쪽 교차 페이드는 하지 않는다 — `LazyColumn`을 `AnimatedContent`에 넣으면 `LazyListState` 하나를 두 목록이 공유하게 되고 검색창 포커스가 끊긴다.
8. **A2 예산 막대:** 자리 유지 대신 `animateContentSize`(지난 달을 볼 때 빈 자리를 남기지 않는다. 화살표는 높이가 변하는 곳보다 위에 있어 손가락 밑에서 움직이지 않는다).
9. **M1:** 두 StateFlow 결합 대신 "소분류가 이미 있으면 자동 선택은 아무것도 하지 않는다"는 순수 함수.
10. **M3:** `aggregateBetween`을 기간마다 부르는 대신 `archived_periods LEFT JOIN transactions` 쿼리 하나(같은 합계, Room이 두 테이블 변경을 모두 감지). 상세 화면은 이미 구독 중인 거래 목록에서 합계를 낸다. 스냅샷 컬럼은 그대로 두고 읽지 않는다.
11. **자정 넘김:** 앱 복귀(`onResume`) 시 갱신. 자정 틱은 만들지 않는다.
12. **M5 잠금 중 화면 보존:** 다시 잠글 때 내용을 컴포지션에서 빼면 입력 중이던 거래·내비게이션 위치가 사라진다. 한 번이라도 풀린 뒤에는 내용을 그대로 두고 불투명한 잠금 화면으로 덮는다.

## Review Focus

스펙이 직접 말하지 않지만 실제 사용에서 먼저 터질 조건. 괄호 안이 그것을 고정하는 테스트/확인이다.

1. **거래 입력 중에 은행 앱을 보러 나갔다 옴(잠금 켜짐):** 다시 잠겼다 풀린 뒤 입력하던 금액·화면이 그대로여야 한다. (Task 5 `shouldComposeContent_keepsContentWhileRelocked`, Task 7 Step 5)
2. **화살표 연타 / 애니메이션 도중 "오늘로":** 마지막에 고른 달이 완전히 불투명하게, 제자리에 보여야 한다. (Task 6 `slideDirection_*`, Task 7 Step 7의 5연타 후 화면 덤프)
3. **대분류에 직접 걸린 옛 거래·소분류 목록에 없는 카테고리:** 편집 화면을 열기만 해서는 카테고리가 바뀌면 안 된다. (Task 1 `autoCategorySelection_keepsCategoryNotAmongLeaves`)
4. **거래가 하나도 없는 지난 달, 경계일 거래:** 지난 기록 목록에서 그 달이 사라지지 않고 0원으로 보이며, 끝(미포함) 날짜의 거래는 다음 달에만 잡힌다. (Task 2 `archivedPeriods_emptyPeriodStaysWithZeroTotals`, `archivedPeriods_endDayBelongsToNextPeriod`)
5. **저장된 감시 목록이 전부 지워진 앱이거나, debug 빌드의 Shell 테스트 앱:** 전부 지워졌으면 빈 집합(충전을 "기록 안 함"으로 삼키지 않음), Shell은 debuggable에서 유지. (Task 3 `filterInstalled_*`)

---

## File Structure

| 파일 | 변경 | 태스크 |
|---|---|---|
| `app/.../ui/EditTransactionViewModel.kt` | 재진입 방지, `autoCategorySelection` | 1 |
| `app/.../ui/edit/EditTransactionScreen.kt` | 자동 선택 호출부 | 1 |
| `app/.../ui/BudgetNavHost.kt` | 멱등 닫기 | 1 |
| `app/.../data/repository/BudgetRepository.kt` | `today`·`currentPeriod`·`observeSummaryAtOffset`, `HomeSummary` 합계 1회 계산 | 2 |
| `app/.../data/local/dao/ArchivedPeriodDao.kt` | 실시간 합계 쿼리 | 2 |
| `app/.../ui/BudgetViewModel.kt`, `ui/stats/StatsViewModel.kt`, `MainActivity.kt` | 단일 출처 사용, 복귀 시 갱신 | 2 |
| `ui/stats/StatsScreen.kt`, `ui/calendar/CalendarScreen.kt`, `ui/settings/SettingsScreen.kt`, `ui/settings/BudgetSettingsScreen.kt`, `ui/archive/ArchiveListScreen.kt`, `ui/archive/ArchiveDetailScreen.kt` | `today` 구독, Flow remember, 실시간 합계 | 2 |
| `app/.../data/export/CsvExporter.kt` | 수식 주입 방지 | 2 |
| `app/.../data/repository/AutoEntryRepository.kt`, `dao/NotificationLogDao.kt`, `dao/AutoEntryDao.kt` | 원문 최소화, 정리, 뮤텍스 앞 검사 / 되살리기 | 3, 4 |
| `app/.../autoentry/WatchedApps.kt`, `AppContainer.kt` | 교집합, 캐시 | 3 |
| `AndroidManifest.xml`, `res/xml/data_extraction_rules.xml`, `res/xml/backup_rules.xml` | 백업 규칙 | 3 |
| `core/.../autoentry/AutoClassifier.kt` | `ReviewReason.RESTORED` | 4 |
| `ui/autoentry/AutoEntrySettingsScreen.kt`, `ui/autoentry/PendingReviewScreen.kt`, `res/values/strings_auto.xml` | 기록하지 않은 알림 목록 | 4 |
| `app/.../security/AppLockGate.kt` | 다시 잠김, 로드 전 빈 화면 | 5 |
| `app/.../ui/feed/FeedLogic.kt`(신규), `ui/feed/FeedScreen.kt` | 그룹핑·방향 순수 함수, 애니메이션·레이아웃 | 6 |
| `app/build.gradle.kts`, `app/proguard-rules.pro` | R8, release 서명 | 6 |

경로의 `app/...`은 `app/src/main/java/com/householdbudget/app`, 테스트는 `app/src/test/java/com/householdbudget/app`, `core/...`는 `core/src/main/kotlin/com/householdbudget/app/domain`이다.

---

### Task 1: 편집 화면 — 저장 중복(H1)·소분류 초기화(M1) [Sonnet·기계적]

**Files:**
- Modify: `app/.../ui/EditTransactionViewModel.kt` (`save` 180–212, `delete` 214–225, 파일 끝에 순수 함수)
- Modify: `app/.../ui/edit/EditTransactionScreen.kt:148-167`
- Modify: `app/.../ui/BudgetNavHost.kt:67,91`
- Test: `app/src/test/.../ui/EditTransactionViewModelTest.kt` (신규)

**Interfaces:**
- Consumes: 없음
- Produces:
  ```kotlin
  // EditTransactionViewModel.kt 최상위
  /** 자동으로 고를 (대분류 id, 소분류 id). null = 지금 선택을 그대로 둔다. */
  internal fun autoCategorySelection(
      parents: List<CategoryEntity>,
      childrenByParent: Map<Long, List<CategoryEntity>>,
      parentId: Long?,
      categoryId: Long?,
  ): Pair<Long, Long?>?
  ```

- [ ] **Step 1: 실패하는 테스트 작성** — `EditTransactionViewModelTest`

  준비: 인메모리 DB를 `.allowMainThreadQueries().setQueryExecutor(Runnable::run).setTransactionExecutor(Runnable::run)`으로 만들어 DAO 호출이 호출 스레드에서 끝나게 한다(테스트 스레드가 Robolectric 메인 스레드라 `viewModelScope`가 `save()` 안에서 끝까지 실행된다). 그래도 비동기로 남으면 단언 전에 `shadowOf(Looper.getMainLooper()).idle()`을 부른다. `BudgetRepository`는 `AutoEntryRepositoryTest.budgetRepository_editingAutoCategory_callsHook`처럼 만든다. `leaf`는 `db.categoryDao().findLeafByPath("EXPENSE", "식비", "카페")!!`.

  ```kotlin
  @Test fun save_calledTwice_insertsOnce() {
      val vm = EditTransactionViewModel(repo, null)
      vm.setAmountText("4500"); vm.setCategoryId(leaf.id)
      var successes = 0
      vm.save({ successes++ }, {}); vm.save({ successes++ }, {})
      assertEquals(1, txCount()); assertEquals(1, successes)
      assertTrue(vm.uiState.value.isSaving)          // 성공 뒤에도 풀지 않는다
  }

  @Test fun delete_calledTwice_callsSuccessOnce() {   // 거래 1건을 넣고 그 id로 VM 생성
      var successes = 0
      vm.delete { successes++ }; vm.delete { successes++ }
      assertEquals(0, txCount()); assertEquals(1, successes)
  }

  @Test fun save_invalid_doesNotLockButton() {
      val vm = EditTransactionViewModel(repo, null)
      var invalid = 0
      vm.save({}, { invalid++ })
      assertEquals(1, invalid); assertFalse(vm.uiState.value.isSaving)
  }

  // 순수 함수. p1(id=1), p2(id=2) 대분류, c11(id=11,parent=1), c21(id=21), c22(id=22, parent=2)
  @Test fun autoCategorySelection_childrenNotLoaded_keepsSelection() =
      assertNull(autoCategorySelection(listOf(p1, p2), emptyMap(), 2, 22))
  @Test fun autoCategorySelection_existingCategory_isNotChanged() =
      assertNull(autoCategorySelection(listOf(p1, p2), children, 2, 22))
  @Test fun autoCategorySelection_keepsCategoryNotAmongLeaves() =   // 대분류에 직접 걸린 옛 거래
      assertNull(autoCategorySelection(listOf(p1, p2), children, null, 2))
  @Test fun autoCategorySelection_newTransaction_picksFirstParentAndLeaf() =
      assertEquals(1L to 11L, autoCategorySelection(listOf(p1, p2), children, null, null))
  @Test fun autoCategorySelection_parentChosenNoLeafYet_picksFirstLeaf() =
      assertEquals(2L to 21L, autoCategorySelection(listOf(p1, p2), children, 2, null))
  @Test fun autoCategorySelection_parentsNotLoaded_keepsSelection() =
      assertNull(autoCategorySelection(emptyList(), children, null, null))
  ```

- [ ] **Step 2: 실패 확인** — `GRADLE :app:testDebugUnitTest --tests "com.householdbudget.app.ui.EditTransactionViewModelTest"` → `autoCategorySelection` 미정의로 컴파일 실패.

- [ ] **Step 3: `autoCategorySelection` 구현.** 규칙(위에서부터): `categoryId != null` → null. `parents`나 `childrenByParent`가 비었으면 → null. 대분류는 `parentId`가 `parents`에 있으면 그것, 없으면 첫 대분류. 소분류는 그 대분류의 첫 소분류(없으면 null). 결과가 현재 `(parentId, null)`과 같으면 null.

- [ ] **Step 4: `save`/`delete` 재진입 방지.** 두 함수 모두 첫 줄 `if (_uiState.value.isSaving) return`. `save`는 유효성 검사(→ `onInvalid`) 뒤, `launch` **앞에서** 동기적으로 `isSaving = true`. `finally`의 리셋을 없애고, 실패(예외)일 때만 `isSaving = false`로 되돌린 뒤 다시 던진다.

- [ ] **Step 5: 호출부.** `EditTransactionScreen`의 `LaunchedEffect(parents, leaves, ...)` 본문을 `autoCategorySelection(parents, childrenByParent, ui.parentId, ui.categoryId)?.let { (p, leafId) -> vm.setParent(p, childrenByParent[p]?.firstOrNull { it.id == leafId }) }`로 바꾼다(`loadFinished` 가드 유지, 키에 `childrenByParent` 추가). `BudgetNavHost`의 두 `onClose`를 `{ navController.popBackStack(BudgetRoutes.TABS, inclusive = false) }`로 바꾼다.

- [ ] **Step 6: 통과 확인** — Step 2 명령 → PASS. `GRADLE :app:testDebugUnitTest` 전체 PASS.

- [ ] **Step 7: 커밋** — `git add` 위 4개 파일, `fix: 편집 화면 저장·삭제 중복 실행과 소분류 초기화 수정`.

---

### Task 2: 오늘·회계월 단일 출처, 지난 기록 실시간 합계, CSV (M3, C 전부, A3 일부) [Opus·까다로움]

**Files:**
- Modify: `app/.../data/repository/BudgetRepository.kt`, `app/.../data/local/dao/ArchivedPeriodDao.kt`
- Modify: `app/.../ui/BudgetViewModel.kt`, `app/.../ui/stats/StatsViewModel.kt`, `app/.../MainActivity.kt`
- Modify: `ui/stats/StatsScreen.kt:98`, `ui/calendar/CalendarScreen.kt:75,329`, `ui/settings/SettingsScreen.kt:96-98,143-159`, `ui/settings/BudgetSettingsScreen.kt:87`, `ui/archive/ArchiveListScreen.kt:67,115`, `ui/archive/ArchiveDetailScreen.kt:109-112,128,146-150`
- Modify: `app/.../data/export/CsvExporter.kt:82-88`
- Test: `app/src/test/.../data/repository/BudgetRepositoryTest.kt` (신규), `app/src/test/.../data/export/CsvExporterTest.kt` (신규, 러너 없음)

**Interfaces:**
- Consumes: 없음
- Produces (Task 6이 쓴다):
  ```kotlin
  class BudgetRepository(
      /* 기존 파라미터 그대로 */,
      private val todayProvider: () -> LocalDate = { LocalDate.now(zoneId) },   // 맨 끝에 추가
  ) {
      val today: StateFlow<LocalDate>                 // 앱 전체의 "오늘"
      fun refreshToday()                              // today.value = todayProvider()
      val currentPeriod: Flow<BudgetPeriod>           // (월급일, today) → 기간, distinctUntilChanged
      fun observeSummaryAtOffset(offset: Int): Flow<HomeSummary>   // 0 = 현재, -1 = 직전
      fun observeHomeSummary(): Flow<HomeSummary>     // = observeSummaryAtOffset(0)
      fun observeArchivedPeriods(): Flow<List<ArchivedPeriodEntity>>   // 합계 필드가 실시간 값
  }
  data class HomeSummary(period, transactions) {
      val totalIncomeMinor: Long; val totalExpenseMinor: Long; val totalSavingsMinor: Long; val netMinor: Long
      // getter가 아니라 생성 시 1회 계산하는 val
  }
  // BudgetViewModel
  val today: StateFlow<LocalDate>   // = repository.today
  // CsvExporter
  internal fun csvCell(value: String): String
  ```

- [ ] **Step 1: 실패하는 테스트 작성**

  `BudgetRepositoryTest`(Robolectric, 인메모리 DB): `var fakeToday = LocalDate.of(2026, 11, 19)`, `repo = BudgetRepository(db, ..., UserPreferencesRepository(context), todayProvider = { fakeToday })`. 월급일 기본값 25 → 2026-11-19가 속한 기간은 `2026-10-23 ~ 2026-11-25`(10-25가 일요일이라 10-23으로 당겨짐), 직전은 `2026-09-23 ~ 2026-10-23`(9-25가 추석이라 9-23으로 당겨짐). `S = LocalDate.of(2026, 9, 23).toEpochDay()`, `E = LocalDate.of(2026, 10, 23).toEpochDay()`. `tx(day, amount, kind)`는 `db.transactionDao().insert(TransactionEntity(...))` 도우미, `archive(S, E)`는 합계 0으로 `ArchivedPeriodEntity`를 넣는 도우미.

  ```kotlin
  @Test fun archivedPeriods_totalsFollowEditedTransactions() = runBlocking {
      archive(S, E)                                   // 스냅샷은 0/0/0
      val id = tx(S + 3, 10_000, "EXPENSE"); tx(S + 4, 300_000, "INCOME")
      var row = repo.observeArchivedPeriods().first().single()
      assertEquals(10_000L, row.totalExpenseMinor); assertEquals(300_000L, row.totalIncomeMinor)
      repo.updateTransaction(id, LocalDate.ofEpochDay(S + 3), 25_000, leaf.id, "")
      row = repo.observeArchivedPeriods().first().single()
      assertEquals(25_000L, row.totalExpenseMinor)
  }
  @Test fun archivedPeriods_emptyPeriodStaysWithZeroTotals() = runBlocking {
      archive(S, E)
      val row = repo.observeArchivedPeriods().first().single()
      assertEquals(0L, row.totalIncomeMinor + row.totalExpenseMinor + row.totalSavingsMinor)
  }
  @Test fun archivedPeriods_endDayBelongsToNextPeriod() = runBlocking {
      archive(S, E); tx(E, 7_000, "EXPENSE"); tx(E - 1, 1_000, "EXPENSE")
      assertEquals(1_000L, repo.observeArchivedPeriods().first().single().totalExpenseMinor)
  }
  @Test fun currentPeriod_followsTodayAfterRefresh() = runBlocking {
      assertEquals(LocalDate.of(2026, 10, 23), repo.currentPeriod.first().startInclusive)
      fakeToday = LocalDate.of(2026, 11, 25)
      assertEquals(LocalDate.of(2026, 10, 23), repo.currentPeriod.first().startInclusive)   // 갱신 전
      repo.refreshToday()
      assertEquals(LocalDate.of(2026, 11, 25), repo.currentPeriod.first().startInclusive)
      assertEquals(LocalDate.of(2026, 11, 25), repo.observeHomeSummary().first().period.startInclusive)
  }
  @Test fun observeSummaryAtOffset_minusOne_isPreviousPeriod() = runBlocking {
      tx(S + 1, 5_000, "EXPENSE")
      val s = repo.observeSummaryAtOffset(-1).first()
      assertEquals(LocalDate.of(2026, 9, 23), s.period.startInclusive)
      assertEquals(5_000L, s.totalExpenseMinor); assertEquals(-5_000L, s.netMinor)
  }
  ```

  `CsvExporterTest`:
  ```kotlin
  @Test fun csvCell_prefixesFormulaTriggers() {
      assertEquals("'=1+1", CsvExporter.csvCell("=1+1"))
      assertEquals("'+82", CsvExporter.csvCell("+82"))
      assertEquals("'-5000 환불", CsvExporter.csvCell("-5000 환불"))
      assertEquals("\"'@SUM(A1,B1)\"", CsvExporter.csvCell("@SUM(A1,B1)"))   // 접두 뒤 RFC 4180 인용
  }
  @Test fun csvCell_leavesOrdinaryTextAlone() {
      assertEquals("스타벅스", CsvExporter.csvCell("스타벅스")); assertEquals("", CsvExporter.csvCell(""))
      assertEquals("a=b", CsvExporter.csvCell("a=b"))
  }
  ```

- [ ] **Step 2: 실패 확인** — `GRADLE :app:testDebugUnitTest --tests "*BudgetRepositoryTest" --tests "*CsvExporterTest"` → 컴파일 실패.

- [ ] **Step 3: `ArchivedPeriodDao.observeAll()` 쿼리 교체** (이름·반환형 유지):

  ```sql
  SELECT a.id, a.start_epoch_day, a.end_epoch_day, a.archived_at_epoch_ms,
         COALESCE(SUM(CASE WHEN t.kind = 'INCOME'  THEN t.amount_minor ELSE 0 END), 0) AS total_income_minor,
         COALESCE(SUM(CASE WHEN t.kind = 'EXPENSE' THEN t.amount_minor ELSE 0 END), 0) AS total_expense_minor,
         COALESCE(SUM(CASE WHEN t.kind = 'SAVINGS' THEN t.amount_minor ELSE 0 END), 0) AS total_savings_minor
  FROM archived_periods a
  LEFT JOIN transactions t
         ON t.occurred_epoch_day >= a.start_epoch_day AND t.occurred_epoch_day < a.end_epoch_day
  GROUP BY a.id ORDER BY a.start_epoch_day DESC
  ```

- [ ] **Step 4: `BudgetRepository`.** Interfaces대로 `today`(MutableStateFlow, 초기값 `todayProvider()`), `refreshToday`, `currentPeriod`, `observeSummaryAtOffset`(`combine(paydayDom, today)`로 기간을 구해 `-offset`번 `previousPeriod` → `distinctUntilChanged` → `flatMapLatest { observeBetween → HomeSummary }`)를 추가한다. `observeHomeSummary`·`observeTransactionsInCurrentPeriod`·`tryArchiveCompletedPeriods`의 `LocalDate.now(zoneId)`를 이 출처로 바꾼다. `HomeSummary`의 세 합계와 `netMinor`를 본문 `val`로 바꾼다.

- [ ] **Step 5: ViewModel·Activity.** `BudgetViewModel`: `today` 노출, 사설 `observeSummaryAtOffset` 삭제 후 `_ledgerPeriodOffset.flatMapLatest(repository::observeSummaryAtOffset)`, `daysRemainingInPeriod`는 `combine(repository.currentPeriod, repository.today)`. `StatsViewModel`: `combine(repository.paydayDom, repository.currentPeriod)`로 (기간, 직전 기간)을 만들고 `distinctUntilChanged` 뒤 기존 `combine`. `MainActivity`: `override fun onResume() { super.onResume(); app.container.budgetRepository.refreshToday() }` (`// shortcut: 앱을 켠 채 자정을 넘기면 다음 복귀 때 갱신된다, 자정 틱이 필요해지면 추가`).

- [ ] **Step 6: 화면.**
  - `StatsScreen:98`, `CalendarScreen:75`, `ArchiveDetailScreen:128`, `ArchiveListScreen:115`(카드에는 `today` 파라미터로 내려준다): `LocalDate.now(...)` → `val today by repository.today.collectAsStateWithLifecycle()`.
  - `SettingsScreen`: `nextPayday`의 키를 `remember(payday, today)`로, `LocalDate.now` 대신 `today`. `exportCsv`는 `repository.refreshToday(); val period = repository.currentPeriod.first()`로 내보내는 순간의 기간을 쓴다(`budgetViewModel.homeSummary.value` 사용 제거).
  - Flow remember: `CalendarScreen:329` `remember(repository, epoch) { repository.observeTransactionsOnDay(epoch) }`, `ArchiveDetailScreen:109` `remember(repository, h.startEpochDay, h.endEpochDay) { ... }`, `ArchiveListScreen:67` `remember(repository) { repository.observeArchivedPeriods() }`, `BudgetSettingsScreen:87` `remember(repository) { repository.observeCategoryBudgets() }`.
  - `ArchiveDetailScreen`: 거래 Flow의 초기값을 `null`로 하고, `KindSummaryRow`에는 `h.total*` 대신 `remember(period, txs) { HomeSummary(period, txs) }`의 합계를 넘긴다. 로드 전(`null`)에는 합계 줄과 목록을 그리지 않는다.

- [ ] **Step 7: `CsvExporter`.** `private fun escape`를 `internal fun csvCell`로 바꾼다: 첫 글자가 `=`, `+`, `-`, `@` 중 하나면 앞에 `'`를 붙인 뒤 기존 RFC 4180 처리. 호출부 5곳 이름 변경(금액 칸은 숫자 그대로).

- [ ] **Step 8: 통과 확인** — Step 2 명령 PASS, `GRADLE :app:testDebugUnitTest :core:test` 전체 PASS.

- [ ] **Step 9: 커밋** — 고친 파일 경로를 나열해 `git add`, `fix: 오늘·회계월을 한곳에서 계산하고 지난 기록 합계를 실시간으로, CSV 수식 주입 방지`.

---

### Task 3: 자동 기록 — 원문 최소화(H2)·설치된 앱 교집합(M4)·알림당 비용(M6) [Opus·까다로움]

**Files:**
- Modify: `app/.../data/repository/AutoEntryRepository.kt`, `app/.../data/local/dao/NotificationLogDao.kt`, `app/.../data/local/dao/AutoEntryDao.kt`
- Modify: `app/.../autoentry/WatchedApps.kt`, `app/.../AppContainer.kt`
- Modify: `app/src/main/AndroidManifest.xml`; Create: `app/src/main/res/xml/data_extraction_rules.xml`, `app/src/main/res/xml/backup_rules.xml`
- Test: `app/src/test/.../data/repository/AutoEntryRepositoryTest.kt`, `app/src/test/.../autoentry/WatchedAppsTest.kt`

**Interfaces:**
- Consumes: 없음
- Produces:
  ```kotlin
  class AutoEntryRepository(
      private val database: AppDatabase,
      private val watchedPackages: suspend () -> Set<String>,          // 정확한 집합(뮤텍스 안에서 호출)
      private val isCandidate: suspend (String) -> Boolean = { true },  // 값싼 사전 검사(뮤텍스 앞)
      private val zoneId: ZoneId = ZoneId.of("Asia/Seoul"),
  )
  companion: const val LOG_TEXT_RETENTION_MS = 24 * 60 * 60_000L

  // NotificationLogDao
  @Query("UPDATE notification_log SET title = :title, body = :body WHERE id = :id")
  suspend fun setText(id: Long, title: String, body: String)
  @Query("UPDATE notification_log SET title = '', body = '' WHERE (title != '' OR body != '') " +
      "AND (post_time_ms < :cutoffMs OR id NOT IN (SELECT log_id FROM auto_entries))")
  suspend fun clearStaleText(cutoffMs: Long): Int
  // AutoEntryDao
  @Query("DELETE FROM auto_entries WHERE status != 'PENDING' AND posted_at_ms < :cutoffMs")
  suspend fun deleteResolvedOlderThan(cutoffMs: Long): Int

  // WatchedApps
  fun isCandidate(pkg: String, stored: Set<String>?, debuggable: Boolean): Boolean
  fun filterInstalled(packages: Set<String>, debuggable: Boolean, isInstalled: (String) -> Boolean): Set<String>
  fun effective(stored: Set<String>?, context: Context): Set<String>   // 저장값은 filterInstalled를 거친다
  ```

- [ ] **Step 1: 실패하는 테스트 작성**

  `AutoEntryRepositoryTest`에 추가(`logTexts()`는 `SELECT title || body FROM notification_log`, `logHashes()`는 `SELECT content_hash FROM notification_log` 결과 목록을 읽는 도우미):
  ```kotlin
  @Test fun unparsedNotification_storesHashOnly() = runBlocking {
      assertEquals(HandleOutcome.LOGGED_ONLY, handle(n(KBANK, "가짜 이벤트", "금액 없는 광고 문구")))
      assertEquals(listOf(""), logTexts())
      assertEquals(64, logHashes().single().length)
  }
  @Test fun groupSummary_storesHashOnly() = runBlocking {
      handle(n(CARD, "가짜카드", "승인 4,500원\n가짜커피").copy(isGroupSummary = true))
      assertEquals(listOf(""), logTexts())
  }
  @Test fun purge_clearsTextAfter24h_andStillDetectsRepostBefore() = runBlocking {
      handle(n(CARD, "가짜카드", "승인 4,500원 가짜커피"))
      repo.purgeOldLogs(T0 + 60_000)                                   // 1분 뒤: 원문 유지
      assertEquals(HandleOutcome.DUPLICATE,
          handle(n(CARD, "가짜카드", "승인 4,500원 가짜커피 잔액 10,000원", "k1", T0 + 90_000)))
      repo.purgeOldLogs(T0 + AutoEntryRepository.LOG_TEXT_RETENTION_MS + 1)
      assertEquals(setOf(""), logTexts().toSet())
      assertEquals(1, txCount())                                       // 거래는 건드리지 않는다
  }
  @Test fun purge_deletesResolvedEntriesAfter90Days_keepsPendingAndTransactions() = runBlocking {
      handle(n(CARD, "가짜카드", "승인 4,500원\n가짜커피"))                // REGISTERED
      kimOut("k2")                                                      // PENDING
      repo.purgeOldLogs(T0 + TimeUnit.DAYS.toMillis(91))
      assertEquals(1, count("auto_entries")); assertEquals(1, pending().size); assertEquals(1, txCount())
  }
  @Test fun notCandidate_returnsWithoutWaitingForMutex() = runBlocking {
      val gate = CompletableDeferred<Unit>()
      val exactCalls = AtomicInteger()
      repo = AutoEntryRepository(db, { exactCalls.incrementAndGet(); gate.await(); watched }, { it in watched })
      val held = async(Dispatchers.Default) { handle(n(CARD, "가짜카드", "승인 4,500원\n가짜커피")) }
      while (exactCalls.get() == 0) delay(5)                                  // 뮤텍스를 쥔 채 멈춰 있다
      assertEquals(HandleOutcome.NOT_WATCHED,
          withTimeout(1_000) { handle(n("com.other.app", "가짜", "승인 4,500원\n가짜커피")) })
      assertEquals(1, exactCalls.get())
      gate.complete(Unit); assertEquals(HandleOutcome.REGISTERED, held.await())
  }
  ```
  `WatchedAppsTest`에 추가:
  ```kotlin
  @Test fun filterInstalled_dropsUninstalledPayApp() =
      assertEquals(setOf("a"), WatchedApps.filterInstalled(setOf("a", "com.kakaopay.app"), false) { it == "a" })
  @Test fun filterInstalled_allUninstalled_isEmpty() =
      assertEquals(emptySet<String>(), WatchedApps.filterInstalled(setOf("x", "y"), false) { false })
  @Test fun filterInstalled_keepsShellOnlyWhenDebuggable() {
      assertEquals(setOf(WatchedApps.SHELL_PACKAGE), WatchedApps.filterInstalled(setOf(WatchedApps.SHELL_PACKAGE), true) { false })
      assertEquals(emptySet<String>(), WatchedApps.filterInstalled(setOf(WatchedApps.SHELL_PACKAGE), false) { false })
  }
  @Test fun effective_intersectsStoredWithInstalled() {
      shadowOf(ctx.packageManager).installPackage(PackageInfo().apply { packageName = NotificationParsers.KBANK_PACKAGE })
      assertEquals(setOf(NotificationParsers.KBANK_PACKAGE),
          WatchedApps.effective(setOf(NotificationParsers.KBANK_PACKAGE, "viva.republica.toss"), ctx))
  }
  @Test fun isCandidate_storedSetWins_elseKnownFinanceApps() {
      assertTrue(WatchedApps.isCandidate("x", setOf("x"), false))
      assertFalse(WatchedApps.isCandidate(NotificationParsers.KBANK_PACKAGE, setOf("x"), false))
      assertTrue(WatchedApps.isCandidate(NotificationParsers.KBANK_PACKAGE, null, false))
      assertFalse(WatchedApps.isCandidate(WatchedApps.SHELL_PACKAGE, null, false))
      assertTrue(WatchedApps.isCandidate(WatchedApps.SHELL_PACKAGE, null, true))
  }
  ```

- [ ] **Step 2: 실패 확인** — `GRADLE :app:testDebugUnitTest --tests "*AutoEntryRepositoryTest" --tests "*WatchedAppsTest"` → 컴파일 실패.

- [ ] **Step 3: DAO 3개 메서드 추가** (Interfaces의 쿼리 그대로).

- [ ] **Step 4: `AutoEntryRepository`.**
  - `handleNotification`: 첫 줄 `if (!isCandidate(n.packageName)) return HandleOutcome.NOT_WATCHED`, 그 뒤 기존 `mutex.withLock { ... }`(안쪽의 정확한 검사 유지).
  - `process`: 로그를 `title = "", body = ""`로 넣는다. `sameKeyTexts.any { it in newText }` DUPLICATE 반환 **바로 다음 줄**에 `logDao.setText(logId, n.title.take(NotificationParsers.TITLE_CAP), body.take(NotificationParsers.BODY_CAP))`. 이 지점 이후의 모든 경로는 `auto_entries` 행을 만든다.
  - `purgeOldLogs`: `database.withTransaction` 안에서 `entryDao.deleteResolvedOlderThan(90일 컷)` → `logDao.clearStaleText(nowMs - LOG_TEXT_RETENTION_MS)` → `logDao.deleteOlderThan(90일 컷)`. 반환값은 지금처럼 지운 로그 수. 첫 실행에서 예전에 쌓인 원문도 여기서 비워진다.

- [ ] **Step 5: `WatchedApps`·`AppContainer`.** `isCandidate`: `stored != null`이면 `pkg in stored`, 아니면 `pkg in KnownApps.FINANCE_PACKAGES || (debuggable && pkg == SHELL_PACKAGE)`. `filterInstalled`: `isInstalled(it) || (debuggable && it == SHELL_PACKAGE)`. `effective`: `stored`가 null이면 `defaults(context)`, 아니면 `filterInstalled(stored, debuggable) { runCatching { pm.getPackageInfo(it, 0) }.isSuccess }`. `AppContainer`:
  ```kotlin
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val debuggable = appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
  // DataStore를 알림마다 다시 읽지 않는다. 읽기 실패는 "저장한 적 없음"으로 본다.
  private val storedWatched = userPreferencesRepository.watchedPackagesOrNull
      .catch { emit(null) }.shareIn(scope, SharingStarted.Eagerly, replay = 1)
  val autoEntryRepository = AutoEntryRepository(
      database,
      watchedPackages = { WatchedApps.effective(storedWatched.first(), appContext) },
      isCandidate = { WatchedApps.isCandidate(it, storedWatched.first(), debuggable) },
  )
  ```

- [ ] **Step 6: 백업 규칙.** `<application>`에 `android:dataExtractionRules="@xml/data_extraction_rules"`, `android:fullBackupContent="@xml/backup_rules"` 추가.
  ```xml
  <!-- data_extraction_rules.xml (API 31+). 기기 간 이전은 기본값(전부) 그대로. -->
  <data-extraction-rules>
      <cloud-backup disableIfNoEncryptionCapabilities="true">
          <include domain="database" path="." />
          <include domain="file" path="." />
          <include domain="sharedpref" path="." />
      </cloud-backup>
  </data-extraction-rules>
  <!-- backup_rules.xml (API 30 이하) -->
  <full-backup-content>
      <include domain="database" path="." requireFlags="clientSideEncryption" />
      <include domain="file" path="." />
      <include domain="sharedpref" path="." />
  </full-backup-content>
  ```

- [ ] **Step 7: 통과 확인** — Step 2 명령 PASS(기존 `purgeOldLogs_keeps90Days`, `sameKeyUpdatedText_within5Min_isDuplicate` 포함). `GRADLE :app:testDebugUnitTest :app:assembleDebug` 성공.

- [ ] **Step 8: 커밋** — 고친 파일 경로 나열, `fix: 알림 원문 보관 최소화, 감시 대상은 설치된 앱만, 알림당 검사 비용 절감`.

---

### Task 4: 기록하지 않은 알림 목록·되살리기 (M4) [Sonnet·기계적]

**Files:**
- Modify: `core/.../autoentry/AutoClassifier.kt:7` (`ReviewReason`에 `RESTORED` 추가)
- Modify: `app/.../data/repository/AutoEntryRepository.kt`
- Modify: `app/.../ui/autoentry/PendingReviewScreen.kt:65-70` (`reasonLabelRes`), `app/.../ui/autoentry/AutoEntrySettingsScreen.kt`, `app/src/main/res/values/strings_auto.xml`
- Test: `app/src/test/.../data/repository/AutoEntryRepositoryTest.kt`, `app/src/test/.../ui/autoentry/PendingLabelsTest.kt`

**Interfaces:**
- Consumes: Task 3의 `AutoEntryRepository`(생성자·뮤텍스).
- Produces:
  ```kotlin
  fun observeIgnored(): Flow<List<AutoEntryEntity>>     // status IGNORED, 최신순
  suspend fun restoreIgnored(entryId: Long)             // IGNORED → PENDING, reason = "RESTORED". 그 외 상태면 아무것도 안 함
  ```

- [ ] **Step 1: 실패하는 테스트 작성**
  ```kotlin
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
      // 다음 충전은 여전히 기록 안 함
      assertEquals(HandleOutcome.IGNORED,
          handle(n(KBANK, "출금 10,000원", "네이버페이 충전 | 가짜통장(1234)", "k9", T0 + 86_400_000)))
  }
  @Test fun restoreIgnored_onRegisteredEntry_doesNothing() = runBlocking {
      handle(n(CARD, "가짜카드", "승인 4,500원\n가짜커피"))
      val id = db.openHelper.readableDatabase.query("SELECT id FROM auto_entries").use { it.moveToFirst(); it.getLong(0) }
      repo.restoreIgnored(id)
      assertEquals(0, pending().size); assertEquals(1, txCount())
  }
  // PendingLabelsTest.reasonLabels 에 추가
  assertEquals(R.string.auto_reason_restored, reasonLabelRes("RESTORED"))
  ```

- [ ] **Step 2: 실패 확인** — `GRADLE :app:testDebugUnitTest --tests "*AutoEntryRepositoryTest" --tests "*PendingLabelsTest"` → 컴파일 실패.

- [ ] **Step 3: 구현.** `ReviewReason.RESTORED` 추가. `observeIgnored`는 `entryDao.observeByStatus(AutoEntryStatus.IGNORED)`. `restoreIgnored`는 `mutex.withLock { database.withTransaction { ... } }` 안에서 상태가 IGNORED일 때만 `entryDao.update(copy(status = PENDING, reason = ReviewReason.RESTORED.name))`. `reasonLabelRes`에 `"RESTORED" -> R.string.auto_reason_restored`.

- [ ] **Step 4: 문구** (`strings_auto.xml`):
  ```xml
  <string name="auto_ignored_title">기록하지 않은 알림</string>
  <string name="auto_ignored_desc">결제 앱 충전처럼 자동으로 건너뛴 알림이에요. 빠진 지출이 있으면 되살려서 기록하세요.</string>
  <string name="auto_ignored_restore">되살리기</string>
  <string name="auto_reason_restored">되살린 알림</string>
  ```

- [ ] **Step 5: 화면.** `AutoEntrySettingsScreen`의 "학습한 규칙" 섹션 아래에, `remember(repo) { repo.observeIgnored() }`가 비어 있지 않을 때만 섹션을 붙인다: `SectionHeader(auto_ignored_title)` + 설명 문구 + `items(ignored, key = { "ignored_${it.id}" })`. 각 행은 `PendingReviewScreen`의 행과 같은 구성(이름 없으면 `auto_pending_no_name`, 금액 `formatWon()`, 날짜 `formatShortDayLabel()`) + `TextButton(auto_ignored_restore) { scope.launch { repo.restoreIgnored(it.id) } }`. 새 화면·새 `SettingsPane`은 만들지 않는다.

- [ ] **Step 6: 통과 확인** — Step 2 명령 PASS, `GRADLE :core:test :app:testDebugUnitTest :app:assembleDebug` 성공.

- [ ] **Step 7: 커밋** — `feat: 기록하지 않은 알림을 자동 기록 설정에서 보고 되살리기`.

---

### Task 5: 앱 잠금 — 나갔다 오면 다시 잠김(M5) [Opus·까다로움]

**Files:**
- Modify: `app/.../security/AppLockGate.kt`
- Test: `app/src/test/.../security/AppLockGateTest.kt` (신규, 러너 없음)

**Interfaces:**
- Consumes: 없음
- Produces:
  ```kotlin
  internal enum class LockGateState { LOADING, LOCKED, OPEN }
  /** lockEnabled == null 은 설정을 아직 못 읽은 상태. */
  internal fun lockGateState(lockEnabled: Boolean?, unlocked: Boolean, canAuthenticate: Boolean): LockGateState
  /** 잠금 화면 뒤에 앱 내용을 컴포지션에 둘지. 한 번도 안 풀렸으면 두지 않는다. */
  internal fun shouldComposeContent(state: LockGateState, everUnlocked: Boolean): Boolean
  ```

- [ ] **Step 1: 실패하는 테스트 작성**
  ```kotlin
  @Test fun lockGateState_loadingUntilPreferenceKnown() {
      assertEquals(LockGateState.LOADING, lockGateState(null, unlocked = false, canAuthenticate = true))
      assertEquals(LockGateState.LOADING, lockGateState(null, unlocked = true, canAuthenticate = false))
  }
  @Test fun lockGateState_lockedOnlyWhenEnabledAndAuthAvailable() {
      assertEquals(LockGateState.LOCKED, lockGateState(true, false, true))
      assertEquals(LockGateState.OPEN, lockGateState(true, true, true))
      assertEquals(LockGateState.OPEN, lockGateState(true, false, false))    // 인증 수단 없으면 잠그지 않는다(기존 동작)
      assertEquals(LockGateState.OPEN, lockGateState(false, false, true))
  }
  @Test fun shouldComposeContent_keepsContentWhileRelocked() {
      assertTrue(shouldComposeContent(LockGateState.OPEN, everUnlocked = false))
      assertFalse(shouldComposeContent(LockGateState.LOCKED, everUnlocked = false))   // 첫 잠금: 내용 없음
      assertTrue(shouldComposeContent(LockGateState.LOCKED, everUnlocked = true))     // 다시 잠김: 상태 보존
      assertFalse(shouldComposeContent(LockGateState.LOADING, everUnlocked = true))
  }
  ```

- [ ] **Step 2: 실패 확인** — `GRADLE :app:testDebugUnitTest --tests "*AppLockGateTest"` → 컴파일 실패.

- [ ] **Step 3: 순수 함수 2개 구현.**

- [ ] **Step 4: `AppLockGate` 재구성.** 이 순서로 만든다.
  1. `appLockEnabled.collectAsState(initial = null)`(타입 `Boolean?`). `unlocked`, `everUnlocked`는 `remember { mutableStateOf(false) }`. 인증 성공 콜백과 "FragmentActivity 없음" 폴백에서 둘 다 true.
  2. `var started by remember { mutableStateOf(false) }`와 `LifecycleStartEffect(Unit) { started = true; onStopOrDispose { started = false; unlocked = false; focusManager.clearFocus() } }` — 화면이 멈추면 다시 잠그고, 키보드가 잠금 화면 위에 남지 않게 한다.
  3. `LOADING`이면 배경색만 칠한 빈 `Box`를 그리고 끝.
  4. `shouldComposeContent(...)`면 `Box(if (state == LOCKED) Modifier.clearAndSetSemantics { } else Modifier) { content() }` — 호출 위치가 같아야 상태가 유지된다. 잠긴 동안 TalkBack이 뒤 내용을 읽지 못한다.
  5. `LOCKED`면 그 위에 `Surface(Modifier.fillMaxSize(), color = background)`로 기존 잠금 화면(아이콘+버튼)을 그린다(`Surface`가 뒤로 가는 터치를 막는다). `BackHandler(enabled = true) { activity?.moveTaskToBack(true) }`를 `content()` **뒤에** 둬서 잠긴 동안 뒤로 가기가 뒤 화면을 조작하지 못하게 한다.
  6. 자동 프롬프트: 기존 `LaunchedEffect(Unit)`을 `LaunchedEffect(state, started) { if (state == LockGateState.LOCKED && started) launchPrompt() }`로 바꾼다. 멈춘 상태(ON_STOP 뒤)에서는 `BiometricPrompt.authenticate`가 무시되므로 `started`를 함께 봐야 돌아온 순간에 뜬다. 첫 실행·복귀·설정에서 잠금을 켠 직후가 이 한 줄로 처리된다. 프롬프트를 취소하면 지금처럼 버튼으로 다시 띄운다.

- [ ] **Step 5: 통과 확인** — Step 2 명령 PASS, `GRADLE :app:assembleDebug` 성공. 동작 확인은 Task 7 Step 5.

- [ ] **Step 6: 커밋** — `fix: 앱을 나갔다 오면 다시 잠그고, 잠금 설정을 읽기 전에는 내용을 그리지 않음`.

---

### Task 6: 가계부 화살표 UX + release 최적화 (A1–A4) [Opus·까다로움]

**Files:**
- Create: `app/.../ui/feed/FeedLogic.kt`
- Modify: `app/.../ui/feed/FeedScreen.kt`
- Modify: `app/build.gradle.kts:20-28`, `app/proguard-rules.pro`
- Test: `app/src/test/.../ui/feed/FeedLogicTest.kt` (신규, 러너 없음)

**Interfaces:**
- Consumes: Task 2의 `BudgetViewModel.today: StateFlow<LocalDate>`, `HomeSummary`의 `val` 합계.
- Produces:
  ```kotlin
  // FeedLogic.kt
  data class FeedDay(
      val epochDay: Long,
      val rows: List<TransactionWithCategoryRow>,
      val incomeMinor: Long, val expenseMinor: Long, val savingsMinor: Long,
  ) { fun totalOf(kind: CategoryKind): Long }
  /** 날짜 내림차순. 하루 안의 행 순서는 입력 순서 그대로. */
  fun groupFeedDays(rows: List<TransactionWithCategoryRow>): List<FeedDay>
  /** 새 기간이 더 미래면 +1(오른쪽에서 들어옴), 과거면 -1, 처음이거나 같으면 0(애니메이션 없음). */
  fun slideDirection(previousStartEpochDay: Long?, newStartEpochDay: Long?): Int
  ```

- [ ] **Step 1: 실패하는 테스트 작성** — `row(id, day, amount, kind)`는 `TransactionWithCategoryRow`를 만드는 도우미.
  ```kotlin
  @Test fun groupFeedDays_sortsDaysDescending_andKeepsRowOrder() {
      val days = groupFeedDays(listOf(row(3, 100, 1_000, "EXPENSE"), row(2, 101, 2_000, "EXPENSE"), row(1, 100, 500, "INCOME")))
      assertEquals(listOf(101L, 100L), days.map { it.epochDay })
      assertEquals(listOf(3L, 1L), days[1].rows.map { it.id })
  }
  @Test fun groupFeedDays_computesPerKindTotalsOnce() {
      val day = groupFeedDays(listOf(row(1, 100, 1_000, "EXPENSE"), row(2, 100, 2_500, "EXPENSE"), row(3, 100, 9_000, "INCOME"))).single()
      assertEquals(3_500L, day.expenseMinor); assertEquals(9_000L, day.incomeMinor); assertEquals(0L, day.savingsMinor)
      assertEquals(3_500L, day.totalOf(CategoryKind.EXPENSE))
  }
  @Test fun groupFeedDays_empty() = assertEquals(emptyList<FeedDay>(), groupFeedDays(emptyList()))
  @Test fun slideDirection_olderPeriodEntersFromLeft() = assertEquals(-1, slideDirection(200, 170))
  @Test fun slideDirection_newerPeriodEntersFromRight() = assertEquals(1, slideDirection(170, 200))
  @Test fun slideDirection_jumpBackToTodayAcrossSeveralPeriods() = assertEquals(1, slideDirection(80, 200))
  @Test fun slideDirection_firstLoadOrSamePeriod_isZero() {
      assertEquals(0, slideDirection(null, 200)); assertEquals(0, slideDirection(200, null)); assertEquals(0, slideDirection(200, 200))
  }
  ```

- [ ] **Step 2: 실패 확인** — `GRADLE :app:testDebugUnitTest --tests "*FeedLogicTest"` → 컴파일 실패.

- [ ] **Step 3: `FeedLogic.kt` 구현.**

- [ ] **Step 4: FeedScreen 렌더링(A3).**
  - `today`는 `budgetViewModel.today.collectAsStateWithLifecycle()`.
  - `val days = remember(filteredRows) { groupFeedDays(filteredRows) }`를 `LazyColumn` 밖에 두고, 날짜 헤더 합계는 `day.totalOf(kind)`.
  - 필터 요약의 합계는 `remember(filteredRows) { filteredRows.sumOf { it.amountMinor } }`.
  - 모든 `item`/`items`에 `contentType`: `"header"`, `"pendingBanner"`, `"hero"`, `"controls"`, `"empty"`, `"filterSummary"`, `"dayHeader"`, `"tx"`.

- [ ] **Step 5: 전환 애니메이션(A1).** 상수 `PERIOD_ENTER_MS = 180`, 슬라이드 `24.dp`. FeedScreen 안:
  ```kotlin
  private class PeriodEnter(val direction: Int) { val progress = Animatable(if (direction == 0) 1f else 0f) }

  val periodStart = currentSummary?.period?.startInclusive?.toEpochDay()
  val previousStart = remember { arrayOfNulls<Long>(1) }          // 재구성을 일으키지 않는 기억
  val enter = remember(periodStart) {
      PeriodEnter(slideDirection(previousStart[0], periodStart)).also { if (periodStart != null) previousStart[0] = periodStart }
  }
  LaunchedEffect(enter) { enter.progress.animateTo(1f, tween(PERIOD_ENTER_MS)) }
  val slidePx = with(LocalDensity.current) { 24.dp.toPx() }
  val periodEnter = Modifier.graphicsLayer {                       // 그리기 단계에서만 읽는다 → 프레임마다 재구성 없음
      val p = enter.progress.value
      alpha = p; translationX = (1f - p) * enter.direction * slidePx
  }
  ```
  `periodEnter`를 붙이는 곳: 히어로의 기간 의존 부분(왼쪽 위 슬롯·기간 범위·달 라벨·남은 금액·예산 막대 — 카드 배경과 화살표 버튼에는 붙이지 않는다), `KindInlineSummary`, `"empty"`/`"filterSummary"`/`"dayHeader"`/`"tx"` 항목. 검색창·필터 칩에는 붙이지 않는다. 키가 바뀔 때 `Animatable`을 새로 만들기 때문에 연타해도 마지막 기간이 0→1로 끝난다.

- [ ] **Step 6: 레이아웃 고정(A2)** — `FeedHeroCard`.
  - 화면에 보이는 부분(카운트다운 ↔ "오늘로", 예산 막대)은 `periodOffset == 0` 대신 `val isCurrent = period.contains(today)`로 가른다(오프셋은 탭 즉시 바뀌고 데이터는 조금 뒤에 와서 한두 프레임 어긋난다). › 버튼의 `enabled`만 `periodOffset < 0`을 유지한다.
  - 왼쪽 위 슬롯: `Box(Modifier.heightIn(min = 24.dp), contentAlignment = Alignment.CenterStart)` 안에 카운트다운 또는 알약.
  - 달 라벨: `archiveMonthTitle(start, today)`(`ui/archive/ArchiveListScreen.kt`의 internal 함수)로 만들고 하드코딩된 `"${...}월"`을 없앤다. `Box(contentAlignment = Alignment.Center)` 안에 투명한 틀 글자 `archiveMonthTitle(start.withMonth(12), today)`(`Modifier.alpha(0f).clearAndSetSemantics { }`)와 실제 라벨을 겹쳐 폭을 "12월" 기준으로 고정한다.
  - ‹ › : `IconButton(Modifier.size(48.dp))`, 아이콘 20dp 유지. 화살표 `Row`에 `Modifier.offset(x = 14.dp)`를 줘 글리프의 오른쪽 끝이 카드 안쪽 여백에 맞게 하고, 상단 `Row`를 `Alignment.CenterVertically`로, 그 아래 `Spacer(Space.sm)`를 없애 카드 높이를 지금과 비슷하게 유지한다.
  - 히어로 `Column`에 `Modifier.animateContentSize(tween(PERIOD_ENTER_MS))`(패딩 앞).

- [ ] **Step 7: R8(A4)** — `app/build.gradle.kts`의 release:
  ```kotlin
  isMinifyEnabled = true
  isShrinkResources = true
  // 설치 가능한 release APK를 만들기 위해 debug 키로 서명한다(스토어 배포 없음).
  signingConfig = signingConfigs.getByName("debug")
  ```
  `app/proguard-rules.pro`에 추가:
  ```
  # DataStore Preferences의 protobuf-lite는 필드를 이름으로 찾는다.
  -keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite { <fields>; }
  ```

- [ ] **Step 8: 통과 확인** — `GRADLE :app:testDebugUnitTest :core:test :app:assembleDebug :app:assembleRelease` 전부 성공. `ls app/build/outputs/apk/release/app-release.apk`가 있다(unsigned 아님).

- [ ] **Step 9: 커밋** — `git add` 위 5개 파일(+테스트), `feat: 가계부 달 이동에 전환 애니메이션과 레이아웃 고정, release 빌드 R8 활성화`.

---

### Task 7: 에뮬레이터 확인 (AVD `test35`, 가짜 데이터) [Opus·까다로움]

제품 코드를 고치지 않는다. 문제가 나오면 해당 태스크로 돌아가 고치고 그 태스크 커밋에 이어 `fix:` 커밋을 따로 만든다. 산출물(녹화·캡처)은 `build/emulator-check/`(gitignore됨)에 두고 커밋하지 않는다. 아래에서 `ADB`는 `~/Library/Android/sdk/platform-tools/adb -s emulator-5580`, `PKG`는 `com.householdbudget.app`.

**Files:** 없음(확인만).

**Interfaces:**
- Consumes: Task 1–6 전부. `app/build/outputs/apk/debug/app-debug.apk`, `app/build/outputs/apk/release/app-release.apk`.

- [ ] **Step 1: 에뮬레이터 기동.** `~/Library/Android/sdk/emulator/emulator -avd test35 -port 5580 -no-snapshot-save`(백그라운드). `ADB wait-for-device`, `ADB shell getprop sys.boot_completed`가 `1`. `adb devices`에 실제 폰이 보여도 `-s emulator-5580` 없이 명령을 보내지 않는다.

- [ ] **Step 2: debug 설치·가짜 데이터.** `ADB uninstall PKG`(있으면) → `ADB install app/build/outputs/apk/debug/app-debug.apk` → 한 번 실행해 DB를 만든 뒤 `ADB shell am force-stop PKG`. `ADB shell "run-as PKG sqlite3 databases/household_budget.db \"...\""`로 넣는다(`sqlite3`가 없으면 `ADB root` 후 `/data/data/PKG/databases/`에서). 오늘 epoch day는 SQL에서 `CAST(strftime('%s','now','+9 hours')/86400 AS INTEGER)`.
  - 거래: 지출 소분류 id 하나와 수입 소분류 id 하나를 `SELECT id FROM categories WHERE parent_id IS NOT NULL AND kind = ...`로 얻어, 오늘 기준 0·1·3·30·35·40·65·70일 전에 가짜 금액(예: 4,500 / 12,000 / 300,000)과 메모 `가짜커피` 등으로 12건 이상. 메모 하나는 `=1+1`.
  - 지난 기록: 앱을 열어 ‹ 한 번 눌러 직전 기간 범위(예: `9.23 ~ 10.22`)를 읽고, 시작일과 끝 다음 날의 epoch day를 `python3 -c "from datetime import date; print((date(Y,M,D)-date(1970,1,1)).days)"`로 구해 `archived_periods`에 합계 **0/0/0**으로 한 줄 넣는다.

- [ ] **Step 3: 편집 연타(H1).** + 버튼 → 키패드로 금액 입력(좌표는 `ADB shell uiautomator dump`로 찾는다) → 저장 버튼 좌표 X Y에 `ADB shell "input tap X Y & input tap X Y"`. 기대: `SELECT COUNT(*) FROM transactions`가 정확히 1 늘고, 화면은 가계부 탭(빈 화면 아님, 덤프에 하단 탭이 있다). 기존 거래를 열어 삭제 확인 버튼도 같은 방식으로 연타 → 1건만 줄고 탭 화면.

- [ ] **Step 4: 편집 열기(M1).** 가짜 거래 하나를 열었다가 아무것도 안 바꾸고 뒤로 → "변경 사항 버리기" 다이얼로그가 뜨지 않고, DB의 그 행 `category_id`가 그대로다. 3건 반복.

- [ ] **Step 5: 앱 잠금(M5).** `ADB shell locksettings set-pin 1234` → 설정에서 앱 잠금 켜기 → + 로 금액 `7777` 입력한 상태에서 `ADB shell input keyevent 3`(홈) → `ADB shell monkey -p PKG 1`로 복귀. 기대: 잠금 화면 + 인증 프롬프트가 뜬다(캡처로 뒤 내용이 안 보이는지 확인). `ADB shell input text 1234; ADB shell input keyevent 66` → 풀리고 **금액 7777이 입력된 편집 화면 그대로**. 앱을 완전히 종료 후 재실행 → 내용이 한 프레임도 보이지 않고 잠금 화면부터(스크린레코드 첫 프레임들 확인). 끝나면 앱 잠금 끄고 `ADB shell locksettings clear --old 1234`.

- [ ] **Step 6: 지난 기록 합계(M3).** 설정 → 지난 기록: Step 2에서 0/0/0으로 넣은 달이 그 기간 가짜 거래의 실제 합계로 보인다. 가계부에서 ‹ 로 그 달로 가 거래 하나의 금액을 바꿔 저장 → 지난 기록 목록·상세의 지출 합계가 바뀐 금액만큼 달라진다.

- [ ] **Step 7: 화살표 전환(A1–A3) — release 빌드로.** `ADB install -r app/build/outputs/apk/release/app-release.apk`(데이터 유지). `ADB shell dumpsys gfxinfo PKG reset` → `ADB shell screenrecord --time-limit 20 /sdcard/feed-arrows.mp4 &` → ‹ ‹ › › 를 0.7초 간격으로, 이어서 ‹ 5연타 후 "오늘로" → `ADB pull /sdcard/feed-arrows.mp4 build/emulator-check/`. 확인:
  - 녹화(가능하면 `ffmpeg -i … -vf fps=30 build/emulator-check/f_%03d.png`로 프레임을 뽑아 탭 직후 6장을 본다): 새 달 내용이 옆에서 살짝 밀려 들어오며 밝아진다. ‹ 는 왼쪽에서, › 와 "오늘로"는 오른쪽에서.
  - 오프셋 0과 −1에서 각각 `uiautomator dump`: ‹ › 버튼과 기간 범위 글자의 bounds가 **같다**. ‹ › bounds는 48dp 이상.
  - 5연타 뒤: 달 라벨이 5달 전이고 내용이 흐릿하게 멈춰 있지 않다.
  - `ADB shell dumpsys gfxinfo PKG`의 Janky frames·90th/95th percentile을 기록한다(에뮬레이터 수치라 참고용).

- [ ] **Step 8: release 스모크(R8).** 가계부·달력·통계·설정 탭, 지난 기록, 예산 설정, 자동 기록 설정(기록하지 않은 알림 섹션 포함), 거래 추가·수정·삭제, CSV 내보내기(공유 시트가 뜨면 성공, 취소)까지 한 번씩 연다. `ADB logcat -d -s AndroidRuntime:E`에 `PKG` 크래시가 없다. 설정을 하나 바꾸고(월급일) 앱을 재시작해 유지되는지 본다(DataStore가 R8 뒤에도 동작).

- [ ] **Step 9: 정리·보고.** `ADB emu kill`. 결과(각 Step 통과/실패, gfxinfo 수치, 녹화 경로)를 보고한다. 커밋 없음.

---

## Self-Review 결과

- **스펙 대응:** A1·A2 → Task 6 Step 5–6, A3 → Task 2(HomeSummary 1회 계산)·Task 6 Step 4, A4 → Task 6 Step 7. H1·M1 → Task 1. H2·M4(교집합)·M6 → Task 3. M4(무시 목록) → Task 4. M3·자정 넘김·CSV 기간·Flow remember·CSV 수식 주입 → Task 2. M5 → Task 5. 에뮬레이터 제약 → Task 7. 제외 항목(M2, 환불)은 건드리지 않는다.
- **타입 일치:** `BudgetViewModel.today`/`HomeSummary` val(Task 2 → 6), `AutoEntryRepository` 생성자(Task 3 → 4), `ReviewReason.RESTORED`/`"RESTORED"`(Task 4 안에서 일치), `archiveMonthTitle(start, today)`는 기존 internal 함수.
- **남는 한계(의도한 것):** 앱을 켠 채 자정을 넘기면 다음 복귀까지 "오늘"이 하루 늦다. 90일 지난 자동 등록 거래의 카테고리를 고쳐도 규칙 학습이 일어나지 않는다(연결 행이 정리됨). 저장된 알림 원문으로 파서를 나중에 점검하는 일은 24시간 안에서만 가능하다. API 28 이하에서 기기 비밀번호 화면으로 인증하면 돌아올 때 프롬프트가 한 번 더 뜰 수 있다(대상 기기는 Android 13+).
