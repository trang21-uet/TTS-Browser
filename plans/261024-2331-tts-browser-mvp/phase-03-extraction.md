# Phase 03 — Extraction (Readability, cleaner, link finder, segmenter, element picker)

## Context
[PDR F2, F3](../../docs/project-overview-pdr.md) · [Architecture §2–§5](../../docs/system-architecture.md) · Findings akaytruyen.com ở [plan.md](plan.md)

## Overview
Priority: P0 · Status: Done (assembleDebug + unit test pass 2026-10-25; script JS kiểm bằng jsdom trên HTML tổng hợp), chờ thử trên máy
FAB "Đọc" trên trình duyệt → chạy Readability.js trong WebView → làm sạch văn bản, tìm link chương trước/sau, tách câu → `Chapter` + `List<Sentence>`. Nội dung < 200 ký tự hoặc lỗi → chọn vùng thủ công; selector nhớ theo host (`site_rule`).
Reader UI thật làm ở Phase 5; phase này hiện kết quả trong một bottom sheet xem trước để kiểm tra trích.

## Key insights
- Readability.js 0.6.0 (Apache-2.0) bundle trong `assets/js/`. Nối nguồn vào **trong IIFE** mỗi lần trích → không rò global, trang không ghi đè được `Readability`. Chạy trên `document.cloneNode(true)` vì Readability sửa DOM.
- `textContent` của Readability mất ngắt đoạn → chuyển `content` (HTML) sang text bằng walker riêng, parse qua `DOMParser` (document trơ, `<img onerror>` không chạy).
- JS chỉ **thu thập** ứng viên link (href, text, aria/title, id/class, class icon con, disabled); chấm điểm ở Kotlin (`ChapterLinkFinder`) → unit test được.
- akaytruyen: cả 2 nút có class `btn-back-next` → token id/class chứa cả next lẫn back thì bỏ qua, dựa vào icon `fa-chevron-left/right`. Chương cuối: nút sau là `button[disabled]` → không có ứng viên → `nextUrl = null`.
- Không có JS bridge: kết quả trả qua `evaluateJavascript` (chuỗi JSON). Element picker cài `window.__ttsPicker`, Kotlin poll trạng thái 500ms khi đang chọn.
- Tiêu đề: ưu tiên `h1` khi tiêu đề Readability/`document.title` chứa nó ("Chương 5: X - Truyện - Site" → "Chương 5: X").
- Tách câu bằng `java.text.BreakIterator` (Android dùng ICU bên dưới, JVM test chạy được), câu > 300 ký tự cắt theo `,;:` rồi khoảng trắng.

- Code review 2026-10-25 (đã sửa): số chương chỉ so trong cùng truyện (host + path trước `chuong-N`) và marker phải đầu từ (`sach24.vn` không phải chương 24); đổi trang → hủy trích/kết quả cũ; poll picker tuần tự có timeout, picker mất (reload) → thoát chế độ chọn; không nuốt `CancellationException`; cache script Readability; báo lỗi khi không lưu được `site_rule`; bộ lọc watermark chỉ dùng cụm cụ thể.

## Requirements
- Thứ tự trích: có `site_rule` cho host → dùng selector; selector không khớp/quá ngắn → Readability.
- Văn bản: gộp khoảng trắng, giữ đoạn; bỏ dòng điều hướng ("Chương trước | Mục lục | Chương sau"), dòng chỉ ký hiệu.
- Link chương: `rel=next/prev` > text/aria (`Chương sau/tiếp`, `Next`, `›`, `»`, `→`…) > id/class (`next`, `chap-next`…) > icon (`*-right/left`) > số chương trong URL (±1). Bỏ link `javascript:`, trùng trang hiện tại, disabled.
- Picker: chạm khối nội dung → viền cam; "Mở rộng" chọn element cha; "Xác nhận" lưu selector theo host và trích lại; "Hủy". Đổi trang → thoát picker.
- Sheet kết quả: tiêu đề, số đoạn/câu/ký tự, nội dung, nút Chương trước/sau, "Chọn vùng khác", "Bỏ vùng đã chọn" (khi đang dùng selector).

## Related files
Create:
- `assets/js/Readability.js`, `assets/js/extract-page.js`, `assets/js/element-picker.js`
- `extraction/`: `Chapter.kt`, `PageScriptRunner.kt` (+ `ExtractionScripts`), `RawPage.kt` (+ `JsResult` parser), `TextCleaner.kt`, `ChapterLinkFinder.kt`, `ReadabilityExtractor.kt`, `ElementPicker.kt`
- `text/SentenceSegmenter.kt`
- `data/db/SiteRuleEntity.kt`, `data/db/SiteRuleDao.kt`, `data/repo/SiteRuleRepository.kt`
- `ui/browser/ExtractionViewModel.kt`, `ExtractionOverlay.kt` (FAB + dialog lỗi), `ExtractionResultSheet.kt`, `ElementPickerBar.kt`
- Test: `TextCleanerTest`, `ChapterLinkFinderTest`, `SentenceSegmenterTest`, `JsResultTest`, `HostOfTest`
Modify: `AppDatabase.kt` (v2, AutoMigration 1→2), `AppContainer.kt`, `AppNavHost.kt`, `BrowserScreen.kt`, `strings.xml`, `libs.versions.toml`, `app/build.gradle.kts` (org.json cho unit test)

## Todo
- [x] Bundle Readability.js + script trích + script picker
- [x] TextCleaner, ChapterLinkFinder, SentenceSegmenter, RawPage parser + unit test (fixture tổng hợp akaytruyen)
- [x] site_rule (Room v2, AutoMigration) + repository
- [x] ReadabilityExtractor, ElementPicker
- [x] UI: FAB "Đọc", sheet kết quả, thanh picker
- [x] `assembleDebug` + `testDebugUnitTest` pass
- [ ] Thử trên máy: akaytruyen.com (tự động), một trang Readability trích sai (chọn vùng) (cần người dùng)

## Success criteria
Trên akaytruyen.com: bấm "Đọc" ra đúng nội dung chương, tiêu đề, link chương sau/trước (chương cuối không có chương sau). Trang trích sai → chọn vùng → các chương sau cùng host tự dùng selector.

## Risks
- Selector sinh ra phụ thuộc `:nth-of-type` → đổi layout là hỏng: fallback Readability + nút "Bỏ vùng đã chọn".
- Trang ghi đè hàm DOM chuẩn: chấp nhận, kết quả chỉ là dữ liệu.

## Security
Script chỉ đọc DOM, trả dữ liệu qua `evaluateJavascript`; không `addJavascriptInterface`. HTML Readability parse bằng `DOMParser` (không thực thi). Kết quả từ trang được coi là dữ liệu không tin cậy (chỉ hiển thị dạng text).

## Next
Phase 4 nhận `List<Sentence>` cho TtsPlayer; Phase 5 thay sheet xem trước bằng ReaderScreen; Phase 6 dùng `ReadabilityExtractor` cho HeadlessPageLoader.
