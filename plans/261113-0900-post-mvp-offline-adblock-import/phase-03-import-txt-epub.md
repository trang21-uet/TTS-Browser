# Phase 3 — Nhập TXT/EPUB

**Priority:** Medium · **Status:** Pending · **Phụ thuộc:** Phase 2

## Overview
Chọn file .txt/.epub từ máy (Storage Access Framework), tách chương, lưu vào Library để nghe bằng cùng Reader/TtsPlayer.

## Requirements
- Nút "Nhập file" trong Library → `ActivityResultContracts.OpenDocument`.
- TXT: tách chương theo mẫu "Chương N"/"Chapter N", fallback cắt theo độ dài; phát hiện encoding UTF‑8 (fallback UTF‑16/Windows‑1258).
- EPUB: đọc ZIP → OPF spine → XHTML từng chương → `TextCleaner`; lấy tên truyện + ảnh bìa từ metadata.
- Chương lưu bằng bảng cache của Phase 2 với URL dạng `import://{bookId}/{index}`; prev/next theo thứ tự.
- Không thêm dependency nặng: dùng `java.util.zip` + XmlPullParser/Jsoup nếu đã có.

## Related files
- Create: `import/TxtChapterSplitter.kt`, `import/EpubReader.kt`, `import/BookImporter.kt`, tests với file mẫu tổng hợp (không commit truyện thật)
- Modify: `ui/library/LibraryScreen.kt`, `LibraryViewModel.kt`, `LibraryRepository.kt`, `di/AppContainer.kt`

## Steps
1. `TxtChapterSplitter` + test (mẫu tiêu đề, file không có tiêu đề, encoding).
2. `EpubReader` + test với EPUB tổng hợp nhỏ.
3. `BookImporter` ghi Book + chương, chạy nền, báo tiến độ/lỗi.
4. UI nhập file + xử lý lỗi (file hỏng, quá lớn).
5. Mở chương nhập từ Library → Reader; next/prev hoạt động offline.

## Success criteria
Build + test + lint pass; nhập 1 TXT và 1 EPUB, nghe và tiếp tục đúng câu sau khi đóng/mở app.

## Risks
EPUB DRM/định dạng lạ → báo lỗi rõ ràng. File rất lớn → đọc stream, giới hạn kích thước.
