# Phase 05 — Reader UI (highlight, seek, theme, mini player)

## Context
[PDR F3, luồng 1](../../docs/project-overview-pdr.md) · [Architecture §2, §4](../../docs/system-architecture.md) · Đầu vào: `TtsPlayer` + `PlaybackConnection` từ [Phase 4](phase-04-tts-core.md)

## Overview
Priority: P0 · Status: Done (assembleDebug + unit test pass 2026-10-25), chờ thử trên máy
FAB "Đọc" → trích → nạp chương vào player (giữ trạng thái phát) → mở **ReaderScreen** (thay sheet kết quả trích). Reader: văn bản sạch, tô sáng câu đang đọc, tự cuộn, chạm câu để đọc từ đó, cỡ chữ / nền (Hệ thống/Sáng/Tối/Sepia) / tốc độ lưu DataStore. Mini player trên thanh tab khi có chương đã nạp. Lỗi phát hiện trong reader / mini player thay Toast.

## Key insights
- State UI đọc **thẳng từ `TtsPlayer`** (cùng process, main thread) qua `Player.Listener.onEvents` → `PlaybackUiState` (index câu, nút play theo `Util.shouldShowPlayButton`, buffering, lỗi): đúng cả khi chưa/không còn `MediaController`. Lệnh vẫn qua controller để service được bind.
- Reader hiện chương **vừa trích** (`ReaderViewModel.shown`), **không** thay hàng đợi đang phát; chỉ khi bấm Play / chạm câu mới `load()` vào player. Highlight, vị trí, lỗi chỉ hiện khi player đang giữ đúng chương đó. Ngoại lệ: "Chương trước/sau" từ reader khi chương đang hiện đang phát → nạp ngay, đọc tiếp. Mini player luôn theo chương trong player; chạm → reader hiện chương đó.
- `PlaybackConnection.load()`: cùng URL + cùng câu → chỉ cập nhật metadata (nguồn, link, tiêu đề), giữ vị trí; khác → `setChapter` (giữ play/pause).
- Chạm câu = `prepare` (nếu IDLE/lỗi) + `seekTo(i)` + `play()`; không dùng `handlePlayButtonAction` vì ENDED sẽ về câu 0.
- Mỗi đoạn = 1 item `LazyColumn`; mỗi câu là `LinkAnnotation.Clickable` trong `AnnotatedString` (không cần tự tính offset chạm). Offset câu trong đoạn tính từ `Sentence.start` (`sentenceSpansByParagraph`, có test). Chỉ đoạn chứa câu hiện tại tính lại chuỗi.
- Tự cuộn: dùng `TextLayoutResult` của đoạn để biết dòng của câu; chỉ cuộn khi câu ra ngoài viewport, đặt câu ở 1/4 màn hình. Lần đầu nhảy, sau đó animate. Người dùng kéo danh sách → tắt theo dõi, hiện nút "Về câu đang đọc"; chạm câu cũng bật lại.
- Reader ↔ trình duyệt: `ExtractionViewModel` lên scope Activity; reader gửi `PageRequest` (Pick, ClearRule, ExtractWhenLoaded) chạy khi tab trình duyệt hiện lại. "Chương trước/sau" = tải URL trong WebView rồi tự trích khi tải xong (HeadlessPageLoader khi app ở nền: Phase 6). Request trích bị hủy nếu trang chuyển sang URL khác (redirect).
- "Chọn vùng khác"/"Bỏ vùng đã chọn" chỉ hiện khi trang trình duyệt vẫn là chương đang đọc (`pageResult` cùng URL).
- Theme reader = `MaterialTheme` lồng: Hệ thống giữ màu app (dynamic color), Sáng/Tối dùng màu app nếu trùng chế độ hệ thống, Sepia tự định nghĩa; icon status bar theo độ sáng nền, trả lại khi rời reader. Reader tự xử lý insets (không nhận padding của Scaffold ngoài) để màu nền phủ status bar.
- Tốc độ: lưu DataStore, `AppContainer` collect ngay khi tạo `TtsPlayer` (kể cả service khởi động không có UI) → nguồn duy nhất; session bỏ `COMMAND_SET_SPEED_AND_PITCH` khỏi controller ngoài. Slider tốc độ chỉ lưu khi thả tay; cỡ chữ đổi khi kéo, thumb giữ state cục bộ trong lúc kéo.
- DataStore: `ReplaceFileCorruptionHandler`, lỗi IO → mặc định + thử đọc lại (flow không bao giờ kết thúc vì lỗi).

- Code review 2026-10-25 (đã sửa): request "chương sau" bị hủy khi redirect (giờ: tới URL đích hoặc bất kỳ trang nào khác trang lúc yêu cầu, chờ tải xong ổn định 700ms); sự kiện mở reader bị giữ lại rồi mở muộn (giờ collect ở Activity, bỏ nếu không ở tab trình duyệt); trích trang thay hàng đợi đang phát; metadata cũ khi trích lại; slider cỡ chữ giật; tự cuộn dùng layout cỡ chữ cũ; AppNavHost recompose mỗi câu; controller ngắt sớm làm mất lệnh; tốc độ không áp khi service tự khởi động; `pageOf` trùng lặp.

## Requirements
- Văn bản sạch, tô sáng câu, tự cuộn, chạm câu để đọc từ đó.
- Cỡ chữ 14–30sp, nền Hệ thống/Sáng/Tối/Sepia, tốc độ 0.5–3.0 (bước 0.1), lưu qua lần mở app.
- Mini player (tiêu đề, câu i/n hoặc lỗi, play/pause, tiến độ) trên mọi tab; chạm mở reader.

## Related files
Create:
- `data/settings/SettingsRepository.kt` (DataStore: `fontSize`, `readerTheme`, `rate`)
- `playback/PlaybackUiState.kt` (`LoadedChapter`, `PlaybackUiState`)
- `ui/reader/`: `ReaderScreen.kt`, `ReaderViewModel.kt`, `ReaderText.kt`, `ReaderControls.kt`, `ReaderDisplaySheet.kt`, `ReaderColors.kt`, `SentenceSpans.kt`, `ChapterNavRow.kt`
- `ui/player/`: `MiniPlayerBar.kt`, `PlayPauseButton.kt`
- `ui/browser/ExtractionUiState.kt` (tách từ ViewModel, thêm `PageRequest`)
- Test: `SentenceSpansTest`
Modify: `PlaybackConnection.kt`, `AppContainer.kt`, `ExtractionViewModel.kt`, `ExtractionOverlay.kt`, `BrowserScreen.kt`, `AppNavHost.kt`, `Theme.kt`, `TtsPlayerCommands.kt` (opt-in lint), `strings.xml`, `libs.versions.toml`, `app/build.gradle.kts` (datastore-preferences 1.3.0)
Delete: `ui/browser/ExtractionResultSheet.kt`

## Todo
- [x] SettingsRepository (DataStore) + áp tốc độ vào player
- [x] PlaybackConnection: `load`, state flow, lệnh play/pause/seek/câu trước-sau; bỏ Toast lỗi
- [x] ReaderScreen: highlight, chạm câu, tự cuộn + nút theo dõi, chương trước/sau, menu trang
- [x] Sheet hiển thị: cỡ chữ, nền, tốc độ
- [x] Mini player trên thanh tab
- [x] `assembleDebug` + `testDebugUnitTest` pass; lint không có lỗi mới
- [ ] Thử trên máy (Xiaomi 13): chương dài ~1000 câu (độ mượt cuộn), chạm câu, đổi theme/cỡ chữ khi đang đọc, chương sau từ reader (cần người dùng)

## Success criteria
Bấm "Đọc" → reader hiện văn bản; Play → câu đang đọc tô sáng và tự cuộn; chạm câu bất kỳ → đọc từ câu đó; đổi cỡ chữ/nền/tốc độ có hiệu lực ngay và còn sau khi mở lại app; về tab khác vẫn thấy mini player điều khiển được.

## Risks
- Chương rất dài: mỗi đoạn có nhiều `LinkAnnotation` → theo dõi độ mượt; nếu chậm, đổi sang `pointerInput` + `getOffsetForPosition`.
- Trang redirect khi bấm "Chương sau" → không tự trích (request bị hủy), người dùng bấm "Đọc".
- `PlaybackUiState.error` dùng `PlaybackException.message` (đã là chuỗi tiếng Việt từ TtsPlayer).

## Security
Không thêm quyền, không dữ liệu ra ngoài. DataStore chỉ lưu cài đặt hiển thị/tốc độ.

## Next
Phase 6: Room `book/chapter/progress` (lưu/khôi phục vị trí câu), "Lưu truyện", hẹn giờ tắt, HeadlessPageLoader + custom command "Chương sau" trên thông báo.
