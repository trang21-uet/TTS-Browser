package com.trangnx.ttsbrowser.playback

import com.trangnx.ttsbrowser.extraction.Chapter
import com.trangnx.ttsbrowser.extraction.ExtractionResult
import com.trangnx.ttsbrowser.extraction.HeadlessPageLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Previous/next chapter without the browser tab (notification, lock screen, headset): loads the
 * loaded chapter's `prevUrl` / `nextUrl` in a headless WebView and reads it from the start.
 * One at a time. Main thread only.
 */
class AdjacentChapterLoader(
    private val scope: CoroutineScope,
    private val loader: HeadlessPageLoader,
    private val playback: PlaybackConnection,
) {
    private var job: Job? = null

    /** [onFailure] gets why it failed; null: the chapter has no previous chapter. */
    fun openPrevious(onFailure: (ExtractionResult.Reason?) -> Unit) = open(Chapter::prevUrl, onFailure)

    /** [onFailure] gets why it failed; null: the chapter has no next chapter. */
    fun openNext(onFailure: (ExtractionResult.Reason?) -> Unit) = open(Chapter::nextUrl, onFailure)

    private fun open(target: (Chapter) -> String?, onFailure: (ExtractionResult.Reason?) -> Unit) {
        if (job?.isActive == true) return
        val from = playback.loaded.value?.chapter
        val url = from?.let(target)
        if (url == null) {
            onFailure(null)
            return
        }
        job = scope.launch {
            val result = try {
                loader.load(url)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ExtractionResult.Failure(ExtractionResult.Reason.LoadFailed)
            }
            when (result) {
                is ExtractionResult.Failure -> onFailure(result.reason)
                is ExtractionResult.Success -> {
                    // Something else was loaded meanwhile (the user went on by themselves): leave it.
                    if (playback.loaded.value?.chapter?.url != from.url) return@launch
                    playback.load(LoadedChapter(result.chapter, result.sentences))
                    playback.playFrom(0)
                }
            }
        }
    }
}
