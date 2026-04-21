# Household Budget (Android)

월급일 기준 회계월로 수입·지출을 관리하는 안드로이드 가계부 앱입니다.

## 동작(MVP)

- **Room** 로컬 DB: 거래·카테고리 저장(기본 카테고리 자동 시드)
- **DataStore**: 월급일(1–31) 설정
- **`:core` `PeriodResolver`**: 월급일 기준 회계월 `[시작, 다음월급일)` 집계
- **화면**: 홈(요약·최근), 내역(목록·탭에서 수정), **달력**(월별 격자에 일별 +수입/-지출, 날짜 탭 시 상세), 설정(월급일·**자동 이체/반복 내역**), 거래 추가/수정/삭제
- **반복 내역**: 매월 지정한 일(1–31, 없는 달은 말일)에 **달력 기준**으로 거래가 없으면 자동 추가. 앱 실행 시 + **WorkManager**(약 1일 주기)로 적용

## 구성

- **명세**: [`docs/SPEC.md`](docs/SPEC.md) — 회계월·월급일 알고리즘 정의
- **Cursor 규칙**: [`.cursor/rules/android-budget.mdc`](.cursor/rules/android-budget.mdc)
- **도메인**: `:core` JVM 모듈(`core/src/main/kotlin/.../PeriodResolver.kt`) + 테스트 `gradlew :core:test`

## 빌드

1. Android Studio에서 **이 폴더**(`household-budget-android`)를 연다.
2. 루트에 `local.properties`를 두고 `sdk.dir=...` 를 설정한다.  
   - 이 저장소를 처음 받았다면 [`local.properties.example`](local.properties.example)을 복사해 `local.properties`로 만들고 경로를 수정한다.  
   - Windows 기본 경로 예: `C:/Users/<사용자>/AppData/Local/Android/Sdk`  
   - 또는 사용자 환경 변수 **`ANDROID_HOME`**(또는 `ANDROID_SDK_ROOT`)에 SDK 루트를 지정한다.
3. Windows: `gradlew.bat :app:assembleDebug`  
   도메인 단위 테스트(SDK 불필요): `gradlew.bat :core:test`  
   앱 단위 테스트: `gradlew.bat :app:testDebugUnitTest`(SDK 필요)

## 참고

상위 워크스페이스의 `.cursorrules`(다른 프로젝트용)와 분리하려면, Cursor에서 이 하위 폴더만 열거나 `.cursor/rules`의 glob이 `household-budget-android/**`를 가리키도록 유지한다.
