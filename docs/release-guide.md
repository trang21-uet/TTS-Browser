# Build release và kiểm tra trước khi dùng

## Build

Yêu cầu JDK 21 và Android SDK theo README. Từ thư mục gốc, chạy PowerShell:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug assembleRelease lintRelease --console=plain
```

Không cấu hình signing sẽ tạo `app/build/outputs/apk/release/app-release-unsigned.apk`. APK chưa ký không cài được trực tiếp. R8 và resource shrink đã bật; không dùng debug key làm key phát hành cá nhân.

Người dùng chọn giữ APK chưa ký trong phiên Phase 8 ngày 2026-10-26. Khi cần ký sau này, đặt đủ các biến môi trường trong môi trường build:

| Biến | Giá trị |
|---|---|
| `TTS_KEYSTORE_PATH` | Đường dẫn keystore cá nhân, ưu tiên ngoài repository |
| `TTS_KEYSTORE_PASSWORD` | Mật khẩu keystore |
| `TTS_KEY_ALIAS` | Alias của key |
| `TTS_KEY_PASSWORD` | Mật khẩu key |

Chạy lại `assembleRelease`; kết quả là `app-release.apk`. Không truyền mật khẩu qua tham số dòng lệnh, không đưa vào file tracked. `.jks` và `.keystore` đã được ignore. Giữ bản sao key an toàn: những lần cập nhật phải dùng cùng key. APK debug đã cài có thể dùng key khác; sao lưu tiến độ trước khi gỡ app để đổi key.

## Phát hành tự động trên GitHub

- `build-apk.yml`: mỗi lần push lên `main` build APK debug và đính vào kết quả chạy (Artifacts).
- `release-apk.yml`: khi push lên `main` làm thay đổi `versionName`/`versionCode` trong `app/build.gradle.kts`, workflow build APK release đã ký và tạo GitHub release `v<versionName>` kèm `TTS-Browser-<versionName>.apk`. Nội dung release lấy từ mục `## [<versionName>]` trong [project-changelog.md](project-changelog.md); không có mục đó thì release chỉ ghi tên phiên bản.
- Ký bằng 4 GitHub secret: `TTS_KEYSTORE_BASE64`, `TTS_KEYSTORE_PASSWORD`, `TTS_KEY_ALIAS`, `TTS_KEY_PASSWORD`. Bản sao keystore nằm ngoài repo, phải sao lưu.

Quy trình ra bản mới: trong cùng commit thay đổi code, tăng `versionName` (SemVer) và `versionCode` (+1) trong `app/build.gradle.kts`, thêm mục `## [x.y.z]` vào `docs/project-changelog.md`, rồi push lên `main`. Commit chỉ đổi tài liệu thì không tăng version nên không tạo release. Gỡ bản debug trước khi cài bản release vì hai bản ký khác key.

## Nghe nền / HyperOS

Hướng dẫn xuất hiện lần đầu và có thể mở lại trong **Cài đặt → Nghe khi tắt màn hình**. App chỉ mở các màn hình hệ thống khi người dùng bấm nút. Có thể đóng hướng dẫn để nghe ngay.

1. Bấm **Cho phép bỏ qua tối ưu pin**. Một số ROM mở trang quản lý pin riêng thay vì hộp thoại Android.
2. Mở **Thông tin ứng dụng**, tìm mục Pin / Tiết kiệm pin và chọn **Không hạn chế** nếu có.
3. Tìm **Tự khởi chạy trong nền** trong cài đặt ứng dụng/quyền và bật TTS Browser nếu cần.
4. Khóa thẻ TTS Browser trong đa nhiệm nếu ROM có lựa chọn này.

Tên menu thay đổi theo ROM. App chỉ đọc được trạng thái miễn tối ưu pin Android, không xác minh được các công tắc HyperOS. Miễn tối ưu pin không bảo đảm hệ thống không bao giờ dừng app.

Đối chiếu: [Android Doze và miễn tối ưu pin](https://developer.android.com/training/monitoring-device-state/doze-standby), [Xiaomi: Background autostart](https://www.mi.com/global/support/faq/details/KA-507611/). Cảnh báo lint `BatteryLife` được giữ lại và ghi nhận; bản này phân phối APK cá nhân theo PDR.

## Kiểm tra trên thiết bị

Máy trong phiên: Xiaomi 13 (model 2211133C), Android 16. Chỉ ghi đạt khi có bằng chứng cho đúng mục kiểm tra.

| Mục | Kết quả |
|---|---|
| Cài/mở bản debug Phase 8 | Đạt |
| Hướng dẫn lần đầu, đóng bằng Back, force-stop/mở lại không tự hiện | Đạt |
| Mở lại hướng dẫn trong Cài đặt | Đạt |
| Nút tối ưu pin | Mở được Battery details của HyperOS; không thay đổi công tắc pin |
| Nút thông tin ứng dụng | Mở đúng TTS Browser |
| Chọn engine/giọng hệ thống | Mở đúng Text-to-speech settings; không thay đổi giọng |
| Nghe liên tục 60 phút / màn hình khóa / Bluetooth | Chưa xác minh |
| System TTS khi màn hình tắt, kiểm tra ngắn | MediaSession vẫn PLAYING, active item 5 → 9; máy báo Dozing. Lệnh media pause đưa về PAUSED. Không thay thế kiểm tra âm thanh/pin 60 phút |
| Edge hai giọng, mất mạng fallback, sleep fade | Chưa xác minh trong Phase 8 |
| APK release đã ký và chạy qua R8 trên thiết bị | Chưa xác minh; giữ unsigned theo lựa chọn người dùng |

## Ma trận đa trang

Mỗi trang cần kiểm tra: mở chương thực, bấm Đọc, xem nội dung đầu/cuối không lẫn menu/bình luận, link trước/sau, chọn vùng thủ công nếu cần. Mở được Reader chỉ xác nhận smoke test trích nội dung, chưa chứng minh toàn bộ chương sạch. Không lưu nội dung truyện vào repository.

| Trang / URL thử | Kết quả |
|---|---|
| `https://truyenfull.vision/chuong-chau/chuong-1/` | Chuyển sang `truyenfull.live`, Reader hiện đúng tiêu đề Chưởng Châu và các đoạn mở đầu; chưa kiểm tra toàn chương/link |
| `https://akaytruyen.com/ke-phan-quyet/1-anh-hung-tro-ve` | Reader hiện đúng chương 1, nội dung mở đầu, 117 câu; system TTS chuyển câu và MediaSession báo PLAYING; chưa kiểm tra toàn chương/link |
| `https://truyen.tangthuvien.vn/doc-truyen/chap-ma/chuong-1` | WebView báo `ERR_CONNECTION_TIMED_OUT`, chưa thử được extraction |
| `https://tangthuvien.org/cau-chu-tuoc/1` | Máy chủ trả Cloudflare 502, chưa thử được extraction |
| `https://truyenhdx.com/truyen/vuong-phi-cua-quy-vuong/chap/7310525-chuong-1` | WebView báo `ERR_NAME_NOT_RESOLVED`, chưa thử được extraction |

Trên thiết bị dùng Gboard tiếng Việt, `adb input text` có thể biến URL do Telex. Khi thử đã chuyển tạm sang English và dùng nút Go của bàn phím; đã khôi phục subtype tiếng Việt sau QA. Đã dừng phát và cài lại APK debug cuối cùng; không thay đổi engine/giọng hoặc công tắc pin của người dùng.

## Kết quả build Phase 8

Lần kiểm tra cuối: `assembleDebug testDebugUnitTest lintDebug assembleRelease lintRelease` thành công. 83 test, 0 failure/error. Lint debug/release: 0 error, 4 warning đã nêu trong kế hoạch Phase 8. APK unsigned: 3.049.157 byte (2,91 MiB), dưới 15 MB. Chưa cài bản release vì chưa ký; kiểm tra thiết bị dùng bản debug.
