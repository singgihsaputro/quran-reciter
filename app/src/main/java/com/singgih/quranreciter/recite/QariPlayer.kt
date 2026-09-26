package com.singgih.quranreciter.recite

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Plays how a verse, or one word of it, should sound — streamed, never bundled.
 *
 * Verses are Mahmoud Khalil al-Husary's teaching recitation (al-Mu'allim): slow
 * and deliberate, made for learners to copy. Words are Quran.com's
 * word-by-word audio. Both need the network.
 */
class QariPlayer {

    companion object {
        fun verseUrl(surah: Int, verse: Int) =
            "https://everyayah.com/data/Husary_Muallim_128kbps/%03d%03d.mp3".format(surah, verse)

        fun wordUrl(path: String) = "https://audio.qurancdn.com/$path"
    }

    private var player: MediaPlayer? = null

    /** The url playing now, so the UI can show which button or word is sounding. */
    var playing by mutableStateOf<String?>(null)
        private set

    var failed by mutableStateOf(false)
        private set

    fun play(url: String) {
        stop()
        failed = false
        playing = url
        // setDataSource throws on a malformed source; that is a failed play, not a crash.
        player = runCatching { MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            setOnPreparedListener { it.start() }
            setOnCompletionListener { stop() }
            setOnErrorListener { _, _, _ -> stop(); failed = true; true }
            setDataSource(url)
            prepareAsync()
        } }.getOrElse { playing = null; failed = true; null }
    }

    fun stop() {
        player?.release()
        player = null
        playing = null
    }
}
