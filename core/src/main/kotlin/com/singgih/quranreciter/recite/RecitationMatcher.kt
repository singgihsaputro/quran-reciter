package com.singgih.quranreciter.recite

/** What happened to one expected word. */
enum class WordStatus { CORRECT, MISREAD, MISSED }

data class WordResult(
    val expected: String,
    val heard: String?,
    val status: WordStatus,
)

data class RecitationResult(
    val words: List<WordResult>,
    /** Words extra to the verse — recogniser noise, or genuinely added words. */
    val extraHeard: List<String>,
) {
    val correctCount: Int get() = words.count { it.status == WordStatus.CORRECT }
    val total: Int get() = words.size

    /** 0f..1f. A ratio of words, nothing more — see the class docs on tajweed. */
    val accuracy: Float get() = if (total == 0) 0f else correctCount.toFloat() / total
}

/**
 * Aligns what was heard against the verse, word by word.
 *
 * Uses a longest-common-subsequence alignment rather than position-by-position
 * comparison: a single dropped or inserted word would otherwise shift everything
 * after it and report the whole rest of the verse as wrong.
 *
 * A word not in the LCS is MISSED when nothing was heard near it, and MISREAD
 * when something was heard in its place. That distinction matters to a learner:
 * skipping a word and mispronouncing one are different mistakes.
 *
 * **This compares words. It does not evaluate tajweed** — not makharij, not madd
 * length, not ghunnah, not qalqalah. Normalisation deletes the very marks those
 * rules live in. A perfect score here means the right words in the right order,
 * and says nothing about whether they were recited correctly.
 */
object RecitationMatcher {

    fun match(expectedVerse: String, heardText: String): RecitationResult {
        val expected = ArabicNormalizer.words(expectedVerse)
        val heard = ArabicNormalizer.words(heardText)

        if (expected.isEmpty()) return RecitationResult(emptyList(), heard)

        val pairs = longestCommonSubsequence(expected, heard)
        val matchedExpected = pairs.keys
        val matchedHeard = pairs.values.toSet()

        // Heard words that matched nothing, kept in order, to attribute to gaps.
        val leftovers = ArrayDeque(
            heard.indices.filter { it !in matchedHeard }.map { heard[it] }
        )

        val words = expected.mapIndexed { i, word ->
            when {
                i in matchedExpected -> WordResult(word, word, WordStatus.CORRECT)
                leftovers.isNotEmpty() -> WordResult(word, leftovers.removeFirst(), WordStatus.MISREAD)
                else -> WordResult(word, null, WordStatus.MISSED)
            }
        }
        return RecitationResult(words, leftovers.toList())
    }

    /** Map of expectedIndex -> heardIndex for one longest common subsequence. */
    private fun longestCommonSubsequence(a: List<String>, b: List<String>): Map<Int, Int> {
        val lengths = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) {
            for (j in b.indices.reversed()) {
                lengths[i][j] = if (a[i] == b[j]) lengths[i + 1][j + 1] + 1
                else maxOf(lengths[i + 1][j], lengths[i][j + 1])
            }
        }
        val pairs = LinkedHashMap<Int, Int>()
        var i = 0
        var j = 0
        while (i < a.size && j < b.size) {
            when {
                a[i] == b[j] -> { pairs[i] = j; i++; j++ }
                lengths[i + 1][j] >= lengths[i][j + 1] -> i++
                else -> j++
            }
        }
        return pairs
    }
}
