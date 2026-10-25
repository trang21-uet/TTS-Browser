# TTS Browser — Roadmap

## MVP

| Phase | Nội dung | Phụ thuộc | Trạng thái |
|-------|----------|-----------|-----------|
| 1 | Khởi tạo project: Gradle KTS, version catalog, Compose, Navigation, AppContainer, theme | — | Xong |
| 2 | Trình duyệt: WebView, thanh địa chỉ/tìm kiếm, back/forward, lịch sử, bookmark | 1 | Xong (chờ thử trên máy) |
| 3 | Trích nội dung: Readability.js, TextCleaner, ChapterLinkFinder, SentenceSegmenter, ElementPicker + site_rule + unit test (fixture akaytruyen.com) | 2 | Xong (chờ thử trên máy) |
| 4 | Lõi TTS: TtsEngine, SystemTtsEngine, TtsPlayer, PlaybackService, MediaSession, audio focus | 3 | Xong (chờ thử trên máy) |
| 5 | Reader UI: highlight câu, auto-scroll, chạm để seek, cỡ chữ/theme, mini player | 4 | Xong (chờ thử trên máy) |
| 6 | Thư viện & tiến độ: Room, lưu/khôi phục vị trí, hẹn giờ tắt, HeadlessPageLoader (chương sau) | 5 | Xong (chờ thử trên máy) |
| 7 | [Edge TTS](../plans/261024-2331-tts-browser-mvp/phase-07-edge-tts.md): WebSocket/token v2, prefetch/cache RAM, fallback, Settings, sleep fade | 4 | Xong; review 9.5/10, build + 78 test + lint pass; smoke test Xiaomi 13, còn thử âm thanh/nền |
| 8 | Hoàn thiện: hướng dẫn pin HyperOS, xử lý lỗi, thử trên 5 trang truyện, build APK release (ký bằng key cá nhân) | 6, 7 | Chưa bắt đầu |

Phase 6 và 7 có thể làm song song.

### Còn lại trước release

- Giữ checklist thiết bị Phase 1–6 chưa hoàn tất cho tới khi có kết quả từng hạng mục.
- Phase 7: hai giọng Việt, pause/seek, mất mạng → fallback đúng câu, đổi engine khi đang đọc, tắt màn hình và sleep fade.
- Phase 8: sửa đường lỗi đồng bộ `SystemTtsEngine.queueMore` (`Failed` bị lọc do tăng generation ngay sau emit); thêm regression test.
- Hoàn thiện lựa chọn giọng system nếu cần, thử pin/lock screen/tai nghe và release signing.

## Phase 2 (sau MVP)

- Tự động sang chương sau
- Adapter CSS selector theo trang
- Tải trước / đọc offline
- Chặn quảng cáo (host blocklist)
- Nhập TXT/EPUB
