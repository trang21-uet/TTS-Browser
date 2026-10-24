package com.trangnx.ttsbrowser.extraction

import org.json.JSONObject

/** The element currently outlined by the picker, e.g. `div.chapter-c` with 3 200 chars of text. */
data class PickedElement(val label: String, val textLength: Int)

sealed interface PickerStatus {
    /** The picker is no longer running on the page (reload, new document, or stopped). */
    data object Gone : PickerStatus

    data class Active(val picked: PickedElement?) : PickerStatus
}

/** Kotlin side of `element-picker.js`: manual selection of the content region on the page. */
class ElementPicker(private val scripts: ExtractionScripts) {

    suspend fun start(runner: PageScriptRunner) {
        runner.evaluate(scripts.load(PICKER_JS))
    }

    suspend fun status(runner: PageScriptRunner): PickerStatus = parseStatus(runner.evaluate(call("status")))

    /** Moves the selection to the parent element. */
    suspend fun expand(runner: PageScriptRunner): PickerStatus = parseStatus(runner.evaluate(call("parent")))

    /** A CSS selector uniquely matching the picked element, or `null`. */
    suspend fun selector(runner: PageScriptRunner): String? = JsResult.string(runner.evaluate(call("selector")))

    suspend fun stop(runner: PageScriptRunner) {
        runner.evaluate(call("stop"))
    }

    private fun call(method: String) = "window.__ttsPicker ? window.__ttsPicker.$method() : null"

    private fun parseStatus(raw: String?): PickerStatus {
        val json = JsResult.string(raw) ?: return PickerStatus.Gone
        return runCatching {
            val obj = JSONObject(json)
            if (!obj.optBoolean("active")) return PickerStatus.Gone
            val picked = obj.optJSONObject("picked")?.let { PickedElement(it.getString("label"), it.getInt("textLength")) }
            PickerStatus.Active(picked)
        }.getOrDefault(PickerStatus.Gone)
    }

    private companion object {
        const val PICKER_JS = "element-picker.js"
    }
}
