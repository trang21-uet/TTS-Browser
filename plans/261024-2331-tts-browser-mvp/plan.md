# TTS Browser MVP — Plan

Spec: [PDR](../../docs/project-overview-pdr.md) · [Architecture](../../docs/system-architecture.md) · [Roadmap](../../docs/development-roadmap.md)

| Phase | File | Status |
|-------|------|--------|
| 1. Project setup | [phase-01-project-setup.md](phase-01-project-setup.md) | Done (chờ thử trên máy) |
| 2. Browser (WebView, address bar, history, bookmark) | [phase-02-browser.md](phase-02-browser.md) | Done (chờ thử trên máy) |
| 3. Extraction (Readability, cleaner, link finder, segmenter, element picker) | [phase-03-extraction.md](phase-03-extraction.md) | Done (chờ thử trên máy) |
| 4. TTS core (engine, TtsPlayer, PlaybackService) | [phase-04-tts-core.md](phase-04-tts-core.md) | Done (chờ thử trên máy) |
| 5. Reader UI (highlight, seek, theme, mini player) | [phase-05-reader-ui.md](phase-05-reader-ui.md) | Done (chờ thử trên máy) |
| 6. Library, progress, sleep timer, headless loader | [phase-06-library-progress.md](phase-06-library-progress.md) | Done (chờ thử trên máy) |
| 7. Edge TTS | [phase-07-edge-tts.md](phase-07-edge-tts.md) | Done (build + 78 test + lint pass; smoke test Xiaomi 13, còn thử âm thanh/nền) |
| 8. Polish, battery guide (HyperOS), release APK | phase-08 | Pending |

## Key dependencies
- AGP 9.4.1 (built-in Kotlin, cần Gradle ≥ 9.6) → Gradle 9.8.2, JDK 21
- compileSdk 37 (Compose BOM 2026.09 / Navigation 2.10 yêu cầu), targetSdk 36, minSdk 26
- Compose BOM 2026.09.00, Media3 1.11.1, Room 2.8.5 + KSP 2.3.12, OkHttp 5.5.1

## Tiến độ và bước tiếp theo

- Phase 1–6: các bước triển khai đã hoàn tất; mọi checklist thiết bị vẫn giữ pending theo từng phase.
- Phase 7: triển khai xong, review 9.5/10 không còn Critical/High; kiểm tra cuối build + 78 unit test + lint pass. Xiaomi 13 cài/mở app, cài đặt Edge Nam Minh tồn tại qua force-stop/relaunch; kiểm thử phát âm thanh/nền còn pending.
- Phase 8 chưa bắt đầu. Gồm hướng dẫn pin HyperOS, polish/release và lỗi baseline `SystemTtsEngine.queueMore` làm `Failed` bị lọc khi tăng generation ngay sau emit.

## Findings: akaytruyen.com (trang thử nghiệm chính)
- Laravel + Cloudflare; HTML render phía server → WebView/Readability đọc được, không cần JS đặc biệt
- Nội dung chương: chuỗi `<p>` trong `div` không class, nằm sau `h1.custom-text` và widget tìm kiếm
- Nav chương: `.chapter-nav a.btn-back-next` chỉ chứa icon (`fa-chevron-left/right`), **không có chữ** → ChapterLinkFinder phải nhận diện theo icon/class, không chỉ theo text
- Chương cuối: nút sau là `button[disabled]` → nextUrl = null
- URL: `/{slug-truyen}/chuong-{N}-{slug}`; trang truyện `/truyen/{slug-truyen}` (lấy tên truyện từ link này)
- `og:title` = "Chương X: … - {Tên truyện} - Akay Truyện"
- Test fixture: dùng HTML tổng hợp mô phỏng cấu trúc, **không** commit nội dung truyện thật
