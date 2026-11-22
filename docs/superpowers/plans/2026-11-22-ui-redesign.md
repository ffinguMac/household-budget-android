# UI 리디자인 (라이트 블루 / 다크 블루) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 승인된 감사 결과대로 테마 토큰(라이트 블루 기본 + 다크 블루)을 바꾸고, 홈·거래 추가·확인 필요·통계·달력·예산 설정 화면과 감사에서 나온 결함을 고친다.

**Architecture:** 색·글자·모양은 전부 Material3 `ColorScheme`/`Typography`/`Shapes` 슬롯에 매핑해서 화면 코드가 `MaterialTheme`만 읽게 한다(새 CompositionLocal 없음). 테마 선택은 DataStore의 Boolean 하나(`dark_theme`)다. 새 계산(남은 예산·하루 여유, 월급 주기 달력 범위, 확인 필요 순서)은 Compose 밖의 순수 함수로 두고 단위 테스트한다. 화면은 기존 ViewModel·저장소·컴포저블을 고쳐 쓰고 새 화면 파일을 만들지 않는다.

**Tech Stack:** Kotlin, Jetpack Compose Material3(BOM 2024.10.01), Room, DataStore, JUnit4 + Robolectric. 새 의존성 없음(글꼴 파일 2개 + 라이선스 1개만 추가).

**Spec:** `/Users/ffingumac/orca/household-budget-android/.superpowers/design-audit-2026-11-22.md` (git 제외 파일이라 워크트리에는 없다. 이 절대 경로로 읽는다.) 파일 **맨 끝의 "결정" 두 단락이 앞의 민트/다크 토큰을 덮어쓴다.** 시각 목표는 `/private/tmp/claude-501/-Users-ffingumac-orca-household-budget-android/9ded73d8-a081-4dcb-97fb-81c6d0096b39/scratchpad/design/project/New{Home,Add,Pending,PendingDone,Stats,Calendar,Budget}.dc.html` (390×844 정적 HTML). 스펙 표와 목업이 다르면 목업을 따른다.

## Global Constraints

- 브랜치 `feat/ui-redesign`에서 작업한다(`master`에 직접 커밋 금지).
- 새 의존성 금지. 네트워크 호출 추가 금지(글꼴 내려받기는 Task 1의 일회성 작업).
- 휴대폰·에뮬레이터를 조작하지 않는다. APK를 복사하지 않는다. 검증은 빌드 + 단위 테스트 + 코드 리뷰다.
- 저장소의 `.xlsx/.xls/.numbers/.csv`는 열지도 `git add` 하지도 않는다. `git add`는 파일 경로를 지정한다(`-A`, `.` 금지). `core/bin/`은 건드리지 않는다.
- DB 스키마·마이그레이션 변경 없음.
- UI 문구는 한국어 해요체, `app/src/main/res/values/strings_*.xml`에 둔다. 금액에는 항상 `원`(차트·달력 칸은 `만`)을 붙인다.
- 화면 코드의 색은 `MaterialTheme.colorScheme`에서만 읽는다(`ui/theme/` 밖 `Color(0x…)` 금지, 카테고리 색 `categoryStyleOf` 제외). 역할 매핑은 Task 1 표.
- 글자는 여섯 슬롯만 쓴다: `displaySmall`(32/700), `titleLarge`(20/700), `titleMedium`(16/700), `bodyLarge`(16/400), `labelLarge`(13/700), `bodySmall`(13/400). 화면 코드에 `fontWeight =`, `fontSize =`를 쓰지 않는다. 700은 값·행동에만, 라벨·섹션 제목은 `bodySmall` + `onSurfaceVariant`.
- 간격은 `Space`(4·8·12·16·20·24·32), 모서리는 `shapes.small`(12)·`shapes.large`(20)·`shapes.extraLarge`(28, 시트)·`CircleShape`만.
- 지출 금액은 `onSurface` + "−", 수입은 `primary` + "+", 저축은 `onSurface` + "↑". `error`는 예산 초과·삭제·오류에만.
- 손대는 터치 대상은 48dp 이상.
- 빌드·테스트(저장소에 `./gradlew` 없음). 아래에서 `GRADLE`은 이 줄 전체다:
  `export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=~/Library/Android/sdk; ~/.gradle/wrapper/dists/gradle-8.10.2-bin/a04bxjujx95o3nb99gddekhwo/gradle-8.10.2/bin/gradle`
- 모든 태스크의 마지막 두 단계는 같다: `GRADLE :app:assembleDebug` → `BUILD SUCCESSFUL`, `GRADLE test` → `BUILD SUCCESSFUL`. 그 뒤 태스크당 커밋 하나(한국어 `feat:`/`fix:`), 마지막 줄 `Co-Authored-By: Claude <실제 모델 이름> <noreply@anthropic.com>`.
- 모델 배정은 태스크 제목의 `[Sonnet]` / `[Opus]`.
- 만들지 않는다: 프레스 스케일·숫자 롤링·저장 후 행 깜빡임 같은 모션(스펙 4.6–4.7), 월급 후 기간 마감 카드, 자동 기록 꺼짐 빈 상태, 주별 차트 평균선, 달력 빈 날의 "거래 추가" 버튼, 확인 필요 카드의 알림 원문 줄.

## Review Focus

1. **예산 미설정·0원·초과.** 홈 히어로가 0으로 나누거나 음수 "하루 여유"를 보이면 안 된다 → Task 4 `budgetStatus` 테스트.
2. **한 번의 선택이 같은 가맹점 여러 건을 함께 정리하거나, 전부 "나중에"를 누른 경우.** 진행 수가 어긋나거나 화면이 멈추면 안 된다 → Task 7 `triageState`·`resolvePending` 테스트.
3. **6주가 필요한 월급 주기, 월급일 1–5일(앞 달로 당겨짐), 일요일에 끝나는 주기.** 달력 칸이 빠지거나 밀리면 안 된다 → Task 5 `calendarGridDays`·`periodAtOffset` 테스트.
4. **메모가 빈 거래.** 행 제목이 비면 안 된다 → Task 3 `transactionRowTexts` 테스트.
5. **7–9자리 금액과 큰 글꼴 배율.** 금액 `Text`에 `maxLines`/`Ellipsis`를 걸지 않는다(가맹점·메모만 말줄임) → 자동 테스트 없음. Task 4·8·10 리뷰 항목.
6. **다크 블루 사용자의 콜드 스타트와 시스템 다크모드.** 시스템 설정과 무관하게 저장된 테마가 첫 컴포지션부터 적용된다 → 자동 테스트 없음. Task 2 리뷰 항목.

---

### Task 1 [Opus]: 테마 토큰 — 색 2종, 글자, 모양, 간격, Pretendard

**Files:**
- Create: `app/src/main/res/font/pretendard_regular.otf`, `app/src/main/res/font/pretendard_bold.otf`, `app/src/main/assets/licenses/pretendard-OFL.txt`
- Modify: `app/src/main/java/com/householdbudget/app/ui/theme/{Color,Theme,Type,Shape,Spacing,KindStyle}.kt`, `app/src/main/java/com/householdbudget/app/MainActivity.kt`(호출만), `ui/` 아래 `Space.xxs`·`shapes.extraLarge` 사용처

**Interfaces:**
- Produces: `HouseholdBudgetTheme(dark: Boolean = false, content: @Composable () -> Unit)`; `kindAccent(kind)`(수입 `primary`, 지출·저축 `onSurface`); `kindDotColor(kind: CategoryKind): Color?`(수입 `primary`, 저축 `tertiary`, 지출 `null`).

- [ ] **Step 1: 글꼴 내려받기.** Pretendard v1.3.9 (github.com/orioncactus/pretendard, SIL OFL 1.1). 정적 OTF 두 개만:

```bash
cd app/src/main && mkdir -p res/font assets/licenses
B=https://github.com/orioncactus/pretendard/raw/v1.3.9
curl -fL -o res/font/pretendard_regular.otf $B/packages/pretendard/dist/public/static/Pretendard-Regular.otf
curl -fL -o res/font/pretendard_bold.otf    $B/packages/pretendard/dist/public/static/Pretendard-Bold.otf
curl -fL -o assets/licenses/pretendard-OFL.txt $B/LICENSE
```

Expected: 각 OTF 약 1.57MB, 라이선스 약 4KB.

- [ ] **Step 2: `Color.kt`를 두 팔레트로 바꾼다.** 기존 상수(Toss·Dark·Midnight·Mint 등)는 전부 지운다. `ui/theme/` 밖에서 쓰는 곳이 없는지 `grep -rn "Midnight\|Mint[A-Z]\|ExpenseRed\|SavingsBlue\|Toss[A-Z]\|Gray[0-9]" app/src/main/java`로 확인한다.

| 역할 → `ColorScheme` 슬롯 | 라이트 블루 | 다크 블루 |
|---|---|---|
| `background`, `surfaceContainerLowest` | `#F6F7F9` | `#0E1014` |
| `surface`, `surfaceContainerLow`, `surfaceContainer`, `inverseOnSurface` | `#FFFFFF` | `#16191F` |
| `surfaceVariant`, `surfaceContainerHigh`, `surfaceContainerHighest`, `tertiaryContainer`, `errorContainer` | `#EEF0F3` | `#23272F` |
| `outlineVariant` (divider, 카드 1dp 테두리) | `#E5E7EB` | `#262A33` |
| `outline` | `#D1D5DB` | `#3A404C` |
| `onBackground`, `onSurface`, `inverseSurface` | `#111827` | `#F3F4F6` |
| `onSurfaceVariant` | `#6B7280` | `#9BA3AF` |
| `primary`, `secondary`, `surfaceTint` | `#2563EB` | `#6E9BFF` |
| `onPrimary`, `onSecondary` | `#FFFFFF` | `#0E1014` |
| `primaryContainer`, `secondaryContainer` | `#EAF1FE` | `#1A2540` |
| `onPrimaryContainer`, `onSecondaryContainer` | `#1D4ED8` | `#A9C2FF` |
| `tertiary`, `onTertiaryContainer` (저축) | `#0E7490` | `#22D3EE` |
| `error`, `onErrorContainer` | `#DC2626` | `#F87171` |
| `onTertiary`, `onError` | `#FFFFFF` | `#0E1014` |
| `inversePrimary` | `#6E9BFF` | `#2563EB` |
| `scrim` | `#000000` | `#000000` |

- [ ] **Step 3: `Theme.kt`** — `lightColorScheme(...)`·`darkColorScheme(...)` 두 개를 위 표로 만들고 `HouseholdBudgetTheme(dark: Boolean = false, content)`가 고른다. `isSystemInDarkTheme()`은 쓰지 않는다. `MainActivity`의 호출은 인자 없이 그대로 컴파일된다.

- [ ] **Step 4: `Type.kt`** — `val Pretendard = FontFamily(Font(R.font.pretendard_regular, FontWeight.Normal), Font(R.font.pretendard_bold, FontWeight.Bold))`. 네 가지 기본 스타일을 만들고 15개 슬롯을 전부 채운다. 모든 스타일에 `fontFamily = Pretendard`, `fontFeatureSettings = "tnum"`.

| 슬롯 | 크기/줄 | 굵기 | 자간 |
|---|---|---|---|
| `displayLarge/Medium/Small`, `headlineLarge/Medium` | 32/40sp | Bold | `(-0.02).em` |
| `headlineSmall`, `titleLarge` | 20/28sp | Bold | `(-0.01).em` |
| `titleMedium` | 16/24sp | Bold | 0 |
| `bodyLarge` | 16/24sp | Normal | 0 |
| `titleSmall`, `labelLarge`, `labelMedium` | 13/18sp | Bold | 0 |
| `bodyMedium`, `bodySmall`, `labelSmall` | 13/18sp | Normal | 0 |

- [ ] **Step 5: `Shape.kt`** — `extraSmall = small = medium = 12.dp`, `large = 20.dp`, `extraLarge = 28.dp`. 카드에 쓰던 `MaterialTheme.shapes.extraLarge`(18곳)는 전부 `MaterialTheme.shapes.large`로 바꾼다(시트는 M3 기본값이 `extraLarge`라 손대지 않는다).

- [ ] **Step 6: `Spacing.kt`** — `xxs` 삭제, 사용처 6곳은 `Space.xs`로. 주석의 "토스풍" 삭제.

- [ ] **Step 7: `KindStyle.kt`** — `kindAccent`를 위 Interfaces대로 바꾸고 `kindDotColor` 추가. `heroGradient()`는 `SolidColor(colorScheme.surface)`, `onHeroColor()`는 `onSurface`, `onHeroMutedColor()`는 `onSurfaceVariant`를 돌려주게만 고친다(Task 4에서 삭제). 파일 머리 주석을 새 규칙으로 고친다.

- [ ] **Step 8: 빌드·테스트·커밋** (Global Constraints). 커밋: `feat: 라이트 블루·다크 블루 테마 토큰과 Pretendard 글꼴 적용`

### Task 2 [Sonnet]: 화면 테마 저장·전환과 시스템 바

**Files:**
- Modify: `app/src/main/java/com/householdbudget/app/data/preferences/UserPreferencesRepository.kt`, `app/src/main/java/com/householdbudget/app/MainActivity.kt`, `app/src/main/res/values/themes.xml`, `app/src/main/java/com/householdbudget/app/ui/settings/SettingsScreen.kt`, `app/src/main/res/values/strings_more.xml`

**Interfaces:**
- Consumes: `HouseholdBudgetTheme(dark)` (Task 1).
- Produces: `UserPreferencesRepository.darkTheme: Flow<Boolean>`(기본 `false`), `suspend fun setDarkTheme(value: Boolean)`. 키 `booleanPreferencesKey("dark_theme")`.

- [ ] **Step 1:** 저장소에 위 두 멤버를 `appLockEnabled`와 같은 모양으로 추가.
- [ ] **Step 2: `MainActivity.onCreate`** — `val initialDark = runBlocking { preferences.darkTheme.first() }`(첫 프레임부터 맞는 테마. 주석: `// shortcut: 첫 읽기를 막고 기다린다(수 ms). 느려지면 스플래시로 옮긴다`). `setContent` 안에서 `val dark by preferences.darkTheme.collectAsStateWithLifecycle(initialDark)`, `HouseholdBudgetTheme(dark = dark)`. `enableEdgeToEdge` 호출을 `private fun applySystemBars(dark: Boolean)`로 옮겨 `onCreate`에서 한 번, `LaunchedEffect(dark)`에서 다시 부른다: 다크면 `SystemBarStyle.dark(Color.TRANSPARENT)`, 라이트면 `SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)`를 상태바·내비바 둘 다에.
- [ ] **Step 3: `themes.xml`** — `windowBackground`/`statusBarColor`/`navigationBarColor` `#F6F7F9`, `windowLightStatusBar` `true`. 주석: 다크 블루 사용자는 Compose 첫 프레임 전까지 밝은 창이 잠깐 보인다.
- [ ] **Step 4: 설정 화면** — "기본" 묶음에 `SettingsActionRow`(아이콘 `Icons.Rounded.Palette`, 제목 `settings_theme_title` "화면 테마", 부제 = 현재 값). 누르면 리마인더 시간 시트와 같은 `ModalBottomSheet`에 두 줄: `settings_theme_light` "라이트 블루", `settings_theme_dark` "다크 블루". 각 줄은 `Modifier.selectable(role = Role.RadioButton)`, 선택된 줄 오른쪽에 `Icons.Rounded.Check`(`primary`). 누르면 `scope.launch { prefs.setDarkTheme(…) }` 후 시트를 닫는다.
- [ ] **Step 5: 리뷰 항목** — 코드 어디에도 `isSystemInDarkTheme`이 없다. `values-night/`를 만들지 않았다.
- [ ] **Step 6: 빌드·테스트·커밋.** `feat: 설정에서 화면 테마(라이트 블루·다크 블루) 고르기`

### Task 3 [Sonnet]: 공용 부품 — 상단 바 하나, 가맹점 우선 거래 행, 밀려난 화면에서 하단 바 숨김

**Files:**
- Modify: `ui/components/BudgetComponents.kt`, `ui/components/TransactionRow.kt`, `ui/MainTabScaffold.kt`, `ui/settings/BudgetSettingsScreen.kt`, `ui/archive/ArchiveDetailScreen.kt`, `ui/archive/ArchiveListScreen.kt`, `ui/autoentry/AutoEntrySettingsScreen.kt`, `ui/autoentry/PendingReviewScreen.kt`, `ui/settings/SettingsScreen.kt`, `ui/feed/FeedScreen.kt` (모두 `app/src/main/java/com/householdbudget/app/` 아래)
- Test: `app/src/test/java/com/householdbudget/app/ui/components/TransactionRowTextsTest.kt`

**Interfaces:**
- Produces:
  - `@Composable fun AppTopBar(title: String, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {})` — 높이 56dp, 제목 `titleLarge`, 뒤로 버튼 48dp. 인셋은 넣지 않는다(바깥 `Scaffold`가 상태바 여백을 이미 준다).
  - `@Composable fun cardBorder(): BorderStroke` = `BorderStroke(1.dp, colorScheme.outlineVariant)`.
  - `internal fun transactionRowTexts(categoryName: String, parentCategoryName: String?, memo: String, dateLabel: String?): Pair<String, String>` (제목, 보조줄).
  - `TransactionRow(...)` 시그니처는 그대로.

- [ ] **Step 1: 실패하는 테스트**

```kotlin
class TransactionRowTextsTest {
    @Test fun merchantIsTitle_categoryIsSubtitle() =
        assertEquals("김가짜 칼국수" to "식비 · 식사", transactionRowTexts("식사", "식비", "김가짜 칼국수", null))
    @Test fun blankMemo_fallsBackToLeafName() =
        assertEquals("식사" to "식비", transactionRowTexts("식사", "식비", "  ", null))
    @Test fun dateLabelLeadsSubtitle() =
        assertEquals("가짜커피" to "11.22 (일) · 식비 · 카페", transactionRowTexts("카페", "식비", "가짜커피", "11.22 (일)"))
    @Test fun noParentNoMemo_hasEmptySubtitle() =
        assertEquals("월급" to "", transactionRowTexts("월급", null, "", null))
}
```

- [ ] **Step 2:** `GRADLE :app:testDebugUnitTest --tests "*TransactionRowTextsTest"` → FAIL (unresolved reference).
- [ ] **Step 3: `TransactionRow`** — `transactionRowTexts`를 구현해 쓴다. 아바타 40dp(카테고리 색 16% + 이모지), 제목 `bodyLarge`, 보조줄 `bodySmall`/`onSurfaceVariant`, 금액 `titleMedium` + `kindAccent(kind)`(말줄임 없음), 최소 높이 64dp, 좌우 여백 `Space.lg`.
- [ ] **Step 4: `AppTopBar`·`cardBorder` 추가, `ScreenHeader` 삭제.** `ScreenHeader` 사용처 5곳(가계부·설정·자동 기록·확인 필요·지난 기록 목록)과 `TopAppBar` 2곳(예산 설정·지난 기록 상세)을 `AppTopBar`로 바꾼다. 예산 설정·지난 기록 상세의 안쪽 `Scaffold`는 `Column { AppTopBar(…); 내용 }`으로 바꾼다(인셋이 두 번 들어가 생긴 100dp 빈 공간의 원인). 지난 기록 목록의 부제는 바 아래 `bodySmall` 한 줄로 옮긴다. `ElevatedPanel`은 `surface` + `shapes.large` + `cardBorder()`로.
- [ ] **Step 5: `MainTabScaffold`** — `val pushed = selected == MainTab.SETTINGS && settingsPane != SettingsPane.Main`; `bottomBar = { if (!pushed) AppBottomBar(…) }`(`MidnightBottomBar` → `AppBottomBar`, `TossNavItem` → `NavItem`으로 이름만 변경). 밀려난 화면 5개의 목록은 `FabContentBottomPadding` 대신 `WindowInsets.navigationBars` 여백 + `Space.xxl`을 쓴다(예산 설정 각주가 + 버튼에 가려지던 문제 해결). + 버튼: 56dp, 돌출 16dp, `Modifier.shadow(8.dp, CircleShape, ambientColor = primary, spotColor = primary)`. 탭 라벨 `labelLarge`(선택 `primary`, 아니면 `onSurfaceVariant`), 인라인 `fontWeight` 삭제.
- [ ] **Step 6:** 테스트 PASS 확인 후 빌드·테스트·커밋. `feat: 상단 바를 하나로 통일하고 거래 행을 가맹점 우선으로`

### Task 4 [Opus]: 홈 — 남은 예산 히어로

**Files:**
- Modify: `ui/feed/FeedLogic.kt`, `ui/feed/FeedScreen.kt`, `ui/components/BudgetBar.kt`, `ui/components/SummaryTiles.kt`, `ui/theme/KindStyle.kt`, `ui/util/Formatters.kt`, `ui/MainTabScaffold.kt`, `res/values/strings_ledger.xml`, `res/values/strings_components.xml`, `res/values/strings_auto.xml`
- Test: `app/src/test/java/com/householdbudget/app/ui/feed/FeedLogicTest.kt`, `.../ui/util/FormattersTest.kt`

**Interfaces:**
- Produces:
  - `data class BudgetStatus(val remainingMinor: Long, val dailyAllowanceMinor: Long, val usedFraction: Float)`
  - `fun budgetStatus(budgetMinor: Long, spentMinor: Long, daysRemaining: Int): BudgetStatus` — `remaining = budget − spent`(음수 = 초과). `dailyAllowance = max(remaining, 0) / max(daysRemaining, 1)`을 100원 단위로 내림. `usedFraction = spent / budget`을 0..1로 자름, `budget <= 0`이면 `spent > 0`일 때 1 아니면 0.
  - `@Composable fun BudgetProgressBar(spentMinor: Long, budgetMinor: Long, modifier: Modifier = Modifier)` — 8dp 막대(`primary` / 트랙 `surfaceVariant`, 초과면 `error`) + 아래 `bodySmall` 두 개: 왼쪽 "187,600원 씀", 오른쪽 "예산 1,200,000원 중 84% 남음"(초과면 "예산 1,200,000원 초과"). `daysRemaining`·`onHero` 인자는 없앤다.
  - `@Composable fun StatColumn(label: String, value: String, dotColor: Color?, modifier: Modifier = Modifier)` — `bodySmall` 라벨(8dp 점) 위, `titleMedium` 값 아래. `KindSummaryRow`는 컨테이너 없는 `StatColumn` 세 개로 다시 쓴다(점: `kindDotColor`). `SummaryTile`은 쓰는 곳이 없어지면 지운다.
  - `FeedScreen(…, onOpenBudget: () -> Unit = {})` 인자 추가.

- [ ] **Step 1: 실패하는 테스트** (`FeedLogicTest`에 추가)

```kotlin
@Test fun budgetStatus_matchesMockup() {
    val s = budgetStatus(1_200_000, 187_600, 26)
    assertEquals(1_012_400, s.remainingMinor)
    assertEquals(38_900, s.dailyAllowanceMinor) // 38,938 → 100원 내림
    assertEquals(0.156f, s.usedFraction, 0.001f)
}
@Test fun budgetStatus_overBudget_hasNoAllowance() {
    val s = budgetStatus(100_000, 130_000, 5)
    assertEquals(-30_000, s.remainingMinor); assertEquals(0, s.dailyAllowanceMinor); assertEquals(1f, s.usedFraction)
}
@Test fun budgetStatus_lastDay_dividesByOne() = assertEquals(60_000, budgetStatus(100_000, 40_000, 0).dailyAllowanceMinor)
@Test fun budgetStatus_zeroBudget() {
    assertEquals(0f, budgetStatus(0, 0, 10).usedFraction); assertEquals(1f, budgetStatus(0, 500, 10).usedFraction)
}
```

`FormattersTest`의 `paydayCountdownLabel` 기대값을 새 문구로 고친다: 보통 `"월급까지 26일"`, 주기 첫날 `"오늘이 월급날이에요"`, 마지막 날 `"내일이 월급날이에요"`.

- [ ] **Step 2:** `GRADLE :app:testDebugUnitTest --tests "*FeedLogicTest" --tests "*FormattersTest"` → FAIL.
- [ ] **Step 3:** `budgetStatus`, `paydayCountdownLabel` 문구, `BudgetProgressBar`, `StatColumn`/`KindSummaryRow` 구현. 테스트 PASS.
- [ ] **Step 4: `FeedScreen` 다시 짜기** (목업 `NewHome.dc.html`, 스펙 5.1). 위에서 아래로:
  1. **상단 바**(`AppTopBar` 대신 전용 `Row`, 56dp): `‹ 11월 ›`(`titleLarge`, 화살표 48dp, 기존 `archiveMonthTitle`·`previousPeriod`/`nextPeriod`) + `bodySmall` 범위 "11.20 ~ 12.17". 오른쪽 검색 아이콘(48dp)이 `rememberSaveable showSearch`를 토글한다. 기존 검색 필드는 `showSearch`일 때만 칩 위에 보인다. "가계부" 큰 제목은 없앤다.
  2. **`FeedHeroCard`**(`surface`, `shapes.large`, `cardBorder()`, 안쪽 20dp): 줄1 `bodySmall` "이번 달 예산에서" + 오른쪽 `paydayCountdownLabel`(지난 기간이면 "오늘로" 칩 → `resetPeriod`). 줄2 `displaySmall` 남은 금액 + `bodySmall` "남았어요". 줄3 `bodySmall` "하루 38,900원씩 쓸 수 있어요"(현재 기간이고 남았을 때만). 그 아래 `BudgetProgressBar`.
     - 초과: 줄1 "이번 달 예산보다", 금액은 `-remainingMinor`를 `error`로, 뒤에 "더 썼어요". 줄3 없음.
     - 예산 미설정(`monthlyBudgetMinor == null`): 줄1 "이번 달 남은 돈", 금액 `summary.netMinor`, 막대 대신 `TextButton` "예산 정하기" → `onOpenBudget`.
  3. **`KindSummaryRow`**(수입·지출·저축).
  4. **확인 필요 띠**(`pendingCount > 0`): `primaryContainer`, `shapes.small`, 높이 48dp, 24dp 원 배지(`primary`/`onPrimary`)에 건수, `titleMedium` "확인할 거래가 있어요", 오른쪽 `labelLarge` `primary` "정리하기 ›". 띠 전체가 버튼.
  5. **필터 칩**(전체·지출·수입·저축): 선택 `primaryContainer`/`onPrimaryContainer`, 미선택 1dp `outline` 테두리, 36dp 높이에 48dp 터치.
  6. **날짜 묶음**: 머리줄 `labelLarge` `onSurfaceVariant` "오늘 · 11.22 (일)" + 오른쪽 `bodySmall` "101,000원 씀". 그 아래 **하루에 카드 하나**(`surface`, `shapes.large`, `cardBorder()`)에 그날의 `TransactionRow`들, 행 사이 `HorizontalDivider(color = outlineVariant)`를 왼쪽 72dp 들여서. 행마다 카드를 두지 않는다.
- [ ] **Step 5:** `MainTabScaffold`에서 `onOpenBudget = { selectedOrdinal = MainTab.SETTINGS.ordinal; settingsPane = SettingsPane.Budget }` 연결(바로 위 `onOpenPending` 콜백과 같은 모양). `KindStyle.kt`의 `heroGradient`·`onHeroColor`·`onHeroMutedColor` 삭제. 안 쓰게 된 문자열(`budget_bar_meta`, `budget_bar_daily_leeway`, `budget_bar_until_today`, `auto_pending_banner`) 삭제.
- [ ] **Step 6: 리뷰 항목** — 히어로·요약 줄·행의 금액 `Text`에 `maxLines`/`overflow`가 없다. `FeedScreen.kt`에 `fontWeight =`가 없다.
- [ ] **Step 7: 빌드·테스트·커밋.** `feat: 홈 히어로를 남은 예산으로 바꾸고 거래 목록을 날짜별 카드로`

### Task 5 [Opus]: 달력 — 월급 주기 표시

**Files:**
- Modify: `core/src/main/kotlin/com/householdbudget/app/domain/PeriodResolver.kt`, `app/.../data/repository/BudgetRepository.kt`, `app/.../ui/calendar/CalendarViewModel.kt`, `app/.../ui/calendar/CalendarScreen.kt`, `res/values/strings.xml`
- Create: `app/src/main/java/com/householdbudget/app/ui/calendar/CalendarLogic.kt`
- Test: `core/src/test/kotlin/com/householdbudget/app/domain/PeriodResolverTest.kt`, `app/src/test/java/com/householdbudget/app/ui/calendar/CalendarLogicTest.kt`

**Interfaces:**
- Produces:
  - `PeriodResolver.periodAtOffset(reference: LocalDate, paydayDom: Int, offset: Int): BudgetPeriod` — 0 = `periodContaining`, 음수는 `previousPeriod`를, 양수는 `nextPeriod`를 그만큼 반복.
  - `BudgetRepository.observePeriodAtOffset(offset: Int): Flow<BudgetPeriod>`; `observeSummaryAtOffset`은 이것을 쓰게 고친다. `observeDayTotalsInMonth(YearMonth)`는 `observeDayTotalsIn(period: BudgetPeriod): Flow<List<DayTotalRow>>`로 바꾼다(DAO `observeDayTotalsBetween`의 WHERE 경계를 읽고 마지막 포함일 `endExclusive − 1`이 들어가게 넘긴다).
  - `CalendarViewModel`: `visibleMonth`·`previousMonth`·`nextMonth` 삭제 → `val period: StateFlow<BudgetPeriod?>`, `val offset: StateFlow<Int>`, `fun previousPeriod()`, `fun nextPeriod()`(미래도 허용), `fun resetPeriod()`. `dayTotals`는 그대로 `StateFlow<Map<Long, DayTotalRow>>`.
  - `fun calendarGridDays(period: BudgetPeriod): List<LocalDate?>` — 일요일 시작. 앞 빈칸(`null`) + `startInclusive..endExclusive−1` + (마지막 줄에 자리가 남으면) `endExclusive` 한 칸(다음 월급날 표시용) + 뒤 빈칸. 크기는 7의 배수.

- [ ] **Step 1: 실패하는 테스트**

```kotlin
// PeriodResolverTest (월급일 20일, 2026-12-20은 일요일이라 12-18로 당겨진다)
@Test fun periodAtOffset_zeroPrevNext() {
    val r = PeriodResolver(); val ref = LocalDate.of(2026, 11, 22)
    assertEquals(BudgetPeriod(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 12, 18)), r.periodAtOffset(ref, 20, 0))
    assertEquals(BudgetPeriod(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 20)), r.periodAtOffset(ref, 20, -1))
    assertEquals(BudgetPeriod(LocalDate.of(2026, 12, 18), LocalDate.of(2027, 1, 20)), r.periodAtOffset(ref, 20, 1))
}
@Test fun periodAtOffset_earlyPayday_pulledIntoPreviousMonth() { // 2027-01-01 신정 → 2026-12-31
    val p = PeriodResolver().periodAtOffset(LocalDate.of(2026, 12, 31), 1, 0)
    assertEquals(LocalDate.of(2026, 12, 31), p.startInclusive)
}

// CalendarLogicTest
@Test fun grid_fiveWeeks_withNextPaydayMarker() { // 11.20(금) ~ 12.17(목), 다음 월급날 12.18(금)
    val g = calendarGridDays(BudgetPeriod(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 12, 18)))
    assertEquals(35, g.size)
    assertEquals(listOf<LocalDate?>(null, null, null, null, null), g.take(5))
    assertEquals(LocalDate.of(2026, 11, 20), g[5]); assertEquals(LocalDate.of(2026, 12, 17), g[32])
    assertEquals(LocalDate.of(2026, 12, 18), g[33]); assertNull(g[34])
}
@Test fun grid_sixWeeks() { // 10.30(금) ~ 11.29(일), 다음 월급날 11.30(월)
    val g = calendarGridDays(BudgetPeriod(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 30)))
    assertEquals(42, g.size); assertEquals(LocalDate.of(2026, 11, 29), g[35]); assertEquals(LocalDate.of(2026, 11, 30), g[36])
}
@Test fun grid_endsOnSaturday_hasNoMarker() { // 11.1(일) ~ 11.28(토)
    val g = calendarGridDays(BudgetPeriod(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 29)))
    assertEquals(28, g.size); assertEquals(LocalDate.of(2026, 11, 28), g.last())
}
```

- [ ] **Step 2:** `GRADLE :core:test --tests "*PeriodResolverTest" :app:testDebugUnitTest --tests "*CalendarLogicTest"` → FAIL.
- [ ] **Step 3:** `periodAtOffset`, 저장소 두 함수, `calendarGridDays`, `CalendarViewModel` 구현. `observeDayTotalsInMonth`를 쓰던 테스트가 있으면 새 함수로 옮긴다. 테스트 PASS.
- [ ] **Step 4: `CalendarScreen` 다시 짜기** (목업 `NewCalendar.dc.html`, 스펙 5.5):
  1. 상단(56dp): 왼쪽 `titleLarge` "11월"(`archiveMonthTitle(period.startInclusive, today)`) + `bodySmall` "11.20 ~ 12.17 · 월급날 기준". 오른쪽 `offset != 0`일 때 `TextButton` "오늘" → `resetPeriod`, 그리고 ‹ › (48dp).
  2. 요약 한 줄: `labelLarge` `onSurface` "지출 187,600원" + `bodySmall` `onSurfaceVariant` " · 수입 3,000,000원 · 저축 500,000원". `KindSummaryRow` 호출은 없앤다.
  3. 요일 줄: 7개 모두 `bodySmall` `onSurfaceVariant`(일·토 색 없음).
  4. 격자: `calendarGridDays(period).chunked(7)`, 칸 높이 64dp. 날짜 숫자 `bodyLarge`. 그 아래 `bodySmall`: 지출이 있으면 `compactAmountLabel(expenseMinor)`(`ui/stats/MoneyMap.kt`의 기존 함수, `onSurfaceVariant`), 수입이 있으면 `"+" + compactAmountLabel(incomeMinor)`(`primary`)를 우선, 둘 다 없고 `startInclusive`거나 `endExclusive` 칸이면 "월급"(`primary`), 그것도 아니고 그 달 1일이면 "12월"(`onSurfaceVariant`, 달이 바뀌는 자리 표시). 오늘은 숫자 둘레 32dp `primary` 1.5dp 링. 선택된 칸은 `surfaceVariant` + `shapes.small`. `endExclusive` 칸은 누를 수 없고 숫자가 `onSurfaceVariant`.
  5. 선택 날짜: `selectedEpochDay` 기본값은 오늘이 이 기간에 있으면 오늘, 없으면 선택 없음(`remember(period)`). 머리줄 `titleMedium` "11월 22일 (일) · 오늘" + 오른쪽 `bodySmall` "4건 · 101,000원". 그 아래 Task 4와 같은 "하루 카드". 비었으면 `bodySmall` `calendar_day_empty` = "이날은 쓴 돈이 없어요".
- [ ] **Step 5: 빌드·테스트·커밋.** `feat: 달력이 달력 월 대신 월급 주기를 보여주게 하기`

### Task 6 [Opus]: 카테고리 선택기와 거래 추가 화면

**Files:**
- Create: `app/src/main/java/com/householdbudget/app/ui/components/CategoryPicker.kt`
- Modify: `ui/edit/EditTransactionScreen.kt`, `res/values/strings_edit.xml`

**Interfaces:**
- Produces: `@Composable fun CategoryPicker(parents: List<CategoryEntity>, childrenByParent: Map<Long, List<CategoryEntity>>, selectedLeafId: Long?, onLeafSelected: (CategoryEntity) -> Unit, modifier: Modifier = Modifier, onMore: (() -> Unit)? = null)`
  - 4열 격자, 타일 높이 64dp, 간격 8dp, `surfaceVariant` + `shapes.small`, 이모지(24sp 크기는 `titleLarge` 사용) 위 `bodySmall` 이름. `parents`는 앞에서 7개까지, `onMore != null`이면 마지막 타일 "더 보기".
  - 타일을 누르면 **같은 자리에서** 그 대분류의 세부 칩(`FlowRow`)으로 바뀌고 맨 앞에 "‹ 식비" 칩(누르면 격자로). 상태는 내부 `rememberSaveable expandedParentId`. 세부가 하나뿐이면 바로 `onLeafSelected`.
  - 선택된 세부 칩만 `primaryContainer`/`onPrimaryContainer`. 나머지는 1dp `outline`.
- Consumes: `EditTransactionViewModel`의 기존 `setKind`/`setParent`/`appendAmountDigits`/`deleteLastAmountDigit`/`setMemo`/`setDate`/`save`와 `EditTransactionUiState.canSave` — ViewModel은 고치지 않는다. 선택은 `vm.setParent(leaf.parentId!!, leaf)`.

- [ ] **Step 1: `CategoryPicker` 작성.**
- [ ] **Step 2: `EditTransactionScreen` 배치 바꾸기** (목업 `NewAdd.dc.html`, 스펙 5.2). 위에서 아래로, 금액 영역은 스크롤 밖에 고정:
  1. 머리(48dp): 가운데 `KindSelector`를 3칸 세그먼트로(트랙 `surfaceVariant` 원형, 선택 칸 `surface` + `labelLarge`, 빨간색 없음). 오른쪽 닫기 X(48dp). 수정 모드에서는 왼쪽에 기존 삭제 아이콘. "거래 추가" 제목은 없앤다.
  2. 금액(고정): 가운데 `displaySmall` — 비었으면 "0원"을 `onSurfaceVariant`, 입력되면 `onSurface`. 아래 날짜 알약(`surfaceVariant`, 원형, `bodySmall` "오늘 · 11.22 (일) ⌄") → 기존 날짜 선택 흐름(오늘/어제/날짜 선택)을 `DropdownMenu`로. 굵은 "오늘" 라벨과 `DateQuickChip` 줄은 없앤다.
  3. 카테고리: 한 줄 상태 — 미선택이면 `bodySmall` `onSurfaceVariant` "카테고리", 선택되면 `titleMedium` "식비 · 식사" + `Icons.Rounded.Check`(`primary`). 그 아래 "최근"(`recentCategoryIdsByKind[kind]`, 글자만 있는 칩의 `LazyRow`) → `CategoryPicker(parents = parentsByKind[kind], …, onMore = { showCategorySheet = true })`(기존 카테고리 검색 시트 재사용). 안쪽 전용 스크롤 창은 없앤다.
  4. 메모(48dp): `surfaceVariant` + `shapes.small` 한 줄 입력, placeholder `edit_memo_hint` = "메모 (선택)"(✏️ 이모지 삭제). 메모에 포커스가 있으면 `AmountKeypad`를 숨긴다.
  5. `AmountKeypad`: 키 `surfaceVariant` + `shapes.small`, 숫자 `titleLarge`, 줄 높이 56dp(`BoxWithConstraints`로 화면 높이 700dp 미만이면 48dp), 간격 8dp.
  6. 저장(56dp, `shapes.small`, 전체 폭, `titleMedium`): 금액 0이면 비활성 + `edit_save_need_amount` "금액을 입력해 주세요", 금액은 있고 카테고리가 없으면 비활성 + `edit_save_need_category` "카테고리를 골라 주세요", 가능하면 `primary` + `edit_save_amount` "%1$s 저장"("12,000원 저장"). 키패드 위에 떠 있던 안내 문구는 없앤다. 하단 바의 `shadowElevation = 8.dp`는 없애고 위쪽 `HorizontalDivider(outlineVariant)`로.
  2–4는 작은 화면 대비로 하나의 `verticalScroll` 안에 두되(3은 자체 스크롤 없음), 1·금액·5·6은 스크롤 밖이다.
- [ ] **Step 3:** `EditCard`·`DateQuickChip`·`leafChipColors` 등 안 쓰게 된 private 컴포저블과 문자열 삭제. `EditTransactionScreen.kt`에 `fontWeight =`가 없다.
- [ ] **Step 4: 빌드·테스트(기존 `EditTransactionViewModelTest` 그대로 통과)·커밋.** `feat: 거래 추가 화면에서 금액을 고정하고 카테고리를 격자로 고르기`

### Task 7 [Opus]: 확인 필요 — 한 건씩 정리 + 완료 화면

**Files:**
- Create: `app/src/main/java/com/householdbudget/app/ui/autoentry/PendingTriage.kt`
- Modify: `app/.../data/repository/AutoEntryRepository.kt`, `app/.../ui/autoentry/PendingReviewScreen.kt`, `app/.../ui/MainTabScaffold.kt`, `res/values/strings_auto.xml`
- Test: `app/src/test/java/com/householdbudget/app/ui/autoentry/PendingTriageTest.kt`, `app/src/test/java/com/householdbudget/app/data/repository/AutoEntryRepositoryTest.kt`

**Interfaces:**
- Consumes: `CategoryPicker` (Task 6), `budgetStatus` (Task 4), `TransactionRow`, `AppTopBar` (Task 3).
- Produces:
  - `suspend fun AutoEntryRepository.resolvePending(entryId: Long, categoryId: Long?): List<AutoEntryEntity>` — 이번 호출로 정리된 항목 전부(누른 항목 먼저, 같은 가맹점으로 함께 정리된 항목 뒤). 대기 중이 아니면 빈 목록. 동작은 그대로, 반환값만 추가.
  - `suspend fun AutoEntryRepository.suggestedCategoryId(entry: AutoEntryEntity): Long?` = `merchantKey`가 비어 있지 않을 때 `ruleDao.getByKey(entry.merchantKey, entry.direction)?.categoryId`.
  - `data class TriageState(val current: AutoEntryEntity?, val upNext: List<AutoEntryEntity>, val position: Int, val total: Int)`
  - `fun triageState(pending: List<AutoEntryEntity>, deferredIds: Set<Long>, doneCount: Int): TriageState` — 순서 = 미루지 않은 항목(원래 순서) 뒤에 미룬 항목. `current` = 첫 항목, `upNext` = 그다음 2개, `total = doneCount + pending.size`, `position = min(doneCount + 1, total)`.
  - `PendingReviewScreen(budgetViewModel, onBack: () -> Unit, onDone: () -> Unit, modifier)`.

- [ ] **Step 1: 실패하는 테스트**

```kotlin
// PendingTriageTest — e(id)는 AutoEntryEntity를 만드는 테스트 헬퍼(status = PENDING)
@Test fun firstIsCurrent_nextTwoAreUpNext() {
    val s = triageState(listOf(e(1), e(2), e(3)), emptySet(), 0)
    assertEquals(1L, s.current?.id); assertEquals(listOf(2L, 3L), s.upNext.map { it.id })
    assertEquals(1, s.position); assertEquals(3, s.total)
}
@Test fun deferredGoesToTheEnd() {
    val s = triageState(listOf(e(1), e(2), e(3)), setOf(1L), 0)
    assertEquals(2L, s.current?.id); assertEquals(listOf(3L, 1L), s.upNext.map { it.id })
}
@Test fun cascadeResolvedTwoAtOnce_progressStaysConsistent() {
    val s = triageState(listOf(e(3)), emptySet(), 2)
    assertEquals(3, s.position); assertEquals(3, s.total); assertEquals(emptyList<AutoEntryEntity>(), s.upNext)
}
@Test fun allDeferred_stillShowsOne() = assertEquals(1L, triageState(listOf(e(1)), setOf(1L), 0).current?.id)
@Test fun empty_hasNoCurrent() {
    val s = triageState(emptyList(), emptySet(), 3)
    assertNull(s.current); assertEquals(3, s.position); assertEquals(3, s.total)
}
```

`AutoEntryRepositoryTest`에 기존 픽스처로 세 개 추가: `resolvePending_returnsTargetThenCascaded`(같은 `merchantKey`·UNCLASSIFIED 대기 2건 → 반환 id 2개, 누른 항목이 첫 번째), `resolvePending_notPending_returnsEmpty`, `suggestedCategoryId_returnsLearnedRule_orNull`(정리해서 규칙이 생긴 가맹점은 그 카테고리 id, 모르는 가맹점·빈 키는 `null`).

- [ ] **Step 2:** `GRADLE :app:testDebugUnitTest --tests "*PendingTriageTest" --tests "*AutoEntryRepositoryTest"` → FAIL.
- [ ] **Step 3:** `triageState`, 저장소 두 함수 구현. 테스트 PASS.
- [ ] **Step 4: `PendingReviewScreen` 다시 짜기** (목업 `NewPending.dc.html`·`NewPendingDone.dc.html`, 스펙 5.3). `ResolveSheet`와 목록 UI는 지운다. 화면 상태: `deferred: Set<Long>`, `resolved: List<Pair<AutoEntryEntity, Long?>>`(항목, 고른 세부 id 또는 `null` = 기록 안 함), `kind`(현재 항목이 바뀔 때 `defaultKindFor(direction)`으로 초기화). `resolve(leafId)`는 `repo.resolvePending`의 반환 목록을 `resolved`에 더한다.
  - **정리 중**(`state.current != null`):
    1. `AppTopBar(title = "확인할 거래", onBack)` + 오른쪽 `bodySmall` "3건 중 1번째". 바로 아래 4dp `LinearProgressIndicator(progress = { resolved.size.toFloat() / total })`.
    2. 현재 항목 카드(`surface`, `shapes.large`, `cardBorder()`, 20dp): `bodySmall` 날짜(`formatShortDayLabel`) + 이유가 UNCLASSIFIED가 아니면 " · " + `reasonLabelRes` 문구. `titleLarge` 가맹점(비면 `auto_pending_no_name`). `displaySmall` 금액. `AnimatedContent(targetState = current.id)`로 감싼다.
    3. "다음에 볼 거래": `upNext`를 48dp 줄로(`bodyLarge` `onSurfaceVariant` 가맹점 / 금액).
    4. `titleMedium` "어디에 쓴 돈인가요?"(한 번만).
    5. 추천(`suggestedCategoryId(current)`가 있을 때만, `produceState`): `primary` 1dp 테두리 카드 한 줄 — `titleMedium` "식비 · 카페로 정리하기" + `bodySmall` "지난번에 이렇게 정리했어요". 누르면 그 id로 정리.
    6. `CategoryPicker(parents = parentsByKind[kind], …, onLeafSelected = { resolve(it.id) })`.
    7. 종류 바꾸기 버튼 2개(48dp, `surfaceVariant`, `shapes.small`): 현재 `kind`가 아닌 두 종류 — "수입이에요"/"저축이에요"/"지출이에요". 누르면 `kind`만 바뀐다.
    8. 맨 아래 줄: 왼쪽 `TextButton` "기록 안 함" → `resolve(null)`. 오른쪽 `TextButton` "나중에 할게요" → `deferred += current.id`; 남은 항목이 전부 미뤄졌으면 `onBack()`.
  - **완료**(`pending.isEmpty() && resolved.isNotEmpty()`): 72dp `primary` 원 안 체크, `titleLarge` "%d건 모두 정리했어요"(`resolved.size`), 기록된 금액 합이 0보다 크면 `bodySmall` "%s이 가계부에 들어갔어요", 기록된 항목들을 `TransactionRow`로(카테고리 이름은 `budgetViewModel.categories`에서 찾는다), 월 예산이 있으면 `bodySmall` "이번 달 남은 예산" + `titleLarge` 금액(`budgetStatus(…).remainingMinor`, `budgetViewModel.homeSummary`·`monthlyBudgetMinor`·`daysRemainingInPeriod`), 맨 아래 56dp `primary` 버튼 "가계부로 돌아가기" → `onDone`.
  - **처음부터 비어 있음**: 기존 `EmptyState` 그대로.
- [ ] **Step 5: `MainTabScaffold`** — `var pendingFromFeed by remember { mutableStateOf(false) }`. 홈 띠·알림으로 들어오면 `true`, 자동 기록 화면에서 들어오면 `false`. `onBack`과 `BackHandler`: `pendingFromFeed`면 `selectedOrdinal = MainTab.FEED.ordinal`, 아니면 `SettingsPane.AutoEntry`. `onDone`은 항상 가계부 탭.
- [ ] **Step 6: 문자열** — `auto_pending_title` "확인할 거래", 새 문구 추가, `auto_reason_unclassified`는 다른 곳(자동 기록 화면)에서 쓰면 남긴다. `PendingLabelsTest`가 그대로 통과한다.
- [ ] **Step 7: 빌드·테스트·커밋.** `feat: 확인 필요를 한 건씩 정리하고 끝나면 완료 화면 보여주기`

### Task 8 [Sonnet]: 통계 다시 꾸미기

**Files:**
- Modify: `ui/stats/StatsScreen.kt`, `ui/stats/MapSections.kt`, `ui/stats/MoneyMap.kt`, `ui/stats/MapLogic.kt`, `res/values/strings_stats.xml`
- Test: `app/src/test/java/com/householdbudget/app/ui/stats/MapLogicTest.kt`

**Interfaces:**
- Consumes: `BudgetProgressBar`, `StatColumn`, `budgetStatus` (Task 4), `AppTopBar` (Task 3), 기존 `deltaPercent(currentMinor, previousMinor): Int?`.
- Produces: `internal fun spentThroughDay(rows: List<TransactionWithCategoryRow>, periodStartEpochDay: Long, elapsedDays: Long): Long` — `EXPENSE`이고 `occurredEpochDay − periodStartEpochDay <= elapsedDays`인 행의 합("지난달 이맘때").

- [ ] **Step 1: 실패하는 테스트** (`MapLogicTest`, 기존 행 헬퍼 사용)

```kotlin
@Test fun spentThroughDay_countsOnlyExpensesUpToElapsedDay() {
    val rows = listOf(row(day = 100, amount = 1_000, kind = "EXPENSE"), row(day = 102, amount = 2_000, kind = "EXPENSE"),
        row(day = 103, amount = 4_000, kind = "EXPENSE"), row(day = 101, amount = 9_000, kind = "INCOME"))
    assertEquals(3_000, spentThroughDay(rows, periodStartEpochDay = 100, elapsedDays = 2))
    assertEquals(0, spentThroughDay(emptyList(), 100, 2))
}
```

- [ ] **Step 2:** 실행 → FAIL. **Step 3:** 구현 → PASS.
- [ ] **Step 4: 화면** (목업 `NewStats.dc.html`, 스펙 5.4):
  1. 머리(전용 `Row`, 56dp — `StatsHeader`를 고친다): 왼쪽 `titleLarge` 월 이름 + `bodySmall` 범위, 오른쪽 `bodySmall` `paydayCountdownLabel`. 페이지 수준의 리포트/맵 토글은 여기서 뺀다.
  2. 요약 카드(`BudgetGaugeCard`를 대체, 도넛 삭제): `bodySmall` "이번 달 쓴 돈", `displaySmall` 지출 합, `bodySmall` "예산 1,200,000원 중 16% · 1,012,400원 남았어요"(예산 없으면 이 줄과 막대 생략), `BudgetProgressBar`의 막대 부분, 오른쪽 위 `bodySmall` 비교 문구 — `deltaPercent(spentThroughDay(current…), spentThroughDay(previous…))`: 음수면 `primary` "지난달 이맘때보다 12% 덜 썼어요", 양수면 `onSurfaceVariant` "…12% 더 썼어요"(빨강 아님), `null`·0이면 숨김. 맨 아래 `StatColumn` 세 개: 지출 / 저축(점 `tertiary`) / 남은 돈(수입 − 지출 − 저축).
  3. `WeeklySpendCard`는 남기고 색만: 지난 주 막대 `surfaceVariant`, 이번 주 `primary`, 위 값·아래 주 라벨 `bodySmall`, 막대 위 모서리 12dp.
  4. 카테고리 카드: 머리줄 `titleMedium` "카테고리별 지출" + 오른쪽 `ModeToggle`(목록 / 맵, 기존 `mode` 상태 재사용).
     - 목록 행(64dp 이상): 아바타 40dp, `bodyLarge` 이름, 오른쪽 `titleMedium` 금액, 이름 아래 4dp 막대(카테고리 색 / `surfaceVariant`) + `bodySmall`: "예산 350,000원의 12%" / 초과면 `error` "예산 30,000원보다 9,000원 더 썼어요" / 예산 없으면 "예산을 정하지 않았어요" / 지출 0이면 "아직 쓴 돈이 없어요". 행의 `DeltaLabel`(▲30%)은 뺀다.
     - 맵: `MapSummaryCard` + `MoneyMap`만. 아래 중복 목록 `MapRankList`·`RankRow`는 지운다. 타일은 카테고리 색 16% 채움, 테두리 없음. `SpentBarColor`(주황 하드코딩) 삭제 → 지출 구간 `onSurface` 60%, 저축 `tertiary`, 남은 돈 `surfaceVariant`.
  5. 카테고리 시트: 제목 `titleLarge`, 부제 `bodySmall` "23건 · 311,400원 · 지난달보다 30% 늘었어요"(줄었으면 "줄었어요", `null`이면 뒷부분 생략), 제목과 행의 왼쪽 여백을 20dp로 맞춘다.
- [ ] **Step 5:** `deltaDisplay`·`DeltaLabel`·`BudgetGaugeCard`가 안 쓰이면 그 테스트와 문자열까지 지운다. 리뷰 항목: 금액 `Text`에 말줄임 없음, `ui/stats/`에 `Color(0x` 없음.
- [ ] **Step 6: 빌드·테스트·커밋.** `feat: 통계를 금액 중심으로 다시 꾸미고 목록·맵 토글을 카드 안으로`

### Task 9 [Sonnet]: 예산 설정 화면

**Files:**
- Modify: `ui/settings/BudgetSettingsScreen.kt`, `res/values/strings_more.xml`

**Interfaces:**
- Consumes: `AppTopBar`, `cardBorder` (Task 3; 겹침 원인인 하단 바는 Task 3에서 이미 숨겼다), 기존 `BudgetSheetTarget` 시트·`openSheet`/`applySheet`/`clearSheet`.

- [ ] **Step 1: 화면** (목업 `NewBudget.dc.html`, 스펙 5.6):
  1. 합계 카드(전체가 버튼 → `openSheet(Monthly)`): `bodySmall` "한 달 예산", `displaySmall` 금액(미설정이면 `titleLarge` "예산을 정해 주세요"), 오른쪽 `labelLarge` `primary` "바꾸기". 8dp 배정 막대(카테고리 색 구간을 이어 붙임 / 트랙 `surfaceVariant`). `bodySmall` "카테고리에 580,000원 · 자유롭게 620,000원"; 배정 합이 한 달 예산보다 크면 "카테고리 예산이 한 달 예산보다 %s 많아요"(`onSurfaceVariant`, 빨강 아님).
  2. 섹션 머리: `labelLarge` `onSurfaceVariant` "카테고리별 예산" + `bodySmall` "정해 두면 통계에서 얼마나 썼는지 보여 드려요"(맨 아래 각주를 여기로 옮기고 각주는 지운다).
  3. 카테고리 카드 하나에 행(64dp)들, 구분선 `outlineVariant`: 아바타 40dp(`TransactionRow`와 같은 16% 원), `bodyLarge` 이름, 아래 `bodySmall` "41,800원 썼어요"(0이면 "아직 쓴 돈이 없어요", 초과면 " · 9,000원 초과"를 `error`로 덧붙임). 오른쪽: 예산이 있으면 `titleMedium` 금액 + 16dp `KeyboardArrowRight`(`onSurfaceVariant`), 없으면 `labelLarge` `primary` "예산 정하기". 민트/강조색으로 값을 칠하지 않는다. "—"는 없앤다.
- [ ] **Step 2: 리뷰 항목** — 목록 맨 아래 여백이 내비게이션 바 인셋 + `Space.xxl`이고 `FabContentBottomPadding`을 쓰지 않는다.
- [ ] **Step 3: 빌드·테스트·커밋.** `fix: 예산 설정에서 가려지던 안내를 위로 옮기고 편집 가능한 행을 드러내기`

### Task 10 [Sonnet]: 남은 결함, 문구 해요체, 마지막 쓸기

**Files:**
- Modify: `ui/archive/ArchiveListScreen.kt`, `ui/archive/ArchiveDetailScreen.kt`, `ui/settings/SettingsScreen.kt`, `ui/autoentry/AutoEntrySettingsScreen.kt`, `security/AppLockGate.kt`, `res/values/strings.xml`, `strings_edit.xml`, `strings_more.xml`, 그리고 아래 grep에 걸리는 파일

- [ ] **Step 1: 지난 기록 카드(금액 잘림).** `ArchiveCard`: `bodySmall` "9월 · 9.25 ~ 10.24", `titleLarge` "1,830,250원 남겼어요"(음수면 `onSurface`로 "%s 더 썼어요", 빨강 아님), `bodySmall` 한 줄 "수입 3,000,000원 · 지출 669,750원 · 저축 500,000원"(줄바꿈 허용, `maxLines` 없음). `KindSummaryRow`·휴지통 아이콘·보관 시각·"자세히 보기"·카드마다 반복되던 "(월급날 기준)"은 뺀다. 카드 전체가 버튼(이미 그렇다).
- [ ] **Step 2: 삭제를 상세로.** 목록에 있던 삭제 확인 다이얼로그와 `repository.deleteArchivedPeriod` 호출을 `ArchiveDetailScreen`으로 옮기고 `AppTopBar`의 `actions`에 휴지통 `IconButton`을 둔다. 삭제 후 `onBack()`. 상세 제목은 기간("9월"), "당시 거래 내역" → "거래 내역".
- [ ] **Step 3: 월급날 시트(29–31 잘림).** `rememberModalBottomSheetState(skipPartiallyExpanded = true)`, 내용 `Column`에 `verticalScroll` + `navigationBarsPadding()`. 칸 48dp × 7열 5줄, 채운 원 없이 숫자만(`bodyLarge`), 선택된 날만 `primary` 원 + `onPrimary`. 안내는 `settings_payday_hint` 한 문장만, `settings_payday_select`·`settings_payday_adjust_hint`는 지운다. 고르면 `Toast`로 `settings_payday_changed` "월급날을 %d일로 바꿨어요".
- [ ] **Step 4: 설정 화면.** 행 아이콘 타일 `surfaceVariant` 바탕 + `onSurfaceVariant` 아이콘. 섹션 머리 `bodySmall` `onSurfaceVariant`. `Switch` 꺼짐 상태 `uncheckedBorderColor`·`uncheckedThumbColor` = `onSurfaceVariant`, `uncheckedTrackColor` = `surface`(3:1 확보). 자동 기록 행은 대기 건수가 있으면 오른쪽에 24dp `primary` 원 배지. `settings_export_title` "파일로 내보내기".
- [ ] **Step 5: 낮은 대비 테두리.** `OutlinedTextField`(홈 검색 등)의 `unfocusedBorderColor`는 `onSurfaceVariant`. 칩 테두리는 목업대로 `outline`을 유지한다(라벨이 칩을 식별한다). `outlineVariant.copy(alpha = …)`로 더 옅게 만든 구분선은 `outlineVariant` 그대로 쓴다.
- [ ] **Step 6: 해요체.**

| 키 | 새 문구 |
|---|---|
| `ledger_empty` | 아직 기록한 거래가 없어요 |
| `settings_payday_hint` | 월급날을 골라 주세요. 주말·공휴일이면 앞 평일로, 31일이 없는 달은 말일로 맞춰요. |
| `settings_saved` | 저장했어요. |
| `archive_subtitle` | 월급날 기준으로 끝난 달을 자동으로 보관해요. |
| `archive_empty` | 아직 보관된 기록이 없어요. 다음 달로 넘어가면 지난달이 자동으로 저장돼요. |
| `archive_missing` | 보관 항목을 찾을 수 없어요. |
| `edit_delete_confirm_body` | 삭제하면 되돌릴 수 없어요. |
| `edit_discard_body` | 입력한 내용이 사라져요. |
| `edit_category_search_empty` | 검색 결과가 없어요. |

화면에 보이는 "대분류/소분류"는 "카테고리/세부"로, "학습한 규칙"은 "자동 분류 규칙"으로 바꾼다(문자열 값만, 키는 그대로).

- [ ] **Step 7: 마지막 쓸기.** 아래가 전부 **출력 없음**이 될 때까지 남은 화면(`AutoEntrySettingsScreen`, `AppLockGate`, `ArchiveDetailScreen`, `MoneyMap`, `EmptyState` 등)을 고친다. 고칠 때는 Global Constraints의 여섯 슬롯으로 옮긴다(16sp 이상 굵은 라벨 → `bodyLarge` 또는 `bodySmall`, 값 → `titleMedium`).

```bash
cd app/src/main
grep -rn "니다" res/values/
grep -rnE "fontWeight =|fontSize =|Space\.xxs|labelSmall|labelMedium|titleSmall|bodyMedium|headline(Large|Medium|Small)|display(Large|Medium)" java/com/householdbudget/app --include="*.kt" | grep -v "/ui/theme/"
grep -rn "Color(0x" java/com/householdbudget/app --include="*.kt" | grep -v "/ui/theme/"
grep -rnE "Midnight|Toss|민트|미드나잇" java/com/householdbudget/app res/values
grep -rnE "RoundedCornerShape\((2|3|4|7|8|14|24)\.dp\)" java/com/householdbudget/app
```

`MoneyMap`의 Canvas 글자처럼 `TextStyle`을 직접 만드는 곳은 `MaterialTheme.typography.bodySmall`/`labelLarge`를 받아 쓰게 한다.

- [ ] **Step 8: 빌드·테스트·커밋.** `fix: 지난 기록 금액 잘림·월급날 29–31일 잘림을 고치고 문구를 해요체로 통일`
