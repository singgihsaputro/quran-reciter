package com.singgih.quranreciter.data

import android.content.Context
import org.json.JSONObject

data class Verse(val number: Int, val arabic: String, val translation: String)
data class Surah(val number: Int, val name: String, val meaning: String, val verses: List<Verse>)

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
    )
}
