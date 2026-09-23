package com.singgih.quranreciter.recite

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** What the microphone is doing, for the UI to animate against. */
sealed interface ListenState {
    data object Idle : ListenState
    data object Listening : ListenState
    /** rms is the live input level, for the pulsing rings. */
    data class Hearing(val rms: Float) : ListenState
    data class Done(val text: String) : ListenState
    data class Failed(val reason: String) : ListenState
}

/**
 * Thin wrapper over Android's [SpeechRecognizer], asking for Arabic.
 *
 * Recognition quality is entirely the device's: it depends on the Arabic
 * language pack being installed and on whether the vendor's engine handles
 * Quranic recitation, which is not how most people speak. Treat a poor result as
 * "the recogniser did not understand", never as "the recitation was wrong".
 */
class SpeechController(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(onState: (ListenState) -> Unit) {
        if (!available) {
            onState(ListenState.Failed("No speech recogniser on this device"))
            return
        }
        stop()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = onState(ListenState.Listening)
                override fun onRmsChanged(rmsdB: Float) = onState(ListenState.Hearing(rmsdB))
                override fun onResults(results: Bundle) {
                    val text = results
                        .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    onState(ListenState.Done(text))
                }
                override fun onError(error: Int) = onState(ListenState.Failed(describe(error)))
                override fun onBeginningOfSpeech() = Unit
                override fun onEndOfSpeech() = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
            )
        }
    }

    fun stop() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun describe(error: Int) = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Microphone error"
        SpeechRecognizer.ERROR_NO_MATCH -> "Did not catch that — try again"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Network needed — the Arabic pack may not be installed for offline use"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
            "Arabic is not installed for speech recognition on this device"
        else -> "Recognition failed"
    }
}
