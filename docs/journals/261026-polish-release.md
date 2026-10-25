# Nhật ký — Phase 8, 2026-10-26

## Thực hiện

- Hướng dẫn nghe nền lần đầu và mở lại trong Cài đặt; lưu DataStore, hiển thị trạng thái Android battery exemption, mở cài đặt pin/app với fallback; hướng dẫn HyperOS cuộn được.
- Nút chọn engine/giọng hệ thống qua cài đặt Android.
- Tách SystemSpeechQueue để kiểm tra logic queue thực. Khi `speak` bị từ chối, dừng engine/vô hiệu callback cũ trước rồi phát Failed dưới generation mới. 5 regression test bao phủ lỗi queue đầu/top-up và stop/seek.
- Signing tùy chọn qua 4 biến môi trường. Người dùng chọn giữ APK chưa ký, không tạo key hay ký bằng debug key.

## Xác minh

- `assembleDebug testDebugUnitTest lintDebug assembleRelease lintRelease` thành công sau thay đổi cuối.
- 83 unit tests, 0 failure/error. Lint cả hai biến thể: 0 error, 4 warning (`OldTargetApi`, `BatteryLife`, `ExportedService`, `ObsoleteSdkInt`). BatteryLife được giữ và giải thích theo phạm vi APK cá nhân.
- Release unsigned minify/shrink: 3.049.157 byte, 2,91 MiB, dưới 15 MB. Chưa xác minh release trên thiết bị vì chưa ký.
- Xiaomi 13 Android 16: hướng dẫn lần đầu/nhớ trạng thái/mở lại; mở đúng màn hình pin, thông tin app, giọng TTS. Đã cài APK debug cuối cùng.
- Thử 5 domain: Akay và TruyenFull mở đúng chương trong Reader; truyen.tangthuvien.vn timeout, tangthuvien.org Cloudflare 502, truyenhdx.com DNS lỗi. Không coi các trang không tải được là extraction đạt.
- Akay chương 1: 117 câu, system TTS chuyển câu. Khi màn hình tắt (Dozing), MediaSession PLAYING và active item tiến 5 → 9; media pause đưa về PAUSED. Chỉ là smoke test ngắn, không chứng minh nghe thực tế hay 60 phút ổn định.
- Đã dừng phát và khôi phục subtype Gboard tiếng Việt; không đổi các công tắc pin/giọng đọc. Máy khóa màn hình sau kiểm tra tắt màn hình.

## Còn lại

QA toàn chương/link/chọn vùng thủ công, thử lại các trang lỗi mạng, nghe 60 phút, Bluetooth, Edge hai giọng/mất mạng/sleep fade; release đã ký nếu người dùng cần sau này. Không đánh dấu MVP hoàn tất.
