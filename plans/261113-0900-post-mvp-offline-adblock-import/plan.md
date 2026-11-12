# Post-MVP — Plan

Nguồn: mục "Phase 2 (sau MVP)" trong [roadmap](../../docs/development-roadmap.md) · [Architecture](../../docs/system-architecture.md)

Đã có sẵn, không làm lại: tự động sang chương sau (`6f997d6`), selector theo trang (`site_rule` + `ElementPicker`).

| Phase | File | Version dự kiến | Status |
|-------|------|-----------------|--------|
| 1. Chặn quảng cáo (host blocklist trong trình duyệt) | [phase-01-ad-blocking.md](phase-01-ad-blocking.md) | MINOR | Done 0.6.0 (chờ thử trên máy) |
| 2. Tải trước / đọc offline chương | [phase-02-offline-chapters.md](phase-02-offline-chapters.md) | MINOR (Room migration) | Pending |
| 3. Nhập TXT/EPUB vào thư viện | [phase-03-import-txt-epub.md](phase-03-import-txt-epub.md) | MINOR | Pending |

## Thứ tự và phụ thuộc
- Phase 1 độc lập, nhỏ nhất → làm trước.
- Phase 2 thêm bảng chương (Room AutoMigration) → Phase 3 tái dùng bảng này để lưu chương nhập từ file.
- Mỗi phase = 1 commit, tăng `versionName`/`versionCode` + mục changelog trong cùng commit (xem CLAUDE.md).
