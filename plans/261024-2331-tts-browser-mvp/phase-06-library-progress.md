# Phase 06 — Thư viện, tiến độ, hẹn giờ tắt, HeadlessPageLoader

## Context
[PDR F6/F7/F8, luồng 2–5](../../docs/project-overview-pdr.md) · [Architecture §4, §5](../../docs/system-architecture.md) · Đầu vào: reader + `PlaybackConnection` từ [Phase 5](phase-05-reader-ui.md)

## Overview
Priority: P0 · Status: Done (assembleDebug + unit test pass 2026-10-25), chờ thử trên máy
Reader có "Lưu truyện" → truyện vào Thư viện. Vị trí đọc (chương + câu + charOffset) tự lưu khi đọc / pause / đóng app; Thư viện chạm → tải lại chương bằng WebView ẩn, mở reader ở đúng câu (dừng, chờ bấm Play). Hẹn giờ tắt 15/30/45/60 phút / hết chương. Thông báo có nút "Chương sau" (tải bằng WebView ẩn, đọc từ đầu).

## Key insights
- **Không có bảng `chapter`** (kiến trúc ban đầu có): chưa có đọc offline (Phase 2 sau MVP), mở lại chương = tải lại bằng `HeadlessPageLoader`. `book.currentChapterUrl` cũng bỏ: trùng `progress.chapterUrl`.
- **Chương nào thuộc truyện nào**: không giữ "truyện đang đọc" riêng. `progress.chapterUrl ∈ {url, prevUrl, nextUrl}` của chương đang xét → truyện đó. Đọc chương kế tiếp (reader, thông báo, tự bấm link trên web) vẫn khớp vì progress còn ở chương liền trước. Nhảy xa hơn 1 chương → coi như chưa lưu; "Lưu truyện" lại sẽ gộp vào truyện cũ theo (host, tên).
- `ProgressTracker` ghi progress khi chương trong player thuộc một truyện: tối đa 1 lần/2s (bản mới nhất, không bị đói khi câu ngắn) + ngay khi pause/hết chương + khi service bị gỡ. Chỉ ghi khi (truyện, chương, chỉ số câu) cùng thuộc một chương → `PlaybackConnection.load` đặt `loaded` **trước** `setChapter`.
- Khôi phục theo `charOffset` (`Sentence.start`) nếu cách tách câu đổi; khớp `sentenceIndex` + `start` thì dùng luôn.
- Mở lại từ Thư viện: chương đang nằm trong player → giữ nguyên vị trí sống (không pause); ngược lại nạp ở câu đã lưu rồi pause.
- Tên truyện gợi ý từ `document.title`/`og:title` (`BookTitleSuggester`: bỏ đoạn "Chương …", bỏ tên site ở cuối); người dùng sửa được trước khi lưu.
- Hẹn giờ: mốc theo `elapsedRealtime` (ngủ sâu không làm trễ), vòng kiểm tra 15s. **Không giảm dần âm lượng**: TTS hệ thống xếp hàng 5 câu và âm lượng đặt theo utterance → không đổi được câu đã xếp (PDR: "nếu engine hỗ trợ"); làm cùng Edge TTS (Phase 7). "Hết chương": player hiện đã tự dừng ở cuối chương (chưa có tự sang chương) nên chỉ gỡ hẹn giờ khi chương hết; lúc có tự sang chương thì chặn nó.
- `defaultSleepMinutes` (DataStore) bỏ: chưa có chỗ đặt.

## Requirements
- Room v3 (AutoMigration 2→3): `book(id, title, host, createdAt, updatedAt; unique host+title)`, `progress(bookId PK→book cascade, chapterUrl, chapterTitle, sentenceIndex, charOffset, updatedAt)`.
- Thư viện: danh sách theo `updatedAt` giảm dần, chạm = tiếp tục, xóa có xác nhận, báo lỗi tải chương.
- `HeadlessPageLoader`: WebView không gắn UI, timeout tải 15s, chờ trang ổn định 500ms sau `onPageFinished`, dùng chung `ReadabilityExtractor` + site_rule, hủy WebView sau khi xong.
- Thông báo / màn hình khóa: nút "Chương sau" chỉ hiện khi chương có `nextUrl`.

## Related files
Create:
- `data/db/`: `BookEntity`, `ProgressEntity`, `BookWithProgress`, `LibraryDao`; `data/repo/LibraryRepository.kt`
- `text/`: `ResumePosition.kt`, `BookTitleSuggester.kt`
- `extraction/`: `HeadlessPageLoader.kt`, `PageWebViewSettings.kt` (cấu hình WebView dùng chung)
- `playback/`: `ProgressTracker.kt`, `SleepTimer.kt`, `NextChapterLoader.kt`
- `ui/library/LibraryViewModel.kt`; `ui/player/SleepTimerSheet.kt`; `ui/reader/SaveBookDialog.kt`; `ui/browser/ExtractionMessages.kt`
- Test: `ResumePositionTest`, `BookTitleSuggesterTest`, `SleepTimerTest`
Modify: `AppDatabase`, `AppContainer`, `PlaybackConnection`, `PlaybackUiState`, `PlaybackService`, `Chapter`, `RawPage`, `ReadabilityExtractor`, `extract-page.js`, `BrowserWebView`, `ExtractionOverlay`, `LibraryScreen`, `ReaderViewModel`, `ReaderScreen`, `AppNavHost`, `strings.xml`, docs
Delete: không (`PlaceholderContent` vẫn dùng cho Thư viện trống / Cài đặt)

## Todo
- [x] Room: entity, DAO, migration, `LibraryRepository`
- [x] `ResumePosition`, `BookTitleSuggester` + test
- [x] `HeadlessPageLoader` + cấu hình WebView dùng chung
- [x] `ProgressTracker` (+ flush khi service gỡ)
- [x] `SleepTimer` + sheet + test
- [x] "Chương sau" trên thông báo (`NextChapterLoader`, custom command)
- [x] Thư viện UI + "Lưu truyện" trong reader + điều hướng
- [x] `assembleDebug` + `testDebugUnitTest` pass (50 test); lint không có lỗi mới; cập nhật docs
- [ ] Thử trên máy (Xiaomi 13): đóng/mở app → tiếp tục đúng câu; hẹn giờ khi tắt màn hình; "Chương sau" từ thông báo khi app ở nền (cần người dùng)

## Success criteria
Lưu truyện → đọc vài câu → đóng hẳn app → mở Thư viện → chạm → reader ở đúng câu, Play đọc tiếp. Hẹn giờ 15 phút dừng đúng hạn khi tắt màn hình. Thông báo "Chương sau" nạp và đọc chương kế tiếp khi app ở nền.

## Risks
- Trang cần Cloudflare/JS lâu hơn 15s → "Không tải được chương" (mở trang trong trình duyệt rồi bấm Đọc).
- WebView ẩn bị HyperOS hạn chế khi app ở nền → kiểm tra trên máy; nếu lỗi, `NextChapterLoader` báo qua Toast.
- Progress ghi trễ ≤2s: kill tiến trình đột ngột có thể mất vài câu.

## Security
Không thêm quyền. WebView ẩn dùng cùng cấu hình bảo mật (tắt file/content access, chặn scheme ngoài http/https, không JS bridge). Chỉ tải URL do trang đã trích (`nextUrl`) hoặc URL người dùng đã lưu.

## Next
Phase 7: Edge TTS (có thể thêm giảm âm lượng cho hẹn giờ).
