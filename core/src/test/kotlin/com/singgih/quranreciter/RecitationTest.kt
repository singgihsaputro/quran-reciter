package com.singgih.quranreciter

import com.singgih.quranreciter.recite.ArabicNormalizer
import com.singgih.quranreciter.recite.RecitationMatcher
import com.singgih.quranreciter.recite.WordStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArabicNormalizerTest {

    @Test
    fun `strips harakat so undiacritised speech can match Uthmani text`() {
        // Al-Ikhlas 1, as bundled (Uthmani) vs as a recogniser would return it
        val uthmani = "قُلْ هُوَ ٱللَّهُ أَحَدٌ"
        val spoken = "قل هو الله احد"
        assertEquals(ArabicNormalizer.normalize(spoken), ArabicNormalizer.normalize(uthmani))
    }

    @Test
    fun `folds the alif variants onto one letter`() {
        val forms = listOf("أحد", "إحد", "آحد", "ٱحد", "احد")
        val normalized = forms.map { ArabicNormalizer.normalize(it) }.toSet()
        assertEquals(1, normalized.size, "alif variants should fold together: $normalized")
    }

    @Test
    fun `folds ta marbuta and alif maqsura`() {
        assertEquals(ArabicNormalizer.normalize("صلاه"), ArabicNormalizer.normalize("صلاة"))
        assertEquals(ArabicNormalizer.normalize("هدي"), ArabicNormalizer.normalize("هدى"))
    }

    @Test
    fun `drops tatweel and latin punctuation`() {
        assertEquals("قل", ArabicNormalizer.normalize("قـــل."))
    }

    @Test
    fun `collapses whitespace and yields words`() {
        assertEquals(listOf("قل", "هو", "الله"), ArabicNormalizer.words("  قل   هو \n الله  "))
    }

    @Test
    fun `empty input is handled`() {
        assertEquals(emptyList(), ArabicNormalizer.words("   "))
    }
}

class RecitationMatcherTest {

    private val ikhlas1 = "قُلْ هُوَ ٱللَّهُ أَحَدٌ"

    @Test
    fun `a correct recitation scores every word`() {
        val r = RecitationMatcher.match(ikhlas1, "قل هو الله احد")
        assertEquals(4, r.total)
        assertEquals(4, r.correctCount)
        assertEquals(1f, r.accuracy)
        assertTrue(r.extraHeard.isEmpty())
    }

    @Test
    fun `a skipped word is reported missed, not as wrecking the rest`() {
        // "هو" dropped — the words after it are still correct
        val r = RecitationMatcher.match(ikhlas1, "قل الله احد")
        assertEquals(3, r.correctCount)
        assertEquals(WordStatus.MISSED, r.words[1].status)
        assertEquals(WordStatus.CORRECT, r.words[2].status)
        assertEquals(WordStatus.CORRECT, r.words[3].status)
    }

    @Test
    fun `a wrong word is misread, and what was heard is kept`() {
        val r = RecitationMatcher.match(ikhlas1, "قل هو الله واحد")
        assertEquals(3, r.correctCount)
        assertEquals(WordStatus.MISREAD, r.words[3].status)
        assertEquals("واحد", r.words[3].heard)
    }

    @Test
    fun `an inserted word does not shift everything after it`() {
        val r = RecitationMatcher.match(ikhlas1, "قل يا هو الله احد")
        assertEquals(4, r.correctCount, "all four verse words were still said")
        assertEquals(listOf("يا"), r.extraHeard)
    }

    @Test
    fun `silence scores zero and misses everything`() {
        val r = RecitationMatcher.match(ikhlas1, "")
        assertEquals(0, r.correctCount)
        assertEquals(0f, r.accuracy)
        assertTrue(r.words.all { it.status == WordStatus.MISSED })
    }

    @Test
    fun `wrong order is not silently accepted`() {
        val r = RecitationMatcher.match(ikhlas1, "احد الله هو قل")
        assertTrue(r.correctCount < 4, "reversed recitation must not score full marks")
    }

    @Test
    fun `an empty verse cannot divide by zero`() {
        val r = RecitationMatcher.match("", "قل")
        assertEquals(0, r.total)
        assertEquals(0f, r.accuracy)
    }

    @Test
    fun `works on a longer verse with a middle omission`() {
        val fatihah2 = "ٱلْحَمْدُ لِلَّهِ رَبِّ ٱلْعَـٰلَمِينَ"
        val r = RecitationMatcher.match(fatihah2, "الحمد لله العالمين")
        assertEquals(4, r.total, "al-hamdu lillahi rabbi al-alamin is four words")
        assertEquals(WordStatus.MISSED, r.words[2].status, "rabbi was skipped")
        assertEquals(3, r.correctCount)
    }
}
