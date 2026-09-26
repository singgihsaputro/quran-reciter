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
        'أ' to 'ا', 'إ' to 'ا', 'آ' to 'ا', 'ٱ' to 'ا',
        'ى' to 'ي', 'ئ' to 'ي',
        'ؤ' to 'و',
        'ة' to 'ه',
    )
    private const val DAGGER_ALIF = '\u0670'

    // Uthmani writes some long vowels as a letter carrying a superscript alif
    // where modern spelling — what a recogniser returns — has a plain alif.
    // A waw right against it is silent (ٱلْحَيَوٰةَ, al-hayah); a waw with its
    // own vowel first is a real waw (ٱلسَّمَـٰوَٰتِ) and is left alone.
    private val silentWaw = Regex("\u0648\u0670")
    // Mid-word, ىٰ is an alif (أَتَىٰكَ → أتاك); at the end it stays ى (إِلَىٰ → إلى).
    private val yaAlifInWord = Regex("\u0649\u0670(?=[\u064B-\u0655]*[ء-ي])")
    // A bare hamza sits where modern spelling seats one on an alif (أَرَءَيْتَ →
    // أرأيت), and hamza + alif is madd (ءَامَنُوا → آمنوا): so hamza is an alif,
    // and a run of alifs is one.
    private val alifRun = Regex("ا{2,}")
    // Uthmani writes al-layl with one lam; everyone else writes two.
    private val layl = Regex("(?<!\\S)(و?)الليل")

    /**
     * [daggerAlif] is what a superscript alif becomes. Modern spelling writes
     * some out (العالمين) and drops others (الرحمن, هذا), so the matcher tries
     * a verse both ways; text from a recogniser has none to begin with.
     */
    fun normalize(input: String, daggerAlif: String = "ا"): String {
        val unstretched = input.replace(tatweel.toString(), "")
            .replace(silentWaw, "ا")
            .replace(yaAlifInWord, "ا")
        val folded = buildString(unstretched.length) {
            for (c in unstretched) {
                if (c == DAGGER_ALIF) append(daggerAlif) else append(letterFolds[c] ?: c)
            }
        }
        return folded
            .replace(marks, "")
            .replace(nonArabic, " ")
            .replace(spaces, " ")
            .trim()
            .replace('ء', 'ا')
            .replace(alifRun, "ا")
            .replace(layl, "\$1اليل")
    }

    /** Normalised words, in order. The unit the matcher works in. */
    fun words(input: String, daggerAlif: String = "ا"): List<String> =
        normalize(input, daggerAlif).split(' ').filter { it.isNotBlank() }

    /**
     * The verse as drawn, one entry per word, aligned index-for-index with [words].
     *
     * Uthmani text sets some pause marks (ۖ ۚ ۩) apart as tokens of their own.
     * They are not words, and [words] drops them, so they ride on the word
     * before — otherwise every word after one would show its neighbour's result.
     * A mark that opens a verse (۞) has no word before it and leads the next.
     */
    fun displayWords(input: String): List<String> {
        val out = mutableListOf<String>()
        var lead = ""
        for (token in input.split(spaces).filter { it.isNotBlank() }) when {
            words(token).isNotEmpty() -> { out += lead + token; lead = "" }
            out.isEmpty() -> lead += "$token "
            else -> out[out.lastIndex] += " $token"
        }
        return out
    }
}
