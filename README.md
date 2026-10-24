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

Yêu cầu: JDK 17+ (đang dùng JDK 21), Android SDK 36.

```bash
./gradlew assembleDebug
```
