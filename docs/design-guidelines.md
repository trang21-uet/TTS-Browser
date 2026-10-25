# TTS Browser — Hướng dẫn giao diện

## Bối cảnh
Ứng dụng Android dùng Jetpack Compose Material 3; ưu tiên đọc tiếng Việt và điều khiển bằng một tay. Giữ theme đang có, không thêm font tải từ mạng.

## Thành phần và nhịp bố cục
- Dùng `MaterialTheme.colorScheme` và typography, hỗ trợ dynamic color Android 12+ và sáng/tối; không mã hóa màu riêng trong Settings.
- Khoảng cách theo bội số 4/8 dp. Settings padding 20 dp, nhóm cách nhau 20 dp; hàng lựa chọn tối thiểu 64 dp, toàn hàng chạm được.
- Nhóm giọng đọc dùng radio có semantics `RadioButton`, heading rõ ràng. Chữ theo sp và nội dung cuộn được để hỗ trợ font hệ thống lớn.
- Tốc độ/cao độ: slider có nhãn giá trị, lưu khi nhả tay; khóa tương tác trong lúc ghi DataStore. Lỗi lưu dùng snackbar, trạng thái tải dùng progress indicator.

## Thông báo và dữ liệu
- Chỉ hiện chọn giọng Edge khi đã chọn Edge; thông tin gửi câu đọc/tiền tải tới Microsoft đặt ngay trong nhóm đó.
- Fallback dùng snackbar khi Activity STARTED, Toast khi app ở nền. Không lặp thông báo cho từng câu.
- Reader giữ highlight theo câu, nền sáng/tối/sepia và nút trở lại câu đang đọc; cấu hình tốc độ theo nguồn giọng đang chọn.

## Kiểm tra tiếp theo
Trên Xiaomi 13 cần kiểm tra TalkBack, cỡ chữ lớn, landscape, tương phản dynamic color, vị trí snackbar và thao tác khi đang phát. Chưa xác nhận visual QA chỉ từ build/unit test.
