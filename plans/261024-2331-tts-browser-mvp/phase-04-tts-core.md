# Phase 04 — TTS core (engine, TtsPlayer, PlaybackService)

## Context
[PDR F4, F6](../../docs/project-overview-pdr.md) · [Architecture §4, §6](../../docs/system-architecture.md) · Đầu vào: `Chapter` + `List<Sentence>` từ [Phase 3](phase-03-extraction.md)

## Overview
Priority: P0 · Status: Done (assembleDebug + unit test pass 2026-10-25), chờ thử trên máy
Nút "Nghe" trong sheet kết quả trích → nạp chương vào `TtsPlayer` → phát qua `MediaController` → `PlaybackService` (MediaSession) hiện thông báo, điều khiển ở màn hình khóa / tai nghe. Engine: TTS hệ thống (`SystemTtsEngine`). Reader UI, highlight, mini player làm ở Phase 5; lỗi phát tạm hiện bằng Toast.

## Key insights
- **Mỗi câu = 1 `MediaItem`** (playlist trong `SimpleBasePlayer`) → nút next/prev của thông báo, lock screen, tai nghe tự thành câu sau/trước; `seekTo(index)` = nhảy tới câu (Phase 5 chạm câu). Vị trí luôn 0, duration `TIME_UNSET`; `seekToPrevious` (vị trí 0 ≤ maxSeekToPrevious) → về câu trước.
- TTS hệ thống không có pause thật: pause = `stop()` + nhớ index; resume đọc lại từ đầu câu hiện tại.
- Queue **cuốn chiếu** 5 câu (`QUEUE_ADD`), mỗi `onDone/onError` nạp thêm (callback đánh thức process, foreground service giữ sống khi tắt màn hình) → resume/seek/đổi tốc độ không phải gọi hàng trăm IPC `speak()` trên main thread. Đổi tốc độ/cao độ/seek → speak lại từ câu hiện tại.
- `speak()` bị từ chối (service TTS chết) → `TtsEvent.Failed` → lỗi ngay. Watchdog: đang đọc mà 90s không có callback → lỗi (service chết không báo gì). Init engine timeout 15s.
- `handle*()` không gọi `invalidateState()` (SimpleBasePlayer tự đọc state sau handler, giữ đúng reason seek). Vị trí là hằng 0 (`PositionSupplier`), không ngoại suy → "câu trước" luôn về câu trước. Hạn chế đã biết: câu tự chuyển báo `MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED` thay vì AUTO — Phase 5 không dựa vào reason.
- `PlaybackConnection` giữ 1 `MediaController`; `onTaskRemoved` release nó để service dừng được khi không phát (controller còn bind thì `stopSelf` vô hiệu). Hết chương → giải phóng engine.
- utteranceId `"<generation>:<index>"`; generation tăng ở mỗi `speak/stop` và được **lọc trong collector (main thread)** → callback trễ của lần đọc cũ không đổi index.
- `TtsPlayer` là singleton trong `AppContainer` (UI nạp chương trực tiếp, cùng process); service chỉ bọc vào `MediaSession`, `onDestroy` gọi `player.stop()` (giải phóng engine) chứ không `release()`.
- Phát qua `MediaController` (không gọi thẳng player) → service được bind, session tồn tại, Media3 tự đưa service lên foreground khi đang phát. `Util.handlePlayButtonAction`: IDLE → `prepare()`, ENDED → `seekToDefaultPosition()` (player hiểu là đọc lại từ câu đầu).
- Audio focus: `setWillPauseWhenDucked(true)` → app khác "duck" thì tạm dừng (giọng nói nhỏ đi khó nghe). Mất focus tạm (cuộc gọi) → `PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS`, lấy lại thì đọc tiếp; mất hẳn → pause. `ACTION_AUDIO_BECOMING_NOISY` → pause.
- Lỗi 1 câu không dừng phát (engine đi tiếp câu sau); 3 lỗi liên tiếp → `playerError`. Engine thiếu giọng vi-VN (`LANG_MISSING_DATA/NOT_SUPPORTED`) → lỗi rõ ràng, không đọc bằng giọng ngoại.
- Android 13+: thông báo của media session được miễn `POST_NOTIFICATIONS` → không xin quyền.

- Code review 2026-10-25 (đã sửa): đọc kẹt im lặng khi `speak()` lỗi giữa chừng / service TTS chết; controller không release giữ service sống mãi; invalidate trong handler sai reason; init không timeout; "câu trước" lặp câu hiện tại do vị trí ngoại suy; queue cả chương trên main thread; `onDisconnected` xóa nhầm controller mới; engine giữ sau khi hết chương.

## Requirements
- Play/pause, câu sau/trước, dừng từ thông báo, lock screen, tai nghe Bluetooth.
- Tốc độ 0.5–3.0, cao độ 0.5–2.0 (`setPlaybackParameters`, clamp).
- Đọc tiếp khi tắt màn hình; app vào nền vẫn đọc (foreground service `mediaPlayback`).
- Cuộc gọi / app khác phát → dừng; xong cuộc gọi tạm → đọc tiếp. Rút tai nghe → pause.

## Related files
Create:
- `tts/TtsEngine.kt` (interface, `TtsEvent`, `TtsInitException`), `tts/system/SystemTtsEngine.kt`, `tts/system/UtteranceId.kt`
- `playback/`: `TtsPlayer.kt`, `SpeechDriver.kt` (vòng đời engine), `TtsPlayerCommands.kt`, `SentencePlaylist.kt`, `AudioFocusHandler.kt`, `BecomingNoisyReceiver.kt`, `PlaybackService.kt`, `PlaybackConnection.kt`
- Test: `UtteranceIdTest`, `SentencePlaylistTest`
Modify: `AndroidManifest.xml` (quyền FGS, service, `<queries>` TTS_SERVICE), `AppContainer.kt`, `ExtractionViewModel.kt`, `ExtractionResultSheet.kt`, `ExtractionOverlay.kt`, `strings.xml`, `libs.versions.toml`, `app/build.gradle.kts` (media3-session 1.11.1)

## Todo
- [x] TtsEngine + SystemTtsEngine (vi-VN, AudioAttributes speech, generation id)
- [x] TtsPlayer (SimpleBasePlayer) + SpeechDriver, audio focus, becoming noisy
- [x] PlaybackService + PlaybackConnection (MediaController), manifest
- [x] Nút "Nghe" trong sheet kết quả trích
- [x] `assembleDebug` + `testDebugUnitTest` pass
- [ ] Thử trên máy (Xiaomi 13): đọc 1 chương tắt màn hình, điều khiển lock screen + tai nghe BT, cuộc gọi đến, rút tai nghe (cần người dùng)

## Success criteria
Bấm "Nghe" → đọc từ câu đầu, thông báo có play/pause/câu trước/sau; tắt màn hình vẫn đọc hết chương; tai nghe BT điều khiển được; cuộc gọi đến → dừng, cúp máy → đọc tiếp.

## Risks
- HyperOS kill foreground service → hướng dẫn pin ở Phase 8; tiến độ lưu ở Phase 6.
- Playlist lớn (~1000 câu) qua MediaSession: Media3 chia nhỏ khi gửi timeline; theo dõi độ trễ khi thử trên máy.
- Engine TTS trả `onError` liên tục (thiếu dữ liệu giọng, mất mạng với giọng online) → dừng sau 3 lỗi, Toast báo lỗi.

## Security
Không có dữ liệu ra ngoài: TTS hệ thống chạy trên máy (trừ khi engine người dùng chọn tự dùng mạng). Service exported là bắt buộc với `MediaSessionService`; session chỉ nhận lệnh phát chuẩn, không nhận nội dung từ controller ngoài.

## Next
Phase 5: ReaderScreen dùng `MediaController` (index câu = `currentMediaItemIndex`) để highlight, chạm câu → `seekTo(index, 0)`, mini player; hiện `playerError` thay Toast. Phase 6: custom command "Chương sau", hẹn giờ, lưu tiến độ. Phase 7: `EdgeTtsEngine` cắm vào `engineFactory`.
