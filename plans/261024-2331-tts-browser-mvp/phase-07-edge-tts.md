# Phase 07 — Edge TTS

## Context
[PDR F5/F8](../../docs/project-overview-pdr.md) · [Architecture](../../docs/system-architecture.md) · Đầu vào: [Phase 6](phase-06-library-progress.md).

## Overview
Priority: P0 · Status: Done (review 9.5/10, assembleDebug + 78 unit test + lint pass 2026-10-26); đã smoke test Xiaomi 13, còn checklist phát âm thanh/nền
Đầu vào: Phase 6 đã triển khai. Mục tiêu: chọn Edge với hai giọng Việt, đọc nền và giữ highlight/seek; lỗi mạng tự chuyển hệ thống từ câu hiện tại.

## Thiết kế và phạm vi
- OkHttp WebSocket, SSML escape XML, giới hạn kích thước phản hồi và timeout; token theo giao thức upstream edge-tts, không ghi log văn bản.
- EdgeTtsEngine dùng ExoPlayer, tiền tải tối đa 3 câu, cache RAM giới hạn theo byte; hủy request/job và bỏ callback cũ khi stop/seek/release.
- Engine bọc fallback: khi Edge lỗi, dừng Edge và khởi tạo system để đọc lại câu hiện tại; thông báo một lần. Cài đặt mặc định vẫn là system.
- DataStore lưu engine/giọng Edge, rate/pitch riêng theo engine; Settings cho chọn engine/giọng/cao độ. Tốc độ trong reader dùng engine đang chọn.
- Đổi engine/giọng khi đang nghe: giải phóng engine cũ, tiếp tục từ câu hiện tại. ExoPlayer không tự xử lý audio focus (TtsPlayer đang quản lý).
- Giảm âm lượng trong 15 giây cuối hẹn giờ với Edge; khôi phục khi hủy/hết giờ. Không đổi hành vi system.
- Kiểm thử protocol/token/cache/fallback và build/unit/lint; thử giọng thực/đọc nền trên Xiaomi cần thiết bị.

## Related files
- `tts/edge/`: `EdgeTtsEngine`, `EdgeTtsClient`, `EdgeProtocol`, `EdgeTokenGenerator`, `AudioMemoryCache`.
- `tts/FallbackTtsEngine.kt`, `TtsEngine`, `SpeechDriver`, `TtsPlayer`, `SleepTimer`, `AppContainer`.
- `SettingsRepository`, `SettingsViewModel`, `SettingsScreen`, `AppNavHost`, strings, Gradle dependencies, manifest `WAKE_LOCK`.
- `assets/licenses/`: upstream LGPL và GPL; test protocol/token/cache, fallback, speech preferences và sleep fade.

## Review thiết kế
Đã đối chiếu PDR F5, kiến trúc TtsEngine/SpeechDriver. Không tự sang chương, không lưu audio xuống đĩa. Cần kiểm tra giao thức upstream hiện tại trước triển khai vì endpoint không chính thức có thể đổi.

Review độc lập 9.5/10, không còn Critical/High. Đã sửa hai đường lỗi trong review: timeout WebSocket phải thành `IOException` để kích hoạt fallback; queue sau starvation vẫn gỡ audio câu cũ để không giữ cả chương trong RAM.

## Todo
- [x] Client/protocol/token + test
- [x] Edge engine/prefetch/cache + fallback
- [x] Settings và tích hợp player
- [x] Hẹn giờ giảm âm lượng
- [x] Code review độc lập: 9.5/10, không còn Critical/High
- [x] Kiểm tra cuối `assembleDebug` + `testDebugUnitTest`: 78 tests, 0 failure, 0 error
- [x] `lintDebug`: 0 error, 3 warning baseline (`OldTargetApi`, `ExportedService`, `ObsoleteSdkInt`)
- [x] Diff check và secret scan sạch
- [x] Đồng bộ docs/plan và nhật ký
- [x] Xiaomi 13: cài đè debug, mở app, chọn Edge Nam Minh, force-stop/mở lại vẫn giữ cài đặt; không crash
- [ ] Kiểm thử thiết bị: hai giọng, mất mạng, seek/pause, tắt màn hình, hẹn giờ

## Validation và giới hạn
- Live endpoint đã trả 11.232 byte MP3 cho câu tổng hợp “Xin chào”; xác nhận giao thức/token hoạt động tại thời điểm thử.
- Xiaomi 13 đã cài APK mới nhất/mở app và kiểm tra cài đặt Edge Nam Minh tồn tại qua force-stop/relaunch, không crash. Fixture HTTPS chưa nhập được chính xác qua bàn phím ADB/Gboard nên chưa xác nhận playback trên thiết bị. Đã khôi phục System/Hoài My và giữ các cài đặt khác; không có server hay job thử nghiệm còn chạy.
- APK debug 25.466.203 byte (khoảng 24,3 MiB). Mục tiêu APK release <15 MB cần kiểm tra trong Phase 8 với minify/shrink và signing.
- Lint lần đầu vướng baseline `PropertyEscape` của `local.properties`; đã sửa escape đường SDK trong file ignored. Lần kiểm tra cuối pass 0 error, còn 3 warning baseline nêu trên.

## Risks
- Endpoint không chính thức có thể đổi giao thức hoặc chặn; system là fallback và mặc định.
- Unit tests kiểm tra logic protocol/cache/fallback; chuyển câu ExoPlayer, audio thực và pin HyperOS cần thử thiết bị.
- Giới hạn RAM: 4 MiB cache + cửa sổ nguồn/request tối đa 3 câu, mỗi phản hồi audio tối đa 2 MiB; đây không phải cache offline.

## Next
Phase 8: hướng dẫn pin HyperOS, polish, thử đa trang và release APK. Giữ các checklist thiết bị Phase 1–6 pending. Ghi nhận lỗi baseline `SystemTtsEngine.queueMore`: `Failed` bị lọc khi tăng generation ngay sau emit; cần fix + regression test trong Phase 8.
