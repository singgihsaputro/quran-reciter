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

    /** The part covering expected words [from] until [to]: one verse of a whole-surah recitation. */
    fun slice(from: Int, to: Int) = RecitationResult(words.subList(from, to), emptyList())

    /** 0..3, the game's reward: 3 for every word, 2 from 70%, 1 from 40%. */
    val stars: Int get() = when {
        total == 0 -> 0
        correctCount == total -> 3
        accuracy >= 0.7f -> 2
        accuracy >= 0.4f -> 1
        else -> 0
    }
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
        // The same verse with superscript alifs dropped instead of written out:
        // a recogniser spells ٱلرَّحْمَـٰنِ الرحمن but ٱلْعَـٰلَمِينَ العالمين.
        val bare = ArabicNormalizer.words(expectedVerse, daggerAlif = "")
        val heard = ArabicNormalizer.words(heardText)

        if (expected.isEmpty()) return RecitationResult(emptyList(), heard)

        val pairs = longestCommonSubsequence(expected.size, heard.size) { i, j ->
            heard[j] == expected[i] || heard[j] == bare[i]
        }
        val matchedExpected = pairs.keys
        val matchedHeard = pairs.values.toSet()

        // Heard words that matched nothing, kept in order, to attribute to gaps.
        val leftovers = ArrayDeque(
            heard.indices.filter { it !in matchedHeard }.map { heard[it] }
        )

        val words = expected.mapIndexed { i, word ->
            when {
                i in matchedExpected -> WordResult(word, heard[pairs.getValue(i)], WordStatus.CORRECT)
                leftovers.isNotEmpty() -> WordResult(word, leftovers.removeFirst(), WordStatus.MISREAD)
                else -> WordResult(word, null, WordStatus.MISSED)
            }
        }
        return RecitationResult(words, leftovers.toList())
    }

    /** Map of expectedIndex -> heardIndex for one longest common subsequence. */
    private fun longestCommonSubsequence(a: Int, b: Int, same: (Int, Int) -> Boolean): Map<Int, Int> {
        val lengths = Array(a + 1) { IntArray(b + 1) }
        for (i in (0 until a).reversed()) {
            for (j in (0 until b).reversed()) {
                lengths[i][j] = if (same(i, j)) lengths[i + 1][j + 1] + 1
                else maxOf(lengths[i + 1][j], lengths[i][j + 1])
            }
        }
        val pairs = LinkedHashMap<Int, Int>()
        var i = 0
        var j = 0
        while (i < a && j < b) {
            when {
                same(i, j) -> { pairs[i] = j; i++; j++ }
                lengths[i + 1][j] >= lengths[i][j + 1] -> i++
                else -> j++
            }
        }
        return pairs
    }
}
