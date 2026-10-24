# TTS Browser — Roadmap

## MVP

| Phase | Nội dung | Phụ thuộc | Trạng thái |
|-------|----------|-----------|-----------|
| 1 | Khởi tạo project: Gradle KTS, version catalog, Compose, Navigation, AppContainer, theme | — | Xong |
| 2 | Trình duyệt: WebView, thanh địa chỉ/tìm kiếm, back/forward, lịch sử, bookmark | 1 | Xong (chờ thử trên máy) |
| 3 | Trích nội dung: Readability.js, TextCleaner, ChapterLinkFinder, SentenceSegmenter, ElementPicker + site_rule + unit test (fixture akaytruyen.com) | 2 | Chưa bắt đầu |
| 4 | Lõi TTS: TtsEngine, SystemTtsEngine, TtsPlayer, PlaybackService, MediaSession, audio focus | 3 | Chưa bắt đầu |
| 5 | Reader UI: highlight câu, auto-scroll, chạm để seek, cỡ chữ/theme, mini player | 4 | Chưa bắt đầu |
| 6 | Thư viện & tiến độ: Room, lưu/khôi phục vị trí, hẹn giờ tắt, HeadlessPageLoader (chương sau) | 5 | Chưa bắt đầu |
| 7 | Edge TTS: WebSocket client, token, prefetch, cache, fallback | 4 | Chưa bắt đầu |
| 8 | Hoàn thiện: settings, xử lý lỗi, thử trên 5 trang truyện, build APK release (ký bằng key cá nhân) | 6, 7 | Chưa bắt đầu |

Phase 6 và 7 có thể làm song song.

## Phase 2 (sau MVP)

- Tự động sang chương sau
- Adapter CSS selector theo trang
- Tải trước / đọc offline
- Chặn quảng cáo (host blocklist)
- Nhập TXT/EPUB
