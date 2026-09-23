package com.singgih.quranreciter.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.singgih.quranreciter.data.Surah
import com.singgih.quranreciter.data.Verse
import com.singgih.quranreciter.recite.*
import kotlinx.coroutines.delay

private val Correct = Color(0xFF2E9E6B)
private val Misread = Color(0xFFD08A1E)
private val Missed = Color(0xFF9199A1)

@Composable
fun SurahList(surahs: List<Surah>, onOpen: (Surah) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(surahs) { surah ->
            ElevatedCard(
                onClick = { onOpen(surah) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${surah.number}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(36.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(surah.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${surah.meaning} · ${surah.verses.size} verses",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReciteScreen(
    surah: Surah,
    speech: SpeechController,
    onRequestMic: () -> Unit,
    hasMicPermission: Boolean,
) {
    var selected by remember(surah) { mutableStateOf(surah.verses.first()) }
    var state by remember { mutableStateOf<ListenState>(ListenState.Idle) }
    var result by remember { mutableStateOf<RecitationResult?>(null) }
    var awaitingPermission by remember { mutableStateOf(false) }
    // Identifies one listening session, so a watchdog cannot clobber the result
    // of the session that already finished.
    var session by remember { mutableIntStateOf(0) }

    val listening = state is ListenState.Listening || state is ListenState.Hearing

    fun beginListening() {
        result = null
        session++
        speech.start { s -> 
            state = s
            if (s is ListenState.Done) {
                result = RecitationMatcher.match(selected.arabic, s.text)
            }
        }
    }

    fun stopListening(reason: String? = null) {
        speech.stop()
        state = reason?.let { ListenState.Failed(it) } ?: ListenState.Idle
    }

    // Granting the permission should start the recitation the tap asked for,
    // rather than making the user tap a second time.
    LaunchedEffect(hasMicPermission) {
        if (hasMicPermission && awaitingPermission) {
            awaitingPermission = false
            beginListening()
        }
    }

    // Some engines never call back — no result, no error. Without this the UI
    // sits on "Listening…" forever with no way out.
    LaunchedEffect(session, listening) {
        if (!listening) return@LaunchedEffect
        val watched = session
        delay(15_000)
        val stillStuck = watched == session &&
            (state is ListenState.Listening || state is ListenState.Hearing)
        if (stillStuck) {
            stopListening("Nothing heard — check the microphone and the Arabic language pack")
        }
    }

    DisposableEffect(Unit) { onDispose { speech.stop() } }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            surah.meaning,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        VerseChips(
            verses = surah.verses,
            selected = selected,
            onSelect = { selected = it; result = null; state = ListenState.Idle },
        )

        Spacer(Modifier.height(16.dp))

        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(18.dp)) {
                AnimatedVerse(verse = selected, result = result)
                Spacer(Modifier.height(14.dp))
                Text(
                    selected.translation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                result?.let {
                    Spacer(Modifier.height(18.dp))
                    ScoreBar(it)
                }
            }
        }

        Spacer(Modifier.weight(1f))
        MicButton(
            state = state,
            enabled = speech.available,
            onClick = {
                when {
                    listening -> stopListening()
                    !hasMicPermission -> { awaitingPermission = true; onRequestMic() }
                    else -> beginListening()
                }
            },
        )
        StatusLine(state, speech.available)
    }
}

/** Verse numbers as a row of chips; the selected one scales up. */
@Composable
private fun VerseChips(verses: List<Verse>, selected: Verse, onSelect: (Verse) -> Unit) {
    val listState = rememberLazyListState()

    // A plain Row clipped everything past the sixth chip. Juz 'Amma surahs run to
    // 40+ verses, so this scrolls, and follows the selection when it moves.
    LaunchedEffect(selected.number) {
        listState.animateScrollToItem(index = (selected.number - 1).coerceAtLeast(0))
    }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(verses, key = { it.number }) { verse ->
            val isSelected = verse.number == selected.number
            val scale by animateFloatAsState(if (isSelected) 1.12f else 1f, label = "chip")
            AssistChip(
                onClick = { onSelect(verse) },
                label = { Text("${verse.number}") },
                modifier = Modifier.scale(scale),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface,
                ),
            )
        }
    }
}

/**
 * The verse, word by word.
 *
 * Before a recitation the words fade in from the right, which is the direction
 * Arabic is read. After one, each word animates to the colour of its result.
 * Untested words stay neutral — the colours only ever appear once there is a
 * real result to show.
 */
@Composable
private fun AnimatedVerse(verse: Verse, result: RecitationResult?) {
    val words = remember(verse) { verse.arabic.split(" ").filter { it.isNotBlank() } }

    FlowRowRtl {
        words.forEachIndexed { index, word ->
            val status = result?.words?.getOrNull(index)?.status
            val target = when (status) {
                WordStatus.CORRECT -> Correct
                WordStatus.MISREAD -> Misread
                WordStatus.MISSED -> Missed
                null -> MaterialTheme.colorScheme.onSurface
            }
            val color by animateColorAsState(target, tween(420), label = "word")

            // stagger the entrance so the verse assembles rather than snapping in
            val appear = remember(verse) { Animatable(0f) }
            LaunchedEffect(verse) {
                delay(index * 55L)
                appear.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            }

            Text(
                text = word,
                fontSize = 26.sp,
                color = color,
                textAlign = TextAlign.Right,
                modifier = Modifier
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .scale(appear.value),
            )
        }
    }
}

@Composable
private fun ScoreBar(result: RecitationResult) {
    val progress by animateFloatAsState(result.accuracy, tween(700), label = "score")
    Column {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = if (result.accuracy > 0.85f) Correct else Misread,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "${result.correctCount} of ${result.total} words matched",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Word match only — this does not check tajweed.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Mic button with rings that pulse to the live input level. */
@Composable
private fun MicButton(state: ListenState, enabled: Boolean, onClick: () -> Unit) {
    val listening = state is ListenState.Listening || state is ListenState.Hearing
    val level = (state as? ListenState.Hearing)?.rms ?: 0f
    val ring by animateFloatAsState(
        if (listening) 1f + (level.coerceIn(0f, 10f) / 18f) else 1f,
        spring(stiffness = Spring.StiffnessLow),
        label = "ring",
    )
    val infinite = rememberInfiniteTransition(label = "idlePulse")
    val idle by infinite.animateFloat(
        initialValue = 1f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "idle",
    )

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(88.dp)
                .scale(if (listening) ring else idle)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), CircleShape)
        )
        FilledIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(64.dp),
        ) { Text(if (listening) "■" else "🎙", fontSize = 22.sp) }
    }
}

@Composable
private fun StatusLine(state: ListenState, available: Boolean) {
    val text = when {
        !available -> "No speech recogniser on this device"
        state is ListenState.Listening -> "Listening…"
        state is ListenState.Hearing -> "Listening…"
        state is ListenState.Failed -> state.reason
        state is ListenState.Done -> "Heard: ${state.text.ifBlank { "nothing" }}"
        else -> "Tap the microphone and recite the selected verse"
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        textAlign = TextAlign.Center,
    )
}

/** Wraps words right-to-left, so the verse reads the way Arabic is read. */
@Composable
private fun FlowRowRtl(content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content = { content() }) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val rows = mutableListOf<MutableList<androidx.compose.ui.layout.Placeable>>(mutableListOf())
        var rowWidth = 0
        placeables.forEach { p ->
            if (rowWidth + p.width > constraints.maxWidth && rows.last().isNotEmpty()) {
                rows += mutableListOf<androidx.compose.ui.layout.Placeable>()
                rowWidth = 0
            }
            rows.last() += p
            rowWidth += p.width
        }
        val height = rows.sumOf { row -> row.maxOfOrNull { it.height } ?: 0 }
        layout(constraints.maxWidth, height) {
            var y = 0
            rows.forEach { row ->
                var x = constraints.maxWidth
                row.forEach { p ->
                    x -= p.width
                    p.placeRelative(x, y)
                }
                y += row.maxOfOrNull { it.height } ?: 0
            }
        }
    }
}
