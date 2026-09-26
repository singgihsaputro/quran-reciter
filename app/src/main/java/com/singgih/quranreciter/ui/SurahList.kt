package com.singgih.quranreciter.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.singgih.quranreciter.data.Progress
import com.singgih.quranreciter.data.Story
import com.singgih.quranreciter.data.Surah

/** The level select: every surah a tile, filling up with stars as it is learnt. */
@Composable
fun SurahList(
    surahs: List<Surah>,
    progress: Progress,
    tonight: Story,
    onOpen: (Surah) -> Unit,
    onStory: () -> Unit,
    onSwitchLanguage: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Header(stars = surahs.sumOf { progress.stars(it) }, onSwitchLanguage)
                TonightCard(tonight, onStory)
            }
        }
        itemsIndexed(surahs, key = { _, s -> s.number }) { i, surah ->
            LevelTile(surah, progress.stars(surah), Candy[i % Candy.size]) { onOpen(surah) }
        }
    }
}

@Composable
private fun Header(stars: Int, onSwitchLanguage: () -> Unit) {
    val s = LocalStrings.current
    val float by rememberInfiniteTransition(label = "moon").animateFloat(
        initialValue = -4f, targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "float",
    )
    Column(Modifier.padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🌙", fontSize = 30.sp, modifier = Modifier.graphicsLayer { translationY = float.dp.toPx() })
            Spacer(Modifier.width(8.dp))
            Text(
                "Recite Quest",
                fontSize = 26.sp,
                maxLines = 1,
                softWrap = false,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            LanguagePill(onSwitchLanguage)
            Spacer(Modifier.width(8.dp))
            StarPill("$stars")
        }
        Text(
            s.tagline,
            color = Color.White.copy(alpha = 0.88f),
        )
    }
}

@Composable
private fun TonightCard(story: Story, onClick: () -> Unit) {
    val s = LocalStrings.current
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp),
    ) {
        Row(Modifier.background(Night).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(story.emoji, fontSize = 40.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.tonightsBedtimeStory, color = Sun, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(s.telling(story).title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(44.dp).background(Sun, CircleShape), contentAlignment = Alignment.Center) {
                Text("▶", color = Ink, fontSize = 18.sp)
            }
        }
    }
}

/** EN ⇄ ID: switches the translation, the stories and every word of the app. */
@Composable
private fun LanguagePill(onClick: () -> Unit) {
    val s = LocalStrings.current
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.22f),
        modifier = Modifier.semantics { contentDescription = s.switchLanguage },
    ) {
        Text(
            (if (s.indonesian) "🇮🇩 " else "🇬🇧 ") + s.code,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun LevelTile(surah: Surah, stars: Int, color: Color, onClick: () -> Unit) {
    val max = surah.verses.size * 3
    ElevatedCard(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
                    Text("${surah.number}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                }
                Spacer(Modifier.weight(1f))
                if (stars == max) Text("🏆", fontSize = 24.sp)
            }
            Spacer(Modifier.height(10.dp))
            Text(surah.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                LocalStrings.current.meaning(surah),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Bar(stars / max.toFloat(), color, Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Text("★ $stars / $max", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        }
    }
}
