# Mark7 Android BLE

Mark7 로봇 의수를 BLE로 제어·모니터링하는 안드로이드 앱.

- **기능 목적**은 `Mandro_Controller_robot_android` (기존 의수 컨트롤러)와 비슷하다:
  손가락 직접 구동, 동작 패턴 설정, 온도·전류 모니터링.
- **통신 규약이 완전히 다르다.** Mark7 펌웨어의 12 B CMD / 117 B SET / 36 B STATUS
  프레임을 쓴다 — 상세는 [`docs/PROTOCOL.md`](docs/PROTOCOL.md).
- **UI도 완전히 다르다.** 체크박스 나열 대신, 암밴드가 인식하는 4가지 액션
  (굽히기 / 펴기 / 쥐기 / 휴식)에 **사진을 보고** 손 모양을 연결하는 방식.
- 프로젝트 구조는 `mandro-final_Armband_Android/mandro-dynamic-gesture`를 따른다
  (Compose + Hilt + Clean Architecture + 버전 카탈로그). 단, 이 앱은 온디바이스
  학습이 없으므로 Chaquopy / TFLite 는 뺐다.

---

## 스택

| | |
|---|---|
| UI | Jetpack Compose + Material 3, Navigation-Compose |
| DI | Hilt |
| 비동기 | Coroutines / Flow (MVVM: 단일 `UiState` + `StateFlow`) |
| 저장 | DataStore(Preferences) + kotlinx.serialization — 설정/매핑 JSON |
| 이미지 | Coil (assets 제스처 사진) |
| 빌드 | AGP 8.7 / Kotlin 2.1 / JDK 21 / Gradle 9.4 (wrapper) |

`minSdk 24 · targetSdk 35 · applicationId com.mandro.mark7`

---

## 모듈 구조

```
app/src/main/java/com/mandro/mark7/
├── Mark7Application.kt          @HiltAndroidApp
├── MainActivity.kt              Compose 진입 · 바텀 4탭 NavHost
│
├── core/ble/
│   ├── MarkSevenProtocol.kt     ★ CMD/SET 인코딩, STATUS/ACK 디코딩 (완성)
│   ├── FrameReassembler.kt      RX 바이트 스트림 → ACK(5B) / STATUS(36B) 프레임
│   └── BleManager.kt            @Singleton 스캔/연결/GATT/notify, 프레임 송수신
│
├── domain/
│   ├── model/                   BleModels · HandStatus · HandConfig(패턴8+설정)
│   │                            · HandAction(F/E/close/rest) · MotorCommand
│   └── repository/HandRepository.kt   ViewModel 이 의존하는 유일한 창구
│
├── data/
│   ├── ble/HandRepositoryImpl.kt      BleManager + 로컬 저장 결합, SET→ACK 대기
│   └── local/HandConfigStore.kt       HandConfig / ActionMapping 영속화
│
├── di/                         BleModule 불필요(@Inject) · RepositoryModule · DataStoreModule
│
└── presentation/
    ├── theme/Theme.kt          계측 장비 톤(그래파이트+청록) — 기존 만드로 앱과 구분
    ├── navigation/Navigation.kt
    ├── components/Components.kt SectionCard · StatRow
    └── ui/
        ├── splash/             연결 여부 보고 Scan/Monitor 분기
        ├── scan/               Mark7('m…') 스캔·연결
        ├── monitor/            STATUS 모니터링 (온도/전류/위치/EMG)
        ├── manual/             직접 구동 (CMD: GRASP/RELEASE/STOP/RESET)
        ├── action/             ★ 액션→패턴 매핑 (사진 선택) + 점진적 잡기 토글
        │   ├── ActionScreen.kt · PatternPickerScreen.kt
        │   └── GestureAssets.kt   assets/gesture_guides/<action>/*.jpg 로더
        └── settings/           SET 프레임 편집·전송 (전류/위치/속도/SL/EMG)
```

`assets/gesture_guides/{flexion,extension,close,rest}/fNN.jpg` — 현재는
`mandro-dynamic-gesture`의 가이드 이미지를 임시로 복사해 둔 상태.
실제 "패턴별 손 모양 사진"으로 교체 예정.

---

## 지금 상태 (스캐폴드)

| 영역 | 상태 |
|---|---|
| 프로토콜 코덱 (`MarkSevenProtocol`) | **구현 완료** + 단위테스트 (`./gradlew test`) |
| BLE 매니저 / 프레임 재조립 | 구현 (실기기 검증 전) |
| Repository / DI / Navigation / Theme | 배선 완료 |
| 화면 6종 | **뼈대만** — 상태 표시·전송 버튼은 동작, 상세 편집 UI 는 `// TODO` |
| BLE 런타임 권한 요청 플로우 | 미구현 (`// TODO` — Scan 화면) |

### 다음 작업 (`// TODO` 요약)

1. Scan: Android 12+ `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` 권한 요청 + 마지막 기기 기억
2. Manual: 손가락별 위치/속도/전류 슬라이더
3. Action: 패턴 8개의 대표 손 모양 사진 세트 준비 → PatternPicker 를 그 사진으로
4. Settings: 패턴 8개(S1..S8) 편집 UI, 전역 설정 슬라이더
5. Monitor: 온도/전류 시계열 그래프 + 과열·과전류 경보(자동 STOP 옵션)
6. 실기기로 CMD/SET/STATUS 왕복 검증, `PROTOCOL.md` 값 확정

---

## 빌드

```bash
./gradlew :app:assembleDebug      # APK
./gradlew test                    # 프로토콜 단위테스트
```

Android Studio 로 열면 `local.properties` 는 자동 생성된다.
