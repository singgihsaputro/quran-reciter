package com.singgih.quranreciter.data

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate

/** One language's telling of a story. */
data class Telling(val title: String, val paragraphs: List<String>, val lesson: String)

/**
 * A bedtime story: a retelling for children, written for this app in English
 * and Indonesian — prose, not scripture. [refs] names the verses it retells. It
 * ends on one real verse, [verseKey], whose [arabic] and translations are
 * fetched from Quran.com by `tools/fetch_quran.py`, never typed.
 */
data class Story(
    val emoji: String,
    val refs: String,
    val english: Telling,
    val indonesian: Telling,
    val verseKey: String,
    val arabic: String,
    val translation: String,
    val translationIndonesian: String,
) {
    val surah get() = verseKey.substringBefore(':').toInt()
    val verse get() = verseKey.substringAfter(':').toInt()
}

object Stories {
    fun load(context: Context): List<Story> {
        fun asset(name: String) = context.assets.open(name).bufferedReader().use { it.readText() }
        val verses = JSONObject(asset("story_verses.json"))
        val stories = JSONObject(asset("stories.json")).getJSONArray("stories")
        return List(stories.length()) { i ->
            val s = stories.getJSONObject(i)
            val key = s.getString("verse")
            val verse = verses.getJSONObject(key)
            Story(
                emoji = s.getString("emoji"),
                refs = s.getString("refs"),
                english = s.toTelling(),
                indonesian = s.getJSONObject("indonesian").toTelling(),
                verseKey = key,
                arabic = verse.getString("arabic"),
                translation = verse.getString("translation"),
                translationIndonesian = verse.getString("indonesian"),
            )
        }
    }

    private fun JSONObject.toTelling() = Telling(
        title = getString("title"),
        paragraphs = getJSONArray("paragraphs").let { p -> List(p.length()) { p.getString(it) } },
        lesson = getString("lesson"),
    )

    /** One story per night, walking through them in order, the same all day. */
    fun tonight(count: Int, today: LocalDate = LocalDate.now()) = Math.floorMod(today.toEpochDay(), count.toLong()).toInt()
}
