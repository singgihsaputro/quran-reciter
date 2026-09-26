package com.singgih.quranreciter.data

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf

/** Best stars earned per verse, kept on the device. Reads are Compose state. */
class Progress(context: Context) {

    private val prefs = context.getSharedPreferences("stars", Context.MODE_PRIVATE)
    private val best = mutableStateMapOf<String, Int>().apply {
        prefs.all.forEach { (key, value) -> if (value is Int) put(key, value) }
    }

    fun stars(surah: Int, verse: Int) = best["$surah:$verse"] ?: 0

    fun stars(surah: Surah) = surah.verses.sumOf { stars(surah.number, it.number) }

    /** Keeps the best attempt; a worse one never takes stars away. */
    fun record(surah: Int, verse: Int, stars: Int) {
        val key = "$surah:$verse"
        if (stars <= (best[key] ?: 0)) return
        best[key] = stars
        prefs.edit().putInt(key, stars).apply()
    }
}
