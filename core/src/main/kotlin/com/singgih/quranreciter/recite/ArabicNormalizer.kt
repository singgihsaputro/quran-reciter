package com.singgih.quranreciter.recite

/**
 * Reduces Arabic to a form two sources can be compared in.
 *
 * Uthmani script carries diacritics, Quranic annotation marks and several
 * orthographic variants of the same letter. A speech recogniser returns none of
 * that — it gives modern spelling, bare of harakat. Comparing them raw fails on
 * every word, so both sides are folded to a common skeleton first.
 *
 * This is deliberately lossy. It throws away exactly the information tajweed
 * cares about, which is why this app does not claim to judge tajweed.
 */
object ArabicNormalizer {

    // harakat, tanwin, shadda, sukun, superscript alif, and Quranic annotation
    private val marks = Regex("[ً-ٰٟۖ-ࣰۭ-ࣿ]")
    private val tatweel = 'ـ'
    private val nonArabic = Regex("[^ء-يٱ\\s]")
    private val spaces = Regex("\\s+")

    /** Letters that appear in more than one shape for the same sound. */
    private val letterFolds = mapOf(
        'أ' to 'ا', 'إ' to 'ا', 'آ' to 'ا', 'ٱ' to 'ا', 'ٰ' to 'ا',
        'ى' to 'ي', 'ئ' to 'ي',
        'ؤ' to 'و',
        'ة' to 'ه',
    )

    fun normalize(input: String): String {
        val folded = buildString(input.length) {
            for (c in input) {
                when {
                    c == tatweel -> Unit
                    else -> append(letterFolds[c] ?: c)
                }
            }
        }
        return folded
            .replace(marks, "")
            .replace(nonArabic, " ")
            .replace(spaces, " ")
            .trim()
    }

    /** Normalised words, in order. The unit the matcher works in. */
    fun words(input: String): List<String> =
        normalize(input).split(' ').filter { it.isNotBlank() }
}
