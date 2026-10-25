# TTS Browser

Trình duyệt Android đọc truyện chữ bằng giọng nói (TTS): lướt web → trích nội dung chương → nghe, kể cả khi tắt màn hình.

## Tài liệu

- [Spec sản phẩm (PDR)](docs/project-overview-pdr.md)
- [Kiến trúc & công nghệ](docs/system-architecture.md)
- [Roadmap](docs/development-roadmap.md)

## Công nghệ

Kotlin · Jetpack Compose · WebView + Readability.js · Android TextToSpeech + Edge TTS · Media3 (MediaSessionService, ExoPlayer) · Room · DataStore

## Quyết định đã chốt

| Mục | Quyết định |
|-----|-----------|
| Package | `com.trangnx.ttsbrowser`, tên hiển thị "TTS Browser" |
| Trang thử nghiệm chính | akaytruyen.com |
| Thiết bị mục tiêu | Xiaomi 13 (HyperOS) |
| Phân phối | APK cá nhân (sideload), ký bằng key riêng |
| Chọn vùng nội dung thủ công | Có trong MVP |

## Build

Yêu cầu: JDK 21, Android SDK 37 (compileSdk 37, targetSdk 36), Gradle wrapper 9.8.2.

```bash
./gradlew assembleDebug
```

Build APK release tối ưu, ký cá nhân và checklist HyperOS: [Hướng dẫn release](docs/release-guide.md). Phase 8 có hướng dẫn nghe nền ở lần mở đầu và trong Cài đặt; có thể mở phần chọn giọng hệ thống của Android ngay trong app.

Trong tab **Cài đặt**, chọn TTS hệ thống hoặc Microsoft Edge TTS (Hoài My/Nam Minh), chỉnh tốc độ/cao độ riêng cho từng nguồn giọng. Edge cần mạng và gửi các câu đang đọc/tiền tải tới Microsoft; khi lỗi sẽ tự chuyển sang giọng hệ thống từ câu hiện tại.

Mã token Edge v2 được port từ `rany2/edge-tts` (LGPL-3.0-only), có attribution trong mã và license tại `app/src/main/assets/licenses/`. Tiến độ và các kiểm tra thiết bị còn lại nằm trong [kế hoạch MVP](plans/261024-2331-tts-browser-mvp/plan.md).
