# Phase 08 — Hoàn thiện và chuẩn bị release

## Phạm vi

Đầu vào: Phase 6–7 đã triển khai. Hoàn thiện hướng dẫn pin HyperOS, đường lỗi TTS hệ thống, cấu hình ký cá nhân và kiểm tra APK tối ưu. Không tự động sang chương.

Trạng thái: Đã triển khai; build/test/lint đạt. Đã thử 5 domain: 2 trích được chương, 3 không tải được do timeout/DNS/502. QA đầy đủ và nghe nền dài hạn còn pending. Người dùng chọn giữ APK chưa ký.

## Thực hiện

- [x] Hướng dẫn nghe khi tắt màn hình ở lần mở đầu; lưu trạng thái đã xem bằng DataStore.
- [x] Mở lại hướng dẫn từ Cài đặt; nội dung cuộn được khi dùng chữ lớn.
- [x] Nút yêu cầu bỏ qua tối ưu pin và mở thông tin ứng dụng; fallback nếu ROM không hỗ trợ intent.
- [x] Hướng dẫn Không hạn chế, Tự khởi chạy và khóa đa nhiệm; không tự bật cài đặt.
- [x] Mở phần chọn engine/giọng hệ thống của Android.
- [x] Sửa lỗi từ chối `speak`: vô hiệu hóa callback cũ, dừng âm thanh rồi phát `Failed` thuộc generation mới.
- [x] 5 regression test: lỗi lần đầu, lỗi khi bổ sung queue, stop, seek, queue bình thường.
- [x] Signing tùy chọn qua 4 biến môi trường, không commit key/mật khẩu; thiếu toàn bộ biến tạo APK chưa ký, cấu hình một phần báo lỗi.
- [x] Build debug/release, 83 unit tests, lint debug/release.
- [x] APK release minify/shrink nhỏ hơn 15 MB.
- [x] Xiaomi 13 / Android 16: cài/mở debug, hướng dẫn lần đầu, nhớ trạng thái qua force-stop/relaunch, mở lại hướng dẫn, mở cài đặt pin/app/TTS.
- [ ] Hoàn tất ma trận 5 trang, kiểm tra sạch nội dung và link chương theo từng trang.
- [x] Thử mở chương trên 5 domain, ghi kết quả và lỗi tải trang; không coi lỗi mạng là đạt extraction.
- [ ] Xác minh nghe nền 60 phút, màn hình khóa, tai nghe, hai giọng Edge, mất mạng và sleep fade.
- [ ] Kiểm thử APK release đã ký trên thiết bị (người dùng chọn giữ unsigned trong phiên này).

## Giới hạn

- Unit test kiểm tra logic queue thực tế dùng bởi SystemTtsEngine; không giả lập dịch vụ TTS Android.
- Android battery exemption không đồng nghĩa các hạn chế riêng HyperOS đã tắt; giao diện chỉ báo trạng thái Android.
- Lint giữ cảnh báo `BatteryLife` vì PDR yêu cầu yêu cầu miễn tối ưu pin cho APK cá nhân; cần đánh giá lại trước khi phân phối qua Play Store. Các cảnh báo baseline: `OldTargetApi`, `ExportedService`, `ObsoleteSdkInt`.
- Không đánh dấu các checklist thiết bị Phase 1–7 hoàn tất chỉ từ smoke test giao diện.

Chi tiết build và QA: [Hướng dẫn release](../../docs/release-guide.md).
