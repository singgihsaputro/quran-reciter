package com.singgih.quranreciter

import com.singgih.quranreciter.recite.ArabicNormalizer
import com.singgih.quranreciter.recite.RecitationMatcher
import com.singgih.quranreciter.recite.WordStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import java.io.File
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

    @Test
    fun `a standalone pause mark stays on the word before it`() {
        val shown = ArabicNormalizer.displayWords("قل ۖ هو الله")
        assertEquals(listOf("قل ۖ", "هو", "الله"), shown)
        assertEquals(ArabicNormalizer.words("قل ۖ هو الله").size, shown.size)
        assertEquals(listOf("۞ قل", "هو"), ArabicNormalizer.displayWords("۞ قل هو"))
    }

    @Test
    fun `every bundled verse draws exactly as many words as it matches`() {
        // The UI colours displayWords[i] by result.words[i]; any drift paints
        // the wrong word. Checked against the real text, all of it.
        val json = File("../app/src/main/assets/quran.json").readText()
        val verses = Regex("\"arabic\": \"([^\"]*)\"").findAll(json).map { it.groupValues[1] }.toList()
        assertEquals(571, verses.size)
        verses.forEach {
            assertEquals(ArabicNormalizer.words(it).size, ArabicNormalizer.displayWords(it).size, it)
            assertEquals(ArabicNormalizer.words(it).size, ArabicNormalizer.words(it, daggerAlif = "").size, it)
        }
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
        assertEquals(3, r.stars)
        assertTrue(r.extraHeard.isEmpty())
    }

    @Test
    fun `a skipped word is reported missed, not as wrecking the rest`() {
        // "هو" dropped — the words after it are still correct
        val r = RecitationMatcher.match(ikhlas1, "قل الله احد")
        assertEquals(3, r.correctCount)
        assertEquals(2, r.stars, "3 of 4 is 75%")
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
        assertEquals(0, r.stars)
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
    fun `Uthmani long vowels match how a recogniser spells them`() {
        // Uthmani words as bundled, against modern spelling. Before these folds
        // about 4% of words recited perfectly still scored as misread.
        listOf(
            "ٱلرَّحْمَـٰنِ" to "الرحمن", // superscript alif dropped in modern spelling
            "ٱلْعَـٰلَمِينَ" to "العالمين", // ...and written out here
            "ٱلْحَيَوٰةَ" to "الحياة", // a silent waw is an alif
            "ٱلسَّمَـٰوَٰتِ" to "السماوات", // but a voweled waw is a real waw
            "أَتَىٰكَ" to "أتاك", // ya + superscript alif mid-word is an alif
            "إِلَىٰ" to "إلى", // and stays a ya at the end
            "ءَامَنُوا۟" to "آمنوا", // hamza + alif is madd
            "وَٱلَّيْلِ" to "والليل", // one lam in Uthmani, two in modern
        ).forEach { (uthmani, spoken) ->
            assertEquals(1, RecitationMatcher.match(uthmani, spoken).correctCount, "$uthmani vs $spoken")
        }
    }

    @Test
    fun `a real difference still fails after folding`() {
        assertEquals(0, RecitationMatcher.match("ٱلسَّمَـٰوَٰتِ", "السمات").correctCount)
    }

    @Test
    fun `a whole-surah result splits back into verses`() {
        // Al-Ikhlas 1 and 2 recited together, with the second verse's first word skipped
        val verses = listOf("قُلْ هُوَ ٱللَّهُ أَحَدٌ", "ٱللَّهُ ٱلصَّمَدُ")
        val r = RecitationMatcher.match(verses.joinToString(" "), "قل هو الله احد الصمد")
        val first = r.slice(0, 4)
        val second = r.slice(4, 6)
        assertEquals(3, first.stars)
        assertEquals(1, second.correctCount)
        assertEquals(WordStatus.MISSED, second.words[0].status)
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
