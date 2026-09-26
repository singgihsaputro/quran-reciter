package com.singgih.quranreciter.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.singgih.quranreciter.data.Story
import com.singgih.quranreciter.data.Surah
import com.singgih.quranreciter.data.Verse
import com.singgih.quranreciter.recite.Failure
import java.util.Locale

/**
 * Every word the app shows, in English and Bahasa Indonesia.
 *
 * Chosen by the switch on the home screen rather than the phone's locale: a
 * family's phone is often in one language while the child learns in another.
 */
val LocalStrings = staticCompositionLocalOf<Strings> { English }

sealed interface Strings {
    val indonesian: Boolean
    /** For the story voice. */
    val locale: Locale
    val code: String

    fun translation(verse: Verse) = if (indonesian) verse.translationIndonesian else verse.translation
    fun translation(story: Story) = if (indonesian) story.translationIndonesian else story.translation
    fun meaning(surah: Surah) = if (indonesian) surah.meaningIndonesian else surah.meaning
    fun telling(story: Story) = if (indonesian) story.indonesian else story.english

    val switchLanguage: String
    val tagline: String
    val tonightsBedtimeStory: String
    val back: String

    val oneVerse: String
    val wholeSurah: String
    fun verse(n: Int): String
    fun verseStars(n: Int, stars: Int): String
    val listen: String
    val playing: String
    val tapAnyWord: String
    fun starsOutOf3(n: Int): String
    fun wordsRight(right: Int, total: Int): String
    fun cheer(stars: Int): String
    val iHeard: String
    val hearItRight: String
    val next: String
    val notTajweed: String
    val stop: String
    val recite: String
    val noRecogniser: String
    fun listening(right: Int, total: Int): String
    val listeningNow: String
    val audioFailed: String
    val tapToTryAgain: String
    fun tapToRecite(verse: Int): String
    val tapToReciteSurah: String
    fun failure(reason: Failure): String

    val tonightsStory: String
    val bedtimeStories: String
    fun storyCount(n: Int, of: Int): String
    fun retoldFrom(refs: String): String
    val lessonPrefix: String
    fun fromQuran(key: String): String
    val goodnight: String
    fun forGrownUps(refs: String): String
    val previousStory: String
    val nextStory: String
    val pause: String
    val tellStory: String
    val noStoryVoice: String
    val telling: String
    val verseFailed: String
    val tapToHearStory: String
}

object English : Strings {
    override val indonesian = false
    override val locale: Locale = Locale.US
    override val code = "EN"

    override val switchLanguage = "Language: English. Switch to Bahasa Indonesia"
    override val tagline = "Pick a surah, recite each verse, collect the stars!"
    override val tonightsBedtimeStory = "🌙 Tonight's bedtime story"
    override val back = "Back"

    override val oneVerse = "One verse"
    override val wholeSurah = "Whole surah"
    override fun verse(n: Int) = "Verse $n"
    override fun verseStars(n: Int, stars: Int) = "Verse $n, $stars of 3 stars"
    override val listen = "Listen"
    override val playing = "Playing…"
    override val tapAnyWord = "Tap any word to hear it 👂"
    override fun starsOutOf3(n: Int) = "$n of 3 stars"
    override fun wordsRight(right: Int, total: Int) = "$right of $total words right"
    override fun cheer(stars: Int) = when (stars) {
        3 -> "MashaAllah! Perfect! 🎉"
        2 -> "Great job! Nearly perfect 💪"
        1 -> "Good try! Listen, then try again 👂"
        else -> "Let's listen together, then try again 🌱"
    }
    override val iHeard = "I heard: "
    override val hearItRight = "Hear it right"
    override val next = "Next ➜"
    override val notTajweed = "Stars count the words, not the tajweed."
    override val stop = "Stop"
    override val recite = "Recite"
    override val noRecogniser = "No speech recogniser on this device"
    override fun listening(right: Int, total: Int) = "Listening… $right of $total words so far"
    override val listeningNow = "Listening… recite now!"
    override val audioFailed = "Couldn't play the audio — is the internet on?"
    override val tapToTryAgain = "Tap the mic to try again"
    override fun tapToRecite(verse: Int) = "Tap the mic and recite verse $verse"
    override val tapToReciteSurah = "Tap the mic and recite the whole surah — short pauses are fine"
    override fun failure(reason: Failure) = when (reason) {
        Failure.NO_RECOGNISER -> noRecogniser
        Failure.MICROPHONE -> "Microphone error"
        Failure.NO_MATCH -> "Did not catch that — try again"
        Failure.NO_SPEECH -> "No speech heard"
        Failure.PERMISSION -> "Microphone permission denied"
        Failure.NETWORK -> "Network needed — the Arabic pack may not be installed for offline use"
        Failure.NO_ARABIC -> "Arabic is not installed for speech recognition on this device"
        Failure.STUCK -> "Nothing heard — check the microphone and the Arabic language pack"
        Failure.OTHER -> "Recognition failed"
    }

    override val tonightsStory = "Tonight's Story"
    override val bedtimeStories = "Bedtime Stories"
    override fun storyCount(n: Int, of: Int) = "Story $n of $of · a new one every night"
    override fun retoldFrom(refs: String) = "Retold from $refs"
    override val lessonPrefix = "Tonight's lesson: "
    override fun fromQuran(key: String) = "From the Qur'an · $key"
    override val goodnight = "Goodnight, sleep well 🌙"
    override fun forGrownUps(refs: String) =
        "For grown-ups: this is a retelling for children, not the words of the Qur'an. " +
            "Only the verse above is Qur'anic text. Please check the story against $refs."
    override val previousStory = "Previous story"
    override val nextStory = "Next story"
    override val pause = "Pause"
    override val tellStory = "Tell me the story"
    override val noStoryVoice = "This phone has no English story voice — read it together!"
    override val telling = "Telling the story…"
    override val verseFailed = "Couldn't play the verse — is the internet on?"
    override val tapToHearStory = "Tap ▶ to hear the story"
}

object Indonesian : Strings {
    override val indonesian = true
    override val locale: Locale = Locale.forLanguageTag("id-ID")
    override val code = "ID"

    override val switchLanguage = "Bahasa: Indonesia. Ganti ke bahasa Inggris"
    override val tagline = "Pilih surah, baca tiap ayat, kumpulkan bintangnya!"
    override val tonightsBedtimeStory = "🌙 Cerita sebelum tidur malam ini"
    override val back = "Kembali"

    override val oneVerse = "Satu ayat"
    override val wholeSurah = "Satu surah"
    override fun verse(n: Int) = "Ayat $n"
    override fun verseStars(n: Int, stars: Int) = "Ayat $n, $stars dari 3 bintang"
    override val listen = "Dengarkan"
    override val playing = "Diputar…"
    override val tapAnyWord = "Ketuk kata mana saja untuk mendengarnya 👂"
    override fun starsOutOf3(n: Int) = "$n dari 3 bintang"
    override fun wordsRight(right: Int, total: Int) = "$right dari $total kata benar"
    override fun cheer(stars: Int) = when (stars) {
        3 -> "Masya Allah! Sempurna! 🎉"
        2 -> "Hebat! Hampir sempurna 💪"
        1 -> "Bagus! Dengarkan, lalu coba lagi 👂"
        else -> "Yuk dengarkan bersama, lalu coba lagi 🌱"
    }
    override val iHeard = "Yang terdengar: "
    override val hearItRight = "Dengar yang benar"
    override val next = "Lanjut ➜"
    override val notTajweed = "Bintang menghitung kata, bukan tajwid."
    override val stop = "Berhenti"
    override val recite = "Baca"
    override val noRecogniser = "Tidak ada pengenal suara di perangkat ini"
    override fun listening(right: Int, total: Int) = "Mendengarkan… $right dari $total kata"
    override val listeningNow = "Mendengarkan… ayo baca!"
    override val audioFailed = "Audio tidak bisa diputar — apakah internet menyala?"
    override val tapToTryAgain = "Ketuk mikrofon untuk mencoba lagi"
    override fun tapToRecite(verse: Int) = "Ketuk mikrofon dan baca ayat $verse"
    override val tapToReciteSurah = "Ketuk mikrofon dan baca satu surah penuh — boleh berhenti sejenak"
    override fun failure(reason: Failure) = when (reason) {
        Failure.NO_RECOGNISER -> noRecogniser
        Failure.MICROPHONE -> "Mikrofon bermasalah"
        Failure.NO_MATCH -> "Belum terdengar jelas — coba lagi"
        Failure.NO_SPEECH -> "Tidak ada suara terdengar"
        Failure.PERMISSION -> "Izin mikrofon ditolak"
        Failure.NETWORK -> "Perlu internet — paket bahasa Arab mungkin belum terpasang untuk offline"
        Failure.NO_ARABIC -> "Bahasa Arab belum terpasang untuk pengenalan suara di perangkat ini"
        Failure.STUCK -> "Tidak ada yang terdengar — periksa mikrofon dan paket bahasa Arab"
        Failure.OTHER -> "Pengenalan suara gagal"
    }

    override val tonightsStory = "Cerita Malam Ini"
    override val bedtimeStories = "Cerita Sebelum Tidur"
    override fun storyCount(n: Int, of: Int) = "Cerita $n dari $of · cerita baru tiap malam"
    override fun retoldFrom(refs: String) = "Diceritakan ulang dari $refs"
    override val lessonPrefix = "Pelajaran malam ini: "
    override fun fromQuran(key: String) = "Dari Al-Qur'an · $key"
    override val goodnight = "Selamat tidur, mimpi indah 🌙"
    override fun forGrownUps(refs: String) =
        "Untuk orang tua: ini cerita ulang untuk anak-anak, bukan kata-kata Al-Qur'an. " +
            "Hanya ayat di atas yang merupakan teks Al-Qur'an. Mohon periksa cerita ini dengan $refs."
    override val previousStory = "Cerita sebelumnya"
    override val nextStory = "Cerita berikutnya"
    override val pause = "Jeda"
    override val tellStory = "Ceritakan"
    override val noStoryVoice = "Ponsel ini belum punya suara bahasa Indonesia — baca bersama, yuk!"
    override val telling = "Sedang bercerita…"
    override val verseFailed = "Ayat tidak bisa diputar — apakah internet menyala?"
    override val tapToHearStory = "Ketuk ▶ untuk mendengarkan cerita"
}
