# 카테고리 색·이모지 통일 + 통계 맵 개편 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 카테고리 색·이모지를 한 곳에서 정의해 카테고리가 나오는 모든 화면에 쓰고, 통계 "맵"을 요약 + 카테고리 색 트리맵 + 순위 목록(A+B 시안)으로 바꾼다.

**Architecture:** 대분류 이름 → (색, 이모지) 조회 함수 하나를 `app`의 `ui/theme`에 두고, 이모지는 `core`의 `FixedCategories.TREE`에서 그대로 가져온다(DB 변경 없음). 맵의 계산(요약, 전월 대비, 순위 행, 타일 크기 단계)은 Compose에 의존하지 않는 순수 함수로 빼서 JVM 테스트로 고정하고, 화면은 그 결과만 그린다. 트리맵은 기존 `Canvas` + squarify 배치를 유지하고 그리기만 바꾼다.

**Tech Stack:** Kotlin 2.0.21, Compose(BOM 2024.10.01, Material3), Room v8(변경 없음), JUnit4.

**Spec:** `docs/superpowers/specs/2026-11-19-category-style-and-map-design.md` — 구현자는 자기 태스크 + Global Constraints + 스펙을 함께 읽는다. 승인된 시안(배치·크기 참고): `.superpowers/brainstorm/68957-1795094215/content/map-ab2.html` (git에 없는 로컬 파일. 시안의 px는 dp로 읽는다).

## Global Constraints

- 신규 의존성을 추가하지 않는다(테스트 전용 포함). `app/build.gradle.kts`, `core/build.gradle.kts`를 건드리지 않는다.
- DB 스키마·마이그레이션을 바꾸지 않는다(Room v8 유지). `FixedCategories.TREE`의 이름·이모지를 바꾸지 않는다.
- UI 문구는 한국어, `app/src/main/res/values/strings_*.xml`에 둔다(맵 문구는 `strings_stats.xml`). 코드에 한국어 UI 문구를 하드코딩하지 않는다(이모지·`▲▼` 같은 기호 조합은 예외).
- 로그를 추가하지 않는다(`Log.*`, `println` 금지).
- `git add`는 항상 파일 경로를 지정한다(`git add -A`, `git add .` 금지). 저장소 루트의 `.xlsx/.xls/.numbers/.csv` 파일은 열거나 읽거나 `git add` 하지 않는다.
- 테스트 데이터는 가짜만 쓴다(금액·이름 모두 지어낸 값, 가맹점 `가짜커피` 식).
- 이 계획의 새 테스트는 전부 순수 JVM 테스트다. 그래도 Android 클래스가 필요해지면 기존 app 테스트처럼 `@RunWith(RobolectricTestRunner::class)` + `@Config(sdk = [34], application = android.app.Application::class)`를 붙인다.
- 화면 구조는 맵 외에는 바꾸지 않는다. 테마(다크 단일 "Midnight")를 바꾸지 않는다.
- 커밋 메시지는 한국어(`feat: …`, `refactor: …`, `test: …`), 끝에 `Co-Authored-By: <실행한 모델> <noreply@anthropic.com>`. 브랜치는 `feat/category-style-map` 유지.
- 빌드·테스트 명령(저장소에 `./gradlew` 없음). 아래에서 `GRADLE`은 이 줄 전체를 뜻한다:
  `export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=~/Library/Android/sdk; ~/.gradle/wrapper/dists/gradle-8.10.2-bin/a04bxjujx95o3nb99gddekhwo/gradle-8.10.2/bin/gradle`
- 에뮬레이터: AVD `test35`, `ADB=~/Library/Android/sdk/platform-tools/adb`, 앱 id `com.householdbudget.app`.
- 모델 배정: 각 태스크 제목의 `[Sonnet·기계적]` / `[Opus·까다로움]`을 따른다.

## Review Focus

스펙이 테스트를 직접 요구하지 않지만 실제 사용에서 먼저 터질 입력들. 각 줄의 테스트는 괄호 안 태스크에 있다.

1. 이번 주기에 수입 기록이 없다(월급 전, 수입을 안 적는 사용자) → 지출 합계만 보이고 비율·막대가 없으며 0으로 나누지 않는다. (Task 2 `noIncome_hidesRatioAndBar`)
2. 지난달에 그 카테고리 지출이 0원이었다(새로 쓰기 시작한 카테고리, 앱 첫 달) → `▲무한%`가 아니라 "—". (Task 2 `delta_previousZero_isFlat`)
3. 지출 + 저축이 수입보다 많다 → "남은 돈" 대신 "초과 N만"(빨강), 막대 조각은 음수 없이 그려진다. (Task 2 `overIncome_showsOverSegment`)
4. 저축이 0원이거나 남은 돈이 정확히 0원이다 → 막대에 굵기 0인 조각을 넣지 않는다(Compose `weight(0f)`는 예외를 던진다). (Task 2 `zeroParts_areDropped`)
5. 한 카테고리가 지출의 1.5% 미만이거나 이름이 길다("유동 지출") → 글자가 타일 밖으로 넘치지 않고 작은 타일은 이모지만 보인다. (Task 2 `tileTier_thresholds`, Task 5 스크린샷)

## 파일 구조

| 파일 | 책임 | 태스크 |
|---|---|---|
| `app/.../ui/theme/CategoryStyle.kt` (신규) | 대분류 이름 → 색·이모지, `categoryLabel`, `CategoryDot` | 1 |
| `app/.../ui/components/TransactionRow.kt` | 아바타를 카테고리 색 원 + 이모지로 | 1 |
| `app/.../ui/stats/MapLogic.kt` (신규) | 맵 순수 계산: `deltaPercent`, `MapSummary`, `buildMapRows`, `tileTier` | 2 |
| `app/.../ui/stats/MoneyMap.kt` | 트리맵 배치(유지) + 새 타일 그리기 | 3 |
| `app/.../ui/stats/MapSections.kt` (신규) | `MapSummaryCard`, `MapRankList` 컴포저블 | 3 |
| `app/.../ui/stats/StatsScreen.kt` | 맵 모드 조립, 옛 맵 코드 삭제, 리포트 카드 스타일 | 2, 3, 4 |
| `app/.../ui/edit/EditTransactionScreen.kt`, `ui/autoentry/PendingReviewScreen.kt`, `ui/settings/BudgetSettingsScreen.kt` | 대분류 표시에 이모지·색 점 | 4 |
| `app/src/main/res/values/strings_stats.xml` | 맵 문구 추가·삭제 | 3 |

`app/...` = `app/src/main/java/com/householdbudget/app`, 테스트는 `app/src/test/java/com/householdbudget/app`.

---

### Task 1: 카테고리 스타일 정의 + 거래 행 아바타 `[Sonnet·기계적]`

**Files:**
- Create: `app/src/main/java/com/householdbudget/app/ui/theme/CategoryStyle.kt`
- Modify: `app/src/main/java/com/householdbudget/app/ui/components/TransactionRow.kt:82-109` (아바타 부분)
- Test: `app/src/test/java/com/householdbudget/app/ui/theme/CategoryStyleTest.kt`

**Interfaces:**
- Consumes: `FixedCategories.TREE: List<FixedParent>` (`name`, `icon`) — `core` 모듈, 이미 있음.
- Produces (패키지 `com.householdbudget.app.ui.theme`):
  - `data class CategoryStyle(val color: Color, val emoji: String)`
  - `fun categoryStyleOf(parentName: String): CategoryStyle` — 대분류 이름으로 조회. 없는 이름은 회색 `#90A4AE` + 이름 첫 글자(빈 문자열이면 `"?"`).
  - `fun categoryLabel(parentName: String): String` — `"🍽️ 식비"` 형태(이모지 + 공백 + 이름).
  - `@Composable fun CategoryDot(color: Color, modifier: Modifier = Modifier)` — 지름 8dp 원.
  - 호출 규칙: 거래 행에서는 `categoryStyleOf(parentCategoryName ?: categoryName)`. 소분류 이름만으로 조회하지 않는다("기타"가 대분류이자 수입·저축의 소분류라서 섞인다).

- [ ] **Step 1: 실패하는 테스트 작성** — `CategoryStyleTest`:

```kotlin
class CategoryStyleTest {
    @Test fun everyFixedParent_hasItsOwnColorAndTreeEmoji() {
        for (p in FixedCategories.TREE) assertEquals(p.name, p.icon, categoryStyleOf(p.name).emoji)
        assertEquals(9, FixedCategories.TREE.map { categoryStyleOf(it.name).color }.toSet().size)
    }

    @Test fun palette_matchesSpec() {
        assertEquals(Color(0xFFFF8A65), categoryStyleOf("식비").color)
        assertEquals(Color(0xFF4FC3F7), categoryStyleOf("교통").color)
        assertEquals(Color(0xFFAED581), categoryStyleOf("생활").color)
        assertEquals(Color(0xFFBA68C8), categoryStyleOf("쇼핑").color)
        assertEquals(Color(0xFFFFD54F), categoryStyleOf("여가").color)
        assertEquals(Color(0xFF90A4AE), categoryStyleOf("기타").color)
        assertEquals(Color(0xFFF06292), categoryStyleOf("유동 지출").color)
        assertEquals(Color(0xFF64B5F6), categoryStyleOf("수입").color)
        assertEquals(Color(0xFF4DB6AC), categoryStyleOf("저축").color)
    }

    @Test fun unknownName_fallsBackToGrayAndFirstLetter() {
        assertEquals(CategoryStyle(Color(0xFF90A4AE), "가"), categoryStyleOf("가짜분류"))
        assertEquals("?", categoryStyleOf("").emoji)
    }

    @Test fun label_isEmojiThenName() = assertEquals("🍽️ 식비", categoryLabel("식비"))
}
```

- [ ] **Step 2: 실패 확인** — Run: `GRADLE :app:testDebugUnitTest --tests "com.householdbudget.app.ui.theme.CategoryStyleTest"` → Expected: 컴파일 실패(`categoryStyleOf` 없음).

- [ ] **Step 3: `CategoryStyle.kt` 구현** — 색은 파일 안 `private val` 맵(이름 → `Color`, 위 9개 값), 이모지는 `FixedCategories.TREE.associate { it.name to it.icon }`. 색은 데이터 구분용이라 테마 색이 아닌 고정값이다(파일 머리 주석 한 줄로 남긴다).

- [ ] **Step 4: 통과 확인** — 같은 명령 → Expected: `BUILD SUCCESSFUL`, 4 tests passed.

- [ ] **Step 5: `TransactionRow` 아바타 교체** — 함수 시그니처는 그대로 둔다(호출처 4곳: Feed, Calendar, ArchiveDetail, Stats 시트는 수정하지 않는다). `kind`에 따라 `avatarBg`/`avatarFg`를 고르던 분기를 지우고, `val style = categoryStyleOf(parentCategoryName?.takeIf { it.isNotBlank() } ?: categoryName)`으로 44dp 원 배경 `style.color.copy(alpha = 0.18f)`, 가운데 `Text(style.emoji)`(글자색 지정: `style.color` — 이모지엔 영향 없고 첫 글자 폴백에만 쓰인다). `clearAndSetSemantics { }`는 유지. 안 쓰게 된 `kindContainer`/`kindOnContainer` import와 KDoc의 "카테고리 첫 글자" 표현을 정리한다(`KindStyle.kt`의 함수 자체는 Feed·Edit에서 쓰므로 지우지 않는다).

- [ ] **Step 6: 전체 확인** — Run: `GRADLE :app:testDebugUnitTest` → Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: 커밋**

```bash
git add app/src/main/java/com/householdbudget/app/ui/theme/CategoryStyle.kt \
  app/src/main/java/com/householdbudget/app/ui/components/TransactionRow.kt \
  app/src/test/java/com/householdbudget/app/ui/theme/CategoryStyleTest.kt
git commit -m "feat: 카테고리 색·이모지를 한 곳에서 정의하고 거래 행 아바타에 적용"
```

---

### Task 2: 맵 계산 로직(순수 함수) `[Sonnet·기계적]`

**Files:**
- Create: `app/src/main/java/com/householdbudget/app/ui/stats/MapLogic.kt`
- Modify: `app/src/main/java/com/householdbudget/app/ui/stats/StatsScreen.kt:258` (`private data class ParentSpend` → `internal data class ParentSpend`, 그 한 단어만)
- Test: `app/src/test/java/com/householdbudget/app/ui/stats/MapLogicTest.kt`

**Interfaces:**
- Consumes: `ParentSpend(parentId: Long, name: String, currentMinor: Long, previousMinor: Long)` (StatsScreen.kt).
- Produces (패키지 `com.householdbudget.app.ui.stats`, 모두 `internal`, Compose import 없음):
  - `fun deltaPercent(currentMinor: Long, previousMinor: Long): Int?` — 반올림한 전월 대비 %. `previousMinor <= 0`이거나 `|%| < 5`면 `null`(화면에서 "—").
  - `enum class BarPart { SPENT, SAVINGS, LEFTOVER, OVER }`, `data class BarSegment(val part: BarPart, val weightMinor: Long)`
  - `data class MapSummary(val incomeMinor: Long, val spentMinor: Long, val savingsMinor: Long)` + 멤버:
    - `val hasIncome: Boolean` (`incomeMinor > 0`)
    - `val spentPercent: Int` (지출/수입 반올림 %, 수입 없으면 0)
    - `val leftoverMinor: Long` (`income − spent − savings`, 음수면 초과)
    - `val isOver: Boolean` (`leftoverMinor < 0`)
    - `fun barSegments(): List<BarSegment>` — 수입 없으면 빈 목록. 있으면 순서대로 SPENT(지출), SAVINGS(저축), 그리고 LEFTOVER(남은 돈) 또는 OVER(`−leftoverMinor`). `weightMinor <= 0`인 조각은 넣지 않는다.
  - `data class MapRow(val key: Long, val name: String, val amountMinor: Long, val sharePercent: Int, val deltaPercent: Int?, val barFraction: Float)`
  - `fun buildMapRows(parents: List<ParentSpend>): List<MapRow>` — `currentMinor > 0`만, 금액 내림차순. `sharePercent` = 전체 대비 반올림 %, `barFraction` = 최댓값 대비(0f..1f), `key` = `parentId`.
  - `enum class TileTier { BIG, MID, SMALL }`, `fun tileTier(widthDp: Float, heightDp: Float): TileTier` — BIG: 가로 ≥ 80 그리고 세로 ≥ 64. MID: 가로 ≥ 54 그리고 세로 ≥ 40. 그 외 SMALL. (간격을 빼기 전 타일 크기 기준, 시안 값)

- [ ] **Step 1: 실패하는 테스트 작성** — `MapLogicTest`(가짜 금액은 시안의 예시 값):

```kotlin
class MapLogicTest {
    private val parents = listOf(
        ParentSpend(1, "식비", 480_000, 420_000), ParentSpend(2, "쇼핑", 208_000, 150_000),
        ParentSpend(3, "유동 지출", 150_000, 150_000), ParentSpend(4, "생활", 129_000, 140_000),
        ParentSpend(5, "여가", 106_000, 60_000), ParentSpend(6, "교통", 99_000, 120_000),
        ParentSpend(7, "기타", 22_000, 30_000),
    )

    @Test fun delta_roundsAndTreatsUnder5PercentAsFlat() {
        assertEquals(14, deltaPercent(480_000, 420_000))
        assertEquals(-8, deltaPercent(129_000, 140_000))
        assertEquals(5, deltaPercent(105_000, 100_000))
        assertEquals(-5, deltaPercent(95_000, 100_000))
        assertNull(deltaPercent(104_000, 100_000))
        assertNull(deltaPercent(96_000, 100_000))
        assertNull(deltaPercent(150_000, 150_000))
    }

    @Test fun delta_previousZero_isFlat() {
        assertNull(deltaPercent(50_000, 0)); assertNull(deltaPercent(0, 0)); assertNull(deltaPercent(50_000, -1))
    }

    @Test fun summary_normalMonth() {
        val s = MapSummary(incomeMinor = 3_000_000, spentMinor = 1_194_000, savingsMinor = 700_000)
        assertTrue(s.hasIncome); assertEquals(40, s.spentPercent)
        assertEquals(1_106_000, s.leftoverMinor); assertFalse(s.isOver)
        assertEquals(
            listOf(BarSegment(BarPart.SPENT, 1_194_000), BarSegment(BarPart.SAVINGS, 700_000), BarSegment(BarPart.LEFTOVER, 1_106_000)),
            s.barSegments(),
        )
    }

    @Test fun noIncome_hidesRatioAndBar() {
        val s = MapSummary(incomeMinor = 0, spentMinor = 300_000, savingsMinor = 100_000)
        assertFalse(s.hasIncome); assertEquals(0, s.spentPercent); assertTrue(s.barSegments().isEmpty())
    }

    @Test fun overIncome_showsOverSegment() {
        val s = MapSummary(incomeMinor = 1_000_000, spentMinor = 900_000, savingsMinor = 300_000)
        assertTrue(s.isOver); assertEquals(-200_000, s.leftoverMinor); assertEquals(90, s.spentPercent)
        assertEquals(BarSegment(BarPart.OVER, 200_000), s.barSegments().last())
        assertTrue(s.barSegments().all { it.weightMinor > 0 })
    }

    @Test fun zeroParts_areDropped() {
        assertEquals(listOf(BarPart.SPENT, BarPart.LEFTOVER),
            MapSummary(1_000_000, 400_000, 0).barSegments().map { it.part })
        assertEquals(listOf(BarPart.SPENT, BarPart.SAVINGS),
            MapSummary(1_000_000, 600_000, 400_000).barSegments().map { it.part })
        assertEquals(listOf(BarPart.LEFTOVER), MapSummary(1_000_000, 0, 0).barSegments().map { it.part })
    }

    @Test fun rows_sortedWithShareDeltaAndBar() {
        val rows = buildMapRows(parents.shuffled(java.util.Random(1)))
        assertEquals(listOf("식비", "쇼핑", "유동 지출", "생활", "여가", "교통", "기타"), rows.map { it.name })
        assertEquals(listOf(40, 17, 13, 11, 9, 8, 2), rows.map { it.sharePercent })
        assertEquals(listOf(14, 39, null, -8, 77, -17, -27), rows.map { it.deltaPercent })
        assertEquals(1f, rows.first().barFraction, 0.0001f)
        assertEquals(208_000f / 480_000f, rows[1].barFraction, 0.0001f)
        assertEquals(1L, rows.first().key)
    }

    @Test fun rows_totalEqualsSummarySpent_andDropsNonPositive() {
        val rows = buildMapRows(parents + ParentSpend(8, "가짜분류", 0, 10_000))
        assertEquals(7, rows.size)
        assertEquals(1_194_000, rows.sumOf { it.amountMinor })
        assertTrue(buildMapRows(emptyList()).isEmpty())
    }

    @Test fun tileTier_thresholds() {
        assertEquals(TileTier.BIG, tileTier(80f, 64f))
        assertEquals(TileTier.MID, tileTier(79.9f, 64f))
        assertEquals(TileTier.MID, tileTier(54f, 40f))
        assertEquals(TileTier.SMALL, tileTier(53.9f, 40f))
        assertEquals(TileTier.SMALL, tileTier(200f, 39.9f))
    }
}
```

- [ ] **Step 2: 실패 확인** — Run: `GRADLE :app:testDebugUnitTest --tests "com.householdbudget.app.ui.stats.MapLogicTest"` → Expected: 컴파일 실패(`deltaPercent` 없음).

- [ ] **Step 3: `ParentSpend`를 `internal`로 바꾸고 `MapLogic.kt` 구현** — 반올림은 `kotlin.math.roundToInt`(Double 계산). 다른 것은 시그니처와 테스트가 정한다.

- [ ] **Step 4: 통과 확인** — 같은 명령 → Expected: `BUILD SUCCESSFUL`, 9 tests passed.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/householdbudget/app/ui/stats/MapLogic.kt \
  app/src/main/java/com/householdbudget/app/ui/stats/StatsScreen.kt \
  app/src/test/java/com/householdbudget/app/ui/stats/MapLogicTest.kt
git commit -m "feat: 통계 맵의 요약·전월 대비·순위 계산을 순수 함수로 추가"
```

---

### Task 3: 맵 화면 교체(요약 + 트리맵 + 순위 목록) `[Opus·까다로움]`

**Files:**
- Modify: `app/src/main/java/com/householdbudget/app/ui/stats/MoneyMap.kt` (타일 타입·그리기 교체, `squarifiedLayout`/`worstAspect`/`compactAmountLabel`은 유지)
- Create: `app/src/main/java/com/householdbudget/app/ui/stats/MapSections.kt`
- Modify: `app/src/main/java/com/householdbudget/app/ui/stats/StatsScreen.kt` (상수 `:83-96`, 맵 모드 조립 `:119-129`·`:186-229`, `MapData`/`deltaColor`/`buildMapTiles`/`sheetForTileKey` `:349-471`, `DeltaLabel` `:979-1015`, `MapLegendRow` `:1017-1052`)
- Modify: `app/src/main/res/values/strings_stats.xml`

**Interfaces:**
- Consumes: Task 1 `categoryStyleOf`, `categoryLabel`, `CategoryDot`. Task 2 `MapSummary`, `BarPart`, `BarSegment`, `MapRow`, `buildMapRows`, `TileTier`, `tileTier`, `deltaPercent`.
- Produces:
  - `@Composable fun MoneyMap(rows: List<MapRow>, onTileClick: (Long) -> Unit, modifier: Modifier = Modifier)` — `MapTile`, `MergedTiles`, `mergeSmallTiles`는 삭제. `squarifiedLayout`은 `List<MapRow>`를 받도록 타입만 바꾼다.
  - `@Composable internal fun MapSummaryCard(month: Int, summary: MapSummary, modifier: Modifier = Modifier)`
  - `@Composable internal fun MapRankList(rows: List<MapRow>, onRowClick: (Long) -> Unit, modifier: Modifier = Modifier)`
  - `@Composable internal fun DeltaLabel(deltaPercent: Int?, modifier: Modifier = Modifier)` — StatsScreen.kt의 기존 `DeltaLabel(currentMinor, previousMinor)`를 이 시그니처로 바꾼다. 색 규칙은 그대로(▲ `colorScheme.error`, ▼ `colorScheme.primary`, "—" `onSurfaceVariant`), 문구는 기존 `stats_delta_up/down/flat`. 리포트 카드의 호출부는 `DeltaLabel(deltaPercent(parent.currentMinor, parent.previousMinor))`로 바꾼다 → 리포트도 ±5% 미만은 "—"가 된다(맵과 같은 규칙 하나만 둔다).

**결정 사항(스펙이 빌더에게 맡긴 것 포함):**
- 맵 모드의 LazyColumn item 3개, 순서대로 `key = "map_summary"`, `"map"`, `"map_rank"`. 전부 기존 카드 모양(`Surface` + `MaterialTheme.shapes.extraLarge` + `colorScheme.surface` + `cardItemModifier()`). 순위 목록은 item 하나의 패널(행 최대 7개).
- 지출 대분류가 하나도 없으면(`buildMapRows` 결과가 빈 목록) 기존 `EmptyState`("map_empty")만 보인다 — 지금 동작 유지.
- **요약 카드**: 라벨 `stats_map_summary_title`(월 = 헤더와 같은 `period.startInclusive.monthValue`), 큰 숫자 `spentMinor.formatWon()`. `summary.hasIncome`일 때만 둘째 줄과 막대·범례를 그린다. 둘째 줄은 ` · `로 이은 세 조각: `stats_map_summary_income`(수입 `compactAmountLabel`, `spentPercent`), `stats_map_summary_savings`, 그리고 `isOver`면 `stats_map_summary_over`(`colorScheme.error`), 아니면 `stats_map_summary_leftover`. 금액은 모두 `compactAmountLabel`.
- **막대**: 높이 14dp, 모서리 7dp, `summary.barSegments()`를 `Row` + `weight(weightMinor.toFloat())`로. 색 — SPENT `Color(0xFFFF8A65)`, SAVINGS `categoryStyleOf("저축").color`, LEFTOVER `colorScheme.surfaceContainerHighest`, OVER `colorScheme.error`. 범례는 막대에 실제로 있는 조각만(색 네모 + `stats_map_legend_spent` / `stats_map_savings` / `stats_map_leftover` / `stats_map_overspent`).
- **트리맵**: 카드 안 높이 230dp, 패딩 `Space.md`. 타일 = 배치 사각형을 사방 3dp씩 줄인 것(간격 6dp), 모서리 14dp, 채움 `style.color.copy(alpha = 0.15f)`, 테두리 1.5dp `style.color.copy(alpha = 0.5f)`, 안쪽 패딩 8dp. 단계는 `tileTier(배치 사각형 가로 dp, 세로 dp)`:
  - BIG: 이모지 / 이름 / 금액(굵게) / `"{sharePercent}%"`(`onSurfaceVariant`) + 전월 대비(▲ error, ▼ primary, "—" onSurfaceVariant).
  - MID: 이모지 / 이름 / 금액(굵게). 한 단계 작은 글씨.
  - SMALL: 이모지만. 줄인 타일의 짧은 변이 이모지 한 글자보다 작으면 아무것도 그리지 않는다.
  - 글자는 전부 한 줄 + 말줄임(`measureSingleLine` 재사용), 다음 줄이 타일 높이를 넘으면 그 줄부터 생략한다(기존 금액 줄 처리와 같은 방식). 글자색은 `colorScheme.onSurface`. 글씨 크기는 `MaterialTheme.typography`에서 고른다(시안: BIG 16/12/16/10sp, MID 13/10/12sp).
  - 배치·글자 측정은 지금처럼 `remember(rows, canvasSize, …)`로 한 번만 한다. 탭은 기존 `pointerInput` + `rect.contains` 방식 그대로, `onTileClick(row.key)`.
  - `contentDescription`: 금액 내림차순으로 `, ` 연결. 타일마다 `stats_map_tile_desc_up/down/flat`.
- **순위 목록**: 행 = `CategoryDot(style.color)` + `categoryLabel(name)` 아래 4dp 막대(트랙 `surfaceContainerHigh`, 채움 `style.color`, 폭 `barFraction`) + 오른쪽에 `compactAmountLabel(amountMinor)`(굵게) 아래 `DeltaLabel(row.deltaPercent)`. 행 전체가 `clickable`, 최소 높이 48dp.
- **탭 동작**: `sheetForTileKey`를 대분류 분기만 남겨 `private fun sheetForParent(key: Long, stats: PeriodStats, rows: List<TransactionWithCategoryRow>): SheetData`로 줄인다(제목 = 대분류 이름, 행 = `expenseRowsOfParent`). 타일과 목록 행 모두 `sheet = sheetForParent(...)`. `TransactionsSheet`는 그대로.
- **삭제**: `SAVINGS_TILE_KEY`, `OTHER_TILE_KEY`, `MapDeepGreen`~`MapOnDelta` 10개, `MapData`, `deltaColor`, `buildMapTiles`, `MapLegendRow`, StatsScreen의 `otherLabel`/`leftoverLabel`/`overspentLabel`/`savingsLabel`/`mapData`, 안 쓰게 된 import. 문자열 `stats_map_other`, `stats_legend_less`, `stats_legend_same`, `stats_legend_more`.
- **문자열 추가**(`strings_stats.xml`의 "머니 맵" 구역):

```xml
<string name="stats_map_summary_title">%1$d월 지출</string>
<string name="stats_map_summary_income">수입 %1$s 중 %2$d%%</string>
<string name="stats_map_summary_savings">저축 %1$s</string>
<string name="stats_map_summary_leftover">남은 돈 %1$s</string>
<string name="stats_map_summary_over">초과 %1$s</string>
<string name="stats_map_legend_spent">지출</string>
<string name="stats_map_tile_desc_up">%1$s %2$s, 지난달보다 %3$d%% 증가</string>
<string name="stats_map_tile_desc_down">%1$s %2$s, 지난달보다 %3$d%% 감소</string>
<string name="stats_map_tile_desc_flat">%1$s %2$s</string>
```

- [ ] **Step 1: 문자열 추가·삭제** (위 목록).
- [ ] **Step 2: `DeltaLabel` 시그니처 교체 + 리포트 호출부 수정** — Run: `GRADLE :app:compileDebugKotlin` → `BUILD SUCCESSFUL`.
- [ ] **Step 3: `MoneyMap.kt` 교체** — 타입 삭제, `squarifiedLayout` 입력 타입 변경, 새 타일 그리기와 `contentDescription`. 파일 머리 주석("색은 전월 대비 증감 구간")을 새 규칙(카테고리 색)으로 고친다.
- [ ] **Step 4: `MapSections.kt` 작성** — `MapSummaryCard`, `MapRankList`.
- [ ] **Step 5: `StatsScreen.kt` 맵 모드 조립 + 옛 코드 삭제** — `val mapRows = remember(stats) { stats?.let { buildMapRows(it.parents) }.orEmpty() }`, `val summary = MapSummary(currentStats.incomeMinor, currentStats.expenseMinor, currentStats.savingsMinor)`.
- [ ] **Step 6: 확인** — Run: `GRADLE :app:testDebugUnitTest :app:assembleDebug` → Expected: `BUILD SUCCESSFUL`. Run: `grep -n "MapTile\|mergeSmallTiles\|MapLegendRow\|deltaColor\|stats_legend_\|stats_map_other" -r app/src` → Expected: 출력 없음.
- [ ] **Step 7: 커밋**

```bash
git add app/src/main/java/com/householdbudget/app/ui/stats/MoneyMap.kt \
  app/src/main/java/com/householdbudget/app/ui/stats/MapSections.kt \
  app/src/main/java/com/householdbudget/app/ui/stats/StatsScreen.kt \
  app/src/main/res/values/strings_stats.xml
git commit -m "feat: 통계 맵을 요약·카테고리 색 트리맵·순위 목록으로 교체"
```

---

### Task 4: 리포트 카드·카테고리 선택·예산 설정에 스타일 적용 `[Sonnet·기계적]`

**Files:**
- Modify: `app/src/main/java/com/householdbudget/app/ui/stats/StatsScreen.kt` (`CategoryBudgetRow`: 이름 `Text`, 예산 막대의 `colorScheme.primary` 두 곳)
- Modify: `app/src/main/java/com/householdbudget/app/ui/edit/EditTransactionScreen.kt:453-467` (대분류 `FilterChip`), `:652-659` (검색 시트의 대분류 제목)
- Modify: `app/src/main/java/com/householdbudget/app/ui/autoentry/PendingReviewScreen.kt:154` (대분류 제목)
- Modify: `app/src/main/java/com/householdbudget/app/ui/settings/BudgetSettingsScreen.kt:270-272` (대분류 이름)

**Interfaces:**
- Consumes: Task 1 `categoryStyleOf(parentName)`, `categoryLabel(parentName)`, `CategoryDot(color)`.
- Produces: 없음(화면 표시만).

- [ ] **Step 1: `CategoryBudgetRow`** — 이름을 `categoryLabel(parent.name)`으로, 예산 막대의 채움색 `colorScheme.primary` 두 곳을 `categoryStyleOf(parent.name).color`로. 초과 구간의 `colorScheme.error`는 그대로.
- [ ] **Step 2: `EditTransactionScreen`** — 대분류 `FilterChip`: `label = { Text(categoryLabel(parent.name)) }`, `leadingIcon = { CategoryDot(categoryStyleOf(parent.name).color) }`. 검색 시트의 대분류 제목 `Text`: `categoryLabel(parent.name)`. 소분류 칩·최근 칩·검색 필터(`parent.name.contains`)는 건드리지 않는다.
- [ ] **Step 3: `PendingReviewScreen`** — 대분류 제목을 `Row(verticalAlignment = CenterVertically, spacedBy(Space.sm)) { CategoryDot(색); Text(categoryLabel(parent.name), …기존 스타일) }`로.
- [ ] **Step 4: `BudgetSettingsScreen`** — 목록의 `text = parent.name`을 `categoryLabel(parent.name)`으로. `BudgetSheetTarget.Category(parent.id, parent.name)`(시트 제목)은 그대로.
- [ ] **Step 5: 확인** — Run: `GRADLE :core:test :app:testDebugUnitTest :app:assembleDebug` → Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 6: 커밋**

```bash
git add app/src/main/java/com/householdbudget/app/ui/stats/StatsScreen.kt \
  app/src/main/java/com/householdbudget/app/ui/edit/EditTransactionScreen.kt \
  app/src/main/java/com/householdbudget/app/ui/autoentry/PendingReviewScreen.kt \
  app/src/main/java/com/householdbudget/app/ui/settings/BudgetSettingsScreen.kt
git commit -m "feat: 리포트 카드·카테고리 선택·예산 설정에 카테고리 색과 이모지 표시"
```

---

### Task 5: 에뮬레이터 스크린샷 확인 `[Sonnet·기계적]`

**Files:** 없음(확인만. 고칠 것이 나오면 해당 태스크의 파일을 고치고 `fix:` 커밋). 스크린샷은 저장소 밖 임시 폴더에 두고 커밋하지 않는다.

**Interfaces:**
- Consumes: Task 1~4 전체.

- [ ] **Step 1: 준비** — `~/Library/Android/sdk/emulator/emulator -avd test35 -no-snapshot-save &` 후 `$ADB wait-for-device`. Run: `GRADLE :app:installDebug` → `BUILD SUCCESSFUL`. `$ADB shell monkey -p com.householdbudget.app 1`.
- [ ] **Step 2: 가짜 거래 넣기** — 앱의 + 버튼(거래 입력 화면)으로 가짜 거래를 넣는다: 수입 `3,000,000`(수입 > 월급), 저축 `700,000`, 지출은 대분류 4개 이상에 금액을 다르게(예: 식비 `480,000`, 쇼핑 `208,000`, 교통 `99,000`, 기타 `22,000`). 좌표는 `$ADB shell uiautomator dump`로 찾는다. 이때 대분류 칩에 이모지와 색 점이 보이는지 스크린샷으로 확인.
- [ ] **Step 3: 통계 > 맵** — `$ADB exec-out screencap -p > <임시폴더>/map.png`. 확인: (a) 요약의 지출 합계 = 순위 목록 금액의 합, (b) "수입 300만 중 27% · 저축 70만 · 남은 돈 149만"과 3색 막대, (c) 트리맵에 저축·남은 돈 타일이 없고 타일 색이 순위 목록의 점 색과 같다, (d) 작은 타일(기타)은 글자가 넘치지 않는다, (e) 옛 범례 행이 없다. 타일 하나와 목록 행 하나를 눌러 그 대분류의 거래 시트가 열리는지 확인.
- [ ] **Step 4: 다른 화면** — 홈(피드) 거래 행, 통계 > 리포트의 카테고리 카드, 설정 > 예산, 달력 상세를 각각 찍는다. 확인: 같은 대분류가 모든 화면에서 같은 이모지·같은 색이다(스펙의 실패 조건).
- [ ] **Step 5: 경계 한 가지** — 수입 거래를 지우고 맵을 다시 찍는다 → 지출 합계만 보이고 비율 문구·막대가 없다. (확인 뒤 되돌릴 필요 없음.)
- [ ] **Step 6: 결과 보고** — 화면별 통과/실패와 스크린샷 경로를 조정자에게 알린다. 실패가 있으면 고치고 `GRADLE :app:testDebugUnitTest` 통과 뒤 `fix:` 커밋.

---

## Self-Review 결과

- **스펙 대응**: 상단 요약(수입 0·초과 포함) → Task 2·3. 트리맵 스타일·크기 단계·범례 삭제 → Task 2·3. 순위 목록 → Task 2·3. 탭 → Task 3(`sheetForParent`). 거래 행 → Task 1. 리포트 카드·선택 UI·예산 설정 → Task 4. 스타일 출처·조회 키·폴백 → Task 1. 옛 5단계 색·"기타로 합치기" 삭제 → Task 3. 접근성 문구 → Task 3. 검증(JVM 테스트, 매핑 테스트, 에뮬레이터) → Task 1·2·5.
- **스펙의 "1.5% 미만은 이모지만"**: 별도 비율 검사 없이 크기 단계(`tileTier`)로 처리한다. 폰 폭(약 300×206dp 지도)에서 1.5% 타일은 약 930dp²라 MID 기준(54×40 = 2,160dp²)에 못 미쳐 SMALL이 된다.
- **스펙과 다르게 정한 것 1가지**: 리포트의 전월 대비도 ±5% 미만을 "—"로 표시한다(기존엔 1%부터 ▲▼). 맵과 리포트가 같은 카테고리에 다른 표기를 보이지 않게 하기 위해서다.
- **시안과 스펙이 다른 곳**: 시안은 쇼핑 🛍️·유동 지출 🎈, 스펙은 "`FixedCategories.TREE`의 이모지를 그대로"(🛒·💸). 계획은 스펙을 따른다.
