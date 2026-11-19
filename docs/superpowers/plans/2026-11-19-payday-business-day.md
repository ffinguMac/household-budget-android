# 월급일 영업일 보정 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 설정한 월급일이 주말·한국 공휴일이면 그 앞 평일로 당겨서, 회계월 경계가 실제 월급 입금일과 맞게 한다.

**Architecture:** `core` 모듈에 2024–2030년 한국 공휴일 표(`KoreanHolidays`)를 코드로 두고, `PeriodResolver.paydayInMonth`가 "말일 클램프 → 주말·공휴일이면 하루씩 앞으로" 보정한 날짜를 돌려주게 바꾼다. 회계월은 명목 월(`YearMonth`)을 키로 계산하고(`periodContaining`은 다음 달·이번 달·전달 순으로 보정 월급일을 본다), `previousPeriod`/`nextPeriod`는 `periodContaining`에 위임한다. 홈·내역·통계·아카이브는 이미 `PeriodResolver`만 쓰므로 호출부는 고치지 않고, 설정 화면에 안내 문구만 더한다.

**Tech Stack:** Kotlin 2.0.21, `java.time`, JUnit4(`core`·`app` 단위 테스트), Compose(설정 화면).

**Spec:** `docs/superpowers/specs/2026-11-19-payday-business-day-design.md` — 각 태스크 구현자는 자기 태스크 + 이 Global Constraints + 스펙을 함께 읽는다.

## Global Constraints

- 작업 위치는 워크트리 `/Users/ffingumac/orca/household-budget-payday`(브랜치 `feat/payday-business-day`)뿐이다. `/Users/ffingumac/orca/household-budget-android`는 건드리지 않는다.
- 신규 의존성을 추가하지 않는다(운영·테스트 모두). 네트워크 호출을 추가하지 않는다.
- Logcat/`println` 로그를 추가하지 않는다.
- UI 문구는 한국어, `app/src/main/res/values/strings_*.xml`에 둔다(코드에 문자열 하드코딩 금지).
- 저장소의 `.xlsx/.xls/.numbers/.csv` 파일은 열거나 읽거나 `git add` 하지 않는다. `git add`는 항상 파일 경로를 지정한다(`git add -A`, `git add .` 금지).
- `core/bin/` 아래 파일은 IDE가 남긴 복사본이다. 고치지도 `git add` 하지도 않는다. 진짜 소스는 `core/src/`다.
- 기존 거래·이미 저장된 아카이브 스냅샷은 지우거나 고치지 않는다. DB 스키마·마이그레이션 변경 없음.
- 비목표(만들지 않는다): 다음 영업일로 미루기, 보정 on/off 토글, 공휴일을 인터넷에서 받아오기, 공휴일을 규칙으로 계산하는 코드(표만 둔다).
- 공휴일 날짜는 이 문서 Task 1의 표를 그대로 옮긴다. 구현자가 날짜를 더하거나 빼거나 추측하지 않는다.
- 커밋 메시지는 한국어(`feat: …`, `test: …`), 태스크 하나에 커밋 하나. 마지막 줄에 그 태스크를 실제로 돌린 모델 이름으로 트레일러를 붙인다: `Co-Authored-By: Claude <모델 이름> <noreply@anthropic.com>`.
- 빌드·테스트 명령(저장소에 `./gradlew` 없음, 워크트리 루트에서 실행). 아래에서 `GRADLE`은 이 줄 전체를 뜻한다:
  `export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=~/Library/Android/sdk; ~/.gradle/wrapper/dists/gradle-8.10.2-bin/a04bxjujx95o3nb99gddekhwo/gradle-8.10.2/bin/gradle`
- 모델 배정: 각 태스크 제목의 `[Sonnet·기계적]` / `[Opus·까다로움]`을 따른다.

## Review Focus

스펙이 직접 테스트를 요구하지 않지만 실제 사용에서 먼저 터질 입력들. 각 줄의 테스트는 괄호 안 태스크에 들어 있다.

1. **기준일이 "당겨진 날 ~ 명목 월급일 사이"** (예: 월급일 21, 오늘 2026-11-21 토요일): 이미 새 회계월(11-20 시작)에 속해야 한다. 옛 코드처럼 `YearMonth.from(오늘)`의 명목일과만 비교하면 맞지만, 월급일 1일처럼 보정일이 앞 달로 넘어가면(오늘 2026-12-31) 다음 달 명목 월을 봐야만 맞는다. (Task 2 `periodContaining_betweenAdjustedAndNominal…`, `periodContaining_payday1_lastDayOfDecember…`)
2. **표 밖 연도**(2023년 이전, 2031년 이후): 예외 없이 주말만 보정한다. (Task 1 `outsideTable_isNeverHoliday`, Task 2 `paydayInMonth_yearOutsideTable_weekendOnly`)
3. **앱 업데이트 직후 아카이브**: 옛 규칙으로 저장된 "마지막으로 본 회계월 시작일"(예: 2026-11-21)보다 새 규칙의 현재 시작일(11-20)이 하루 이르다. `BudgetRepository.tryArchiveCompletedPeriods`는 `candidate.startInclusive < lastSeen`이면 건너뛰므로, 직전 회계월 시작(10-21)이 옛 시작일보다 앞이면 중복 보관이 생기지 않는다. (Task 2 `upgradeMidPeriod_previousStartsBeforeOldRuleStart`)
4. **긴 연휴가 월급일을 일주일 당기는 경우**(월급일 9, 2025-10 → 10-02): 앞뒤 회계월이 뒤집히거나 길이가 0이 되면 안 된다. (Task 2 `paydayInMonth_holidayRunPlusWeekend`, 속성 테스트의 `start < end`)
5. **월급일 29–31 × 모든 달**: 클램프 뒤 보정에서 예외·빈틈이 없어야 한다. (Task 2 속성 테스트가 `dom` 1..31 전부를 돈다)

---

## File Structure

| 파일 | 역할 |
|---|---|
| Create `core/src/main/kotlin/com/householdbudget/app/domain/KoreanHolidays.kt` | 2024–2030 한국 공휴일 날짜 표. 날짜 조회만 한다. |
| Create `core/src/test/kotlin/com/householdbudget/app/domain/KoreanHolidaysTest.kt` | 연도별 개수·대표 날짜 테스트 |
| Modify `core/src/main/kotlin/com/householdbudget/app/domain/PeriodResolver.kt` | 보정된 월급일, 명목 월 기준 회계월 |
| Modify `core/src/test/kotlin/com/householdbudget/app/domain/PeriodResolverTest.kt` | 기존 테스트 3개 기대값 수정 + 새 테스트 |
| Modify `app/src/main/java/com/householdbudget/app/ui/util/Formatters.kt` | `"M월 d일(E)"` 포매터 하나 추가 |
| Create `app/src/test/java/com/householdbudget/app/ui/util/FormattersTest.kt` | 포매터 테스트 |
| Modify `app/src/main/res/values/strings_more.xml` | 설정 문구 |
| Modify `app/src/main/java/com/householdbudget/app/ui/settings/SettingsScreen.kt` | 설정 행 부제 + 바텀시트 안내 한 줄 |

호출부(`BudgetRepository`, `BudgetViewModel`, `StatsViewModel`)는 `periodContaining`/`previousPeriod` 시그니처가 그대로라 수정하지 않는다. `FeedScreen`의 월급날 배지는 `period.startInclusive`를 보므로 자동으로 맞는다.

---

### Task 1: 한국 공휴일 표 `KoreanHolidays` [Sonnet·기계적]

**Files:**
- Create: `core/src/main/kotlin/com/householdbudget/app/domain/KoreanHolidays.kt`
- Test: `core/src/test/kotlin/com/householdbudget/app/domain/KoreanHolidaysTest.kt`

**Interfaces:**
- Consumes: 없음
- Produces (Task 2가 쓴다):
  ```kotlin
  package com.householdbudget.app.domain

  object KoreanHolidays {
      /** 2024–2030년 관공서 공휴일(주말과 겹친 날 포함). 표 밖 연도는 비어 있다. */
      val dates: Set<LocalDate>
      fun isHoliday(date: LocalDate): Boolean   // date in dates
  }
  ```

**공휴일 데이터 (그대로 옮긴다. 총 141일)**

출처와 확인 상태:
- 2024–2027: 한국천문연구원(KASI) 달력자료(`astro.kasi.re.kr/life/post/calendarData?year=YYYY`)와 kholidayz.com(인사혁신처 고시 기반이라고 밝힘) 두 곳이 일치. 2027년은 우주항공청 「2027년 월력요항」(2026-06-29 발표, 국경일·대체공휴일 등 24일) 보도와 개수 일치. 임시공휴일·선거일(2024-10-01, 2025-01-27, 2025-06-03, 2026-06-03)은 이미 지난 확정일. → **VERIFIED**
- 노동절(05-01)·제헌절(07-17)은 2026년부터 공휴일(인사혁신처 2026-04-28 「관공서의 공휴일에 관한 규정」 개정 보도자료, 대체공휴일 적용). 그래서 2024·2025년 표에는 없고 2026년부터 있다. → **VERIFIED**
- 2028: KASI 달력자료(공식 발표 전 자료라고 명시됨)와 kholidayz.com 일치, 대체공휴일 규칙으로 계산한 결과와도 일치. 공식 「2028년 월력요항」은 2027년 중반 발표 예정이라 아직 없음. 2028-04-12(제23대 국회의원선거)는 KASI 표에 실려 있으나 선거일 공고 전. → **UNVERIFIED (공식 발표 전, 2개 출처 + 규칙 일치)**
- 2029·2030: KASI에 아직 자료 없음. 음력 기준일(2029 설날 02-13·부처님오신날 05-20·추석 09-22, 2030 설날 02-03·부처님오신날 05-09·추석 09-12)은 kholidayz.com·datedb.net·dallyeok.com에서 일치. 대체공휴일은 아래 규칙으로 계산했고 kholidayz.com과 일치. → **UNVERIFIED (공식 발표 전, 계산값)**
- 적용한 대체공휴일 규칙(「관공서의 공휴일에 관한 규정」 제3조, 2026-04 개정 반영): ① 설날·추석 연휴(전날·당일·다음날)가 **일요일** 또는 다른 공휴일과 겹치면, ② 3·1절·노동절·어린이날·부처님오신날·제헌절·광복절·개천절·한글날·기독탄신일이 **토요일·일요일** 또는 다른 공휴일과 겹치면 → 그 공휴일 다음의 첫 번째 비공휴일(토·일 제외)이 대체공휴일. 1월 1일·현충일은 대체공휴일 없음(예: 2028-01-01 토요일은 대체 없음).
- **표에 넣지 않은 날(UNVERIFIED, 넣지 말 것):** 2030년 대통령선거일·전국동시지방선거일. 공직선거법 제34조로 계산하면 각각 2030-03-27(수)·2030-06-12(수)로 추정되지만 공고 전이고 출처로 확인하지 못했다. 앞으로의 임시공휴일도 당연히 없다. 이런 날은 앱 업데이트로 추가한다.

```
2024 (19일)
2024-01-01 신정
2024-02-09 설날 연휴
2024-02-10 설날
2024-02-11 설날 연휴
2024-02-12 대체공휴일(설날)
2024-03-01 3·1절
2024-04-10 제22대 국회의원선거
2024-05-05 어린이날
2024-05-06 대체공휴일(어린이날)
2024-05-15 부처님오신날
2024-06-06 현충일
2024-08-15 광복절
2024-09-16 추석 연휴
2024-09-17 추석
2024-09-18 추석 연휴
2024-10-01 임시공휴일(국군의 날)
2024-10-03 개천절
2024-10-09 한글날
2024-12-25 기독탄신일
```

```
2025 (19일)
2025-01-01 신정
2025-01-27 임시공휴일
2025-01-28 설날 연휴
2025-01-29 설날
2025-01-30 설날 연휴
2025-03-01 3·1절
2025-03-03 대체공휴일(3·1절)
2025-05-05 어린이날·부처님오신날
2025-05-06 대체공휴일(부처님오신날)
2025-06-03 제21대 대통령선거
2025-06-06 현충일
2025-08-15 광복절
2025-10-03 개천절
2025-10-05 추석 연휴
2025-10-06 추석
2025-10-07 추석 연휴
2025-10-08 대체공휴일(추석)
2025-10-09 한글날
2025-12-25 기독탄신일
```

```
2026 (22일)
2026-01-01 신정
2026-02-16 설날 연휴
2026-02-17 설날
2026-02-18 설날 연휴
2026-03-01 3·1절
2026-03-02 대체공휴일(3·1절)
2026-05-01 노동절
2026-05-05 어린이날
2026-05-24 부처님오신날
2026-05-25 대체공휴일(부처님오신날)
2026-06-03 제9회 전국동시지방선거
2026-06-06 현충일
2026-07-17 제헌절
2026-08-15 광복절
2026-08-17 대체공휴일(광복절)
2026-09-24 추석 연휴
2026-09-25 추석
2026-09-26 추석 연휴
2026-10-03 개천절
2026-10-05 대체공휴일(개천절)
2026-10-09 한글날
2026-12-25 기독탄신일
```

```
2027 (24일)
2027-01-01 신정
2027-02-06 설날 연휴
2027-02-07 설날
2027-02-08 설날 연휴
2027-02-09 대체공휴일(설날)
2027-03-01 3·1절
2027-05-01 노동절
2027-05-03 대체공휴일(노동절)
2027-05-05 어린이날
2027-05-13 부처님오신날
2027-06-06 현충일
2027-07-17 제헌절
2027-07-19 대체공휴일(제헌절)
2027-08-15 광복절
2027-08-16 대체공휴일(광복절)
2027-09-14 추석 연휴
2027-09-15 추석
2027-09-16 추석 연휴
2027-10-03 개천절
2027-10-04 대체공휴일(개천절)
2027-10-09 한글날
2027-10-11 대체공휴일(한글날)
2027-12-25 기독탄신일
2027-12-27 대체공휴일(기독탄신일)
```

```
2028 (18일) — UNVERIFIED: 공식 월력요항 발표 전
2028-01-01 신정
2028-01-26 설날 연휴
2028-01-27 설날
2028-01-28 설날 연휴
2028-03-01 3·1절
2028-04-12 제23대 국회의원선거 (공고 전)
2028-05-01 노동절
2028-05-02 부처님오신날
2028-05-05 어린이날
2028-06-06 현충일
2028-07-17 제헌절
2028-08-15 광복절
2028-10-02 추석 연휴
2028-10-03 추석·개천절
2028-10-04 추석 연휴
2028-10-05 대체공휴일(개천절)
2028-10-09 한글날
2028-12-25 기독탄신일
```

```
2029 (20일) — UNVERIFIED: 계산값
2029-01-01 신정
2029-02-12 설날 연휴
2029-02-13 설날
2029-02-14 설날 연휴
2029-03-01 3·1절
2029-05-01 노동절
2029-05-05 어린이날
2029-05-07 대체공휴일(어린이날)
2029-05-20 부처님오신날
2029-05-21 대체공휴일(부처님오신날)
2029-06-06 현충일
2029-07-17 제헌절
2029-08-15 광복절
2029-09-21 추석 연휴
2029-09-22 추석
2029-09-23 추석 연휴
2029-09-24 대체공휴일(추석)
2029-10-03 개천절
2029-10-09 한글날
2029-12-25 기독탄신일
```

```
2030 (19일) — UNVERIFIED: 계산값, 선거일 제외
2030-01-01 신정
2030-02-02 설날 연휴
2030-02-03 설날
2030-02-04 설날 연휴
2030-02-05 대체공휴일(설날)
2030-03-01 3·1절
2030-05-01 노동절
2030-05-05 어린이날
2030-05-06 대체공휴일(어린이날)
2030-05-09 부처님오신날
2030-06-06 현충일
2030-07-17 제헌절
2030-08-15 광복절
2030-09-11 추석 연휴
2030-09-12 추석
2030-09-13 추석 연휴
2030-10-03 개천절
2030-10-09 한글날
2030-12-25 기독탄신일
```

- [ ] **Step 1: 실패하는 테스트 작성** — `KoreanHolidaysTest.kt`

```kotlin
package com.householdbudget.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class KoreanHolidaysTest {

    private fun d(iso: String) = LocalDate.parse(iso)

    @Test
    fun countPerYear_matchesPlanTable() {
        val expected = mapOf(2024 to 19, 2025 to 19, 2026 to 22, 2027 to 24, 2028 to 18, 2029 to 20, 2030 to 19)
        assertEquals(expected, KoreanHolidays.dates.groupingBy { it.year }.eachCount().toSortedMap())
        assertEquals(141, KoreanHolidays.dates.size)
    }

    @Test
    fun seollalAndChuseok_eachYear() {
        listOf(
            "2024-02-10", "2024-09-17", "2025-01-29", "2025-10-06", "2026-02-17", "2026-09-25",
            "2027-02-07", "2027-09-15", "2028-01-27", "2028-10-03", "2029-02-13", "2029-09-22",
            "2030-02-03", "2030-09-12",
        ).forEach { assertTrue(it, KoreanHolidays.isHoliday(d(it))) }
    }

    @Test
    fun substituteTemporaryAndElectionDays() {
        listOf(
            "2024-02-12", "2024-04-10", "2024-05-06", "2024-10-01",
            "2025-01-27", "2025-03-03", "2025-05-06", "2025-06-03", "2025-10-08",
            "2026-03-02", "2026-05-25", "2026-06-03", "2026-08-17", "2026-10-05",
            "2027-02-09", "2027-05-03", "2027-07-19", "2027-08-16", "2027-10-04", "2027-10-11", "2027-12-27",
            "2028-04-12", "2028-10-05",
            "2029-05-07", "2029-05-21", "2029-09-24",
            "2030-02-05", "2030-05-06",
        ).forEach { assertTrue(it, KoreanHolidays.isHoliday(d(it))) }
    }

    @Test
    fun laborDayAndConstitutionDay_onlyFrom2026() {
        listOf("2024-05-01", "2025-05-01", "2024-07-17", "2025-07-17").forEach {
            assertFalse(it, KoreanHolidays.isHoliday(d(it)))
        }
        listOf("2026-05-01", "2026-07-17", "2030-05-01", "2030-07-17").forEach {
            assertTrue(it, KoreanHolidays.isHoliday(d(it)))
        }
    }

    @Test
    fun ordinaryDaysAndUnannouncedDays_areNotHolidays() {
        // 평일, 대체 없는 2028-01-01 다음 월요일, 미확정 2030 선거 추정일
        listOf("2026-11-20", "2028-01-03", "2030-03-27", "2030-06-12").forEach {
            assertFalse(it, KoreanHolidays.isHoliday(d(it)))
        }
    }

    @Test
    fun outsideTable_isNeverHoliday() {
        listOf("2023-12-25", "2031-01-01", "2031-03-01").forEach {
            assertFalse(it, KoreanHolidays.isHoliday(d(it)))
        }
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `GRADLE :core:test --tests 'com.householdbudget.app.domain.KoreanHolidaysTest'`
Expected: 컴파일 실패 — `Unresolved reference: KoreanHolidays`

- [ ] **Step 3: `KoreanHolidays` 구현**

위 Interfaces의 시그니처대로, 위 7개 블록의 날짜를 연도별로 묶어 옮긴다(이름은 주석으로 남겨도 되고 생략해도 된다). 날짜 계산 로직은 넣지 않는다. 객체 위에 이 주석을 그대로 단다:

```kotlin
// shortcut: 2024–2030년 표만 있다(2028년 이후는 공식 발표 전 계산값, 2030년 선거일 없음).
// 표 밖 연도·임시공휴일·새 대체공휴일은 반영되지 않으니 매년 월력요항 발표 뒤 앱 업데이트로 표를 고친다.
```

- [ ] **Step 4: 통과 확인**

Run: `GRADLE :core:test --tests 'com.householdbudget.app.domain.KoreanHolidaysTest'`
Expected: BUILD SUCCESSFUL, 6 tests passed

- [ ] **Step 5: 커밋**

```bash
git add core/src/main/kotlin/com/householdbudget/app/domain/KoreanHolidays.kt \
        core/src/test/kotlin/com/householdbudget/app/domain/KoreanHolidaysTest.kt
git commit -m "feat: 2024–2030년 한국 공휴일 표 추가" -m "Co-Authored-By: Claude <모델 이름> <noreply@anthropic.com>"
```

---

### Task 2: `PeriodResolver` 영업일 보정 [Opus·까다로움]

**Files:**
- Modify: `core/src/main/kotlin/com/householdbudget/app/domain/PeriodResolver.kt` (전체)
- Test: `core/src/test/kotlin/com/householdbudget/app/domain/PeriodResolverTest.kt`

**Interfaces:**
- Consumes: `KoreanHolidays.isHoliday(date: LocalDate): Boolean` (Task 1)
- Produces (시그니처는 지금과 같다. 뜻만 바뀐다. Task 3과 기존 호출부가 쓴다):
  ```kotlin
  class PeriodResolver {
      /** 명목 월 [yearMonth]의 **보정된** 월급일. 앞 달 날짜일 수 있다(예: 2027-01, 1일 → 2026-12-31). */
      fun paydayInMonth(yearMonth: YearMonth, paydayDom: Int): LocalDate
      fun periodContaining(reference: LocalDate, paydayDom: Int): BudgetPeriod
      fun previousPeriod(period: BudgetPeriod, paydayDom: Int): BudgetPeriod
      fun nextPeriod(period: BudgetPeriod, paydayDom: Int): BudgetPeriod
  }
  ```

**규칙(이 태스크가 정하는 것)**
- `paydayInMonth`: `require(paydayDom in 1..31)` 유지 → 말일 클램프(`minOf(paydayDom, lengthOfMonth)`) → 그 날이 토·일이거나 `KoreanHolidays.isHoliday`이면 하루씩 `minusDays(1)` 반복.
- `periodContaining`: `ym = YearMonth.from(reference)`. 명목 월 `ym+1`, `ym`, `ym-1` 순서로 보면서 `paydayInMonth(m) <= reference`인 **첫** `m`을 고르고 `BudgetPeriod(paydayInMonth(m), paydayInMonth(m+1))`을 돌려준다. `YearMonth.from(보정일)`로 명목 월을 역산하지 않는다. (`ym-1`의 보정일은 항상 `reference`보다 앞이라 반드시 하나는 걸린다.)
- `previousPeriod(period) = periodContaining(period.startInclusive.minusDays(1), paydayDom)`, `nextPeriod(period) = periodContaining(period.endExclusive, paydayDom)`. 시작일에서 명목 월을 되찾는 계산을 따로 두지 않는다 — 이동이 `periodContaining`과 어긋날 수 없게 하려는 것이다.
- 클래스 KDoc의 "날짜 산술은 이 클래스에만 둔다" 문장은 유지하고, 각 함수 KDoc을 새 규칙에 맞게 고친다.

- [ ] **Step 1: 기존 테스트 3개의 기대값 수정**

2025-05-25가 일요일이라 월급일 25의 5월 보정일은 2025-05-23(금)이 된다. `PeriodResolverTest.kt`에서:

| 테스트 | 바꿀 내용 |
|---|---|
| `periodContaining_dayAfterPayday_startsThisMonthPayday` | `startInclusive` 기대값 `2025-05-25` → `2025-05-23` (끝 `2025-06-25` 그대로) |
| `periodContaining_dayBeforePayday_startsPreviousMonthPayday` | 기준일 `2025-05-24` → `2025-05-22`(두 군데), `endExclusive` 기대값 `2025-05-25` → `2025-05-23` (시작 `2025-04-25` 그대로) |
| `periodContaining_onPaydayInclusive_startsThisMonthPayday` | 기준일 `2025-05-25` → `2025-05-23`, `startInclusive` 기대값 → `2025-05-23` |

나머지 기존 테스트(`paydayInMonth_clampsDay31…` 2개, `periodContaining_payday31_januaryToFebruary`, 왕복 2개)는 그대로 둔다(해당 날짜가 모두 평일이다).

- [ ] **Step 2: 새 실패 테스트 추가** — 같은 클래스에 덧붙인다

```kotlin
    private fun d(iso: String) = LocalDate.parse(iso)
    private fun period(start: String, end: String) = BudgetPeriod(d(start), d(end))

    @Test
    fun paydayInMonth_saturday_movesToFriday() {
        assertEquals(d("2026-11-20"), resolver.paydayInMonth(YearMonth.of(2026, 11), 21)) // 11-21 토
    }

    @Test
    fun paydayInMonth_sunday_movesToFriday() {
        assertEquals(d("2027-02-19"), resolver.paydayInMonth(YearMonth.of(2027, 2), 21)) // 02-21 일
    }

    @Test
    fun paydayInMonth_weekdayNonHoliday_unchanged() {
        assertEquals(d("2026-12-21"), resolver.paydayInMonth(YearMonth.of(2026, 12), 21)) // 월
    }

    @Test
    fun paydayInMonth_chuseok_movesToPrecedingBusinessDay() {
        // 2026-09-25(금) 추석, 09-24(목) 추석 연휴 → 09-23(수)
        assertEquals(d("2026-09-23"), resolver.paydayInMonth(YearMonth.of(2026, 9), 25))
    }

    @Test
    fun paydayInMonth_holidayRunPlusWeekend() {
        // 2025-10-09 한글날 ← 10-08 대체 ← 10-05~07 추석 ← 10-04 토 ← 10-03 개천절 → 10-02(목)
        assertEquals(d("2025-10-02"), resolver.paydayInMonth(YearMonth.of(2025, 10), 9))
        // 2025-01-30 설 연휴 ← 01-27~29 ← 01-25~26 주말 → 01-24(금)
        assertEquals(d("2025-01-24"), resolver.paydayInMonth(YearMonth.of(2025, 1), 30))
    }

    @Test
    fun paydayInMonth_day1_crossesIntoPreviousMonth() {
        assertEquals(d("2026-12-31"), resolver.paydayInMonth(YearMonth.of(2027, 1), 1)) // 신정(금) → 목
        assertEquals(d("2027-12-31"), resolver.paydayInMonth(YearMonth.of(2028, 1), 1)) // 신정(토) → 금
    }

    @Test
    fun paydayInMonth_day31_clampThenAdjust() {
        assertEquals(d("2026-02-27"), resolver.paydayInMonth(YearMonth.of(2026, 2), 31)) // 02-28 토
        assertEquals(d("2026-05-29"), resolver.paydayInMonth(YearMonth.of(2026, 5), 31)) // 05-31 일
    }

    @Test
    fun paydayInMonth_yearOutsideTable_weekendOnly() {
        assertEquals(d("2023-10-03"), resolver.paydayInMonth(YearMonth.of(2023, 10), 3)) // 개천절이지만 표 밖
        assertEquals(d("2031-02-28"), resolver.paydayInMonth(YearMonth.of(2031, 3), 1))  // 03-01 토
    }

    @Test
    fun periodContaining_betweenAdjustedAndNominal_belongsToNewPeriod() {
        val expected = period("2026-11-20", "2026-12-21")
        assertEquals(expected, resolver.periodContaining(d("2026-11-20"), 21))
        assertEquals(expected, resolver.periodContaining(d("2026-11-21"), 21))
        assertEquals(period("2026-10-21", "2026-11-20"), resolver.periodContaining(d("2026-11-19"), 21))
    }

    @Test
    fun periodContaining_payday1_lastDayOfDecember_belongsToJanuaryPeriod() {
        val january = period("2026-12-31", "2027-02-01")
        assertEquals(january, resolver.periodContaining(d("2026-12-31"), 1))
        assertEquals(january, resolver.periodContaining(d("2027-01-01"), 1))
        assertEquals(period("2026-12-01", "2026-12-31"), resolver.periodContaining(d("2026-12-30"), 1))
    }

    @Test
    fun periodContaining_payday31_februaryClampAdjusted() {
        assertEquals(period("2026-02-27", "2026-03-31"), resolver.periodContaining(d("2026-02-28"), 31))
    }

    @Test
    fun previousAndNext_acrossMonthBoundaryShift() {
        val january = resolver.periodContaining(d("2027-01-15"), 1)
        assertEquals(period("2026-12-01", "2026-12-31"), resolver.previousPeriod(january, 1))
        // 2027-03-01(월) 3·1절 → 앞으로 당겨 02-26(금). 뒤로 미루지 않는다.
        assertEquals(period("2027-02-01", "2027-02-26"), resolver.nextPeriod(january, 1))
    }

    @Test
    fun upgradeMidPeriod_previousStartsBeforeOldRuleStart() {
        // 옛 규칙의 시작일(명목 2026-11-21)이 저장된 상태에서 업데이트된 경우
        val current = resolver.periodContaining(d("2026-11-22"), 21)
        assertEquals(d("2026-11-20"), current.startInclusive)
        assertTrue(resolver.previousPeriod(current, 21).startInclusive.isBefore(d("2026-11-21")))
    }

    @Test
    fun everyDay_inExactlyOnePeriod_over36Months_forEveryPayday() {
        val first = d("2025-01-01")
        val last = d("2027-12-31")
        for (dom in 1..31) {
            var prev: BudgetPeriod? = null
            var day = first
            while (!day.isAfter(last)) {
                val p = resolver.periodContaining(day, dom)
                val tag = "dom=$dom day=$day"
                assertTrue(tag, p.contains(day))
                assertTrue(tag, p.startInclusive.isBefore(p.endExclusive))
                assertTrue(tag, p.startInclusive.dayOfWeek.value <= 5)
                assertFalse(tag, KoreanHolidays.isHoliday(p.startInclusive))
                // 빈틈·겹침 없음: 하루 넘어가면 같은 구간이거나, 앞 구간의 끝 = 새 구간의 시작
                prev?.let { if (it != p) assertEquals(tag, it.endExclusive, p.startInclusive) }
                // 이동이 periodContaining과 일치
                assertEquals(tag, resolver.periodContaining(p.endExclusive, dom), resolver.nextPeriod(p, dom))
                assertEquals(tag, p, resolver.previousPeriod(resolver.nextPeriod(p, dom), dom))
                assertEquals(tag, p, resolver.nextPeriod(resolver.previousPeriod(p, dom), dom))
                prev = p
                day = day.plusDays(1)
            }
        }
    }
```

`import org.junit.Assert.assertFalse`를 추가한다.

- [ ] **Step 3: 실패 확인**

Run: `GRADLE :core:test --tests 'com.householdbudget.app.domain.PeriodResolverTest'`
Expected: FAIL — 새 테스트와 Step 1에서 고친 3개가 실패(예: `paydayInMonth_saturday_movesToFriday` expected 2026-11-20 but was 2026-11-21). 기존 `paydayInMonth_clamps…` 2개는 통과.

- [ ] **Step 4: `PeriodResolver` 구현**

위 "규칙" 절대로 구현한다. 명목일 계산과 영업일 판정은 `private` 함수로 둔다. 공개 시그니처는 바꾸지 않는다.

- [ ] **Step 5: 통과 확인**

Run: `GRADLE :core:test`
Expected: BUILD SUCCESSFUL (`PeriodResolverTest` 22개, `KoreanHolidaysTest` 6개 포함 전부 통과)

- [ ] **Step 6: 앱이 그대로 컴파일되는지 확인**

Run: `GRADLE :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (호출부 수정 없음)

- [ ] **Step 7: 커밋**

```bash
git add core/src/main/kotlin/com/householdbudget/app/domain/PeriodResolver.kt \
        core/src/test/kotlin/com/householdbudget/app/domain/PeriodResolverTest.kt
git commit -m "feat: 월급일이 주말·공휴일이면 앞 평일로 당겨 회계월을 계산" -m "Co-Authored-By: Claude <모델 이름> <noreply@anthropic.com>"
```

---

### Task 3: 설정 화면 안내 문구 [Sonnet·기계적]

**Files:**
- Modify: `app/src/main/java/com/householdbudget/app/ui/util/Formatters.kt`
- Create: `app/src/test/java/com/householdbudget/app/ui/util/FormattersTest.kt`
- Modify: `app/src/main/res/values/strings_more.xml:26` 부근
- Modify: `app/src/main/java/com/householdbudget/app/ui/settings/SettingsScreen.kt:91, 179-180, 351-355` 부근

**Interfaces:**
- Consumes: `PeriodResolver().paydayInMonth(yearMonth: YearMonth, paydayDom: Int): LocalDate` (Task 2, 보정된 날짜)
- Produces: `fun LocalDate.formatMonthDayWeekday(): String` — `"11월 20일(금)"` 형식(`Formatters.kt`)

**이 태스크가 정하는 것**
- "이번 달" = 오늘(`Asia/Seoul`)이 속한 **달력 월**의 보정 월급일: `PeriodResolver().paydayInMonth(YearMonth.from(today), payday)`. 스펙 예시(11월 19일에 "이번 달은 11월 20일(금)부터")와 맞춘 것이다. 월급일 1일에 신정이 겹치면 "이번 달은 12월 31일(목)부터"처럼 앞 달 날짜가 나올 수 있고, 그대로 둔다.
- 표시 위치: 설정 목록의 "월급 받는 날" 행 부제에 실제 시작일, 월급일 선택 바텀시트에 안내 한 줄. (바텀시트는 날짜를 누르면 바로 닫히므로 시작일은 닫힌 뒤에도 보이는 행 부제에 둔다.)
- 문구(`strings_more.xml`):
  - `settings_payday_subtitle_v2`를 `매월 %1$d일 · 이번 달은 %2$s부터`로 바꾼다.
  - 새 문자열 `settings_payday_adjust_hint` = `주말·공휴일이면 앞 평일로 당겨요`.

- [ ] **Step 1: 실패하는 테스트 작성** — `FormattersTest.kt`

```kotlin
package com.householdbudget.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FormattersTest {
    @Test
    fun formatMonthDayWeekday_koreanNoPadding() {
        assertEquals("11월 20일(금)", LocalDate.of(2026, 11, 20).formatMonthDayWeekday())
        assertEquals("1월 2일(토)", LocalDate.of(2027, 1, 2).formatMonthDayWeekday())
        assertEquals("12월 31일(목)", LocalDate.of(2026, 12, 31).formatMonthDayWeekday())
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `GRADLE :app:testDebugUnitTest --tests 'com.householdbudget.app.ui.util.FormattersTest'`
Expected: 컴파일 실패 — `Unresolved reference: formatMonthDayWeekday`

- [ ] **Step 3: `LocalDate.formatMonthDayWeekday()` 구현**

`Formatters.kt`에 기존 포매터들과 같은 방식으로 `DateTimeFormatter.ofPattern("M월 d일(E)").withLocale(Locale.KOREA)`를 `private val`로 두고 확장 함수를 추가한다(기존 `dayLabelFormatter`는 괄호 앞에 공백이 있어 재사용하지 않는다).

- [ ] **Step 4: 통과 확인**

Run: `GRADLE :app:testDebugUnitTest --tests 'com.householdbudget.app.ui.util.FormattersTest'`
Expected: BUILD SUCCESSFUL, 1 test passed

- [ ] **Step 5: 문구와 설정 화면 수정**

- `strings_more.xml`: 위 "문구" 대로 한 줄 수정, 한 줄 추가(`settings_payday_subtitle_v2` 바로 아래).
- `SettingsScreen.kt`:
  - `payday` 수집 아래에서 `remember(payday) { … }`로 이번 달 보정 월급일(`LocalDate`)을 구한다(오늘은 `LocalDate.now(ZoneId.of("Asia/Seoul"))`).
  - "월급 받는 날" `SettingsActionRow`의 `subtitle`을 `stringResource(R.string.settings_payday_subtitle_v2, payday, 그날.formatMonthDayWeekday())`로 바꾼다.
  - 바텀시트에서 `settings_payday_hint` `Text` 바로 아래에 같은 스타일(`bodySmall`, `onSurfaceVariant`)의 `Text(stringResource(R.string.settings_payday_adjust_hint))`를 추가한다.
- `settings_payday_subtitle_v2`의 다른 사용처가 없는지 확인한다: `grep -rn settings_payday_subtitle_v2 app/src/main` → `SettingsScreen.kt` 한 곳과 `strings_more.xml`만 나와야 한다.

- [ ] **Step 6: 빌드·전체 테스트 확인**

Run: `GRADLE :core:test :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 커밋**

```bash
git add app/src/main/java/com/householdbudget/app/ui/util/Formatters.kt \
        app/src/test/java/com/householdbudget/app/ui/util/FormattersTest.kt \
        app/src/main/res/values/strings_more.xml \
        app/src/main/java/com/householdbudget/app/ui/settings/SettingsScreen.kt
git commit -m "feat: 설정에 월급일 앞당김 안내와 이번 달 실제 시작일 표시" -m "Co-Authored-By: Claude <모델 이름> <noreply@anthropic.com>"
```

---

## 범위 밖(이 계획에서 하지 않음)

- `CalendarScreen.kt:84`의 달력 월급날 표시는 `minOf(paydayDom, daysInMonth)`로 명목일을 직접 계산한다. 스펙이 달력을 언급하지 않아 그대로 둔다 — 보정 뒤에는 달력의 월급날 표시(예: 21일)와 실제 회계월 시작(20일)이 하루 이틀 다를 수 있다. 사용자 확인 뒤 별도 작업.
- 이미 저장된 아카이브 스냅샷의 경계는 고치지 않는다(스펙 결정).
