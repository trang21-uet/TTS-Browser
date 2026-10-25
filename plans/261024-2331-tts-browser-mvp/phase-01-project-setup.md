# Phase 01 — Project setup

## Overview
Priority: P0 · Status: Done (assembleDebug pass 2026-10-24), chờ thử trên máy
Dựng project Android build được, có khung điều hướng 3 màn (Browser / Library / Settings) và AppContainer.

## Requirements
- Gradle Kotlin DSL + `gradle/libs.versions.toml`, Gradle wrapper 9.8.2 (đã nâng theo AGP 9.4.1)
- Single module `app`, package `com.trangnx.ttsbrowser`
- Compose Material 3, dynamic color (Android 12+), dark mode
- Navigation Compose với bottom bar: Browser, Thư viện, Cài đặt (màn placeholder)
- `TtsBrowserApp : Application` giữ `AppContainer` (manual DI)
- Manifest: INTERNET; các quyền khác thêm ở phase dùng tới

## Related files (create)
- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/*`, `gradlew(.bat)`
- `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/trangnx/ttsbrowser/`: `TtsBrowserApp.kt`, `MainActivity.kt`, `di/AppContainer.kt`, `ui/theme/Theme.kt`, `ui/navigation/AppNavHost.kt`, `ui/browser/BrowserScreen.kt`, `ui/library/LibraryScreen.kt`, `ui/settings/SettingsScreen.kt`
- `app/src/main/res/values/strings.xml`, `themes.xml`, launcher icon (adaptive, vector)

## Todo
- [x] Gradle files + wrapper
- [x] Manifest, Application, Activity, theme
- [x] Navigation + 3 placeholder screens
- [x] `./gradlew assembleDebug` pass
- [ ] Cài lên Xiaomi 13, kiểm tra 3 tab (cần người dùng)

## Success criteria
`assembleDebug` thành công, APK cài được, chuyển qua lại 3 tab.

## Risks
AGP 9 built-in Kotlin khác AGP 8 (không apply `kotlin-android`) → kiểm tra bằng build thực tế.
