package com.trangnx.ttsbrowser.ui.reader

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.trangnx.ttsbrowser.R
import com.trangnx.ttsbrowser.data.settings.ReaderFont

private val BeVietnamPro = FontFamily(
    Font(R.font.be_vietnam_pro_regular, FontWeight.Normal),
    Font(R.font.be_vietnam_pro_bold, FontWeight.Bold),
)

/** One variable file serves every weight (minSdk 26 supports font variations). */
private val Lora = FontFamily(
    Font(R.font.lora_variable, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.lora_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

/** Typeface of [this]; null keeps the app's default text style. */
val ReaderFont.family: FontFamily?
    get() = when (this) {
        ReaderFont.Default -> null
        ReaderFont.BeVietnamPro -> BeVietnamPro
        ReaderFont.Lora -> Lora
        ReaderFont.Serif -> FontFamily.Serif
        ReaderFont.Mono -> FontFamily.Monospace
    }
