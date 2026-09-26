package com.singgih.quranreciter

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import java.util.Locale
import com.singgih.quranreciter.data.Progress
import com.singgih.quranreciter.data.Quran
import com.singgih.quranreciter.data.Stories
import com.singgih.quranreciter.data.Surah
import com.singgih.quranreciter.recite.QariPlayer
import com.singgih.quranreciter.recite.SpeechController
import com.singgih.quranreciter.ui.English
import com.singgih.quranreciter.ui.Indonesian
import com.singgih.quranreciter.ui.KidTheme
import com.singgih.quranreciter.ui.LocalStrings
import com.singgih.quranreciter.ui.ReciteScreen
import com.singgih.quranreciter.ui.Sky
import com.singgih.quranreciter.ui.StoryScreen
import com.singgih.quranreciter.ui.SurahList

private sealed interface Screen {
    data object Home : Screen
    data class Recite(val surah: Surah) : Screen
    data class Bedtime(val story: Int) : Screen
}

class MainActivity : ComponentActivity() {

    private var micGranted by mutableStateOf(false)

    private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        micGranted = it
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Light status-bar icons: every screen sits on a dark or saturated sky.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

        setContent {
            KidTheme {
                val context = LocalContext.current
                val surahs = remember { Quran.load(context) }
                val stories = remember { Stories.load(context) }
                val tonight = remember { Stories.tonight(stories.size) }
                val speech = remember { SpeechController(context) }
                val qari = remember { QariPlayer() }
                val progress = remember { Progress(context) }
                val settings = remember { context.getSharedPreferences("settings", MODE_PRIVATE) }
                // Starts in the phone's language; the switch on the home screen remembers the choice.
                var indonesian by remember {
                    mutableStateOf(settings.getBoolean("indonesian", Locale.getDefault().language in setOf("in", "id")))
                }
                var screen by remember { mutableStateOf<Screen>(Screen.Home) }

                BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

                CompositionLocalProvider(LocalStrings provides if (indonesian) Indonesian else English) {
                    Box(Modifier.fillMaxSize().background(Sky)) {
                        AnimatedContent(
                            targetState = screen,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "screen",
                        ) { current ->
                            val insets = Modifier.fillMaxSize().safeDrawingPadding()
                            when (current) {
                                Screen.Home -> Box(insets) {
                                    SurahList(
                                        surahs = surahs,
                                        progress = progress,
                                        tonight = stories[tonight],
                                        onOpen = { screen = Screen.Recite(it) },
                                        onStory = { screen = Screen.Bedtime(tonight) },
                                        onSwitchLanguage = {
                                            indonesian = !indonesian
                                            settings.edit { putBoolean("indonesian", indonesian) }
                                        },
                                    )
                                }
                                is Screen.Recite -> Box(insets) {
                                    ReciteScreen(
                                        surah = current.surah,
                                        speech = speech,
                                        qari = qari,
                                        progress = progress,
                                        hasMicPermission = micGranted,
                                        onRequestMic = { askMic.launch(Manifest.permission.RECORD_AUDIO) },
                                        onBack = { screen = Screen.Home },
                                    )
                                }
                                // Draws its own night sky edge to edge, and insets itself.
                                is Screen.Bedtime -> StoryScreen(stories, current.story, qari) { screen = Screen.Home }
                            }
                        }
                    }
                }
            }
        }
    }
}
