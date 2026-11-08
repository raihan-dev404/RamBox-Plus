// HumScreen.kt: sing or hum a tune, RamBox + finds the song
//
// The mic sits in the middle with a ring of bars around it that jump with your voice. As soon as a song is found
// (it does not wait for the full 12 seconds) the ring breaks apart, a bloop plays, and the matches rise up from the
// bottom: gold, silver and bronze for the top three, with how sure the match is on the right. Tap one to play it.

package com.rambox.plus.ui.screens.hum

import android.Manifest
import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.rambox.plus.LocalPlayerConnection
import com.rambox.plus.R
import com.rambox.plus.models.toMediaMetadata
import com.rambox.plus.playback.queues.YouTubeQueue
import com.rambox.plus.recognition.HumCandidate
import com.rambox.plus.recognition.HumCredentials
import com.rambox.plus.recognition.HumRecognizer
import com.rambox.plus.recognition.HumState
import com.rambox.plus.ui.component.SubSettingsScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val Gold = Color(0xFFFFC83D)
private val Silver = Color(0xFFC3CAD3)
private val Bronze = Color(0xFFCD7F32)
private val Medals = listOf(Gold, Silver, Bronze)

/** bars around the mic, also the number of pieces it breaks into */
private const val BarCount = 56

@Composable
fun HumScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current
    val sounds = rememberHumSounds()
    val creds = remember { HumCredentials(context) }
    var state by remember { mutableStateOf<HumState>(HumState.Idle) }
    var level by remember { mutableStateOf(0f) }
    var progress by remember { mutableStateOf(0f) }
    var job by remember { mutableStateOf<Job?>(null) }
    var playingIndex by remember { mutableStateOf(-1) }
    var showSetup by remember { mutableStateOf(!creds.isSet) }
    var host by remember { mutableStateOf(creds.host) }
    var key by remember { mutableStateOf(creds.accessKey) }
    var secret by remember { mutableStateOf(creds.accessSecret) }

    fun start() {
        job?.cancel()
        level = 0f
        progress = 0f
        playingIndex = -1
        sounds.tick(1.1f)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        job = scope.launch {
            state = HumState.Listening(0f)
            val result = HumRecognizer.listenAndIdentify(
                creds = creds,
                onLevel = { level = it },
                onProgress = { progress = it },
                onSearching = { level = 0f; state = HumState.Searching },
            )
            level = 0f
            state = result
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else state = HumState.Failed("Microphone permission is needed to hear you.")
    }

    // the moment a song is found: bloop, buzz, and the ring breaks apart
    val found = state is HumState.Found
    val burst = remember { Animatable(0f) }
    LaunchedEffect(found) {
        if (found) {
            sounds.win()
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            burst.snapTo(0f)
            burst.animateTo(1f, tween(950, easing = FastOutSlowInEasing))
        } else {
            burst.snapTo(0f)
        }
    }

    fun play(index: Int, match: HumCandidate) {
        if (playingIndex != -1) return
        playingIndex = index
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        val query = "${match.title} ${match.artist}".trim()
        scope.launch {
            val song = withContext(Dispatchers.IO) {
                runCatching {
                    YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrThrow()
                        .items.filterIsInstance<SongItem>().firstOrNull()
                }.getOrNull()
            }
            if (song != null && playerConnection != null) {
                playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
                Toast.makeText(context, "Playing ${match.title}", Toast.LENGTH_SHORT).show()
            } else {
                // could not find it to play right away, so show the search for it instead
                navController.navigate("search/${URLEncoder.encode(query, "UTF-8")}")
            }
            playingIndex = -1
        }
    }

    // a quiet room should leave the ring calm; only real sound moves it
    val smoothLevel by animateFloatAsState(
        targetValue = ((level - 0.14f) / 0.86f).coerceIn(0f, 1f),
        animationSpec = tween(90, easing = LinearEasing),
        label = "humLevel",
    )

    SubSettingsScaffold(title = "Sing or hum", onBack = { navController.navigateUp() }) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HumStage(
                listening = state is HumState.Listening,
                searching = state is HumState.Searching,
                found = found,
                level = { smoothLevel },
                progress = { progress },
                burst = { burst.value },
                onMic = {
                    when (state) {
                        is HumState.Listening -> { job?.cancel(); level = 0f; state = HumState.Idle }
                        HumState.Searching -> Unit
                        else -> {
                            if (!creds.isSet) { showSetup = true } else permission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
            )

            if (state is HumState.Listening) {
                Text("${(progress * 100).toInt()}%", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                text = when (val s = state) {
                    HumState.Idle -> "Tap the mic, then sing or hum the tune"
                    is HumState.Listening -> "Listening… keep humming"
                    HumState.Searching -> "Finding your song…"
                    is HumState.Found -> "Pick your song"
                    is HumState.Failed -> s.message
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            if (state is HumState.Failed) {
                Text("Tap the mic to try again", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            (state as? HumState.Found)?.let { f ->
                val matches = remember(f) { f.matches.sortedByDescending { it.score }.take(6) }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    matches.forEachIndexed { index, m ->
                        ResultRow(
                            index = index,
                            match = m,
                            loading = playingIndex == index,
                            sounds = sounds,
                            revealKey = f,
                            onClick = { play(index, m) },
                        )
                    }
                    Text(
                        "Tap a song to play it",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
                    )
                }
            }

            // with the app's own keys built in there is nothing for the person to set up
            if (!creds.builtIn) TextButton(onClick = { showSetup = !showSetup }) {
                Text(if (showSetup) "Hide recognition setup" else "Recognition setup")
            }
            if (showSetup && !creds.builtIn) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Humming needs a free ACRCloud project with \"Humming\" turned on. Paste its host and keys here once.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(host, { host = it }, label = { Text("Host (e.g. identify-eu-west-1.acrcloud.com)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(key, { key = it }, label = { Text("Access key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(secret, { secret = it }, label = { Text("Access secret") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        creds.host = host; creds.accessKey = key; creds.accessSecret = secret
                        showSetup = false
                    }) { Text("Save") }
                }
            }
            Spacer(Modifier.height(140.dp))
        }
    }
}

/**
 * the mic in the middle of its stage: a soft wobbling shape behind it, rings around it, a progress arc, and a
 * ring of bars that jump with your voice. When a song is found the bars break into pieces that fly outward.
 */
@Composable
private fun HumStage(
    listening: Boolean,
    searching: Boolean,
    found: Boolean,
    level: () -> Float,
    progress: () -> Float,
    burst: () -> Float,
    onMic: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val c1 = scheme.primary
    val c2 = scheme.tertiary
    val c3 = scheme.secondary
    val blobColor = scheme.surfaceVariant
    val ink = scheme.onSurface

    val stageHeight by animateDpAsState(
        targetValue = if (found) 250.dp else 380.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
        label = "humStage",
    )
    // the bounce when it settles is the "bloop"
    val micScale by animateFloatAsState(
        targetValue = if (found) 0.8f else 1f,
        animationSpec = spring(dampingRatio = 0.33f, stiffness = 320f),
        label = "humMicScale",
    )

    val transition = rememberInfiniteTransition(label = "humStage")
    val fast by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1700, easing = LinearEasing), RepeatMode.Restart),
        label = "fast",
    )
    val slow by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "slow",
    )

    // fixed random bits so every piece flies its own way
    val shardDistance = remember { Random(11).let { r -> FloatArray(BarCount) { 70f + r.nextFloat() * 170f } } }
    val shardSpin = remember { Random(12).let { r -> FloatArray(BarCount) { (r.nextFloat() - 0.5f) * 540f } } }
    val shardLength = remember { Random(13).let { r -> FloatArray(BarCount) { 12f + r.nextFloat() * 12f } } }
    val shardDrift = remember { Random(14).let { r -> FloatArray(BarCount) { (r.nextFloat() - 0.5f) * 0.5f } } }

    Box(
        modifier = Modifier.fillMaxWidth().height(stageHeight),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val lv = if (listening) level() else 0f
            val active = listening || searching
            val baseR = 74.dp.toPx() * micScale
            val twoPi = (2.0 * PI).toFloat()
            val fastAngle = fast * twoPi
            val slowAngle = slow * twoPi

            // glow that swells with the sound
            if (active) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(c1.copy(alpha = 0.10f + 0.28f * lv), Color.Transparent),
                        center = center,
                        radius = baseR * 2.5f,
                    ),
                    radius = baseR * 2.5f,
                    center = center,
                )
            }

            // the mic's own shape: a scalloped circle that breathes, and swells with the voice
            val breathe = 0.035f * sin(fastAngle)
            drawPath(
                path = wobblyCircle(center, baseR * (1f + 0.10f * lv), 10, 0.05f + 0.07f * lv + breathe, slowAngle),
                color = blobColor,
            )

            // two soft outlines turning the other way
            val outlineAlpha = if (active) 0.55f else 0.22f
            drawPath(
                path = wobblyCircle(center, baseR * 1.2f, 7, 0.05f + 0.10f * lv, -slowAngle * 2f),
                color = c1.copy(alpha = outlineAlpha),
                style = Stroke(width = 2.dp.toPx()),
            )
            drawPath(
                path = wobblyCircle(center, baseR * 1.32f, 5, 0.05f + 0.12f * lv, slowAngle * 3f),
                color = c2.copy(alpha = outlineAlpha * 0.7f),
                style = Stroke(width = 1.5.dp.toPx()),
            )

            // time listened so far
            if (active) {
                val arcR = baseR * 1.46f
                drawCircle(color = ink.copy(alpha = 0.12f), radius = arcR, center = center, style = Stroke(width = 3.dp.toPx()))
                drawArc(
                    color = ink.copy(alpha = 0.9f),
                    startAngle = -90f,
                    sweepAngle = 360f * (if (searching) 1f else progress()),
                    useCenter = false,
                    topLeft = Offset(center.x - arcR, center.y - arcR),
                    size = Size(arcR * 2f, arcR * 2f),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
            }

            val barInner = baseR * 1.64f
            val barWidth = 4.dp.toPx()

            // the ring of bars: listening = your voice, searching = a wave running around, idle = a faint breath
            if (!found) {
                val brush = Brush.sweepGradient(listOf(c1, c2, c3, c1), center = center)
                for (i in 0 until BarCount) {
                    val a = i * twoPi / BarCount - twoPi / 4f
                    val amp = when {
                        listening -> 0.06f + lv * (0.35f + 0.65f * (0.5f + 0.5f * sin(fastAngle * (1 + i % 3) + i * 1.3f)))
                        searching -> 0.18f + 0.22f * (0.5f + 0.5f * sin(fastAngle - i * twoPi * 3f / BarCount))
                        else -> 0.05f + 0.04f * sin(fastAngle + i * 0.35f)
                    }.coerceIn(0.03f, 1f)
                    val length = 3.dp.toPx() + amp * 42.dp.toPx()
                    val dx = cos(a)
                    val dy = sin(a)
                    drawLine(
                        brush = brush,
                        start = Offset(center.x + dx * barInner, center.y + dy * barInner),
                        end = Offset(center.x + dx * (barInner + length), center.y + dy * (barInner + length)),
                        strokeWidth = barWidth,
                        cap = StrokeCap.Round,
                        alpha = if (active) 0.5f + 0.5f * amp else 0.35f,
                    )
                }
            }

            // song found: every bar becomes a shard and flies away, with a shockwave
            val b = burst()
            if (found && b > 0f && b < 1f) {
                val eased = 1f - (1f - b) * (1f - b) * (1f - b)
                val fade = (1f - b).coerceIn(0f, 1f)
                drawCircle(
                    color = c1.copy(alpha = 0.55f * fade),
                    radius = barInner + 190.dp.toPx() * eased,
                    center = center,
                    style = Stroke(width = (5f * fade).coerceAtLeast(0.5f).dp.toPx()),
                )
                val tints = listOf(c1, c2, c3, ink)
                for (i in 0 until BarCount) {
                    val a = i * twoPi / BarCount - twoPi / 4f + shardDrift[i] * eased
                    val r = barInner + shardLength[i].dp.toPx() / 2f + shardDistance[i].dp.toPx() * eased
                    val x = center.x + cos(a) * r
                    val y = center.y + sin(a) * r
                    drawShard(
                        at = Offset(x, y),
                        angleDeg = a * 180f / PI.toFloat() + shardSpin[i] * eased,
                        length = shardLength[i].dp.toPx() * (1f - 0.4f * eased),
                        thickness = barWidth,
                        color = tints[i % tints.size].copy(alpha = fade),
                    )
                }
            }
        }

        // the mic button itself
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .graphicsLayer {
                    val s = micScale * (1f + 0.07f * (if (listening) level() else 0f))
                    scaleX = s
                    scaleY = s
                }
                .size(112.dp)
                .clip(CircleShape)
                .clickable(enabled = !searching, onClick = onMic),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_hum_mic),
                contentDescription = if (listening) "Stop listening" else "Start listening",
                tint = Color(0xFFE6E6E6),
                modifier = Modifier.size(52.dp),
            )
        }
    }
}

private fun DrawScope.drawShard(at: Offset, angleDeg: Float, length: Float, thickness: Float, color: Color) {
    withTransform({
        translate(at.x, at.y)
        rotate(angleDeg, Offset.Zero)
    }) {
        drawRoundRect(
            color = color,
            topLeft = Offset(-length / 2f, -thickness / 2f),
            size = Size(length, thickness),
            cornerRadius = CornerRadius(thickness / 2f),
        )
    }
}

/** a circle whose edge swells in and out [lobes] times, like a flower or a cookie */
private fun wobblyCircle(center: Offset, radius: Float, lobes: Int, amplitude: Float, rotation: Float): Path {
    val path = Path()
    val steps = 96
    for (i in 0..steps) {
        val t = i * (2f * PI.toFloat()) / steps
        val r = radius * (1f + amplitude * cos(lobes * t + rotation))
        val x = center.x + r * cos(t)
        val y = center.y + r * sin(t)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/** one match: rises from the bottom in turn, medal for the top three, how sure the match is on the right */
@Composable
private fun ResultRow(
    index: Int,
    match: HumCandidate,
    loading: Boolean,
    sounds: HumSounds,
    revealKey: Any,
    onClick: () -> Unit,
) {
    val reveal = remember(revealKey) { Animatable(0f) }
    LaunchedEffect(revealKey) {
        delay(520L + index * 170L)
        sounds.tick((1.3f - index * 0.07f).coerceAtLeast(0.8f))
        reveal.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 230f))
    }

    val medal = Medals.getOrNull(index)
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val shape = RoundedCornerShape(22.dp)
    val percent = (if (match.score <= 1.0) match.score * 100.0 else match.score).coerceIn(0.0, 100.0)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = reveal.value.coerceIn(0f, 1f)
                translationY = (1f - reveal.value) * 220.dp.toPx()
            }
            .clip(shape)
            .background(
                if (medal != null) Brush.horizontalGradient(listOf(medal.copy(alpha = 0.28f), surface))
                else Brush.horizontalGradient(listOf(surface, surface))
            )
            .border(if (medal != null) BorderStroke(1.dp, medal.copy(alpha = 0.7f)) else BorderStroke(0.dp, Color.Transparent), shape)
            .clickable(enabled = reveal.value > 0.9f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(medal ?: Color.Transparent)
                .border(if (medal == null) BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline) else BorderStroke(0.dp, Color.Transparent), CircleShape),
        ) {
            Text(
                text = "${index + 1}",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = if (medal != null) Color(0xFF1B1B1B) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(match.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (match.artist.isNotBlank()) {
                Text(
                    match.artist,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.size(10.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background((medal ?: MaterialTheme.colorScheme.onSurface).copy(alpha = 0.16f))
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    "${percent.toInt()}%",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = medal ?: MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** the little sounds of the hum screen: a tick when listening starts and as each match rises, a bloop when a song is found */
private class HumSounds(context: Context) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val tickId = pool.load(context, R.raw.gacha_tick, 1)
    private val winId = pool.load(context, R.raw.gacha_win, 1)

    fun tick(rate: Float = 1f) {
        pool.play(tickId, 0.6f, 0.6f, 1, 0, rate)
    }

    fun win() {
        pool.play(winId, 0.85f, 0.85f, 2, 0, 1f)
    }

    fun release() = pool.release()
}

@Composable
private fun rememberHumSounds(): HumSounds {
    val context = LocalContext.current
    val sounds = remember { HumSounds(context.applicationContext) }
    DisposableEffect(Unit) { onDispose { sounds.release() } }
    return sounds
}