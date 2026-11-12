# Phase 2 — Tải trước / đọc offline

**Priority:** High · **Status:** Pending

## Overview
Lưu văn bản chương đã trích vào Room để mở lại không cần mạng, và tải trước N chương kế tiếp của truyện đang đọc (dùng `HeadlessPageLoader` + `nextUrl` đã có).

## Requirements
- Bảng `chapter_cache` (url PK, bookId?, title, paragraphs JSON, prevUrl, nextUrl, fetchedAt) — Room AutoMigration (version 4 → 5), dữ liệu cũ giữ nguyên.
- Mọi chương đã đọc tự lưu; `AdjacentChapterLoader` tra cache trước khi tải mạng.
- Tải trước N chương kế (setting 0/3/10, mặc định 3) khi đang phát, chỉ qua Wi‑Fi tùy chọn; dừng khi hết `nextUrl`.
- Dọn cache: giới hạn dung lượng/số chương, xóa theo truyện trong Library.

## Related files
- Create: `data/db/ChapterCacheEntity.kt`, `ChapterCacheDao.kt`, `data/repo/ChapterCacheRepository.kt`, `playback/ChapterPrefetcher.kt`, tests
- Modify: `data/db/AppDatabase.kt` (+ schema export), `playback/AdjacentChapterLoader.kt`, `di/AppContainer.kt`, Settings UI

## Steps
1. Entity/DAO/repo + AutoMigration 4→5 + migration test.
2. `AdjacentChapterLoader`: cache-first, ghi cache sau khi tải.
3. `ChapterPrefetcher`: vòng lặp tuần tự theo `nextUrl`, hủy khi đổi truyện/dừng.
4. Settings: số chương tải trước, chỉ Wi‑Fi, nút xóa cache.
5. Test: cache hit không gọi mạng; prefetch dừng đúng ở chương cuối.

## Success criteria
Build + test + lint pass; bật chế độ máy bay vẫn đọc được các chương đã tải trước; nâng cấp từ bản cũ không mất thư viện/tiến độ.

## Risks
Phình dung lượng → giới hạn LRU. Tải dồn dập bị trang chặn → delay giữa các chương.
