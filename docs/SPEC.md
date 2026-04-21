# 가계부(안드로이드) 제품·기능 명세

## 1. 비전·레퍼런스

- **목표**: 월급일을 기준으로 “한 달”을 정의해, 그 기간 안에서 수입·지출·잔액을 한눈에 보여 주는 **모바일 가계부**(안드로이드).
- **UX 레퍼런스**: 뱅크샐러드 가계부와 유사한 **카드형 요약·기간 전환·카테고리 비중**을 지향한다.
- **MVP 범위**: **수동 입력·분류** 중심. **오픈뱅킹/카드·계좌 자동 연동**은 **Phase 2**로 분리한다.
- **데이터**: MVP는 **기기 로컬 저장(Room 등)** 을 전제로 한다. **클라우드 동기화·계정 로그인**은 후속 단계에서 별도 명세로 다룬다.

## 2. 용어

| 용어 | 정의 |
|------|------|
| 월급일 | 사용자가 설정한 매월 **N일**(1–31). 해당 일이 없는 달은 **그 달의 마지막 날**로 보정(클램프)한다. |
| 회계월(예산 기간) | 직전 월급일(포함)부터 **다음 월급일(미포함)** 까지의 기간. 반열린 구간 **`[start, end)`** 로 표현한다. |
| 거래 | 일시, 금액, 유형(수입/지출), 카테고리, 메모 등을 갖는 레코드(MVP에서 필수 필드는 구현 단계에서 확정). |
| 예산 | 회계월 단위로 카테고리별 상한 등을 두는 기능(후속; MVP에서는 “기간 합계” 우선). |

## 3. 사용자 스토리 (MVP)

1. **월급일 설정**: 사용자는 월급일(1–31)을 저장하고 언제든 변경할 수 있다.
2. **회계월 확인**: 홈(또는 대시보드) 상단에 현재 회계월이 `YYYY-MM-DD ~ YYYY-MM-DD` 형태(실제로는 `[start, end)` 의미)로 표시된다.
3. **기간 이동**: 이전/다음 **회계월**로 이동할 수 있다.
4. **거래 추가**: 사용자는 수입/지출 거래를 날짜와 함께 등록한다.
5. **기간별 합계**: 선택한 회계월에 속한 거래만 집계하여 총수입·총지출·순액을 본다.
6. **카테고리**: 지출은 카테고리에 속하며, 회계월 기준 카테고리별 합계/비율을 본다(차트는 선택).

## 4. 기능 목록

### 4.1 MVP (In scope)

- 월급일 설정·변경(DataStore 등).
- 회계월 계산·표시·이전/다음 탐색(도메인 단일 모듈에서 수행).
- 거래 CRUD(로컬 DB).
- 회계월 필터 집계(합계, 카테고리별).
- Material 3 기반 기본 UI(Compose).

### 4.2 Out of scope (초기 릴리스 제외)

- 금융사·오픈뱅킹 자동 연동.
- 가족/다인용 공유, 서버 동기화.
- 영수증 OCR, 위젯, 워치(추후 검토).

## 5. 화면·내비게이션 (초안)

- **온보딩/설정**: 월급일 입력.
- **홈**: 현재 회계월 라벨, 요약 카드(수입/지출/잔액), 최근 거래.
- **거래 목록**: 회계월 필터, 정렬(일시 내림차순 기본).
- **거래 입력/수정**: 폼.
- **설정**: 월급일 변경, 통화(추후), 백업(후속).

내비게이션은 Jetpack Navigation(Compose) 도입을 권장한다.

## 6. 멀티모듈(구현)

- **`:core`**: `java.time` 기반 순수 도메인(`PeriodResolver`, `BudgetPeriod`). JVM 단위 테스트.
- **`:app`**: Jetpack Compose UI, Room 등 안드로이드 의존 코드. `implementation(project(":core"))` 로 도메인 재사용.

## 7. 데이터 모델 (초안)

엔티티 이름은 구현 시 조정 가능하나, 필드 의미는 아래를 따른다.

- **PaydaySettings**
  - `paydayDom: Int` — 1..31
  - (선택) `zoneId: String` — 기본 `Asia/Seoul`
- **Transaction**
  - `id: String` 또는 `Long`
  - `occurredAt: Instant` 또는 `LocalDate` + 시각(정책 확정 필요)
  - `amountMinor: Long` — 소수 통화 대비 **minor unit**(원화면 그대로 원 단위).
  - `isIncome: Boolean`
  - `categoryId: String` / `Long`
  - `memo: String?`
- **Category**
  - `id`, `name`, `sortOrder`

**인덱스(권장)**

- `Transaction(occurredAt)` — 기간 조회.
- 필요 시 `Transaction(categoryId, occurredAt)` 복합 인덱스.

거래가 어느 회계월에 속하는지는 **저장된 `occurredAt`** 과 **당시 유효한 월급일 규칙**으로 `PeriodResolver`와 동일한 규칙으로 판별한다(과거 데이터 일관성은 별도 정책으로 “월급일 변경 시 재배치 여부”를 명시할 수 있음 — MVP 기본은 **조회 시점 규칙으로 재계산**).

## 8. 회계월 결정 알고리즘 (필수)

구현 기준은 코드의 `PeriodResolver`와 단위 테스트와 **동일**해야 한다.

### 8.1 월급일 클램프

- 입력: `yearMonth`, `paydayDom` (1–31).
- 출력: 해당 달의 월급일 `LocalDate`.
- 규칙: `day = min(paydayDom, yearMonth.lengthOfMonth())`, `yearMonth.atDay(day)`.

예:

| yearMonth | paydayDom | 결과 |
|-----------|-------------|------|
| 2025-02 | 31 | 2025-02-28 |
| 2024-02 | 31 | 2024-02-29 |
| 2025-03 | 31 | 2025-03-31 |

### 8.2 reference 날짜가 속한 회계월

- 입력: `reference: LocalDate`, `paydayDom`.
- `thisPay = paydayInMonth(YearMonth(reference), paydayDom)`.
- **`reference >= thisPay`** 이면  
  `start = thisPay`, `end = paydayInMonth(YearMonth(reference).plusMonths(1), paydayDom)`.
- 그렇지 않으면  
  `start = paydayInMonth(YearMonth(reference).minusMonths(1), paydayDom)`, `end = thisPay`.
- 회계월은 **`[start, end)`** (start 포함, end 미포함).

예(월급일 25일):

| reference | [start, end) |
|-----------|----------------|
| 2025-05-24 | [2025-04-25, 2025-05-25) |
| 2025-05-25 | [2025-05-25, 2025-06-25) |
| 2025-05-26 | [2025-05-25, 2025-06-25) |

### 8.3 이전/다음 회계월

- **다음**: `start' = end`, `end' = paydayInMonth(YearMonth(start').plusMonths(1), paydayDom)`.
- **이전**: `end' = start`, `start' = paydayInMonth(YearMonth(end').minusMonths(1), paydayDom)`.

### 8.4 타임존·시각

- MVP 권장: 거래 일자는 **`Asia/Seoul`** 기준 달력일로 저장·표시하거나, **기기 로컬** 중 하나로 **명세·설정에 고정**한다. (현재 샘플 UI는 서울 존을 사용.)
- “월급일 당일 0시” 기준으로 단순화한다. **실제 입금 시각** 반영은 수동 조정(후속)으로 둔다.

## 9. 비기능

- **성능**: 일반 가정 하에서 목록 스크롤 60fps에 가깝게.
- **보안**: 로컬 DB 파일 접근은 앱 샌드박스 내(표준 안드로이드).
- **백업**: MVP에서 필수 아님. 추후: Android Backup 또는 파일보내기.

## 10. 릴리스 기준(테스트 관점)

- 단위 테스트: 월급일 **31**·**윤년 2월**·**기간 경계**(월급일 당일 전/후)에서 기대 구간 일치.
- UI 스모크: 앱 기동, 현재 회계월 문자열 표시.

## 11. 구현 참조 파일

- 도메인(JVM 모듈, Android SDK 없이 테스트 가능): `core/src/main/kotlin/com/householdbudget/app/domain/PeriodResolver.kt`
- 단위 테스트: `core/src/test/kotlin/com/householdbudget/app/domain/PeriodResolverTest.kt`
- 앱 모듈은 `implementation(project(":core"))` 로 동일 도메인을 사용한다.
