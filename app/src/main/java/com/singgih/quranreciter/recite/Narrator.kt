package com.singgih.quranreciter.recite

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Reads a story aloud with the device's own text-to-speech, one part at a time,
 * so the screen can follow along. Callbacks arrive on a binder thread and are
 * moved to the main thread before they touch any state.
 */
class Narrator(context: Context) {

    private val main = ContextCompat.getMainExecutor(context)
    private lateinit var tts: TextToSpeech
    private var last = -1

    /** Null while the engine starts, false if it could not. The story can still be read by eye. */
    var ready by mutableStateOf<Boolean?>(null)
        private set

    /** Index of the part being read now, or null when quiet. */
    var reading by mutableStateOf<Int?>(null)
        private set

    /** Goes up each time a story is read to the end, for the screen to react to. */
    var finished by mutableIntStateOf(0)
        private set

    init {
        // On failure onInit can fire inside the constructor, before `tts` is
        // assigned — so `tts` is only touched once status says SUCCESS.
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            // A little slower than conversation: this is a bedtime story.
            if (ready == true) tts.setSpeechRate(0.9f)
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) = main.execute { reading = id.toInt() }
            override fun onDone(id: String) = main.execute {
                if (id.toInt() == last) {
                    reading = null
                    finished++
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String) = main.execute { reading = null }
        })
    }

    /** Whether this phone has a voice for [locale]; Indonesian often needs a download. */
    fun speaks(locale: Locale) =
        ready == true && tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE

    /** Reads [parts] aloud in [locale], from [from] to the end. */
    fun read(parts: List<String>, locale: Locale, from: Int = 0) {
        if (!speaks(locale)) return
        tts.stop()
        tts.setLanguage(locale)
        last = parts.lastIndex
        for (i in from..parts.lastIndex) {
            tts.speak(parts[i], TextToSpeech.QUEUE_ADD, null, "$i")
        }
    }

    fun stop() {
        tts.stop()
        reading = null
    }

    fun shutdown() = tts.shutdown()
}
