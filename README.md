# 민사고 급식 위젯 (KMLA Meal Widget)

kmlaonline.net 메인 페이지(로그인 전 화면)에 뜨는 오늘의 급식표를 갤럭시 홈 화면 위젯으로 보여주는 안드로이드 앱입니다.

## 기능

- **시간에 맞춰 자동 전환**: ~8:30 아침 → ~13:30 점심 → 그 이후 저녁
- **탭 전환**: 위젯의 아침/점심/저녁 탭을 누르면 그 끼니를 보여줌 (다음 자동 전환 때 원래대로)
- **자동 갱신**: 30분마다 확인해서 날짜가 바뀌었거나 3시간이 지나면 새로 받아옴
- **↻ 버튼**: 즉시 새로고침
- **오프라인 대응**: 마지막으로 받은 메뉴를 저장해 두고 보여줌. 날짜가 지난 메뉴면 ⚠ 표시
- **크기 대응**: 위젯이 작으면 메뉴를 `·`로 이어서 한 덩어리로 표시
- **다크 모드 + 배경화면 색 맞춤** (Android 12 이상, One UI 4+)
- 위젯 본문을 누르면 앱이 열리고 세 끼 전체를 보여줌
- **글자 크기 4단계** (작게/보통/크게/아주 크게) — 앱 화면 아래 설정에서 변경, 위젯에도 바로 적용
- **주요 반찬 강조**: 매일 나오는 밥·김치류는 흐리게 표시 (설정에서 끌 수 있음)
- 메뉴가 많고 위젯이 넓으면 **2칸**으로 나눠 표시

## 폰에 바로 설치 (가장 쉬움)

main 브랜치에 올라갈 때마다 GitHub Actions가 APK를 자동으로 빌드해 **Releases**에 올립니다.

1. 갤럭시에서 GitHub에 로그인한 브라우저로 아래 링크 열기 → APK 다운로드
   `https://github.com/syw963/KMLA_launch/releases/latest/download/kmla-meal-widget.apk`
2. 다운로드한 파일 열기 → "출처를 알 수 없는 앱" 설치 허용 → 설치
3. 홈 화면 길게 누르기 → 위젯 → **민사고 급식** 추가

새 버전도 같은 링크로 받아 설치하면 기존 앱 위에 업데이트됩니다.

## 실행 방법 (Android Studio)

1. **Android Studio** (Ladybug 2024.2 이상)에서 `File → Open` → 이 폴더(`KMLA_lauch`) 선택
2. 처음 열면 Gradle Sync가 자동으로 진행됨 (몇 분 걸림)
3. 갤럭시 폰 준비
   - 설정 → 휴대전화 정보 → 소프트웨어 정보 → **빌드번호 7번 탭** → 개발자 모드 켜짐
   - 설정 → 개발자 옵션 → **USB 디버깅** 켜기
   - USB로 맥에 연결 → 폰에서 "USB 디버깅 허용" 확인
4. Android Studio 위쪽 기기 목록에서 폰 선택 → ▶ **Run**
5. 폰 홈 화면 빈 곳 길게 누르기 → **위젯** → **민사고 급식** → 끌어다 놓기

### APK 파일로 설치하고 싶다면

`Build → Build App Bundle(s) / APK(s) → Build APK(s)` → 생성된 `app/build/outputs/apk/debug/app-debug.apk`를 폰에 옮겨 설치 (출처를 알 수 없는 앱 설치 허용 필요).

### 파서 테스트

`app/src/test/.../MealParserTest.kt`를 열고 클래스 옆 ▶ 버튼. 사이트 구조가 그대로인지 확인할 수 있습니다.

## 구조

```
app/src/main/java/com/kmla/mealwidget/
├── data/
│   ├── Meal.kt              끼니 종류, 시간대 경계(8:30 / 13:30)
│   ├── MealParser.kt        HTML → 메뉴 목록 (jsoup)
│   └── MealRepository.kt    다운로드 + 캐시(SharedPreferences)
├── widget/
│   ├── MealWidgetProvider.kt  위젯 이벤트(추가/삭제/탭/새로고침)
│   ├── WidgetRenderer.kt      위젯 화면 그리기
│   └── WidgetState.kt         어느 끼니를 보여줄지
├── work/
│   └── MealRefreshWorker.kt   30분 주기 백그라운드 갱신 (WorkManager)
└── MainActivity.kt            앱 화면 (세 끼 전체)
```

## 사이트 구조가 바뀌면

급식표는 메인 페이지의 다음 요소에서 읽습니다.

| 끼니 | 요소 |
|---|---|
| 아침 | `<div id="food-breakfast">` |
| 점심 | `<div id="food-lunch">` |
| 저녁 | `<div id="food-dinner">` |

각 div 안에서 `<hr>` 뒤의 텍스트를 `<br>` 기준으로 나눠 메뉴로 씁니다. 바뀌면 `MealType.htmlId`와 `MealParser.kt`만 고치면 됩니다.

## 참고

- 안드로이드 배터리 정책상 위젯 갱신 주기는 최소 15분이고, 절전 모드에선 더 늦어질 수 있어요. 갤럭시에서 갱신이 잘 안 되면 **설정 → 애플리케이션 → 민사고 급식 → 배터리 → 제한 없음**으로 바꿔 주세요.
- 학생 커뮤니티 서버이므로 요청 횟수는 하루 수십 번 이내로 낮게 유지하도록 만들었습니다. 여러 사람에게 배포할 계획이면 운영진과 먼저 이야기해 보세요.
