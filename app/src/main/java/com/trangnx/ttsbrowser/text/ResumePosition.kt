package com.trangnx.ttsbrowser.text

/**
 * The sentence to resume from, given where reading stopped ([sentenceIndex], [charOffset] = the
 * sentence's start in the chapter text). The saved index is trusted if that sentence still starts at
 * [charOffset]; otherwise (the chapter or its segmentation changed) the sentence covering [charOffset].
 */
fun List<Sentence>.resumeIndex(sentenceIndex: Int, charOffset: Int): Int {
    if (isEmpty()) return 0
    if (getOrNull(sentenceIndex)?.start == charOffset) return sentenceIndex
    val covering = indexOfLast { it.start <= charOffset }
    return if (covering >= 0) covering else 0
}
