# 안정성 수정 + 가계부 화살표 UX 설계

작성일 2026-11-19 · 기준 `master` (b624a0a) + 커밋 9229893("자동" 표시 제거)

사용자 승인: 아래 항목 전부를 한 묶음으로 수정. M5는 "앱을 나갔다 오면 다시 잠김"으로 동작 변경 승인. 제외: M2(월급일 1–5일 달 제목), 환불의 수입 처리(현 동작 유지).

## 근거
- 화살표 버벅임: 실기기(Galaxy S23, debug 빌드) 측정. 화살표 1회당 27–53ms 프레임 1개, 전환 애니메이션 없음, 레이아웃 이동.
- 나머지: 전체 정적 점검(Fable) 결과.

## 요구사항

### A. 가계부(피드) 화살표 UX — `ui/feed/FeedScreen.kt`
1. 기간 전환 애니메이션: 기간 의존 영역(히어로·요약·목록)을 `period.startInclusive` 키로 150–200ms 페이드 + 화살표 방향 소폭 가로 슬라이드.
2. 레이아웃 고정: 히어로 왼쪽 위 슬롯(카운트다운 ↔ "오늘로" 알약) 최소 높이 고정, 예산 진행 막대 자리 유지 또는 `animateContentSize`, 달 라벨 고정 폭 + 가운데 정렬, ‹ › 터치 영역 48dp.
3. 렌더링: LazyColumn `item`/`items`에 `contentType`("dayHeader"/"tx" 등). 날짜 그룹핑·정렬을 `remember(filteredRows)` 또는 ViewModel로. 날짜 헤더 합계 1회 계산. `HomeSummary` 합계를 매번 재계산하는 getter 대신 1회 계산 값으로. `LocalDate.now`는 remember.
4. (선택, 권장) release 빌드 최적화: R8 활성 + baseline profile(`profileinstaller`). 신규 의존성은 이 항목에 한해 허용 여부를 빌더가 판단해 보고(기본은 R8만 켜고 profileinstaller는 보류).

### B. 버그 수정
- **H1** 저장/닫기 중복: `EditTransactionViewModel.save()/delete()` 재진입 방지(`isSaving`이면 return, 성공 후 리셋하지 않음), 닫기는 `popBackStack(BudgetRoutes.TABS, inclusive=false)`처럼 멱등.
- **H2** 알림 원문 보관: `auto_entries` 행을 만들지 않은 로그는 원문(title/body) 대신 해시만 저장(또는 비움). `notification_log`(및 auto_entries 원문 필드)를 백업에서 제외(`dataExtractionRules` + `fullBackupContent`, DB 파일 단위라면 대안 보고). `auto_entries`도 90일 지난 처리 완료 항목 정리.
- **M1** 편집 화면 소분류 초기화: 부모·자식 카테고리를 한 StateFlow로 결합하거나, 자식 목록이 비어 있을 때 기존 `categoryId`를 지우지 않음. 처음 열 때 기존 카테고리가 바뀌지 않음을 테스트.
- **M3** 지난 기록 합계: 상세·목록 합계를 저장 스냅샷 대신 `aggregateBetween`으로 실시간 계산(테이블은 기간 목록으로만 사용).
- **M4** 결제 앱 경유 지출 누락: `WatchedApps.effective`에서 저장된 집합과 설치된 앱을 교집합. 카드/은행 알림이 페이 키워드를 포함해 Ignore 되는 경우, 그 페이 앱 알림이 일정 시간(예: 10분) 내 오지 않으면 확인 필요로 보내거나, 최소한 "무시된 항목" 목록을 자동 기록 설정에서 볼 수 있게(빌더 판단, 더 단순한 쪽 우선, 데이터 유실 없는 쪽).
- **M5** 앱 잠금: `ON_STOP`에서 `unlocked` 초기화(다시 잠김), 잠금 설정 로드 전에는 아무것도 그리지 않음.
- **M6** 알림당 비용: 감시 대상 집합을 prefs Flow에서 캐시, 기본값 1회 계산, mutex 잡기 전에 패키지 포함 여부 확인.

### C. 낮음 중 쉬운 것
- 자정 넘김: 홈/통계/설정의 "오늘"이 날짜가 바뀌면 갱신(앱 복귀 시 또는 자정 틱).
- CSV 내보내기: 내보내기 시점에 기간을 다시 계산.
- 컴포지션 안 재조회: `CalendarScreen.kt:329`, `ArchiveDetailScreen.kt:109-112`, `ArchiveListScreen.kt:67`, `BudgetSettingsScreen.kt:87`의 Flow를 remember.
- CSV 수식 주입: `= + - @`로 시작하는 셀 앞에 `'` 붙이기.

## 제약
- 신규 의존성 금지(A4 profileinstaller만 예외 검토). 로그에 알림 원문·금액 출력 금지. 기존 거래 삭제 금지.
- UI 확인은 **에뮬레이터(test35)에서만**, 가짜 데이터. 실제 폰에는 adb input 금지.
- `git add` 경로 지정, 저장소 루트의 .xlsx/.xls/.numbers/.csv 금지.
