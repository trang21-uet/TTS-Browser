# Phase 02 — Browser (WebView, address bar, history, bookmark)

## Context
[PDR F1](../../docs/project-overview-pdr.md) · [Architecture §2, §5, §7](../../docs/system-architecture.md)

## Overview
Priority: P0 · Status: Done (assembleDebug + unit test pass 2026-10-25), chờ thử trên máy
WebView một tab trong tab "Trình duyệt": thanh địa chỉ/tìm kiếm, back/forward/reload/stop, thanh tiến trình, lịch sử, bookmark (Room).

## Key insights
- Bottom-nav dùng `saveState/restoreState` nhưng composable vẫn rời composition khi đổi tab → WebView bị hủy. Giải pháp: giữ `WebView` trong `AppNavHost` (sống suốt Activity) và khai báo `configChanges` cho `MainActivity` để xoay màn hình không tạo lại Activity.
- Không giữ `WebView` trong ViewModel (leak Activity context). ViewModel chỉ giữ state + DB; lệnh `loadUrl/goBack` do UI gọi.
- Lịch sử ghi ở `doUpdateVisitedHistory` (bỏ qua reload, bỏ trùng URL liên tiếp); tiêu đề cập nhật khi `onReceivedTitle`.
- Renderer WebView chết (`onRenderProcessGone`) → trả `true` và tạo WebView mới, tránh crash app.
- Room bật `exportSchema` (Room Gradle plugin) để các phase sau thêm bảng bằng AutoMigration.

## Requirements
- Nhập URL hoặc từ khóa: có scheme → giữ; giống domain (`abc.com`, `localhost:8080`) → thêm `https://`; còn lại → tìm Google.
- Back hệ thống: nếu WebView `canGoBack` → `goBack()`.
- Scheme ngoài http/https → mở app ngoài (`ACTION_VIEW`, `CATEGORY_BROWSABLE`), lỗi thì bỏ qua.
- Bảo mật: không `addJavascriptInterface`, tắt file/content access; dark mode bằng algorithmic darkening (androidx.webkit).
- Lịch sử: xem, mở, xóa từng mục, xóa hết. Bookmark: thêm/bỏ trang hiện tại, xem, mở, xóa.
- Trang trống (chưa mở URL) hiện danh sách bookmark để truy cập nhanh.

## Related files
Create:
- `data/db/AppDatabase.kt`, `data/db/HistoryEntity.kt`, `data/db/BookmarkEntity.kt`, `data/db/HistoryDao.kt`, `data/db/BookmarkDao.kt`
- `data/repo/BrowsingRepository.kt` (history + bookmark)
- `ui/browser/UrlInputResolver.kt`, `BrowserViewModel.kt`, `BrowserWebView.kt` (tạo/cấu hình WebView + client), `BrowserAddressBar.kt`, `PageLinkList.kt` (sheet + list dùng chung)
- `test/.../UrlInputResolverTest.kt`
Modify: `libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`, `AndroidManifest.xml`, `AppContainer.kt`, `AppNavHost.kt`, `BrowserScreen.kt`, `strings.xml`

## Todo
- [x] Dependencies: Room + KSP + Room plugin, androidx.webkit
- [x] Room: history, bookmark + DAO + repository
- [x] UrlInputResolver + unit test
- [x] WebView factory (settings an toàn, client, chrome client), giữ ở AppNavHost
- [x] BrowserViewModel (state, ghi lịch sử, bookmark)
- [x] UI: address bar, progress, back/forward, menu, sheet lịch sử/bookmark, trang trống
- [x] `assembleDebug` + `testDebugUnitTest` pass
- [ ] Thử trên Xiaomi 13: akaytruyen.com, đổi tab giữ trang, xoay màn hình (cần người dùng)

## Success criteria
Mở akaytruyen.com, chuyển chương bằng link, back/forward đúng; đổi tab rồi quay lại vẫn giữ trang; lịch sử và bookmark lưu qua lần mở app sau.

## Risks
- Process bị kill → mất back stack WebView: chấp nhận ở MVP (lịch sử vẫn còn trong DB).
- Trang chặn WebView (Cloudflare challenge): WebView có JS + DOM storage nên thường qua được.

## Security
Không bridge JS; chỉ http/https load trong WebView; intent ngoài luôn kèm `CATEGORY_BROWSABLE`.

## Next
Phase 3 dùng WebView này để inject Readability (FAB "Đọc").
