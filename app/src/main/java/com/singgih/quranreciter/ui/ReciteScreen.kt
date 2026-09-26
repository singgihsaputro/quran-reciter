package com.singgih.quranreciter.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.singgih.quranreciter.data.Progress
import com.singgih.quranreciter.data.Surah
import com.singgih.quranreciter.data.Verse
import com.singgih.quranreciter.recite.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Recite one verse, or the whole surah in one go.
 *
 * Both are the same game over a different stretch of text: [targets] is either
 * the selected verse or every verse, matched as one sequence, and each verse
 * then keeps the stars of its own share of the result.
 */
@Composable
fun ReciteScreen(
    surah: Surah,
    speech: SpeechController,
    qari: QariPlayer,
    progress: Progress,
    hasMicPermission: Boolean,
    onRequestMic: () -> Unit,
    onBack: () -> Unit,
) {
    val s = LocalStrings.current
    var wholeSurah by remember(surah) { mutableStateOf(false) }
    var selected by remember(surah) { mutableStateOf(surah.verses.first()) }
    val targets = if (wholeSurah) surah.verses else listOf(selected)
    val expected = remember(targets) { targets.joinToString(" ") { it.arabic } }
    // Where each target verse's words start in a result over all of them.
    val starts = remember(targets) {
        targets.runningFold(0) { at, verse -> at + ArabicNormalizer.words(verse.arabic).size }
    }

    var state by remember { mutableStateOf<ListenState>(ListenState.Idle) }
    var result by remember { mutableStateOf<RecitationResult?>(null) }
    // What has been heard so far, while the child is still reciting.
    var live by remember { mutableStateOf<RecitationResult?>(null) }
    // A whole surah outlasts one recognition session — the recogniser stops at
    // the first pause. Each session's text is kept here and the next one starts
    // straight away, until the last word is heard or the child goes quiet.
    var heard by remember { mutableStateOf("") }
    var partial by remember { mutableStateOf("") }
    var quiet by remember { mutableIntStateOf(0) }
    var awaitingPermission by remember { mutableStateOf(false) }
    // Identifies one listening session, so a watchdog cannot clobber the next.
    var session by remember { mutableIntStateOf(0) }
    var celebrate by remember { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()

    val listening = state is ListenState.Listening || state is ListenState.Hearing
    fun part(r: RecitationResult, i: Int) = r.slice(starts[i], starts[i + 1])
    // "Hear it right" plays the first verse with a slip.
    val firstSlip = result?.let { r -> targets.indices.firstOrNull { part(r, it).stars < 3 } } ?: 0
    val hearItUrl = QariPlayer.verseUrl(surah.number, targets[firstSlip].number)

    fun reset() {
        speech.stop()
        qari.stop()
        result = null
        live = null
        heard = ""
        partial = ""
        quiet = 0
        state = ListenState.Idle
    }

    fun select(verse: Verse) {
        reset()
        selected = verse
    }

    fun switchMode(whole: Boolean) {
        if (whole == wholeSurah) return
        reset()
        wholeSurah = whole
    }

    fun toggle(url: String) = if (qari.playing == url) qari.stop() else qari.play(url)

    fun finish(text: String) {
        speech.stop()
        val r = RecitationMatcher.match(expected, text)
        result = r
        live = null
        state = ListenState.Done(text)
        targets.forEachIndexed { i, verse -> progress.record(surah.number, verse.number, part(r, i).stars) }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        if (r.stars == 3) celebrate++
    }

    fun listen() {
        session++
        speech.start(onPartial = {
            partial = it
            live = RecitationMatcher.match(expected, "$heard $it")
        }) { st ->
            state = st
            if (!wholeSurah) {
                if (st is ListenState.Done) finish(st.text)
                return@start
            }
            val text = (st as? ListenState.Done)?.text
            val pause = st is ListenState.Failed && (st.reason == Failure.NO_MATCH || st.reason == Failure.NO_SPEECH)
            if (text == null && !(pause && heard.isNotBlank())) return@start
            if (text.isNullOrBlank()) quiet++ else {
                heard = "$heard $text".trim()
                partial = ""
                quiet = 0
            }
            val sofar = RecitationMatcher.match(expected, heard)
            live = sofar
            if (sofar.words.lastOrNull()?.status == WordStatus.CORRECT || quiet >= 2) finish(heard)
            else scope.launch { delay(150); listen() }
        }
    }

    fun begin() {
        reset()
        listen()
    }

    // Stopping a whole surah part-way still scores what was recited.
    fun stop() {
        val sofar = "$heard $partial".trim()
        if (wholeSurah && sofar.isNotBlank()) finish(sofar) else {
            speech.stop()
            live = null
            state = ListenState.Idle
        }
    }

    // Granting the permission should start the recitation the tap asked for,
    // rather than making the user tap a second time.
    LaunchedEffect(hasMicPermission) {
        if (hasMicPermission && awaitingPermission) {
            awaitingPermission = false
            begin()
        }
    }

    // Some engines never call back — no result, no error. Without this the UI
    // sits on "Listening…" forever. Every word heard restarts the clock.
    LaunchedEffect(session, listening, partial) {
        if (!listening) return@LaunchedEffect
        delay(15_000)
        if (wholeSurah && heard.isNotBlank()) finish(heard) else {
            speech.stop()
            live = null
            state = ListenState.Failed(Failure.STUCK)
        }
    }

    // A miss is answered with how it should sound: once the stars have landed,
    // the qari recites the first verse with a slip. Any new action cancels it.
    LaunchedEffect(result) {
        val r = result ?: return@LaunchedEffect
        delay(100) // let the result panel lay out before scrolling to it
        list.animateScrollToItem(list.layoutInfo.totalItemsCount - 1)
        if (r.stars < 3) {
            delay(1700)
            qari.play(hearItUrl)
        }
    }

    // Whole surah: keep the verse being recited in view.
    val reciting = live?.let { r ->
        val word = r.words.indexOfLast { it.status == WordStatus.CORRECT }
        starts.indexOfLast { it <= word }.coerceIn(0, targets.lastIndex)
    }
    LaunchedEffect(reciting) {
        if (wholeSurah && reciting != null) list.animateScrollToItem(reciting)
    }

    DisposableEffect(Unit) {
        onDispose {
            speech.stop()
            qari.stop()
        }
    }

    val next = surah.verses.getOrNull(surah.verses.indexOf(selected) + 1)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            TopBar(surah.name, s.meaning(surah), onBack) {
                StarPill("${progress.stars(surah)} / ${surah.verses.size * 3}")
            }
            ModeSwitch(wholeSurah) { switchMode(it) }
            if (!wholeSurah) VerseBubbles(surah, selected, progress) { select(it) }
            LazyColumn(
                state = list,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(targets, key = { _, verse -> verse.number }) { i, verse ->
                    val url = QariPlayer.verseUrl(surah.number, verse.number)
                    VerseCard(
                        verse = verse,
                        result = (result ?: live)?.let { part(it, i) },
                        final = result != null,
                        playing = qari.playing,
                        verseAudioPlaying = qari.playing == url,
                        onListen = { toggle(url) },
                        onWord = { qari.play(QariPlayer.wordUrl(it)) },
                    )
                }
                result?.let { r ->
                    item(key = "result") {
                        ResultPanel(
                            result = r,
                            heard = (state as? ListenState.Done)?.text.orEmpty(),
                            playing = qari.playing == hearItUrl,
                            onListen = { toggle(hearItUrl) },
                            onNext = if (wholeSurah) null else next?.let { { select(it) } },
                        )
                    }
                }
            }
            MicButton(
                state = state,
                enabled = speech.available,
                onClick = {
                    when {
                        listening -> stop()
                        !hasMicPermission -> { awaitingPermission = true; onRequestMic() }
                        else -> begin()
                    }
                },
            )
            StatusLine(state, speech.available, live, selected.number, wholeSurah, qari.failed)
        }
        Confetti(celebrate)
    }
}

/** One verse at a time, or the whole surah in one go. */
@Composable
private fun ModeSwitch(wholeSurah: Boolean, onChange: (Boolean) -> Unit) {
    val s = LocalStrings.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.18f), CircleShape)
            .padding(4.dp),
    ) {
        listOf(false to "📖 ${s.oneVerse}", true to "🕌 ${s.wholeSurah}").forEach { (whole, label) ->
            val on = whole == wholeSurah
            Surface(
                onClick = { onChange(whole) },
                shape = CircleShape,
                color = if (on) Color.White else Color.Transparent,
                modifier = Modifier.weight(1f).semantics { selected = on },
            ) {
                Text(
                    label,
                    color = if (on) Grape else Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 9.dp),
                )
            }
        }
    }
}

@Composable
fun TopBar(title: String, subtitle: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    val back = LocalStrings.current.back
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onBack,
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.22f),
            modifier = Modifier.size(44.dp).semantics { contentDescription = back },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("←", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
        }
        trailing()
    }
}

/** Verse numbers as level bubbles, each showing the stars it has earned. */
@Composable
private fun VerseBubbles(surah: Surah, selected: Verse, progress: Progress, onSelect: (Verse) -> Unit) {
    val s = LocalStrings.current
    val listState = rememberLazyListState()

    // Juz 'Amma surahs run to 40+ verses, so this scrolls, and follows the
    // selection when it moves.
    LaunchedEffect(selected.number) {
        listState.animateScrollToItem(index = (selected.number - 2).coerceAtLeast(0))
    }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(surah.verses, key = { it.number }) { verse ->
            val isSelected = verse.number == selected.number
            val stars = progress.stars(surah.number, verse.number)
            val scale by animateFloatAsState(
                if (isSelected) 1.15f else 1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "bubble",
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .scale(scale)
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onSelect(verse) }
                    .padding(6.dp)
                    .clearAndSetSemantics { contentDescription = s.verseStars(verse.number, stars) },
            ) {
                Box(
                    Modifier
                        .size(42.dp)
                        .background(
                            when {
                                isSelected -> Sun
                                stars == 3 -> Correct
                                else -> Color.White
                            },
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${verse.number}",
                        fontWeight = FontWeight.ExtraBold,
                        color = if (stars == 3 && !isSelected) Color.White else Ink,
                    )
                }
                Text("★".repeat(stars) + "☆".repeat(3 - stars), fontSize = 11.sp, color = Sun)
            }
        }
    }
}

@Composable
private fun VerseCard(
    verse: Verse,
    result: RecitationResult?,
    final: Boolean,
    playing: String?,
    verseAudioPlaying: Boolean,
    onListen: () -> Unit,
    onWord: (String) -> Unit,
) {
    val s = LocalStrings.current
    Surface(
        shape = MaterialTheme.shapes.large,
        color = Color.White,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.verse(verse.number),
                    color = Grape,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f),
                )
                ListenChip(s.listen, verseAudioPlaying, onListen)
            }
            Spacer(Modifier.height(10.dp))
            AnimatedVerse(verse, result, final, playing, onWord)
            Spacer(Modifier.height(6.dp))
            Text(
                s.tapAnyWord,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                s.translation(verse),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ListenChip(label: String, playing: Boolean, onClick: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "sound").animateFloat(
        initialValue = 1f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
        label = "pulse",
    )
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (playing) Sun else Grape.copy(alpha = 0.1f),
        modifier = Modifier.scale(if (playing) pulse else 1f),
    ) {
        Text(
            if (playing) "🔊 ${LocalStrings.current.playing}" else "🔊 $label",
            color = Ink,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}

/**
 * The verse, word by word, each word a button that plays how it sounds.
 *
 * Before a recitation the words pop in from the right, the direction Arabic is
 * read. While reciting, heard words light up green one by one. After, each word
 * takes the colour of its result, and the slips give a little shake so the eye
 * goes straight to them.
 */
@Composable
private fun AnimatedVerse(
    verse: Verse,
    result: RecitationResult?,
    final: Boolean,
    playing: String?,
    onWord: (String) -> Unit,
) {
    val words = remember(verse) { ArabicNormalizer.displayWords(verse.arabic) }

    FlowRowRtl {
        words.forEachIndexed { index, word ->
            // Mid-recitation only the heard words light up; the rest are waiting, not wrong.
            val status = result?.words?.getOrNull(index)?.status
                ?.takeIf { final || it == WordStatus.CORRECT }
            val tint = when (status) {
                WordStatus.CORRECT -> Correct
                WordStatus.MISREAD -> Misread
                WordStatus.MISSED -> Missed
                null -> Ink
            }
            val audio = verse.audio.getOrNull(index)
            val sounding = audio != null && playing == QariPlayer.wordUrl(audio)
            val fg by animateColorAsState(tint, tween(420), label = "word")
            val bg by animateColorAsState(
                when {
                    sounding -> Sun.copy(alpha = 0.55f)
                    status == null -> Color.Transparent
                    else -> tint.copy(alpha = 0.13f)
                },
                tween(300),
                label = "wordBg",
            )

            // stagger the entrance so the verse assembles rather than snapping in
            val appear = remember(verse) { Animatable(0f) }
            LaunchedEffect(verse) {
                delay(index * 55L)
                appear.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            }

            val shake = remember { Animatable(0f) }
            LaunchedEffect(result, final) {
                if (final && (status == WordStatus.MISREAD || status == WordStatus.MISSED)) {
                    delay(300)
                    shake.animateTo(0f, keyframes {
                        durationMillis = 480
                        -7f at 60
                        7f at 160
                        -5f at 260
                        4f at 360
                    })
                }
            }

            Text(
                text = word,
                fontSize = 28.sp,
                color = fg,
                modifier = Modifier
                    .padding(3.dp)
                    .graphicsLayer {
                        scaleX = appear.value
                        scaleY = appear.value
                        translationX = shake.value.dp.toPx()
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .clickable(enabled = audio != null) { audio?.let(onWord) }
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun ResultPanel(
    result: RecitationResult,
    heard: String,
    playing: Boolean,
    onListen: () -> Unit,
    onNext: (() -> Unit)?,
) {
    val s = LocalStrings.current
    val score = remember(result) { Animatable(0f) }
    LaunchedEffect(result) { score.animateTo(result.accuracy * 100, tween(900, easing = FastOutSlowInEasing)) }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = Color.White,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = s.starsOutOf3(result.stars)
                },
            ) {
                repeat(3) { i -> PopStar(earned = i < result.stars, big = i == 1, delayMs = 200L + i * 220, key = result) }
            }
            Text("${score.value.roundToInt()}", fontSize = 48.sp, fontWeight = FontWeight.Black, color = Grape)
            Text(
                s.wordsRight(result.correctCount, result.total),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                s.cheer(result.stars),
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Ink,
                textAlign = TextAlign.Center,
            )
            if (heard.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    s.iHeard + heard,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ListenChip(s.hearItRight, playing, onListen)
                onNext?.let {
                    Button(onClick = it, colors = ButtonDefaults.buttonColors(containerColor = Grape)) {
                        Text(s.next, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                s.notTajweed,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PopStar(earned: Boolean, big: Boolean, delayMs: Long, key: Any) {
    val pop = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(delayMs)
        pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessLow))
    }
    Text(
        "★",
        fontSize = if (big) 64.sp else 48.sp,
        color = if (earned) Sun else Missed.copy(alpha = 0.3f),
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
                rotationZ = (1 - pop.value) * -90f
            },
    )
}

/** Mic button with rings that pulse to the live input level. */
@Composable
private fun MicButton(state: ListenState, enabled: Boolean, onClick: () -> Unit) {
    val s = LocalStrings.current
    val listening = state is ListenState.Listening || state is ListenState.Hearing
    val level = (state as? ListenState.Hearing)?.rms ?: 0f
    val ring by animateFloatAsState(
        if (listening) 1f + (level.coerceIn(0f, 10f) / 14f) else 1f,
        spring(stiffness = Spring.StiffnessLow),
        label = "ring",
    )
    val idle by rememberInfiniteTransition(label = "idlePulse").animateFloat(
        initialValue = 1f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "idle",
    )

    Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(112.dp)
                .scale(if (listening) ring * 1.1f else idle)
                .background(Color.White.copy(alpha = 0.14f), CircleShape)
        )
        Box(
            Modifier
                .size(96.dp)
                .scale(if (listening) ring else 1f)
                .background(Color.White.copy(alpha = 0.22f), CircleShape)
        )
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            color = if (listening) Misread else Sun,
            shadowElevation = 10.dp,
            modifier = Modifier
                .size(80.dp)
                .semantics { contentDescription = if (listening) s.stop else s.recite },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(if (listening) "■" else "🎙", fontSize = 32.sp, color = Color.White)
            }
        }
    }
}

@Composable
private fun StatusLine(
    state: ListenState,
    available: Boolean,
    live: RecitationResult?,
    verse: Int,
    wholeSurah: Boolean,
    audioFailed: Boolean,
) {
    val s = LocalStrings.current
    val text = when {
        !available -> s.noRecogniser
        state is ListenState.Listening || state is ListenState.Hearing ->
            live?.let { s.listening(it.correctCount, it.total) } ?: s.listeningNow
        audioFailed -> s.audioFailed
        state is ListenState.Failed -> s.failure(state.reason)
        state is ListenState.Done -> s.tapToTryAgain
        wholeSurah -> s.tapToReciteSurah
        else -> s.tapToRecite(verse)
    }
    Text(
        text,
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
    )
}

/** A burst of falling confetti each time [trigger] goes up. Draws only; never takes a touch. */
@Composable
private fun Confetti(trigger: Int) {
    if (trigger == 0) return
    val fall = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) { fall.animateTo(1f, tween(2600, easing = LinearEasing)) }
    val pieces = remember(trigger) {
        List(80) {
            Piece(
                x = Random.nextFloat(),
                start = Random.nextFloat() * 0.3f,
                speed = 0.8f + Random.nextFloat() * 0.6f,
                spin = Random.nextFloat() * 720f - 360f,
                color = (Candy + Sun).random(),
            )
        }
    }
    if (fall.value >= 1f) return

    Canvas(Modifier.fillMaxSize()) {
        val fade = ((1f - fall.value) / 0.2f).coerceIn(0f, 1f)
        pieces.forEach { p ->
            val t = ((fall.value - p.start) / (1f - p.start)).coerceAtLeast(0f)
            if (t == 0f) return@forEach
            val x = p.x * size.width + sin(t * 10f + p.x * 6f) * 24.dp.toPx()
            val y = -20.dp.toPx() + t * p.speed * (size.height + 40.dp.toPx())
            rotate(p.spin * t, pivot = Offset(x, y)) {
                drawRect(
                    p.color,
                    topLeft = Offset(x - 6.dp.toPx(), y - 3.dp.toPx()),
                    size = Size(12.dp.toPx(), 6.dp.toPx()),
                    alpha = fade,
                )
            }
        }
    }
}

private class Piece(val x: Float, val start: Float, val speed: Float, val spin: Float, val color: Color)

/** Wraps words right-to-left, so the verse reads the way Arabic is read. */
@Composable
fun FlowRowRtl(content: @Composable () -> Unit) {
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
