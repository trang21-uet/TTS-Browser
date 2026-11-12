# TTS Browser

Trình duyệt Android đọc truyện chữ bằng TTS. Tổng quan, kiến trúc và build: [README.md](README.md), [docs/](docs/).

## Versioning

`versionName` (`MAJOR.MINOR.PATCH`) và `versionCode` trong [app/build.gradle.kts](app/build.gradle.kts) là nguồn sự thật duy nhất. **Tăng version trong mọi commit có đổi code, ngay trong commit đó** — không tạo commit `chore: bump version` riêng. Push lên `main` làm đổi version sẽ tự build APK ký và tạo GitHub release (xem [docs/release-guide.md](docs/release-guide.md)).

SemVer ở đây trả lời câu hỏi "người dùng phải làm gì để app tiếp tục chạy đúng?", không phải "việc này tốn bao nhiêu công". Một tính năng lớn vẫn là MINOR; một thay đổi nhỏ làm hỏng dữ liệu đã lưu có thể là MAJOR.

### PATCH — `x.y.Z`

Sửa lỗi, hiệu năng, việc nội bộ. Người dùng không có thêm khả năng mới và không phải làm gì.

- Sửa crash, lỗi highlight câu, lỗi trích nội dung một trang cụ thể.
- Chỉnh căn lề/màu/câu chữ; `refactor:`/`style:`/`perf:`/`test:`/`ci:`/`chore:`; nâng dependency không đổi hành vi.
- Chuyển một chức năng sang chỗ khác mà người dùng không mất khả năng nào.

### MINOR — `x.Y.z`

Khả năng mới, tương thích ngược. Người dùng làm được điều trước đây chưa làm được; ai không cập nhật chỉ là bỏ lỡ.

- Màn hình, luồng hoặc tùy chọn mới (thêm giọng Edge, chế độ đọc mới, xuất/nhập dữ liệu).
- Thêm bảng/cột Room có migration giữ nguyên dữ liệu cũ.

### MAJOR — `X.y.z`

Thay đổi phá vỡ: người dùng phải làm gì đó hoặc dữ liệu đã lưu đổi nghĩa.

- Dữ liệu đã lưu bị xóa hoặc không đọc được (Room không migration được, đổi định dạng DataStore làm mất cài đặt/tiến độ).
- Nâng `minSdk` làm máy cũ không cập nhật được; đổi `applicationId` hoặc key ký (không cài đè được lên bản cũ).
- Một khả năng bị gỡ hẳn mà không có gì thay thế.

### Checklist

Chạy trước mỗi lần release; dừng ở câu đầu tiên trả lời "có".

1. Bản đã cài sẽ mất dữ liệu, không cài đè được hoặc phải thiết lập lại? → **MAJOR**.
2. Người dùng làm được điều mà trước đó không làm được? → **MINOR**.
3. Hành vi đã phát hành có đổi không (sửa lỗi, hiệu năng, refactor, nâng dependency)? → **PATCH**.
4. Chỉ đổi docs/plans, không có code phát hành? → **không tăng version** (`docs:`).

Rồi, trong cùng commit:

- Tăng `versionCode` đúng 1 cho mọi mức MAJOR/MINOR/PATCH; Android không cho cài đè bản có `versionCode` thấp hơn hoặc bằng.
- Thêm mục `## [x.y.z] - YYYY-MM-DD` vào [docs/project-changelog.md](docs/project-changelog.md) với `### Added`/`### Changed`/`### Fixed`. Nội dung mục này trở thành ghi chú của GitHub release.
