# TTS Browser — Product Spec (PDR)

> Phiên bản: 0.2 · Ngày: 2026-10-24 · Trạng thái: đã duyệt
>
> Package `com.trangnx.ttsbrowser` · Thiết bị mục tiêu Xiaomi 13 (HyperOS) · Phân phối APK cá nhân · Trang thử nghiệm chính: akaytruyen.com

## 1. Mục tiêu

App Android dạng trình duyệt cho phép lướt bất kỳ trang truyện chữ nào, trích nội dung chương và **đọc to bằng TTS** — kể cả khi tắt màn hình — với giao diện đọc tô sáng câu đang phát.

Người dùng chính: cá nhân (chủ dự án), đọc truyện tiếng Việt trên web (truyện convert, truyện dịch…), muốn "nghe truyện" khi đi đường, làm việc, trước khi ngủ.

## 2. Phạm vi MVP

| # | Tính năng | Mô tả ngắn |
|---|-----------|------------|
| F1 | Trình duyệt | WebView một tab: thanh địa chỉ/tìm kiếm, back/forward/reload, lịch sử, bookmark trang |
| F2 | Reader mode | Nút "Đọc" trích nội dung chính bằng Readability.js; phát hiện link chương trước/sau |
| F3 | Giao diện đọc | Hiển thị văn bản sạch, tô sáng câu đang đọc, tự cuộn, chạm câu để đọc từ đó; chỉnh cỡ chữ, nền sáng/tối/sepia |
| F4 | TTS hệ thống | Dùng `android.speech.tts.TextToSpeech` (offline), chọn engine/giọng, tốc độ, cao độ |
| F5 | Edge TTS | Giọng neural Microsoft (vi-VN-HoaiMyNeural, vi-VN-NamMinhNeural…), cần mạng; fallback về TTS hệ thống khi lỗi |
| F6 | Đọc nền | Foreground service + MediaSession: điều khiển trên thông báo, màn hình khóa, tai nghe Bluetooth (play/pause, câu trước/sau, chương sau thủ công) |
| F7 | Thư viện | Lưu truyện đang đọc; nhớ chương + câu đang dừng; tiếp tục bằng 1 chạm |
| F8 | Hẹn giờ tắt | 15/30/45/60 phút hoặc "hết chương"; giảm dần âm lượng trước khi dừng (nếu engine hỗ trợ) |

### Ngoài phạm vi MVP (Phase 2+)

- Tự động sang chương sau khi đọc hết (đã chuẩn bị sẵn dữ liệu `nextUrl`)
- Adapter CSS selector cho từng trang
- Tải trước / đọc offline nhiều chương
- Chặn quảng cáo, nhiều tab, đồng bộ cloud, nhập TXT/EPUB, iOS

## 3. User flows chính

1. **Đọc lần đầu:** Mở app → nhập URL/tìm kiếm → vào trang chương → bấm FAB "Đọc" → Reader hiện văn bản → bấm Play → nghe; tắt màn hình vẫn đọc.
2. **Thêm vào thư viện:** Trong Reader bấm "Lưu truyện" → app đề xuất tên truyện (từ breadcrumb / `og:title` / tiêu đề trang, cho sửa) → lưu.
3. **Tiếp tục:** Mở app → tab Thư viện → chạm truyện → mở đúng chương, cuộn tới câu đã dừng → Play.
4. **Sang chương:** Trong Reader / thông báo bấm "Chương sau" → tải `nextUrl` (WebView ẩn) → trích → đọc từ đầu.
5. **Ngủ:** Bấm hẹn giờ 30 phút → hết giờ tự dừng, lưu vị trí.

## 4. Yêu cầu chức năng chi tiết

### F2 — Trích nội dung
- Inject `Readability.js` (Mozilla, Apache-2.0) vào trang đã load qua `evaluateJavascript`, trả về `{title, textContent, content(html), byline}`.
- Chuẩn hóa văn bản: bỏ dòng rác (quảng cáo dạng text, "Chương trước | Mục lục | Chương sau"), gộp khoảng trắng, giữ ngắt đoạn.
- Phát hiện link chương: ưu tiên `<link rel="next/prev">`, `a[rel=next]`; sau đó heuristic theo text/aria (`Chương sau`, `Chương tiếp`, `Next`, `›`, `→`) và id/class (`next`, `chap-next`…).
- Nếu Readability thất bại hoặc nội dung < 200 ký tự → báo lỗi và cho phép **chọn vùng thủ công** (thuộc MVP): bật chế độ chọn, chạm vào khối nội dung trên WebView → viền sáng element đang chọn, nút "mở rộng" chọn element cha → xác nhận. Selector đã chọn được nhớ theo host và dùng tự động cho các chương sau của cùng trang.

### F3/F4/F5 — Đọc & tô sáng
- Tách câu bằng `android.icu.text.BreakIterator` (locale vi), câu dài > ~300 ký tự cắt tiếp theo dấu phẩy/khoảng trắng; mỗi câu = 1 utterance.
- Highlight theo câu (không theo từ) cho cả 2 engine → đồng nhất, đơn giản.
- Prefetch: Edge TTS tổng hợp trước 2–3 câu kế tiếp để không có khoảng lặng.
- Tốc độ 0.5×–3.0×, cao độ 0.5–2.0; lưu theo engine.
- Lỗi mạng khi dùng Edge → tự chuyển TTS hệ thống cho câu hiện tại + hiện snackbar.

### F6 — Phát nền
- Media3 `MediaSessionService`, `foregroundServiceType="mediaPlayback"`.
- Xử lý audio focus (dừng khi có cuộc gọi / app khác phát nhạc, tiếp tục khi lấy lại), `ACTION_AUDIO_BECOMING_NOISY` (rút tai nghe → pause).
- Xiaomi 13 / HyperOS hay kill service: màn hình hướng dẫn lần đầu mở app, dẫn tới (1) xin `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, (2) Cài đặt app → Tiết kiệm pin → "Không hạn chế", (3) bật Tự khởi chạy (Autostart), (4) khóa app trong đa nhiệm.

### F7 — Thư viện & tiến độ
- Lưu vị trí mỗi khi chuyển câu (debounce 2s) và khi pause/stop/kill.
- Vị trí = `chapterUrl + sentenceIndex + charOffset` (charOffset để khôi phục khi cách tách câu thay đổi).

## 5. Yêu cầu phi chức năng

- **Nền tảng:** Android 8.0+ (minSdk 26), target SDK mới nhất (35/36).
- **Hiệu năng:** trích nội dung < 1s sau khi trang load xong; độ trễ giữa 2 câu < 300ms (system TTS) / < 500ms (Edge, có prefetch).
- **Pin:** không giữ WakeLock CPU ngoài thời gian phát; WebView ẩn bị hủy sau khi trích xong.
- **Riêng tư:** không analytics, không gửi dữ liệu ngoài; chỉ văn bản câu đang đọc được gửi tới Microsoft khi bật Edge TTS.
- **Kích thước APK:** < 15 MB.

## 6. Rủi ro chính

| Rủi ro | Mức | Giảm thiểu |
|--------|-----|-----------|
| Edge TTS là API **không chính thức**, Microsoft có thể đổi token (`Sec-MS-GEC`) hoặc chặn | Cao | Engine tách rời qua interface; fallback system TTS; không phát hành lên Play Store với tính năng này |
| Trang dùng chống copy / nội dung render bằng canvas / mã hóa font | TB | Báo không hỗ trợ; Phase 2 adapter theo trang |
| ROM Trung Quốc kill foreground service | TB | Hướng dẫn tắt tối ưu pin; khôi phục vị trí khi mở lại |
| Readability trích sai (lẫn bình luận, menu) | TB | Bộ lọc dòng rác; chọn vùng thủ công; adapter Phase 2 |

## 7. Tiêu chí hoàn thành MVP

- [ ] Đọc liên tục 1 chương ≥ 5.000 chữ với màn hình tắt, không bị ngắt
- [ ] Điều khiển play/pause được từ màn hình khóa và tai nghe Bluetooth
- [ ] Đóng app → mở lại → tiếp tục đúng câu đã dừng
- [ ] Trích đúng nội dung chương trên akaytruyen.com (tự động hoặc qua selector chọn thủ công)
- [ ] Chọn vùng thủ công hoạt động trên một trang mà Readability trích sai
- [ ] Edge TTS hoạt động; rút mạng giữa chừng thì tự chuyển system TTS
- [ ] Đọc nền ổn định ≥ 60 phút trên Xiaomi 13 sau khi làm theo hướng dẫn pin
