# TTS Browser MVP — Plan

Spec: [PDR](../../docs/project-overview-pdr.md) · [Architecture](../../docs/system-architecture.md) · [Roadmap](../../docs/development-roadmap.md)

| Phase | File | Status |
|-------|------|--------|
| 1. Project setup | [phase-01-project-setup.md](phase-01-project-setup.md) | Done |
| 2. Browser (WebView, address bar, history, bookmark) | [phase-02-browser.md](phase-02-browser.md) | Done (chờ thử trên máy) |
| 3. Extraction (Readability, cleaner, link finder, segmenter, element picker) | phase-03 | Pending |
| 4. TTS core (engine, TtsPlayer, PlaybackService) | phase-04 | Pending |
| 5. Reader UI (highlight, seek, theme, mini player) | phase-05 | Pending |
| 6. Library, progress, sleep timer, headless loader | phase-06 | Pending |
| 7. Edge TTS | phase-07 | Pending |
| 8. Polish, battery guide (HyperOS), release APK | phase-08 | Pending |

## Key dependencies
- AGP 9.4.1 (built-in Kotlin, cần Gradle ≥ 9.6) → Gradle 9.8.2, JDK 21
- compileSdk 37 (Compose BOM 2026.09 / Navigation 2.10 yêu cầu), targetSdk 36, minSdk 26
- Compose BOM 2026.09.00, Media3 1.11.1, Room 2.8.5 + KSP 2.3.12, OkHttp 5.5.1

## Findings: akaytruyen.com (trang thử nghiệm chính)
- Laravel + Cloudflare; HTML render phía server → WebView/Readability đọc được, không cần JS đặc biệt
- Nội dung chương: chuỗi `<p>` trong `div` không class, nằm sau `h1.custom-text` và widget tìm kiếm
- Nav chương: `.chapter-nav a.btn-back-next` chỉ chứa icon (`fa-chevron-left/right`), **không có chữ** → ChapterLinkFinder phải nhận diện theo icon/class, không chỉ theo text
- Chương cuối: nút sau là `button[disabled]` → nextUrl = null
- URL: `/{slug-truyen}/chuong-{N}-{slug}`; trang truyện `/truyen/{slug-truyen}` (lấy tên truyện từ link này)
- `og:title` = "Chương X: … - {Tên truyện} - Akay Truyện"
- Test fixture: dùng HTML tổng hợp mô phỏng cấu trúc, **không** commit nội dung truyện thật
