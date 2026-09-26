package com.singgih.quranreciter.recite

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Why listening stopped without a result; the UI words it in the chosen language. */
enum class Failure { NO_RECOGNISER, MICROPHONE, NO_MATCH, NO_SPEECH, PERMISSION, NETWORK, NO_ARABIC, STUCK, OTHER }

/** What the microphone is doing, for the UI to animate against. */
sealed interface ListenState {
    data object Idle : ListenState
    data object Listening : ListenState
    /** rms is the live input level, for the pulsing rings. */
    data class Hearing(val rms: Float) : ListenState
    data class Done(val text: String) : ListenState
    data class Failed(val reason: Failure) : ListenState
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

    private companion object { const val TAG = "Recite" }

    private var recognizer: SpeechRecognizer? = null

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Google's full recogniser, if it is installed.
     *
     * The system default is often `com.google.android.tts`, a small on-device
     * model that ships with a handful of languages and frequently not Arabic.
     * `googlequicksearchbox` is the full engine. Targeting it explicitly is the
     * difference between Arabic working and a bare "language unavailable".
     */
    private fun preferredService(): ComponentName? {
        val intent = Intent(RecognitionService.SERVICE_INTERFACE)
        val services = context.packageManager.queryIntentServices(intent, 0)
        val best = services.firstOrNull {
            it.serviceInfo.packageName == "com.google.android.googlequicksearchbox"
        } ?: return null
        return ComponentName(best.serviceInfo.packageName, best.serviceInfo.name)
    }

    /** [onPartial] gets the text so far, so words can light up as they are said. */
    fun start(onPartial: (String) -> Unit = {}, onState: (ListenState) -> Unit) {
        if (!available) {
            onState(ListenState.Failed(Failure.NO_RECOGNISER))
            return
        }
        stop()
        val service = preferredService()
        Log.i(TAG, "using recogniser: ${service?.flattenToShortString() ?: "system default"}")

        recognizer = (
            if (service != null) SpeechRecognizer.createSpeechRecognizer(context, service)
            else SpeechRecognizer.createSpeechRecognizer(context)
        ).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = onState(ListenState.Listening)
                override fun onRmsChanged(rmsdB: Float) = onState(ListenState.Hearing(rmsdB))
                override fun onResults(results: Bundle) {
                    val text = results
                        .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    Log.i(TAG, "heard: \"$text\"")
                    onState(ListenState.Done(text))
                }
                override fun onError(error: Int) {
                    Log.w(TAG, "recognition error $error: ${describe(error)}")
                    onState(ListenState.Failed(describe(error)))
                }
                override fun onBeginningOfSpeech() = Unit
                override fun onEndOfSpeech() = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.let(onPartial)
                }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                    )
                    // ar-SA is recognised more widely than bare "ar".
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-SA")
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    // NOT offline-only: the on-device Arabic model is usually
                    // absent, and forcing offline turns that into a hard failure
                    // instead of falling back to the network.
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                }
            )
        }
    }

    fun stop() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun describe(error: Int) = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> Failure.MICROPHONE
        SpeechRecognizer.ERROR_NO_MATCH -> Failure.NO_MATCH
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Failure.NO_SPEECH
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Failure.PERMISSION
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> Failure.NETWORK
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> Failure.NO_ARABIC
        else -> Failure.OTHER
    }
}
