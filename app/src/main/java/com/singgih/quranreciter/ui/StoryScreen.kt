package com.singgih.quranreciter.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.singgih.quranreciter.data.Stories
import com.singgih.quranreciter.data.Story
import com.singgih.quranreciter.recite.Narrator
import com.singgih.quranreciter.recite.QariPlayer
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

/**
 * Bedtime: tonight's story from the Qur'an, read aloud by the device's voice with
 * each part lit as it is read, ending on the real verse, recited by the qari.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StoryScreen(stories: List<Story>, start: Int, qari: QariPlayer, onBack: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val narrator = remember { Narrator(context) }
    val tonight = remember { Stories.tonight(stories.size) }
    var index by remember { mutableIntStateOf(start) }
    val story = stories[index]
    val telling = s.telling(story)
    // What the voice reads: the title, the story, then its lesson.
    val parts = remember(telling) { listOf(telling.title) + telling.paragraphs + (s.lessonPrefix + telling.lesson) }
    val lights = remember(telling) { List(parts.size) { BringIntoViewRequester() } }
    var resumeFrom by remember(telling) { mutableIntStateOf(0) }
    // Indonesian often needs its voice downloaded; without one, the story is read by eye.
    val canSpeak = remember(narrator.ready, s) { narrator.speaks(s.locale) }
    val reading = narrator.reading
    val verseUrl = QariPlayer.verseUrl(story.surah, story.verse)
    val scroll = rememberScrollState()

    fun go(to: Int) {
        narrator.stop()
        qari.stop()
        index = Math.floorMod(to, stories.size)
    }

    fun toggle() {
        if (reading != null) {
            resumeFrom = reading
            narrator.stop()
        } else {
            qari.stop()
            narrator.read(parts, s.locale, resumeFrom)
        }
    }

    LaunchedEffect(index) { scroll.scrollTo(0) }
    LaunchedEffect(reading) { reading?.let { lights.getOrNull(it)?.bringIntoView() } }

    // The story ends on the verse it comes from, recited by the qari.
    LaunchedEffect(narrator.finished) {
        if (narrator.finished == 0) return@LaunchedEffect
        resumeFrom = 0
        scroll.animateScrollTo(scroll.maxValue)
        delay(700)
        qari.play(verseUrl)
    }

    DisposableEffect(Unit) {
        onDispose {
            narrator.shutdown()
            qari.stop()
        }
    }

    Box(Modifier.fillMaxSize().background(Night)) {
        NightSky()
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp)) {
            TopBar(
                title = if (index == tonight) s.tonightsStory else s.bedtimeStories,
                subtitle = s.storyCount(index + 1, stories.size),
                onBack = onBack,
            )
            Column(
                Modifier.weight(1f).verticalScroll(scroll).padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().bringIntoViewRequester(lights[0]),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Bobbing { Text(story.emoji, fontSize = 64.sp) }
                    Text(
                        telling.title,
                        fontSize = 26.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (reading == 0) Sun else Color.White,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        s.retoldFrom(story.refs),
                        fontSize = 12.sp,
                        color = Moonlight.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                    )
                }
                telling.paragraphs.forEachIndexed { i, text ->
                    val part = i + 1
                    val bg by animateColorAsState(
                        if (reading == part) Sun.copy(alpha = 0.16f) else Color.Transparent,
                        tween(400),
                        label = "para",
                    )
                    Text(
                        text,
                        fontSize = 18.sp,
                        lineHeight = 28.sp,
                        color = Moonlight.copy(alpha = if (reading != null && reading != part) 0.55f else 1f),
                        modifier = Modifier
                            .bringIntoViewRequester(lights[part])
                            .clip(MaterialTheme.shapes.medium)
                            .background(bg)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(lights.last())
                        .background(
                            if (reading == parts.lastIndex) Sun.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f),
                            MaterialTheme.shapes.medium,
                        )
                        .padding(14.dp),
                ) {
                    Text("💡", fontSize = 22.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(telling.lesson, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
                StoryVerse(story, playing = qari.playing == verseUrl) {
                    if (qari.playing == verseUrl) qari.stop() else { narrator.stop(); qari.play(verseUrl) }
                }
                Text(
                    s.goodnight,
                    color = Moonlight,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Text(
                    s.forGrownUps(story.refs),
                    color = Moonlight.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Controls(
                reading = reading != null,
                enabled = canSpeak,
                onPrevious = { go(index - 1) },
                onToggle = { toggle() },
                onNext = { go(index + 1) },
            )
            Text(
                when {
                    narrator.ready != null && !canSpeak -> s.noStoryVoice
                    reading != null -> s.telling
                    qari.failed -> s.verseFailed
                    else -> s.tapToHearStory
                },
                color = Moonlight.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
        }
    }
}

/** The one real verse the story comes from: fetched text, and the qari reciting it. */
@Composable
private fun StoryVerse(story: Story, playing: Boolean, onListen: () -> Unit) {
    val s = LocalStrings.current
    Surface(shape = MaterialTheme.shapes.large, color = Color(0xFFFFF8E7), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.fromQuran(story.verseKey),
                    color = Grape,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
                ListenChip(s.listen, playing, onListen)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                story.arabic,
                fontSize = 26.sp,
                lineHeight = 48.sp,
                color = Ink,
                textAlign = TextAlign.Right,
                style = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Text(s.translation(story), style = MaterialTheme.typography.bodyMedium, color = Color(0xFF5B567A))
        }
    }
}

@Composable
private fun Controls(
    reading: Boolean,
    enabled: Boolean,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
) {
    val s = LocalStrings.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundButton("‹", s.previousStory, 52, Color.White.copy(alpha = 0.16f), Color.White, onClick = onPrevious)
        RoundButton(
            if (reading) "❚❚" else "▶",
            if (reading) s.pause else s.tellStory,
            76,
            Sun,
            Ink,
            enabled = enabled,
            onClick = onToggle,
        )
        RoundButton("›", s.nextStory, 52, Color.White.copy(alpha = 0.16f), Color.White, onClick = onNext)
    }
}

@Composable
private fun RoundButton(
    glyph: String,
    label: String,
    size: Int,
    color: Color,
    content: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (enabled) color else color.copy(alpha = 0.4f),
        shadowElevation = if (size > 60) 10.dp else 0.dp,
        modifier = Modifier.size(size.dp).semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(glyph, color = content, fontSize = (size / 2.4f).sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Bobbing(content: @Composable () -> Unit) {
    val y by rememberInfiniteTransition(label = "bob").animateFloat(
        initialValue = -5f, targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "y",
    )
    Box(Modifier.graphicsLayer { translationY = y.dp.toPx() }) { content() }
}

/** Slowly twinkling stars behind the story. */
@Composable
private fun NightSky() {
    val stars = remember {
        List(70) { Triple(Offset(Random.nextFloat(), Random.nextFloat() * 0.7f), Random.nextFloat() * 6.28f, 0.8f + Random.nextFloat() * 1.4f) }
    }
    val t by rememberInfiniteTransition(label = "twinkle").animateFloat(
        initialValue = 0f, targetValue = 6.2832f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing)),
        label = "t",
    )
    Canvas(Modifier.fillMaxSize()) {
        stars.forEach { (at, phase, radius) ->
            drawCircle(
                Color.White,
                radius = radius.dp.toPx(),
                center = Offset(at.x * size.width, at.y * size.height),
                alpha = 0.25f + 0.6f * ((sin(t + phase) + 1f) / 2f),
            )
        }
    }
}
