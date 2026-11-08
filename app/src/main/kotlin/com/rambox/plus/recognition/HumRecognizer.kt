// HumRecognizer.kt
// Sing / hum recognition: records the mic, then asks ACRCloud's humming-recognition project which song it is.
// (Normal "song playing nearby" recognition is MusicRecognitionService; humming needs a melody matcher, which is this.)

package com.rambox.plus.recognition

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

data class HumCandidate(val title: String, val artist: String, val score: Double)

sealed interface HumState {
    data object Idle : HumState
    data class Listening(val level: Float) : HumState
    data object Searching : HumState
    data class Found(val matches: List<HumCandidate>) : HumState
    /** [retryable] = the sound just didn't match yet, so listening can carry on; false = keys/network problem, stop at once */
    data class Failed(val message: String, val retryable: Boolean = false) : HumState
}

/**
 * the ACRCloud project used for humming. The app's own keys (put in local.properties when building) are used
 * unless the person typed their own on the hum screen.
 */
class HumCredentials(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("rambox_hum", Context.MODE_PRIVATE)

    /** true when the app was built with its own keys, so nobody has to set anything up */
    val builtIn: Boolean
        get() = com.rambox.plus.BuildConfig.ACR_ACCESS_KEY.isNotBlank() && com.rambox.plus.BuildConfig.ACR_ACCESS_SECRET.isNotBlank()

    var host: String
        get() = prefs.getString("host", "").orEmpty().ifBlank {
            com.rambox.plus.BuildConfig.ACR_HOST.ifBlank { "identify-eu-west-1.acrcloud.com" }
        }
        set(v) = prefs.edit().putString("host", v.trim()).apply()
    var accessKey: String
        get() = prefs.getString("key", "").orEmpty().ifBlank { com.rambox.plus.BuildConfig.ACR_ACCESS_KEY }
        set(v) = prefs.edit().putString("key", v.trim()).apply()
    var accessSecret: String
        get() = prefs.getString("secret", "").orEmpty().ifBlank { com.rambox.plus.BuildConfig.ACR_ACCESS_SECRET }
        set(v) = prefs.edit().putString("secret", v.trim()).apply()
    val isSet get() = host.isNotBlank() && accessKey.isNotBlank() && accessSecret.isNotBlank()
}

object HumRecognizer {
    private const val SampleRate = 16000
    private const val RecordMs = 12_000L
    private const val FirstTryMs = 5_000L
    private const val RetryEveryMs = 2_500L

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** records for [RecordMs] (calling [onLevel] with the loudness 0..1) and returns 16 kHz mono PCM */
    @SuppressLint("MissingPermission")
    suspend fun record(onLevel: (Float) -> Unit): ByteArray = withContext(Dispatchers.IO) {
        val min = AudioRecord.getMinBufferSize(SampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufferSize = maxOf(min, SampleRate / 2)
        val rec = AudioRecord(MediaRecorder.AudioSource.MIC, SampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(bufferSize)
        val start = System.currentTimeMillis()
        try {
            rec.startRecording()
            while (isActive && System.currentTimeMillis() - start < RecordMs) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) {
                    out.write(buf, 0, n)
                    var peak = 0
                    var i = 0
                    while (i + 1 < n) {
                        val v = (buf[i].toInt() and 0xFF) or (buf[i + 1].toInt() shl 8)
                        peak = maxOf(peak, abs(v.toShort().toInt()))
                        i += 2
                    }
                    onLevel(kotlin.math.sqrt((peak / 9000f).coerceIn(0f, 1f)))
                }
            }
        } finally {
            runCatching { rec.stop() }
            rec.release()
        }
        out.toByteArray()
    }

    /**
     * listens for up to [RecordMs] but does not wait that long: after [FirstTryMs], and again every [RetryEveryMs],
     * what has been heard so far is sent to ACRCloud, and the first match ends the listening early.
     * [onLevel] gets the loudness 0..1, [onProgress] 0..1 of the listening time, [onSearching] is called when the
     * time is up and the last try over everything heard begins.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @SuppressLint("MissingPermission")
    suspend fun listenAndIdentify(
        creds: HumCredentials,
        onLevel: (Float) -> Unit,
        onProgress: (Float) -> Unit,
        onSearching: () -> Unit,
    ): HumState = withContext(Dispatchers.IO) {
        val min = AudioRecord.getMinBufferSize(SampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufferSize = maxOf(min, SampleRate / 2)
        val rec = AudioRecord(MediaRecorder.AudioSource.MIC, SampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(bufferSize)
        var outcome: HumState? = null
        var last = HumState.Failed("No song found. Try humming the chorus more clearly.", retryable = true)

        fun settle(r: HumState) {
            when {
                r is HumState.Found -> outcome = r
                r is HumState.Failed && !r.retryable -> outcome = r
                r is HumState.Failed -> last = r
            }
        }

        try {
            coroutineScope {
                var inflight: Deferred<HumState>? = null
                var nextTry = FirstTryMs
                val start = System.currentTimeMillis()
                rec.startRecording()
                while (isActive && outcome == null) {
                    val elapsed = System.currentTimeMillis() - start
                    if (elapsed >= RecordMs) break
                    val n = rec.read(buf, 0, buf.size)
                    if (n > 0) {
                        out.write(buf, 0, n)
                        var peak = 0
                        var i = 0
                        while (i + 1 < n) {
                            val v = (buf[i].toInt() and 0xFF) or (buf[i + 1].toInt() shl 8)
                            peak = maxOf(peak, abs(v.toShort().toInt()))
                            i += 2
                        }
                        onLevel(kotlin.math.sqrt((peak / 9000f).coerceIn(0f, 1f)))
                    }
                    onProgress((elapsed / RecordMs.toFloat()).coerceIn(0f, 1f))
                    val job = inflight
                    if (job != null && job.isCompleted) {
                        inflight = null
                        settle(job.getCompleted())
                    }
                    if (outcome == null && inflight == null && elapsed >= nextTry) {
                        nextTry += RetryEveryMs
                        val snapshot = out.toByteArray()
                        inflight = async { identify(creds, snapshot) }
                    }
                }
                if (outcome == null) inflight?.let { settle(it.await()) }
                if (outcome == null) {
                    onProgress(1f)
                    onSearching()
                    settle(identify(creds, out.toByteArray()))
                }
            }
        } finally {
            runCatching { rec.stop() }
            rec.release()
        }
        outcome ?: last
    }

    private fun wav(pcm: ByteArray): ByteArray {
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()); h.putInt(36 + pcm.size); h.put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()); h.putInt(16); h.putShort(1); h.putShort(1)
        h.putInt(SampleRate); h.putInt(SampleRate * 2); h.putShort(2); h.putShort(16)
        h.put("data".toByteArray()); h.putInt(pcm.size)
        return h.array() + pcm
    }

    private fun sign(secret: String, text: String): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA1"))
        return Base64.encodeToString(mac.doFinal(text.toByteArray()), Base64.NO_WRAP)
    }

    suspend fun identify(creds: HumCredentials, pcm: ByteArray): HumState = withContext(Dispatchers.IO) {
        runCatching {
            val sample = wav(pcm)
            val ts = (System.currentTimeMillis() / 1000).toString()
            val signature = sign(creds.accessSecret, "POST\n/v1/identify\n${creds.accessKey}\naudio\n1\n$ts")
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("access_key", creds.accessKey)
                .addFormDataPart("sample_bytes", sample.size.toString())
                .addFormDataPart("timestamp", ts)
                .addFormDataPart("signature", signature)
                .addFormDataPart("data_type", "audio")
                .addFormDataPart("signature_version", "1")
                .addFormDataPart("sample", "hum.wav", sample.toRequestBody("audio/wav".toMediaType()))
                .build()
            val host = creds.host.removePrefix("https://").removePrefix("http://").trimEnd('/')
            val request = Request.Builder().url("https://$host/v1/identify").post(body).build()
            client.newCall(request).execute().use { response ->
                val json = JSONObject(response.body?.string().orEmpty())
                val status = json.optJSONObject("status")
                val code = status?.optInt("code", -1) ?: -1
                if (code != 0) {
                    return@use if (code == 1001) HumState.Failed("No song found. Try humming the chorus more clearly.", retryable = true)
                    else HumState.Failed(status?.optString("msg").orEmpty().ifBlank { "Recognition failed ($code)" })
                }
                val meta = json.optJSONObject("metadata")
                val list = meta?.optJSONArray("humming") ?: meta?.optJSONArray("music")
                val matches = buildList {
                    if (list != null) for (i in 0 until list.length()) {
                        val o = list.getJSONObject(i)
                        val artists = o.optJSONArray("artists")
                        val artist = if (artists != null && artists.length() > 0) artists.getJSONObject(0).optString("name") else ""
                        add(HumCandidate(o.optString("title"), artist, o.optString("score", "0").toDoubleOrNull() ?: 0.0))
                    }
                }
                if (matches.isEmpty()) HumState.Failed("No song found. Try again, a bit louder.", retryable = true) else HumState.Found(matches)
            }
        }.getOrElse { HumState.Failed(it.message ?: "Could not reach the recognition server", retryable = true) }
    }
}