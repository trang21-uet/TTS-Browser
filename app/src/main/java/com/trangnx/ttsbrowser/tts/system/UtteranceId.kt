package com.trangnx.ttsbrowser.tts.system

/** Utterance ids `"<generation>:<sentence index>"`; the generation tells callbacks of an old speak call apart. */
internal data class UtteranceId(val generation: Int, val index: Int) {
    fun encode(): String = "$generation:$index"

    companion object {
        fun decode(id: String?): UtteranceId? {
            val parts = id?.split(':') ?: return null
            if (parts.size != 2) return null
            val generation = parts[0].toIntOrNull() ?: return null
            val index = parts[1].toIntOrNull()?.takeIf { it >= 0 } ?: return null
            return UtteranceId(generation, index)
        }
    }
}
