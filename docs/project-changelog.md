# Changelog

Mỗi phiên bản có một mục `## [x.y.z]`. Workflow `release-apk.yml` lấy nội dung mục khớp với `versionName` làm ghi chú của GitHub release.

## [0.6.0] - 2026-11-13

### Added

- Chặn quảng cáo trong trình duyệt: bỏ qua các yêu cầu tới máy chủ quảng cáo và theo dõi (danh sách `ad-hosts.txt` đóng gói trong app) để trang tải nhanh và ít popup hơn; không chặn trang đang mở. Cũng áp dụng khi app tải chương nền. Bật mặc định, tắt ở **Cài đặt → Trình duyệt**.

## [0.5.0] - 2026-11-13

### Added

- Tùy chọn hiển thị cho màn hình đọc (nút **Hiển thị**): chọn phông chữ (mặc định, Be Vietnam Pro, Lora, Serif, Monospace), cỡ chữ, màu nền (thêm Bạc hà và Đen) và màu chữ riêng (Tự động, Đen, Than, Nâu, Xám, Kem, Trắng). Be Vietnam Pro và Lora được đóng gói theo giấy phép SIL OFL.
- Tự ẩn thanh công cụ khi cuộn xuống, cuộn lên để hiện lại: thanh địa chỉ, mini player và thanh tab của trình duyệt; thanh tiêu đề và thanh điều khiển của màn hình đọc. Bật mặc định, tắt ở **Cài đặt → Đọc truyện**.

### Changed

- Màn hình đọc dễ nhìn hơn: giãn dòng 1.7, đoạn cách nhau rộng hơn, lề 24dp và cột chữ tối đa 680dp, căn giữa trên màn hình rộng.

## [0.4.0] - 2026-11-12

### Added

- Ảnh bìa cho từng truyện: khi lưu truyện, app lấy ảnh đại diện (`og:image`) của trang truyện làm bìa; có thể đổi sang ảnh trong máy hoặc xóa bằng menu ⋮ của truyện.
- Thư viện có hai cách xem, đổi bằng nút ở đầu màn hình (được nhớ lại): lưới 2 truyện mỗi dòng với ảnh bìa lớn, hoặc danh sách; cả hai đều hiện tên truyện và chương đang đọc.

### Changed

- Cơ sở dữ liệu thêm cột ảnh bìa (tự migrate, giữ nguyên truyện đã lưu); truyện đã lưu trước đây chưa có bìa cho đến khi chọn ảnh hoặc lưu lại từ trang truyện.

## [0.3.1] - 2026-11-12

### Fixed

- Giữ đăng nhập của trang truyện: bật cookie bên thứ ba cho trình duyệt và trang tải ngầm, và ghi cookie xuống đĩa ngay sau mỗi trang, khi app tạm dừng và sau khi tải chương.

## [0.3.0] - 2026-11-12

### Added

- Kiểm tra cập nhật: app tự tìm bản mới trên GitHub khi mở (tắt được), hoặc bấm **Cài đặt → Cập nhật → Kiểm tra cập nhật**.
- Tải APK bản mới ngay trong app, có thanh tiến độ chạy nền và nút Huỷ; tải xong bấm **Cài đặt** để mở trình cài đặt của Android.

## [0.2.0] - 2026-11-12

### Added

- Tự động đọc tiếp chương sau khi đọc hết chương hiện tại (hẹn giờ "hết chương" vẫn dừng lại).
- Nút trước/sau của thông báo, màn hình khóa và tai nghe chuyển chương trước/sau.

### Changed

- Thanh điều khiển của chế độ đọc: chuyển chương trước/sau là nút chính cạnh nút phát, tua câu trước/sau nằm ở hai bên ngoài.

## [0.1.0] - 2026-11-12

Bản phát hành đầu tiên.

### Added

- Trình duyệt trong app với thanh địa chỉ, lịch sử và dấu trang.
- Trích nội dung chương bằng Readability, có chọn vùng nội dung thủ công.
- Đọc bằng TTS hệ thống hoặc Microsoft Edge TTS, nghe nền khi tắt màn hình.
- Chế độ đọc có highlight câu, mini player, chuyển chương trước/sau.
- Thư viện, lưu tiến độ đọc, hẹn giờ tắt.
