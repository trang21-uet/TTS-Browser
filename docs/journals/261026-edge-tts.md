# Nhật ký — Edge TTS, 2026-10-26

## Bối cảnh
Tiếp tục MVP sau Phase 6: giọng Edge tiếng Việt, tích hợp luồng phát nền đang có và fallback về system.

## Thực hiện
- Thêm WebSocket client/token v2, ExoPlayer theo câu, tiền tải 3 câu và LRU RAM 4 MiB; timeout/giới hạn phản hồi, hủy request khi seek/stop.
- Settings chọn System/Edge và Hoài My/Nam Minh, lưu tốc độ/cao độ riêng; tốc độ system cũ giữ nguyên. Fade Edge trong 15 giây cuối hẹn giờ.
- Fallback đọc lại câu hiện tại, snackbar foreground/Toast background; bundle attribution/license LGPL cho token port.

## Kết quả và nhận xét
- Review độc lập 9.5/10. Đã sửa timeout bị coi là cancellation và giữ nguồn audio cũ khi queue thiếu dữ liệu; không còn Critical/High trong static review.
- Kiểm tra cuối `assembleDebug` và 78 unit tests pass (0 failure/error). Lint pass 0 error, 3 warning baseline (`OldTargetApi`, `ExportedService`, `ObsoleteSdkInt`) sau sửa `PropertyEscape` trong SDK path ignored. Diff check và secret scan sạch.
- Live endpoint trả 11.232 byte MP3 cho câu tổng hợp “Xin chào”. Xiaomi 13 cài/mở app, chọn Edge Nam Minh rồi force-stop/relaunch vẫn giữ cài đặt, không crash. Chưa suy ra toàn bộ checklist phát âm thanh/nền đạt.

## Quyết định
Không cache audio xuống đĩa, không tự sang chương; mặc định dùng system. Phân biệt request timeout với cancellation của người dùng. Gỡ nguồn đã phát cả khi playback từng bị thiếu dữ liệu.

## Tiếp theo
Tiếp tục QA âm thanh/nền trên thiết bị. Phase 8: HyperOS/battery guide, đa trang, APK release/signing và kích thước <15 MB (debug 25.466.203 byte, khoảng 24,3 MiB). Kiểm tra lỗi baseline `SystemTtsEngine.queueMore` tăng generation sau emit `Failed`, khiến watchdog có thể chỉ báo lỗi sau 90s. Checklist thiết bị Phase 1–6 giữ pending.
