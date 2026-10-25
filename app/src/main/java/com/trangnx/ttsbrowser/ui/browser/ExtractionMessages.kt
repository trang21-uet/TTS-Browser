package com.trangnx.ttsbrowser.ui.browser

import androidx.annotation.StringRes
import com.trangnx.ttsbrowser.R
import com.trangnx.ttsbrowser.extraction.ExtractionResult

/** User-facing explanation of why a chapter could not be extracted. */
@get:StringRes
val ExtractionResult.Reason.message: Int
    get() = when (this) {
        ExtractionResult.Reason.NoContent -> R.string.extract_failed_no_content
        ExtractionResult.Reason.TooShort -> R.string.extract_failed_too_short
        ExtractionResult.Reason.ScriptError -> R.string.extract_failed_script
        ExtractionResult.Reason.LoadFailed -> R.string.extract_failed_load
    }
