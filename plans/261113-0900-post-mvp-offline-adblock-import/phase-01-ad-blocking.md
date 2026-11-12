# Phase 1 — Chặn quảng cáo

**Priority:** Medium · **Status:** Done (build + test + lint pass; chờ thử trên máy)

## Overview
Chặn request tới host quảng cáo/tracker trong `BrowserWebView` (và `HeadlessPageLoader`) để trang tải nhanh, ít popup, trích nội dung sạch hơn.

## Requirements
- Danh sách host nhúng trong app (asset text, ~vài trăm host phổ biến), khớp theo domain + subdomain.
- Chặn qua `WebViewClient.shouldInterceptRequest` trả response rỗng; không chặn request main-frame.
- Công tắc bật/tắt trong Settings (DataStore, mặc định bật); đếm số request đã chặn ở trang hiện tại (tùy chọn, hiển thị nhỏ).
- Không làm hỏng trang truyện: có cách tắt theo phiên khi trang lỗi.

## Related files
- Create: `extraction/AdHostBlocklist.kt`, `app/src/main/assets/ad-hosts.txt`, test `AdHostBlocklistTest`
- Modify: `ui/browser/BrowserWebView.kt`, `extraction/HeadlessPageLoader.kt`, `data/settings/SettingsRepository.kt`, `ui/settings/SettingsScreen.kt`

## Steps
1. `AdHostBlocklist`: load asset → `Set<String>`; `isBlocked(host)` thử host và các domain cha.
2. Gắn vào `shouldInterceptRequest` của cả hai WebViewClient (bỏ qua `isForMainFrame`).
3. Thêm setting `adBlockEnabled` + công tắc.
4. Unit test: khớp subdomain, không khớp host gần giống (`notads.com` ≠ `ads.com`), main-frame không bị chặn.

## Success criteria
Build + test + lint pass; trên máy: trang truyện có quảng cáo tải nhanh hơn, Readability vẫn trích đúng; tắt công tắc thì request không bị chặn.

## Risks
Chặn nhầm CDN chứa nội dung/ảnh bìa → danh sách chỉ gồm host thuần quảng cáo; có công tắc tắt.
