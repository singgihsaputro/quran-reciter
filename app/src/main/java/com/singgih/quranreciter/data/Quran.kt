package com.singgih.quranreciter.data

import android.content.Context
import org.json.JSONObject

/**
 * [translation] is Saheeh International, [translationIndonesian] the Kemenag
 * translation. [audio] holds one Quran.com word-audio path per word of
 * [arabic], in reading order.
 */
data class Verse(
    val number: Int,
    val arabic: String,
    val translation: String,
    val translationIndonesian: String,
    val audio: List<String>,
)
data class Surah(
    val number: Int,
    val name: String,
    val meaning: String,
    val meaningIndonesian: String,
    val verses: List<Verse>,
)

/**
 * Loads the bundled Uthmani text.
 *
 * `assets/quran.json` is fetched from the Quran.com API by `tools/fetch_quran.py`
 * and committed verbatim — no verse text is ever typed by hand.
 */
object Quran {
    fun load(context: Context): List<Surah> =
        context.assets.open("quran.json").bufferedReader().use { it.readText() }
            .let { org.json.JSONArray(it) }
            .let { array ->
                (0 until array.length()).map { i ->
                    val s = array.getJSONObject(i)
                    val verses = s.getJSONArray("verses")
                    Surah(
                        number = s.getInt("number"),
                        name = s.getString("name"),
                        meaning = s.getString("meaning"),
                        meaningIndonesian = s.getString("meaning_indonesian"),
                        verses = (0 until verses.length()).map { j ->
                            verses.getJSONObject(j).toVerse()
                        },
                    )
                }
            }

    private fun JSONObject.toVerse() = Verse(
        number = getInt("number"),
        arabic = getString("arabic"),
        translation = getString("translation"),
        translationIndonesian = getString("indonesian"),
        audio = getJSONArray("audio").let { a -> List(a.length()) { a.getString(it) } },
    )
}
