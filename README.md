# 실시간 자막 앱 (SubtitleApp)

스마트폰에서 재생되는 외국어 음성을 실시간으로 한국어 자막으로 번역해 오버레이로 표시하는 Android 앱.
**스마트폰만으로 설치부터 실행까지 가능.**

---

## 스마트폰에서 설치하는 방법

### 1단계: GitHub에서 APK 받기
1. 이 저장소를 GitHub에 올리기 (또는 Fork)
2. `Actions` 탭 → `Build APK` → 가장 최근 실행 클릭
3. `Artifacts` → `SubtitleApp-debug` 다운로드
4. 스마트폰에서 ZIP 압축 해제 → `app-debug.apk` 설치

> **설치 전 설정**: 설정 → 보안 → 알 수 없는 앱 설치 허용

### 2단계: 앱 실행 → 모델 자동 설치
- 앱 첫 실행 시 STT 모델 선택 화면이 나와요
- 원하는 모드 선택 → "다운로드 시작" 탭
- 다운로드 완료 후 인터넷 없이 동작

---

## 모드 비교

| 모드 | 엔진 | 크기 | 지연 | 정확도 |
|------|------|------|------|--------|
| 빠름 | Vosk small | ~50MB | 즉각 | ★★★☆☆ |
| 균형 | Whisper tiny | ~150MB | 2~3초 | ★★★★☆ |
| 정확 | Whisper base | ~300MB | 5초 | ★★★★★ |

---

## GitHub Actions 자동 빌드

`main` 브랜치에 push 할 때마다 자동으로 APK가 빌드돼요.

```
코드 수정 → git push → Actions 탭 → APK 다운로드
```

---

## 파일 구조

```
app/src/main/java/com/subtitleapp/
├── audio/
│   ├── AudioCaptureService.kt     # MediaProjection 내부 오디오 캡처
│   └── AudioChunkBuffer.kt        # PCM 청크 큐
├── stt/
│   ├── SttEngine.kt               # 인터페이스 + SttMode
│   ├── VoskSttEngine.kt           # 빠름 모드
│   ├── SherpaWhisperEngine.kt     # 균형/정확 모드
│   ├── SttEngineFactory.kt
│   └── SttCoordinator.kt          # STT+번역+오버레이 연결
├── translation/
│   └── MlKitTranslator.kt         # ML Kit 온디바이스 번역
├── overlay/
│   └── SubtitleOverlayService.kt  # 자막 오버레이
├── model/
│   ├── ModelDownloadManager.kt    # 앱 내 모델 다운로더
│   └── ModelSetupActivity.kt      # 최초 설치 화면
└── ui/
    ├── SplashActivity.kt           # 런처 (모델 설치 여부 분기)
    ├── MainActivity.kt
    ├── SettingsActivity.kt
    └── SettingsFragment.kt
```

---

## 알려진 한계
- DRM 콘텐츠(넷플릭스, Disney+): 무음 캡처 (OS 정책)
- 에뮬레이터: 내부 오디오 캡처 불가 → 실기기 필수
- Android 10 (API 29) 이상 필요
