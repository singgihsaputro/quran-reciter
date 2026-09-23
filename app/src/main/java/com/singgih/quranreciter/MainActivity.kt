package com.singgih.quranreciter

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.singgih.quranreciter.data.Quran
import com.singgih.quranreciter.data.Surah
import com.singgih.quranreciter.recite.SpeechController
import com.singgih.quranreciter.ui.ReciteScreen
import com.singgih.quranreciter.ui.SurahList

class MainActivity : ComponentActivity() {

    private var micGranted by mutableStateOf(false)

    private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        micGranted = it
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

        setContent {
            MaterialTheme {
                val context = LocalContext.current
                val surahs = remember { Quran.load(context) }
                val speech = remember { SpeechController(context) }
                var open by remember { mutableStateOf<Surah?>(null) }

                Scaffold(
                    topBar = {
                        @OptIn(ExperimentalMaterial3Api::class)
                        TopAppBar(
                            title = { Text(open?.name ?: "Recite") },
                            navigationIcon = {
                                if (open != null) {
                                    TextButton(onClick = { open = null }) { Text("Back") }
                                }
                            },
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                ) { padding ->
                    Surface(Modifier.padding(padding)) {
                        when (val surah = open) {
                            null -> SurahList(surahs) { open = it }
                            else -> ReciteScreen(
                                surah = surah,
                                speech = speech,
                                hasMicPermission = micGranted,
                                onRequestMic = { askMic.launch(Manifest.permission.RECORD_AUDIO) },
                            )
                        }
                    }
                }
            }
        }
    }
}
